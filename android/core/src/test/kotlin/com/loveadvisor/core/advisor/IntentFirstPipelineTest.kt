package com.loveadvisor.core.advisor

import com.google.gson.*
import com.loveadvisor.core.api.*
import org.junit.Assert.*
import org.junit.Test

class IntentFirstPipelineTest {
    private class Wire:HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            requests.add(request)
            val data=JsonParser.parseString(request.body).asJsonObject
            if(request.url==ProviderPresets.JEV_ENDPOINT) {
                val q=data["questions"].asJsonObject
                val answers=if(q.has("true_intent"))mapOf("true_intent" to mapOf("choice" to "seek_explanation"),"danger_level" to mapOf("score" to 2),"best_action" to mapOf("choice" to "explain")) else (0..5).associate { "candidate_$it" to mapOf("score" to 3) }
                return ApiResponse(200,Gson().toJson(mapOf("answers" to answers)))
            }
            val response=Gson().toJson(mapOf("replies" to AdvisorFixtures.valid().replies.map { mapOf("strategy" to it.strategy.name,"text" to it.text) }))
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to response))))))
        }
    }
    @Test fun judgmentPrecedesDraftAndScoringCannotReplaceItAndRefreshReusesIt() {
        val wire=Wire();val advisor=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire))
        val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),AdvisorFixtures.snapshot())
        val profile=JevProfile(apiKey="fake")
        val batch=advisor.generate(input,profile,emptyList(),Cancellation())
        assertEquals(3,wire.requests.size)
        assertEquals(ProviderPresets.JEV_ENDPOINT,wire.requests[0].url)
        val first=JsonParser.parseString(wire.requests[0].body).asJsonObject
        assertEquals(7,first["questions"].asJsonObject.size())
        val content=JsonParser.parseString(wire.requests[1].body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString
        assertEquals(batch.analysis.intent,JsonParser.parseString(content).asJsonObject["intentAssessment"].asJsonObject["intent"].asString)
        assertEquals(6,JsonParser.parseString(wire.requests[2].body).asJsonObject["questions"].asJsonObject.size())
        advisor.generate(input,profile,emptyList(),Cancellation())
        assertEquals(5,wire.requests.size)
        advisor.generate(input.copy(background="新的已确认事实"),profile,emptyList(),Cancellation())
        assertEquals(8,wire.requests.size)
    }
    @Test fun weakDistributionReportsUnknownRatherThanPretendingToKnow() {
        val labels=(0..5).associate { "$it" to "意图$it" }
        val weak=JsonObject().apply { addProperty("choice","0");add("probabilities",Gson().toJsonTree(labels.keys.associateWith { 1.0/6 })) }
        assertTrue(IntentSummary.describe(weak,labels).contains("信息不足"))
        weak.remove("probabilities")
        assertEquals("意图0",IntentSummary.describe(weak,labels))
    }
    @Test fun customAssessmentIsIndependentOfReplyStyleAndFinalScorerCannotRewriteIntent() {
        val requests=mutableListOf<ApiRequest>()
        val wire=object:HttpTransport {
            override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
                requests.add(request)
                val envelope=JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray
                val data=JsonParser.parseString(envelope[1].asJsonObject["content"].asString).asJsonObject
                val output=if(request.url.contains("judge.example")) {
                    if(data["candidates"].asJsonArray.size()==0) {
                        assertFalse(data.has("replyStyle"));assertFalse(data.has("responsePolicy"))
                        """{"intent":"可能在寻求明确的解释","risk":2,"action":"先解释已知原因"}"""
                    } else """{"scores":[80,70,60,50,40,30]}"""
                } else {
                    assertEquals("可能在寻求明确的解释",data["intentAssessment"].asJsonObject["intent"].asString)
                    Gson().toJson(mapOf("replies" to AdvisorFixtures.valid().replies.map { mapOf("strategy" to it.strategy.name,"text" to it.text) }))
                }
                return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to output))))))
            }
        }
        val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),AdvisorFixtures.snapshot(),replyStyle="温柔一点"),JevProfile(apiKey="fake",custom=true,endpoint="https://judge.example/v1",model="judge"),emptyList(),Cancellation())
        assertEquals(3,requests.size);assertEquals("可能在寻求明确的解释",batch.analysis.intent)
        assertEquals(6,batch.replies.size)
    }
}
