package com.loveadvisor.core.integration
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.api.*
import com.loveadvisor.core.session.*
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.input.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class AdvisorFlowTest {
    @Test fun realApiRefreshFailureAndConversationSwitchCannotReplaceOrFill() {
        var failNext=false
        LocalApiServer {
            val message=mapOf("content" to AdvisorFixtures.json())
            val body=com.google.gson.Gson().toJson(mapOf("choices" to listOf(mapOf("message" to message))))
            if(failNext) ServerReply(503,"FAKE_KEY echoed") else ServerReply(200,body)
        }.use { server ->
            val tracker=SessionTracker(); val gate=RequestGate(tracker); val coordinator=BatchCoordinator(tracker,gate); val advisor=UnifiedAdvisor(ChatCompletionClient(JdkHttpTransport())); val chat=AdvisorFixtures.snapshot()
            val first=coordinator.begin(chat)!!; val input=AdvisorInput(server.profile,chat)
            assertTrue(coordinator.complete(first.requestTicket,advisor.analyze(input,Cancellation()))); val original=coordinator.state.current!!
            failNext=true; val refresh=coordinator.begin(chat)!!
            try { advisor.alternatives(input,refresh.analysis!!,refresh.previous,Cancellation()); fail() } catch(e:ApiException) { coordinator.fail(refresh.requestTicket,e.message.orEmpty()) }
            assertEquals(original,coordinator.state.current); assertFalse(coordinator.state.error.orEmpty().contains("FAKE_KEY"))
            val changed=chat.copy(messages=chat.messages+ChatMessage("new",Speaker.OTHER,null,"后来已经好了")); val pending=coordinator.begin(changed)!!; assertTrue(pending.reanalyze)
            val other=chat.copy(identity=chat.identity!!.copy(id="b")); coordinator.begin(other)
            assertFalse(coordinator.complete(pending.requestTicket,AdvisorFixtures.valid())); assertFalse(gate.accept(first.requestTicket))
            val inputBox=NodeRecord("input","新对象原有草稿","EditText",true,RectI(100,1700,800,1800)); val window=WindowSnapshot(other.packageName,other.windowId,RectI(0,0,1000,2000),listOf(inputBox),other.identity)
            assertFalse(FillGuard(tracker).verify(original.ticket.session,other,window,inputBox).allowed); assertEquals("新对象原有草稿",inputBox.text)
        }
    }
}
