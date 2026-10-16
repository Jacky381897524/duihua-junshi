package com.loveadvisor.core.api
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticApiTest {
    private class Wire(val handler:(ApiRequest)->ApiResponse):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse { cancel.check(); requests.add(request); return handler(request) }
    }
    @Test fun visionSendsImageAndKeepsOriginalSpeakerText() {
        val w=Wire { ApiResponse(200,"""{"choices":[{"message":{"content":"{\"kind\":\"DIRECT\",\"title\":\"小雨\",\"messages\":[{\"speaker\":\"OTHER\",\"text\":\"今天过得怎么样 😊\"},{\"speaker\":\"ME\",\"text\":\"还不错\"}]}"}}]}""") }
        val result=VisionClient(w).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation())
        assertEquals("今天过得怎么样 😊",result.messages[0].text)
        assertEquals(com.loveadvisor.core.model.Speaker.ME,result.messages[1].speaker)
        val body=JsonParser.parseString(w.requests.single().body).asJsonObject
        assertEquals("qwen/qwen2.5-vl-72b-instruct",body["model"].asString)
        assertTrue(body["messages"].toString().contains("image_url"))
    }
    @Test fun visionRejectsUncertainSpeakersAndGroupsWithoutGuessing() {
        for(content in listOf("""{"kind":"GROUP","title":"群","messages":[]}""","""{"kind":"DIRECT","title":"小雨","messages":[{"speaker":"UNKNOWN","text":"你好"}]}""")) {
            val w=Wire { ApiResponse(200,com.google.gson.Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))))) }
            try { VisionClient(w).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation()); fail("must reject") } catch(_:ApiException) {}
        }
    }
    @Test fun catalogExcludesKnownTextOnlyVisionModelsButAllowsUnknownCapability() {
        val w=Wire { ApiResponse(200,"""{"data":[{"id":"text","architecture":{"input_modalities":["text"]}},{"id":"vision","architecture":{"input_modalities":["text","image"]}},{"id":"unknown"}]}""") }
        val models=ModelCatalogClient(w).list(ProviderPresets.OPENROUTER.vision("fake"),Cancellation()).models
        assertNull(models.first { it.id=="unknown" }.supportsImages)
        assertEquals(listOf("vision"),ProviderPresets.visionChoices(models).map { it.id })
    }
    @Test fun automaticBatchUsesFixedJevAndRanksAllSixInOneDecision() {
        val compact="""{"replies":[{"strategy":"ACKNOWLEDGE","text":"后来呢？"},{"strategy":"HUMOR","text":"今天剧情挺丰富呀"},{"strategy":"TEASING","text":"你的专属听众已上线"},{"strategy":"CLARIFY","text":"现在还好吗？"},{"strategy":"CALM","text":"先缓缓，慢慢说"},{"strategy":"PACING","text":"我听着，你继续"}]}"""
        val scores=(0..5).associate { "candidate_$it" to mapOf("score" to it.toDouble()/2) }
        val answers=scores+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0.0),"best_action" to mapOf("choice" to "acknowledge"))
        val w=Wire { request -> ApiResponse(200,if(request.url.endsWith("decisions")) com.google.gson.Gson().toJson(mapOf("answers" to answers)) else com.google.gson.Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to compact)))))) }
        val batch=AutomaticAdvisor(ChatCompletionClient(w),JevJudgeClient(w)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),AdvisorFixtures.snapshot()),JevProfile(true,"https://bad.invalid/ignored","fake","cannot-use"),emptyList(),Cancellation())
        assertEquals(6,batch.replies.size); assertEquals("我听着，你继续",batch.replies.first().text)
        assertTrue(batch.compact); assertTrue(AdvisorValidator().validate(batch).valid)
        assertEquals(2,w.requests.size)
        val reply=JsonParser.parseString(w.requests.first().body).asJsonObject
        assertFalse(reply["reasoning"].asJsonObject["enabled"].asBoolean)
        val decision=w.requests.last(); assertEquals(ProviderPresets.JEV_ENDPOINT,decision.url)
        val json=JsonParser.parseString(decision.body).asJsonObject
        assertEquals("typesafe/jev-1.13",json["model"].asString); assertEquals(13,json["questions"].asJsonObject.size())
    }
    @Test fun missingCandidateScoreDoesNotPublishAnUnrankedBatch() {
        val w=Wire { ApiResponse(200,"""{"answers":{"true_intent":{"choice":"casual_chat"}}}""") }
        try { JevJudgeClient(w).rank(JevProfile(apiKey="fake"),AdvisorFixtures.snapshot(),"",AdvisorFixtures.valid().replies,Cancellation()); fail("missing six scores") } catch(_:ApiException) {}
    }
    @Test fun replyRequestKeepsRolesAndExplicitlyContinuesTheLastSelfTurn() {
        val fixture=AdvisorFixtures.valid().replies
        val compact=com.google.gson.Gson().toJson(mapOf("replies" to fixture.map { mapOf("strategy" to it.strategy.name,"text" to it.text) }))
        val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))
        for((last,mode) in listOf(com.loveadvisor.core.model.Speaker.ME to "CONTINUE_SELF",com.loveadvisor.core.model.Speaker.OTHER to "REPLY_OTHER")) {
            val wire=Wire { ApiResponse(200,com.google.gson.Gson().toJson(if(it.url.endsWith("decisions")) mapOf("answers" to answers) else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to compact)))))) }
            val snapshot=AdvisorFixtures.snapshot().copy(messages=listOf(com.loveadvisor.core.model.ChatMessage("left",com.loveadvisor.core.model.Speaker.OTHER,null,"对方的话"),com.loveadvisor.core.model.ChatMessage("last",last,null,"最后一句")))
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot),JevProfile(apiKey="fake"),emptyList(),Cancellation())
            val envelope=JsonParser.parseString(wire.requests.first().body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString
            val data=JsonParser.parseString(envelope).asJsonObject
            assertEquals("ME",data["replyAs"]?.asString);assertEquals(last.name,data["lastSpeaker"]?.asString);assertEquals(mode,data["responseMode"]?.asString)
            assertEquals(last.name,data["messages"].asJsonArray.last().asJsonObject["speaker"].asString)
            val decision=JsonParser.parseString(wire.requests.last().body).asJsonObject["state"].asJsonObject
            assertEquals(mode,decision["responseMode"]?.asString)
        }
    }
}
