package com.loveadvisor.android.integration

import android.graphics.*
import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.capture.ocr.*
import com.loveadvisor.android.input.*
import com.loveadvisor.android.settings.ConnectionTestImage
import com.loveadvisor.core.api.*
import com.loveadvisor.core.capture.AutomaticConversation
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit device checks: no sends, no saved chat text, one synthetic vision request. */
class V1301DeviceTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun field(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply { isAccessible=true }
    @Test fun configuredVisionKeepsTwoSidesAndExcludesWallpaperWords() {
        val graph=AppGraph.get(i.targetContext);val settings=graph.settings.load()
        val key=settings.vision.apiKey.ifBlank { if(ProviderPresets.ownsOpenRouter(settings.vision))ProviderPresets.openRouterKey(settings.profile,settings.vision) else "" }
        assumeTrue("未配置视觉密钥，跳过真实模型测试",key.isNotBlank())
        val original=Base64.decode(ConnectionTestImage.dataUrl().substringAfter(','),Base64.DEFAULT)
        val image=BitmapFactory.decodeByteArray(original,0,original.size).copy(Bitmap.Config.ARGB_8888,true)
        val canvas=Canvas(image);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.DKGRAY;textSize=14f }
        canvas.drawText("我是壁纸文字 不要作为聊天消息",55f,390f,paint)
        val bytes=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle()
        val started=android.os.SystemClock.elapsedRealtime()
        val result=graph.vision.read(settings.vision.copy(apiKey=key),"data:image/png;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP),Cancellation())
        assertEquals(listOf(Speaker.OTHER,Speaker.ME),result.messages.map { it.speaker })
        assertEquals(listOf("你好","收到"),result.messages.map { it.text.trim() })
        File(i.targetContext.filesDir,"v1301-vision-test.txt").writeText("twoSides=true; wallpaperExcluded=true; model=${settings.vision.selectedModelId}; elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}")
    }
    @Test fun officialFillWritesAndClearsAnEmptyFileAssistantDraft() {
        assumeTrue(Build.VERSION.SDK_INT>=33)
        val s=ChatCaptureService.instance ?: error("无障碍服务未连接")
        assertTrue("须保持微信文件传输助手前台",s.overlayAllowed())
        val window=s.reader.foreground() ?: error("没有前台窗口")
        assertEquals("com.tencent.mm",window.packageName)
        val method=s.inputMethod ?: error("输入接口未初始化")
        assertEquals("com.tencent.mm",method.currentInputEditorInfo?.packageName)
        val connection=method.currentInputConnection ?: error("请先点文件传输助手的空文字输入框")
        assertEquals("测试只允许空草稿","",connection.getSurroundingText(4096,4096,0)?.text?.toString())
        val captured=CountDownLatch(1);var image:CaptureImageResult?=null
        WindowCapture(s).capture(window.windowId,Cancellation()) { image=it;captured.countDown() }
        assertTrue(captured.await(8,TimeUnit.SECONDS));assertTrue(image is CaptureImageResult.Success)
        val bitmap=(image as CaptureImageResult.Success).bitmap
        val ref=VisualFillReference(window.windowId,s.captureGeneration(),VisualFillReference.stamp(bitmap)!!,null)
        bitmap.recycle()
        val conversation=field(s,"conversation").get(s) as AutomaticConversation
        val old=conversation.current;val oldIdentity=field(s,"identity").get(s);val oldRef=field(s,"visualFill").get(s)
        val text="对话军师草稿测试（不会发送）"
        val snapshot=ChatSnapshot("com.tencent.mm",window.windowId,ConversationIdentity("local-fill-test","文件传输助手",ChatKind.DIRECT,true),listOf(ChatMessage("local",Speaker.ME,null,"本地测试上下文")),source=CaptureSource.OCR)
        val done=CountDownLatch(1);var success=false;var message="";val started=android.os.SystemClock.elapsedRealtime();val epoch=s.editorGeneration()
        try {
            i.runOnMainSync {
                conversation.restore(snapshot);field(s,"identity").set(s,snapshot.identity);field(s,"visualFill").set(s,ref)
                s.graph.controller.observe(snapshot)
                ReplyFiller(s).fill(s.graph.tracker.currentToken!!,text) { ok,result -> success=ok;message=result;done.countDown() }
            }
            assertTrue("填入回调超时",done.await(28,TimeUnit.SECONDS));assertTrue(message,success)
            assertEquals(text,connection.getSurroundingText(4096,4096,0)?.text?.toString())
            File(i.targetContext.filesDir,"v1301-fill-test.txt").writeText("success=true; elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}; generationStable=${epoch==s.editorGeneration()}")
        } finally {
            if(epoch==s.editorGeneration() && method.currentInputEditorInfo?.packageName=="com.tencent.mm" && connection.getSurroundingText(4096,4096,0)?.text?.toString()==text) {
                i.runOnMainSync { connection.setSelection(0,text.length);connection.commitText("",1,null) }
                assertEquals("必须清除测试草稿","",connection.getSurroundingText(4096,4096,0)?.text?.toString())
            }
            i.runOnMainSync {
                if(old==null)conversation.clear() else conversation.restore(old)
                field(s,"identity").set(s,oldIdentity);field(s,"visualFill").set(s,oldRef)
                s.graph.controller.invalidate("本地草稿测试结束，请重新读取")
            }
        }
    }
}
