package io.legado.app.server.repo

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.server.db.BookSources
import io.legado.app.server.db.Chapters
import io.legado.app.server.db.UserBooks
import io.legado.app.server.db.Books
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

data class EnabledSource(
    val part: BookSourcePart,
    val source: BookSource
)

data class AvailabilityUpdate(
    val url: String,
    val isSuccess: Boolean,
    val costMs: Long,
    val hasLatestChapter: Boolean,
    val isTextClean: Boolean,
    val isError: Boolean,
    val canFetchArticle: Boolean? = null
)

/**
 * 后台单写者队列：异步批量持久化书源可用性健康指标，彻底根除多协程 SQLite 锁争用
 */
object AvailabilityRecorder {
    private val channel = Channel<AvailabilityUpdate>(capacity = 10000)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            val batch = ArrayList<AvailabilityUpdate>()
            while (isActive) {
                val first = channel.receiveCatching().getOrNull() ?: break
                batch.add(first)
                while (batch.size < 50) {
                    val next = channel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }

                runCatching {
                    BookSourceRepository.batchUpdateAvailability(batch)
                }.onFailure { e ->
                    System.err.println("Availability batch update error: ${e.message}")
                }
                batch.clear()
            }
        }
    }

    fun record(update: AvailabilityUpdate) {
        channel.trySend(update)
    }
}

data class BookSourcePart(
    val bookSourceUrl: String,
    val bookSourceName: String,
    val bookSourceGroup: String?,
    val enabled: Boolean,
    val customOrder: Int,
    val weight: Long,
    val respondTime: Long,
    val bookSourceType: Int,
    val enabledExplore: Boolean,
    val hasExploreUrl: Boolean,
    val availabilityScore: Int = 80,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val hasLatestChapter: Boolean = false,
    val isTextClean: Boolean = true,
    val canFetchArticle: Boolean = true,
    val lastCheckedAt: Long = 0,
)

/**
 * 书源仓库：完整 JSON 存储保证与社区书源格式兼容
 */
object BookSourceRepository {

    private fun rowToPart(it: org.jetbrains.exposed.sql.ResultRow) = BookSourcePart(
        bookSourceUrl = it[BookSources.bookSourceUrl],
        bookSourceName = it[BookSources.name],
        bookSourceGroup = it[BookSources.grp],
        enabled = it[BookSources.enabled],
        customOrder = it[BookSources.customOrder],
        weight = it[BookSources.weight],
        respondTime = it[BookSources.respondTime],
        bookSourceType = it[BookSources.type],
        enabledExplore = it[BookSources.enabledExplore],
        hasExploreUrl = it[BookSources.hasExploreUrl],
        availabilityScore = it[BookSources.availabilityScore],
        successCount = it[BookSources.successCount],
        failCount = it[BookSources.failCount],
        hasLatestChapter = it[BookSources.hasLatestChapter],
        isTextClean = it[BookSources.isTextClean],
        canFetchArticle = it[BookSources.canFetchArticle],
        lastCheckedAt = it[BookSources.lastCheckedAt],
    )

    fun get(bookSourceUrl: String): BookSource? = transaction {
        val trimmed = bookSourceUrl.trim()
        if (trimmed.isEmpty()) return@transaction null
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val withoutSlash = trimmed.trimEnd('/')
        BookSources.selectAll().where {
            (BookSources.bookSourceUrl eq trimmed) or
            (BookSources.bookSourceUrl eq withSlash) or
            (BookSources.bookSourceUrl eq withoutSlash)
        }.firstOrNull()?.let { parse(it[BookSources.json]) }
    }

    fun getPart(bookSourceUrl: String): BookSourcePart? = transaction {
        val trimmed = bookSourceUrl.trim()
        if (trimmed.isEmpty()) return@transaction null
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val withoutSlash = trimmed.trimEnd('/')
        BookSources.selectAll().where {
            (BookSources.bookSourceUrl eq trimmed) or
            (BookSources.bookSourceUrl eq withSlash) or
            (BookSources.bookSourceUrl eq withoutSlash)
        }.firstOrNull()?.let { rowToPart(it) }
    }

    fun list(enabledOnly: Boolean = false, limit: Int? = null, offset: Int? = null): List<BookSourcePart> = transaction {
        var query = BookSources.selectAll()
        if (enabledOnly) {
            query = query.where { BookSources.enabled eq true }
        }
        query = query.orderBy(BookSources.customOrder, SortOrder.ASC)
        if (limit != null && offset != null) {
            query = query.limit(limit).offset(offset.toLong())
        }
        query.map { rowToPart(it) }
    }

