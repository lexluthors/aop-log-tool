package com.mimo.aoplog.runtime.handler

import com.mimo.aoplog.runtime.LogHandler

/**
 * 多 Handler 组合器（组合模式）。
 *
 * 同时使用多个 Handler，例如：Logcat + 文件 + 计数。
 * 每个回调依次委托给所有内部 Handler。
 *
 * 使用方式：
 * ```kotlin
 * LogManager.init(CompositeHandler(
 *     LogcatLogHandler(),
 *     FileLogHandler(context),
 *     CountLogHandler()
 * ))
 * ```
 *
 * 注意：某个 Handler 内部抛异常不影响其他 Handler 的执行。
 */
open class CompositeHandler(vararg handlers: LogHandler) : LogHandler {

    private val handlerList = handlers.toList()

    override fun onEnter(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?
    ) {
        for (handler in handlerList) {
            try {
                handler.onEnter(className, methodName, descriptor, isStatic, instance, args)
            } catch (_: Throwable) {
                // 单个 Handler 异常不影响其他
            }
        }
    }

    override fun onExit(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        returnValue: Any?, costMs: Long
    ) {
        for (handler in handlerList) {
            try {
                handler.onExit(className, methodName, descriptor, isStatic, instance, args, returnValue, costMs)
            } catch (_: Throwable) {}
        }
    }

    override fun onThrow(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        throwable: Throwable, costMs: Long
    ) {
        for (handler in handlerList) {
            try {
                handler.onThrow(className, methodName, descriptor, isStatic, instance, args, throwable, costMs)
            } catch (_: Throwable) {}
        }
    }
}
