package com.loveadvisor.android.capture

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.core.capture.AutomaticConversation
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Fresh runtime only: injects disposable local rows, performs no capture/model/fill/send. */
class RetainedReadContextDeviceTest {
    @Test fun activityFocusAndBackgroundNeverDiscardDirectOrGroupReadTargets() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val service=ChatCaptureService.instance?.takeIf { it.overlayAllowed() }
            ?: throw AssertionError("请开启服务并停在真实聊天页面")
        val graph=service.graph
        assumeTrue("此用例只用于没有已读记录和请求的新测试进程",graph.tracker.lastSnapshot==null && !graph.coordinator.state.loading)
        val conversationField=ChatCaptureService::class.java.getDeclaredField("conversation").apply { isAccessible=true }
        val conversation=conversationField.get(service) as AutomaticConversation
        val identityField=ChatCaptureService::class.java.getDeclaredField("identity").apply { isAccessible=true }
        val leave=ChatCaptureService::class.java.getDeclaredMethod("leaveForeground",String::class.java).apply { isAccessible=true }
        val check=ChatCaptureService::class.java.getDeclaredMethod("checkForeground").apply { isAccessible=true }
        try {
            for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP)) {
                lateinit var snapshot:ChatSnapshot
                lateinit var token:SessionToken
                val target=ReplyTarget("local-question")
                instrumentation.runOnMainSync {
                    val window=service.glassWindow!!
                    snapshot=ChatSnapshot(window.packageName,window.windowId,ConversationIdentity("local-${kind.name}","本地测试对象",kind,true),listOf(ChatMessage("local-question",Speaker.OTHER,if(kind==ChatKind.GROUP)"本地成员" else null,"然后呢")))
                    conversation.restore(snapshot);identityField.set(service,snapshot.identity)
                    graph.controller.observe(snapshot)
                    val request=graph.coordinator.begin(snapshot,target)!!
                    graph.coordinator.fail(request.requestTicket,"本地测试：未请求模型")
                    token=graph.tracker.currentToken!!
                    val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                        packageName=window.packageName
                        className=if(window.packageName=="com.tencent.mm")"com.tencent.mm.ui.LauncherUI" else "${window.packageName}.ChatActivity"
                    }
                    service.onAccessibilityEvent(event)
                    assertEquals(snapshot,service.currentChatSnapshot)
                    assertEquals(token,graph.tracker.currentToken)
                    assertEquals(target,graph.coordinator.targetFor(snapshot))
                    leave.invoke(service,"本地测试暂离前台")
                    assertEquals(snapshot,service.currentChatSnapshot)
                    assertEquals(token,graph.tracker.currentToken)
                    assertEquals(target,graph.coordinator.targetFor(snapshot))
                    check.invoke(service)
                }
                val deadline=SystemClock.uptimeMillis()+4000
                while(!service.overlayAllowed() && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100)
                instrumentation.runOnMainSync {
                    assertTrue("未重新取得实际聊天前台",service.overlayAllowed())
                    assertEquals(snapshot,service.currentChatSnapshot)
                    assertEquals(token,graph.tracker.currentToken)
                    assertEquals(target,graph.coordinator.targetFor(snapshot))
                }
            }
        } finally {
            instrumentation.runOnMainSync { conversation.clear();identityField.set(service,null);graph.controller.invalidate("本地用例结束") }
        }
    }
}
