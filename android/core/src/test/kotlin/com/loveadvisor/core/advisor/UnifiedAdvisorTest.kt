package com.loveadvisor.core.advisor
import com.google.gson.Gson
import com.loveadvisor.core.api.*
import org.junit.Assert.*
import org.junit.Test
class UnifiedAdvisorTest {
    private fun response(content:String)=Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))))
    @Test fun malformedBatchRepairsExactlyOnce() {
        var n=0
        LocalApiServer { ServerReply(200,response(if(n++==0) AdvisorFixtures.json(AdvisorFixtures.valid().let { it.copy(replies=it.replies.take(5)) }) else AdvisorFixtures.json())) }.use { server ->
            val advisor=UnifiedAdvisor(ChatCompletionClient(JdkHttpTransport()))
            val result=advisor.analyze(AdvisorInput(server.profile,AdvisorFixtures.snapshot()),Cancellation())
            assertEquals(6,result.replies.size); assertEquals(2,server.requests.size)
            assertTrue(server.requests[1].body.contains("六"))
        }
    }
    @Test fun secondInvalidResponseReturnsFailureNotCannedReplies() {
        LocalApiServer { ServerReply(200,response("{}")) }.use { server ->
            try { UnifiedAdvisor(ChatCompletionClient(JdkHttpTransport())).analyze(AdvisorInput(server.profile,AdvisorFixtures.snapshot()),Cancellation()); fail("Must fail without synthetic candidates") } catch(_:ApiException) { }
            assertEquals(2,server.requests.size)
        }
    }
}
