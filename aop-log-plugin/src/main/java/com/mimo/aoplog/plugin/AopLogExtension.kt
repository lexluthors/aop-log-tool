package com.mimo.aoplog.plugin

/**
 * AOP 日志插件 DSL 配置。
 *
 * 使用方式：
 * ```groovy
 * aopLog {
 *     enabled = true
 *     handlerClass = "com.example.DebugLogHandler"
 *     matchAnnotations = ["com.mimo.aoplog.runtime.LogTrace"]
 *     matchPatterns = ["com.gatherad.sdk.source.*.*"]
 * }
 * ```
 */
open class AopLogExtension {
    /** 总开关，release 构建设为 false */
    var enabled: Boolean = true

    /** 用户自定义 Handler 类的全限定名，必须实现 com.mimo.aoplog.runtime.LogHandler */
    var handlerClass: String = ""

    /**
     * 注解匹配 — 方法上有这些注解的才会被插桩。
     * 值为注解的全限定类名。
     */
    var matchAnnotations: List<String> = emptyList()

    /**
     * Pattern 匹配 — 匹配到的类/方法会被插桩。
     * 支持通配符：
     *   - com.example.*          → 包下所有类所有方法
     *   - com.example.**         → 包及子包所有类所有方法
     *   - com.example.Foo.*      → Foo 类所有方法
     *   - com.example.Foo.bar    → 精确匹配某个方法
     */
    var matchPatterns: List<String> = emptyList()

    /**
     * 类名精确匹配 — 整个类的所有方法都会被插桩。
     */
    var matchClasses: List<String> = emptyList()

    /**
     * 排除规则 — 匹配到的类/方法不会被插桩。
     * 语法同 matchPatterns。优先级最高。
     */
    var excludePatterns: List<String> = emptyList()

    /** 是否将方法参数传递给 Handler（false 时 args 为 null，减少装箱开销） */
    var logArgs: Boolean = true

    /** 是否将返回值传递给 Handler */
    var logReturnValue: Boolean = true

    /** 是否计算方法耗时 */
    var computeCost: Boolean = true
}
