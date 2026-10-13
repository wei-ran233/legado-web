package io.legado.app.server.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.SearchBook
import io.legado.app.server.auth.SessionRepository
import io.legado.app.server.db.UserBooks
import io.legado.app.server.db.UserProgress
import io.legado.app.server.repo.BookRepository
import io.legado.app.server.service.LibraryService
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.server.repo.BookSourceRepository
import java.io.StringReader
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant

/**
 * 普通用户业务 API：书架 / 搜索 / 目录 / 正文 / 进度 / 图片代理
 */
fun Route.libraryRoutes() {

    // ===== 搜索（SSE 流式聚合） =====
    post("/search/stream") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, Any?>>()
        val keyword = (body["key"] as? String)?.trim().orEmpty()
        val precision = (body["precision"] as? Boolean) ?: true
        val category = ((body["category"] ?: body["type"]) as? Number)?.toInt()
        if (keyword.isBlank()) {
            call.respondError(400, "搜索关键字不能为空")
            return@post
        }
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            LibraryService.search(keyword, precision = precision, category = category).collect { ev ->
                when (ev) {
                    is LibraryService.SearchEvent.Start -> {
                        write("event: start\ndata: ${sseGson.toJson(mapOf("key" to keyword, "sourceCount" to ev.sourceCount))}\n\n")
                        flush()
                    }
                    is LibraryService.SearchEvent.Books -> {
                        val books = ev.books.map { b ->
                            val cachedBook = BookRepository.get(b.bookUrl)
                            val latest = b.latestChapterTitle?.takeIf { it.isNotBlank() }
                                ?: cachedBook?.latestChapterTitle?.takeIf { it.isNotBlank() }
                                ?: ""
                            mapOf(
                                "name" to b.name,
                                "author" to b.author,
                                "kind" to b.kind,
                                "intro" to b.intro?.take(120),
                                "coverUrl" to b.coverUrl,
                                "latestChapter" to latest,
                                "latestChapterTitle" to latest,
                                "bookUrl" to b.bookUrl,
                                "origin" to b.origin,
                                "originName" to ev.sourceName,
                                "type" to b.type,
                                "sourceType" to ev.sourceType,
                                "availabilityScore" to ev.availabilityScore,
                            )
                        }
                        write("event: books\ndata: ${sseGson.toJson(mapOf(
                            "originName" to ev.sourceName,
                            "sourceType" to ev.sourceType,
                            "availabilityScore" to ev.availabilityScore,
                            "books" to books
                        ))}\n\n")
                        flush()
                    }
                    is LibraryService.SearchEvent.SourceEmpty -> {
                        write("event: empty\ndata: ${sseGson.toJson(mapOf("originName" to ev.sourceName))}\n\n")
                        flush()
                    }
                    is LibraryService.SearchEvent.SourceError -> {
                        write("event: error\ndata: ${sseGson.toJson(mapOf("originName" to ev.sourceName, "error" to ev.error))}\n\n")
                        flush()
                    }
                    LibraryService.SearchEvent.Done -> {
                        write("event: done\ndata: {}\n\n")
                        flush()
                    }
                }
            }
        }
    }

    // ===== 换源 =====
    post("/book/changeSource") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, Any?>>()
        val oldBookUrl = body["oldBookUrl"] as? String
        val currentChapterIndex = (body["currentChapterIndex"] as? Number)?.toInt() ?: 0
        val currentChapterTitle = body["currentChapterTitle"] as? String
        val newBookMap = body["newBook"] as? Map<*, *>
            ?: return@post call.respondError(400, "缺少 newBook 参数")
        val searchBook = parseSearchBookFromMap(newBookMap)
            ?: return@post call.respondError(400, "newBook 解析失败")

        try {
            val result = LibraryService.changeSource(
                userId = user.id,
                oldBookUrl = oldBookUrl,
                searchBook = searchBook,
                currentChapterIndex = currentChapterIndex,
                currentChapterTitle = currentChapterTitle,
            )
            call.respondJson(
                mapOf(
                    "status" to "ok",
                    "newBookUrl" to result.newBookUrl,
                    "name" to result.name,
                    "author" to result.author,
                    "chapterIndex" to result.chapterIndex,
                    "chapterTitle" to result.chapterTitle,
                )
            )
        } catch (e: Exception) {
            call.respondError(500, "换源失败: ${e.message}")
        }
    }

    // ===== 书源列表（只读，供搜索/换源筛选） =====
    get("/sources") {
        call.requireUser() ?: return@get
        call.respondJson(io.legado.app.server.repo.BookSourceRepository.list(enabledOnly = true))
    }

    // ===== 书架 =====
    route("/bookshelf") {
        get {
            val user = call.requireUser() ?: return@get
            val items = io.legado.app.server.service.BookShelfService.listBooks(user.id).map { (book, meta) ->
                val progress = getProgress(user.id, book.bookUrl)
                mapOf(
                    "bookUrl" to book.bookUrl,
                    "name" to book.name,
                    "author" to book.author,
                    "coverUrl" to if (book.coverUrl.isNullOrBlank()) ""
                        else if (book.coverUrl!!.startsWith("data:") || book.coverUrl!!.startsWith("/api/")) book.coverUrl
                        else "/api/cover?path=${java.net.URLEncoder.encode(book.coverUrl ?: "", "UTF-8")}",
                    "kind" to book.kind,
                    "intro" to (book.customIntro ?: book.intro ?: ""),
                    "totalChapterNum" to book.totalChapterNum,
                    "latestChapterTitle" to book.latestChapterTitle,
                    "groupName" to (meta?.groupName ?: "默认分组"),
                    "durChapterIndex" to (progress?.get("durChapterIndex") ?: 0),
                    "durChapterTitle" to (progress?.get("durChapterTitle") ?: ""),
                    "type" to book.type,
                    // 书架版面所需：置顶标记、加入时间、最近阅读时间（用于排序与大卡选取）
                    "pinned" to (meta?.pinned ?: false),
                    "addedAt" to (meta?.addedAt ?: 0L),
                    "lastReadAt" to (progress?.get("updatedAt") ?: 0L),
                )
            }
            call.respondJson(items)
        }

        // 手动置顶 / 取消置顶
        post("/pin") {
            val user = call.requireUser() ?: return@post
            val body = call.receive<Map<String, Any?>>()
            val bookUrl = body["bookUrl"] as? String ?: ""
            if (bookUrl.isBlank()) return@post call.respondError(400, "缺少 bookUrl")
            val pinned = when (val raw = body["pinned"]) {
                null -> true
                is Boolean -> raw
                else -> raw.toString().toBoolean()
            }
            val ok = io.legado.app.server.service.BookShelfService.setPinned(user.id, bookUrl, pinned)
            call.respondJson(mapOf("status" to if (ok) "ok" else "notFound", "pinned" to pinned))
        }

        get("/has") {
            val user = call.requireUser() ?: return@get
            val bookUrl = call.request.queryParameters["bookUrl"] ?: ""
            val onShelf = io.legado.app.server.service.BookShelfService.has(user.id, bookUrl)
            call.respondJson(mapOf("onShelf" to onShelf))
        }

        post {
            val user = call.requireUser() ?: return@post
            val body = call.receive<Map<String, Any?>>()
            // 支持两种入参：已有 bookUrl（从馆藏加入）；或完整 SearchBook（搜索后加入）
            val existingUrl = body["bookUrl"] as? String
            if (!existingUrl.isNullOrBlank()) {
                // 已在馆藏中：直接加入书架
                val existing = BookRepository.get(existingUrl)
                if (existing != null) {
                    io.legado.app.server.service.BookShelfService.add(user.id, existing.bookUrl)
                    call.respondJson(mapOf("status" to "ok", "bookUrl" to existing.bookUrl))
                    return@post
                }
                // 不在馆藏：按 SearchBook 处理，走详情抓取流程
            }
            val searchBook = parseSearchBookFromMap(body)
                ?: return@post call.respondError(400, "参数错误")
            val book = LibraryService.addToBookshelf(user.id, searchBook)
            call.respondJson(mapOf("status" to "ok", "bookUrl" to book.bookUrl, "name" to book.name))
        }

        delete {
            val user = call.requireUser() ?: return@delete
            val bookUrl = call.request.queryParameters["bookUrl"] ?: ""
            if (bookUrl.isNotBlank()) {
                io.legado.app.server.service.BookShelfService.remove(user.id, bookUrl)
            }
            call.respondJson(mapOf("status" to "ok"))
        }

        delete("/{bookUrl}") {
            val user = call.requireUser() ?: return@delete
            val raw = call.parameters["bookUrl"] ?: ""
            val bookUrl = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            if (bookUrl.isNotBlank()) {
                io.legado.app.server.service.BookShelfService.remove(user.id, bookUrl)
            }
            call.respondJson(mapOf("status" to "ok"))
        }
    }

    // ===== 准备阅读（预解析目录与详情，存入全局馆藏，但不加入用户个人书架） =====
    post("/book/prepare") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, Any?>>()
        val existingUrl = body["bookUrl"] as? String
        if (!existingUrl.isNullOrBlank()) {
            val existing = BookRepository.get(existingUrl)
            if (existing != null) {
                call.respondJson(mapOf("status" to "ok", "bookUrl" to existing.bookUrl, "name" to existing.name, "type" to existing.type))
                return@post
            }
        }
        val searchBook = parseSearchBookFromMap(body)
            ?: return@post call.respondError(400, "参数错误")
        try {
            val book = LibraryService.prepareBook(searchBook)
            call.respondJson(mapOf("status" to "ok", "bookUrl" to book.bookUrl, "name" to book.name, "type" to book.type))
        } catch (e: Exception) {
            call.respondError(500, "解析书源失败: ${e.message}")
        }
    }

    // ===== 自定义书籍封面 =====
    post("/book/cover") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, String>>()
        val bookUrl = body["bookUrl"]
        val coverUrl = body["coverUrl"]
        if (bookUrl.isNullOrBlank() || coverUrl.isNullOrBlank()) {
            return@post call.respondError(400, "缺少参数")
        }
        val success = BookRepository.updateCover(bookUrl, coverUrl)
        if (success) {
            call.respondJson(mapOf("status" to "ok"))
        } else {
            call.respondError(404, "书籍不存在")
        }
    }

    // ===== 目录 =====
    get("/book/{bookUrl}/toc") {
        call.requireUser() ?: return@get
        val bookUrl = java.net.URLDecoder.decode(call.parameters["bookUrl"] ?: "", "UTF-8")
        try {
            val toc = LibraryService.getToc(bookUrl)
            call.respondJson(
                toc.map {
                    mapOf(
                        "index" to it.index,
                        "title" to it.title,
                        "isVolume" to it.isVolume,
                        "wordCount" to it.wordCount,
                    )
                }
            )
        } catch (e: Exception) {
            call.respondError(500, "获取目录失败: ${e.message}")
        }
    }

    // ===== 正文 =====
    get("/book/content") {
        call.requireUser() ?: return@get
        val bookUrl = java.net.URLDecoder.decode(call.request.queryParameters["bookUrl"] ?: "", "UTF-8")
        val index = call.request.queryParameters["index"]?.toIntOrNull() ?: 0
        try {
            val (chapter, content) = LibraryService.getContent(bookUrl, index)
            call.respondJson(
                mapOf(
                    "index" to chapter.index,
                    "title" to chapter.title,
                    "content" to content,
                )
            )
        } catch (e: Exception) {
            call.respondError(500, "获取正文失败: ${e.message}")
        }
    }

    // ===== 进度 =====
    post("/progress") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, Any?>>()
        val bookUrl = body["bookUrl"] as? String ?: return@post call.respondError(400, "缺少 bookUrl")
        val durChapterIndex = (body["durChapterIndex"] as? Double)?.toInt() ?: 0
        val durChapterPos = (body["durChapterPos"] as? Double)?.toInt() ?: 0
        val durChapterTitle = body["durChapterTitle"] as? String ?: ""
        val durChapterTime = (body["durChapterTime"] as? Double)?.toLong() ?: Instant.now().toEpochMilli()
        transaction {
            UserProgress.upsert(UserProgress.userId, UserProgress.bookUrl) { stmt ->
                stmt[UserProgress.userId] = user.id
                stmt[UserProgress.bookUrl] = bookUrl
                stmt[UserProgress.durChapterIndex] = durChapterIndex
                stmt[UserProgress.durChapterPos] = durChapterPos
                stmt[UserProgress.durChapterTitle] = durChapterTitle
                stmt[UserProgress.durChapterTime] = durChapterTime
                stmt[updatedAt] = Instant.now().epochSecond
            }
        }
        call.respondJson(mapOf("status" to "ok"))
    }

    get("/progress") {
        val user = call.requireUser() ?: return@get
        val bookUrl = call.request.queryParameters["bookUrl"] ?: return@get call.respondError(400, "缺少 bookUrl")
        val progress = getProgress(user.id, bookUrl)
        call.respondJson(progress ?: emptyMap<String, Any?>())
    }

    // ===== 封面在线智能检索（App 同款推书君/有谱说规则） =====
    get("/book/cover/search") {
        val name = call.request.queryParameters["name"].orEmpty()
        val author = call.request.queryParameters["author"].orEmpty()
        if (name.isBlank()) {
            call.respondError(400, "缺少 name 参数")
            return@get
        }
        val candidates = io.legado.app.server.service.CoverService.searchCandidates(name, author)
        call.respondJson(candidates)
    }

    // ===== 封面与正文图片代理（免登录认证，带磁盘缓存与反防盗链） =====
    get("/cover") {
        val target = call.request.queryParameters["path"]
            ?: call.request.queryParameters["url"]
            ?: return@get call.respondError(400, "缺少图片地址")
        val referer = call.request.queryParameters["referer"]
            ?: call.request.queryParameters["origin"]
            ?: call.request.queryParameters["bookUrl"]
        val bookUrl = call.request.queryParameters["bookUrl"]
        serveImage(call, target, referer, bookUrl)
    }

    get("/image") {
        val path = call.request.queryParameters["path"]
        val url = call.request.queryParameters["url"]
        val rawReferer = call.request.queryParameters["referer"]
            ?: call.request.queryParameters["origin"]
        val bookUrl = call.request.queryParameters["bookUrl"]

        val target = when {
            !path.isNullOrBlank() -> path
            !url.isNullOrBlank() -> url
            else -> return@get call.respondError(400, "缺少图片地址")
        }
        val referer = when {
            !rawReferer.isNullOrBlank() -> rawReferer
            !path.isNullOrBlank() && !url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://")) -> url
            else -> bookUrl
        }
        serveImage(call, target, referer, bookUrl)
    }
}

