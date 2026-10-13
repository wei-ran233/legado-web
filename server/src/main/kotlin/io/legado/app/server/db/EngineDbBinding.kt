package io.legado.app.server.db

import io.legado.app.data.EngineDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Cookie
import io.legado.app.server.repo.BookRepository
import io.legado.app.server.repo.BookSourceRepository
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * 将 Exposed 实现注入引擎的 EngineDb 抽象，服务器启动时调用。
 */
object EngineDbBinding {

    fun bind() {
        EngineDb.bookSourceDao = object : EngineDb.BookSourceDao {
            override fun getBookSource(key: String): BookSource? =
                BookSourceRepository.get(key)
        }

        EngineDb.bookDao = object : EngineDb.BookDao {
            override fun getBook(bookUrl: String): Book? = BookRepository.get(bookUrl)

            override fun getBookByOrigin(name: String, origin: String): Book? =
                BookRepository.getByNameAuthorOrigin(name, origin)

            override val all: List<Book>
                get() = BookRepository.all()

            override fun has(bookUrl: String): Boolean = BookRepository.has(bookUrl)

            override fun insert(vararg book: Book) {
                book.forEach { BookRepository.upsert(it) }
            }

            override fun update(vararg book: Book) {
                book.forEach { BookRepository.upsert(it) }
            }

            override fun delete(vararg book: Book) {
                book.forEach { BookRepository.delete(it.bookUrl) }
            }
        }

        EngineDb.bookChapterDao = object : EngineDb.BookChapterDao {
            override fun getChapterList(bookUrl: String): List<BookChapter> =
                BookRepository.getChapterList(bookUrl)

            override fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter> =
                BookRepository.getChapterList(bookUrl).filter { it.index in start..end }

            override fun getChapter(bookUrl: String, index: Int): BookChapter? =
                BookRepository.getChapter(bookUrl, index)

            override fun update(vararg bookChapter: BookChapter) {
                // 章节更新由 refreshToc 批量处理
            }

            override fun upWordCount(bookUrl: String, url: String, wordCount: String) {
                BookRepository.upWordCount(bookUrl, url, wordCount)
            }
        }

        EngineDb.cookieDao = object : EngineDb.CookieDao {
            override fun get(url: String): Cookie? = transaction {
                Cookies.selectAll().where { Cookies.domain eq url }.firstOrNull()?.let {
                    Cookie(it[Cookies.domain], it[Cookies.cookie] ?: "")
                }
            }

            override fun insert(vararg cookie: Cookie) {
                transaction {
                    for (c in cookie) {
                        Cookies.upsert(Cookies.domain) { stmt ->
                            stmt[domain] = c.url
                            stmt[Cookies.cookie] = c.cookie
                        }
                    }
                }
            }

            override fun delete(url: String) {
                transaction { Cookies.deleteWhere { Cookies.domain eq url } }
            }
        }
    }
}
