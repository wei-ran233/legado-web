package io.legado.app.server.db

import org.jetbrains.exposed.sql.Table

/**
 * 用户表：仅管理员创建用户
 */
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val nickname = varchar("nickname", 64).default("")
    val passwordHash = varchar("password_hash", 128)
    /** ADMIN / USER */
    val role = varchar("role", 16).default("USER")
    val totpSecret = text("totp_secret").nullable()
    val totpEnabled = bool("totp_enabled").default(false)
    /** 上一次成功验证的时间窗口步数（防重放） */
    val totpLastStep = long("totp_last_step").default(0)
    /** 密码版本号：改密/禁用时 +1，使全部已发 JWT 失效 */
    val tokenVer = long("token_ver").default(0)
    val disabled = bool("disabled").default(false)
    val mustChangePassword = bool("must_change_password").default(false)
    val participateRanking = bool("participate_ranking").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * 恢复码表：10 个一次性恢复码（BCrypt 哈希存储）
 */
object RecoveryCodes : Table("recovery_codes") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").index()
    val codeHash = varchar("code_hash", 128)
    val used = bool("used").default(false)
    override val primaryKey = PrimaryKey(id)
}

/**
 * 会话表：refresh token 旋转式管理，可按设备踢出
 */
object Sessions : Table("sessions") {
    val id = varchar("id", 36)
    val userId = long("user_id").index()
    val refreshHash = varchar("refresh_hash", 64)
    val device = varchar("device", 128).default("unknown")
    val ip = varchar("ip", 64).default("")
    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at")
    val expiresAt = long("expires_at")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(id)
}

/**
 * 登录防爆破：(ip + username) 维度失败计数
 */
object LoginAttempts : Table("login_attempts") {
    val key = varchar("key", 160)
    val failCount = integer("fail_count").default(0)
    val lockedUntil = long("locked_until").default(0)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(key)
}

/**
 * 书源表：完整 JSON 存储（与社区书源格式 100% 兼容），enabled 即上架/下架状态
 */
object BookSources : Table("book_sources") {
    val bookSourceUrl = varchar("book_source_url", 512)
    val name = varchar("name", 256)
    val grp = varchar("grp", 256).default("")
    /** 0 文本 1 音频 2 图片 3 下载站 4 视频 */
    val type = integer("type").default(0)
    /** 上架状态：false = 已下架（普通用户不可见不可搜） */
    val enabled = bool("enabled").default(true)
    val customOrder = integer("custom_order").default(0)
    val weight = long("weight").default(0)
    val respondTime = long("respond_time").default(180000)
    val lastUpdateTime = long("last_update_time").default(0)
    /** 完整书源 JSON 原文 */
    val json = text("json")
    val enabledExplore = bool("enabled_explore").default(false).index()
    val hasExploreUrl = bool("has_explore_url").default(false)
    /** 综合可用性评分 (0 ~ 100，默认 80) */
    val availabilityScore = integer("availability_score").default(80)
    /** 检索成功次数 */
    val successCount = integer("success_count").default(0)
    /** 检索失败/超时次数 */
    val failCount = integer("fail_count").default(0)
    /** 是否支持并解析出最新章节 */
    val hasLatestChapter = bool("has_latest_chapter").default(false)
    /** 文本质量（无防爬拦截、无生僻乱码） */
    val isTextClean = bool("is_text_clean").default(true)
    /** 是否可成功获取正文/文章 */
    val canFetchArticle = bool("can_fetch_article").default(true)
    /** 最近一次可用性检测时间戳 */
    val lastCheckedAt = long("last_checked_at").default(0)
    override val primaryKey = PrimaryKey(bookSourceUrl)
}

/**
 * 全局书籍馆藏（书源抓到的书全局共享）
 */
