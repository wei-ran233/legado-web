package io.legado.app.server

import io.legado.app.data.entities.SearchBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Test

class PrepareBookTest {
    @Test
    fun testDeserializeSearchBook() {
        val map = mapOf<String, Any?>(
            "name" to "尸兄(我叫白小飞)",
            "author" to "七度鱼",
            "kind" to "热血,搞笑",
            "intro" to "...",
            "coverUrl" to "https://example.com/cover.jpg",
            "latestChapter" to "第500话",
            "latestChapterTitle" to "第500话",
            "bookUrl" to "https://m.g-mh.org/manga/shixiongwojiaobaixiaofei-qiduyu",
            "origin" to "https://m.g-mh.org/",
            "originName" to "动漫之家",
            "type" to 2.0,
            "sourceType" to 2.0,
            "availabilityScore" to 95.0
        )
        
        val jsonFromMap = GSON.toJson(map)
        println("JSON from Map: $jsonFromMap")
        val res = GSON.fromJsonObject<SearchBook>(jsonFromMap)
        println("Result isSuccess: ${res.isSuccess}")
        org.junit.Assert.assertTrue(res.isSuccess)
    }

    @Test
    fun testEdgeCases() {
        val testCases = listOf(
            "type is string" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to "d", "type" to "2"),
            "wordCount is int" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to "d", "wordCount" to 123),
            "variable is map" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to "d", "variable" to mapOf("k" to "v")),
            "origin is null" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to null),
            "name is null" to mapOf("bookUrl" to "http://a.com", "name" to null, "author" to "c", "origin" to "d"),
            "wrapped in rawBook" to mapOf("rawBook" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to "d")),
            "bookSourceUrl instead of origin" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "bookSourceUrl" to "http://a.com"),
            "time is string" to mapOf("bookUrl" to "http://a.com", "name" to "b", "author" to "c", "origin" to "d", "time" to "1789486209")
        )

        for ((desc, map) in testCases) {
            val json = GSON.toJson(map)
            val res = GSON.fromJsonObject<SearchBook>(json)
            println("Test case [$desc]: isSuccess=${res.isSuccess}, error=${res.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun testParseSearchBookFromMap() {
        val wrapped = mapOf(
            "rawBook" to mapOf(
                "name" to "尸兄(我叫白小飞)",
                "author" to "七度鱼",
                "bookUrl" to "https://cn.bzmanga.com/comic/shixiongwojiaobaixiaofei-qiduyu",
                "bookSourceUrl" to "https://cn.bzmanga.com",
                "sourceType" to "2",
                "latestChapter" to "第500话"
            )
        )
        val sb = io.legado.app.server.api.parseSearchBookFromMap(wrapped)
        org.junit.Assert.assertNotNull(sb)
        org.junit.Assert.assertEquals("https://cn.bzmanga.com/comic/shixiongwojiaobaixiaofei-qiduyu", sb?.bookUrl)
        org.junit.Assert.assertEquals("https://cn.bzmanga.com", sb?.origin)
        org.junit.Assert.assertEquals(2, sb?.type)
        org.junit.Assert.assertEquals("第500话", sb?.latestChapterTitle)
        org.junit.Assert.assertEquals("尸兄(我叫白小飞)", sb?.name)
        org.junit.Assert.assertEquals("七度鱼", sb?.author)
    }
}