private suspend fun getProgress(userId: Long, bookUrl: String): Map<String, Any?>? = transaction {
    UserProgress.selectAll()
        .where { (UserProgress.userId eq userId) and (UserProgress.bookUrl eq bookUrl) }
        .firstOrNull()?.let {
            mapOf(
                "durChapterIndex" to it[UserProgress.durChapterIndex],
                "durChapterPos" to it[UserProgress.durChapterPos],
                "durChapterTitle" to it[UserProgress.durChapterTitle],
                "durChapterTime" to it[UserProgress.durChapterTime],
                "updatedAt" to it[UserProgress.updatedAt],
            )
        }
}

private suspend fun serveImage(
    call: ApplicationCall,
    rawUrl: String,
    explicitReferer: String? = null,
    bookUrlParam: String? = null
) {
    if (rawUrl.isBlank()) return
    if (rawUrl.startsWith("data:")) {
        val comma = rawUrl.indexOf(',')
        if (comma == -1) return
        val meta = rawUrl.substring(5, comma)
        val dataPart = rawUrl.substring(comma + 1)
        val mime = meta.substringBefore(';').ifBlank { "image/jpeg" }
        val bytes = if (meta.contains(";base64")) {
            java.util.Base64.getDecoder().decode(dataPart)
        } else {
            java.net.URLDecoder.decode(dataPart, "UTF-8").toByteArray()
        }
        call.respondBytes(bytes, ContentType.parse(mime))
        return
    }

    // 1. 解析 Legado 专属的图片 URL 规则 (例如: https://...jpg,{"headers":{"Referer":"..."}} 或 , {"headers":...})
    var cleanUrl = rawUrl.trim()
    val customHeaders = mutableMapOf<String, String>()
    val paramMatcher = AnalyzeUrl.paramPattern.matcher(cleanUrl)
    if (paramMatcher.find()) {
        val jsonPart = cleanUrl.substring(paramMatcher.end()).trim()
        cleanUrl = cleanUrl.substring(0, paramMatcher.start()).trim()
        runCatching {
            val reader = JsonReader(StringReader(jsonPart)).apply { isLenient = true }
            val element = JsonParser.parseReader(reader)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val headersObj = obj.get("headers")
                if (headersObj != null) {
                    if (headersObj.isJsonObject) {
                        headersObj.asJsonObject.entrySet().forEach { (k, v) ->
                            if (!v.isJsonNull) customHeaders[k] = v.asString
                        }
                    } else if (headersObj.isJsonPrimitive && headersObj.asJsonPrimitive.isString) {
                        runCatching {
                            val nestedReader = JsonReader(StringReader(headersObj.asString)).apply { isLenient = true }
                            val nested = JsonParser.parseReader(nestedReader)
                            if (nested.isJsonObject) {
                                nested.asJsonObject.entrySet().forEach { (k, v) ->
                                    if (!v.isJsonNull) customHeaders[k] = v.asString
                                }
                            }
                        }
                    }
                }
                obj.entrySet().forEach { (k, v) ->
                    if (k != "headers" && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                        customHeaders.putIfAbsent(k, v.asString)
                    }
                }
            }
        }
    }

    // 2. 若提供了 bookUrlParam 或 explicitReferer，尝试关联书籍所在书源获取默认防盗链 Header
    val sourceHeaders = mutableMapOf<String, String>()
    val candidateBookUrl = (bookUrlParam ?: explicitReferer)?.trim()?.let { bUrl ->
        val m = AnalyzeUrl.paramPattern.matcher(bUrl)
        if (m.find()) bUrl.substring(0, m.start()).trim() else bUrl
    }?.lines()?.firstOrNull()?.trim()

    if (!candidateBookUrl.isNullOrBlank()) {
        runCatching {
            val book = BookRepository.get(candidateBookUrl)
            val origin = book?.origin?.takeIf { it.isNotBlank() } ?: candidateBookUrl
            val bookSource = BookSourceRepository.get(origin)
            bookSource?.getHeaderMap()?.forEach { (k, v) ->
                if (v.isNotBlank()) {
                    sourceHeaders[k] = v
                }
            }
        }
    }

    fun sanitizeHeaderValue(valStr: String?): String? {
        if (valStr.isNullOrBlank()) return null
        return valStr.replace("\r", "").replace("\n", "").trim()
    }

    val cacheDir = io.legado.app.server.config.ServerConfig.imageCacheDir
    val name = java.security.MessageDigest.getInstance("MD5")
        .digest(cleanUrl.toByteArray()).joinToString("") { "%02x".format(it) }
    val ext = cleanUrl.substringBefore('?').substringAfterLast('.', "jpg").take(5).filter { it.isLetterOrDigit() }
    val cached = File(cacheDir, "$name.$ext")

    // 若本地已有缓存，检查是否为损坏的反爬拦截图（如 139,826 字节）
    var bytes: ByteArray? = if (cached.exists() && cached.length() > 0) {
        if (cached.length() == 139826L) {
            cached.delete()
            null
        } else {
            cached.readBytes()
        }
    } else null

    if (bytes == null || bytes.isEmpty()) {
        val parsedUri = runCatching { java.net.URI(cleanUrl) }.getOrNull()
        val host = parsedUri?.host.orEmpty().lowercase()

        // 智能提取或推断最佳 Referer
        var cleanReferer = explicitReferer?.trim()?.let { ref ->
            var r = ref
            val m = AnalyzeUrl.paramPattern.matcher(r)
            if (m.find()) r = r.substring(0, m.start()).trim()
            r.lines().firstOrNull()?.trim()
        }?.let { sanitizeHeaderValue(it) }

        var resolvedReferer = customHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            ?: cleanReferer?.takeIf { it.isNotBlank() }
            ?: sourceHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value

        if (resolvedReferer.isNullOrBlank()) {
            if (host.contains("cdndm5.com") || host.contains("dm5.com") || host.contains("1kkk.com")) {
                resolvedReferer = "https://m.1kkk.com/"
            } else if (parsedUri != null && parsedUri.scheme != null && parsedUri.host != null) {
                resolvedReferer = "${parsedUri.scheme}://${parsedUri.host}/"
            }
        } else {
            // 防盗链纠偏：若 referer 指向 CDN 自身（如 mhfm7.cdndm5.com），切换为书源主站
            if ((host.contains("cdndm5.com") || host.contains("dm5.com") || host.contains("1kkk.com")) &&
                resolvedReferer.contains("cdndm5.com")) {
                resolvedReferer = "https://m.1kkk.com/"
            }
        }

        val resolvedUa = sanitizeHeaderValue(
            customHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: sourceHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        val reqBuilder = Request.Builder().url(cleanUrl)
            .header("User-Agent", resolvedUa ?: "Mozilla/5.0")
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

        if (!resolvedReferer.isNullOrBlank()) {
            val sanitizedRef = sanitizeHeaderValue(resolvedReferer)
            if (!sanitizedRef.isNullOrBlank()) {
                reqBuilder.header("Referer", sanitizedRef)
            }
        }

        // 合并来自书源的其它 Header (例如 Cookie)
        sourceHeaders.forEach { (k, v) ->
            if (!k.equals("Referer", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                val sv = sanitizeHeaderValue(v)
                if (!sv.isNullOrBlank()) {
                    reqBuilder.header(k, sv)
                }
            }
        }

        // 图片专属 customHeaders 覆盖最高优先级
        customHeaders.forEach { (k, v) ->
            if (!k.equals("Referer", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                val sv = sanitizeHeaderValue(v)
                if (!sv.isNullOrBlank()) {
                    reqBuilder.header(k, sv)
                }
            }
        }

        val request = reqBuilder.build()
        var respBytes = runCatching {
            val resp = okHttpNoCookie.newCall(request).execute()
            resp.use { r ->
                if (!r.isSuccessful) null else r.body.bytes()
            }
        }.getOrNull()

        // 若依然遭遇 dm5 拦截图 (139826 字节)，用备用 Referer 重试一次
        if (respBytes != null && respBytes.size == 139826 && (host.contains("cdndm5.com") || host.contains("dm5.com") || host.contains("1kkk.com"))) {
            val fallbackReq = reqBuilder.header("Referer", "https://m.1kkk.com/").build()
            val retryBytes = runCatching {
                okHttpNoCookie.newCall(fallbackReq).execute().use { r ->
                    if (r.isSuccessful) r.body.bytes() else null
                }
            }.getOrNull()
            if (retryBytes != null && retryBytes.size != 139826) {
                respBytes = retryBytes
            }
        }

        // 若直连失败 (例如 403/404/502) 且之前附带了 Referer，尝试移除 Referer 重试一次防误杀
        if ((respBytes == null || respBytes.isEmpty()) && !resolvedReferer.isNullOrBlank()) {
            val retryReq = reqBuilder.removeHeader("Referer").build()
            val retryBytes = runCatching {
                okHttpNoCookie.newCall(retryReq).execute().use { r ->
                    if (r.isSuccessful) r.body.bytes() else null
                }
            }.getOrNull()
            if (retryBytes != null && retryBytes.isNotEmpty() && retryBytes.size != 139826) {
                respBytes = retryBytes
            }
        }

        if (respBytes != null && respBytes.isNotEmpty()) {
            // 只有非拦截图才落盘缓存
            if (respBytes.size != 139826) {
                cached.parentFile?.mkdirs()
                runCatching { cached.writeBytes(respBytes) }
            }
            bytes = respBytes
        }
    }

    val isHtmlPage = bytes != null && bytes.size > 15 && runCatching {
        val head = String(bytes, 0, minOf(bytes.size, 100)).trim().lowercase()
        head.startsWith("<!doctype") || head.startsWith("<html") || (head.startsWith("<?xml") && !head.contains("<svg"))
    }.getOrDefault(false)

    if (bytes == null || bytes.isEmpty() || isHtmlPage) {
        call.respondError(502, "图片获取失败")
        return
    }

    val contentType = when {
        ext == "png" -> ContentType.Image.PNG
        ext == "gif" -> ContentType.Image.GIF
        ext == "webp" -> ContentType.parse("image/webp")
        ext == "svg" -> ContentType.parse("image/svg+xml")
        else -> ContentType.Image.JPEG
    }
    call.respondBytes(bytes, contentType)
}

/** SSE 事件载荷必须单行输出，使用紧凑 Gson */
private val sseGson = GsonBuilder().disableHtmlEscaping().create()

private val okHttpNoCookie: OkHttpClient by lazy {
    io.legado.app.help.http.okHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()
}

/**
 * 容错构建 SearchBook 对象：
 * 兼容嵌套 rawBook、type/sourceType 各种数值与字符串类型、latestChapter 与 latestChapterTitle、
 * bookSourceUrl 与 origin、以及根据 bookUrl host 自动匹配书源 origin 的兜底机制。
 */
fun parseSearchBookFromMap(body: Map<*, *>?): SearchBook? {
    if (body == null) return null
    // 1. 若入参包裹在 rawBook 或 data 内，优先解包
    val rawTarget = (body["rawBook"] as? Map<*, *>)
        ?: (body["data"] as? Map<*, *>)
        ?: (body["book"] as? Map<*, *>)
        ?: body

    val map = rawTarget.entries.associate { (k, v) -> k.toString() to v }

    val bookUrl = (map["bookUrl"] as? String)?.trim().orEmpty()
    if (bookUrl.isBlank()) return null

    val name = (map["name"] as? String)?.trim().orEmpty()
    val author = (map["author"] as? String)?.trim().orEmpty()

    var origin = (map["origin"] as? String)?.trim()?.ifEmpty { null }
        ?: (map["bookSourceUrl"] as? String)?.trim()?.ifEmpty { null }
        ?: ""

    // 2. 书源 origin 兜底推断：若为空，尝试从 bookUrl 的 host 自动匹配启用的书源
    if (origin.isBlank()) {
        val host = runCatching { java.net.URI(bookUrl).host?.lowercase() }.getOrNull()
        if (!host.isNullOrBlank()) {
            val matched = BookSourceRepository.list(enabledOnly = true).firstOrNull { part ->
                val sHost = runCatching { java.net.URI(part.bookSourceUrl).host?.lowercase() }.getOrNull()
                sHost != null && (sHost == host || host.endsWith(sHost) || sHost.endsWith(host))
            }
            if (matched != null) {
                origin = matched.bookSourceUrl
            }
        }
    }

    val originName = (map["originName"] as? String)?.trim().orEmpty()

    val type = when (val t = map["type"] ?: map["sourceType"]) {
        is Number -> t.toInt()
        is String -> t.toIntOrNull() ?: 0
        else -> 0
    }

    val latest = (map["latestChapterTitle"] as? String)?.takeIf { it.isNotBlank() }
        ?: (map["latestChapter"] as? String)?.takeIf { it.isNotBlank() }

    val coverUrl = (map["coverUrl"] as? String)?.trim()
    val intro = (map["intro"] as? String)?.trim()
    val kind = (map["kind"] as? String)?.trim()
    val wordCount = map["wordCount"]?.toString()
    val tocUrl = (map["tocUrl"] as? String)?.trim().orEmpty()
    val variable = when (val v = map["variable"]) {
        null -> null
        is String -> v
        else -> GSON.toJson(v)
    }

    return SearchBook(
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        type = type,
        name = name,
        author = author,
        kind = kind,
        coverUrl = coverUrl,
        intro = intro,
        wordCount = wordCount,
        latestChapterTitle = latest,
        tocUrl = tocUrl,
        variable = variable
    )
}

