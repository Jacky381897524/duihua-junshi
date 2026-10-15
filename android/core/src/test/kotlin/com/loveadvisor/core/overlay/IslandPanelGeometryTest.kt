package com.loveadvisor.core.overlay
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class IslandPanelGeometryTest {
    @Test fun everyOpeningResetsTheDraggedPositionBelowTheIsland() {
        val available=RectI(0,30,390,790)
        val expected=PanelBounds(35,42,320,400)
        assertEquals(expected,IslandPanelGeometry.open(PanelBounds(0,0,320,400),available,12))
        assertEquals(expected,IslandPanelGeometry.open(PanelBounds(60,200,320,400),available,12))
    }
    @Test fun keyboardSafeBottomBoundsTheEntirePanel() {
        val actual=IslandPanelGeometry.open(PanelBounds(0,0,320,600),RectI(0,30,390,490),12)
        assertEquals(42,actual.topPx);assertTrue(actual.bottomPx<=490)
    }
    @Test fun tinyAndRotatedAreasAlwaysHavePositiveClampedDimensions() {
        assertEquals(PanelBounds(20,30,1,1),IslandPanelGeometry.open(PanelBounds(0,0,320,400),RectI(20,30,21,31),12))
        val result=IslandPanelGeometry.open(PanelBounds(999,999,320,400),RectI(48,30,750,300),12)
        assertEquals(239,result.leftPx);assertEquals(300,result.bottomPx)
    }
}
