package io.legado.app.server.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.legado.app.server.auth.SessionRepository
import io.legado.app.server.auth.TokenService
import io.legado.app.server.auth.UserRepository

private data class Principal(
    val userId: Long,
    val username: String,
    val role: String,
    val tokenVer: Long,
)

/**
 * Bearer JWT 校验扩展：返回 null 表示未认证（路由内自行 respondError 401）
 */
suspend fun ApplicationCall.requireUser(): io.legado.app.server.auth.UserAccount? {
    val header = request.headers["Authorization"] ?: ""
    // sendBeacon 无法带 header，支持 ?token= 查询参数（仅 POST /api/progress 使用）
    val token = if (header.startsWith("Bearer ")) header.substring(7)
        else request.queryParameters["token"] ?: ""
    if (token.isBlank()) {
        respondError(401, "未登录")
        return null
    }
    val r = TokenService.verify(token)
    if (r !is TokenService.VerifyResult.Valid) {
        respondError(401, "登录已过期")
        return null
    }
    if (r.stage != "full") {
        respondError(401, "请先完成两步验证")
        return null
    }
    val user = UserRepository().findById(r.userId)
    if (user == null || user.disabled) {
        respondError(403, "账户不可用")
        return null
    }
    if (user.tokenVer != r.tokenVer) {
        respondError(401, "凭证已失效（密码已修改或账户被禁用），请重新登录")
        return null
    }
    return user
}

suspend fun ApplicationCall.requireAdmin(): io.legado.app.server.auth.UserAccount? {
    val user = requireUser() ?: return null
    if (user.role != "ADMIN") {
        respondError(403, "需要管理员权限")
        return null
    }
    return user
}
