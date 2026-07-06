package com.mimo.aoplog.runtime

/**
 * AOP 日志切面（ASM 字节码注入的直接目标）。
 *
 * ASM 在目标方法的入口/出口/异常处插入对本类静态方法的调用。
 * 所有方法均通过 LogManager.safeInvoke 提供重入保护和异常隔离。
 *
 * ASM 注入的方法签名（固定，不要修改）：
 * - enter(String, String, String, boolean, Object, Object[])
 * - exit(String, String, String, boolean, Object, Object[], Object, long)
 * - throw_(String, String, String, boolean, Object, Object[], Throwable, long)
 */
object LogAspect {

    /**
     * 方法进入时间记录（ThreadLocal），用于计算耗时。
     * 使用 ArrayDeque 支持同一线程的嵌套调用。
     */
    private val enterTimeStack = ThreadLocal.withInitial { ArrayDeque<Long>() }

    @JvmStatic
    fun enter(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?
    ) {
        // 记录进入时间
        enterTimeStack.get().addLast(System.currentTimeMillis())
        LogManager.safeInvoke { handler ->
            handler.onEnter(className, methodName, descriptor, isStatic, instance, args)
        }
    }

    @JvmStatic
    fun exit(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        returnValue: Any?
    ) {
        // 计算耗时
        val stack = enterTimeStack.get()
        val startTime = if (stack.isNotEmpty()) stack.removeLast() else -1L
        val costMs = if (startTime >= 0) System.currentTimeMillis() - startTime else -1L
        LogManager.safeInvoke { handler ->
            handler.onExit(className, methodName, descriptor, isStatic, instance, args, returnValue, costMs)
        }
    }

    @JvmStatic
    fun throw_(
        className: String,
        methodName: String,
        descriptor: String,
        isStatic: Boolean,
        instance: Any?,
        args: Array<Any?>?,
        throwable: Throwable
    ) {
        // 计算耗时
        val stack = enterTimeStack.get()
        val startTime = if (stack.isNotEmpty()) stack.removeLast() else -1L
        val costMs = if (startTime >= 0) System.currentTimeMillis() - startTime else -1L
        LogManager.safeInvoke { handler ->
            handler.onThrow(className, methodName, descriptor, isStatic, instance, args, throwable, costMs)
        }
    }
}
