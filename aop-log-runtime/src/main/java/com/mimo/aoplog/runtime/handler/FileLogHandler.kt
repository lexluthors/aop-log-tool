package com.mimo.aoplog.runtime.handler

import android.content.Context
import android.util.Log
import com.mimo.aoplog.runtime.BaseLogHandler
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件输出 Handler。
 *
 * 将日志写入本地文件，适合长时间调试（压测、Monkey 测试等）。
 *
 * 文件路径：{外部存储}/Android/data/{package}/files/aop-log/{yyyy-MM-dd}.log
 * 支持按天分割、文件大小限制、自动清理旧文件。
 *
 * 使用方式：
 * ```kotlin
 * LogManager.init(FileLogHandler(context))
 * ```
 */
open class FileLogHandler(
    private val context: Context,
    private val config: Config = Config()
) : BaseLogHandler() {

    data class Config(
        /** 单个文件最大 MB，超出则滚动创建新文件 */
        val maxFileSizeMb: Int = 50,
        /** 最多保留的日志文件数量，超出自动删除最旧的 */
        val maxTotalFiles: Int = 7,
        /** 缓冲区行数，攒够再 flush（减少 IO） */
        val bufferLines: Int = 50,
        /** 日志子目录名（在 filesDir 下） */
        val subDir: String = "aop-log"
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile
    private var writer: BufferedWriter? = null
    @Volatile
    private var currentFile: File? = null
    @Volatile
    private var currentDate: String = ""
    private var bufferedLineCount = 0
    private val lock = Any()

    override fun onEnter(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?
    ) {
        writeLine("[ENTER] $className#$methodName args=${args?.contentDeepToString()}")
    }

    override fun onExit(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        returnValue: Any?, costMs: Long
    ) {
        writeLine("[EXIT]  $className#$methodName args=${args?.contentDeepToString()} result=$returnValue cost=${costMs}ms")
    }

    override fun onThrow(
        className: String, methodName: String, descriptor: String,
        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
        throwable: Throwable, costMs: Long
    ) {
        writeLine("[THROW] $className#$methodName args=${args?.contentDeepToString()} error=${throwable.javaClass.simpleName}: ${throwable.message}")
    }

    /**
     * 手动 flush 缓冲区到磁盘。
     * 在 Activity.onPause 等时机调用，避免日志丢失。
     */
    fun flush() {
        synchronized(lock) {
            try {
                writer?.flush()
                bufferedLineCount = 0
            } catch (_: Exception) {}
        }
    }

    /**
     * 关闭文件。App 退出时调用。
     */
    fun close() {
        synchronized(lock) {
            try {
                writer?.flush()
                writer?.close()
                writer = null
                currentFile = null
            } catch (_: Exception) {}
        }
    }

    /**
     * 获取当前日志文件路径。
     */
    fun getCurrentFilePath(): String? = currentFile?.absolutePath

    // ──────────────────── 内部实现 ────────────────────

    private fun writeLine(content: String) {
        try {
            ensureWriter()
            val timestamp = dateFormat.format(Date())
            val threadName = Thread.currentThread().name
            synchronized(lock) {
                writer?.write("[$timestamp][$threadName] $content")
                writer?.newLine()
                bufferedLineCount++

                if (bufferedLineCount >= config.bufferLines) {
                    writer?.flush()
                    bufferedLineCount = 0
                }
            }

            // 检查文件大小
            val file = currentFile
            if (file != null && file.length() > config.maxFileSizeMb * 1024L * 1024L) {
                rotateFile()
            }
        } catch (e: Exception) {
            Log.e("AopLog-File", "write error", e)
        }
    }

    private fun ensureWriter() {
        val today = dayFormat.format(Date())
        if (writer == null || today != currentDate) {
            synchronized(lock) {
                if (writer == null || today != currentDate) {
                    openFile(today)
                }
            }
        }
    }

    private fun openFile(dateStr: String) {
        // 关闭旧文件
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {}

        val dir = context.getExternalFilesDir(config.subDir)
            ?: File(context.filesDir, config.subDir)
        dir.mkdirs()

        val file = File(dir, "$dateStr.log")
        currentFile = file
        currentDate = dateStr
        bufferedLineCount = 0

        try {
            writer = BufferedWriter(FileWriter(file, true))
        } catch (e: Exception) {
            Log.e("AopLog-File", "open file error", e)
            writer = null
        }

        cleanOldFiles(dir)
    }

    private fun rotateFile() {
        val file = currentFile ?: return
        val suffix = System.currentTimeMillis()
        val rotated = File(file.parent, "${file.nameWithoutExtension}_${suffix}.log")
        try {
            file.renameTo(rotated)
        } catch (_: Exception) {}
        openFile(currentDate)
    }

    private fun cleanOldFiles(dir: File) {
        try {
            val files = dir.listFiles()?.sortedBy { it.lastModified() }?.toMutableList() ?: return
            while (files.size > config.maxTotalFiles) {
                files.removeFirstOrNull()?.delete()
            }
        } catch (_: Exception) {}
    }
}
