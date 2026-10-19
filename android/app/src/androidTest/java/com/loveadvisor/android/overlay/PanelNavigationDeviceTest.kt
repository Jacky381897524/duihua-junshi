package com.loveadvisor.android.overlay
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class PanelNavigationDeviceTest {
    @Test fun nestedPagePreservesUnsavedTextAndRestoresEditableFocusOnReturn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            var editable=false
            val host=PanelPageHost(context,{ editable=it },{})
            val draft=EditText(context).apply { setText("尚未保存的特殊昵称👩🏽‍💻") }
            host.push("form",draft,true);host.push("choices",TextView(context),false)
            assertFalse(editable);assertTrue(host.pop());assertTrue(editable)
            assertEquals("尚未保存的特殊昵称👩🏽‍💻",draft.text.toString())
            host.clear();assertFalse(editable);assertFalse(host.hasPages)
        }
    }
}
