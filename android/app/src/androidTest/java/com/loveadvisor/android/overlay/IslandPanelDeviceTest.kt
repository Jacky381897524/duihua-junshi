package com.loveadvisor.android.overlay
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class IslandPanelDeviceTest {
    @Test fun onlyIslandPanelUsesTheDedicatedCenteredGrip() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            fun panel(mode:PanelDragMode)=AdvisorPanelView(context,{},{_,_->},{},{},{},{},{true},{true},dragMode=mode)
            val normal=panel(PanelDragMode.TITLE)
            assertEquals("军师分析",(normal.dragHandle as android.widget.TextView).text.toString())
            val island=panel(PanelDragMode.TOP_GRIP)
            assertEquals("拖动候选面板",island.dragHandle.contentDescription)
            assertNotEquals(normal.dragHandle.contentDescription,island.dragHandle.contentDescription)
            assertEquals(View.VISIBLE,island.dragHandle.visibility)
            val density=context.resources.displayMetrics.density
            assertTrue(island.dragHandle.layoutParams.width>=64*density)
            assertTrue(island.dragHandle.layoutParams.height>=24*density)
            normal.release();island.release()
        }
    }
}
