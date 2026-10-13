package io.legado.app.server.help

import io.legado.app.help.CacheEntry
import io.legado.app.help.CachePersistence
import io.legado.app.server.db.KvCache
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

/**
 * 引擎 CacheManager 的 Exposed 持久化后端
 */
object CachePersistenceExposed : CachePersistence {

    override fun get(key: String): CacheEntry? = transaction {
        KvCache.selectAll().where { KvCache.key eq key }.firstOrNull()?.let {
            CacheEntry(it[KvCache.key], it[KvCache.value], it[KvCache.deadline])
        }
    }

    override fun insert(key: String, value: String, deadline: Long) {
        transaction {
            KvCache.upsert(KvCache.key) {
                it[KvCache.key] = key
                it[KvCache.value] = value
                it[KvCache.deadline] = deadline
            }
        }
    }

    override fun delete(key: String) {
        transaction {
            KvCache.deleteWhere { KvCache.key eq key }
        }
    }
}
