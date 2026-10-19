package com.loveadvisor.core.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.loveadvisor.core.analysis.MessageJudgmentService
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SelectedJudgmentContextTest {
    private val answers="""{"answers":{"true_intent":{"choice":"seek_explanation"},"best_action":{"choice":"explain"},"danger_level":{"score":1}}}"""
    private class Wire(private val answers:String):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            requests.add(request)
            return ApiResponse(200,if(request.url==ProviderPresets.JEV_ENDPOINT)answers else Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to answers))))))
        }
    }
    private fun snapshot(kind:ChatKind)=ChatSnapshot("com.tencent.mm",7,ConversationIdentity("conversation","对象",kind,true),listOf(
        ChatMessage("selected",Speaker.OTHER,"甲","然后呢"),
        ChatMessage("later-other",Speaker.OTHER,"乙","晚点再说"),
        ChatMessage("later-me",Speaker.ME,null,"我刚刚查到了")))
    @Test fun judgmentUsesFullReadContextAndKeepsSelectedOtherEvenWhenMeSpokeLast() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(custom in listOf(false,true))for(last in listOf(Speaker.ME,Speaker.OTHER)) {
            val original=snapshot(kind)
            val s=original.copy(messages=original.messages.dropLast(1)+original.messages.last().copy(speaker=last));val wire=Wire(answers)
            // This asserts the public context boundary before the endpoint contract.
            val context=MessageJudgmentService.context(s,"selected")
            assertEquals(listOf("selected","later-other","later-me"),context.messages.map { it.id })
            val result=JevJudgeClient(wire).inspect(JevProfile(apiKey="fake",endpoint="https://example.test/v1",model="judge",custom=custom),context,"",Cancellation(),"selected")
            assertEquals("selected",result.messageId)
            assertEquals(listOf("selected","later-other","later-me"),result.evidence.map { it.id })
            val body=JsonParser.parseString(wire.requests.single().body).asJsonObject
            val state:JsonObject=if(custom)JsonParser.parseString(body["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject else body["state"].asJsonObject
            assertEquals("selected",state["targetMessageId"].asString)
            assertEquals("然后呢",state["targetMessageText"].asString)
            assertEquals("REPLY_TARGET",state["responseMode"].asString)
            assertEquals(listOf("selected","later-other","later-me"),state["chat"].asJsonObject["messages"].asJsonArray.map { it.asJsonObject["id"].asString })
            assertEquals(if(last==Speaker.ME)"me" else "other",state["chat"].asJsonObject["latest_from"].asString)
        }
    }
    @Test fun missingOwnOrDuplicateTargetNeverCallsTheModel() {
        val s=snapshot(ChatKind.DIRECT)
        for((sample,target) in listOf(s to "missing",s to "later-me",s.copy(messages=s.messages+s.messages.first()) to "selected")) {
            val wire=Wire(answers)
            try { JevJudgeClient(wire).inspect(JevProfile(apiKey="fake"),sample,"",Cancellation(),target);fail("Invalid target must fail before network") } catch(_:IllegalArgumentException) {}
            assertTrue(wire.requests.isEmpty())
        }
    }
}


