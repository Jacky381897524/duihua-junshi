package com.loveadvisor.core.advisor

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Real wire boundaries: an obsolete topic must not leak into drafting or ranking. */
class ConversationTimeRegressionTest {
    private val gson=Gson()
    private fun snapshot(kind:ChatKind=ChatKind.DIRECT):ChatSnapshot {
        val now=System.currentTimeMillis()
        val rows=listOf(
            mapOf("id" to "old","speaker" to "OTHER","sender" to "甲","text" to "前天的电影几点开场", "kind" to "TEXT","time" to mapOf("label" to "前天 18:00","epochMillis" to now-48*3600000L)),
            mapOf("id" to "new","speaker" to "OTHER","sender" to "乙","text" to "我刚下班", "kind" to "TEXT","time" to mapOf("label" to "今天","epochMillis" to now-60000)))
        return gson.fromJson(gson.toJson(mapOf("packageName" to "com.tencent.mm","windowId" to 7,"identity" to ConversationIdentity("c","对象",kind,true),"source" to "NODES","contextLimit" to 50,"messages" to rows)),ChatSnapshot::class.java)
    }
    private class Wire(private val custom:Boolean=false,private val target:String="new"):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            requests.add(request)
            val replies=Strategy.entries.take(6).mapIndexed { i,s -> mapOf("strategy" to s.name,"text" to "刚下班先歇一会儿$i","targetMessageId" to target,"topic" to "下班") }
            val body=if(request.url==ProviderPresets.JEV_ENDPOINT)mapOf("answers" to ((0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))))
                else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to if(custom && request.url.contains("judge.example"))"""{"intent":"聊下班","risk":1,"action":"先休息","scores":[80,75,70,65,60,55]}""" else Gson().toJson(mapOf("replies" to replies))))))
            return ApiResponse(200,Gson().toJson(body))
        }
    }
    @Test fun newJudgmentExcludesTwoDayOldTopicsInBothReplyModesAndChatKinds() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(natural in listOf(false,true))for(custom in listOf(false,true)) {
            val wire=Wire(custom)
            val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot(kind),naturalRepliesEnabled=natural)
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input,JevProfile(apiKey="fake",custom=custom,endpoint="https://judge.example/v1",model="judge"),emptyList(),Cancellation())
            assertEquals(2,wire.requests.size)
            val draft=JsonParser.parseString(JsonParser.parseString(wire.requests[0].body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
            val judgeBody=JsonParser.parseString(wire.requests[1].body).asJsonObject
            val judge=if(custom)JsonParser.parseString(judgeBody["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject else judgeBody["state"].asJsonObject
            assertEquals(listOf("new"),draft["messages"].asJsonArray.map { it.asJsonObject["id"].asString })
            val judged=if(custom)judge["chat"].asJsonArray else judge["chat"].asJsonObject["messages"].asJsonArray
            assertEquals(listOf("new"),judged.map { it.asJsonObject["id"].asString })
        }
    }
    @Test fun explicitHistoricalReplyStillGeneratesSixCandidatesWithItsHistoricalContext() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(natural in listOf(false,true)) {
            val wire=Wire(target="old")
            val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot(kind),replyTarget=ReplyTarget("old"),naturalRepliesEnabled=natural)
            val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input,JevProfile(apiKey="fake"),emptyList(),Cancellation())
            assertEquals(6,batch.replies.size)
            assertTrue(batch.replies.all { it.targetMessageId=="old" })
            val draft=JsonParser.parseString(JsonParser.parseString(wire.requests[0].body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
            assertEquals("true",draft["historicalTarget"].asString)
            assertEquals(listOf("old","new"),draft["messages"].asJsonArray.map { it.asJsonObject["id"].asString })
        }
    }
    @Test fun expiredReadProducesActionableFeedbackWithoutPayingForAnotherModelCall() {
        val original=snapshot()
        val wire=Wire()
        val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),original.copy(messages=original.messages.take(1)))
        try {
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input,JevProfile(apiKey="fake"),emptyList(),Cancellation())
            fail("An expired read must not generate replies as if the topic were current")
        } catch(e:IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("指定回复")) }
        assertTrue(wire.requests.isEmpty())
        assertEquals(2,original.messages.size)
    }
}
