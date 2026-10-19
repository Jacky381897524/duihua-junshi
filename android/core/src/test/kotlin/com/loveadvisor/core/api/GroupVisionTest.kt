package com.loveadvisor.core.api
import com.google.gson.Gson
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class GroupVisionTest {
    private fun read(pkg:String,rows:List<Map<String,Any?>>,limit:Int=50):VisionConversation {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            val content=Gson().toJson(mapOf("kind" to "GROUP","title" to "朋友群(12)","memberCount" to 12,"messages" to rows))
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
        } }
        return VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation(),pkg,limit)
    }
    @Test fun everyPlatformPreservesEachMembersTextQuoteAndSticker() {
        val rows=listOf(
            mapOf("speaker" to "OTHER","sender" to "꧁甲✨꧂","kind" to "TEXT","text" to "今晚几点集合","quote" to mapOf("author" to "乙","text" to "六点半")),
            mapOf("speaker" to "OTHER","sender" to "乙","kind" to "STICKER","description" to "企鹅点头"),
            mapOf("speaker" to "ME","kind" to "TEXT","text" to "我六点到")
        )
        for(pkg in ChatApps.packages) {
            val result=read(pkg,rows)
            assertEquals(ChatKind.GROUP,result.kind);assertEquals(12,result.memberCount)
            assertEquals("꧁甲✨꧂",result.messages[0].sender);assertEquals("乙",result.messages[1].sender)
            assertEquals(ChatQuote("六点半","乙"),result.messages[0].quote)
            assertEquals(MessageKind.STICKER,result.messages[1].kind)
            assertEquals(Speaker.ME,result.messages.last().speaker)
        }
    }
    @Test fun configuredGroupLimitDoesNotSilentlyTruncateToFifty() {
        val rows=(1..75).map { mapOf<String,Any?>("speaker" to "OTHER","sender" to "甲","kind" to "TEXT","text" to "第${it}条") }
        assertEquals(75,read(ChatApps.WECHAT,rows,100).messages.size)
        try { read(ChatApps.WECHAT,rows,50);fail("超出选择范围应明确失败") } catch(_:ApiException) {}
    }
    @Test fun clippedMessageDoesNotRejectTheOtherCompleteRowsOrInventAMember() {
        val result=read(ChatApps.WECHAT,listOf(mapOf("speaker" to "OTHER","clipped" to true),mapOf("speaker" to "OTHER","kind" to "TEXT","text" to "这是一条完整消息")))
        assertEquals(1,result.messages.size);assertNull(result.messages.single().sender)
        assertEquals("这是一条完整消息",result.messages.single().text)
    }
}
