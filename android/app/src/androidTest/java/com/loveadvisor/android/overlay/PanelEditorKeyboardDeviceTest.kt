package com.loveadvisor.android.overlay

import android.app.UiAutomation
import android.os.SystemClock
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
        val field=ChatCaptureService::class.java.getDeclaredField("overlay").apply { isAccessible=true }
        val controller=field.get(service) as OverlayController
        val panelField=OverlayController::class.java.getDeclaredField("panel").apply { isAccessible=true }
        lateinit var panel:AdvisorPanelView
        lateinit var editor:ReplyStyleEditorView
        var editorCreated=false
        val ime=service.getSystemService(InputMethodManager::class.java)
        try {
            instrumentation.runOnMainSync {
                controller.show();panel=panelField.get(controller) as AdvisorPanelView
                editor=ReplyStyleEditorView(service,"com.tencent.mm","键盘测试",ReplyStyleCatalog(),"",{ fail("不能保存专属设置") },{ fail("不能保存全局设置") },{})
                editorCreated=true
                panel.showStyleEditor(editor) {};editor.scopeGroup.check(editor.global.id)
                editor.styleInput.setText("尚未保存的风格草稿")
                assertTrue(editor.styleInput.requestFocus());editor.styleInput.performClick()
                ime.showSoftInput(editor.styleInput,InputMethodManager.SHOW_IMPLICIT)
            }
            SystemClock.sleep(1500)
            instrumentation.runOnMainSync {
                assertTrue("未检测到实际键盘窗口，不能认定键盘回归通过",service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD })
                assertSame(panel,panelField.get(controller));assertTrue(service.overlayAllowed())
                assertEquals("reply-style",panel.currentPageId)
                assertEquals("尚未保存的风格草稿",editor.styleInput.text.toString())
                assertTrue(editor.styleInput.hasFocus())
                // A keyboard/configuration notification must resize this editor, not recreate candidates.
                service.onConfigurationChanged(android.content.res.Configuration(service.resources.configuration))
                assertSame(panel,panelField.get(controller))
                assertEquals("reply-style",panel.currentPageId)
                controller.suspendForBackground();service.ensureOverlay()
                assertSame(panel,panelField.get(controller))
                assertTrue(panel.isAttachedToWindow)
                assertEquals("尚未保存的风格草稿",editor.styleInput.text.toString())
            }
        } finally {
            instrumentation.runOnMainSync {
                if(editorCreated)ime.hideSoftInputFromWindow(editor.styleInput.windowToken,0)
                controller.hide()
            }
        }
    }
}