    fun count(enabledOnly: Boolean = false): Long = transaction {
        if (enabledOnly) {
            BookSources.selectAll().where { BookSources.enabled eq true }.count()
        } else {
            BookSources.selectAll().count()
        }
    }

    /** 智能识别书源类型：JSON 显式声明优先，其次根据书源名称与分组关键词推断 */
    fun inferSourceType(name: String, grp: String, jsonStr: String): Int {
        try {
            val parsed = GSON.fromJsonObject<Map<String, Any?>>(jsonStr).getOrNull()
            val bst = (parsed?.get("bookSourceType") as? Number)?.toInt()
            if (bst != null && bst in 1..4) {
                return bst
            }
        } catch (_: Exception) {}

        val full = "$name $grp".lowercase()
        // 音乐 / 听书 / 音频 / 电台
        if (listOf("听书", "音频", "有声", "电台", "音乐", "fm", "广播", "评书", "voice", "sound").any { full.contains(it) }) {
            return 1
        }
        // 漫画 / 动漫图 / 漫客 / 绘本 / 图站
        if (listOf("漫画", "动漫图", "漫客", "manga", "comic", "写真", "图站", "美图", "绘本", "插画").any { full.contains(it) }) {
            return 2
        }
        // 视频 / 影视 / 电影 / 剧集 / 短剧
        if (listOf("视频", "影视", "电影", "电视剧", "短剧", "剧集", "录像", "tv", "movie", "video").any { full.contains(it) }) {
            return 4
        }
        // 网盘 / 下载站
        if (listOf("网盘", "下载", "云盘", "度盘", "夸克").any { full.contains(it) }) {
            return 3
        }
        return 0
    }

    /** 保存/导入书源（JSON 对象列表），返回导入数量 */
    fun import(jsonList: List<Map<String, Any?>>): Int = transaction {
        var count = 0
        jsonList.forEach { map ->
            val url = map["bookSourceUrl"] as? String ?: return@forEach
            val json = GSON.toJson(map)
            val name = (map["bookSourceName"] as? String) ?: url
            val grp = (map["bookSourceGroup"] as? String) ?: ""
            val rawType = (map["bookSourceType"] as? Number)?.toInt()
            val finalType = if (rawType != null && rawType in 1..4) {
                rawType
            } else {
                inferSourceType(name, grp, json)
            }
            BookSources.upsert(BookSources.bookSourceUrl) {
                it[bookSourceUrl] = url
                it[BookSources.name] = name
                it[BookSources.grp] = grp
                it[type] = finalType
                it[enabled] = (map["enabled"] as? Boolean) ?: true
                it[customOrder] = (map["customOrder"] as? Number)?.toInt() ?: 0
                it[weight] = (map["weight"] as? Number)?.toLong() ?: 0
                it[lastUpdateTime] = (map["lastUpdateTime"] as? Number)?.toLong() ?: 0
                it[BookSources.json] = json
                it[enabledExplore] = (map["enabledExplore"] as? Boolean) ?: false
                it[hasExploreUrl] = (map["exploreUrl"] as? String).isNullOrBlank() == false
                it[availabilityScore] = 80
            }
            count++
        }
        count
    }

    fun setEnabled(urls: List<String>, enabled: Boolean) = transaction {
        urls.forEach { url ->
            BookSources.update({ BookSources.bookSourceUrl eq url }) {
                it[BookSources.enabled] = enabled
            }
        }
    }

    /** 批量更新书源分类类型 */
    fun updateType(urls: List<String>, type: Int) = transaction {
        urls.forEach { url ->
            BookSources.update({ BookSources.bookSourceUrl eq url }) {
                it[BookSources.type] = type
            }
        }
    }

