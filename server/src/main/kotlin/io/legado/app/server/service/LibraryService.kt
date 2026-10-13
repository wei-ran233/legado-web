package io.legado.app.server.service

import io.legado.app.data.EngineDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.book.isImage
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.getProxyClient
import io.legado.app.model.webBook.WebBook
import io.legado.app.server.db.UserBooks
import io.legado.app.server.db.UserProgress
import io.legado.app.server.repo.BookRepository
import io.legado.app.server.repo.BookSourceRepository
import io.legado.app.server.repo.BookSourcePart
import io.legado.app.server.repo.EnabledSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.io.File
import java.time.Instant

/**
 * 原版 Legado 同款并发安全并行 Flow 映射函数（app/src/main/java/io/legado/app/utils/FlowExtensions.kt）
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.mapParallelSafe(
    concurrency: Int,
    crossinline transform: suspend (T) -> R,
): Flow<R> = flatMapMerge(concurrency) { value ->
    flow {
        try {
            emit(transform(value))
        } catch (_: Throwable) {
            currentCoroutineContext().ensureActive()
        }
    }
}.buffer(0)

/**
 * 核心阅读业务：搜索 / 加书架 / 目录 / 正文（正文缓存全局共享）
 */
object LibraryService {

    /** 多源聚合搜索：参考原版 Legado SearchModel 采用 Flow + mapParallelSafe 滑动流式管道调度 */
    fun search(key: String, page: Int = 1, precision: Boolean = true, category: Int? = null): Flow<SearchEvent> = callbackFlow {
        val searchKey = key.trim()
        val keywords = searchKey.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 一次性单 SQL 查询预取所有启用的书源并按质量可用性排序（零搜索期数据库读争用）
        val sources = BookSourceRepository.listEnabledSources(category)

        send(SearchEvent.Start(sources.size))

        if (sources.isEmpty()) {
            send(SearchEvent.Done)
            close()
            return@callbackFlow
        }

        // 原版 App 搜索相关度过滤规则
        val searchFilter: ((String, String, String?) -> Boolean) = { name, author, kind ->
            if (!precision) {
                true
            } else if (keywords.size > 1) {
                keywords.all { kw ->
                    name.contains(kw, ignoreCase = true) ||
                            author.contains(kw, ignoreCase = true) ||
                            kind?.contains(kw, ignoreCase = true) == true
                }
            } else {
                name.contains(searchKey, ignoreCase = true) ||
                        author.contains(searchKey, ignoreCase = true) ||
                        kind?.contains(searchKey) == true
            }
        }

        // 采用原版 Legado Flow.mapParallelSafe 管道并发调度（并发度 24，平滑消费任务）
        val searchJob = launch(Dispatchers.IO) {
            flow {
                for (item in sources) {
                    emit(item)
                }
            }.mapParallelSafe(24) { item ->
                val part = item.part
                val source = item.source
                val startMs = System.currentTimeMillis()
                var searchError: Throwable? = null
                val books = try {
                    withTimeout(30_000L) { // 严格对齐原版 Legado 30 秒单源超时
                        WebBook.searchBookAwait(source, searchKey, page, filter = searchFilter)
                    }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    searchError = e
                    null
                }

                // 释放 HTML 解析缓存，即时优化 JVM 堆内存（原版 Legado 同款）
                books?.forEach { book ->
                    book.releaseHtmlData()
                }

                val costMs = System.currentTimeMillis() - startMs
                val isSuccess = !books.isNullOrEmpty()
                val isError = (books == null && searchError != null)
                val hasLatest = books?.any { !it.latestChapterTitle.isNullOrBlank() } ?: false
                val hasGarbled = books?.any { b ->
                    isGarbledOrBlocked(b.name) || isGarbledOrBlocked(b.latestChapterTitle) || isGarbledOrBlocked(b.intro)
                } ?: false

                // 异步提交可用性更新（放入后台单消费者队列，绝不引发 SQLite 锁死）
                BookSourceRepository.updateAvailability(
                    url = part.bookSourceUrl,
                    isSuccess = isSuccess,
                    costMs = costMs,
                    hasLatestChapter = hasLatest,
                    isTextClean = !hasGarbled,
                    isError = isError
                )

                Triple(part, books, searchError?.message)
            }.collect { (part, books, errorMsg) ->
                if (!books.isNullOrEmpty()) {
                    // 同步书本的媒体类型
                    books.forEach { b -> if (b.type == 0) b.type = part.bookSourceType }
                    send(SearchEvent.Books(
                        sourceName = part.bookSourceName,
                        books = books,
                        sourceType = part.bookSourceType,
                        availabilityScore = part.availabilityScore
                    ))
                } else if (errorMsg != null) {
                    send(SearchEvent.SourceError(part.bookSourceName, errorMsg))
                } else {
                    send(SearchEvent.SourceEmpty(part.bookSourceName))
                }
            }

            send(SearchEvent.Done)
            close()
        }

        awaitClose {
            searchJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun isGarbledOrBlocked(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        for (ch in text) {
            if (ch in '\uE800'..'\uE863') return true
        }
        val lower = text.lowercase()
        return listOf(
            "为了使用完整的阅读功能", "请考虑使用", "chrome 谷歌浏览器", "safari 苹果浏览器",
            "内容加载失败", "防盗章节", "正在验证", "系统繁忙", "502 bad gateway",
            "cloudflare", "attention required", "just a moment"
        ).any { lower.contains(it) }
    }

    sealed class SearchEvent {
        abstract val sourceName: String

        data class Start(val sourceCount: Int) : SearchEvent() {
            override val sourceName: String get() = ""
        }
        data class Books(
            override val sourceName: String,
            val books: List<SearchBook>,
            val sourceType: Int = 0,
            val availabilityScore: Int = 80
        ) : SearchEvent()
        data class SourceEmpty(override val sourceName: String) : SearchEvent()
        data class SourceError(override val sourceName: String, val error: String) : SearchEvent()
        data object Done : SearchEvent() {
            override val sourceName: String get() = ""
        }
    }

    data class ChangeSourceResult(
        val newBookUrl: String,
        val name: String,
        val author: String,
        val chapterIndex: Int,
        val chapterTitle: String,
    )

    /** 换源业务：抓取新源详情与目录、章节比对定位、更新书架源替换与进度 */
    suspend fun changeSource(
        userId: Long,
        oldBookUrl: String?,
        searchBook: SearchBook,
        currentChapterIndex: Int,
        currentChapterTitle: String?
    ): ChangeSourceResult {
        // 1. 抓取新书详情与目录（进入全局馆藏，不直接新增书架，避免重复）
        val newBook = prepareBook(searchBook)
        val newChapters = getToc(newBook.bookUrl)

        // 2. 章节智能对齐
        val targetIndex = matchChapter(newChapters, currentChapterIndex, currentChapterTitle)
        val targetTitle = newChapters.getOrNull(targetIndex)?.title ?: currentChapterTitle ?: ""

        // 3. 更新用户阅读进度与书架替换
        transaction {
            UserProgress.upsert(UserProgress.userId, UserProgress.bookUrl) { stmt ->
                stmt[UserProgress.userId] = userId
                stmt[UserProgress.bookUrl] = newBook.bookUrl
                stmt[UserProgress.durChapterIndex] = targetIndex
                stmt[UserProgress.durChapterPos] = 0
                stmt[UserProgress.durChapterTitle] = targetTitle
                stmt[UserProgress.durChapterTime] = Instant.now().toEpochMilli()
                stmt[UserProgress.updatedAt] = Instant.now().epochSecond
            }
            // 如果旧书在书架上，必须将书架上的旧源替换为新源，继承分组、排序与时间，杜绝书架同名重复！
            if (!oldBookUrl.isNullOrBlank() && oldBookUrl != newBook.bookUrl) {
                val oldMeta = UserBooks.selectAll()
                    .where { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq oldBookUrl) }
                    .firstOrNull()
                if (oldMeta != null) {
                    val groupName = oldMeta[UserBooks.groupName]
                    val sortOrder = oldMeta[UserBooks.sortOrder]
                    val addedAt = oldMeta[UserBooks.addedAt]

                    // 1. 从书架中移除旧源
                    UserBooks.deleteWhere { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq oldBookUrl) }

                    // 2. 将新源加入书架（保留旧书的分组与排序等属性）
                    val exists = UserBooks.selectAll()
                        .where { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq newBook.bookUrl) }
                        .count() > 0
                    if (!exists) {
                        UserBooks.insert {
                            it[UserBooks.userId] = userId
                            it[UserBooks.bookUrl] = newBook.bookUrl
                            it[UserBooks.groupName] = groupName
                            it[UserBooks.sortOrder] = sortOrder
                            it[UserBooks.addedAt] = addedAt
                        }
                    } else {
                        UserBooks.update({ (UserBooks.userId eq userId) and (UserBooks.bookUrl eq newBook.bookUrl) }) {
                            it[UserBooks.groupName] = groupName
                            it[UserBooks.sortOrder] = sortOrder
                        }
                    }
                }
            }
        }

