package com.loveadvisor.android.overlay
import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.settings.*
import com.loveadvisor.core.api.Cancellation
import org.junit.Assert.*
import org.junit.Test
class CompactPanelDeviceTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun intentAndTwoSmallActionsShareOneRow() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val c=test.targetContext;val d=c.resources.displayMetrics.density
            val panel=AdvisorPanelView(c,{},{_,_->},{},{},{},{},{true},{true})
            panel.measure(View.MeasureSpec.makeMeasureSpec((320*d).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((470*d).toInt(),View.MeasureSpec.EXACTLY));panel.layout(0,0,panel.measuredWidth,panel.measuredHeight)
            val views=all(panel);val read=views.first { it.contentDescription=="读取信息" };val analyze=views.first { it.contentDescription=="判断分析" }
            val intent=views.filterIsInstance<TextView>().first { it.text.startsWith("对方真实意图") }
            assertSame(intent.parent,read.parent);assertSame(analyze.parent,read.parent)
            assertTrue(read.width<panel.width*.22);assertTrue(analyze.width<panel.width*.22);assertTrue(intent.width>panel.width*.54)
        }
    }
    @Test fun compactModelFormReducesSpaceWithoutHidingSave() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val c=test.targetContext
            val host=object:SettingsFormHost {
                override fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit) {}
                override fun <T> work(id:String,task:(Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit) {}
                override fun message(text:String) {}
                override fun close() {}
            }
            val normal=ModelSettingsView(c,AppGraph.get(c),host)
            val compact=ModelSettingsView(c,AppGraph.get(c),host,compact=true)
            val width=(300*c.resources.displayMetrics.density).toInt()
            for(v in listOf(normal,compact))v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            assertTrue(compact.measuredHeight<normal.measuredHeight*.85)
            val save=all(compact).filterIsInstance<Button>().first { it.text=="保存并返回" }
            assertTrue(save.measuredHeight>=44*c.resources.displayMetrics.density)
        }
    }
}
