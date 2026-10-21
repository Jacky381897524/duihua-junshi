package com.loveadvisor.android.overlay

import android.app.UiAutomation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.core.advisor.ReplyStyleCatalog
import org.junit.Assert.*
import org.junit.Test

/** Requires a visible supported chat and enabled service; never saves or writes to the chat. */
class PanelEditorKeyboardDeviceTest {
    @Test fun tappingGlobalStyleInputKeepsTheSamePanelAndUnsavedDraft() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val service=ChatCaptureService.instance?.takeIf { it.overlayAllowed() }
            ?: throw AssertionError("请开启无障碍并停在真实聊天页；本测试不读取或发送消息")
        assertTrue("本测试要求Android 12以上并开启轻毛玻璃",android.os.Build.VERSION.SDK_INT>=31 && service.graph.settings.load().frostedPanelEnabled)
        val field=ChatCaptureService::class.java.getDeclaredField("overlay").apply { isAccessible=true }
        val controller=field.get(service) as OverlayController
        val panelField=OverlayController::class.java.getDeclaredField("panel").apply { isAccessible=true }
        lateinit var panel:AdvisorPanelView
        lateinit var editor:ReplyStyleEditorView
        var editorCreated=false
        var detachments=0
        var attachments=0
        val attachmentListener=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view:View) { attachments++ }
            override fun onViewDetachedFromWindow(view:View) { detachments++ }
        }
        val ime=service.getSystemService(InputMethodManager::class.java)
        try {
            instrumentation.runOnMainSync {
                controller.show();panel=panelField.get(controller) as AdvisorPanelView
                assertNotNull("毛玻璃实际宿主必须建立，不能用降级普通窗口代替",OverlayController::class.java.getDeclaredField("frostedWindow").apply { isAccessible=true }.get(controller))
                editor=ReplyStyleEditorView(service,"com.tencent.mm","键盘测试",ReplyStyleCatalog(),"",{ fail("不能保存专属设置") },{ fail("不能保存全局设置") },{})
                editorCreated=true
                panel.showStyleEditor(editor) {};editor.scopeGroup.check(editor.global.id)
                editor.styleInput.setText("尚未保存的风格草稿")
                panel.addOnAttachStateChangeListener(attachmentListener)
            }
            instrumentation.waitForIdleSync()
            val point=IntArray(2)
            instrumentation.runOnMainSync {
                assertFalse("打开编辑页不应直接抢占键盘焦点",controller.hasEditorLease())
                assertTrue(editor.styleInput.isShown)
                editor.styleInput.getLocationOnScreen(point)
                point[0]+=editor.styleInput.width/2;point[1]+=editor.styleInput.height/2
            }
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,point[0].toFloat(),point[1].toFloat(),0)
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
            }
            SystemClock.sleep(1500)
            instrumentation.runOnMainSync {
                assertTrue("未检测到实际键盘窗口，不能认定键盘回归通过",service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD })
                assertSame(panel,panelField.get(controller));assertTrue(service.overlayAllowed())
                assertEquals("reply-style",panel.currentPageId)
                assertEquals("尚未保存的风格草稿",editor.styleInput.text.toString())
                assertTrue(editor.styleInput.hasFocus())
                assertTrue(controller.hasEditorLease())
                assertEquals("输入过程中不能反复撤下面板",0,detachments)
                assertEquals("输入过程中不能反复重挂面板",0,attachments)
                // A keyboard/configuration notification must resize this editor, not recreate candidates.
                service.onConfigurationChanged(android.content.res.Configuration(service.resources.configuration))
                assertSame(panel,panelField.get(controller))
                assertEquals("reply-style",panel.currentPageId)
                controller.suspendForBackground();service.ensureOverlay()
                assertFalse("恢复草稿时要等待再次点击输入框",controller.hasEditorLease())
                assertSame(panel,panelField.get(controller))
                assertTrue(panel.isAttachedToWindow)
                assertEquals("尚未保存的风格草稿",editor.styleInput.text.toString())
            }
        } finally {
            instrumentation.runOnMainSync {
                if(editorCreated)panel.removeOnAttachStateChangeListener(attachmentListener)
                if(editorCreated)ime.hideSoftInputFromWindow(editor.styleInput.windowToken,0)
                controller.hide()
            }
        }
    }
}