        return ChangeSourceResult(
            newBookUrl = newBook.bookUrl,
            name = newBook.name,
            author = newBook.author,
            chapterIndex = targetIndex,
            chapterTitle = targetTitle,
        )
    }

    private fun matchChapter(chapters: List<BookChapter>, oldIndex: Int, oldTitle: String?): Int {
        if (chapters.isEmpty()) return 0
        if (!oldTitle.isNullOrBlank()) {
            val cleanOld = cleanChapterTitle(oldTitle)
            if (cleanOld.isNotEmpty()) {
                // 1. 精确匹配清理后标题
                val exact = chapters.indexOfFirst { cleanChapterTitle(it.title) == cleanOld }
                if (exact >= 0) return exact
                // 2. 包含匹配
                val contains = chapters.indexOfFirst {
                    val cleanNew = cleanChapterTitle(it.title)
                    cleanNew.isNotEmpty() && (cleanNew.contains(cleanOld) || cleanOld.contains(cleanNew))
                }
                if (contains >= 0) return contains
            }
        }
        // 3. 按序号对齐，防止越界
        return oldIndex.coerceIn(0, chapters.lastIndex)
    }

    private fun cleanChapterTitle(title: String): String {
        return title
            .replace(Regex("^(第[0-9一二两三四五六七八九十百千万]+[章节回卷集部篇话]|Chapter\\s*[0-9]+)\\s*"), "")
            .replace(Regex("[\\s　_—\\-—:：,，。！!？?\"'“”‘’（）\\[\\]()]"), "")
            .trim()
    }

    /** 准备书籍（抓详情+目录入全局馆藏，不加入用户个人书架，供直接阅读） */
    suspend fun prepareBook(searchBook: SearchBook): Book {
        val sourcePart = BookSourceRepository.getPart(searchBook.origin)
        if (sourcePart == null || !sourcePart.enabled) {
            throw IllegalArgumentException("书源不存在或已下架")
        }
        val source = BookSourceRepository.get(searchBook.origin)
            ?: throw IllegalArgumentException("书源不存在或已下架")
        val book = searchBook.toBook()
        val info = withTimeout(10_000) {
            WebBook.getBookInfoAwait(source, book)
        }
        if (info.coverUrl.isNullOrBlank()) {
            val autoCover = runCatching { CoverService.searchCover(info.name, info.author) }.getOrNull()
            if (!autoCover.isNullOrBlank()) {
                info.coverUrl = autoCover
            }
        }
        refreshToc(source, info)
        BookRepository.upsert(info)
        return info
    }

    /** 搜索后加入书架：抓详情+目录入全局馆藏，再关联用户书架 */
    suspend fun addToBookshelf(userId: Long, searchBook: SearchBook): Book {
        val info = prepareBook(searchBook)
        BookShelfService.add(userId, info.bookUrl)
        return info
    }

    /** 刷新目录并存入全局章节表 */
    suspend fun refreshToc(source: BookSource, book: Book) {
        val chapters = withTimeout(15_000) {
            WebBook.getChapterListAwait(source, book).getOrThrow()
        }
        BookRepository.replaceChapters(book.bookUrl, chapters)
        book.totalChapterNum = chapters.size
        chapters.lastOrNull()?.let {
            book.latestChapterTitle = it.title
        }
    }

    /** 获取目录（缓存优先，空则刷新；刷新失败回退到缓存目录） */
    suspend fun getToc(bookUrl: String): List<BookChapter> {
        val cached = BookRepository.getChapterList(bookUrl)
        if (cached.isNotEmpty()) return cached
        val book = BookRepository.get(bookUrl) ?: throw IllegalArgumentException("书籍不存在")
        val source = BookSourceRepository.get(book.origin)
            ?: throw IllegalStateException("书源不存在或已下架")
        try {
            refreshToc(source, book)
            BookRepository.upsert(book)
        } catch (e: Exception) {
            val stale = BookRepository.getChapterList(bookUrl)
            if (stale.isNotEmpty()) return stale
            throw e
        }
        return BookRepository.getChapterList(bookUrl)
    }

    /** 获取正文（全局磁盘缓存优先；自动过滤反爬损坏缓存；字符反混淆还原） */
    suspend fun getContent(bookUrl: String, index: Int): Pair<BookChapter, String> {
        val book = BookRepository.get(bookUrl) ?: throw IllegalArgumentException("书籍不存在")
        val chapter = BookRepository.getChapter(bookUrl, index)
            ?: throw IllegalArgumentException("章节不存在")
        val cached = ContentCache.read(book, chapter)
        // 若命中有效磁盘缓存，且不包含反爬截断/拦截错误提示，则解码 PUA 字符后直接返回
        if (cached != null && !FontAdapter.isAntiBotContent(cached)) {
            return chapter to FontAdapter.adaptText(cached)
        }
        val text = try {
            val startFetch = System.currentTimeMillis()
            val source = BookSourceRepository.get(book.origin)
                ?: throw IllegalStateException("书源不存在或已下架")
            val isManga = book.isImage || source.bookSourceType == 2
            val timeoutMs = if (isManga) 60_000L else 30_000L
            withTimeout(timeoutMs) {
                val next = BookRepository.getChapter(bookUrl, index + 1)
                val raw = WebBook.getContentAwait(source, book, chapter, next?.url, needSave = false)
                val isClean = !FontAdapter.isAntiBotContent(raw)
                val adapted = FontAdapter.adaptText(raw)
                val cost = System.currentTimeMillis() - startFetch
                val validComic = if (isManga) {
                    isComicContentValid(adapted, chapter.url, book.bookUrl)
                } else true

                BookSourceRepository.updateAvailability(
                    url = book.origin,
                    isSuccess = isClean && validComic,
                    costMs = cost,
                    hasLatestChapter = true,
                    isTextClean = isClean && validComic,
                    isError = false,
                    canFetchArticle = isClean && (if (isManga) validComic else adapted.length > 30)
                )
                adapted
            }.also { ContentCache.write(book, chapter, it) }
        } catch (e: Exception) {
            BookSourceRepository.updateAvailability(
                url = book.origin,
                isSuccess = false,
                costMs = 5000,
                hasLatestChapter = false,
                isTextClean = false,
                isError = true,
                canFetchArticle = false
            )
            // 网络失败：若有旧缓存则容错回退
            val stale = ContentCache.read(book, chapter)
            if (stale != null) FontAdapter.adaptText(stale) else throw e
        }
        return chapter to text
    }

    private fun isComicContentValid(content: String, chapterUrl: String, bookUrl: String): Boolean {
        val imgPattern = Regex("""<img\b[^>]*?\s(?:src|data-src|data-original)=["']?([^"',\s>]+)""", RegexOption.IGNORE_CASE)
        val matches = imgPattern.findAll(content).map { it.groupValues[1].trim() }.toList()
        if (matches.isEmpty()) {
            val lines = content.lines().map { it.trim() }
            val hasImgLine = lines.any { line ->
                val clean = line.split(",")[0].trim().lowercase()
                clean.startsWith("http") && (clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") || clean.endsWith(".webp") || clean.endsWith(".gif"))
            }
            return hasImgLine
        }
        val cleanChap = chapterUrl.substringBefore('?').substringBefore('#').lowercase()
        val cleanBook = bookUrl.substringBefore('?').substringBefore('#').lowercase()
        val realImgs = matches.filterNot { src ->
            val cleanSrc = src.substringBefore('?').substringBefore('#').lowercase()
            cleanSrc == cleanChap || cleanSrc == cleanBook || cleanSrc.endsWith(".html") || cleanSrc.endsWith(".htm") || cleanSrc.endsWith(".php")
        }
        return realImgs.isNotEmpty()
    }
}

