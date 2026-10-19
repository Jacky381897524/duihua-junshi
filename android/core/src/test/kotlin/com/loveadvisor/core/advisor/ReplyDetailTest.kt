package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test
class ReplyDetailTest {
    @Test fun shortStructuredFieldsRequiredAndNoEssayFallback() {
        val json="""{"why":"接住已知内容","whenToUse":"轻松聊天时","caution":"不要编造承诺","positive":"顺势确认","ambiguous":"不追问","rejection":"尊重拒绝"}"""
        assertEquals("尊重拒绝",ReplyDetailCodec.decode(json).rejection)
        for(raw in listOf("论文",json.replace("接住已知内容","长".repeat(61)),json.replace("\"caution\":\"不要编造承诺\",","")))try { ReplyDetailCodec.decode(raw);fail() } catch(_:Exception) {}
    }
}
