package com.loveadvisor.android.overlay

import android.app.NotificationManager
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.core.overlay.WindowRegistration
import org.junit.Assert.*
import org.junit.Test

/** Real-window checks require an enabled service and supported chat foreground; compile is not a device pass. */
class TopCapsuleWindowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun field(owner:Any,name:String):Any?=owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(owner)
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    @Test fun leftCapsuleReplacesTheBallWithoutPostingAnIslandNotification() {
        val s=ChatCaptureService.instance?.takeIf { it.overlayAllowed() } ?: throw AssertionError("须启用无障碍并保持已解锁的聊天应用前台")
        val c=field(s,"overlay") as OverlayController;val original=s.graph.settings.load()
        try {
            main { c.hide();s.graph.settings.update { it.copy(topCapsuleEnabled=true,nativeIslandEnabled=false,capsuleOffsetXdp=0f,capsuleOffsetYdp=0f) };s.ensureOverlay() }
            instrumentation.waitForIdleSync()
            main {
                val v=field(c,"capsule") as TopCapsuleView;val p=field(c,"capsuleParams") as WindowManager.LayoutParams
                assertTrue(v.isAttachedToWindow);assertFalse((field(c,"bubbleWindow") as WindowRegistration).registered)
                val screen=s.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
                assertTrue("默认位置必须在摄像头左侧",p.x+p.width<=screen.centerX())
                assertFalse(s.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==14014 })
                val generation=s.captureGeneration();v.performClick();assertEquals(generation,s.captureGeneration())
                assertTrue((field(c,"panelWindow") as WindowRegistration).registered)
                assertEquals("拖动候选面板",(field(c,"panel") as AdvisorPanelView).dragHandle.contentDescription)
            }
            main { c.hide();s.graph.settings.update { it.copy(topCapsuleEnabled=false) };s.ensureOverlay() }
            instrumentation.waitForIdleSync()
            main { assertFalse((field(c,"capsuleWindow") as WindowRegistration).registered);assertTrue((field(c,"bubbleWindow") as WindowRegistration).registered) }
        } finally { main { s.graph.settings.save(original);c.hide();s.settingsChanged() } }
    }
    @Test fun onlyHeldMovementAdjustsPositionAndItSurvivesDetaching() {
        val s=ChatCaptureService.instance?.takeIf { it.overlayAllowed() } ?: throw AssertionError("须启用无障碍并保持聊天前台")
        val c=field(s,"overlay") as OverlayController;val original=s.graph.settings.load()
        try {
            main { c.hide();s.graph.settings.update { it.copy(topCapsuleEnabled=true,nativeIslandEnabled=false,capsuleOffsetXdp=0f,capsuleOffsetYdp=0f) };s.ensureOverlay() }
            instrumentation.waitForIdleSync()
            main {
                val v=field(c,"capsule") as TopCapsuleView
                fun xy()=(field(c,"capsuleParams") as WindowManager.LayoutParams).let { listOf(it.x,it.y) }
                val before=xy();val start=android.os.SystemClock.uptimeMillis()
                fun e(action:Int,delta:Long,dx:Float=0f) { val event=MotionEvent.obtain(start,start+delta,action,20f+dx,10f,0);v.dispatchTouchEvent(event);event.recycle() }
                val move=12*s.resources.displayMetrics.density
                e(MotionEvent.ACTION_DOWN,0);e(MotionEvent.ACTION_MOVE,50,move);e(MotionEvent.ACTION_UP,90,move)
                assertEquals(before,xy());assertFalse((field(c,"panelWindow") as WindowRegistration).registered)
                e(MotionEvent.ACTION_DOWN,1000);e(MotionEvent.ACTION_MOVE,1700,move);e(MotionEvent.ACTION_UP,1750,move)
                val adjusted=xy();assertNotEquals(before,adjusted);assertTrue(s.graph.settings.load().capsuleOffsetXdp>0f)
                c.suppress();s.ensureOverlay();assertEquals(adjusted,xy())
            }
        } finally { main { s.graph.settings.save(original);c.hide();s.settingsChanged() } }
    }
    @Test fun heldDragMovesImmediatelyWhenSavedPositionWasClamped() {
        val s=ChatCaptureService.instance?.takeIf { it.overlayAllowed() } ?: throw AssertionError("须启用无障碍并保持聊天前台")
        val c=field(s,"overlay") as OverlayController;val original=s.graph.settings.load()
        try {
            main { c.hide();s.graph.settings.update { it.copy(topCapsuleEnabled=true,nativeIslandEnabled=false,capsuleOffsetYdp=4096f) };s.ensureOverlay() }
            instrumentation.waitForIdleSync()
            main {
                val v=field(c,"capsule") as TopCapsuleView
                val before=(field(c,"capsuleParams") as WindowManager.LayoutParams).y
                val move=12*s.resources.displayMetrics.density
                val start=android.os.SystemClock.uptimeMillis()
                fun e(action:Int,delta:Long,y:Float) { val event=MotionEvent.obtain(start,start+delta,action,20f,y,0);v.dispatchTouchEvent(event);event.recycle() }
                e(MotionEvent.ACTION_DOWN,0,10f);e(MotionEvent.ACTION_MOVE,700,10f-move)
                assertEquals("被边界夹住后仍应立即跟手上移",(before-move).toDouble(),(field(c,"capsuleParams") as WindowManager.LayoutParams).y.toDouble(),1.0)
                e(MotionEvent.ACTION_UP,750,10f-move)
                assertTrue(s.graph.settings.load().capsuleOffsetYdp<4096f)
            }
        } finally { main { s.graph.settings.save(original);c.hide();s.settingsChanged() } }
    }
}
