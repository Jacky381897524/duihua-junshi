package com.loveadvisor.android.integration
import android.content.Intent
import android.view.*
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Test

class SettingsConnectionButtonsTest {
    @Test fun eachOfThreeProvidersHasAnActualRequestTestButton() {
        val i=InstrumentationRegistry.getInstrumentation()
        val activity=i.startActivitySync(Intent(i.targetContext,SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            i.runOnMainSync {
                fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
                val labels=all(activity.window.decorView).filterIsInstance<Button>().map { it.text.toString() }
                assertTrue(labels.contains("测试判断连接")); assertTrue(labels.contains("测试回复连接")); assertTrue(labels.contains("测试图片识别"))
            }
        } finally { i.runOnMainSync { activity.finish() } }
    }
}
