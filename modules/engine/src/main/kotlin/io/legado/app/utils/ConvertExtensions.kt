@file:Suppress("unused")

package io.legado.app.utils

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

/**
 * 数据类型转换、单位转换
 *
 * @author 李玉江[QQ:1023694760]
 * @since 2014-4-18
 */
@Suppress("MemberVisibilityCanBePrivate")
object ConvertUtils {
    const val GB: Long = 1073741824
    const val MB: Long = 1048576
    const val KB: Long = 1024

    fun toInt(obj: Any): Int {
        return kotlin.runCatching {
            Integer.parseInt(obj.toString())
        }.getOrDefault(-1)
    }

    fun toInt(bytes: ByteArray): Int {
        var result = 0
        var byte: Byte
        for (i in bytes.indices) {
            byte = bytes[i]
            result += (byte.toInt() and 0xFF).shl(8 * i)
        }
        return result
    }

    fun toFloat(obj: Any): Float {
        return kotlin.runCatching {
            java.lang.Float.parseFloat(obj.toString())
        }.getOrDefault(-1f)
    }

    fun toString(objects: Array<Any>, tag: String): String {
        val sb = StringBuilder()
        for (`object` in objects) {
            sb.append(`object`)
            sb.append(tag)
        }
        return sb.toString()
    }

    fun formatFileSize(length: Long): String {
        if (length <= 0) return "0"
        val units = arrayOf("b", "kb", "M", "G", "T")
        //计算单位的，原理是利用lg,公式是 lg(1024^n) = nlg(1024)，最后 nlg(1024)/lg(1024) = n。
        val digitGroups = (log10(length.toDouble()) / log10(1024.0)).toInt()
        //计算原理是，size/单位值。单位值指的是:比如说b = 1024,KB = 1024^2
        return DecimalFormat("#,##0.##").format(length / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    @JvmOverloads
    fun toString(`is`: InputStream, charset: String = "utf-8"): String {
        val sb = StringBuilder()
        kotlin.runCatching {
            val reader = BufferedReader(InputStreamReader(`is`, charset))
            while (true) {
                val line = reader.readLine()
                if (line == null) {
                    break
                } else {
                    sb.append(line).append("\n")
                }
            }
            reader.close()
            `is`.close()
        }
        return sb.toString()
    }

}

val Int.hexString: String
    get() = Integer.toHexString(this)

