package com.loveadvisor.core.api
import com.google.gson.*
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class GroupGenerationTest {
    @Test fun actualReplyAndJudgeRequestsKeep200RowsAndDistinctMembers() {
        for(lastSpeaker in listOf(Speaker.ME,Speaker.OTHER)) for(limit in listOf(100,200)) {
            val messages=(0 until limit).map { ChatMessage("m$it",if(it==limit-1)lastSpeaker else Speaker.OTHER,if(it%2==0)"甲🔥" else "乙", "第${it}条内容") }
            val snapshot=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("g","群",ChatKind.GROUP,true),messages,contextLimit=limit)
            for(target in listOf<ReplyTarget?>(null,ReplyTarget("m1"))) {
                val requests=mutableListOf<ApiRequest>()
                val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
                    requests.add(request)
                    val content=mapOf("replies" to (0..5).map { mapOf("strategy" to "ACKNOWLEDGE","text" to "接着说第${it}种方案", "targetMessageId" to (target?.messageId ?: "m${it}"),"topic" to "话题${it%3}") })
                    val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))
                    return ApiResponse(200,Gson().toJson(if(request.url==ProviderPresets.JEV_ENDPOINT)mapOf("answers" to answers) else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to Gson().toJson(content)))))))
                } }
                val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(ProviderPresets.OPENROUTER.reply("fake"),snapshot,replyTarget=target),JevProfile(apiKey="fake"),emptyList(),Cancellation())
                assertEquals(ChatKind.GROUP,batch.kind);assertEquals(6,batch.replies.size)
                assertEquals(1,batch.replies.map { it.strategy }.distinct().size)
                val prompt=JsonParser.parseString(requests[0].body).asJsonObject["messages"].asJsonArray[1].asJsonObject["content"].asString
                val data=JsonParser.parseString(prompt).asJsonObject
                assertEquals(limit,data["messages"].asJsonArray.size());assertEquals(if(target!=null)"REPLY_TARGET" else if(lastSpeaker==Speaker.ME)"CONTINUE_SELF" else "REPLY_OTHER",data["responseMode"].asString)
                val decision=JsonParser.parseString(requests[1].body).asJsonObject["state"].asJsonObject
                assertEquals(limit,decision["chat"].asJsonObject["messages"].asJsonArray.size())
                assertTrue(decision.toString().contains("甲🔥"));assertTrue(decision.toString().contains("targetMessageId"))
                if(target!=null) {
                    assertTrue(batch.replies.all { it.targetMessageId==target.messageId })
                    assertEquals("REPLY_TARGET",decision["responseMode"].asString)
                    assertEquals(target.messageId,decision["targetMessageId"].asString)
                    assertEquals("第1条内容",decision["targetMessageText"].asString)
                    val instruction=JsonParser.parseString(requests[1].body).asJsonObject["questions"].asJsonObject["true_intent"].asJsonObject["instructions"].asString
                    assertTrue(instruction.contains("selected target"))
                }
            }
        }
    }
}
