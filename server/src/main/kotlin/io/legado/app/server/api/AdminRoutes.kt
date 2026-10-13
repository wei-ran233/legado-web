package io.legado.app.server.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.legado.app.server.auth.RecoveryCodeRepository
import io.legado.app.server.auth.SessionRepository
import io.legado.app.server.auth.UserRepository
import io.legado.app.server.db.Settings
import io.legado.app.server.db.Users
import io.legado.app.server.repo.BookSourceRepository
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

/**
 * 管理员 API：书源管理（上架/下架） / 用户管理 / 系统统计与设置
 */
fun Route.adminRoutes() {

    route("/admin") {

        // ============ 书源管理 ============
        route("/sources") {
            get {
                call.requireAdmin() ?: return@get
                val enabledOnly = call.request.queryParameters["enabled"] == "true"
                val page = call.request.queryParameters["page"]?.toIntOrNull()
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()
                if (page != null && pageSize != null && page > 0 && pageSize > 0) {
                    val offset = (page - 1) * pageSize
                    val total = BookSourceRepository.count(enabledOnly)
                    val items = BookSourceRepository.list(enabledOnly = enabledOnly, limit = pageSize, offset = offset)
                    call.respondJson(mapOf(
                        "total" to total,
                        "page" to page,
                        "pageSize" to pageSize,
                        "items" to items
                    ))
                } else {
                    call.respondJson(BookSourceRepository.list(enabledOnly = enabledOnly))
                }
            }

            post("/import") {
                call.requireAdmin() ?: return@post
                // 支持单个书源对象或书源数组 JSON
                val text = call.receiveText()
                val trimmed = text.trim()
                val list: List<Map<String, Any?>> = when {
                    trimmed.startsWith("[") ->
                        GSON.fromJsonArray<Map<String, Any?>>(trimmed).getOrNull() ?: emptyList()
                    trimmed.startsWith("{") ->
                        listOf(GSON.fromJsonObject<Map<String, Any?>>(trimmed).getOrNull() ?: emptyMap())
                    else -> emptyList()
                }
                if (list.isEmpty()) {
                    call.respondError(400, "未解析到有效书源（需含 bookSourceUrl 字段）")
                    return@post
                }
                val count = BookSourceRepository.import(list)
                call.respondJson(mapOf("imported" to count))
            }

            post("/enable") {
                call.requireAdmin() ?: return@post
                val body = call.receive<Map<String, Any?>>()
                val urls = (body["urls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (urls.isEmpty()) {
                    call.respondError(400, "缺少 urls")
                    return@post
                }
                BookSourceRepository.setEnabled(urls, enabled = true)
                call.respondJson(mapOf("updated" to urls.size))
            }

            /** 下架：普通用户不可见、不可搜 */
            post("/disable") {
                call.requireAdmin() ?: return@post
                val body = call.receive<Map<String, Any?>>()
                val urls = (body["urls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (urls.isEmpty()) {
                    call.respondError(400, "缺少 urls")
                    return@post
                }
                BookSourceRepository.setEnabled(urls, enabled = false)
                call.respondJson(mapOf("updated" to urls.size))
            }

            post("/delete") {
                call.requireAdmin() ?: return@post
                val body = call.receive<Map<String, Any?>>()
                val urls = (body["urls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (urls.isEmpty()) {
                    call.respondError(400, "缺少 urls")
                    return@post
                }
                BookSourceRepository.delete(urls)
                call.respondJson(mapOf("deleted" to urls.size))
            }

            post("/type") {
                call.requireAdmin() ?: return@post
                val body = call.receive<Map<String, Any?>>()
                val urls = (body["urls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val type = (body["type"] as? Number)?.toInt()
                if (urls.isEmpty() || type == null || type !in 0..4) {
                    call.respondError(400, "参数错误（需包含有效 urls 和 type[0~4]）")
                    return@post
                }
                BookSourceRepository.updateType(urls, type)
                call.respondJson(mapOf("updated" to urls.size, "type" to type))
            }

            get("/{url}") {
                call.requireAdmin() ?: return@get
                val url = java.net.URLDecoder.decode(call.parameters["url"] ?: "", "UTF-8")
                val source = BookSourceRepository.get(url)
                    ?: return@get call.respondError(404, "书源不存在")
                call.respondJson(source)
            }
        }

        // ============ 用户管理 ============
        route("/users") {
            get {
                call.requireAdmin() ?: return@get
                val users = transaction {
                    Users.selectAll().map {
                        mapOf(
                            "id" to it[Users.id],
                            "username" to it[Users.username],
                            "nickname" to it[Users.nickname],
                            "role" to it[Users.role],
                            "totpEnabled" to it[Users.totpEnabled],
                            "disabled" to it[Users.disabled],
                            "createdAt" to it[Users.createdAt],
                        )
                    }
                }
                call.respondJson(users)
            }

            post {
                call.requireAdmin() ?: return@post
                val body = call.receive<Map<String, String>>()
                val username = body["username"]?.trim() ?: ""
                val password = body["password"] ?: ""
                val role = body["role"]?.uppercase() ?: "USER"
                if (username.length < 2) {
                    call.respondError(400, "用户名至少 2 位")
                    return@post
                }
                if (password.length < 8) {
                    call.respondError(400, "初始密码至少 8 位")
                    return@post
                }
                if (role != "USER" && role != "ADMIN") {
                    call.respondError(400, "角色仅支持 USER/ADMIN")
                    return@post
                }
                if (UserRepository().findByUsername(username) != null) {
                    call.respondError(409, "用户名已存在")
                    return@post
                }
                val id = UserRepository().create(username, password, role)
                call.respondJson(mapOf("id" to id, "username" to username, "role" to role))
            }

            patch("/{id}") {
                call.requireAdmin() ?: return@patch
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@patch call.respondError(400, "无效 id")
                val body = call.receive<Map<String, Any?>>()
                val repo = UserRepository()
                val target = repo.findById(id) ?: return@patch call.respondError(404, "用户不存在")
                when (body["action"]) {
                    "disable" -> repo.setDisabled(id, true)
                    "enable" -> repo.setDisabled(id, false)
                    "set_role" -> repo.setRole(id, (body["role"] as? String)?.uppercase() ?: "USER")
                    "reset_password" -> {
                        val pwd = body["password"] as? String ?: ""
                        if (pwd.length < 8) {
                            call.respondError(400, "密码至少 8 位")
                            return@patch
                        }
                        repo.changePassword(id, pwd)
                        SessionRepository().revokeAll(id)
                    }
                    "reset_2fa" -> repo.reset2fa(id)
                    else -> return@patch call.respondError(400, "未知 action")
                }
                call.respondJson(mapOf("status" to "ok"))
            }

            delete("/{id}") {
                call.requireAdmin() ?: return@delete
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@delete call.respondError(400, "无效 id")
                val admin = call.requireAdmin() ?: return@delete
                if (id == admin.id) {
                    call.respondError(400, "不能删除自己")
                    return@delete
                }
                UserRepository().setDisabled(id, true)
                SessionRepository().revokeAll(id)
                call.respondJson(mapOf("status" to "ok"))
            }
        }

        // ============ 统计与设置 ============
        get("/stats") {
            call.requireAdmin() ?: return@get
            call.respondJson(
                mapOf(
                    "users" to UserRepository().count(),
                    "sources" to BookSourceRepository.count(),
                    "sourcesEnabled" to BookSourceRepository.list(enabledOnly = true).size,
                    "books" to io.legado.app.server.repo.BookRepository.all().size,
                )
            )
        }

        get("/settings") {
            call.requireAdmin() ?: return@get
            val settings = transaction {
                Settings.selectAll().associate { it[Settings.key] to (it[Settings.value] ?: "") }
            }
            call.respondJson(settings)
        }

        patch("/settings") {
            call.requireAdmin() ?: return@patch
            val body = call.receive<Map<String, String>>()
            transaction {
                body.forEach { (k, v) ->
                    Settings.upsert(Settings.key) {
                        it[Settings.key] = k
                        it[Settings.value] = v
                    }
                }
            }
            call.respondJson(mapOf("status" to "ok"))
        }

        // ============ 缓存统计与手动维护 ============
        get("/cache/stats") {
            call.requireAdmin() ?: return@get
            call.respondJson(io.legado.app.server.service.CacheCleanService.getStats())
        }

        post("/cache/clean") {
            call.requireAdmin() ?: return@post
            val body = runCatching { call.receiveNullable<Map<String, Any?>>() }.getOrNull() ?: emptyMap()
            val expireDays = (body["expireDays"] as? Number)?.toInt() ?: 7
            val result = io.legado.app.server.service.CacheCleanService.clean(expireDays)
            call.respondJson(result)
        }
    }
}
