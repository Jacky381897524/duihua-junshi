package com.loveadvisor.core.advisor

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.api.*
import org.junit.Assert.*
import org.junit.Test

class ReplyStylePromptTest {
    private val style="简短温柔，少用感叹号"
    private fun input():AdvisorInput {
        // Decode a future settings-bound input so the RED test exercises the old real prompt.
        val json=Gson().toJsonTree(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),AdvisorFixtures.snapshot())).asJsonObject
        json.addProperty("replyStyle",style)
        return Gson().fromJson(json,AdvisorInput::class.java)
    }
    private class Wire:HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            cancel.check(); requests.add(request)
            if(request.url==ProviderPresets.JEV_ENDPOINT) {
                val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3.0) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0.0),"best_action" to mapOf("choice" to "acknowledge"))
                return ApiResponse(200,Gson().toJson(mapOf("answers" to answers)))
            }
            val detail=JsonParser.parseString(request.body).asJsonObject["messages"].toString().contains("whenToUse")
            val content=if(detail) Gson().toJson(ReplyDetail("承接原话","适合分享时","不要猜测","继续聊","留空间","尊重拒绝")) else Gson().toJson(mapOf("replies" to AdvisorFixtures.valid().replies.map { mapOf("strategy" to it.strategy.name,"text" to it.text) }))
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
        }
    }
    private fun data(request:ApiRequest)=JsonParser.parseString(JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
    @Test fun unifiedInitialAndRefreshCarryCurrentContactStyle() {
        for(common in listOf(null,AdvisorFixtures.valid().analysis)) {
            val payload=JsonParser.parseString(UnifiedPrompt.data(input(),common,listOf("旧回复"))).asJsonObject
            assertEquals("The style must reach all six suggestions on both paths",style,payload["replyStyle"]?.asString)
        }
    }
    @Test fun automaticInitialAndRefreshCarryStyleWithoutChangingFixedJudge() {
        for(previous in listOf(emptyList(),listOf("旧回复"))) {
            val wire=Wire()
            val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input(),JevProfile(apiKey="fake"),previous,Cancellation())
            assertEquals(6,batch.replies.size)
            assertEquals(style,data(wire.requests.first())["replyStyle"]?.asString)
            assertEquals(ProviderPresets.JEV_ENDPOINT,wire.requests.last().url)
            assertEquals(ProviderPresets.JEV_MODEL,JsonParser.parseString(wire.requests.last().body).asJsonObject["model"].asString)
        }
    }
    @Test fun detailCarriesStyleForFollowupAdvice() {
        val wire=Wire()
        AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).detail(input(),AdvisorFixtures.valid().replies.first(),Cancellation())
        assertEquals(style,data(wire.requests.single())["replyStyle"]?.asString)
    }
}
