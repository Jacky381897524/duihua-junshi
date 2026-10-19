package com.loveadvisor.core.overlay
import org.junit.Assert.*
import org.junit.Test
class PanelHeaderLayoutTest {
    @Test fun actionsStaySmallAndLeaveMostWidthForIntent() {
        for(width in listOf(230,280,340,500)) {
            val layout=PanelHeaderLayout.measure(width,1f,1f)
            assertTrue(layout.summaryWidth>=width*.60)
            assertTrue(layout.actionWidth<=56)
            assertEquals(width,layout.summaryWidth+2*layout.actionWidth+2*layout.gap)
        }
    }
    @Test fun largeFontsUseShortActionLabelsWithoutTakingIntentWidth() {
        val layout=PanelHeaderLayout.measure(840,3f,1.8f)
        assertTrue(layout.shortLabels);assertTrue(layout.summaryWidth>=840*.60)
        assertTrue(layout.actionHeight>=28*3*1.8f)
    }
}
