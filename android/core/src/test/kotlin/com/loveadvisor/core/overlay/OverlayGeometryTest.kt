package com.loveadvisor.core.overlay
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
class OverlayGeometryTest {
    @Test fun keyboardLimitAlsoKeepsChatComposerVisible() {
        val panel=OverlayGeometry.measure(RectI(0,0,1080,2344),InsetsI(top=106,bottom=54),RectI(0,1574,1080,2344),2.625f)
        assertTrue("键盘上方的聊天输入栏也必须露出",panel.bottomPx<=1574-(72*2.625f).toInt())
    }
    @Test fun dimensionsFollowReferenceAndStayAboveKeyboard() {
        val b=OverlayGeometry.measure(RectI(0,0,1080,2400),InsetsI(top=80,bottom=80),null,3f)
        assertTrue(b.widthPx in 900..960); assertEquals(1422,b.heightPx)
        val k=OverlayGeometry.measure(RectI(0,0,1080,2400),InsetsI(top=80,bottom=80),RectI(0,1400,1080,2400),3f)
        assertTrue(k.bottomPx<=1400); assertTrue(k.heightPx>0)
    }
    @Test fun rightBubbleAndPanelShareTopAndTouchEdges() {
        val d=OverlayGeometry.dock(PanelBounds(950,300,120,120),PanelBounds(0,0,780,900),RectI(0,80,1080,2300))
        assertEquals(d.bubble.topPx,d.panel.topPx)
        assertEquals(d.bubble.leftPx,d.panel.leftPx+d.panel.widthPx)
        assertEquals(900,d.window.widthPx)
        assertEquals(900,d.window.heightPx)
    }
    @Test fun leftBubbleOpensPanelOnItsRightAndBothMoveTogether() {
        val a=OverlayGeometry.dock(PanelBounds(10,300,120,120),PanelBounds(0,0,780,900),RectI(0,80,1080,2300))
        val b=OverlayGeometry.dock(PanelBounds(50,500,120,120),PanelBounds(0,0,780,900),RectI(0,80,1080,2300))
        assertEquals(a.bubble.leftPx+a.bubble.widthPx,a.panel.leftPx)
        assertEquals(40,b.panel.leftPx-a.panel.leftPx)
        assertEquals(200,b.panel.topPx-a.panel.topPx)
        assertEquals(b.bubble.topPx,b.panel.topPx)
    }
    @Test fun wholeDockedGroupStaysInsideScreenNearKeyboardAndEdges() {
        val screen=RectI(0,80,1080,1400)
        val d=OverlayGeometry.dock(PanelBounds(2000,2000,120,120),PanelBounds(0,0,780,900),screen)
        assertTrue(d.window.leftPx>=screen.left)
        assertTrue(d.window.leftPx+d.window.widthPx<=screen.right)
        assertTrue(d.window.topPx>=screen.top)
        assertTrue(d.window.bottomPx<=screen.bottom)
        assertEquals(d.bubble.topPx,d.panel.topPx)
    }
    @Test fun smallScreensAndDraggingAreClamped() {
        val b=OverlayGeometry.measure(RectI(0,0,400,300),InsetsI(top=20,bottom=20),null,1f)
        assertTrue(b.widthPx>0); assertTrue(b.bottomPx<=280)
        val moved=OverlayGeometry.clamp(b.copy(leftPx=2000,topPx=-500),RectI(0,20,400,280))
        assertTrue(moved.leftPx+moved.widthPx<=400); assertTrue(moved.topPx>=20)
    }
    @Test fun referencePhoneHasRoomForThreeCardsWithoutCoveringEntireChat() {
        val screen=RectI(0,30,390,790)
        val panel=OverlayGeometry.measure(screen,InsetsI(),null,1f)
        assertEquals(474,panel.heightPx)
        val dock=OverlayGeometry.dock(PanelBounds(342,160,40,40),panel,screen)
        assertEquals(40,dock.bubble.widthPx)
        assertEquals(dock.panel.topPx,dock.bubble.topPx)
        assertEquals(dock.panel.leftPx+dock.panel.widthPx,dock.bubble.leftPx)
        assertTrue(dock.window.bottomPx<screen.bottom)
    }
}
