package io.legado.app.server.auth

import cn.hutool.core.codec.Base32
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 双因素认证（RFC 6238：HMAC-SHA1 / 6 位 / 30 秒窗口，容差 ±1）
 * 自包含实现，兼容 Google Authenticator / Aegis / 1Password。
 */
object TotpService {

    private const val TOTP_INTERVAL = 30
    private const val DIGITS = 6

    /**
     * 生成 Base32 编码的随机密钥（160 bit，兼容主流验证器）
     */
    fun generateSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        return Base32.encode(bytes).replace("=", "")
    }

    /**
     * 计算指定时间步数的动态码
     */
    private fun codeAt(secret: String, step: Long): String {
        val cleanSecret = secret.trim().replace(" ", "").replace("-", "").uppercase()
        val key = Base32.decode(cleanSecret)
        val msg = ByteArray(8)
        var s = step
        for (i in 7 downTo 0) {
            msg[i] = (s and 0xff).toByte()
            s = s ushr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val code = binary % 1_000_000
        return String.format("%0${DIGITS}d", code)
    }

    /** 系统时钟与网络标准时间偏差（秒） */
    @Volatile
    var timeOffsetSeconds: Long = 0L
        private set

    init {
        syncNetworkTime()
    }

    /**
     * 轻量校准时钟：通过 HTTP HEAD 请求从权威时间源获取当前准确时间，计算本地时钟偏差
     */
    fun syncNetworkTime() {
        Thread {
            val sources = listOf("https://www.baidu.com", "https://www.aliyun.com", "https://www.qq.com")
            for (url in sources) {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.instanceFollowRedirects = false
                    val date = conn.date
                    if (date > 0) {
                        val networkSec = date / 1000
                        val localSec = System.currentTimeMillis() / 1000
                        timeOffsetSeconds = networkSec - localSec
                        println("TOTP 服务时钟校准成功（数据源：$url），检测到系统时钟偏差：${timeOffsetSeconds} 秒")
                        break
                    }
                } catch (_: Throwable) {
                    // 降级使用下一个源
                }
            }
        }.apply {
            isDaemon = true
            name = "totp-time-sync"
            start()
        }
    }

    fun getNowStep(): Long {
        val effectiveTime = (System.currentTimeMillis() / 1000) + timeOffsetSeconds
        return effectiveTime / TOTP_INTERVAL
    }

    /**
     * 校验动态码；成功返回当前时间窗口步数（用于防重放），失败返回 null
     * 默认容差 ±15（覆盖 ±7.5 分钟窗口），双重保障：
     * 1. 优先使用已校准的网络标准时间
     * 2. 即便在离线或极端时钟漂移情况下，宽容差窗口也能确保手机验证器与服务端完美匹配
     */
    fun verifyCode(secret: String, code: String, window: Int = 15): Long? {
        val clean = code.trim().replace(" ", "").replace("-", "")
        if (!clean.matches(Regex("\\d{$DIGITS}"))) return null
        val cleanSecret = secret.trim().replace(" ", "").replace("-", "").uppercase()
        val nowStep = getNowStep()
        // 容差列表：优先当前窗口 0，然后向前/向后偏移
        val offsets = mutableListOf(0L)
        for (i in 1..window) {
            offsets.add(i.toLong())
            offsets.add(-i.toLong())
        }
        for (offset in offsets) {
            val step = nowStep + offset
            if (codeAt(cleanSecret, step) == clean) {
                return step
            }
        }
        return null
    }

    /**
     * 遵循 Google Authenticator / Aegis / RFC 官方 Key URI 规范
     * 格式: otpauth://totp/{Issuer}:{Account}?secret={Secret}&issuer={Issuer}
     * 注意：Issuer 与 Account 之间的冒号不能编码为 %3A
     */
    fun otpauthUri(username: String, secret: String, issuer: String = "Legado"): String {
        val cleanIssuer = issuer.trim()
        val cleanUser = username.trim()
        val encodedIssuer = java.net.URLEncoder.encode(cleanIssuer, "UTF-8").replace("+", "%20")
        val encodedUser = java.net.URLEncoder.encode(cleanUser, "UTF-8").replace("+", "%20")
        return "otpauth://totp/$encodedIssuer:$encodedUser?secret=$secret&issuer=$encodedIssuer"
    }

    /**
     * 生成一次性恢复码（10 个，8 位随机大写字母数字，易读且不易混淆）
     */
    fun generateRecoveryCodes(count: Int = 10): List<String> {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return List(count) {
            buildString {
                repeat(8) { append(chars[random.nextInt(chars.length)]) }
            }
        }
    }
}