    /**
     * 单次 SQL 查询预取所有启用的书源并完成 JSON 解析与排序，供并发搜索流水线高效消费
     */
    fun listEnabledSources(category: Int? = null): List<EnabledSource> = transaction {
        BookSources.selectAll()
            .where { BookSources.enabled eq true }
            .mapNotNull { row ->
                val json = row[BookSources.json]
                val source = parse(json) ?: return@mapNotNull null
                if (source.searchUrl.isNullOrBlank()) return@mapNotNull null
                val part = rowToPart(row)
                val matchesCategory = when (category) {
                    null, -1 -> true // 全部
                    0 -> part.bookSourceType == 0 || part.bookSourceType == 3 // 书籍 (文本/小说/网盘TXT)
                    1 -> part.bookSourceType == 1 // 音乐 (音频/听书/电台)
                    2 -> part.bookSourceType == 2 // 漫画 (图片)
                    4 -> part.bookSourceType in listOf(3, 4) // 视频 (影视/短剧)
                    else -> part.bookSourceType == category
                }
                if (!matchesCategory) return@mapNotNull null
                EnabledSource(part, source)
            }
            .sortedWith(
                // 1. 可用性评分高的排在最前 (100 -> 0)
                compareByDescending<EnabledSource> { it.part.availabilityScore }
                    // 2. 响应时间快的排在前面
                    .thenBy { if (it.part.respondTime in 1..30000) it.part.respondTime else 99999L }
                    // 3. 自定义排序排在前面
                    .thenBy { it.part.customOrder }
                    // 4. 权重大的排在前面
                    .thenByDescending { it.part.weight }
            )
    }

    /**
     * 批量持久化可用性指标：在单一数据库事务内批量执行，大幅降低 SQLite 磁盘 I/O 并避免死锁
     */
    fun batchUpdateAvailability(updates: List<AvailabilityUpdate>) = transaction {
        if (updates.isEmpty()) return@transaction
        val urls = updates.map { it.url }.toSet()
        val rows = BookSources.selectAll().where { BookSources.bookSourceUrl inList urls }
            .associateBy { it[BookSources.bookSourceUrl] }

        for (u in updates) {
            val row = rows[u.url] ?: continue
            val curScore = row[BookSources.availabilityScore]
            val curSuccess = row[BookSources.successCount]
            val curFail = row[BookSources.failCount]
            val oldRespond = row[BookSources.respondTime]

            var newScore = curScore
            if (u.isSuccess) {
                newScore += 5
                if (u.hasLatestChapter) newScore += 5
                if (u.isTextClean) newScore += 3 else newScore -= 15
                if (u.costMs in 1..2000) newScore += 3 else if (u.costMs > 6000) newScore -= 3
            } else if (u.isError) {
                newScore -= 10
            }
            if (u.canFetchArticle == false) {
                newScore -= 15
            } else if (u.canFetchArticle == true) {
                newScore += 5
            }
            newScore = newScore.coerceIn(0, 100)

            val newRespond = if (oldRespond in 1..60000) {
                (oldRespond * 0.7 + u.costMs * 0.3).toLong()
            } else {
                u.costMs
            }

            BookSources.update({ BookSources.bookSourceUrl eq u.url }) {
                it[availabilityScore] = newScore
                if (u.isSuccess) it[successCount] = curSuccess + 1
                if (u.isError) it[failCount] = curFail + 1
                if (u.hasLatestChapter) it[BookSources.hasLatestChapter] = true
                it[BookSources.isTextClean] = u.isTextClean
                if (u.canFetchArticle != null) it[BookSources.canFetchArticle] = u.canFetchArticle
                it[respondTime] = newRespond
                it[lastCheckedAt] = System.currentTimeMillis()
            }
        }
    }

    /** 自动记录并自愈平滑更新书源可用性健康指标（通过后台单写者队列异步批量持久化） */
    fun updateAvailability(
        url: String,
        isSuccess: Boolean,
        costMs: Long,
        hasLatestChapter: Boolean,
        isTextClean: Boolean,
        isError: Boolean,
        canFetchArticle: Boolean? = null
    ) {
        AvailabilityRecorder.record(
            AvailabilityUpdate(
                url = url,
                isSuccess = isSuccess,
                costMs = costMs,
                hasLatestChapter = hasLatestChapter,
                isTextClean = isTextClean,
                isError = isError,
                canFetchArticle = canFetchArticle
            )
        )
    }

    fun delete(urls: List<String>) = transaction {
        urls.forEach { url -> BookSources.deleteWhere { BookSources.bookSourceUrl eq url } }
    }

    private fun parse(json: String): BookSource? =
        GSON.fromJsonObject<BookSource>(json).getOrNull()
}

/**
 * 书籍仓库：全局馆藏 + 章节目录
 */
object BookRepository {

    fun get(bookUrl: String): Book? = transaction {
        Books.selectAll().where { Books.bookUrl eq bookUrl }.firstOrNull()?.let { rowToBook(it) }
    }

    fun has(bookUrl: String): Boolean = transaction {
        Books.selectAll().where { Books.bookUrl eq bookUrl }.count() > 0
    }

