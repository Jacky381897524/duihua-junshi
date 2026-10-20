package com.loveadvisor.core.api

import com.google.gson.GsonBuilder
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VisionParsingTest {
    private val conversation="""{"kind":"DIRECT","title":"测试联系人","messages":[{"speaker":"OTHER","kind":"TEXT","text":"准备吃饭"}]}"""

    private class Wire(content:Any?,finishReason:Any?="stop",refusal:Any?=null,val cancelAfterResponse:Boolean=false):HttpTransport {
        var calls=0
        private val response=GsonBuilder().serializeNulls().create().toJson(mapOf(
            "choices" to listOf(mapOf("finish_reason" to finishReason,"message" to mapOf("role" to "assistant","content" to content,"refusal" to refusal)))
        ))
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            calls++
            if(cancelAfterResponse)cancel.cancel()
            return ApiResponse(200,response)
        }
    }

    private fun read(wire:Wire,cancel:Cancellation=Cancellation(),limit:Int=50):VisionConversation {
        return try {
            VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",cancel,ChatApps.WECHAT,limit)
        } finally { assertEquals("one manual read must issue only one request",1,wire.calls) }
    }

    private fun rejects(wire:Wire) {
        try { read(wire);fail("unsafe or unusable response must be rejected") } catch(_:ApiException) {}
    }

    @Test fun fileCardsDoNotDiscardAdjacentOriginalTextOrChangeSpeakers() {
        val result=read(Wire("""{"kind":"DIRECT","title":"测试联系人","messages":[
            {"speaker":"ME","kind":"FILE","text":"IMG_fixture_a.jpg 4.2MB"},
            {"speaker":"ME","kind":"FILE","text":"IMG_fixture_b.jpg 28.8MB"},
            {"speaker":"ME","kind":"TEXT","text":"准备吃饭"},
            {"speaker":"OTHER","kind":"TEXT","text":"一起去"},
            {"speaker":"OTHER","kind":"TEXT","text":"出发"}
        ]}"""))
        assertEquals(listOf("准备吃饭","一起去","出发"),result.messages.map { it.text })
        assertEquals(listOf(Speaker.ME,Speaker.OTHER,Speaker.OTHER),result.messages.map { it.speaker })
        assertTrue(result.messages.all { it.kind==MessageKind.TEXT })
        assertTrue(result.mediaIds.isEmpty())
    }

    @Test fun explicitNullEmotionDoesNotDiscardVisibleImageOrSticker() {
        val result=read(Wire("""{"kind":"DIRECT","title":"测试联系人","messages":[
            {"speaker":"OTHER","kind":"IMAGE","text":null,"description":"桌上的一盘面","emotion":null,"y":300},
            {"speaker":"ME","kind":"STICKER","description":"小猫点头","emotion":null,"y":400}
        ]}"""))
        assertEquals(listOf("[图片] 桌上的一盘面","[表情包] 小猫点头"),result.messages.map { it.text })
        assertEquals(setOf("vision-0","vision-1"),result.mediaIds)
        assertEquals(mapOf("vision-0" to 300.0,"vision-1" to 400.0),result.positions)
    }

    @Test fun malformedRowsAreSkippedWithoutPromotingUnknownKindsToText() {
        val result=read(Wire("""{"kind":"DIRECT","title":"测试联系人","messages":[
            null,23,[],"not a row",
            {"speaker":"OTHER","kind":null,"text":"不能当正文"},
            {"speaker":"OTHER","kind":{},"text":"不能当正文"},
            {"speaker":"OTHER","kind":"VIDEO","text":"不能当正文"},
            {"speaker":"OTHER","kind":"TEXT","text":null},
            {"speaker":"OTHER","kind":"TEXT","text":23},
            {"speaker":"OTHER","kind":"TEXT","text":false},
            {"speaker":"OTHER","kind":"TEXT","text":[]},
            {"speaker":"OTHER","kind":"TEXT","text":"  "},
            {"speaker":"UNKNOWN","kind":"TEXT","text":"不能猜左右"},
            {"speaker":"ME","clipped":true,"kind":"TEXT","text":"不能补齐"},
            {"speaker":"ME","text":"保留原文 😊"}
        ]}"""))
        assertEquals(1,result.messages.size)
        assertEquals("保留原文 😊",result.messages.single().text)
        assertEquals(Speaker.ME,result.messages.single().speaker)
        assertEquals(MessageKind.TEXT,result.messages.single().kind)
        assertTrue(result.mediaIds.isEmpty())
    }

    @Test fun mediaWithoutVisibleDescriptionIsSkippedWithoutInventingContent() {
        for(description in listOf("null","\"\"","\"  \"","[]","{}")) {
            val result=read(Wire("""{"kind":"DIRECT","title":"测试联系人","messages":[
                {"speaker":"ME","kind":"IMAGE","description":$description},
                {"speaker":"OTHER","kind":"STICKER"},
                {"speaker":"OTHER","kind":"TEXT","text":"看到了"}
            ]}"""))
            assertEquals(listOf("看到了"),result.messages.map { it.text })
            assertTrue(result.mediaIds.isEmpty())
        }
    }

    @Test fun malformedOptionalMetadataDoesNotRejectReadableGroupText() {
        for(memberCount in listOf("null","\"未知\"","{}","[]","true","1.5","100001")) {
            val result=read(Wire("""{"kind":"GROUP","title":"测试群","memberCount":$memberCount,"messages":[
                {"speaker":"OTHER","sender":null,"kind":"TEXT","text":"准备出发","quote":null,"y":null},
                {"speaker":"OTHER","sender":{},"kind":"TEXT","text":"收到","quote":{"text":null,"author":[]},"y":"未知"}
            ]}"""))
            assertEquals(listOf("准备出发","收到"),result.messages.map { it.text })
            assertNull(result.memberCount)
            assertTrue(result.messages.all { it.sender==null && it.quote==null })
            assertTrue(result.positions.isEmpty())
        }
    }

    @Test fun optionalNullVoiceTextKeepsUntranscribedMarkerAndOriginalTypedText() {
        val result=read(Wire("""{"kind":"DIRECT","title":"测试联系人","messages":[
            {"speaker":"OTHER","kind":"VOICE","text":null},
            {"speaker":"ME","kind":"TEXT","text":" 原文空格保持 "}
        ]}"""))
        assertEquals(listOf("[语音，未转写]"," 原文空格保持 "),result.messages.map { it.text })
        assertEquals(MessageKind.VOICE,result.messages.first().kind)
    }

    @Test fun explicitTextContentBlocksAreJoinedWithoutAlteringTheJson() {
        val split=conversation.indexOf("准备")
        val blocks=listOf(mapOf("type" to "text","text" to conversation.take(split)),mapOf("type" to "text","text" to conversation.drop(split)))
        assertEquals("准备吃饭",read(Wire(blocks)).messages.single().text)
    }

    @Test fun refusalCannotPublishEvenWhenContentContainsValidConversationJson() {
        for(refusal in listOf<Any?>("无法处理",mapOf("reason" to "refused"),true))rejects(Wire(conversation,refusal=refusal))
    }

    @Test fun truncatedFilteredAndToolOutputsCannotPublishPartialConversation() {
        for(reason in listOf("length","content_filter","tool_calls","function_call","unknown"))rejects(Wire(conversation,finishReason=reason))
        assertEquals("准备吃饭",read(Wire(conversation,finishReason=null)).messages.single().text)
    }

    @Test fun contentShapesAndNarrativeCannotBeSalvagedIntoAConversation() {
        for(content in listOf<Any?>(null,"",mapOf("text" to conversation),emptyList<Any>(),
            listOf(mapOf("type" to "text","text" to conversation),mapOf("type" to "refusal","refusal" to "拒绝")),
            listOf(mapOf("type" to "image_url","text" to conversation)),
            listOf(mapOf("type" to "text","text" to mapOf("value" to conversation))),
            "说明：$conversation","$conversation 后续说明"))rejects(Wire(content))
    }

    @Test fun conversationAndSpeakerSafetyStillRejectsUnusableResponses() {
        for(content in listOf(
            """{"kind":"UNKNOWN","title":"测试","messages":[{"speaker":"OTHER","text":"你好"}]}""",
            """{"kind":"DIRECT","title":" ","messages":[{"speaker":"OTHER","text":"你好"}]}""",
            """{"kind":"DIRECT","title":"测试","messages":[{"speaker":"UNKNOWN","text":"你好"}]}""",
            """{"kind":"DIRECT","title":"测试","messages":[{"speaker":"ME","kind":"FILE","text":"文件"}]}""",
            """{"kind":"DIRECT","title":"测试","messages":[]}"""
        ))rejects(Wire(content))
        val tooMany=(1..51).joinToString(",") { """{"speaker":"OTHER","text":"合成消息$it"}""" }
        rejects(Wire("""{"kind":"DIRECT","title":"测试","messages":[$tooMany]}"""))
        rejects(Wire("""{"kind":"GROUP","title":"测试群","messages":[$tooMany]}"""))
    }

    @Test fun validComposerCoordinatesArePreservedAsOriginalImagePixels() {
        val content="""{"kind":"DIRECT","title":"测试联系人","messages":[{"speaker":"OTHER","text":"你好"}],"composer":[90,1900,860,2000]}"""
        assertEquals(RectI(90,1900,860,2000),read(Wire(content)).composer)
    }

    @Test fun cancellationAfterTransportCannotPublishOrBecomeAParseFailure() {
        try { read(Wire(conversation,cancelAfterResponse=true));fail("cancelled response must not publish") }
        catch(_:CancellationException) {}
    }
}
