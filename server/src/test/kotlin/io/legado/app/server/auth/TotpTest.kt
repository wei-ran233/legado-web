package io.legado.app.server.auth

import org.junit.Test
import org.junit.Assert.*

class TotpTest {

    @Test
    fun testTotpGenerationAndVerification() {
        val secret = TotpService.generateSecret()
        val nowStep = TotpService.getNowStep()
        
        val method = TotpService.javaClass.getDeclaredMethod("codeAt", String::class.java, Long::class.java)
        method.isAccessible = true
        
        // 测试当前窗口 (offset 0)
        val codeCurrent = method.invoke(TotpService, secret, nowStep) as String
        val verifiedCurrent = TotpService.verifyCode(secret, codeCurrent)
        assertNotNull("Current step should verify", verifiedCurrent)
        assertEquals(nowStep, verifiedCurrent)

        // 测试前一个窗口 (offset -1)
        val codePrev = method.invoke(TotpService, secret, nowStep - 1) as String
        val verifiedPrev = TotpService.verifyCode(secret, codePrev)
        assertNotNull("Previous step (offset -1) should verify", verifiedPrev)
        assertEquals(nowStep - 1, verifiedPrev)

        // 测试后一个窗口 (offset +1, 手机时间稍快)
        val codeNext = method.invoke(TotpService, secret, nowStep + 1) as String
        val verifiedNext = TotpService.verifyCode(secret, codeNext)
        assertNotNull("Next step (offset +1) should verify", verifiedNext)
        assertEquals(nowStep + 1, verifiedNext)

        // 测试空格与分隔符容错
        val codeWithSpace = "${codeCurrent.substring(0, 3)} ${codeCurrent.substring(3)}"
        val verifiedWithSpace = TotpService.verifyCode(secret.lowercase(), codeWithSpace)
        assertNotNull("Code with space and lowercase secret should verify", verifiedWithSpace)

        // 测试超出窗口 (offset +25, 超过默认 ±15 窗口) 应当校验失败
        val codeFarFuture = method.invoke(TotpService, secret, nowStep + 25) as String
        val verifiedFar = TotpService.verifyCode(secret, codeFarFuture)
        assertNull("Far future code (offset +25) should fail", verifiedFar)
    }

    @Test
    fun testRfc6238Vectors() {
        val method = TotpService.javaClass.getDeclaredMethod("codeAt", String::class.java, Long::class.java)
        method.isAccessible = true

        // RFC 6238 官方测试密钥（ASCII: "12345678901234567890"）
        // Base32 编码为: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
        val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // Time = 59s -> step = 1 -> code = 287082
        val code1 = method.invoke(TotpService, rfcSecret, 1L) as String
        println("RFC Vector step 1: $code1, expected: 287082")
        assertEquals("287082", code1)

        // Time = 1111111109s -> step = 37037036L -> code = 081804
        val code2 = method.invoke(TotpService, rfcSecret, 37037036L) as String
        println("RFC Vector step 37037036: $code2, expected: 081804")
        assertEquals("081804", code2)

        // Time = 1234567890s -> step = 41152263L -> code = 005924
        val code3 = method.invoke(TotpService, rfcSecret, 41152263L) as String
        println("RFC Vector step 41152263: $code3, expected: 005924")
        assertEquals("005924", code3)
    }
}
