package com.loveadvisor.core.advisor

import com.google.gson.JsonParser
import com.google.gson.Gson
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class BilingualReplyTest {
    @Test fun codecPreservesChineseGlossWithoutAddingItToSendableText() {
        val json=JsonParser.parseString(AdvisorFixtures.json()).asJsonObject
        json["replies"].asJsonArray[0].asJsonObject.apply { addProperty("text","Sounds good, I'm in!");addProperty("translationZh","听起来不错，算我一个！") }
        val plan=AdvisorCodec().parse(json.toString())!!.replies.first()
        assertEquals("Sounds good, I'm in!",plan.text)
        assertEquals("听起来不错，算我一个！",Gson().toJsonTree(plan).asJsonObject["translationZh"]?.asString)
    }
    @Test fun foreignConversationRequestsTranslationInTheSameGenerationAndKeepsAdviceChinese() {
        val snapshot=AdvisorFixtures.snapshot().copy(messages=listOf(ChatMessage("en",Speaker.OTHER,null,"Wanna grab coffee after work?")))
        val input=AdvisorInput(ApiProfile(baseUrl="https://example.com",apiKey=""),snapshot,realisticRepliesEnabled=true)
        val prompt=UnifiedPrompt.systemFor(input)+UnifiedPrompt.data(input)
        assertTrue(prompt.contains("translationZh"))
        assertTrue(prompt.contains("建议") && prompt.contains("中文"))
    }
    @Test fun translatedChineseMustNeverBeConcatenatedIntoTheSendableBody() {
        val input=AdvisorInput(ApiProfile(baseUrl="https://example.com",apiKey=""),AdvisorFixtures.snapshot().copy(messages=listOf(ChatMessage("en",Speaker.OTHER,null,"Wanna grab coffee after work?"))))
        val reply=AdvisorFixtures.valid().replies.first().copy(text="Sure, sounds good!\n好啊，听起来不错",translationZh="好啊，听起来不错")
        assertFalse(BilingualPolicy.errors(input,listOf(reply)).isEmpty())
        assertFalse(BilingualPolicy.errors(input,listOf(reply.copy(text="我们下班去喝一杯",translationZh="下班一起喝咖啡吧"))).isEmpty())
    }
    @Test fun isolatedForeignGreetingUsesForeignButChineseContextAndLolStayChinese() {
        fun snapshot(vararg texts:String)=AdvisorFixtures.snapshot().copy(messages=texts.mapIndexed { i,s -> ChatMessage("$i",Speaker.OTHER,null,s) })
        assertEquals("foreign",BilingualPolicy.language(snapshot("hey")))
        assertEquals("zh",BilingualPolicy.language(snapshot("今天吃什么","lol")))
        assertEquals("ja",BilingualPolicy.language(snapshot("今日はどうだった？")))
        assertEquals("foreign",BilingualPolicy.language(snapshot("I'm hungry")))
        assertEquals("foreign",BilingualPolicy.language(snapshot("คุณเป็นอย่างไรบ้าง")))
    }
}

