package com.loveadvisor.android.integration
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.MainActivity
import com.loveadvisor.android.knowledge.AssetKnowledgeStore
import com.loveadvisor.android.overlay.AdvisorPanelView
import com.loveadvisor.android.overlay.ReplyCardView
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class AppFlowTest {
    @Test fun bundledKnowledgeIsReadableAndLicensed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val docs=AssetKnowledgeStore(context).documents
        assertEquals(45,docs.size); assertEquals(45,docs.map { it.id }.distinct().size)
        assertTrue(docs.all { it.text.isNotBlank() })
        listOf("Jev-MIT.txt","Goutou-MIT.txt","SOURCES.json").forEach { file -> assertTrue(context.assets.open("notices/$file").bufferedReader().use { it.readText() }.isNotBlank()) }
    }
    @Test fun nativeSixCardsRemainOnFailureAndStaleSessionDisablesFillOnly() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.runOnMainSync {
            var current=true; var refreshed=0; var copied=0; var filled=0
            val panel=AdvisorPanelView(context,{ copied++ },{ _,_ -> filled++ },{ refreshed++ },{},{},{},{ current },{ true })
            val replies=(1..6).map { ReplyPlan("r$it",if(it<3) Strategy.CALM else Strategy.ACKNOWLEDGE,"回复 $it","根据对话选择当前表达方式","当前场景","可能不够直接",ReplyBranches("继续倾听","给对方时间","尊重拒绝")) }
            val batch=AdvisorBatch(AdvisorAnalysis(emptyList(),emptyList(),emptyList(),"测试分析",null,"核对后发送"),replies)
            val visible=VisibleBatch(RequestTicket(SessionToken("com.tencent.mm",7,"confirmed",1,1),1),batch)
            panel.render(BatchState(visible))
            fun children(view:View):List<View> = if(view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) }+view else listOf(view)
            val before=children(panel).filterIsInstance<ReplyCardView>(); assertEquals(6,before.size)
            children(panel).filterIsInstance<Button>().first { it.text.startsWith("刷新换一批") }.performClick(); assertEquals(1,refreshed)
            panel.render(BatchState(visible,error="HTTP 503")); assertEquals(before,children(panel).filterIsInstance<ReplyCardView>())
            current=false; panel.render(BatchState(visible))
            assertTrue(before.all { it.fillButton?.isEnabled==false })
            children(before.first()).filterIsInstance<Button>().first { it.text=="复制" }.performClick()
            assertEquals(1,copied); assertEquals(0,filled); panel.stopAnimations()
        }
    }
    @Test fun launcherOpensWithoutApiCredentialsOrAccessibilityPermission() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { assertFalse(activity.isFinishing) } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