/**
 * 正文磁盘缓存（沿用 legado 的 book_cache 目录与文件命名，全局共享）
 */
object ContentCache {

    fun read(book: Book, chapter: BookChapter): String? {
        val file = chapterFile(book, chapter)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun write(book: Book, chapter: BookChapter, content: String) {
        runCatching {
            val file = chapterFile(book, chapter)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    private fun chapterFile(book: Book, chapter: BookChapter): File {
        val folder = io.legado.app.server.config.ServerConfig.bookCacheDir
        val bookFolder = book.getFolderNameNoCache()
        return File(File(folder, bookFolder), chapter.getFileName())
    }
}

/**
 * 个人书架服务
 */
object BookShelfService {

    fun add(userId: Long, bookUrl: String): Boolean = transaction {
        val exists = UserBooks.selectAll()
            .where { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq bookUrl) }
            .count() > 0
        if (!exists) {
            UserBooks.insert {
                it[UserBooks.userId] = userId
                it[UserBooks.bookUrl] = bookUrl
                it[addedAt] = java.time.Instant.now().epochSecond
            }
        }
        !exists
    }

    fun remove(userId: Long, bookUrl: String) = transaction {
        UserBooks.deleteWhere { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq bookUrl) }
        UserProgress.deleteWhere { (UserProgress.userId eq userId) and (UserProgress.bookUrl eq bookUrl) }
    }

    fun has(userId: Long, bookUrl: String): Boolean = transaction {
        UserBooks.selectAll()
            .where { (UserBooks.userId eq userId) and (UserBooks.bookUrl eq bookUrl) }
            .count() > 0
    }

    fun listBooks(userId: Long): List<Pair<Book, UserBookMeta?>> = transaction {
        UserBooks.selectAll().where { UserBooks.userId eq userId }
            .orderBy(UserBooks.pinned to SortOrder.DESC, UserBooks.addedAt to SortOrder.DESC)
            .mapNotNull { row ->
                val book = BookRepository.get(row[UserBooks.bookUrl])
                    ?: return@mapNotNull null
                book to UserBookMeta(
                    row[UserBooks.groupName],
                    row[UserBooks.sortOrder],
                    row[UserBooks.addedAt],
                    row[UserBooks.pinned],
                )
            }
    }

    /** 手动置顶/取消置顶 */
    fun setPinned(userId: Long, bookUrl: String, pinned: Boolean): Boolean = transaction {
        UserBooks.update({ (UserBooks.userId eq userId) and (UserBooks.bookUrl eq bookUrl) }) {
            it[UserBooks.pinned] = pinned
        } > 0
    }

    data class UserBookMeta(
        val groupName: String,
        val sortOrder: Long,
        val addedAt: Long,
        val pinned: Boolean,
    )
}
