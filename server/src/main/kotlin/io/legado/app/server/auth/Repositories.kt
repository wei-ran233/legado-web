package io.legado.app.server.auth

import cn.hutool.crypto.digest.BCrypt
import io.legado.app.server.db.RecoveryCodes
import io.legado.app.server.db.Sessions
import io.legado.app.server.db.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.Instant

data class UserAccount(
    val id: Long,
    val username: String,
    val nickname: String = "",
    val passwordHash: String,
    val role: String,
    val totpSecret: String?,
    val totpEnabled: Boolean,
    val totpLastStep: Long,
    val tokenVer: Long,
    val disabled: Boolean,
    val mustChangePassword: Boolean,
    val participateRanking: Boolean = true,
)

class UserRepository {
    fun findByUsername(username: String): UserAccount? = transaction {
        Users.selectAll().where { Users.username eq username }.firstOrNull()?.let {
            UserAccount(
                id = it[Users.id],
                username = it[Users.username],
                nickname = it[Users.nickname],
                passwordHash = it[Users.passwordHash],
                role = it[Users.role],
                totpSecret = it[Users.totpSecret],
                totpEnabled = it[Users.totpEnabled],
                totpLastStep = it[Users.totpLastStep],
                tokenVer = it[Users.tokenVer],
                disabled = it[Users.disabled],
                mustChangePassword = it[Users.mustChangePassword],
                participateRanking = it[Users.participateRanking],
            )
        }
    }

    fun findById(id: Long): UserAccount? = transaction {
        Users.selectAll().where { Users.id eq id }.firstOrNull()?.let {
            UserAccount(
                id = it[Users.id],
                username = it[Users.username],
                nickname = it[Users.nickname],
                passwordHash = it[Users.passwordHash],
                role = it[Users.role],
                totpSecret = it[Users.totpSecret],
                totpEnabled = it[Users.totpEnabled],
                totpLastStep = it[Users.totpLastStep],
                tokenVer = it[Users.tokenVer],
                disabled = it[Users.disabled],
                mustChangePassword = it[Users.mustChangePassword],
                participateRanking = it[Users.participateRanking],
            )
        }
    }

    fun count(): Long = transaction {
        Users.selectAll().count()
    }

    fun create(username: String, password: String, role: String, mustChangePassword: Boolean = false): Long =
        transaction {
            Users.insert {
                it[Users.username] = username
                it[passwordHash] = BCrypt.hashpw(password)
                it[Users.role] = role
                it[createdAt] = Instant.now().epochSecond
                it[updatedAt] = Instant.now().epochSecond
                it[Users.mustChangePassword] = mustChangePassword
            } get Users.id
        }

    fun changePassword(userId: Long, newPassword: String) = transaction {
        val current = Users.selectAll().where { Users.id eq userId }
            .firstOrNull()?.get(Users.tokenVer) ?: 0
        Users.update({ Users.id eq userId }) {
            it[passwordHash] = BCrypt.hashpw(newPassword)
            it[tokenVer] = current + 1  // 全局踢出所有已发令牌
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun updateNickname(userId: Long, nickname: String) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.nickname] = nickname
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun updateParticipateRanking(userId: Long, participate: Boolean) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.participateRanking] = participate
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun changeUsername(userId: Long, newUsername: String): Boolean = transaction {
        val existing = Users.selectAll().where { Users.username eq newUsername }.firstOrNull()
        if (existing != null && existing[Users.id] != userId) {
            return@transaction false
        }
        Users.update({ Users.id eq userId }) {
            it[Users.username] = newUsername
            it[updatedAt] = Instant.now().epochSecond
        }
        true
    }

    fun setDisabled(userId: Long, disabled: Boolean) = transaction {
        val current = Users.selectAll().where { Users.id eq userId }
            .firstOrNull()?.get(Users.tokenVer) ?: 0
        Users.update({ Users.id eq userId }) {
            it[Users.disabled] = disabled
            if (disabled) it[tokenVer] = current + 1
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun setRole(userId: Long, role: String) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.role] = role
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun enable2fa(userId: Long, secret: String, step: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.totpSecret] = secret
            it[Users.totpEnabled] = true
            it[Users.totpLastStep] = step
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun disable2fa(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.totpSecret] = null
            it[Users.totpEnabled] = false
            it[Users.totpLastStep] = 0
            it[updatedAt] = Instant.now().epochSecond
        }
    }

    fun markTotpStepUsed(userId: Long, step: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.totpLastStep] = step
        }
    }

    fun reset2fa(userId: Long) = transaction {
        disable2fa(userId)
        RecoveryCodes.deleteWhere { RecoveryCodes.userId eq userId }
    }
}

