package io.legado.app.server.service

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 移植原版 Legado App 的封面搜索规则 (基于 defaultData/coverRule.json)
 * 支持通过推书君 (tuishujun.com) 与有谱说 (ypshuo.com) 智能匹配图书正版高清封面
 */
object CoverService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val coverCache = ConcurrentHashMap<String, String>()

    data class CoverCandidate(
        val title: String,
        val author: String,
        val coverUrl: String,
        val source: String
    )

    /**
     * 智能搜索匹配最佳单一封面
     */
    suspend fun searchCover(bookName: String, bookAuthor: String = ""): String? = withContext(Dispatchers.IO) {
        val cleanName = cleanTitle(bookName)
        if (cleanName.isBlank()) return@withContext null
        val cacheKey = "${cleanName}__${cleanAuthor(bookAuthor)}"
        coverCache[cacheKey]?.let { return@withContext it }

        val candidates = searchCandidates(cleanName, bookAuthor)
        val best = candidates.firstOrNull()?.coverUrl
        if (!best.isNullOrBlank()) {
            coverCache[cacheKey] = best
        }
        best
    }

    /**
     * 搜索所有候选封面列表
     */
    suspend fun searchCandidates(bookName: String, bookAuthor: String = ""): List<CoverCandidate> = withContext(Dispatchers.IO) {
        val cleanName = cleanTitle(bookName)
        val cleanAuth = cleanAuthor(bookAuthor)
        if (cleanName.isBlank()) return@withContext emptyList()

        val results = mutableListOf<CoverCandidate>()

        // 1. 推书君 API
        try {
            val url1 = "https://pre-api.tuishujun.com/api/searchBook?search_value=" + URLEncoder.encode(cleanName, "UTF-8") + "&page=1&pageSize=20"
            val req1 = Request.Builder()
                .url(url1)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(req1).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val json = JsonParser.parseString(body).asJsonObject
                    val dataArray = json.getAsJsonObject("data")?.getAsJsonArray("data")
                    dataArray?.forEach { el ->
                        val obj = el.asJsonObject
                        val title = obj.get("title")?.asString.orEmpty()
                        val author = obj.get("author_nickname")?.asString.orEmpty()
                        val cover = obj.get("cover")?.asString.orEmpty()
                        if (cover.isNotBlank() && isMatch(cleanName, cleanAuth, title, author)) {
                            results.add(CoverCandidate(title, author, cover, "推书君"))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. 有谱说 API
        try {
            val url2 = "http://m.ypshuo.com/api/novel/search?keyword=" + URLEncoder.encode(cleanName, "UTF-8") + "&searchType=1&page=1"
            val req2 = Request.Builder()
                .url(url2)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(req2).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body.string()
                    val json = JsonParser.parseString(body).asJsonObject
                    val dataArray = json.getAsJsonObject("data")?.getAsJsonArray("data")
                    dataArray?.forEach { el ->
                        val obj = el.asJsonObject
                        val title = obj.get("novel_name")?.asString.orEmpty()
                        val author = obj.get("author_name")?.asString.orEmpty()
                        val cover = obj.get("novel_img")?.asString.orEmpty()
                        if (cover.isNotBlank() && isMatch(cleanName, cleanAuth, title, author)) {
                            results.add(CoverCandidate(title, author, cover, "有谱说"))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        results
    }

    private fun isMatch(targetName: String, targetAuthor: String, name: String, author: String): Boolean {
        val tN = cleanTitle(targetName).lowercase()
        val cN = cleanTitle(name).lowercase()
        if (tN != cN && !tN.contains(cN) && !cN.contains(tN)) return false

        if (targetAuthor.isNotBlank() && author.isNotBlank()) {
            val tA = cleanAuthor(targetAuthor).lowercase()
            val cA = cleanAuthor(author).lowercase()
            return tA == cA || tA.contains(cA) || cA.contains(tA)
        }
        return true
    }

    private fun cleanTitle(s: String): String = s
        .replace(Regex("^[《〈【〔(]+|[》〉】〕)]+$"), "")
        .trim()

    private fun cleanAuthor(s: String): String = s
        .replace(Regex("^(作\\s*者|著)\\s*[:：]\\s*"), "")
        .trim()
}
