package com.loveadvisor.android.island

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.overlay.AdvisorPanelView
import com.loveadvisor.android.overlay.OverlayController
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.overlay.OverlayEntryMode
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires the real enabled accessibility service and an unlocked supported chat in foreground.
 * These tests verify software/window integration, never certify that vivo displayed a native island.
 * No capture, model requests, clipboard writes, draft edits or sending.
 */
class IslandWindowIntegrationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun field(owner:Any,name:String):Any?=owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(owner)
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun service():ChatCaptureService=ChatCaptureService.instance?.takeIf { it.overlayAllowed() }
        ?: throw AssertionError("设备用例前提：开启无障碍服务、解锁并保持文件传输助手前台；未执行不能计为通过")
    private fun bubble(c:OverlayController)=field(c,"bubble") as View
    private fun coordinates(c:OverlayController,name:String):List<Int> =(field(c,name) as WindowManager.LayoutParams).let { listOf(it.x,it.y,it.width,it.height) }
    private fun swipe(v:View,dx:Float,dy:Float) {
        val time=android.os.SystemClock.uptimeMillis()
        val x=v.width/2f;val y=v.height/2f
        for((i,action) in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).withIndex()) {
            val e=MotionEvent.obtain(time,time+i*40L,action,x+if(i>0)dx else 0f,y+if(i>0)dy else 0f,0)
            v.dispatchTouchEvent(e);e.recycle()
        }
    }
    private fun descendants(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList())
    @Test fun actualWindowsOnlyMoveByGripAndReopenAtTheInitialPosition() {
        val s=service();val c=field(s,"overlay") as OverlayController
        try {
            var initial=emptyList<Int>();var moved=emptyList<Int>()
            main { c.hide();c.setEntryMode(OverlayEntryMode.ISLAND_TEST);assertTrue(c.showFromIsland()) }
            instrumentation.waitForIdleSync()
            main {
                val panel=field(c,"panel") as AdvisorPanelView
                initial=coordinates(c,"panelParams")
                val title=descendants(panel).filterIsInstance<TextView>().first { it.text.toString()=="军师分析" }
                swipe(title,25f,25f);assertEquals(initial,coordinates(c,"panelParams"))
                swipe(panel.dragHandle,20f,20f);moved=coordinates(c,"panelParams")
                assertNotEquals(initial.take(2),moved.take(2))
                assertTrue(c.showFromIsland());assertEquals(moved,coordinates(c,"panelParams"))
                c.hide();assertTrue(c.showFromIsland());assertEquals(initial,coordinates(c,"panelParams"))
                val ime=s.windows.firstOrNull { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                if(ime!=null) { val r=android.graphics.Rect();ime.getBoundsInScreen(r);val p=field(c,"panelParams") as WindowManager.LayoutParams;assertTrue(p.y+p.height<=r.top-(72*s.resources.displayMetrics.density).toInt()) }
            }
        } finally { main { c.hide();c.setEntryMode(OverlayEntryMode.BUBBLE);s.ensureOverlay() } }
    }
    @Test fun candidatesScrollWithoutDraggingAndOrdinaryTitleStillMovesTheBall() {
        val s=service();val c=field(s,"overlay") as OverlayController
        try {
            main {
                c.hide();c.setEntryMode(OverlayEntryMode.ISLAND_TEST);assertTrue(c.showFromIsland())
                val panel=field(c,"panel") as AdvisorPanelView
                val token=SessionToken("com.tencent.mm",1,"window-test",1,1)
                val strategies=listOf(Strategy.ACKNOWLEDGE,Strategy.HUMOR,Strategy.APPRECIATION,Strategy.TEASING,Strategy.INVITATION,Strategy.CALM)
                val replies=strategies.mapIndexed { i,strategy -> ReplyPlan("test-$i",strategy,"本地窗口测试候选${i+1}\n第二行测试文字","测试","测试","测试",ReplyBranches("测试","测试","测试")) }
                panel.render(BatchState(VisibleBatch(RequestTicket(token,1),AdvisorBatch(AdvisorAnalysis(emptyList(),emptyList(),emptyList(),"本地测试",1,"不读取、不发送"),replies))))
                c.resize()
            }
            instrumentation.waitForIdleSync()
            main {
                val panel=field(c,"panel") as AdvisorPanelView;val before=coordinates(c,"panelParams")
                val scroll=descendants(panel).filterIsInstance<ScrollView>().first()
                swipe(scroll,0f,-(scroll.height*.65f));assertTrue("六条候选应能滚动",scroll.scrollY>0)
                assertEquals(before,coordinates(c,"panelParams"))
                val disabledFill=descendants(panel).filterIsInstance<TextView>().first { it.text.toString()=="填入" }
                swipe(disabledFill,20f,20f);assertEquals(before,coordinates(c,"panelParams"))
                c.hide();c.setEntryMode(OverlayEntryMode.BUBBLE);c.show()
            }
            instrumentation.waitForIdleSync()
            main {
                val p=field(c,"panel") as AdvisorPanelView;val ball=coordinates(c,"bubbleParams");val panel=coordinates(c,"panelParams")
                swipe(p.dragHandle,-30f,-30f)
                val afterBall=coordinates(c,"bubbleParams");val afterPanel=coordinates(c,"panelParams")
                assertNotEquals(ball.take(2),afterBall.take(2));assertEquals(afterBall[0]-ball[0],afterPanel[0]-panel[0]);assertEquals(afterBall[1]-ball[1],afterPanel[1]-panel[1])
            }
        } finally { main { c.hide();s.ensureOverlay() } }
    }
    @Test fun permissionFallbackAttachesBallAndNativeProofIsRevokedBySuspension() {
        val s=service();val c=field(s,"overlay") as OverlayController;val coordinator=field(s,"island") as NativeIslandCoordinator
        val original=s.graph.settings.load()
        try {
            main {
                s.graph.settings.update { it.copy(nativeIslandEnabled=true) }
                c.hide();c.showBubble();c.setEntryMode(OverlayEntryMode.ISLAND)
                assertFalse(bubble(c).isAttachedToWindow)
                // Same path used by watch() when it detects permission/channel loss.
                coordinator.refreshEligibility(s.islandEnvironment().copy(notificationsEnabled=false))
                assertTrue("当前仍在聊天时必须立即挂载回退球",(field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
                coordinator.refreshEligibility(s.islandEnvironment().copy(allowed=false))
                assertFalse((field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
            }
            main { s.graph.settings.save(original);s.ensureOverlay() }
        } finally { main { coordinator.stop();s.graph.settings.save(original);c.hide();s.ensureOverlay() } }
    }
    @Test fun validNativeCallbackConfirmsOnlyUntilTheLeaseIsRetired() {
        val s=service();val c=field(s,"overlay") as OverlayController;val coordinator=field(s,"island") as NativeIslandCoordinator
        val original=s.graph.settings.load();var lease:String?=null;var accepted=false
        val done=CountDownLatch(1)
        try {
            main {
                assertTrue("此设备用例需先授予通知权限",s.islandEnvironment().notificationsEnabled)
                s.graph.settings.update { it.copy(nativeIslandEnabled=true) };coordinator.stop();coordinator.refreshEligibility(s.islandEnvironment())
                lease=coordinator.lease?.id;assertNotNull(lease);assertTrue((field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
                s.onNativeIslandTap(lease!!) { accepted=it;done.countDown() }
            }
            assertTrue("前台校验超时",done.await(6,TimeUnit.SECONDS));assertTrue(accepted)
            main {
                assertNotNull(s.graph.settings.load().nativeIslandConfirmedBuild);assertFalse(bubble(c).isAttachedToWindow)
                coordinator.suspend();assertFalse(coordinator.handleNativeTap(lease!!));s.ensureOverlay()
                assertTrue((field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
                s.graph.settings.update { it.copy(overlayEnabled=false) }
                var rejected=false;s.onNativeIslandTap(lease!!) { rejected=!it };assertTrue(rejected)
            }
        } finally { main { coordinator.stop();s.graph.settings.save(original);c.hide();s.ensureOverlay() } }
    }
    @Test fun staleCallbackIsRejectedAfterSwitchingToAnotherApplicationWindow() {
        val s=service();val c=field(s,"overlay") as OverlayController;val coordinator=field(s,"island") as NativeIslandCoordinator
        val original=s.graph.settings.load();val target=s.islandScope()!!.packageName;var old:String?=null
        try {
            main {
                assertTrue("需先授予通知权限",s.islandEnvironment().notificationsEnabled)
                s.graph.settings.update { it.copy(nativeIslandEnabled=true) };coordinator.stop();coordinator.refreshEligibility(s.islandEnvironment())
                old=coordinator.lease?.id;assertNotNull(old)
                s.startActivity(android.content.Intent(s,com.loveadvisor.android.settings.OverlaySettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            var left=false
            for(i in 0..40) { main { left=!s.overlayAllowed() };if(left)break;Thread.sleep(50) }
            assertTrue("切换离开聊天应撤销入口资格",left)
            main { var rejected=false;s.onNativeIslandTap(old!!) { rejected=!it };assertTrue(rejected) }
        } finally {
            main {
                coordinator.stop();s.graph.settings.save(original);c.hide()
                s.packageManager.getLaunchIntentForPackage(target)?.let { s.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
    @Test fun publicationFailureRestoresTheRealFallbackWindow() {
        val s=service();val c=field(s,"overlay") as OverlayController;val coordinator=field(s,"island") as NativeIslandCoordinator
        val original=s.graph.settings.load()
        try {
            main {
                s.graph.settings.update { it.copy(nativeIslandEnabled=true) }
                coordinator.refreshEligibility(s.islandEnvironment().copy(notificationsEnabled=false))
                c.setEntryMode(OverlayEntryMode.ISLAND)
                assertFalse((field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
                // Exercise the exact handler used for FAILED publish results, not a mock notification.
                coordinator.javaClass.getDeclaredMethod("fail",String::class.java).apply { isAccessible=true }.invoke(coordinator,"设备用例：发布失败")
                assertTrue((field(c,"bubbleWindow") as com.loveadvisor.core.overlay.WindowRegistration).registered)
            }
            instrumentation.waitForIdleSync();main { assertTrue(bubble(c).isAttachedToWindow) }
        } finally { main { coordinator.stop();s.graph.settings.save(original);c.hide();s.ensureOverlay() } }
    }
}
