package io.legado.app.utils

/**
 * 服务器端 JVM 版 DebugLog（原依赖 android.util.Log）。
 */
object DebugLog {
    @JvmStatic
    fun d(tag: String, msg: String) {
        println("D/$tag: $msg")
    }

    @JvmStatic
    fun e(tag: String, msg: Any, throwable: Throwable? = null) {
        val detail = when (msg) {
            is String -> msg
            else -> msg.toString()
        }
        println("E/$tag: $detail ${throwable?.let { it.stackTraceToString() } ?: ""}")
    }
}
