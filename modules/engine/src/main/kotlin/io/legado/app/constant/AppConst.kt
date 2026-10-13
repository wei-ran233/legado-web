package io.legado.app.constant

import androidx.annotation.Keep
import io.legado.app.help.AppEnv
import org.apache.commons.lang3.time.FastDateFormat
import java.util.UUID

/**
 * 服务器端 JVM 版 AppConst：移除 PackageManager/Settings 等 Android 依赖。
 */
@Suppress("ConstPropertyName", "unused")
object AppConst {

    const val APP_TAG = "Legado"

    const val UA_NAME = "User-Agent"

    const val MAX_THREAD = 9

    const val DEFAULT_WEBDAV_ID = -1L

    val timeFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("HH:mm")
    }

    val dateFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("yyyy/MM/dd HH:mm")
    }

    val fileNameFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("yy-MM-dd-HH-mm-ss")
    }

    const val imagePathKey = "imagePath"

    /** 服务器实例固定 ID（对应原 androidId 语义） */
    val androidId: String by lazy {
        val file = java.io.File(AppEnv.filesDir, "instance_id")
        if (file.exists()) {
            file.readText().trim()
        } else {
            val id = UUID.randomUUID().toString()
            file.parentFile?.mkdirs()
            file.writeText(id)
            id
        }
    }

    val appInfo: AppInfo by lazy {
        AppInfo(versionCode = 1L, versionName = io.legado.app.BuildConfig.VERSION_NAME)
    }

    val charsets =
        arrayListOf("UTF-8", "GB2312", "GB18030", "GBK", "Unicode", "UTF-16", "UTF-16LE", "ASCII")

    @Keep
    data class AppInfo(
        var versionCode: Long = 0L,
        var versionName: String = "",
        var appVariant: String = "SERVER"
    )
}
