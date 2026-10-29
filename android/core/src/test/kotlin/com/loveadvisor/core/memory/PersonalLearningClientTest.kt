package com.loveadvisor.core.memory

import com.google.gson.Gson
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.Speaker
import org.junit.Assert.*
import org.junit.Test

class PersonalLearningClientTest {
    @Test fun shortWireAliasesReturnTheOriginalEvidenceIdsAndExactQuote() {
        val source="evidence-"+"a".repeat(64);val peer="person-"+"b".repeat(64)
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            val messages=com.google.gson.JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray
            val row=com.google.gson.JsonParser.parseString(messages[1].asJsonObject["content"].asString).asJsonArray[0].asJsonObject
            assertTrue(row["sourceId"].asString.length<5);assertEquals("我喜欢咖啡",row["text"].asString)
            val answer=Gson().toJson(mapOf("memories" to listOf(mapOf("sourceId" to row["sourceId"].asString,"kind" to "LIKE","quote" to "我喜欢咖啡","supportingSourceIds" to listOf(row["sourceId"].asString)))))
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to answer))))))
        } }
        val result=PersonalLearningClient(ChatCompletionClient(wire)).classify(profile,LearningSlice(1,listOf(PersonalEvidence(source,peer,peer,"我喜欢咖啡",null,1,Speaker.OTHER))),Cancellation()).single()
        assertEquals(source,result.sourceId);assertEquals(listOf(source),result.supportingSourceIds);assertEquals("我喜欢咖啡",result.quote)
    }
    private val profile=ApiProfile(baseUrl="https://example.com/v1",apiKey="fake",selectedModelId="fake-model")
    private val slice=LearningSlice(1,listOf(PersonalEvidence("source","contact","contact","我喜欢咖啡",null,1,Speaker.OTHER)))
    @Test fun classificationIsOneOptionalCallAndMalformedResponseCannotWriteFacts() {
        var calls=0
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse { calls++;return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "broken")))))) } }
        try { PersonalLearningClient(ChatCompletionClient(wire)).classify(profile,slice,Cancellation());fail() } catch(_:ApiException) { }
        assertEquals(1,calls)
    }
    @Test fun emptyIncrementHasNoModelCostAndCancellationCannotReturnAProposal() {
        var calls=0
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse { calls++;cancel.cancel();return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "{\"memories\":[]}")))))) } }
        val client=PersonalLearningClient(ChatCompletionClient(wire))
        assertTrue(client.classify(profile,slice.copy(evidence=emptyList()),Cancellation()).isEmpty());assertEquals(0,calls)
        try { client.classify(profile,slice,Cancellation());fail() } catch(_:java.util.concurrent.CancellationException) { }
        assertEquals(1,calls)
    }
}
