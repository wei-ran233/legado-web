package io.legado.app.constant

import io.legado.app.BuildConfig
import io.legado.app.help.config.AppConfig

/**
 * 服务器端 JVM 版 AppLog（原依赖 android.util.Log / LogUtils 文件日志）。
 */
object AppLog {

    private const val NEWLINE = "\n"

    private val mLogs = arrayListOf<Triple<Long, String, Throwable?>>()

    val logs get() = mLogs.toList()

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (throwable == null) {
            println("AppLog: $message")
        } else {
            println("AppLog: $message" + NEWLINE + throwable.stackTraceToString())
        }
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
    }

    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        println("AppLog: $message")
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (AppConfig.recordLog || BuildConfig.DEBUG) {
            put(message, throwable)
        }
    }
}