    fun getByNameAuthorOrigin(name: String, origin: String): Book? = transaction {
        Books.selectAll().where { (Books.name eq name) and (Books.origin eq origin) }
            .firstOrNull()?.let { rowToBook(it) }
    }

    fun all(): List<Book> = transaction {
        Books.selectAll().map { rowToBook(it) }
    }

    fun upsert(book: Book) = transaction {
        Books.upsert(Books.bookUrl) {
            it[bookUrl] = book.bookUrl
            it[name] = book.name
            it[author] = book.author
            it[coverUrl] = book.coverUrl
            it[origin] = book.origin
            it[originName] = book.originName
            it[kind] = book.kind
            it[intro] = book.intro
            it[latestChapterTitle] = book.latestChapterTitle
            it[totalChapterNum] = book.totalChapterNum
            it[tocUrl] = book.tocUrl
            it[variable] = book.variable
            it[type] = book.type
            it[latestChapterTime] = book.latestChapterTime
            it[canUpdate] = book.canUpdate
        }
    }

    fun updateCover(bookUrl: String, coverUrl: String): Boolean = transaction {
        val count = Books.update({ Books.bookUrl eq bookUrl }) {
            it[Books.coverUrl] = coverUrl
        }
        count > 0
    }

    fun delete(bookUrl: String) = transaction {
        Chapters.deleteWhere { Chapters.bookUrl eq bookUrl }
        Books.deleteWhere { Books.bookUrl eq bookUrl }
        UserBooks.deleteWhere { UserBooks.bookUrl eq bookUrl }
    }

    fun getChapterList(bookUrl: String): List<BookChapter> = transaction {
        Chapters.selectAll().where { Chapters.bookUrl eq bookUrl }
            .orderBy(Chapters.chapterIndex, SortOrder.ASC)
            .map { rowToChapter(it) }
    }

    fun getChapter(bookUrl: String, index: Int): BookChapter? = transaction {
        Chapters.selectAll()
            .where { (Chapters.bookUrl eq bookUrl) and (Chapters.chapterIndex eq index) }
            .firstOrNull()?.let { rowToChapter(it) }
    }

    fun replaceChapters(bookUrl: String, chapters: List<BookChapter>) = transaction {
        Chapters.deleteWhere { Chapters.bookUrl eq bookUrl }
        chapters.forEach { c ->
            Chapters.insert {
                it[Chapters.bookUrl] = bookUrl
                it[chapterIndex] = c.index
                it[url] = c.url
                it[title] = c.title
                it[isVolume] = c.isVolume
                it[baseUrl] = c.baseUrl
                it[isVip] = c.isVip
                it[isPay] = c.isPay
                it[resourceUrl] = c.resourceUrl
                it[tag] = c.tag
                it[wordCount] = c.wordCount
                it[start] = c.start
                it[end] = c.end
                it[variable] = c.variable
            }
        }
    }

    fun upWordCount(bookUrl: String, chapterUrl: String, wordCount: String) = transaction {
        Chapters.update({ (Chapters.bookUrl eq bookUrl) and (Chapters.url eq chapterUrl) }) {
            it[Chapters.wordCount] = wordCount
        }
    }

    private fun rowToBook(it: org.jetbrains.exposed.sql.ResultRow) = Book().apply {
        bookUrl = it[Books.bookUrl]
        name = it[Books.name]
        author = it[Books.author]
        coverUrl = it[Books.coverUrl]
        origin = it[Books.origin]
        originName = it[Books.originName]
        kind = it[Books.kind]
        intro = it[Books.intro]
        latestChapterTitle = it[Books.latestChapterTitle]
        totalChapterNum = it[Books.totalChapterNum]
        tocUrl = it[Books.tocUrl]
        variable = it[Books.variable]
        type = it[Books.type]
        latestChapterTime = it[Books.latestChapterTime]
        canUpdate = it[Books.canUpdate]
    }

    private fun rowToChapter(it: org.jetbrains.exposed.sql.ResultRow) = BookChapter().apply {
        url = it[Chapters.url]
        title = it[Chapters.title]
        isVolume = it[Chapters.isVolume]
        baseUrl = it[Chapters.baseUrl]
        bookUrl = it[Chapters.bookUrl]
        index = it[Chapters.chapterIndex]
        isVip = it[Chapters.isVip]
        isPay = it[Chapters.isPay]
        resourceUrl = it[Chapters.resourceUrl]
        tag = it[Chapters.tag]
        wordCount = it[Chapters.wordCount]
        start = it[Chapters.start]
        end = it[Chapters.end]
        variable = it[Chapters.variable]
    }
}
