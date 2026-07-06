package com.mimo.aoplog.runtime

/**
 * 标记需要 AOP 日志追踪的方法/构造函数/属性访问器。
 *
 * 使用方式：
 * ```kotlin
 * @LogTrace
 * fun loadData(id: String) { ... }
 *
 * @LogTrace(tag = "网络请求", logArgs = false)
 * fun requestApi() { ... }
 * ```
 *
 * 注解匹配规则由插件 DSL 中 matchAnnotations 配置项控制。
 * 只有当 matchAnnotations 中包含此注解的全限定名时，插桩才会生效。
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogTrace(
    /** 自定义标签，为空则使用方法名 */
    val tag: String = "",
    /** 是否记录方法参数（覆盖全局 logArgs 配置） */
    val logArgs: Boolean = true,
    /** 是否记录返回值（覆盖全局 logReturnValue 配置） */
    val logReturn: Boolean = true
)
