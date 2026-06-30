package com.wuling.keyless.service

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object LogRepository {
    private const val LOG_FILE = "keyless_log.txt"
    private const val MAX_LOG_SIZE = 500 * 1024 // 500KB
    private const val MAX_LOG_LINES = 2000

    private var logFile: File? = null
    private var writer: FileWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var lineCount = 0

    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.getExternalFilesDir(null) ?: context.filesDir, LOG_FILE)
        try {
            val existing = logFile!!.readLines()
            lineCount = existing.size
            writer = FileWriter(logFile!!, true)
        } catch (_: Exception) {
            writer = FileWriter(logFile!!, false)
            lineCount = 0
        }
    }

    fun append(tag: String, msg: String) {
        val file = logFile ?: return
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp [$tag] $msg\n"
        scope.launch {
            try {
                writer?.append(line)
                writer?.flush()
                lineCount++

                if (lineCount > MAX_LOG_LINES || file.length() > MAX_LOG_SIZE) {
                    rotate()
                }
            } catch (_: Exception) {}
        }
    }

    fun read(): String {
        val file = logFile ?: return "日志文件未初始化"
        return try { file.readText() } catch (_: Exception) { "读取日志失败" }
    }

    fun getFilePath(): String = logFile?.absolutePath ?: ""

    fun clear() {
        scope.launch {
            try {
                writer?.close()
                logFile?.writeText("")
                writer = FileWriter(logFile!!, true)
                lineCount = 0
            } catch (_: Exception) {}
        }
    }

    private fun rotate() {
        try {
            writer?.close()
            val lines = logFile?.readLines() ?: emptyList()
            val keep = lines.takeLast(MAX_LOG_LINES / 2)
            logFile?.writeText(keep.joinToString("\n") + "\n")
            writer = FileWriter(logFile!!, true)
            lineCount = keep.size
        } catch (_: Exception) {
            logFile?.writeText("")
            writer = FileWriter(logFile!!, true)
            lineCount = 0
        }
    }
}
