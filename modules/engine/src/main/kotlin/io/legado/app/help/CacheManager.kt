package io.legado.app.help

import androidx.collection.LruCache
import io.legado.app.model.analyzeRule.QueryTTF
import io.legado.app.utils.memorySize
import java.util.concurrent.ConcurrentHashMap

private val queryTTFMap = LruCache<String, QueryTTF>(4)

/**
 * 最多只缓存50M的数据,防止OOM
 */
private val memoryLruCache = object : LruCache<String, Any>(1024 * 1024 * 50) {

    override fun sizeOf(key: String, value: Any): Int {
        return value.toString().memorySize()
    }

}

/**
 * CacheManager 的持久化后端。engine 默认使用内存实现，服务器启动时注入 Exposed 实现。
 */
interface CachePersistence {
    fun get(key: String): CacheEntry?
    fun insert(key: String, value: String, deadline: Long)
    fun delete(key: String)
}

data class CacheEntry(val key: String, val value: String?, val deadline: Long)

internal object MemoryOnlyCachePersistence : CachePersistence {
    private val store = ConcurrentHashMap<String, CacheEntry>()
    override fun get(key: String): CacheEntry? = store[key]
    override fun insert(key: String, value: String, deadline: Long) {
        store[key] = CacheEntry(key, value, deadline)
    }
    override fun delete(key: String) {
        store.remove(key)
    }
}

object AppCacheManager {

    fun put(key: String, queryTTF: QueryTTF) {
        queryTTFMap.put(key, queryTTF)
    }

    fun getQueryTTF(key: String): QueryTTF? {
        return queryTTFMap[key]
    }

    fun clearSourceVariables() {
        memoryLruCache.snapshot().keys.forEach {
            if (it.startsWith("v_")
                || it.startsWith("userInfo_")
                || it.startsWith("loginHeader_")
                || it.startsWith("sourceVariable_")
            ) {
                memoryLruCache.remove(it)
            }
        }
    }

}


@Suppress("unused")
object CacheManager {

    /** 持久化后端，服务器启动时替换为 Exposed 实现 */
    @Volatile
    var persistence: CachePersistence = MemoryOnlyCachePersistence

    /**
     * saveTime 单位为秒
     */
    @JvmOverloads
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val deadline =
            if (saveTime == 0) 0L else System.currentTimeMillis() + saveTime * 1000
        val valueStr = value.toString()
        putMemory(key, valueStr)
        persistence.insert(key, valueStr, deadline)
    }

    fun putMemory(key: String, value: Any) {
        memoryLruCache.put(key, value)
    }

    //从内存中获取数据 使用lruCache
    fun getFromMemory(key: String): Any? {
        return memoryLruCache[key]
    }

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    fun get(key: String): String? {
        getFromMemory(key)?.let {
            if (it is String) return it
        }
        val cache = persistence.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value?.also {
                putMemory(key, it)
            }
        }
        return null
    }

    fun get(key: String, onlyDisk: Boolean): String? {
        if (!onlyDisk) {
            return get(key)
        }
        val cache = persistence.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value
        }
        return null
    }

    fun getInt(key: String): Int? {
        getFromMemory(key)?.let {
            if (it is Int) return it
        }
        return get(key, true)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        getFromMemory(key)?.let {
            if (it is Long) return it
        }
        return get(key, true)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        getFromMemory(key)?.let {
            if (it is Double) return it
        }
        return get(key, true)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        getFromMemory(key)?.let {
            if (it is Float) return it
        }
        return get(key, true)?.toFloatOrNull()
    }

    fun putFile(key: String, value: String, saveTime: Int = 0) {
        put(key, value, saveTime)
    }

    fun getFile(key: String): String? {
        return get(key, true)
    }

    fun delete(key: String) {
        persistence.delete(key)
        deleteMemory(key)
    }
}

@Suppress("unused")
object WebCacheManager {
    @JvmOverloads
    fun put(key: String, value: String, saveTime: Int = 0) {
        CacheManager.put(key, value, saveTime)
    }

    fun putMemory(key: String, value: String) {
        memoryLruCache.put(key, value)
    }

    fun getFromMemory(key: String): String? {
        return memoryLruCache[key]?.toString()
    }

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    fun get(key: String): String? {
        return CacheManager.get(key)
    }

    fun get(key: String, onlyDisk: Boolean): String? {
        return CacheManager.get(key, onlyDisk)
    }

    fun putFile(key: String, value: String, saveTime: Int = 0) {
        CacheManager.putFile(key, value, saveTime)
    }

    fun getFile(key: String): String? {
        return CacheManager.getFile(key)
    }

    fun delete(key: String) {
        CacheManager.delete(key)
    }
}
