package io.legado.app.server.db

import io.legado.app.server.config.ServerConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * SQLite 数据库工厂（WAL 模式 + busy_timeout，适合中小规模并发读 + 单写）
 */
object DatabaseFactory {

    fun init() {
        val dbFile = ServerConfig.dbFile
        dbFile.parentFile.mkdirs()
        // PRAGMA 通过连接参数设置（WAL 不能在事务内切换）
        val url = "jdbc:sqlite:${dbFile.absolutePath}?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
        val db = Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
        )
        transaction(db) {
            SchemaUtils.create(
                Users, RecoveryCodes, Sessions, LoginAttempts,
                BookSources, Books, Chapters, UserBooks, UserProgress,
                KvCache, Cookies, Settings, UserReadingRecords
            )
            runCatching { exec("DROP INDEX IF EXISTS idx_books_name_author") }
            exec("CREATE INDEX IF NOT EXISTS idx_books_name_author ON books(name, author)")
            runCatching { exec("ALTER TABLE user_books ADD COLUMN pinned BOOLEAN DEFAULT 0") }
            runCatching { exec("ALTER TABLE users ADD COLUMN nickname VARCHAR(64) DEFAULT ''") }
            runCatching { exec("ALTER TABLE users ADD COLUMN participate_ranking BOOLEAN DEFAULT 1") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN availability_score INTEGER DEFAULT 80") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN success_count INTEGER DEFAULT 0") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN fail_count INTEGER DEFAULT 0") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN has_latest_chapter BOOLEAN DEFAULT 0") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN is_text_clean BOOLEAN DEFAULT 1") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN can_fetch_article BOOLEAN DEFAULT 1") }
            runCatching { exec("ALTER TABLE book_sources ADD COLUMN last_checked_at BIGINT DEFAULT 0") }
            migrateChapterMetadata()
            migrateSourceCategories()
        }
    }

    /** Idempotent migration for databases created before chapter image metadata existed. */
    internal fun migrateChapterMetadata() {
        val columns = mutableSetOf<String>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("PRAGMA table_info(chapters)") { rows ->
            while (rows.next()) columns.add(rows.getString("name"))
        }
        if ("img_url" !in columns) {
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("ALTER TABLE chapters ADD COLUMN img_url TEXT")
        }
    }

    private fun migrateSourceCategories() {
        runCatching {
            val sources = BookSources.selectAll().toList()
            for (row in sources) {
                val currentType = row[BookSources.type]
                val url = row[BookSources.bookSourceUrl]
                val name = row[BookSources.name]
                val grp = row[BookSources.grp]
                val jsonStr = row[BookSources.json]
                val detectedType = io.legado.app.server.repo.BookSourceRepository.inferSourceType(name, grp, jsonStr)
                if (currentType != detectedType) {
                    BookSources.update({ BookSources.bookSourceUrl eq url }) {
                        it[type] = detectedType
                    }
                }
            }
        }
    }
}
