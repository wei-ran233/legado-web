package io.legado.app.help.config

import io.legado.app.constant.PreferKey

/**
 * 服务器端 JVM 版 AppConfig（原为 Android SharedPreferences 实现）。
 * 仅保留 modules/engine 移植代码所需的属性；值保存在内存，由 ServerConfig 在启动时注入。
 */
@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig {
    val isCronet = false

    @Volatile
    var userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @Volatile
    var themeMode = "0"

    @Volatile
    var recordLog = false

    @Volatile
    var adaptSpecialStyle = true

    @Volatile
    var chineseConverterType = 0

    @Volatile
    var threadCount = 32

    @Volatile
    var tocCountWords = true

    @Volatile
    var preDownloadNum = 2

    @Volatile
    var imageRetainNum = 0

    @Volatile
    var replaceEnableDefault = true

    var bookExportFileName: String? = null
    var episodeExportFileName: String? = null
    var defaultBookTreeUri: String? = null
    var importBookPath: String? = null

    /** DNS 解析缓存（服务器端保持内存缓存语义） */
    @Volatile
    private var _addressCache: Map<String, List<java.net.InetAddress>>? = null
    val addressCache: Map<String, List<java.net.InetAddress>>
        get() = _addressCache ?: emptyMap()

    fun putAddressCache(key: String, ips: List<java.net.InetAddress>) {
        _addressCache = _addressCache?.plus(key to ips) ?: mapOf(key to ips)
    }

    val isNightMode: Boolean
        get() = when (themeMode) {
            "2" -> true
            else -> false
        }

    fun getProperty(key: String): Any? = when (key) {
        PreferKey.userAgent -> userAgent
        PreferKey.threadCount -> threadCount
        PreferKey.themeMode -> themeMode
        PreferKey.recordLog -> recordLog
        else -> null
    }
}
