package com.mimo.aoplog.runtime.handler

import com.mimo.aoplog.runtime.LogHandler

/**
 * 过滤器包装器（组合模式）。
 *
 * 包装另一个 Handler，按规则过滤事件后再委托。
 * 支持包名白名单/黑名单、类名/方法名正则、最小耗时过滤。
 *
 * 使用方式：
 * ```kotlin
 * LogManager.init(FilterHandler(
 *     delegate = LogcatLogHandler(),
 *     config = FilterHandler.Config(
 *         includePackages = listOf("com.gatherad.sdk"),
 *         minCostMs = 10
 *     )
 * ))
 * ```
 */
open class FilterHandler(
    private val delegate: LogHandler,
    private val config: Config = Config()
) : LogHandler {

    data class Config(
        /** 包名白名单（类名必须以此列表中某项开头），空则不过滤 */
        val includePackages: List<String> = emptyList(),
        /** 包名黑名单（类名以此列表中某项开头则跳过），优先级高于白名单 */
        val excludePackages: List<String> = emptyList(),
        /** 类名正则过滤，空则不过滤 */
        val classRegex: Regex? = null,
        /** 方法名正则过滤，空则不过滤 */
        val methodRegex: Regex? = null,
        /** 最小耗时过滤（ms），低于此耗时的 EXIT 事件不输出 */
        val minCostMs: Long = 0,
        /** 排除的方法（格式 "className#methodName" 或 "className.*"），优先级最高 */
        val excludeMethods: List<String> = emptyList()
    )

    private fun shouldLog(className: String, methodName: String): Boolean {
        val fullKey = "$className#$methodName"

        // 排除方法（优先级最高）
        for (exclude in config.excludeMethods) {
            if (exclude.endsWith(".*")) {
                if (className == exclude.removeSuffix(".*")) return false
            } else if (fullKey == exclude) {
                return false
            }
        }

        // 黑名单
        for (pkg in config.excludePackages) {
            if (className.startsWith(pkg)) return false
        }

        // 白名单（非空时必须命中）
        if (config.includePackages.isNotEmpty()) {
            var matched = false
            for (pkg in config.includePackages) {
                if (className.startsWith(pkg)) {
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }

        // 类名正则
        if (config.classRegex != null && !config.classRegex.matches(className)) return false

        // 方法名正则
        if (config.methodRegex != null && !config.methodRegex.matches(methodName)) return false

        return true
    }

    override fun onEnter(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?
    ) {
        if (shouldLog(className, methodName)) {
            delegate.onEnter(className, methodName, descriptor, isStatic, instance, args)
        }
    }

    override fun onExit(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        returnValue: Any?, costMs: Long
    ) {
        if (shouldLog(className, methodName) && costMs >= config.minCostMs) {
            delegate.onExit(className, methodName, descriptor, isStatic, instance, args, returnValue, costMs)
        }
    }

    override fun onThrow(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        throwable: Throwable, costMs: Long
    ) {
        if (shouldLog(className, methodName)) {
            delegate.onThrow(className, methodName, descriptor, isStatic, instance, args, throwable, costMs)
        }
    }
}
