package io.legado.app.utils

import java.io.File

/**
 * 服务器端精简版 ACache（原为 Android 文件缓存工具类）。
 * 仅保留字符串读写能力，用于 exploreKinds / jsLib 等文件缓存。
 */
@Suppress("unused")
class ACache private constructor(private val cacheDir: File) {

    companion object {
        @JvmOverloads
        fun get(cacheName: String = "ACache"): ACache {
            return ACache(File(io.legado.app.help.AppEnv.cacheDir, cacheName))
        }

        fun get(cacheDir: File): ACache {
            return ACache(cacheDir)
        }
    }

    private fun keyFile(key: String): File {
        val safeName = MD5Utils.md5Encode(key)
        return File(cacheDir, safeName)
    }

    @JvmOverloads
    fun put(key: String, value: String, saveTime: Int = 0) {
        try {
            cacheDir.mkdirs()
            val f = keyFile(key)
            if (saveTime > 0) {
                val deadline = System.currentTimeMillis() + saveTime * 1000L
                f.writeText(deadline.toString() + "\n" + value)
            } else {
                f.writeText(value)
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    fun getAsString(key: String): String? {
        return try {
            val f = keyFile(key)
            if (!f.exists()) return null
            val text = f.readText()
            val newline = text.indexOf('\n')
            if (newline > 0 && text.substring(0, newline).toLongOrNull() != null) {
                val deadline = text.substring(0, newline).toLong()
                if (deadline < System.currentTimeMillis()) {
                    f.delete()
                    return null
                }
                text.substring(newline + 1)
            } else {
                text
            }
        } catch (e: Exception) {
            null
        }
    }

    fun remove(key: String): Boolean {
        return keyFile(key).delete()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
