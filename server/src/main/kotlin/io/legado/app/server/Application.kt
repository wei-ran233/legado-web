package io.legado.app.server

import cn.hutool.crypto.digest.BCrypt
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.gson
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.*
import io.legado.app.help.AppEnv
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.server.api.adminRoutes
import io.legado.app.server.api.authRoutes
import io.legado.app.server.api.libraryRoutes
import io.legado.app.server.api.rankingRoutes
import io.legado.app.server.auth.TokenService
import io.legado.app.server.auth.UserRepository
import io.legado.app.server.service.CacheCleanService
import io.legado.app.server.config.ServerConfig
import io.legado.app.server.config.ServerEnv
import io.legado.app.server.db.DatabaseFactory
import io.legado.app.server.db.EngineDbBinding
import io.legado.app.server.db.KvCache
import io.legado.app.server.help.CachePersistenceExposed
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import java.security.SecureRandom
import java.time.Instant

fun main() {
    val env = ServerEnv.load()
    ServerConfig.init(env)
    AppEnv.init(env.dataDir)

    // 数据库
    DatabaseFactory.init()
    EngineDbBinding.bind()
    CacheManager.persistence = CachePersistenceExposed

    // JWT
    TokenService.init(env.jwtSecret)

    // 首次启动：初始化管理员
    bootstrapAdmin(env)

    // 引擎配置注入
    AppConfig.userAgent = ServerConfig.userAgent
    AppConfig.threadCount = ServerConfig.threadCount

    // 启动缓存定时自动维护与清理
    CacheCleanService.startScheduledCleaner()

    val port = env.port
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

private fun bootstrapAdmin(env: ServerEnv) {
    val repo = UserRepository()
    if (repo.count() > 0) return
    val password = env.adminPassword ?: run {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
    val id = repo.create(env.adminUsername, password, role = "ADMIN")
    println("========================================")
    println("已创建管理员账号: ${env.adminUsername}")
    println("初始密码: $password")
    if (env.adminPassword == null) {
        println("（随机生成，请登录后立即修改）")
    }
    println("管理员 ID: $id")
    println("========================================")
}

fun Application.module() {
    install(ContentNegotiation) {
        gson()
    }

    // 反向代理支持（Nginx/Caddy 后面取真实 IP）
    install(ForwardedHeaders)
    install(XForwardedHeaders)

    install(CORS) {
        // 同源部署为主；开发时前端 Vite 跨域调试
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("API error on ${call.request.uri}", cause)
            call.respondText(
                io.legado.app.utils.GSON.toJson(
                    mapOf("isSuccess" to false, "errorMsg" to (cause.message ?: "服务器内部错误"), "data" to null)
                ),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    routing {
        get("/api/health") {
            call.respondText("""{"isSuccess":true,"errorMsg":"","data":"ok"}""")
        }

        route("/api") {
            authRoutes()
            libraryRoutes()
            adminRoutes()
            rankingRoutes()
        }

        // SPA 静态托管（modules/web 构建产物，启动时解包到 data/webapp）
        staticWebApp()
    }
}

/**
 * SPA 托管：资源可来自 data/webapp 目录（部署时放入前端构建产物）
 */
private fun Application.staticWebApp() {
    val webDir = java.io.File(ServerConfig.dataDir, "webapp")
    if (!webDir.exists()) {
        webDir.mkdirs()
        webDir.resolve("index.html").writeText(
            """<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<title>Legado Server</title></head><body style="font-family:sans-serif;padding:2rem">
<h2>Legado 多用户书城服务器已启动</h2>
<p>前端未部署：请将 modules/web 构建产物（dist）放入 <code>data/webapp/</code>。</p>
<p>API 入口: <a href="/api/health">/api/health</a></p></body></html>"""
        )
    }
    routing {
        staticFiles("/", webDir, index = "index.html")
    }
}
