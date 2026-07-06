package com.mimo.aoplog.runtime.handler

import android.util.Log
import com.mimo.aoplog.runtime.BaseLogHandler

/**
 * Logcat 格式化输出 Handler。
 *
 * 输出示例：
 * ```
 * D/AopLog: [ENTER] CsjSplashAdapter#loadAd | args: [slotId=1, size=1080x1920] | T: main
 * D/AopLog: [EXIT]  CsjSplashAdapter#loadAd | args: [...] | result: AdInfo{...} | cost: 234ms
 * D/AopLog: [THROW] GdtSplashAdapter#loadAd | args: [...] | error: TimeoutException
 *     at com.gatherad.sdk...
 * ```
 *
 * 特性：
 * - 参数安全序列化（防 OOM，支持截断）
 * - 可选打印调用栈（排查"谁调了我"）
 * - 可配置日志级别和标签
 * - 异常自动输出完整 stacktrace
 */
open class LogcatLogHandler(
    private val config: Config = Config()
) : BaseLogHandler() {

    data class Config(
        /** 是否显示线程名 */
        val showThread: Boolean = true,
        /** 是否显示耗时 */
        val showCost: Boolean = true,
        /** 是否打印调用栈（排查"谁调了我"） */
        val showCallStack: Boolean = false,
        /** 调用栈过滤关键字，只保留包含这些字符串的栈帧；空则显示全部 */
        val callStackFilter: List<String> = emptyList(),
        /** 参数/返回值序列化最大字符数，超出截断 */
        val maxArgLength: Int = 500,
        /** Logcat 标签 */
        val tag: String = "AopLog",
        /** 日志级别：Log.VERBOSE / DEBUG / INFO / WARN / ERROR */
        val logLevel: Int = Log.DEBUG
    )

    override fun onEnter(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?
    ) {
        val sb = StringBuilder()
        sb.append("[ENTER] ${simpleClassName(className)}#$methodName")

        if (!args.isNullOrEmpty()) {
            sb.append(" | args: [").append(formatArgs(args)).append("]")
        }

        if (config.showThread) {
            sb.append(" | T: ").append(Thread.currentThread().name)
        }

        if (config.showCallStack) {
            sb.append("\n").append(formatCallStack())
        }

        Log.println(config.logLevel, config.tag, sb.toString())
    }

    override fun onExit(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        returnValue: Any?, costMs: Long
    ) {
        val sb = StringBuilder()
        sb.append("[EXIT]  ${simpleClassName(className)}#$methodName")

        if (!args.isNullOrEmpty()) {
            sb.append(" | args: [").append(formatArgs(args)).append("]")
        }

        if (returnValue != null) {
            sb.append(" | result: ").append(safeToString(returnValue))
        }

        if (config.showCost && costMs >= 0) {
            sb.append(" | cost: ").append(costMs).append("ms")
        }

        if (config.showThread) {
            sb.append(" | T: ").append(Thread.currentThread().name)
        }

        Log.println(config.logLevel, config.tag, sb.toString())
    }

    override fun onThrow(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        throwable: Throwable, costMs: Long
    ) {
        val sb = StringBuilder()
        sb.append("[THROW] ${simpleClassName(className)}#$methodName")

        if (!args.isNullOrEmpty()) {
            sb.append(" | args: [").append(formatArgs(args)).append("]")
        }

        sb.append(" | error: ").append(throwable.javaClass.simpleName)
        if (throwable.message != null) {
            sb.append(": ").append(throwable.message)
        }

        if (config.showCost && costMs >= 0) {
            sb.append(" | cost: ").append(costMs).append("ms")
        }

        Log.println(config.logLevel, config.tag, sb.toString())
        // 同时输出完整异常栈
        Log.e(config.tag, "${simpleClassName(className)}#$methodName stacktrace:", throwable)
    }

    // ──────────────────── 工具方法 ────────────────────

    private fun simpleClassName(fullName: String): String =
        fullName.substringAfterLast('.')

    private fun formatArgs(args: Array<Any?>): String {
        val maxTotal = config.maxArgLength
        return args.joinToString(", ") { safeToString(it) }
            .let { if (it.length > maxTotal) it.substring(0, maxTotal) + "..." else it }
    }

    private fun safeToString(obj: Any?): String {
        val max = config.maxArgLength
        return try {
            when (obj) {
                null -> "null"
                is String -> "\"${obj.take(max)}${if (obj.length > max) "..." else ""}\""
                is ByteArray -> "ByteArray(${obj.size})"
                is IntArray -> "IntArray(${obj.size})=${obj.contentToString().take(max)}"
                is LongArray -> "LongArray(${obj.size})"
                is FloatArray -> "FloatArray(${obj.size})"
                is DoubleArray -> "DoubleArray(${obj.size})"
                is BooleanArray -> "BooleanArray(${obj.size})"
                is Array<*> -> "[${obj.joinToString(", ") { safeToString(it) }}]".take(max)
                else -> {
                    val str = obj.toString()
                    if (str.length > max) str.substring(0, max) + "..." else str
                }
            }
        } catch (e: Exception) {
            "<error: ${e.message}>"
        }
    }

    private fun formatCallStack(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace
            .filter { frame ->
                val cls = frame.className
                // 排除框架自身和 JDK 反射
                !cls.startsWith("com.mimo.aoplog") &&
                !cls.startsWith("java.lang.reflect") &&
                !cls.startsWith("dalvik.") &&
                !cls.startsWith("android.os.") &&
                (config.callStackFilter.isEmpty() || config.callStackFilter.any { cls.contains(it) })
            }
            .joinToString("\n") { "    at $it" }
    }
}
