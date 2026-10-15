package com.loveadvisor.android.overlay

import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TopCapsuleViewTest {
    @Test fun lockedViewKeepsTapAndContentWidthDoesNotReserveAnEmptyTail() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            var taps=0;var moves=0
            val view=TopCapsuleView(context,{taps++},{},{_,_->moves++},{},{})
            view.setStatus("6条候选");view.applyOptions(true,1f)
            val regular=view.contentWidthDp();assertTrue(regular<64f)
            view.measure(View.MeasureSpec.makeMeasureSpec((regular*context.resources.displayMetrics.density).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(66,View.MeasureSpec.EXACTLY));view.layout(0,0,192,66)
            fun e(action:Int,t:Long,x:Float) { val event=MotionEvent.obtain(0,t,action,x,20f,0);view.dispatchTouchEvent(event);event.recycle() }
            e(MotionEvent.ACTION_DOWN,0,10f);e(MotionEvent.ACTION_UP,60,10f)
            e(MotionEvent.ACTION_DOWN,1000,10f);e(MotionEvent.ACTION_MOVE,1700,80f);e(MotionEvent.ACTION_UP,1800,80f)
            assertEquals(1,taps);assertEquals(0,moves)
            view.applyOptions(false,2f);assertEquals(regular,view.contentWidthDp(),.01f)
            assertTrue(view.contentDescription.toString().contains("长按"));view.release()
        }
    }
    @Test fun tapAndHeldDragHaveDifferentActionsOnTheActualView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            var taps=0;var moves=0;var commits=0
            val view=TopCapsuleView(context,{taps++},{},{_,_->moves++},{commits++},{})
            view.measure(View.MeasureSpec.makeMeasureSpec(192,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(66,View.MeasureSpec.EXACTLY));view.layout(0,0,192,66)
            fun event(action:Int,time:Long,x:Float=20f) { val e=MotionEvent.obtain(0,time,action,x,20f,0);view.dispatchTouchEvent(e);e.recycle() }
            event(MotionEvent.ACTION_DOWN,0);event(MotionEvent.ACTION_UP,80);assertEquals(1,taps)
            event(MotionEvent.ACTION_DOWN,1000);event(MotionEvent.ACTION_MOVE,1600,35f);event(MotionEvent.ACTION_UP,1650,35f)
            assertEquals(1,taps);assertTrue(moves>0);assertEquals(1,commits)
            view.setStatus("6条候选");assertTrue(view.contentDescription.toString().contains("6条候选"))
            view.release()
        }
    }
}
