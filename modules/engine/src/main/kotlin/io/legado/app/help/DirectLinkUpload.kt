package io.legado.app.help

import androidx.annotation.Keep

/**
 * 服务器端桩实现：原为 Android 端直链上传配置（webdav/图床等）。
 * 服务器不提供口令上传功能，getExpiryDate 返回 0 表示口令永久有效。
 */
object DirectLinkUpload {

    const val ruleFileName = "directLinkUploadRule.json"

    @Keep
    data class Rule(
        var uploadUrl: String = "",
        var downloadUrlRule: String = "",
        var expiryDate: Int = 0,
        var summary: String = ""
    )

    fun getRule(): Rule = Rule()

    fun getExpiryDate(): Int = getRule().expiryDate

    fun getSummary(): String = getRule().summary
}
