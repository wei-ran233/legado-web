package io.legado.app.help

import java.io.File

/**
 * 引擎运行环境（替代原 Android appCtx 的文件目录能力）。
 * 服务器启动时通过 init() 注入实际数据目录；默认落在系统临时目录。
 */
object AppEnv {

    @Volatile
    var filesDir: File = File(System.getProperty("java.io.tmpdir"), "legado-engine/files")
        private set

    @Volatile
    var cacheDir: File = File(System.getProperty("java.io.tmpdir"), "legado-engine/cache")
        private set

    /** JsExtensions 文件操作的沙箱根目录（对应原 externalCache） */
    @Volatile
    var externalCache: File = File(System.getProperty("java.io.tmpdir"), "legado-engine/external_cache")
        private set

    val externalCachePath: String
        get() = externalCache.absolutePath

    fun init(dataDir: File) {
        filesDir = File(dataDir, "files")
        cacheDir = File(dataDir, "cache")
        externalCache = File(dataDir, "external_cache")
        filesDir.mkdirs()
        cacheDir.mkdirs()
        externalCache.mkdirs()
    }
}
