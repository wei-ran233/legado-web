package io.legado.app.help.http

import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppLog
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 服务器端 BackstageWebView 实现：
 * 1. 自动 HTTP 抓取页面 HTML（若未传入）；
 * 2. 原生极速解密常见漫画站点的 AES params 加密图片列表，并注入 DOM；
 * 3. 支持 WebJs 规则与页面级 JavaScript 执行（Rhino）；
 * 4. 始终安全降级，不再抛出 UnsupportedOperationException。
 */
class BackstageWebView(
    val url: String? = null,
    val html: String? = null,
    val javaScript: String? = null,
    val tag: String? = null,
    val headerMap: Map<String, String>? = null,
    val sourceRegex: String? = null,
    val overrideUrlRegex: String? = null,
    val cacheFirst: Boolean = false,
    val delayTime: Long = 0,
    val timeout: Long = 10_000,
    val result: String? = null,
    val isRule: Boolean = false,
) {

    fun getStrResponse(): StrResponse {
        val currentUrl = url ?: ""

        // 1. 若没有传入 html，则使用 OkHttp 拉取页面
        var pageHtml = if (!html.isNullOrBlank()) {
            html
        } else if (currentUrl.isNotBlank()) {
            fetchHtml(currentUrl, headerMap)
        } else {
            ""
        }

        // 2. 规则解析场景 (AnalyzeRule.getWebJsResult)
        if (isRule && !javaScript.isNullOrBlank()) {
            val evalResult = evalRuleJs(javaScript, pageHtml, result, currentUrl)
            return StrResponse(currentUrl, evalResult)
        }

        // 3. 漫画站点动态 params 加密（如滴答漫画 ddmanhua 等）内置极速 AES 解密
        val comicImages = decryptComicParams(pageHtml)
        if (!comicImages.isNullOrEmpty()) {
            pageHtml = injectComicImages(pageHtml, comicImages)
            return StrResponse(currentUrl, pageHtml)
        }

        // 4. 页面级 webJs 执行
        if (!javaScript.isNullOrBlank()) {
            pageHtml = evalPageWebJs(javaScript, pageHtml, currentUrl)
        }

        // 5. 安全返回
        return StrResponse(currentUrl, pageHtml)
    }

    private fun fetchHtml(targetUrl: String, headers: Map<String, String>?): String {
        return runBlocking {
            try {
                okHttpClient.newCallStrResponse {
                    headers?.let { addHeaders(it) }
                    url(targetUrl)
                }.body ?: ""
            } catch (e: Exception) {
                AppLog.put("BackstageWebView fetchHtml failed for $targetUrl: ${e.message}", e)
                ""
            }
        }
    }

    private fun evalRuleJs(jsStr: String, currentHtml: String, currentResult: String?, baseUrl: String): String {
        return try {
            val bindings = buildScriptBindings { b ->
                b["html"] = currentHtml
                b["result"] = currentResult ?: ""
                b["baseUrl"] = baseUrl
            }
            val scope = RhinoScriptEngine.getRuntimeScope(bindings)
            RhinoScriptEngine.eval(jsStr, scope)?.toString() ?: (currentResult ?: "")
        } catch (e: Throwable) {
            AppLog.put("BackstageWebView Rhino evalRuleJs error: ${e.message}", e)
            currentResult ?: ""
        }
    }

    private fun evalPageWebJs(webJs: String, currentHtml: String, baseUrl: String): String {
        return try {
            val bindings = buildScriptBindings { b ->
                b["html"] = currentHtml
                b["baseUrl"] = baseUrl
            }
            val scope = RhinoScriptEngine.getRuntimeScope(bindings)
            val res = RhinoScriptEngine.eval(webJs, scope)
            if (res is String && res.isNotBlank()) res else currentHtml
        } catch (e: Throwable) {
            AppLog.put("BackstageWebView Rhino evalPageWebJs error: ${e.message}", e)
            currentHtml
        }
    }

    companion object {
        private val paramsRegex = Pattern.compile("""params\s*=\s*['"]([A-Za-z0-9+/=]{20,})['"]""")

        private val defaultComicKeys = listOf(
            "9S8\$vJnU2ANeSRoF", // 滴答漫画、白嫖怪等通用 key
            "1234567890123456",
            "1234567887654321",
            "0123456789abcdef",
            "comic_aes_key16"
        )

        /**
         * 解密常见漫画网站的 params 加密串
         */
        fun decryptComicParams(html: String): List<String>? {
            if (html.isBlank()) return null
            val matcher = paramsRegex.matcher(html)
            if (!matcher.find()) return null
            val paramsB64 = matcher.group(1) ?: return null

            val raw = try {
                Base64.getDecoder().decode(paramsB64.trim())
            } catch (e: Exception) {
                return null
            }
            if (raw.size <= 16) return null
            val iv = raw.copyOfRange(0, 16)
            val cipherText = raw.copyOfRange(16, raw.size)

            for (keyStr in defaultComicKeys) {
                try {
                    val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                    val decrypted = String(cipher.doFinal(cipherText), Charsets.UTF_8)
                    if (decrypted.startsWith("{") && decrypted.contains("images")) {
                        val map = GSON.fromJson(decrypted, Map::class.java)
                        val images = (map["images"] as? List<*>)?.filterIsInstance<String>()
                        if (!images.isNullOrEmpty()) {
                            return images
                        }
                    }
                } catch (e: Exception) {
                    // 尝试下一个 key
                }
            }
            return null
        }

        /**
         * 将解析出的漫画图片列表注入到 HTML 中，满足 #cp_img@html 规则
         */
        fun injectComicImages(html: String, images: List<String>): String {
            val imagesHtml = buildString {
                append("\n<div id=\"images\">\n")
                for (img in images) {
                    append("  <div class=\"item\" id=\"cp_img\">\n")
                    append("    <img class=\"lazy-read\" src=\"$img\" data-src=\"$img\" referrerPolicy=\"no-referrer\" id=\"cp_image\">\n")
                    append("  </div>\n")
                }
                append("</div>\n")
            }

            return when {
                html.contains("<div id=\"images\"", ignoreCase = true) -> {
                    html.replace(Regex("""<div id="images"[^>]*>[\s\S]*?</div>""", RegexOption.IGNORE_CASE), imagesHtml)
                }
                html.contains("</body>", ignoreCase = true) -> {
                    html.replace(Regex("""</body>""", RegexOption.IGNORE_CASE), "$imagesHtml\n</body>")
                }
                else -> html + imagesHtml
            }
        }

        fun clearCache(tag: String) {
            // 桩实现：无缓存可清
        }
    }
}
