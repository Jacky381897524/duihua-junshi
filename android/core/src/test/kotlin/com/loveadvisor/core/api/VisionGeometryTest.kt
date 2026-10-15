package com.loveadvisor.core.api

import com.google.gson.Gson
import com.loveadvisor.core.model.Speaker
import org.junit.Assert.*
import org.junit.Test

class VisionGeometryTest {
    private fun row(speaker:String,bubble:List<Int>,text:String="原文",textBounds:List<Int> = bubble)=mapOf("speaker" to speaker,"kind" to "TEXT","text" to text,"bubble" to bubble,"textBounds" to textBounds)
    private fun read(rows:List<Map<String,Any>>,reliable:Boolean=true):VisionConversation {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            val content=Gson().toJson(mapOf("kind" to "DIRECT","reliable" to reliable,"title" to "小雨","chatBounds" to listOf(0,100,1000,900),"messages" to rows))
            return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
        } }
        return VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",Cancellation())
    }
    @Test fun sidesAndOrderComeFromGeometryEvenWhenModelTagsAndOrderAreWrong() {
        val result=read(listOf(row("OTHER",listOf(650,400,870,450),"我接着说"),row("ME",listOf(130,200,500,250),"对方先说")))
        assertEquals(listOf("对方先说","我接着说"),result.messages.map { it.text })
        assertEquals(listOf(Speaker.OTHER,Speaker.ME),result.messages.map { it.speaker })
    }
    @Test fun missingBubbleCoordinatesCannotPublishGuessedRoles() {
        try { read(listOf(mapOf("speaker" to "ME","text" to "没有气泡坐标")));fail("must reject ungrounded role") } catch(_:ApiException){}
    }
    @Test fun centeredAmbiguousBubbleCannotPublishCandidates() {
        try { read(listOf(row("ME",listOf(200,200,800,250))));fail("must reject ambiguous side") } catch(_:ApiException){}
    }
    @Test fun wallpaperTextOutsideItsClaimedBubbleCannotEnterContext() {
        try { read(listOf(row("OTHER",listOf(130,200,500,250),"壁纸文字",listOf(50,300,900,350))));fail("must reject background text") } catch(_:ApiException){}
    }
    @Test fun uncertainReadIsNotSilentlyReducedToAConfidentPartialThread() {
        try { read(listOf(row("OTHER",listOf(130,200,500,250))),reliable=false);fail("must reject partial uncertain read") } catch(_:ApiException){}
    }
    @Test fun alignedRightAvatarDisambiguatesWideSelfBubble() {
        val result=read(listOf(row("OTHER",listOf(140,200,870,350))+mapOf("avatar" to listOf(920,200,990,250))))
        assertEquals(Speaker.ME,result.messages.single().speaker)
    }
}
