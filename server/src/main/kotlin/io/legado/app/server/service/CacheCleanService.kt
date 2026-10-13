package io.legado.app.server.service

import io.legado.app.server.config.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

data class CacheStats(
    val imageCount: Long,
    val imageBytes: Long,
    val bookCacheCount: Long,
    val bookCacheBytes: Long,
)

data class CacheCleanResult(
    val deletedFiles: Long,
    val freedBytes: Long,
    val cleanedEmptyDirs: Long,
)

/**
 * 缓存自动维护与定时清理服务
 * 支持定时清理超过指定天数的图片代理缓存与书籍正文缓存，并清理空目录与损坏无效文件
 */
object CacheCleanService {
    private val logger = LoggerFactory.getLogger(CacheCleanService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isStarted = false

    fun getStats(): CacheStats {
        var imgCount = 0L
        var imgBytes = 0L
        ServerConfig.imageCacheDir.walkTopDown().filter { it.isFile }.forEach {
            imgCount++
            imgBytes += it.length()
        }

        var bookCount = 0L
        var bookBytes = 0L
        ServerConfig.bookCacheDir.walkTopDown().filter { it.isFile }.forEach {
            bookCount++
            bookBytes += it.length()
        }

        return CacheStats(
            imageCount = imgCount,
            imageBytes = imgBytes,
            bookCacheCount = bookCount,
            bookCacheBytes = bookBytes
        )
    }

    fun clean(expireDays: Int = 7): CacheCleanResult {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(expireDays.toLong())
        var deletedCount = 0L
        var freedBytes = 0L
        var emptyDirsCleaned = 0L

        // 1. 清理超期或损坏的图片缓存
        runCatching {
            ServerConfig.imageCacheDir.walkTopDown().filter { it.isFile }.forEach { f ->
                if (f.length() == 0L || f.lastModified() < cutoff) {
                    val len = f.length()
                    if (f.delete()) {
                        deletedCount++
                        freedBytes += len
                    }
                }
            }
        }

        // 2. 清理超期的正文缓存及空目录
        runCatching {
            ServerConfig.bookCacheDir.walkTopDown().filter { it.isFile }.forEach { f ->
                if (f.length() == 0L || f.lastModified() < cutoff) {
                    val len = f.length()
                    if (f.delete()) {
                        deletedCount++
                        freedBytes += len
                    }
                }
            }

            // 清理空书籍目录
            ServerConfig.bookCacheDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val files = dir.walkTopDown().filter { it.isFile }.toList()
                if (files.isEmpty()) {
                    if (dir.deleteRecursively()) {
                        emptyDirsCleaned++
                    }
                }
            }
        }

        val mb = "%.2f".format(freedBytes / (1024.0 * 1024.0))
        logger.info("[CacheCleaner] 缓存清理完成：共删除 {} 个文件，清理 {} 个空目录，释放 {} MB 空间", deletedCount, emptyDirsCleaned, mb)
        return CacheCleanResult(deletedCount, freedBytes, emptyDirsCleaned)
    }

    /**
     * 启动后台定时巡检清理协程
     */
    fun startScheduledCleaner(intervalHours: Long = 24, expireDays: Int = 7) {
        if (isStarted) return
        isStarted = true

        scope.launch {
            // 启动延时 10 秒后执行首次轻量巡检，避免影响启动并发
            delay(10_000)
            while (isActive) {
                try {
                    logger.info("[CacheCleaner] 触发定时自动缓存清理巡检（保留 {} 天以内有效缓存）...", expireDays)
                    clean(expireDays)
                } catch (e: Exception) {
                    logger.error("[CacheCleaner] 定时清理异常", e)
                }
                delay(TimeUnit.HOURS.toMillis(intervalHours))
            }
        }
    }
}
