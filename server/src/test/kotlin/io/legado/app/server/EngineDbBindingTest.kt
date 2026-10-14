package io.legado.app.server

import io.legado.app.data.EngineDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Cookie
import io.legado.app.server.db.*
import io.legado.app.server.repo.BookRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineDbBindingTest {
    @get:Rule val files = TemporaryFolder()

    @Before
    fun bindDatabase() {
        Database.connect("jdbc:sqlite:${files.newFile("engine.db").absolutePath}", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(Books, Chapters, UserBooks, Cookies, BookSources) }
        EngineDbBinding.bind()
    }

    @Test
    fun bookAndChapterDataRoundTripThroughServerDaos() {
        val book = Book(bookUrl = "https://example.com/book", origin = "https://example.com", name = "Book", variable = "{\"k\":\"v\"}")
        book.save()
        assertTrue(EngineDb.bookDao.has(book.bookUrl))
        val restored = EngineDb.bookDao.getBook(book.bookUrl)!!
        assertEquals("v", restored.getVariable("k"))
        restored.name = "Updated"
        restored.save()
        assertEquals("Updated", EngineDb.bookDao.getBook(book.bookUrl)!!.name)

        val chapter = BookChapter(bookUrl = book.bookUrl, url = "chapter/0", title = "One", index = 0, variable = "{\"part\":\"first\"}")
        BookRepository.replaceChapters(book.bookUrl, listOf(chapter))
        assertEquals("first", EngineDb.bookChapterDao.getChapter(book.bookUrl, 0)!!.getVariable("part"))
        EngineDb.bookChapterDao.upWordCount(book.bookUrl, chapter.url, "1000")
        assertEquals("1000", EngineDb.bookChapterDao.getChapterList(book.bookUrl).single().wordCount)
        restored.delete()
        assertFalse(EngineDb.bookDao.has(book.bookUrl))
        assertTrue(EngineDb.bookChapterDao.getChapterList(book.bookUrl).isEmpty())
    }

    @Test
    fun okhttpCookieCleanupPreservesSourceCookies() {
        EngineDb.cookieDao.insert(Cookie("example.com", "session=source"), Cookie("example.com|session", "session=http"))
        assertEquals("session=source", EngineDb.cookieDao.get("example.com")!!.cookie)
        EngineDb.cookieDao.deleteOkHttp()
        assertNull(EngineDb.cookieDao.get("example.com|session"))
        assertEquals("session=source", EngineDb.cookieDao.get("example.com")!!.cookie)
    }
}
