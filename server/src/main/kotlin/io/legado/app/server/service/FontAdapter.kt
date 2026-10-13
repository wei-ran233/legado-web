package io.legado.app.server.service

/**
 * 正文字符反混淆适配与反爬残留清理工具
 * 解决杰奇 CMS 等小说站点 PUA 字体混淆（豆腐块 □ 乱码）与防爬拦截提示。
 */
object FontAdapter {
    private const val PUA_START = 0xE800
    private const val MAPPING =
        "的一是了我不人在他有这个上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美乳阴液茎欲呻肉交性胸私穴淫臀舔射脱裸骚唇"
    private val PUA_END = PUA_START + MAPPING.length - 1

    /**
     * 自动将私有区 (PUA) 防爬混淆字符转换为标准中文汉字，并清理防爬垃圾提示
     */
    fun adaptText(text: String): String {
        if (text.isEmpty()) return text
        var hasPua = false
        for (i in text.indices) {
            val code = text[i].code
            if (code in PUA_START..PUA_END) {
                hasPua = true
                break
            }
        }
        val converted = if (!hasPua) {
            text
        } else {
            val sb = StringBuilder(text.length)
            for (i in text.indices) {
                val c = text[i]
                val code = c.code
                if (code in PUA_START..PUA_END) {
                    sb.append(MAPPING[code - PUA_START])
                } else {
                    sb.append(c)
                }
            }
            sb.toString()
        }
        return sanitizeAntiBot(converted)
    }

    /**
     * 检查正文是否为损坏或含反爬拦截提示的内容
     */
    fun isAntiBotContent(text: String): Boolean {
        return text.contains("为了使用完整的阅读功能") ||
                text.contains("章节内容不支持该浏览器显示") ||
                text.contains("请考虑使用〔Chrome") ||
                text.contains("请考虑使用〔Safari") ||
                text.contains("请考虑使用〔Edge")
    }

    /**
     * 剥离正文末尾残留的源站反爬提示、表情及截断错误提示
     */
    fun sanitizeAntiBot(text: String): String {
        if (!text.contains("为了使用完整的阅读功能") &&
            !text.contains("章节内容不支持该浏览器显示") &&
            !text.contains("(ò﹏ò)") &&
            !text.contains("（内容加载失败！）")
        ) {
            return text
        }

        var clean = text
        clean = clean.replace(Regex("""(?m)^.*(为了使用完整的阅读功能|章节内容不支持该浏览器显示|请考虑使用.*?(Chrome|Safari|Edge|浏览器)|原生浏览器阅读).*$\r?\n?"""), "")
        clean = clean.replace(Regex("""(?m)^.*\(ò﹏ò\).*$\r?\n?"""), "")
        clean = clean.replace(Regex("""(?m)^.*（内容加载失败！\).*$\r?\n?"""), "")
        clean = clean.replace(Regex("""(?m)^.*谢谢!!!.*$\r?\n?"""), "")
        return clean.trimEnd()
    }
}
