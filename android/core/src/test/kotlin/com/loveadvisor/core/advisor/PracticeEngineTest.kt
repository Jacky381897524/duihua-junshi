package com.loveadvisor.core.advisor
import com.loveadvisor.core.api.*
import org.junit.Assert.*
import org.junit.Test
class PracticeEngineTest {
    @Test fun eachTurnSimulatesOneLineAndWaitsForUser() {
        LocalApiServer { ServerReply(200,"""{"choices":[{"message":{"content":"{\"reply\":\"最近忙什么呢？\",\"feedback\":\"先回应具体话题，给对方留一点空间\"}"}}]}""") }.use { s ->
            val engine=PracticeEngine(ChatCompletionClient(JdkHttpTransport()),s.profile); val original=engine.start("刚认识，练习自然开场")
            val turn=engine.respond(original,"最近在学做饭",Cancellation())
            assertEquals(listOf("最近忙什么呢？"),turn.simulatedReplies); assertTrue(turn.waitingForUser); assertEquals(2,turn.state.turns.size); assertTrue(original.turns.isEmpty()); assertEquals(1,s.requests.size)
        }
    }
    @Test fun cancellationDoesNotAdvanceStateOrRequest() {
        LocalApiServer { ServerReply(200,"{}") }.use { s ->
            val engine=PracticeEngine(ChatCompletionClient(JdkHttpTransport()),s.profile); val state=engine.start("练习拒绝")
            val cancel=Cancellation(); cancel.cancel()
            try { engine.respond(state,"你好",cancel); fail() } catch(_:java.util.concurrent.CancellationException) { }
            assertTrue(state.turns.isEmpty()); assertTrue(s.requests.isEmpty())
        }
    }
}
