package com.mimo.aoplog.runtime

/**
 * 方法生命周期阶段枚举。
 * 用于 LogHandler 回调中标识当前是方法进入、正常退出、还是异常退出。
 */
enum class MethodPhase {
    /** 方法进入（方法体开始执行前） */
    ENTER,
    /** 方法正常返回（包括 void 方法） */
    EXIT,
    /** 方法抛出异常（异常尚未传播前） */
    THROW
}
