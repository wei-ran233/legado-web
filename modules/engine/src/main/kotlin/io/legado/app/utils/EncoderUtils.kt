package io.legado.app.utils

import java.util.Base64

/**
 * 编码工具 escape base64
 * flags 兼容 android.util.Base64 语义（仅识别 URL_SAFE / NO_WRAP / NO_PADDING 位），默认标准编码
 */
@Suppress("unused")
object EncoderUtils {

    private const val DEFAULT = 0
    private const val NO_WRAP = 2
    private const val URL_SAFE = 8
    private const val NO_PADDING = 1

    fun escape(src: String): String {
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122) {
                tmp.append(char)
                continue
            }

            val prefix = when {
                charCode < 16 -> "%0"
                charCode < 256 -> "%"
                else -> "%u"
            }
            tmp.append(prefix).append(charCode.toString(16))
        }
        return tmp.toString()
    }

    private fun decoder(flags: Int): Base64.Decoder =
        if (flags and URL_SAFE != 0) Base64.getUrlDecoder() else Base64.getMimeDecoder()

    private fun encoder(flags: Int): Base64.Encoder {
        var enc = if (flags and URL_SAFE != 0) Base64.getUrlEncoder() else Base64.getEncoder()
        if (flags and NO_WRAP == 0 && flags and DEFAULT != 0) {
            // android DEFAULT 会在 76 字符处换行，这里等价使用 mime 编码
            enc = Base64.getMimeEncoder(76, "\n".toByteArray())
        }
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = DEFAULT): String {
        val bytes = base64DecodeToByteArray(str, flags)
        return String(bytes)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = NO_WRAP): String? {
        return base64Encode(str.toByteArray(), flags)
    }

    @JvmOverloads
    fun base64Encode(bytes: ByteArray, flags: Int = NO_WRAP): String {
        return encoder(flags).encodeToString(bytes)
    }

    @JvmOverloads
    fun base64DecodeToByteArray(str: String, flags: Int = DEFAULT): ByteArray {
        return decoder(flags).decode(str)
    }
}
