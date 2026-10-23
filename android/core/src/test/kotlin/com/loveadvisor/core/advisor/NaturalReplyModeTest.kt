package com.loveadvisor.core.advisor

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class NaturalReplyModeTest {
    private val texts=listOf("收工啦","歇会儿","忙完了啊","这下能歇了","终于洗完了","碗都洗完啦")
    private fun input(enabled:Boolean,kind:ChatKind,selected:Boolean):AdvisorInput {
        val snapshot=AdvisorFixtures.snapshot().copy(identity=AdvisorFixtures.snapshot().identity!!.copy(kind=kind),messages=listOf(
            ChatMessage("mine",Speaker.ME,null,"我先坐会儿"),ChatMessage("target",Speaker.OTHER,"群友","我刚洗完碗"),ChatMessage("later",Speaker.ME,null,"嗯")))
        val original=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot,replyStyle="简短 不用标点",replyTarget=if(selected)ReplyTarget("target") else null)
        val json=Gson().toJsonTree(original).asJsonObject.apply { addProperty("naturalRepliesEnabled",enabled) }
        return Gson().fromJson(json,AdvisorInput::class.java)
    }
    private class Wire(private val content:String,private val custom:Boolean=false):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            cancel.check();requests.add(request)
            val text=if(custom && request.url.contains("judge.example")) """{"intent":"确认近况","risk":1,"action":"直接接话","scores":[80,80,80,80,80,80]}""" else content
            val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))
            return ApiResponse(200,Gson().toJson(if(request.url==ProviderPresets.JEV_ENDPOINT)mapOf("answers" to answers) else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to text))))))
        }
    }
    private fun content(values:List<String> = texts)=Gson().toJson(mapOf("replies" to values.map { mapOf("strategy" to "ACKNOWLEDGE","text" to it,"targetMessageId" to "target","topic" to "洗碗") }))
    private fun data(request:ApiRequest)=JsonParser.parseString(JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject

    @Test fun enabledShortRepliesWithRepeatedStrategiesNeedNoPaidRepairAndKeepTargets() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(selected in listOf(false,true))for(custom in listOf(false,true)) {
            val wire=Wire(content(),custom)
            val profile=if(custom)JevProfile(apiKey="fake",endpoint="https://judge.example/v1",model="custom",custom=true) else JevProfile(apiKey="fake")
            val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input(true,kind,selected),profile,emptyList(),Cancellation())
            assertEquals(texts.toSet(),batch.replies.map { it.text }.toSet())
            assertEquals(2,wire.requests.size)
            if(selected || kind==ChatKind.GROUP)assertTrue(batch.replies.all { it.targetMessageId=="target" })
            val drafting=data(wire.requests.first())
            val body=JsonParser.parseString(wire.requests.last().body).asJsonObject
            if(!custom)assertEquals(13,body["questions"].asJsonObject.size())
            val judgment=if(custom)data(wire.requests.last()) else body["state"].asJsonObject
            assertEquals(drafting["responsePolicy"],judgment["responsePolicy"])
            assertTrue(judgment["responsePolicy"].asJsonObject.has("naturalExpression"))
            assertEquals("简短 不用标点",judgment["replyStyle"].asString)
            assertEquals("简短 不用标点",drafting["replyStyle"].asString)
        }
    }
    @Test fun offStillRejectsRepeatedDirectStrategiesInsteadOfChangingLegacyRules() {
        val wire=Wire(content())
        try {
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input(false,ChatKind.DIRECT,false),JevProfile(apiKey="fake"),emptyList(),Cancellation())
            fail("OFF must retain V1.5.0 strategy validation")
        } catch(_:ApiException) { }
        assertEquals(2,wire.requests.size)
        assertTrue(wire.requests.none { it.url==ProviderPresets.JEV_ENDPOINT })
    }
    @Test fun enabledFullConsultationAlsoAcceptsShortDistinctRepliesWithOneStrategy() {
        val fixture=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.mapIndexed { i,r -> r.copy(strategy=Strategy.ACKNOWLEDGE,text=texts[i],targetMessageId="target") })
        val wire=Wire(AdvisorFixtures.json(fixture))
        val batch=UnifiedAdvisor(ChatCompletionClient(wire)).analyze(input(true,ChatKind.DIRECT,true),Cancellation())
        assertEquals(texts,batch.replies.map { it.text })
        assertEquals(1,wire.requests.size)
        assertTrue(batch.replies.all { it.targetMessageId=="target" })
    }
    @Test fun offPolicyPayloadIsIdenticalWithMissingOrExplicitFalseSetting() {
        val explicit=input(false,ChatKind.DIRECT,false)
        val json=Gson().toJsonTree(explicit).asJsonObject.apply { remove("naturalRepliesEnabled") }
        val missing=Gson().fromJson(json,AdvisorInput::class.java)
        assertEquals(UnifiedPrompt.data(missing),UnifiedPrompt.data(explicit))
    }

    @Test fun naturalCandidatesReachVisibleStateOnFirstReadAndOnRefresh() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(selected in listOf(false,true)) {
            val request=input(true,kind,selected)
            val tracker=SessionTracker()
            val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
            for(round in 0..1) {
                val pending=coordinator.begin(request.snapshot,request.replyTarget)!!
                val values=texts.map { if(round==0)it else it+"先歇一下" }
                val wire=Wire(content(values))
                val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(request,JevProfile(apiKey="fake"),pending.previous,Cancellation())
                assertTrue("Generated natural candidates must pass the final publication gate: $kind selected=$selected round=$round",coordinator.complete(pending.requestTicket,batch))
                assertFalse(coordinator.state.loading)
                assertNull(coordinator.state.error)
                assertEquals(values.toSet(),coordinator.state.current!!.batch.replies.map { it.text }.toSet())
                assertEquals(2,wire.requests.size)
            }
        }
    }

    @Test fun naturalFullConsultationCandidatesAlsoReachVisibleState() {
        val request=input(true,ChatKind.DIRECT,true)
        val tracker=SessionTracker()
        val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
        val pending=coordinator.begin(request.snapshot,request.replyTarget)!!
        val fixture=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.mapIndexed { i,r -> r.copy(strategy=Strategy.ACKNOWLEDGE,text=texts[i],targetMessageId="target") })
        val batch=UnifiedAdvisor(ChatCompletionClient(Wire(AdvisorFixtures.json(fixture)))).analyze(request,Cancellation())
        assertTrue("Full consultation must keep its generation mode at final publication",coordinator.complete(pending.requestTicket,batch))
        assertFalse(coordinator.state.loading)
        assertEquals(texts,coordinator.state.current!!.batch.replies.map { it.text })
    }

    @Test fun legacyReadThenNaturalRefreshThenLegacyRefreshKeepTheirOwnValidationMode() {
        val request=input(true,ChatKind.DIRECT,false)
        val tracker=SessionTracker()
        val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
        val first=coordinator.begin(request.snapshot)!!
        assertTrue(coordinator.complete(first.requestTicket,AdvisorFixtures.valid()))

        val refresh=coordinator.begin(request.snapshot)!!
        val wire=Wire(content())
        val natural=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(request,JevProfile(apiKey="fake"),refresh.previous,Cancellation())
        assertTrue(coordinator.complete(refresh.requestTicket,natural))
        assertFalse(coordinator.state.loading)
        assertEquals(2,wire.requests.size)

        val off=coordinator.begin(request.snapshot)!!
        val legacy=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(text=it.text+"接着聊") })
        val offWire=Wire(AdvisorFixtures.json(legacy))
        val offBatch=AutomaticAdvisor(ChatCompletionClient(offWire),JevJudgeClient(offWire)).generate(request.copy(naturalRepliesEnabled=false),JevProfile(apiKey="fake"),off.previous,Cancellation())
        assertFalse(offBatch.naturalRepliesEnabled)
        assertTrue(coordinator.complete(off.requestTicket,offBatch))
        assertEquals(2,offWire.requests.size)
        assertFalse(AdvisorValidator().validate(offBatch.copy(replies=offBatch.replies.map { it.copy(strategy=Strategy.ACKNOWLEDGE) })).valid)
    }

    @Test fun modelResponseCannotEnableNaturalModeForAnOffRequest() {
        val fixture=AdvisorFixtures.valid().copy(naturalRepliesEnabled=true,replies=AdvisorFixtures.valid().replies.mapIndexed { i,r -> r.copy(strategy=Strategy.ACKNOWLEDGE,text=texts[i],targetMessageId="target") })
        val wire=Wire(AdvisorFixtures.json(fixture))
        try {
            UnifiedAdvisor(ChatCompletionClient(wire)).analyze(input(false,ChatKind.DIRECT,true),Cancellation())
            fail("Generation mode must come from this request, never the model response")
        } catch(_:ApiException) { }
        assertEquals(2,wire.requests.size)
    }
}
