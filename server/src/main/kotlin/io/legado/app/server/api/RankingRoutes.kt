package io.legado.app.server.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.legado.app.server.db.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.rankingRoutes() {
    // ===== 阅读时长心跳上报 =====
    post("/reading/record") {
        val user = call.requireUser() ?: return@post
        val body = call.receive<Map<String, Any?>>()
        val bookUrl = body["bookUrl"] as? String ?: return@post call.respondError(400, "缺少 bookUrl")
        val bookName = (body["bookName"] as? String).orEmpty()
        val author = (body["author"] as? String).orEmpty()
        val durationRaw = (body["durationSeconds"] as? Number)?.toLong() ?: 0L

        // 防御性校验：单次上报限制在 1 秒到 3600 秒之间
        val durationSeconds = durationRaw.coerceIn(0L, 3600L)
        if (durationSeconds <= 0) {
            call.respondJson(mapOf("status" to "ok", "savedSeconds" to 0))
            return@post
        }

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val now = System.currentTimeMillis()

        transaction {
            val existing = UserReadingRecords.selectAll()
                .where { (UserReadingRecords.userId eq user.id) and (UserReadingRecords.bookUrl eq bookUrl) and (UserReadingRecords.date eq today) }
                .firstOrNull()

            if (existing != null) {
                val current = existing[UserReadingRecords.readDuration]
                UserReadingRecords.update({
                    (UserReadingRecords.userId eq user.id) and (UserReadingRecords.bookUrl eq bookUrl) and (UserReadingRecords.date eq today)
                }) {
                    it[readDuration] = current + durationSeconds
                    it[lastReadAt] = now
                    if (bookName.isNotBlank()) it[UserReadingRecords.bookName] = bookName
                    if (author.isNotBlank()) it[UserReadingRecords.author] = author
                }
            } else {
                UserReadingRecords.insert {
                    it[userId] = user.id
                    it[UserReadingRecords.bookUrl] = bookUrl
                    it[UserReadingRecords.bookName] = bookName
                    it[UserReadingRecords.author] = author
                    it[date] = today
                    it[readDuration] = durationSeconds
                    it[lastReadAt] = now
                }
            }
        }

        call.respondJson(mapOf("status" to "ok", "savedSeconds" to durationSeconds))
    }

    // ===== 个人阅读统计成就 =====
    get("/reading/stats") {
        val user = call.requireUser() ?: return@get
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val stats = transaction {
            val myRecords = UserReadingRecords.selectAll()
                .where { UserReadingRecords.userId eq user.id }
                .toList()

            val todayDur = myRecords.filter { it[UserReadingRecords.date] == today }
                .sumOf { it[UserReadingRecords.readDuration] }

            val totalDur = myRecords.sumOf { it[UserReadingRecords.readDuration] }
            val bookCount = myRecords.map { it[UserReadingRecords.bookUrl] }.distinct().size.toLong()

            // 全站读者排名计算（仅统计开启公开榜单的用户）
            val participatingUserIds = Users.selectAll()
                .where { Users.participateRanking eq true }
                .map { it[Users.id] }
                .toSet()

            val allUserTotals = UserReadingRecords.selectAll().toList()
                .filter { it[UserReadingRecords.userId] in participatingUserIds }
                .groupBy { it[UserReadingRecords.userId] }
                .mapValues { (_, recs) -> recs.sumOf { it[UserReadingRecords.readDuration] } }
                .toList()
                .sortedByDescending { it.second }

            val rank = if (user.participateRanking && totalDur > 0) {
                val rankIndex = allUserTotals.filter { it.second > 0 }.indexOfFirst { it.first == user.id }
                if (rankIndex >= 0) rankIndex + 1 else 0
            } else {
                0
            }

            mapOf(
                "todayDuration" to todayDur,
                "totalDuration" to totalDur,
                "bookCount" to bookCount,
                "rank" to rank,
                "participateRanking" to user.participateRanking,
                "totalReaders" to Users.selectAll().where { Users.participateRanking eq true }.count(),
            )
        }

        call.respondJson(stats)
    }

    // ===== 读者阅读时长排行榜 =====
    get("/ranking/users") {
        call.requireUser() ?: return@get
        val period = call.request.queryParameters["period"] ?: "today" // today | week | total
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val weekStartStr = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val list = transaction {
            val allRecords = UserReadingRecords.selectAll().toList()

            val filtered = when (period) {
                "today" -> allRecords.filter { it[UserReadingRecords.date] == todayStr }
                "week" -> allRecords.filter { it[UserReadingRecords.date] >= weekStartStr }
                else -> allRecords
            }

            data class ReaderInfo(val username: String, val nickname: String, val displayName: String, val role: String)
            // 仅纳入选择公开参与排行榜的书友
            val userMap = Users.selectAll()
                .where { Users.participateRanking eq true }
                .associate {
                    val u = it[Users.username]
                    val nick = it[Users.nickname]
                    val disp = if (nick.isNotBlank()) nick else u
                    it[Users.id] to ReaderInfo(u, nick, disp, it[Users.role])
                }

            filtered.groupBy { it[UserReadingRecords.userId] }
                .mapNotNull { (uid, recs) ->
                    val userInfo = userMap[uid] ?: return@mapNotNull null
                    val dur = recs.sumOf { it[UserReadingRecords.readDuration] }
                    val books = recs.map { it[UserReadingRecords.bookUrl] }.distinct().size.toLong()
                    Triple(uid, userInfo, Pair(dur, books))
                }
                .sortedByDescending { it.third.first }
                .take(50)
                .mapIndexed { index, (uid, userInfo, stats) ->
                    mapOf(
                        "rank" to index + 1,
                        "userId" to uid,
                        "username" to userInfo.username,
                        "nickname" to userInfo.nickname,
                        "displayName" to userInfo.displayName,
                        "role" to userInfo.role,
                        "totalDuration" to stats.first,
                        "bookCount" to stats.second,
                    )
                }
        }

        call.respondJson(list)
    }

    // ===== 热门阅读书籍排行榜 =====
    get("/ranking/books") {
        val user = call.requireUser() ?: return@get
        val type = call.request.queryParameters["type"] ?: "duration" // duration | popularity

        val list = transaction {
            val allReading = UserReadingRecords.selectAll().toList()
            val allShelf = UserBooks.selectAll().toList()

            // 按书籍聚会在读时长和读者数
            val readingStats = allReading.groupBy { it[UserReadingRecords.bookUrl] }
                .mapValues { (_, recs) ->
                    val totalDur = recs.sumOf { it[UserReadingRecords.readDuration] }
                    val readers = recs.map { it[UserReadingRecords.userId] }.distinct().size.toLong()
                    Pair(totalDur, readers)
                }

            // 按书籍聚合书架收藏数
            val shelfStats = allShelf.groupBy { it[UserBooks.bookUrl] }
                .mapValues { (_, recs) -> recs.size.toLong() }

            // 当前用户的书架集合
            val myShelfUrls = allShelf.filter { it[UserBooks.userId] == user.id }
                .map { it[UserBooks.bookUrl] }
                .toSet()

            val allUrls = (readingStats.keys + shelfStats.keys).distinct()
            val booksMap = Books.selectAll().associateBy { it[Books.bookUrl] }

            val fallbackMeta = allReading.groupBy { it[UserReadingRecords.bookUrl] }
                .mapValues { (_, recs) ->
                    val first = recs.first()
                    Pair(first[UserReadingRecords.bookName], first[UserReadingRecords.author])
                }

            val scoredBooks = allUrls.mapNotNull { url ->
                val rStat = readingStats[url] ?: Pair(0L, 0L)
                val shelfCount = shelfStats[url] ?: 0L
                val book = booksMap[url]
                val name = book?.get(Books.name) ?: fallbackMeta[url]?.first ?: return@mapNotNull null
                val author = book?.get(Books.author) ?: fallbackMeta[url]?.second ?: ""
                val rawCover = book?.get(Books.coverUrl)
                val coverUrl = when {
                    rawCover.isNullOrBlank() -> ""
                    rawCover.startsWith("data:") || rawCover.startsWith("/api/cover") -> rawCover
                    else -> "/api/cover?path=${URLEncoder.encode(rawCover, "UTF-8")}"
                }

                mapOf(
                    "bookUrl" to url,
                    "name" to name,
                    "author" to author,
                    "coverUrl" to coverUrl,
                    "type" to (book?.get(Books.type) ?: 0),
                    "latestChapterTitle" to (book?.get(Books.latestChapterTitle) ?: ""),
                    "totalDuration" to rStat.first,
                    "readerCount" to rStat.second,
                    "shelfCount" to shelfCount,
                    "onShelf" to myShelfUrls.contains(url),
                )
            }

            val eligibleBooks = if (type == "duration") {
                scoredBooks.filter { ((it["totalDuration"] as? Long) ?: 0L) > 0L }
            } else {
                scoredBooks.filter {
                    (((it["readerCount"] as? Long) ?: 0L) > 0L) || (((it["shelfCount"] as? Long) ?: 0L) > 0L)
                }
            }

            val sorted = if (type == "duration") {
                eligibleBooks.sortedWith(
                    compareByDescending<Map<String, Any>> { (it["totalDuration"] as? Long) ?: 0L }
                        .thenByDescending { (it["readerCount"] as? Long) ?: 0L }
                        .thenByDescending { (it["shelfCount"] as? Long) ?: 0L }
                )
            } else {
                eligibleBooks.sortedWith(
                    compareByDescending<Map<String, Any>> { (it["readerCount"] as? Long) ?: 0L }
                        .thenByDescending { (it["shelfCount"] as? Long) ?: 0L }
                        .thenByDescending { (it["totalDuration"] as? Long) ?: 0L }
                )
            }

            sorted.take(50).mapIndexed { index, item ->
                item + ("rank" to (index + 1))
            }
        }

        call.respondJson(list)
    }

    // ===== 重置全站排行榜（仅管理员） =====
    post("/ranking/reset") {
        call.requireAdmin() ?: return@post
        transaction {
            UserReadingRecords.deleteAll()
        }
        call.respondJson(mapOf("status" to "ok", "message" to "全站排行榜已清零重置"))
    }
}
