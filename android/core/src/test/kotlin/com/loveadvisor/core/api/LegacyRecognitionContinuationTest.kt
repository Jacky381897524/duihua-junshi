package com.loveadvisor.core.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** The V1.2.13 protocol must feed six self continuations without new geometry gates. */
class LegacyRecognitionContinuationTest {
    private val gson=Gson()
    private fun response(content:Any)=ApiResponse(200,gson.toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to gson.toJson(content)))))))
    @Test fun legacyReadWithoutGeometryKeepsLastSelfTurnThroughAllSixCandidatesAndJudging() {
        val requests=mutableListOf<ApiRequest>()
        val replies=listOf("我再提醒你一句，出门记得带伞呀","我还想问问你那边有没有下雨","我想说的是，路上慢点，别着急","我还有点担心你走路会淋到雨","我接着提醒一下，回来记得擦干头发","我想再叮嘱一句，别忘了多穿一件")
        val wire=object:HttpTransport {
            override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
                requests.add(request)
                val body=JsonParser.parseString(request.body).asJsonObject
                if(request.url.endsWith("decisions")) {
                    val answers=(0..5).associate { "candidate_$it" to mapOf("score" to 3) }+mapOf("true_intent" to mapOf("choice" to "casual_chat"),"danger_level" to mapOf("score" to 0),"best_action" to mapOf("choice" to "acknowledge"))
                    return ApiResponse(200,gson.toJson(mapOf("answers" to answers)))
                }
                val content=body["messages"].asJsonArray[0].asJsonObject["content"]
                if(content.isJsonArray)return response(mapOf("kind" to "DIRECT","title" to "联系人","messages" to listOf(mapOf("speaker" to "OTHER","kind" to "TEXT","text" to "外面有点冷","y" to 900),mapOf("speaker" to "ME","kind" to "TEXT","text" to "下雨了 宝贝","y" to 1200))))
                val strategies=listOf("ACKNOWLEDGE","HUMOR","APPRECIATION","CLARIFY","CALM","PACING")
                return response(mapOf("replies" to replies.mapIndexed { i,text->mapOf("strategy" to strategies[i],"text" to text) }))
            }
        }
        val profile=ProviderPresets.OPENROUTER.reply("fake")
        val read=VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation())
        val snapshot=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("legacy",read.title,ChatKind.DIRECT,true),read.messages)
        val batch=AutomaticAdvisor(ChatCompletionClient(wire),JevJudgeClient(wire)).generate(AdvisorInput(profile,snapshot),JevProfile(apiKey="fake"),emptyList(),Cancellation())
        assertEquals(replies.toSet(),batch.replies.map { it.text }.toSet())
        assertEquals(6,batch.replies.size)
        val request=JsonParser.parseString(requests[1].body).asJsonObject["messages"].asJsonArray
        val data=JsonParser.parseString(request[1].asJsonObject["content"].asString).asJsonObject
        assertEquals("CONTINUE_SELF",data["responseMode"].asString)
        assertEquals("下雨了 宝贝",data["lastMessageText"].asString)
        assertEquals("ME",data["lastSpeaker"].asString)
        assertEquals(2,data["messages"].asJsonArray.size())
        val state=JsonParser.parseString(requests.last().body).asJsonObject["state"].asJsonObject
        assertEquals("CONTINUE_SELF",state["responseMode"].asString)
        assertEquals(6,state["candidates"].asJsonArray.size())
    }
}
