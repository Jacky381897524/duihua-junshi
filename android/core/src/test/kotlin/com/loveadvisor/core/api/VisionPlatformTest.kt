package com.loveadvisor.core.api
import com.google.gson.Gson
import com.loveadvisor.core.capture.ChatApps
import org.junit.Assert.*
import org.junit.Test
class VisionPlatformTest {
    @Test fun platformAndNativePrivateChatRestrictionsReachVisionRequest() {
        for(pkg in listOf(ChatApps.QQ,ChatApps.DOUYIN)) {
            var sent=""
            val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
                sent=request.body.orEmpty()
                val content=Gson().toJson(mapOf("kind" to "DIRECT","title" to "小雨","messages" to listOf(mapOf("speaker" to "OTHER","text" to "你好"))))
                return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
            } }
            val result=VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",Cancellation(),pkg)
            assertEquals("你好",result.messages.single().text)
            assertTrue(sent.contains(pkg) && sent.contains(ChatApps.label(pkg)) && sent.contains("评论区") && sent.contains("UNKNOWN"))
        }
    }
    @Test fun commentsAndFeedsCannotPublishCandidateContext() {
        val wire=object:HttpTransport { override fun execute(request:ApiRequest,cancel:Cancellation)=ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "{\"kind\":\"UNKNOWN\"}")))))) }
        try { VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/png;base64,AA==",Cancellation(),ChatApps.DOUYIN);fail("应拒绝视频流和评论") } catch(_:ApiException){}
    }
}
