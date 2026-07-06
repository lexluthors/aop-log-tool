package com.mimo.aoplog.runtime

/**
 * AOP 日志管理器（单例）。
 *
 * 职责：
 * 1. 持有用户注册的 LogHandler 实例
 * 2. 提供重入保护（ThreadLocal），防止 Handler 自身方法被插桩后死循环
 * 3. 捕获 Handler 内部异常，绝不让日志逻辑影响业务代码
 */
object LogManager {

    @Volatile
    private var handler: LogHandler? = null

    /**
     * 重入保护标记。
     * 当前线程正在执行 Handler 回调时为 true，此时再次进入 LogAspect 会直接跳过，
     * 避免 Handler 内部调用的方法匹配了插桩规则导致的无限递归。
     */
    private val inHandler: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    /**
     * 初始化，注册日志处理器。
     * 应在 Application.onCreate() 中调用，且在业务代码执行前完成。
     *
     * @param handler 日志处理器实现，可以是自定义类或内置 Handler
     */
    fun init(handler: LogHandler) {
        this.handler = handler
    }

    /**
     * 获取当前注册的处理器。
     * 未初始化或已销毁时返回 null。
     */
    fun getHandler(): LogHandler? = handler

    /**
     * 注销处理器，停止所有日志输出。
     */
    fun destroy() {
        handler = null
    }

    /**
     * 判断当前是否已初始化。
     */
    val isInitialized: Boolean
        get() = handler != null

    /**
     * 以重入保护方式执行回调（供 LogAspect 调用）。
     *
     * 保护层级：
     * - 编译期：ASM 排除 Handler 类及其所在包，不产生插桩代码
     * - 运行期（本方法）：ThreadLocal 兜底，覆盖编译期遗漏的情况
     *
     * Handler 内部的任何异常都会被捕获并输出到 Log.e，绝不影响业务逻辑。
     */
    @JvmStatic
    fun safeInvoke(action: (LogHandler) -> Unit) {
        val h = handler ?: return
        if (inHandler.get()) return
        inHandler.set(true)
        try {
            action(h)
        } catch (e: Throwable) {
            // Handler 内部异常绝不影响业务
            try {
                android.util.Log.e("AopLog", "Handler error: ${e.message}", e)
            } catch (_: Throwable) {
                // Log.e 本身也可能失败（极端情况），忽略
            }
        } finally {
            inHandler.set(false)
        }
    }
}
