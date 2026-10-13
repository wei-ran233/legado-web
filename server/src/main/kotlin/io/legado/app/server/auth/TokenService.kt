package io.legado.app.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.legado.app.server.config.ServerConfig
import java.util.Date

/**
 * JWT 访问令牌服务
 * claims: uid / username / role / ver(密码版本号) / stage(2fa 中间态标记)
 */
object TokenService {

    private const val ISSUER = "legado-server"
    private const val ACCESS_TTL_MS = 15 * 60 * 1000L
    private const val TWOFA_TTL_MS = 5 * 60 * 1000L

    private lateinit var algorithm: Algorithm
    private lateinit var verifier: JWTVerifier

    fun init(secret: String) {
        algorithm = Algorithm.HMAC256(secret)
        verifier = JWT.require(algorithm)
            .withIssuer(ISSUER)
            .build()
    }

    fun issueAccessToken(userId: Long, username: String, role: String, tokenVer: Long): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(ISSUER)
            .withClaim("uid", userId)
            .withClaim("username", username)
            .withClaim("role", role)
            .withClaim("ver", tokenVer)
            .withClaim("stage", "full")
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ACCESS_TTL_MS))
            .sign(algorithm)
    }

    /** 2FA 中间态令牌：仅允许调用 2fa/verify 接口 */
    fun issueTwoFaToken(userId: Long, username: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(ISSUER)
            .withClaim("uid", userId)
            .withClaim("username", username)
            .withClaim("stage", "2fa")
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + TWOFA_TTL_MS))
            .sign(algorithm)
    }

    sealed class VerifyResult {
        data class Valid(
            val userId: Long,
            val username: String,
            val role: String,
            val tokenVer: Long,
            val stage: String,
        ) : VerifyResult()

        data object Invalid : VerifyResult()
    }

    fun verify(token: String): VerifyResult {
        return try {
            val jwt = verifier.verify(token)
            val uid = jwt.getClaim("uid").asLong() ?: return VerifyResult.Invalid
            VerifyResult.Valid(
                userId = uid,
                username = jwt.getClaim("username").asString() ?: "",
                role = jwt.getClaim("role").asString() ?: "USER",
                tokenVer = jwt.getClaim("ver").asLong() ?: -1,
                stage = jwt.getClaim("stage").asString() ?: "full",
            )
        } catch (e: JWTVerificationException) {
            VerifyResult.Invalid
        }
    }
}
