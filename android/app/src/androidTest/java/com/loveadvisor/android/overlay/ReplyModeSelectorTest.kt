package com.loveadvisor.android.overlay

import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.storage.*
import com.loveadvisor.android.ui.*
import org.junit.Assert.*
import org.junit.Test

class ReplyModeSelectorTest {
    private fun all(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Test fun threeCompactChoicesUpdateBothFlagsAndRetainSavedRealisticSelection() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val context=test.targetContext
            val settings=AppSettings(AndroidDocumentStore(context,"mode-test-"+java.util.UUID.randomUUID()))
            settings.update { it.copy(realisticRepliesEnabled=true,naturalRepliesEnabled=true) }
            val view=ReplyModeSelector(context,settings,true)
            val labels=all(view).filterIsInstance<TextView>().filter { it.text in listOf("默认","袪味","真实") }
            assertEquals(3,labels.size);assertTrue(labels.single { it.text=="真实" }.isSelected)
            labels.single { it.text=="袪味" }.performClick()
            assertTrue(settings.load().naturalRepliesEnabled);assertFalse(settings.load().realisticRepliesEnabled)
            labels.single { it.text=="默认" }.performClick()
            assertFalse(settings.load().naturalRepliesEnabled);assertFalse(settings.load().realisticRepliesEnabled)
            val width=ViewStyles.dp(context,240f)
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.measuredHeight)
            assertTrue(view.measuredHeight<=ViewStyles.dp(context,88f))
            val track=view.getChildAt(0);val time=android.os.SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN to width*.15f,MotionEvent.ACTION_MOVE to width*.55f,MotionEvent.ACTION_MOVE to width*.9f,MotionEvent.ACTION_UP to width*.9f).forEachIndexed { index,pair ->
                val event=MotionEvent.obtain(time,time+index*30,pair.first,pair.second,track.height/2f,0);track.dispatchTouchEvent(event);event.recycle()
            }
            assertTrue(settings.load().realisticRepliesEnabled);assertFalse(settings.load().naturalRepliesEnabled)
        }
    }
}
