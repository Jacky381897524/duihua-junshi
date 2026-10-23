package com.loveadvisor.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.api.ApiProfile
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
import com.loveadvisor.core.api.*
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.session.*

class RealityModeRegressionTest {
    @Test fun realityModeSurvivesInputAndHasPriorityInPromptData() {
        val snapshot=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("read","小林",ChatKind.DIRECT,true),listOf(ChatMessage("1",Speaker.OTHER,null,"吃饭了吗")))
        val raw=Gson().toJsonTree(AdvisorInput(ApiProfile(baseUrl="https://example.com",apiKey="fake"),snapshot,naturalRepliesEnabled=true)).asJsonObject
        raw.addProperty("realisticRepliesEnabled",true)
        val input=Gson().fromJson(raw,AdvisorInput::class.java)
        val payload=JsonParser.parseString(UnifiedPrompt.data(input)).asJsonObject
        assertEquals("REALISTIC",payload["expressionMode"]?.asString)
    }
    private class Wire:HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            requests.add(request)
            val content=if(request.url==ProviderPresets.JEV_ENDPOINT) {
                mapOf("answers" to ((0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))))
            } else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to Gson().toJson(mapOf("replies" to listOf("吃了呀","刚吃完","还没呢","你先吃","吃啥好","嗯嗯").map { mapOf("strategy" to "ACKNOWLEDGE","text" to it,"targetMessageId" to "1","topic" to "吃饭") }))))))
            return ApiResponse(200,Gson().toJson(content))
        }
    }
    @Test fun realModePublishesSixShortRepliesInDirectAndGroupWithOnlyOneDraftAndOneJudge() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(natural in listOf(false,true)) {
            val snapshot=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("read","小林",kind,true),listOf(ChatMessage("1",Speaker.OTHER,"小林","吃饭了吗")))
            val wire=Wire();val personal=PersonalContext(ownExamples=listOf("嗯嗯 好呀"))
            val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake").copy(selectedModelId="model"),snapshot,replyTarget=ReplyTarget("1"),naturalRepliesEnabled=natural,realisticRepliesEnabled=true,personalContext=personal)
            val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker));val pending=coordinator.begin(snapshot,input.replyTarget)!!
            val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input,JevProfile(apiKey="fake"),emptyList(),Cancellation())
            assertTrue(batch.realisticRepliesEnabled);assertTrue(coordinator.complete(pending.requestTicket,batch));assertFalse(coordinator.state.loading)
            assertEquals(6,coordinator.state.current!!.batch.replies.size);assertEquals(2,wire.requests.size)
            val draft=JsonParser.parseString(wire.requests.first().body).asJsonObject["messages"].asJsonArray
            assertTrue(draft[0].asJsonObject["content"].asString.contains("本次启用真人感"))
            assertFalse(draft[0].asJsonObject["content"].asString.contains("仅本次启用去AI味"))
            val draftData=JsonParser.parseString(draft[1].asJsonObject["content"].asString).asJsonObject
            val judge=JsonParser.parseString(wire.requests.last().body).asJsonObject
            assertEquals(13,judge["questions"].asJsonObject.size())
            assertEquals(draftData["personaContext"],judge["state"].asJsonObject["personaContext"])
            assertEquals("REALISTIC",judge["state"].asJsonObject["expressionMode"].asString)
            assertEquals(personal,batch.personalContext)
        }
    }
    @Test fun offDoesNotIncludePersonalDataAndNaturalAndLegacyKeepTheirOwnRules() {
        val snapshot=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("read","小林",ChatKind.DIRECT,true),listOf(ChatMessage("1",Speaker.OTHER,null,"吃饭了吗")))
        val base=AdvisorInput(ApiProfile(baseUrl="https://example.com",apiKey="fake"),snapshot,personalContext=PersonalContext(ownExamples=listOf("私人样本")))
        val data=UnifiedPrompt.data(base)
        assertFalse(data.contains("私人样本"));assertEquals(ExpressionMode.LEGACY,RealityReplyPolicy.mode(false,false));assertEquals(ExpressionMode.NATURAL,RealityReplyPolicy.mode(false,true))
        assertFalse(UnifiedPrompt.systemFor(base).contains("本次启用真人感"))
        assertTrue(UnifiedPrompt.systemFor(base.copy(naturalRepliesEnabled=true)).contains(NaturalReplyPolicy.instruction))
    }
}
