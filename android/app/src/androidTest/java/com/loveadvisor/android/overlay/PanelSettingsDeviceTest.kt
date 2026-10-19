package com.loveadvisor.android.overlay
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.settings.*
import com.loveadvisor.core.api.Cancellation
import org.junit.Assert.*
import org.junit.Test
class PanelSettingsDeviceTest {
    @Test fun overlappingCatalogRequestsCanAllFinishAndRefreshAgain() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            var requests=0;val pending=mutableMapOf<String,(String)->Unit>()
            val host=object:SettingsFormHost {
                override fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit) {}
                override fun <T> work(id:String,task:(Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit) { requests++;pending[id]=onFailure }
                override fun message(text:String) {}
                override fun close() {}
            }
            val view=ModelSettingsView(context,AppGraph.get(context),host)
            fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val buttons=all(view).filterIsInstance<Button>().filter { it.text=="刷新模型" }
            assertEquals(2,buttons.size);buttons.forEach { it.performClick() }
            assertEquals(2,pending.size)
            pending.values.toList().forEach { it("模拟失败，重试") };pending.clear()
            buttons.forEach { it.performClick() };assertEquals(4,requests)
        }
    }
    @Test fun openingModelSettingsIsLocalAndProviderSelectionUsesThePanelHost() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            var requests=0;var choices=0
            val host=object:SettingsFormHost {
                override fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit) { choices++ }
                override fun <T> work(id:String,task:(Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit) { requests++ }
                override fun message(text:String) {}
                override fun close() {}
            }
            val view=ModelSettingsView(context,AppGraph.get(context),host)
            fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val buttons=all(view).filterIsInstance<Button>()
            assertEquals(0,requests)
            assertTrue(buttons.any { it.text=="测试判断连接" });assertTrue(buttons.any { it.text=="测试回复连接" });assertTrue(buttons.any { it.text=="测试图片识别" })
            buttons.first { it.text.toString().startsWith("判断服务") }.performClick()
            assertEquals(1,choices);assertEquals(0,requests)
        }
    }
}
