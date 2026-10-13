package io.legado.app.server.config

import java.io.File

/**
 * 服务器运行环境配置。环境变量优先，其次 data 目录下的 application.yml（暂未实现 yml，先只读环境变量）。
 */
data class ServerEnv(
    val port: Int,
    val dataDir: File,
    val jwtSecret: String,
    val adminUsername: String,
    val adminPassword: String?,
    val openRegister: Boolean,
) {
    companion object {
        fun load(): ServerEnv {
            val dataDir = File(System.getenv("DATA_DIR") ?: "data").absoluteFile
            val jwtSecret = System.getenv("JWT_SECRET")
                ?: throw IllegalStateException("必须设置 JWT_SECRET 环境变量（建议 64 位以上随机字符串）")
            return ServerEnv(
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                dataDir = dataDir,
                jwtSecret = jwtSecret,
                adminUsername = System.getenv("ADMIN_USERNAME") ?: "admin",
                adminPassword = System.getenv("ADMIN_PASSWORD"),
                openRegister = System.getenv("OPEN_REGISTER") == "true",
            )
        }
    }
}

/**
 * 全局配置单例，引擎（modules/engine）通过 ServerConfig 获取原本来自 Android AppConfig 的配置。
 */
object ServerConfig {
    lateinit var env: ServerEnv
        private set

    val dataDir: File get() = env.dataDir
    val dbFile: File get() = File(env.dataDir, "legado-server.db")
    val bookCacheDir: File get() = File(env.dataDir, "book_cache")
    val imageCacheDir: File get() = File(env.dataDir, "images")
    val jsLibDir: File get() = File(env.dataDir, "shareJs")

    /** 书源搜索并发线程数 */
    @Volatile
    var threadCount: Int = 16

    /** 默认 User-Agent */
    @Volatile
    var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun init(env: ServerEnv) {
        this.env = env
        env.dataDir.mkdirs()
        bookCacheDir.mkdirs()
        imageCacheDir.mkdirs()
        jsLibDir.mkdirs()
    }
}
