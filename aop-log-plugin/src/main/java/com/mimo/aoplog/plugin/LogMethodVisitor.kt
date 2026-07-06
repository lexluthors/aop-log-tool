package com.mimo.aoplog.plugin

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * ASM MethodVisitor — 在目标方法的前后插入 AOP 日志调用。
 *
 * 简化设计：耗时计算由 LogAspect 内部完成（ThreadLocal 记录进入时间），
 * 不在字节码中添加新的局部变量，避免栈帧计算问题。
 *
 * 插入逻辑：
 * ```
 * try {
 *   LogAspect.enter(className, methodName, descriptor, isStatic, this, args)
 *   === 原始方法体 ===
 *   // 每个 return 前：
 *   LogAspect.exit(className, methodName, descriptor, isStatic, this, args, returnValue)
 *   return ...
 * } catch (Throwable $e) {
 *   LogAspect.throw_(className, methodName, descriptor, isStatic, this, args, $e)
 *   throw $e
 * }
 * ```
 */
class LogMethodVisitor(
    private val api: Int,
    mv: MethodVisitor,
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val isStatic: Boolean,
    private val logArgs: Boolean,
    private val logReturnValue: Boolean,
    private val computeCost: Boolean
) : MethodVisitor(api, mv) {

    private val isConstructor = methodName == "<init>"
    private val argTypes: Array<Type> = Type.getArgumentTypes(methodDescriptor)

    // try-catch 标记
    private val tryStart = Label()
    private val tryEnd = Label()
    private val catchHandler = Label()

    // 状态
    private var enterInserted = false

    // 暂存返回值的局部变量槽位（非 void 方法需要）
    private var returnValueSlot = -1

    // 异常暂存槽位
    private var throwableSlot = -1

    // LogAspect 方法签名常量（exit/throw_ 不再有 costMs 参数）
    private companion object {
        const val LOG_ASPECT_INTERNAL = "com/mimo/aoplog/runtime/LogAspect"

        // enter(String, String, String, boolean, Object, Object[])V
        const val ENTER_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
            "ZLjava/lang/Object;[Ljava/lang/Object;)V"

        // exit(String, String, String, boolean, Object, Object[], Object)V
        const val EXIT_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
            "ZLjava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;)V"

        // throw_(String, String, String, boolean, Object, Object[], Throwable)V
        const val THROW_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
            "ZLjava/lang/Object;[Ljava/lang/Object;Ljava/lang/Throwable;)V"
    }

    override fun visitCode() {
        super.visitCode()

        // 分配暂存槽位（在原有 maxLocals 之后）
        allocateSlots()

        // 标记 try 块开始
        mv.visitLabel(tryStart)

        if (!isConstructor) {
            insertEnterCall()
        }
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean
    ) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

        // 构造函数：在 super()/this() 之后插入 enter
        if (isConstructor && !enterInserted &&
            name == "<init>" && opcode == Opcodes.INVOKESPECIAL
        ) {
            insertEnterCall()
        }
    }

    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN,
            Opcodes.DRETURN, Opcodes.ARETURN -> {
                insertExitCallWithReturnValue(opcode)
            }
            Opcodes.RETURN -> {
                if (enterInserted) {
                    insertExitCallVoid()
                }
            }
        }
        super.visitInsn(opcode)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // 标记 try 块结束
        mv.visitLabel(tryEnd)

        // 注册 try-catch
        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable")

        // ─── catch 块 ───
        mv.visitLabel(catchHandler)
        // Throwable 在栈顶

        // 暂存 Throwable
        mv.visitVarInsn(Opcodes.ASTORE, throwableSlot)

        // 构建 throw_ 参数
        insertCommonArgs()

        // 加载 Throwable
        mv.visitVarInsn(Opcodes.ALOAD, throwableSlot)

        // 调用 LogAspect.throw_
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC, LOG_ASPECT_INTERNAL, "throw_", THROW_DESC, false
        )

        // 重新抛出异常
        mv.visitVarInsn(Opcodes.ALOAD, throwableSlot)
        mv.visitInsn(Opcodes.ATHROW)

        super.visitMaxs(maxStack + 10, maxLocals + 3)
    }

    // ━━━━━━━━━━━━━━━━━━━━ 槽位分配 ━━━━━━━━━━━━━━━━━━━━

    private fun allocateSlots() {
        // 计算方法原有参数占用的最大槽位
        var slot = if (isStatic) 0 else 1
        for (type in argTypes) {
            slot += type.size
        }

        throwableSlot = slot++

        // 只为非 void 方法分配返回值暂存槽
        if (!isVoidMethod() && logReturnValue) {
            returnValueSlot = slot++
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━ enter 插入 ━━━━━━━━━━━━━━━━━━━━

    private fun insertEnterCall() {
        if (enterInserted) return
        enterInserted = true

        mv.visitLdcInsn(className)
        mv.visitLdcInsn(methodName)
        mv.visitLdcInsn(methodDescriptor)
        mv.visitInsn(if (isStatic) Opcodes.ICONST_1 else Opcodes.ICONST_0)

        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL)
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
        }

        if (logArgs) {
            insertBoxedArgs()
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL)
        }

        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC, LOG_ASPECT_INTERNAL, "enter", ENTER_DESC, false
        )
    }

    // ━━━━━━━━━━━━━━━━━━━━ exit 插入（有返回值） ━━━━━━━━━━━━━━━━━━━━

    private fun insertExitCallWithReturnValue(opcode: Int) {
        if (!enterInserted) return

        // 返回值在栈顶，先暂存
        mv.visitVarInsn(getStoreOpcode(opcode), returnValueSlot)

        // 构建公共参数
        insertCommonArgs()

        // 加载 returnValue 并装箱
        mv.visitVarInsn(getLoadOpcode(opcode), returnValueSlot)
        boxIfNeeded(getReturnType(opcode))

        // 调用 LogAspect.exit（无 costMs 参数）
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC, LOG_ASPECT_INTERNAL, "exit", EXIT_DESC, false
        )

        // 取回返回值
        mv.visitVarInsn(getLoadOpcode(opcode), returnValueSlot)
    }

    // ━━━━━━━━━━━━━━━━━━━━ exit 插入（void） ━━━━━━━━━━━━━━━━━━━━

    private fun insertExitCallVoid() {
        insertCommonArgs()
        mv.visitInsn(Opcodes.ACONST_NULL) // returnValue = null

        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC, LOG_ASPECT_INTERNAL, "exit", EXIT_DESC, false
        )
    }

    // ━━━━━━━━━━━━━━━━━━━━ 公共参数构建 ━━━━━━━━━━━━━━━━━━━━

    private fun insertCommonArgs() {
        mv.visitLdcInsn(className)
        mv.visitLdcInsn(methodName)
        mv.visitLdcInsn(methodDescriptor)
        mv.visitInsn(if (isStatic) Opcodes.ICONST_1 else Opcodes.ICONST_0)
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL)
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
        }
        if (logArgs) {
            insertBoxedArgs()
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL)
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━ 参数装箱 ━━━━━━━━━━━━━━━━━━━━

    private fun insertBoxedArgs() {
        if (argTypes.isEmpty()) {
            mv.visitInsn(Opcodes.ACONST_NULL)
            return
        }

        pushInt(argTypes.size)
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")

        var slot = if (isStatic) 0 else 1

        for (i in argTypes.indices) {
            val type = argTypes[i]
            mv.visitInsn(Opcodes.DUP)
            pushInt(i)
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot)
            boxIfNeeded(type)
            mv.visitInsn(Opcodes.AASTORE)  // 修复：Object 数组用 aastore，不是 iastore
            slot += type.size
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━ 辅助方法 ━━━━━━━━━━━━━━━━━━━━

    private fun boxIfNeeded(type: Type) {
        when (type.sort) {
            Type.BOOLEAN -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false)
            Type.BYTE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false)
            Type.CHAR -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false)
            Type.SHORT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false)
            Type.INT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            Type.LONG -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
            Type.FLOAT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)
            Type.DOUBLE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false)
        }
    }

    private fun pushInt(value: Int) {
        when (value) {
            in -1..5 -> mv.visitInsn(Opcodes.ICONST_0 + value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(Opcodes.BIPUSH, value)
            in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(Opcodes.SIPUSH, value)
            else -> mv.visitLdcInsn(value)
        }
    }

    private fun isVoidMethod(): Boolean = Type.getReturnType(methodDescriptor) == Type.VOID_TYPE

    private fun getReturnType(opcode: Int): Type = when (opcode) {
        Opcodes.IRETURN -> Type.INT_TYPE
        Opcodes.LRETURN -> Type.LONG_TYPE
        Opcodes.FRETURN -> Type.FLOAT_TYPE
        Opcodes.DRETURN -> Type.DOUBLE_TYPE
        Opcodes.ARETURN -> Type.getType("Ljava/lang/Object;")
        else -> Type.VOID_TYPE
    }

    private fun getStoreOpcode(returnOpcode: Int): Int = when (returnOpcode) {
        Opcodes.IRETURN -> Opcodes.ISTORE
        Opcodes.LRETURN -> Opcodes.LSTORE
        Opcodes.FRETURN -> Opcodes.FSTORE
        Opcodes.DRETURN -> Opcodes.DSTORE
        Opcodes.ARETURN -> Opcodes.ASTORE
        else -> Opcodes.ASTORE
    }

    private fun getLoadOpcode(returnOpcode: Int): Int = when (returnOpcode) {
        Opcodes.IRETURN -> Opcodes.ILOAD
        Opcodes.LRETURN -> Opcodes.LLOAD
        Opcodes.FRETURN -> Opcodes.FLOAD
        Opcodes.DRETURN -> Opcodes.DLOAD
        Opcodes.ARETURN -> Opcodes.ALOAD
        else -> Opcodes.ALOAD
    }
}
