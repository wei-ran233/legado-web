package io.legado.app.utils

/**
 * 服务器端 JVM 桩：无 Android 主线程概念，恒返回 true。
 */
val isMainThread: Boolean
    get() = true
