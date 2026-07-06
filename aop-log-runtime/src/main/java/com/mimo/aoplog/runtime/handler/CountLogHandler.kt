package com.mimo.aoplog.runtime.handler

import com.mimo.aoplog.runtime.BaseLogHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * 方法调用计数 Handler。
 *
 * 统计每个方法的调用次数、平均耗时、最大/最小耗时，
 * 用于排查重复调用、性能瓶颈等问题。
 *
 * 使用方式：
 * ```kotlin
 * val counter = CountLogHandler()
 * LogManager.init(counter)
 *
 * // 在需要查看统计结果时（如按钮点击、定时任务）
 * Log.d("AopLog", counter.dumpReport())
 * ```
 *
 * 输出示例：
 * ```
 * ==================== AOP 调用统计 ====================
 * CsjSplashAdapter#loadAd          调用 15 次 | avg 234ms | max 1020ms | min 45ms
 * GdtSplashAdapter#loadAd          调用 14 次 | avg 198ms | max  890ms | min 38ms
 * AdManager#requestConfig           调用  1 次 | avg  12ms | max   12ms | min  12ms
 * ======================================================
 * ```
 */
open class CountLogHandler : BaseLogHandler() {

    data class MethodStats(
        val key: String,
        var count: Int = 0,
        var errorCount: Int = 0,
        var totalCostMs: Long = 0,
        var maxCostMs: Long = 0,
        var minCostMs: Long = Long.MAX_VALUE,
        var lastCallTime: Long = 0
    ) {
        val avgCostMs: Long get() = if (count > 0) totalCostMs / count else 0
    }

    private val statsMap = ConcurrentHashMap<String, MethodStats>()

    override fun onExit(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        returnValue: Any?, costMs: Long
    ) {
        record("$className#$methodName", costMs, isError = false)
    }

    override fun onThrow(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        throwable: Throwable, costMs: Long
    ) {
        record("$className#$methodName", costMs, isError = true)
    }

    private fun record(key: String, costMs: Long, isError: Boolean) {
        val stats = statsMap.getOrPut(key) { MethodStats(key = key) }
        synchronized(stats) {
            stats.count++
            if (isError) stats.errorCount++
            if (costMs >= 0) {
                stats.totalCostMs += costMs
                if (costMs > stats.maxCostMs) stats.maxCostMs = costMs
                if (costMs < stats.minCostMs) stats.minCostMs = costMs
            }
            stats.lastCallTime = System.currentTimeMillis()
        }
    }

    /**
     * 生成格式化的统计报告。
     */
    fun dumpReport(): String {
        val sb = StringBuilder("\n==================== AOP 调用统计 ====================\n")
        val sorted = statsMap.values.sortedByDescending { it.count }
        for (stats in sorted) {
            val errorStr = if (stats.errorCount > 0) " | err ${stats.errorCount}" else ""
            sb.append(
                "%-50s 调用 %4d 次%s | avg %5dms | max %5dms | min %5dms\n".format(
                    stats.key, stats.count, errorStr,
                    stats.avgCostMs, stats.maxCostMs,
                    if (stats.minCostMs == Long.MAX_VALUE) 0 else stats.minCostMs
                )
            )
        }
        sb.append("======================================================\n")
        return sb.toString()
    }

    /**
     * 获取原始统计数据（供自定义处理）。
     */
    fun getStats(): Map<String, MethodStats> = statsMap.toMap()

    /**
     * 清空所有统计。
     */
    fun clear() = statsMap.clear()
}
