package com.loveadvisor.core.api

import com.google.gson.Gson
import com.loveadvisor.core.model.Speaker
import org.junit.Assert.*
import org.junit.Test

class VisionGeometryTest {
    private fun row(speaker:String,bubble:List<Number>,text:String="原文",textBounds:List<Number> = bubble)=mapOf("speaker" to speaker,"kind" to "TEXT","text" to text,"bubble" to bubble,"textBounds" to textBounds)
    private fun read(rows:List<Map<String,Any>>,reliable:Boolean=true,extra:Map<String,Any> = emptyMap()):VisionConversation {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
            val content=Gson().toJson(mapOf("kind" to "DIRECT","reliable" to reliable,"title" to "小雨","chatBounds" to listOf(0,100,1000,900),"messages" to rows)+extra)
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
    @Test fun finiteFractionalCoordinatesKeepTheActualSideAndText() {
        val result=read(listOf(row("OTHER",listOf(650.2,400.4,869.8,450.3),"这是我说的")))
        assertEquals(Speaker.ME,result.messages.single().speaker)
        assertEquals("这是我说的",result.messages.single().text)
    }
    @Test fun tinyTextBoxRoundingDifferenceDoesNotDiscardAClearBubble() {
        val result=read(listOf(row("OTHER",listOf(130,200,500,250),textBounds=listOf(129,199,501,251))))
        assertEquals(Speaker.OTHER,result.messages.single().speaker)
    }
    @Test fun malformedOptionalInputCoordinatesDoNotDiscardValidMessages() {
        val result=read(listOf(row("OTHER",listOf(130,200,500,250))),extra=mapOf("composer" to listOf(200,920,"bad",970),"keyboardButton" to listOf(-1,920,60,970)))
        assertEquals(1,result.messages.size)
        assertNull(result.composer);assertNull(result.keyboardButton)
    }
    @Test fun malformedOptionalAvatarDoesNotOverrideClearBubbleGeometry() {
        val result=read(listOf(row("OTHER",listOf(650,400,870,450))+mapOf("avatar" to listOf(920,"bad",990,450))))
        assertEquals(Speaker.ME,result.messages.single().speaker)
    }
    @Test fun malformedAvatarStillCannotPublishAnAmbiguousBubble() {
        try { read(listOf(row("ME",listOf(200,200,800,250))+mapOf("avatar" to listOf(920,"bad",990,250))));fail("must reject ambiguous side") }
        catch(e:ApiException) { assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("V_SIDE")) }
    }
    @Test fun missingBubbleAndModelUncertaintyHaveDifferentSafeReasons() {
        val missing=try { read(listOf(mapOf("speaker" to "ME","text" to "private-text")));fail("must reject");"" } catch(e:ApiException) { e.message.orEmpty() }
        val uncertain=try { read(listOf(row("OTHER",listOf(130,200,500,250))),reliable=false);fail("must reject");"" } catch(e:ApiException) { e.message.orEmpty() }
        assertTrue(missing,missing.contains("V_BUBBLE"));assertTrue(uncertain,uncertain.contains("V_UNCERTAIN"))
        assertFalse(missing.contains("private-text"));assertNotEquals(missing,uncertain)
    }
    @Test fun contradictoryAlignedAvatarIsRejectedRatherThanFlippingRoles() {
        try { read(listOf(row("OTHER",listOf(130,200,500,250))+mapOf("avatar" to listOf(920,200,990,250))));fail("must reject role conflict") }
        catch(e:ApiException) { assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("V_SIDE")) }
    }
    @Test fun truncatedJsonReportsFormatFailureWithoutEchoingPrivateContent() {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation)=
            ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "{\"title\":\"private-title\",\"messages\":[")))))) }
        try { VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",Cancellation());fail("must reject truncated JSON") }
        catch(e:ApiException) { assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("V_FORMAT"));assertFalse(e.message.orEmpty().contains("private-title")) }
    }
}
