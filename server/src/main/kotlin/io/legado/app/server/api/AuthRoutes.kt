package io.legado.app.server.api

import cn.hutool.crypto.digest.BCrypt
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.legado.app.server.auth.*

/**
 * 认证与个人账户 API
 */
fun Route.authRoutes() {

    route("/auth") {
        post("/login") {
            val body = call.receive<Map<String, String>>()
            val username = body["username"]?.trim() ?: ""
            val password = body["password"] ?: ""
            val ip = call.request.origin.remoteHost
            val device = call.request.headers["User-Agent"] ?: "unknown"

            LoginRateLimiter.isLocked(ip, username)?.let { until ->
                call.respondError(
                    429,
                    "登录尝试过于频繁，请于 ${until - System.currentTimeMillis() / 1000} 秒后重试"
                )
                return@post
            }

            val user = UserRepository().findByUsername(username)
            if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
                LoginRateLimiter.recordFailure(ip, username)
                call.respondError(401, "用户名或密码错误")
                return@post
            }
            if (user.disabled) {
                call.respondError(403, "账户已被禁用，请联系管理员")
                return@post
            }
            LoginRateLimiter.recordSuccess(ip, username)

            if (user.totpEnabled) {
                val tmp = TokenService.issueTwoFaToken(user.id, user.username)
                call.respondJson(mapOf("status" to "2FA_REQUIRED", "tmp_token" to tmp))
            } else {
                call.respondJson(buildLoginPayload(user, device, ip))
            }
        }

        post("/2fa/verify") {
            val body = call.receive<Map<String, String>>()
            val tmpToken = body["tmp_token"]?.trim() ?: ""
            val rawCode = body["code"]?.trim()?.replace(" ", "")?.replace("-", "") ?: ""
            val ip = call.request.origin.remoteHost
            val device = call.request.headers["User-Agent"] ?: "unknown"

            when (val r = TokenService.verify(tmpToken)) {
                is TokenService.VerifyResult.Invalid -> call.respondError(401, "登录已超时，请重新登录")
                is TokenService.VerifyResult.Valid -> {
                    if (r.stage != "2fa") {
                        call.respondError(400, "无效的登录状态")
                        return@post
                    }
                    val user = UserRepository().findById(r.userId) ?: run {
                        call.respondError(401, "用户不存在")
                        return@post
                    }
                    val secret = user.totpSecret ?: run {
                        call.respondError(400, "2FA 未初始化")
                        return@post
                    }
                    // 先校验 TOTP（防重放与时钟偏差），失败再校验恢复码
                    val step = TotpService.verifyCode(secret, rawCode)
                    val usedRecovery = if (step == null) {
                        RecoveryCodeRepository().redeem(user.id, rawCode.uppercase())
                    } else {
                        if (step <= user.totpLastStep) {
                            call.respondError(401, "动态码已被使用，请等待下一个 30 秒窗口")
                            return@post
                        }
                        UserRepository().markTotpStepUsed(user.id, step)
                        false
                    }
                    if (step == null && !usedRecovery) {
                        call.respondError(401, "验证码或恢复码错误，请重试")
                        return@post
                    }
                    call.respondJson(buildLoginPayload(user, device, ip))
                }
            }
        }

        post("/refresh") {
            val body = call.receive<Map<String, String>>()
            val refresh = body["refresh_token"] ?: ""
            val ip = call.request.origin.remoteHost
            val device = call.request.headers["User-Agent"] ?: "unknown"
            val rotated = SessionRepository().rotate(refresh, device, ip)
                ?: run {
                    call.respondError(401, "会话已失效")
                    return@post
                }
            val (userId, newRefresh) = rotated
            val user = UserRepository().findById(userId) ?: run {
                call.respondError(401, "用户不存在")
                return@post
            }
            if (user.disabled) {
                call.respondError(403, "账户已被禁用")
                return@post
            }
            val access = TokenService.issueAccessToken(user.id, user.username, user.role, user.tokenVer)
            call.respondJson(
                mapOf(
                    "access_token" to access,
                    "refresh_token" to newRefresh,
                    "token_type" to "Bearer",
                    "expires_in" to 900,
                )
            )
        }

        post("/logout") {
            val body = runCatching { call.receive<Map<String, String>>() }.getOrDefault(emptyMap())
            body["refresh_token"]?.let { SessionRepository().revoke(it) }
            call.respondJson(mapOf("status" to "ok"))
        }
    }

    // ============ 个人账户（需登录） ============
    route("/me") {
        get {
            val user = call.requireUser() ?: return@get
            call.respondJson(
                mapOf(
                    "id" to user.id,
                    "username" to user.username,
                    "nickname" to user.nickname,
                    "participate_ranking" to user.participateRanking,
                    "role" to user.role,
                    "totp_enabled" to user.totpEnabled,
                    "recovery_codes_left" to RecoveryCodeRepository().countUnused(user.id),
                    "must_change_password" to user.mustChangePassword,
                )
            )
        }

        // 修改个人设置（展示昵称与榜单参与选项）
        patch("/profile") {
            val user = call.requireUser() ?: return@patch
            val body = call.receive<Map<String, Any?>>()
            val repo = UserRepository()

            var updatedNickname = user.nickname
            if (body.containsKey("nickname")) {
                val nickname = (body["nickname"] as? String)?.trim() ?: ""
                if (nickname.length > 32) {
                    call.respondError(400, "展示名称不能超过 32 个字符")
                    return@patch
                }
                repo.updateNickname(user.id, nickname)
                updatedNickname = nickname
            }

            var updatedParticipate = user.participateRanking
            if (body.containsKey("participate_ranking")) {
                val p = body["participate_ranking"]
                val participate = when (p) {
                    is Boolean -> p
                    is String -> p.toBooleanStrictOrNull() ?: true
                    is Number -> p.toInt() != 0
                    else -> true
                }
                repo.updateParticipateRanking(user.id, participate)
                updatedParticipate = participate
            }

            call.respondJson(
                mapOf(
                    "status" to "ok",
                    "nickname" to updatedNickname,
                    "participate_ranking" to updatedParticipate,
                )
            )
        }

        // 修改登录用户名（账户名）
        patch("/username") {
            val user = call.requireUser() ?: return@patch
            val body = call.receive<Map<String, String>>()
            val newUsername = body["new_username"]?.trim() ?: ""
            val password = body["password"] ?: ""
            if (newUsername.length < 3 || newUsername.length > 32) {
                call.respondError(400, "登录用户名长度需在 3 到 32 个字符之间")
                return@patch
            }
            if (!newUsername.matches(Regex("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))) {
                call.respondError(400, "登录用户名只能包含字母、数字、中文、下划线或短横线")
                return@patch
            }
            if (!BCrypt.checkpw(password, user.passwordHash)) {
                call.respondError(401, "密码错误，无法修改用户名")
                return@patch
            }
            val success = UserRepository().changeUsername(user.id, newUsername)
            if (!success) {
                call.respondError(400, "该登录用户名已被占用，请更换其他名称")
                return@patch
            }
            val updatedUser = UserRepository().findById(user.id)!!
            val newAccessToken = TokenService.issueAccessToken(
                updatedUser.id,
                updatedUser.username,
                updatedUser.role,
                updatedUser.tokenVer
            )
            call.respondJson(
                mapOf(
                    "status" to "ok",
                    "username" to newUsername,
                    "access_token" to newAccessToken,
                )
            )
        }

        patch("/password") {
            val user = call.requireUser() ?: return@patch
            val body = call.receive<Map<String, String>>()
            val oldPassword = body["old_password"] ?: ""
            val newPassword = body["new_password"] ?: ""
            if (newPassword.length < 8) {
                call.respondError(400, "新密码至少 8 位")
                return@patch
            }
            if (!BCrypt.checkpw(oldPassword, user.passwordHash)) {
                call.respondError(401, "原密码错误")
                return@patch
            }
            UserRepository().changePassword(user.id, newPassword)
            SessionRepository().revokeAll(user.id) // 改密后全端下线
            call.respondJson(mapOf("status" to "ok"))
        }

        // ===== 2FA 绑定向导 =====
        post("/2fa/setup") {
            val user = call.requireUser() ?: return@post
            if (user.totpEnabled) {
                call.respondError(400, "2FA 已启用")
                return@post
            }
            val secret = TotpService.generateSecret()
            call.respondJson(
                mapOf(
                    "secret" to secret,
                    "otpauth_uri" to TotpService.otpauthUri(user.username, secret),
                )
            )
        }

        post("/2fa/enable") {
            val user = call.requireUser() ?: return@post
            val body = call.receive<Map<String, String>>()
            val secret = body["secret"]?.trim()?.replace(" ", "")?.replace("-", "") ?: ""
            val code = body["code"]?.trim()?.replace(" ", "")?.replace("-", "") ?: ""
            if (secret.isBlank()) {
                call.respondError(400, "缺少 2FA 密钥")
                return@post
            }
            if (code.length != 6 || !code.all { it.isDigit() }) {
                call.respondError(400, "请输入 6 位纯数字动态验证码")
                return@post
            }
            val step = TotpService.verifyCode(secret, code)
                ?: run {
                    call.respondError(400, "动态码错误或已过期，请核对手机时间后重试")
                    return@post
                }
            val codes = TotpService.generateRecoveryCodes()
            UserRepository().enable2fa(user.id, secret, step)
            RecoveryCodeRepository().replaceAll(user.id, codes)
            call.respondJson(mapOf("recovery_codes" to codes))
        }

        post("/2fa/disable") {
            val user = call.requireUser() ?: return@post
            val body = call.receive<Map<String, String>>()
            val password = body["password"] ?: ""
            if (!BCrypt.checkpw(password, user.passwordHash)) {
                call.respondError(401, "密码错误")
                return@post
            }
            UserRepository().disable2fa(user.id)
            call.respondJson(mapOf("status" to "ok"))
        }

        // ===== 会话管理 =====
        get("/sessions") {
            val user = call.requireUser() ?: return@get
            call.respondJson(SessionRepository().listByUser(user.id))
        }

        delete("/sessions/{id}") {
            val user = call.requireUser() ?: return@delete
            val id = call.parameters["id"] ?: return@delete
            SessionRepository().revokeById(id, user.id)
            call.respondJson(mapOf("status" to "ok"))
        }
    }
}

/** 组装登录成功响应 */
fun buildLoginPayload(
    user: io.legado.app.server.auth.UserAccount,
    device: String,
    ip: String,
): Map<String, Any> {
    val access = TokenService.issueAccessToken(user.id, user.username, user.role, user.tokenVer)
    val refresh = SessionRepository().issue(user.id, device, ip)
    return mapOf(
        "status" to "OK",
        "access_token" to access,
        "refresh_token" to refresh,
        "token_type" to "Bearer",
        "expires_in" to 900,
        "role" to user.role,
        "username" to user.username,
        "nickname" to user.nickname,
    )
}
