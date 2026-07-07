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

        // 方法级匹配：如果 pattern 指定了具体方法名，只插桩匹配的方法
        // 例如 "com.example.Foo.onCreate" 只插桩 onCreate，不插桩 Foo 的其他方法
        if (matchPatterns.isNotEmpty()) {
            val hasMethodSpecificPattern = matchPatterns.any { pattern ->
                val classPattern = extractClassPart(pattern)
                // 如果 pattern 的类名部分能匹配当前类，且方法名部分不是通配符
                PatternMatcher.matchesClassSinglePublic(dotClassName, classPattern) &&
                    extractMethodPart(pattern) != "*" && extractMethodPart(pattern) != "**"
            }
            if (hasMethodSpecificPattern) {
                // 只匹配指定了方法名的 pattern
                val matched = matchPatterns.any { pattern ->
                    val methodPart = extractMethodPart(pattern)
                    (methodPart == "*" || methodPart == "**" || methodPart == name) &&
                        PatternMatcher.matchesClassSinglePublic(
                            dotClassName, extractClassPart(pattern)
                        )
                }
                if (!matched) return false
            }
        }

        return true
    }

    /** 从 pattern 中提取类名部分（和 PatternMatcher.extractClassPattern 逻辑一致） */
    private fun extractClassPart(pattern: String): String {
        if (pattern.endsWith(".**")) return pattern
        if (pattern.endsWith(".*")) {
            val withoutLast = pattern.dropLast(2)
            if (withoutLast.endsWith(".*") || withoutLast.endsWith(".**")) return withoutLast
            val lastDot = withoutLast.lastIndexOf('.')
            if (lastDot >= 0) {
                val seg = withoutLast.substring(lastDot + 1)
                if (seg.contains("*")) return withoutLast
            }
            return withoutLast
        }
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot >= 0) {
            val seg = pattern.substring(lastDot + 1)
            if (seg.isNotEmpty() && seg[0].isLowerCase()) return pattern.substring(0, lastDot)
        }
        return pattern
    }

    /** 从 pattern 中提取方法名部分 */
    private fun extractMethodPart(pattern: String): String {
        if (pattern.endsWith(".**")) return "**"
        if (pattern.endsWith(".*")) return "*"
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot >= 0) {
            val seg = pattern.substring(lastDot + 1)
            if (seg.isNotEmpty() && seg[0].isLowerCase()) return seg
        }
        return "*"  // 没有方法名部分，匹配所有方法
    }
}
