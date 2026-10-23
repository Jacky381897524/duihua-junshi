package com.loveadvisor.core.capture

import com.google.gson.Gson
import com.loveadvisor.core.api.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MessageTimeCaptureTest {
    @Test fun visionKeepsVisibleTimeSeparatorsAsMetadataAndDoesNotMakeThemChatMessages() {
        val response="""{"kind":"DIRECT","title":"朋友","messages":[
            {"speaker":"OTHER","text":"前天那场电影","timeLabel":"前天 18:00"},
            {"speaker":"ME","text":"已经看完了","timeLabel":null},
            {"speaker":"OTHER","text":"今天下班啦","timeLabel":"今天 18:00"}
        ]}"""
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation)=ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to response)))))) }
        val read=VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",Cancellation())
        val times=read.messages.map { Gson().toJsonTree(it).asJsonObject["time"]?.asJsonObject?.get("label")?.asString }
        assertEquals(listOf("前天 18:00","前天 18:00","今天 18:00"),times)
        assertEquals(3,read.messages.size)
    }
    @Test fun nativeParserAttachesCalendarSeparatorsWithoutReadingThemAsBubbles() {
        fun node(text:String,r:RectI,editable:Boolean=false)=NodeRecord(null,text,if(editable)"EditText" else "TextView",editable,r)
        val window=WindowSnapshot(ChatApps.WECHAT,7,RectI(0,0,1000,2000),listOf(
            node("朋友",RectI(400,80,600,145)),node("",RectI(120,1700,800,1800),true),
            node("2026年10月20日 18:00",RectI(350,250,650,290)),
            node("前天那场电影",RectI(130,320,600,400)),
            node("今天 18:00",RectI(350,450,650,490)),
            node("今天下班啦",RectI(130,520,600,600))))
        val read=(AutomaticWeChatParser().extract(window) as CaptureResult.Readable).snapshot
        assertEquals(listOf("前天那场电影","今天下班啦"),read.messages.map { it.text })
        assertEquals(listOf("2026年10月20日 18:00","今天 18:00"),read.messages.map { Gson().toJsonTree(it).asJsonObject["time"]?.asJsonObject?.get("label")?.asString })
    }
}
