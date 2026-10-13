package io.legado.app.server.auth

import io.legado.app.server.db.LoginAttempts
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * 登录防爆破：按 (IP + 用户名) 维度计数，5 次失败锁 15 分钟
 */
object LoginRateLimiter {

    private const val MAX_FAILS = 5
    private const val LOCK_SECONDS = 15 * 60L

    fun isLocked(ip: String, username: String): Long? {
        val key = attemptKey(ip, username)
        val now = Instant.now().epochSecond
        return transaction {
            val row = LoginAttempts.selectAll().where { LoginAttempts.key eq key }.firstOrNull()
            val lockedUntil = row?.get(LoginAttempts.lockedUntil) ?: 0
            if (lockedUntil > now) lockedUntil else null
        }
    }

    fun recordFailure(ip: String, username: String) {
        val key = attemptKey(ip, username)
        val now = Instant.now().epochSecond
        transaction {
            val row = LoginAttempts.selectAll().where { LoginAttempts.key eq key }.firstOrNull()
            if (row == null) {
                LoginAttempts.insert {
                    it[LoginAttempts.key] = key
                    it[failCount] = 1
                    it[lockedUntil] = 0
                    it[updatedAt] = now
                }
            } else {
                val fails = row[LoginAttempts.failCount] + 1
                val lock = if (fails >= MAX_FAILS) now + LOCK_SECONDS else 0
                LoginAttempts.update({ LoginAttempts.key eq key }) {
                    it[failCount] = fails
                    it[lockedUntil] = lock
                    it[updatedAt] = now
                }
            }
        }
    }

    fun recordSuccess(ip: String, username: String) {
        val key = attemptKey(ip, username)
        transaction {
            LoginAttempts.deleteWhere { LoginAttempts.key eq key }
        }
    }

    private fun attemptKey(ip: String, username: String): String = "$ip|$username"
}
