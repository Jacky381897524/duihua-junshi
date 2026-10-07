package com.loveadvisor.android.integration
import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import org.junit.Assert.*
import org.junit.Test

/** Manual device check: leave a WeChat direct conversation open with accessibility enabled. */
class PersistentOverlayDeviceTest {
    @Test fun dockedOverlayMovesTogetherAndOnlyDisplaysInForegroundWechat() {
        val i=InstrumentationRegistry.getInstrumentation()
        val ui=i.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val until=SystemClock.elapsedRealtime()+5000
        while(ChatCaptureService.instance==null && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
        val service=ChatCaptureService.instance
        assertNotNull("请先启用无障碍服务并打开微信单聊",service)
        // Exercise the production ensure/process callback ordering without a frame between calls.
        // Before 0.2.5 this can add the same ImageView twice and crash the accessibility service.
        i.runOnMainSync { repeat(10) { service!!.ensureOverlay() } }
        fun find(label:String):AccessibilityNodeInfo? {
            fun visit(node:AccessibilityNodeInfo,depth:Int):AccessibilityNodeInfo? {
                if(depth>12) return null
                if(node.contentDescription?.toString()==label || node.text?.toString()==label) return node
                for(n in 0 until node.childCount.coerceAtMost(80)) node.getChild(n)?.let { visit(it,depth+1) }?.let { return it }
                return null
            }
            return ui.windows.filter { it.type==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }.mapNotNull { it.root }.firstNotNullOfOrNull { visit(it,0) }
        }
        fun waitFor(label:String):AccessibilityNodeInfo? {
            val deadline=SystemClock.elapsedRealtime()+5000
            do { find(label)?.let { return it }; SystemClock.sleep(100) } while(SystemClock.elapsedRealtime()<deadline)
            return null
        }
        fun rect(node:AccessibilityNodeInfo)=Rect().also { node.getBoundsInScreen(it) }
        fun touch(action:Int,x:Float,y:Float,downTime:Long) {
            val e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0)
            e.source=InputDevice.SOURCE_TOUCHSCREEN; assertTrue(ui.injectInputEvent(e,true)); e.recycle()
        }
        i.runOnMainSync { service!!.showFloating() }
        val ball=waitFor("打开聊天军师悬浮窗"); assertNotNull("微信前台必须显示图标",ball)
        if(find("聊天候选面板")==null) {
            val b=rect(ball!!); val time=SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN,b.exactCenterX(),b.exactCenterY(),time)
            touch(MotionEvent.ACTION_UP,b.exactCenterX(),b.exactCenterY(),time)
        }
        val panel=waitFor("聊天候选面板"); assertNotNull(panel)
        val beforeBall=rect(find("打开聊天军师悬浮窗")!!); val beforePanel=rect(panel!!)
        assertEquals(beforeBall.top,beforePanel.top)
        assertTrue(beforeBall.left==beforePanel.right || beforeBall.right==beforePanel.left)
        val emptyColumnX=beforeBall.centerX(); val emptyColumnY=beforeBall.bottom+20
        ui.windows.filter { it.title?.toString() in setOf("LoveAdvisorBubble","LoveAdvisorPanel") }.forEach { w ->
            val touchBounds=Rect(); w.getBoundsInScreen(touchBounds)
            assertFalse("图标下方空白区域不得被悬浮窗口覆盖",touchBounds.contains(emptyColumnX,emptyColumnY))
        }
        val time=SystemClock.uptimeMillis(); val dx=20f; val dy=60f
        touch(MotionEvent.ACTION_DOWN,beforeBall.exactCenterX(),beforeBall.exactCenterY(),time)
        touch(MotionEvent.ACTION_MOVE,beforeBall.exactCenterX()+dx,beforeBall.exactCenterY()+dy,time)
        touch(MotionEvent.ACTION_UP,beforeBall.exactCenterX()+dx,beforeBall.exactCenterY()+dy,time)
        SystemClock.sleep(300)
        val afterBall=rect(find("打开聊天军师悬浮窗")!!); val afterPanel=rect(find("聊天候选面板")!!)
        assertNotEquals(beforeBall.top,afterBall.top)
        assertEquals(afterBall.top,afterPanel.top)
        assertEquals(afterBall.top-beforeBall.top,afterPanel.top-beforePanel.top)
        assertEquals(afterBall.left-beforeBall.left,afterPanel.left-beforePanel.left)
        assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        val hiddenUntil=SystemClock.elapsedRealtime()+5000
        while(find("打开聊天军师悬浮窗")!=null && SystemClock.elapsedRealtime()<hiddenUntil) SystemClock.sleep(100)
        assertNull("微信后台必须隐藏图标",find("打开聊天军师悬浮窗")); assertNull(find("聊天候选面板"))
        val launch=i.targetContext.packageManager.getLaunchIntentForPackage("com.tencent.mm")
        assertNotNull(launch); i.targetContext.startActivity(launch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertNotNull("返回微信后恢复图标",waitFor("打开聊天军师悬浮窗"))
        assertNull("返回微信时不能主动弹出候选窗",find("聊天候选面板"))
        val restoredBall=rect(find("打开聊天军师悬浮窗")!!); val reopenTime=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,restoredBall.exactCenterX(),restoredBall.exactCenterY(),reopenTime)
        touch(MotionEvent.ACTION_UP,restoredBall.exactCenterX(),restoredBall.exactCenterY(),reopenTime)
        assertNotNull("点击图标后展开候选窗",waitFor("聊天候选面板"))
        assertNotNull("面板提供手动读取信息按钮",find("读取信息"))
        val close=find("收起候选窗"); assertNotNull(close); assertTrue(close!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val closeUntil=SystemClock.elapsedRealtime()+3000
        while(find("聊天候选面板")!=null && SystemClock.elapsedRealtime()<closeUntil) SystemClock.sleep(100)
        assertNull(find("聊天候选面板")); assertNotNull(find("打开聊天军师悬浮窗"))
    }
}
