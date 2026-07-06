package com.mimo.aoplog.runtime

/**
 * AOP 日志处理器接口。
 *
 * 用户实现此接口来定义日志的输出行为（Logcat / 文件 / 网络上报 / 自定义）。
 * 实现类可以引入任何第三方库（Gson、OkHttp、Room 等），框架对实现内容不做限制。
 *
 * 使用方式：
 * 1. 实现此接口
 * 2. 在 Application.onCreate() 中调用 LogManager.init(yourHandler)
 * 3. 在 build.gradle 中通过 aopLog { handlerClass = "..." } 指定 Handler 类名
 *
 * 注意：Handler 自身的方法会被 ASM 插桩排除，不会触发回调，无死循环风险。
 */
interface LogHandler {

    /**
     * 方法进入时回调。
     *
     * @param className   类名（点分隔），如 "com.example.Foo"
     * @param methodName  方法名，如 "doSomething"
     * @param descriptor  方法签名（JVM 格式），如 "(Ljava/lang/String;I)V"
     * @param isStatic    是否静态方法
     * @param instance    实例对象（非静态方法），静态方法为 null
     * @param args        参数数组（按声明顺序），无参或 logArgs=false 时为 null
     */
    fun onEnter(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?
    )

    /**
     * 方法正常返回时回调。
     *
     * @param className   类名
     * @param methodName  方法名
     * @param descriptor  方法签名
     * @param isStatic    是否静态方法
     * @param instance    实例对象，静态方法为 null
     * @param args        参数数组，与 onEnter 相同
     * @param returnValue 返回值，void 方法为 null，logReturnValue=false 时也为 null
     * @param costMs      执行耗时（毫秒），computeCost=false 时为 -1
     */
    fun onExit(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        returnValue: Any?,
        costMs: Long
    )

    /**
     * 方法抛出异常时回调（在异常真正抛出前触发）。
     *
     * @param className   类名
     * @param methodName  方法名
     * @param descriptor  方法签名
     * @param isStatic    是否静态方法
     * @param instance    实例对象，静态方法为 null
     * @param args        参数数组
     * @param throwable   抛出的异常对象
     * @param costMs      执行耗时（毫秒），computeCost=false 时为 -1
     */
    fun onThrow(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        throwable: Throwable,
        costMs: Long
    )
}
