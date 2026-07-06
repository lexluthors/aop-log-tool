package com.mimo.aoplog.plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * ASM ClassVisitor 工厂。
 * AGP 在编译期为每个需要处理的类调用此工厂，创建对应的 LogClassVisitor。
 *
 * 职责：
 * 1. 判断类是否应该被插桩（包名匹配、排除规则等）
 * 2. 将参数从 plugin 传递到 visitor
 */
abstract class LogClassVisitorFactory : AsmClassVisitorFactory<AopLogParams> {

    private fun debugLog(msg: String) {
        java.io.File("/tmp/aop-log-debug.txt").appendText("${System.currentTimeMillis()} $msg\n")
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val params = parameters.get()
        debugLog("createClassVisitor called")
        debugLog("  matchAnnotations=${params.matchAnnotations.get()}")
        debugLog("  matchPatterns=${params.matchPatterns.get()}")
        debugLog("  matchClasses=${params.matchClasses.get()}")
        return LogClassVisitor(
            api = Opcodes.ASM9,
            cv = nextClassVisitor,
            matchAnnotations = params.matchAnnotations.get(),
            matchPatterns = params.matchPatterns.get(),
            matchClasses = params.matchClasses.get(),
            excludePatterns = params.excludePatterns.get(),
            logArgs = params.logArgs.get(),
            logReturnValue = params.logReturnValue.get(),
            computeCost = params.computeCost.get()
        )
    }

    /**
     * 判断给定的类是否应该被插桩。
     * 返回 false 的类会被完全跳过，节省编译时间。
     *
     * @param classData 类信息（AGP 提供）
     * @return true 表示需要插桩
     */
    override fun isInstrumentable(classData: ClassData): Boolean {
        val params = parameters.get()
        val className = classData.className

        // 只记录包含 "demo" 或 "sample" 的类，避免日志太多
        if (className.contains("demo") || className.contains("sample") || className.contains("Sample")) {
            debugLog("isInstrumentable: $className")
            debugLog("  annotations=${classData.classAnnotations}")
            debugLog("  matchAnnotations=${params.matchAnnotations.get()}")
            debugLog("  matchPatterns=${params.matchPatterns.get()}")
        }

        // ① 排除规则优先：命中则直接跳过
        if (PatternMatcher.matchesClass(className, params.excludePatterns.get())) {
            return false
        }

        // ② 排除框架自身（防止死循环）
        if (className.startsWith("com.mimo.aoplog")) {
            return false
        }

        // ③ 注解匹配：类上有 @LogTrace 注解
        val matchAnnotations = params.matchAnnotations.get()
        if (matchAnnotations.isNotEmpty()) {
            for (ann in classData.classAnnotations) {
                // classAnnotations 中的格式为 "Lcom/example/LogTrace;"
                val annName = ann.removePrefix("L").removeSuffix(";").replace('/', '.')
                if (annName in matchAnnotations) return true
            }
        }

        // ④ 类名精确匹配
        if (className in params.matchClasses.get()) {
            return true
        }

        // ⑤ Pattern 匹配（类级别）
        val patternMatch = PatternMatcher.matchesClass(className, params.matchPatterns.get())
        if (patternMatch) {
            if (className.contains("demo") || className.contains("sample") || className.contains("Sample")) {
                debugLog("  → result: TRUE (pattern match)")
            }
            return true
        }

        if (className.contains("demo") || className.contains("sample") || className.contains("Sample")) {
            debugLog("  → result: FALSE (no match)")
        }
        return false
    }
}
