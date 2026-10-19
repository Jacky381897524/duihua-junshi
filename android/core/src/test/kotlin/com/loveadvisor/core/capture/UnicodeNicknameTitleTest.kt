package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
import com.loveadvisor.core.api.*
import com.google.gson.Gson

class UnicodeNicknameTitleTest {
    private val viewport=RectI(0,0,1080,2344)
    private fun title(name:String)=NodeRecord("title",name,"android.widget.TextView",false,RectI(300,130,780,190))
    @Test fun punctuationInAnActualHeaderDoesNotDiscardTheNickname() {
        for(name in listOf("🍃    L 。","小雨？！","꧁༺星✨༻꧂","คิดถึง ๖","🔥"))
            assertEquals(name,TextSceneParser.title(listOf(title(name)),viewport))
    }
    @Test fun longCombinedEmojiNamesAreNotCutAtUtf16Boundaries() {
        val name="👩‍👩‍👧‍👦".repeat(12)
        assertEquals(name,TextSceneParser.title(listOf(title(name)),viewport))
    }
    @Test fun visionKeepsTheWholeCombinedEmojiTitleWithoutRejectingTheChat() {
        val name="👩‍👩‍👧‍👦".repeat(12)
        val content=Gson().toJson(mapOf("kind" to "DIRECT","title" to name,"messages" to listOf(mapOf("speaker" to "OTHER","kind" to "TEXT","text" to "你好"))))
        val transport=object:HttpTransport {
            override fun execute(request:ApiRequest,cancel:Cancellation)=ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
        }
        val result=VisionClient(transport).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation())
        assertEquals(name,result.title)
        assertEquals("你好",result.messages.single().text)
    }
}
