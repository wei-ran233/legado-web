package io.legado.app.data

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.ReplaceRule

/** Persistence boundary for the JVM engine. Required DAOs are bound at server startup. */
object EngineDb {
    interface BookSourceDao {
        fun getBookSource(key: String): BookSource?
    }
    interface BookDao {
        fun getBook(bookUrl: String): Book?
        fun getBookByOrigin(name: String, origin: String): Book?
        val all: List<Book>
        fun has(bookUrl: String): Boolean
        fun insert(vararg book: Book)
        fun update(vararg book: Book)
        fun delete(vararg book: Book)
    }
    interface BookChapterDao {
        fun getChapterList(bookUrl: String): List<BookChapter>
        fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter>
        fun getChapter(bookUrl: String, index: Int): BookChapter?
        fun update(vararg bookChapter: BookChapter)
        fun upWordCount(bookUrl: String, url: String, wordCount: String)
    }
    interface CookieDao {
        fun get(url: String): Cookie?
        fun insert(vararg cookie: Cookie)
        fun delete(url: String)
        fun deleteOkHttp()
    }
    interface ReplaceRuleDao {
        fun findEnabledByTitleScope(name: String, origin: String): List<ReplaceRule>
        fun findEnabledByContentScope(name: String, origin: String): List<ReplaceRule>
        fun update(vararg rule: ReplaceRule)
    }
    interface RssSourceDao {
        fun has(origin: String): Boolean
    }

    lateinit var bookSourceDao: BookSourceDao
    lateinit var bookDao: BookDao
    lateinit var bookChapterDao: BookChapterDao
    lateinit var cookieDao: CookieDao

    // This server has no replacement-rule management or RSS repository.
    // Keep replacement rules empty and preserve RSS variable files during cleanup.
    var replaceRuleDao: ReplaceRuleDao = object : ReplaceRuleDao {
        override fun findEnabledByTitleScope(name: String, origin: String) = emptyList<ReplaceRule>()
        override fun findEnabledByContentScope(name: String, origin: String) = emptyList<ReplaceRule>()
        override fun update(vararg rule: ReplaceRule) = Unit
    }
    var rssSourceDao: RssSourceDao = object : RssSourceDao {
        override fun has(origin: String) = true
    }
}
