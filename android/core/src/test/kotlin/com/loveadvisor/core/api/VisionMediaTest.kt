package com.loveadvisor.core.api
import com.google.gson.Gson
import com.loveadvisor.core.model.Speaker
import org.junit.Assert.*
import org.junit.Test
class VisionMediaTest {
    @Test fun stickerAndImageMeaningRemainInConversationContext() {
        val rows=listOf(mapOf("speaker" to "OTHER","kind" to "STICKER","text" to "","description" to "小猫捂脸","emotion" to "害羞","y" to 300),mapOf("speaker" to "ME","kind" to "IMAGE","text" to "","description" to "一份蛋糕","emotion" to "分享","y" to 420))
        val wire=object:HttpTransport {
            override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
                val content=VisionFixtures.content(Gson().toJson(mapOf("kind" to "DIRECT","title" to "小雨","messages" to rows)))
                return ApiResponse(200,Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content))))))
            }
        }
        val result=VisionClient(wire).read(ProviderPresets.OPENROUTER.vision("fake"),"data:image/jpeg;base64,AA==",Cancellation())
        assertEquals(2,result.messages.size)
        assertEquals(Speaker.OTHER,result.messages.first().speaker)
        assertTrue(result.messages.first().text.contains("表情包") && result.messages.first().text.contains("害羞"))
        assertTrue(result.messages.last().text.contains("图片") && result.messages.last().text.contains("蛋糕"))
    }
}
