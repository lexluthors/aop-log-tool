package com.mimo.aoplog.runtime

/**
 * LogHandler 的抽象基类，所有回调提供空实现。
 *
 * 继承此类只需覆写关心的回调方法，不用每个都实现。
 * 例如只关心异常：
 * ```kotlin
 * class MyHandler : BaseLogHandler() {
 *     override fun onThrow(...) { ... }
 * }
 * ```
 */
abstract class BaseLogHandler : LogHandler {

    override fun onEnter(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?
    ) {}

    override fun onExit(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        returnValue: Any?,
        costMs: Long
    ) {}

    override fun onThrow(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        throwable: Throwable,
        costMs: Long
    ) {}
}
