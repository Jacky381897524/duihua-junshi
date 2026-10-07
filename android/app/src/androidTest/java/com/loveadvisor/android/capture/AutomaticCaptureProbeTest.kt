package com.loveadvisor.android.capture

import android.app.UiAutomation
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.loveadvisor.android.capture.ocr.*
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.capture.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit device probe: the human leaves a chosen WeChat direct conversation visible. */
@RunWith(AndroidJUnit4::class)
class AutomaticCaptureProbeTest {
    @Test fun capturesNativeTextAndAutomaticWindowScreenshotWithoutPreview() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val connectedUntil=System.currentTimeMillis()+10000
        while(ChatCaptureService.instance==null && System.currentTimeMillis()<connectedUntil) Thread.sleep(100)
        val service=ChatCaptureService.instance
        assertNotNull("军师无障碍服务尚未连接",service)
        val window=service!!.reader.read(expectedPackage="com.tencent.mm")
        assertNotNull("请保持一个微信单聊窗口打开",window)
        assertEquals("com.tencent.mm",window!!.packageName)
        assertTrue("微信应用窗口必须有可截图的完整范围",window.viewport.width>0 && window.viewport.height>0)
        File(context.filesDir,"capture-probe.json").writeText(Gson().toJson(window))
        val result=AutomaticWeChatParser().extract(window)
        val summary=when(result) {
            is CaptureResult.Readable -> mapOf("source" to "nodes","messages" to result.snapshot.messages.map { mapOf("speaker" to it.speaker.name,"characters" to it.text.length) })
            is CaptureResult.NeedsImage -> mapOf("source" to "vision-needed","reason" to result.reason)
            else -> mapOf("source" to "not-chat")
        }
        File(context.filesDir,"capture-probe-summary.json").writeText(Gson().toJson(summary))
        assertTrue("原生解析应自动读取或自动转视觉，不能请求预览",result is CaptureResult.Readable || result is CaptureResult.NeedsImage)
        val completed=CountDownLatch(1)
        var capture:CaptureImageResult?=null
        WindowCapture(service).capture(window.windowId,Cancellation()) { capture=it; completed.countDown() }
        assertTrue("自动窗口截图超时",completed.await(15,TimeUnit.SECONDS))
        assertTrue("自动窗口截图失败：$capture",capture is CaptureImageResult.Success)
        val bitmap=(capture as CaptureImageResult.Success).bitmap
        File(context.filesDir,"capture-probe-window.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        assertTrue(bitmap.width>0 && bitmap.height>0)
        bitmap.recycle()
    }
}
