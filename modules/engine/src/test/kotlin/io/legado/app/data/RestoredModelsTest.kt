package io.legado.app.data

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.*
import org.junit.Test

class RestoredModelsTest {
    @Test
    fun sourceAcceptsObjectAndStringRuleFormats() {
        val objectRule = GSON.fromJsonObject<BookSource>(
            """{"bookSourceUrl":"https://example.com","ruleSearch":{"bookList":".books","name":".title"}}"""
        ).getOrThrow()
        val stringRule = GSON.fromJsonObject<BookSource>(
            """{"bookSourceUrl":"https://example.com","ruleSearch":"{\"bookList\":\".books\",\"name\":\".title\"}"}"""
        ).getOrThrow()
        assertEquals(objectRule.getSearchRule(), stringRule.getSearchRule())
        assertEquals(".books", stringRule.getSearchRule().bookList)
    }

    @Test
    fun searchResultBecomesBookWithoutLosingRuleInputs() {
        val search = SearchBook(
            bookUrl = "https://example.com/book/1", origin = "https://example.com",
            name = "A book", author = "An author", variable = "{\"token\":\"value\"}"
        ).apply { infoHtml = "<h1>details</h1>"; tocHtml = "<a>chapter</a>" }
        val book = search.toBook()
        assertEquals(search.bookUrl, book.bookUrl)
        assertEquals(search.origin, book.origin)
        assertEquals(search.infoHtml, book.infoHtml)
        assertEquals(search.tocHtml, book.tocHtml)
        assertEquals("value", book.getVariable("token"))
        val decoded = GSON.fromJsonObject<Book>(GSON.toJson(book)).getOrThrow()
        assertEquals("value", decoded.getVariable("token"))
    }

    @Test
    fun chapterResolvesRelativeAndVolumeLinks() {
        val chapter = BookChapter(url = "chapter/1", baseUrl = "https://example.com/books/", title = "One")
        assertEquals("https://example.com/books/chapter/1", chapter.getAbsoluteURL())
        val volume = chapter.copy(url = "Volume 1", title = "Volume 1", isVolume = true)
        assertEquals(volume.baseUrl, volume.getAbsoluteURL())
    }
}
