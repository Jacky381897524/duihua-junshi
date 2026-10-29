package com.loveadvisor.core.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class DirectReplyTargetTest {
    private fun snapshot(last:Speaker=Speaker.OTHER)=AdvisorFixtures.snapshot().copy(messages=listOf(
        ChatMessage("me",Speaker.ME,null,"想陪你再聊一会"),
        ChatMessage("question",Speaker.OTHER,null,"然后呢"),
        ChatMessage("nickname",Speaker.OTHER,null,"小宝宝"),
        ChatMessage("latest",last,null,if(last==Speaker.ME)"我在听你说" else "嗯嗯")))
    private class Wire(private val returnedTarget:String?="question"):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            requests.add(request)
            val gson=Gson()
            if(request.url==ProviderPresets.JEV_ENDPOINT) {
                val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "seek_explanation"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))
                return ApiResponse(200,gson.toJson(mapOf("answers" to answers)))
            }
            val system=JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[0].asJsonObject["content"].asString
            val value=if(system.contains("\"why\""))mapOf("why" to "接住选中的问题","whenToUse" to "对方继续追问时","caution" to "不编造事实","positive" to "顺着实际回答聊","ambiguous" to "给对方空间","rejection" to "尊重拒绝")
                else mapOf("replies" to AdvisorFixtures.valid().replies.map { mapOf("strategy" to it.strategy.name,"text" to it.text,"targetMessageId" to (returnedTarget ?: JsonParser.parseString(JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject["targetMessageId"].asString)) })
            return ApiResponse(200,gson.toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to gson.toJson(value)))))))
        }
    }
    private fun data(request:ApiRequest)=JsonParser.parseString(JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
    @Test fun longSelectedIdsSurviveDraftAliasesRankingAndPublicationWithOnlyTwoRequests() {
        val target="message-12345678-1234-1234-1234-123456789abc"
        val sample=snapshot().copy(messages=listOf(ChatMessage(target,Speaker.OTHER,null,"然后呢")))
        val wire=Wire(null)
        val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),sample,replyTarget=ReplyTarget(target)),JevProfile(apiKey="fake"),emptyList(),Cancellation())
        assertEquals(2,wire.requests.size);assertTrue(batch.replies.all { it.targetMessageId==target })
        val draft=data(wire.requests.first());assertNotEquals(target,draft["targetMessageId"].asString)
        val state=JsonParser.parseString(wire.requests.last().body).asJsonObject["state"].asJsonObject
        val alias=state["targetMessageId"].asString
        assertEquals(alias,state["chat"].asJsonObject["messages"].asJsonArray[0].asJsonObject["id"].asString)
        assertTrue(state["candidates"].asJsonArray.all { it.asJsonObject["targetMessageId"].asString==alias })
    }
    @Test fun directSelectionSurvivesGenerationRankingAndDetailsEvenWhenSomeoneSpokeLater() {
        for(last in listOf(Speaker.ME,Speaker.OTHER)) {
            val wire=Wire();val advisor=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire))
            val input=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot(last),replyTarget=ReplyTarget("question"))
            val batch=advisor.generate(input,JevProfile(apiKey="fake"),emptyList(),Cancellation())
            assertEquals(ChatKind.DIRECT,batch.kind);assertEquals(6,batch.replies.size)
            assertTrue(batch.replies.all { it.targetMessageId=="question" && it.targetLabel?.contains("然后呢")==true })
            val reply=data(wire.requests[0]);val judge=JsonParser.parseString(wire.requests[1].body).asJsonObject["state"].asJsonObject
            for(fields in listOf(reply,judge)) {
                assertEquals("REPLY_TARGET",fields["responseMode"].asString)
                assertEquals("question",fields["targetMessageId"].asString);assertEquals("然后呢",fields["targetMessageText"].asString)
            }
            advisor.detail(input,batch.replies[0],Cancellation())
            assertEquals("question",data(wire.requests.last())["targetMessageId"].asString)
            assertEquals("REPLY_TARGET",data(wire.requests.last())["responseMode"].asString)
        }
    }
    @Test fun aWrongReturnedTargetNeverReachesRankingOrPublishes() {
        val wire=Wire("nickname")
        try {
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot(),replyTarget=ReplyTarget("question")),JevProfile(apiKey="fake"),emptyList(),Cancellation())
            fail("must reject a candidate batch that changed the selected message")
        } catch(e:IllegalArgumentException) { fail("direct selection must be supported: ${e.message}") }
        catch(e:ApiException) { assertTrue(e.message.orEmpty().contains("选中")) }
        assertTrue(wire.requests.isNotEmpty());assertTrue(wire.requests.none { it.url==ProviderPresets.JEV_ENDPOINT })
    }
    @Test fun refreshKeepsDirectTargetUntilAutomaticModeIsSelected() {
        val s=snapshot();val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker));val target=ReplyTarget("question")
        val request=coordinator.begin(s,target)!!
        assertTrue(coordinator.complete(request.requestTicket,AdvisorFixtures.valid().copy(compact=true,replies=AdvisorFixtures.valid().replies.map { it.copy(targetMessageId="question") })))
        assertEquals(target,coordinator.targetFor(s))
        val refresh=coordinator.begin(s,coordinator.targetFor(s))!!;coordinator.fail(refresh.requestTicket,"test")
        assertEquals(target,coordinator.targetFor(s))
        coordinator.begin(s,null);assertNull(coordinator.targetFor(s))
    }
    @Test fun aTrailingNicknameDoesNotReplaceTheQuestionInTheCurrentDirectTurn() {
        val s=snapshot().copy(messages=snapshot().messages.dropLast(1))
        val fields=ConversationTurn.data(s)
        assertEquals("REPLY_OTHER",fields["responseMode"])
        assertEquals("然后呢\n小宝宝",fields["lastTurnText"])
        val meLast=snapshot(Speaker.ME)
        assertEquals("CONTINUE_SELF",ConversationTurn.data(meLast)["responseMode"])
        assertEquals("我在听你说",ConversationTurn.data(meLast)["lastTurnText"])
    }
    @Test fun defaultGenerationAndJudgeBothReceiveTheWholeLatestDirectTurn() {
        val s=snapshot().copy(messages=snapshot().messages.dropLast(1));val wire=Wire()
        val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),s),JevProfile(apiKey="fake"),emptyList(),Cancellation())
        assertTrue(batch.replies.all { it.targetMessageId==null })
        val judged=JsonParser.parseString(wire.requests.last().body).asJsonObject["state"].asJsonObject
        for(fields in listOf(data(wire.requests.first()),judged))assertEquals("然后呢\n小宝宝",fields["lastTurnText"].asString)
    }
    @Test fun ownMissingAndAmbiguousMessagesCannotBecomeReplyTargets() {
        val base=snapshot()
        for((s,target) in listOf(base to "me",base to "missing",base.copy(messages=base.messages+base.messages[1]) to "question")) {
            val wire=Wire()
            try {
                AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),s,replyTarget=ReplyTarget(target)),JevProfile(apiKey="fake"),emptyList(),Cancellation())
                fail("must reject invalid selection")
            } catch(_:IllegalArgumentException) {}
            assertTrue(wire.requests.isEmpty())
        }
    }
}
