package com.loveadvisor.core.api

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class PromptAliasesTest {
    @Test fun restoresMessageAndMemoryReferencesWithoutRewritingProse() {
        val id="chat-12345678-1234-1234-1234-123456789abc"
        val source="a".repeat(64)
        val root=JsonParser.parseString(Gson().toJson(mapOf("messages" to listOf(mapOf("id" to id,"text" to "别把 $id 或 i1 当成我的资料")),"targetMessageId" to id,"sourceId" to source,"supportingSourceIds" to listOf(source))))
        val codec=PromptAliases();val packed=codec.encode(root).asJsonObject
        val alias=packed["targetMessageId"].asString
        assertTrue(alias.length<5);assertEquals(alias,packed["messages"].asJsonArray[0].asJsonObject["id"].asString)
        assertEquals(root.asJsonObject["messages"].asJsonArray[0].asJsonObject["text"],packed["messages"].asJsonArray[0].asJsonObject["text"])
        assertEquals(root,JsonParser.parseString(codec.restore(packed.toString())))
        val response=codec.restore("```json\n{\"targetMessageId\":\"$alias\",\"text\":\"$alias\"}\n```")
        assertEquals(id,JsonParser.parseString(response).asJsonObject["targetMessageId"].asString)
        assertEquals(alias,JsonParser.parseString(response).asJsonObject["text"].asString)
    }
    @Test fun shortIdsInLaterMessagesCannotCollideWithNewAliases() {
        val longId="b".repeat(64);val codec=PromptAliases()
        val rows=codec.messages(listOf(PromptMessage("user","{\"id\":\"$longId\"}"),PromptMessage("user","{\"id\":\"i1\"}")))
        assertNotEquals("i1",JsonParser.parseString(rows[0].content).asJsonObject["id"].asString)
        assertEquals("i1",JsonParser.parseString(rows[1].content).asJsonObject["id"].asString)
    }
    @Test fun repeatedOpaqueIdentifiersShrinkWhileEveryMessageAndFactSurvives() {
        val rows=(0 until 100).map { i -> mapOf("id" to "message-"+i.toString().padStart(64,'0'),"ownerId" to "person-"+"a".repeat(64),"text" to "第${i}句：我不喜欢太甜的饮料，明天再讨论") }
        val original=Gson().toJson(mapOf("messages" to rows));val codec=PromptAliases();val packed=codec.encode(JsonParser.parseString(original)).toString()
        assertTrue(packed.length<original.length*.65)
        assertEquals(JsonParser.parseString(original),JsonParser.parseString(codec.restore(packed)))
        println("PROMPT_ALIAS_FIXTURE originalChars=${original.length} packedChars=${packed.length} messages=${rows.size} allFactsPreserved=true")
    }
    @Test fun nonJsonRepliesAndMalformedResponsesRemainAvailableToNormalValidation() {
        val codec=PromptAliases();codec.encode(JsonParser.parseString("{\"id\":\"${"a".repeat(64)}\"}"))
        assertEquals("hello",codec.restore("hello"));assertEquals("{broken",codec.restore("{broken"))
    }
}