object Books : Table("books") {
    val bookUrl = varchar("book_url", 512)
    val name = varchar("name", 256)
    val author = varchar("author", 128).default("")
    val coverUrl = text("cover_url").nullable()
    /** 书源 URL */
    val origin = varchar("origin", 512).default("")
    val originName = varchar("origin_name", 256).default("")
    val kind = text("kind").nullable()
    val intro = text("intro").nullable()
    val latestChapterTitle = text("latest_chapter_title").nullable()
    val totalChapterNum = integer("total_chapter_num").default(0)
    val tocUrl = text("toc_url").default("")
    /** 书籍自定义变量 JSON */
    val variable = text("variable").nullable()
    /** Book type int（文本/图片/音频…） */
    val type = integer("type").default(0)
    val latestChapterTime = long("latest_chapter_time").default(0)
    val canUpdate = bool("can_update").default(true)
    override val primaryKey = PrimaryKey(bookUrl)
}

/**
 * 全局章节目录
 */
object Chapters : Table("chapters") {
    val bookUrl = varchar("book_url", 512)
    val chapterIndex = integer("index")
    val url = text("url")
    val title = text("title")
    val isVolume = bool("is_volume").default(false)
    val baseUrl = text("base_url").default("")
    val isVip = bool("is_vip").default(false)
    val isPay = bool("is_pay").default(false)
    val resourceUrl = text("resource_url").nullable()
    val tag = text("tag").nullable()
    val wordCount = text("word_count").nullable()
    val start = long("start").nullable()
    val end = long("end").nullable()
    val variable = text("variable").nullable()
    override val primaryKey = PrimaryKey(bookUrl, chapterIndex)
}

/**
 * 个人书架：用户与馆藏的关联
 */
object UserBooks : Table("user_books") {
    val userId = long("user_id")
    val bookUrl = varchar("book_url", 512)
    val groupName = varchar("group_name", 128).default("默认分组")
    val sortOrder = long("sort_order").default(0)
    val addedAt = long("added_at")
    /** 手动置顶：置顶书籍优先占据书架大卡位置 */
    val pinned = bool("pinned").default(false)
    override val primaryKey = PrimaryKey(userId, bookUrl)
}

/**
 * 个人阅读进度
 */
object UserProgress : Table("user_progress") {
    val userId = long("user_id")
    val bookUrl = varchar("book_url", 512)
    val durChapterIndex = integer("dur_chapter_index").default(0)
    val durChapterPos = integer("dur_chapter_pos").default(0)
    val durChapterTime = long("dur_chapter_time").default(0)
    val durChapterTitle = text("dur_chapter_title").default("")
    val durVolumeIndex = integer("dur_volume_index").default(0)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, bookUrl)
}

/**
 * KV 缓存持久层（引擎 CacheManager 后端）
 */
object KvCache : Table("kv_cache") {
    val key = varchar("key", 512)
    val value = text("value").nullable()
    val deadline = long("deadline").default(0)
    override val primaryKey = PrimaryKey(key)
}

/**
 * Cookie 持久层（引擎 CookieStore 后端）
 */
object Cookies : Table("cookies") {
    val domain = varchar("domain", 256)
    val cookie = text("cookie")
    override val primaryKey = PrimaryKey(domain)
}

/**
 * 站点设置
 */
object Settings : Table("settings") {
    val key = varchar("key", 128)
    val value = text("value").nullable()
    override val primaryKey = PrimaryKey(key)
}

/**
 * 用户阅读记录表：按天按书记录阅读时长（秒），支持用户与热门书籍排行榜
 */
object UserReadingRecords : Table("user_reading_records") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").index()
    val bookUrl = varchar("book_url", 512).index()
    val bookName = varchar("book_name", 256).default("")
    val author = varchar("author", 128).default("")
    /** 日期格式 YYYY-MM-DD */
    val date = varchar("date", 10).index()
    /** 累计阅读秒数 */
    val readDuration = long("read_duration").default(0)
    val lastReadAt = long("last_read_at").default(0)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_reading_user_book_date", userId, bookUrl, date)
    }
}
