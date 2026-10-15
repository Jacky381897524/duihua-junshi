package com.loveadvisor.core.api
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ConnectionTestClientTest {
    private class Wire(val answer:(ApiRequest)->ApiResponse):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse { cancel.check(); requests.add(request); return answer(request) }
    }
    private fun textResponse(text:String)=ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to text))))))
    @Test fun replyTestMakesRealCompletionRequestInsteadOfTestingCatalogOnly() {
        val wire=Wire { textResponse("连接成功") }
        val result=ConnectionTestClient(wire).reply(ProviderPresets.DEEPSEEK.reply("fake").copy(selectedModelId="deepseek-v4-pro"),Cancellation())
        assertEquals("deepseek-v4-pro",result.model); assertTrue(result.detail.contains("连接成功"))
        assertTrue(wire.requests.single().url.endsWith("/chat/completions"))
        assertFalse(wire.requests.single().body.orEmpty().contains("聊天记录"))
    }
    @Test fun visionTestActuallySendsAnImageAndValidatesRecognition() {
        val wire=Wire { textResponse(VisionFixtures.content("""{"kind":"DIRECT","title":"连接测试","messages":[{"speaker":"OTHER","text":"你好"},{"speaker":"ME","text":"收到"}]}""")) }
        val result=ConnectionTestClient(wire).vision(ProviderPresets.DEEPSEEK.vision("fake").copy(selectedModelId="deepseek-flash"),"data:image/png;base64,AA==",Cancellation())
        assertTrue(result.detail.contains("2"))
        val body=JsonParser.parseString(wire.requests.single().body).asJsonObject
        assertEquals("image_url",body["messages"].asJsonArray[0].asJsonObject["content"].asJsonArray[1].asJsonObject["type"].asString)
    }
    @Test fun visionWithWrongTextDoesNotReportSuccess() {
        val wire=Wire { textResponse(VisionFixtures.content("""{"kind":"DIRECT","title":"连接测试","messages":[{"speaker":"ME","text":"猜测内容"}]}""")) }
        try { ConnectionTestClient(wire).vision(ProviderPresets.DEEPSEEK.vision("fake").copy(selectedModelId="deepseek-flash"),"data:image/png;base64,AA==",Cancellation()); fail("not valid recognition") } catch(_:ApiException) {}
    }
    @Test fun judgeTestUsesFixedDecisionProtocolAndAllSixScores() {
        val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"best_action" to mapOf("choice" to "acknowledge"),"danger_level" to mapOf("score" to 0))
        val wire=Wire { ApiResponse(200,Gson().toJson(mapOf("answers" to answers))) }
        val result=ConnectionTestClient(wire).judge("fake",Cancellation())
        assertEquals("typesafe/jev-1.13",result.model)
        val request=wire.requests.single(); assertEquals(ProviderPresets.JEV_ENDPOINT,request.url)
        assertEquals(13,JsonParser.parseString(request.body).asJsonObject["questions"].asJsonObject.size())
    }
    @Test fun providerDenialIsShownAsFailure() {
        val wire=Wire { ApiResponse(401,"{}") }
        try { ConnectionTestClient(wire).reply(ProviderPresets.OPENROUTER.reply("fake"),Cancellation()); fail("denied key") } catch(e:ApiException) { assertEquals(401,e.status) }
    }
    @Test fun deepSeekCatalogKeepsProForReplyAndOnlyFlashForVision() {
        val wire=Wire { ApiResponse(200,"""{"data":[{"id":"deepseek-flash","name":"DeepSeek-V4.1-Flash","input_modalities":["text","image"]},{"id":"deepseek-v4-pro","name":"DeepSeek-V4-Pro","input_modalities":["text"]}]}""") }
        val models=ModelCatalogClient(wire).list(ProviderPresets.DEEPSEEK.reply("fake"),Cancellation()).models
        assertEquals(listOf("deepseek-flash","deepseek-v4-pro"),models.map { it.id })
        assertEquals(listOf("deepseek-flash"),ProviderPresets.visionChoices(models).map { it.id })
    }
    @Test fun fastDeepSeekRequestsDisableDefaultThinkingForShortReplies() {
        val wire=Wire { textResponse("连接成功") }
        ConnectionTestClient(wire).reply(ProviderPresets.DEEPSEEK.reply("fake").copy(selectedModelId="deepseek-v4-pro"),Cancellation())
        val body=JsonParser.parseString(wire.requests.single().body).asJsonObject
        assertEquals("disabled",body.getAsJsonObject("thinking")?.get("type")?.asString)
    }
    @Test fun deepSeekVisionExtractionDisablesDefaultThinking() {
        val wire=Wire { textResponse(VisionFixtures.content("""{"kind":"DIRECT","title":"连接测试","messages":[{"speaker":"OTHER","text":"你好"},{"speaker":"ME","text":"收到"}]}""")) }
        ConnectionTestClient(wire).vision(ProviderPresets.DEEPSEEK.vision("fake").copy(selectedModelId="deepseek-flash"),"data:image/png;base64,AA==",Cancellation())
        val body=JsonParser.parseString(wire.requests.single().body).asJsonObject
        assertEquals("disabled",body.getAsJsonObject("thinking")?.get("type")?.asString)
    }
}
