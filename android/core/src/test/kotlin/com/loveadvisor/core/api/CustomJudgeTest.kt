package com.loveadvisor.core.api
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.AdvisorFixtures
import org.junit.Assert.*
import org.junit.Test
class CustomJudgeTest {
    @Test fun customJudgeUsesConfiguredChatEndpointModelAndAllSixScores() {
        val answer="""{"intent":"希望被理解","risk":3,"action":"先回应具体内容","scores":[10,90,30,60,40,80]}"""
        var observed:ApiRequest?=null
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            observed=request
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to answer))))))
        } }
        val replies=AdvisorFixtures.valid().replies
        val result=JevJudgeClient(wire).rank(JevProfile(true,"https://custom.example/v1","fake","my-judge",custom=true),AdvisorFixtures.snapshot(),"",replies,Cancellation())
        assertEquals("https://custom.example/v1/chat/completions",observed!!.url)
        assertEquals("my-judge",JsonParser.parseString(observed!!.body).asJsonObject["model"].asString)
        assertEquals("希望被理解",result.first.intent)
        assertEquals(replies[1].text,result.second.first().text)
        assertEquals(.9,result.second.first().score!!,.00001)
    }
    @Test fun malformedCustomScoresAreRejectedInsteadOfPublishingUnrankedReplies() {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation)=ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to """{"intent":"x","risk":2,"action":"x","scores":[10]}""")))))) }
        try { JevJudgeClient(wire).rank(JevProfile(true,"https://custom.example/v1","fake","my-judge",custom=true),AdvisorFixtures.snapshot(),"",AdvisorFixtures.valid().replies,Cancellation());fail("Missing score must fail") } catch(_:ApiException){}
    }
}
