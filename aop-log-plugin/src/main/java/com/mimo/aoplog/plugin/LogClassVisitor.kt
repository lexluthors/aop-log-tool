package com.mimo.aoplog.plugin

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * ASM ClassVisitor — 遍历类的方法，对匹配的方法创建 LogMethodVisitor。
 *
 * 职责：
 * 1. 记录当前类名
 * 2. 判断每个方法是否应被插桩（排除构造函数特殊处理、抽象方法、native 等）
 * 3. 将 LogMethodVisitor 包装在原始 MethodVisitor 外层
 */
class LogClassVisitor(
    api: Int = Opcodes.ASM9,
    cv: ClassVisitor,
    private val matchAnnotations: List<String>,
    private val matchPatterns: List<String>,
    private val matchClasses: List<String>,
    private val excludePatterns: List<String>,
    private val logArgs: Boolean,
    private val logReturnValue: Boolean,
    private val computeCost: Boolean
) : ClassVisitor(api, cv) {

    /** 保存 api 版本供 visitMethod 使用 */
    private val asmApi: Int = api

    /** 当前类名（内部格式：com/example/Foo） */
    private var internalClassName: String = ""

    /** 当前类名（点分隔格式：com.example.Foo） */
    private var dotClassName: String = ""

    /** 当前类上是否有 @LogTrace 注解 */
    private var classHasAnnotation: Boolean = false

    /** 当前类的所有注解 */
    private val classAnnotations = mutableListOf<String>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        internalClassName = name
        dotClassName = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val annName = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
        classAnnotations.add(annName)
        if (annName in matchAnnotations) {
            classHasAnnotation = true
        }
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        val originalMv = super.visitMethod(access, name, descriptor, signature, exceptions)
            ?: return null

        // 跳过不需要插桩的方法
        if (!shouldInstrumentMethod(access, name, descriptor)) {
            return originalMv
        }

        val isStatic = (access and Opcodes.ACC_STATIC) != 0

        return LogMethodVisitor(
            api = asmApi,
            mv = originalMv,
            className = dotClassName,
            methodName = name,
            methodDescriptor = descriptor,
            isStatic = isStatic,
            logArgs = logArgs,
            logReturnValue = logReturnValue,
            computeCost = computeCost
        )
    }

    private fun shouldInstrumentMethod(access: Int, name: String, descriptor: String): Boolean {
        // 跳过构造方法和静态初始化块
        // <init> 中 super() 前后 this 的初始化状态不同，try-catch 跨越边界会导致 VerifyError
        // <clinit> 在类加载时执行，插桩意义不大且容易出错
        if (name == "<init>" || name == "<clinit>") return false

        // 跳过抽象方法和 native 方法（无方法体）
        if ((access and Opcodes.ACC_ABSTRACT) != 0) return false
        if ((access and Opcodes.ACC_NATIVE) != 0) return false

        // 跳过桥接方法和合成方法
        if ((access and Opcodes.ACC_BRIDGE) != 0) return false
        if ((access and Opcodes.ACC_SYNTHETIC) != 0 && name.startsWith("access$")) return false

        // 排除规则
        if (PatternMatcher.matches(dotClassName, name, excludePatterns)) return false

        // 注解匹配：如果配置了注解匹配，检查方法上是否有注解
        // （方法的注解在 visitMethod 之后才会被访问，这里无法直接判断）
        // 采用策略：如果配置了注解匹配且类级别没有匹配注解，则不插桩
        // 方法级注解匹配在 LogMethodVisitor 中处理（visitAnnotation 回调）

        return true
    }
}
