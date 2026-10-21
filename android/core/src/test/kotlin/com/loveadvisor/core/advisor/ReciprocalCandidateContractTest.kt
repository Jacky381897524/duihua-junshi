package com.loveadvisor.core.advisor

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ReciprocalCandidateContractTest {
    private val strategies=listOf(Strategy.ACKNOWLEDGE,Strategy.HUMOR,Strategy.CLARIFY,Strategy.CALM,Strategy.BOUNDARY,Strategy.PACING)
    private val replies=listOf("文件我先核对一下，缺的部分再告诉你。","这份表格有点绕，我们先把口径对齐。","你需要的是周报，还是上次那份明细？","这事先别着急，把要核对的项目发我。","今天能先看关键项，其他部分明天再确认。","我先看一下资料，确认后再接着聊。")
    private class Wire(private val content:String,private val custom:Boolean=false):HttpTransport {
        val requests=mutableListOf<ApiRequest>()
        override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            cancel.check();requests.add(request)
            val text=if(custom && request.url.contains("judge.example")) """{"intent":"确认资料","risk":1,"action":"核对实际内容","scores":[45,81,79,60,72,65]}""" else content
            val scores=(0..5).associate { "candidate_$it" to mapOf("score" to (if(it==1)4 else 2)) }
            val answers=scores+mapOf("true_intent" to mapOf("choice" to "request_action"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "check_history"))
            return ApiResponse(200,Gson().toJson(if(request.url==ProviderPresets.JEV_ENDPOINT)mapOf("answers" to answers) else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to text))))))
        }
    }
    private fun input(text:String,style:String="")=AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),AdvisorFixtures.snapshot().copy(messages=listOf(ChatMessage("target",Speaker.OTHER,null,text))),replyStyle=style)
    private fun content(texts:List<String> = replies)=Gson().toJson(mapOf("replies" to texts.mapIndexed { i,text -> mapOf("strategy" to strategies[i].name,"text" to text) }))
    private fun data(request:ApiRequest)=JsonParser.parseString(JsonParser.parseString(request.body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject

    @Test fun ordinaryWorkCandidatesNeedNoComplimentFlirtOrInvitationAndCostOneGeneration() {
        val wire=Wire(content())
        val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input("你帮我看看这份资料？"),JevProfile(apiKey="fake"),emptyList(),Cancellation())
        assertEquals(6,batch.replies.size)
        assertTrue(batch.replies.none { it.strategy in setOf(Strategy.APPRECIATION,Strategy.TEASING,Strategy.INVITATION) })
        assertEquals("Valid ordinary replies must not trigger paid format repair",2,wire.requests.size)
        assertEquals(replies[1],batch.replies.first().text)
        assertEquals("Keep actual Jev scores; do not relabel heuristic scores",1.0,batch.replies.first().score!!,0.0)
    }

    @Test fun refusalAndBereavementCanOfferSixNonFlirtingActions() {
        val cases=listOf(
            "请别再联系我了。" to listOf("收到，我尊重你的决定。","明白，这个话题到这里。","我理解你的意思，不再继续了。","好的，你不用再解释。","我会停止联系，保重。","好，我们就到这里。"),
            "家里老人刚去世，我想静一静。" to listOf("听到了，你先照顾好自己。","这会儿不用勉强说话，我理解。","你想静一静就先静一静。","难受的时候不用撑着回应。","我不追问，你按自己的节奏来。","你先忙家里的事，不用回我。")
        )
        for((text,texts) in cases) {
            val wire=Wire(content(texts))
            val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(input(text),JevProfile(apiKey="fake"),emptyList(),Cancellation())
            assertEquals(texts.toSet(),batch.replies.map { it.text }.toSet())
            assertEquals("Do not force a flirting repair in this scene: $text",2,wire.requests.size)
        }
    }

    @Test fun generationAndBothJudgesReceiveSameReciprocalRubricAndCurrentStyle() {
        val style="温柔一点，详细两句，保留我的语气"
        for(custom in listOf(false,true)) {
            val wire=Wire(content(),custom)
            val profile=if(custom)JevProfile(apiKey="fake",endpoint="https://judge.example/v1",model="custom",custom=true) else JevProfile(apiKey="fake")
            val request=input("你帮我看看这份资料？",style)
            AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(request,profile,emptyList(),Cancellation())
            val generation=data(wire.requests.first())
            val judgment=if(custom)data(wire.requests.last()) else JsonParser.parseString(wire.requests.last().body).asJsonObject["state"].asJsonObject
            assertNotNull("Generation must receive actionable shared quality criteria",generation["responsePolicy"])
            assertEquals(generation["responsePolicy"],judgment["responsePolicy"])
            val dimensions=judgment["responsePolicy"].asJsonObject["dimensions"].asJsonObject
            assertTrue(dimensions.has("reciprocity") && dimensions.has("responseEase") && dimensions.has("calibration"))
            assertTrue(dimensions.entrySet().all { it.value.asString.isNotBlank() })
            assertEquals(style,judgment["replyStyle"]?.asString)
            assertEquals(6,judgment["candidates"].asJsonArray.size())
            assertEquals(2,wire.requests.size)
        }
    }

    @Test fun unifiedUsesSameQualityPolicyAndKeepsExplicitTarget() {
        val request=input("周末想去喝咖啡").copy(replyTarget=ReplyTarget("target"))
        val data=JsonParser.parseString(UnifiedPrompt.data(request)).asJsonObject
        assertNotNull(data["responsePolicy"])
        assertEquals("target",data["targetMessageId"]?.asString)
        assertEquals("REPLY_TARGET",data["responseMode"]?.asString)
    }

    @Test fun fullUnifiedPathKeepsTargetAssociationInPublishedCandidates() {
        val request=input("资料里第二项怎么理解？").copy(replyTarget=ReplyTarget("target"))
        val fixture=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(targetMessageId="target") })
        val wire=Wire(AdvisorFixtures.json(fixture))
        val batch=UnifiedAdvisor(ChatCompletionClient(wire)).analyze(request,Cancellation())
        assertTrue(batch.replies.all { it.targetMessageId=="target" })
        assertEquals(1,wire.requests.size)
    }

    @Test fun fullUnifiedRejectsRepliesToAnotherMessageInsteadOfPublishingThem() {
        val request=input("资料里第二项怎么理解？").copy(replyTarget=ReplyTarget("target"))
        val fixture=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(targetMessageId="unread") })
        val wire=Wire(AdvisorFixtures.json(fixture))
        try { UnifiedAdvisor(ChatCompletionClient(wire)).analyze(request,Cancellation());fail("Wrong target must not be published") } catch(_:ApiException) { }
        assertEquals("Existing format repair budget remains one retry",2,wire.requests.size)
    }

    @Test fun fullUnifiedGroupKeepsIndividualTargetAndAllowsRepeatedStrategies() {
        val original=input("资料里第二项怎么理解？")
        val request=original.copy(snapshot=original.snapshot.copy(identity=original.snapshot.identity!!.copy(kind=ChatKind.GROUP)))
        val fixture=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(strategy=Strategy.CLARIFY,targetMessageId="target",topic="资料") })
        val wire=Wire(AdvisorFixtures.json(fixture))
        val batch=UnifiedAdvisor(ChatCompletionClient(wire)).analyze(request,Cancellation())
        assertEquals(ChatKind.GROUP,batch.kind)
        assertTrue(batch.replies.all { it.targetMessageId=="target" && it.topic=="资料" })
        assertEquals(1,wire.requests.size)
    }

    @Test fun detailUsesSharedQualityCriteriaWithoutRequestingAnotherCandidateBatch() {
        val wire=Wire(Gson().toJson(ReplyDetail("接住实际原话","适合对方分享时","避免连续追问","按真实回应继续","先留空间","停止对应推进")))
        val detail=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).detail(input("刚发现一家好喝的咖啡店"),AdvisorFixtures.valid().replies.first(),Cancellation())
        assertEquals("接住实际原话",detail.why)
        val system=JsonParser.parseString(wire.requests.single().body).asJsonObject["messages"].asJsonArray[0].asJsonObject["content"].asString
        assertFalse("One detail request must not ask for a fresh six-reply batch",system.contains("组织六条"))
        assertNotNull(data(wire.requests.single())["responsePolicy"])
    }
}