class SessionRepository {

    fun issue(
        userId: Long,
        device: String,
        ip: String,
        ttlSeconds: Long = 7 * 24 * 3600,
    ): String {
        val refresh = randomToken()
        val now = Instant.now().epochSecond
        transaction {
            Sessions.insert {
                it[id] = java.util.UUID.randomUUID().toString()
                it[Sessions.userId] = userId
                it[refreshHash] = sha256(refresh)
                it[Sessions.device] = device.take(120)
                it[Sessions.ip] = ip.take(60)
                it[createdAt] = now
                it[lastUsedAt] = now
                it[expiresAt] = now + ttlSeconds
            }
        }
        return refresh
    }

    /** 旋转式刷新：校验旧值 -> 作废 -> 发新值；返回 userId 或 null */
    fun rotate(refresh: String, device: String, ip: String): Pair<Long, String>? {
        val hash = sha256(refresh)
        return transaction {
            val row = Sessions.selectAll().where { Sessions.refreshHash eq hash }.firstOrNull()
                ?: return@transaction null
            val id = row[Sessions.id]
            val userId = row[Sessions.userId]
            val expiresAt = row[Sessions.expiresAt]
            val revoked = row[Sessions.revoked]
            val now = Instant.now().epochSecond
            if (revoked || expiresAt < now) {
                Sessions.deleteWhere { Sessions.id eq id }
                return@transaction null
            }
            // 旋转：删除旧会话，建立新会话
            Sessions.deleteWhere { Sessions.id eq id }
            val newRefresh = randomToken()
            Sessions.insert {
                it[Sessions.id] = java.util.UUID.randomUUID().toString()
                it[Sessions.userId] = userId
                it[refreshHash] = sha256(newRefresh)
                it[Sessions.device] = device.take(120)
                it[Sessions.ip] = ip.take(60)
                it[createdAt] = now
                it[lastUsedAt] = now
                it[Sessions.expiresAt] = now + 7 * 24 * 3600
            }
            userId to newRefresh
        }
    }

    fun revoke(refresh: String) = transaction {
        Sessions.deleteWhere { Sessions.refreshHash eq sha256(refresh) }
    }

    fun revokeAll(userId: Long) = transaction {
        Sessions.deleteWhere { Sessions.userId eq userId }
    }

    fun revokeById(sessionId: String, userId: Long) = transaction {
        Sessions.deleteWhere { (Sessions.id eq sessionId) and (Sessions.userId eq userId) }
    }

    fun listByUser(userId: Long): List<Map<String, Any>> = transaction {
        Sessions.selectAll().where { Sessions.userId eq userId }.map {
            mapOf(
                "id" to it[Sessions.id],
                "device" to it[Sessions.device],
                "ip" to it[Sessions.ip],
                "createdAt" to it[Sessions.createdAt],
                "lastUsedAt" to it[Sessions.lastUsedAt],
                "expiresAt" to it[Sessions.expiresAt],
            )
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

class RecoveryCodeRepository {

    fun replaceAll(userId: Long, codes: List<String>) = transaction {
        RecoveryCodes.deleteWhere { RecoveryCodes.userId eq userId }
        codes.forEach { code ->
            RecoveryCodes.insert {
                it[RecoveryCodes.userId] = userId
                it[codeHash] = BCrypt.hashpw(code)
            }
        }
    }

    /** 核销一个恢复码，成功返回 true */
    fun redeem(userId: Long, code: String): Boolean = transaction {
        val rows = RecoveryCodes.selectAll().where { (RecoveryCodes.userId eq userId) and (RecoveryCodes.used eq false) }
        for (row in rows) {
            val hash = row[RecoveryCodes.codeHash]
            if (BCrypt.checkpw(code.trim(), hash)) {
                RecoveryCodes.update({ RecoveryCodes.id eq row[RecoveryCodes.id] }) {
                    it[used] = true
                }
                return@transaction true
            }
        }
        false
    }

    fun countUnused(userId: Long): Long = transaction {
        RecoveryCodes.selectAll()
            .where { (RecoveryCodes.userId eq userId) and (RecoveryCodes.used eq false) }
            .count()
    }
}
