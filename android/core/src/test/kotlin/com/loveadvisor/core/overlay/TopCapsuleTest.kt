package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class TopCapsuleTest {
    private val display=RectI(0,0,390,844)
    @Test fun defaultIsBesideTheClockAndNotAtTheCenterCamera() {
        val cutout=RectI(176,0,214,30)
        val placed=TopCapsuleGeometry.place(display,30,1f,listOf(cutout))
        assertEquals(PanelBounds(56,4,64,22),placed)
        assertTrue(placed.leftPx>=56);assertTrue(placed.leftPx+placed.widthPx<cutout.left)
    }
    @Test fun savedOffsetsAreAppliedWithoutMovingIntoTheClockOrCutout() {
        val placed=TopCapsuleGeometry.place(display,30,1f,listOf(RectI(176,0,214,30)),500f,-100f)
        assertTrue(placed.leftPx>=56);assertTrue(placed.leftPx+placed.widthPx<=168);assertEquals(0,placed.topPx)
        assertEquals(PanelBounds(66,9,64,22),TopCapsuleGeometry.place(display,30,1f,emptyList(),10f,5f))
    }
    @Test fun aNarrowStatusBarFallsBelowItInsteadOfCoveringTheTime() {
        val placed=TopCapsuleGeometry.place(RectI(0,0,220,500),24,1f,listOf(RectI(95,0,125,24)))
        assertTrue(placed.topPx>=24);assertTrue(placed.leftPx+placed.widthPx<=220)
        val tiny=TopCapsuleGeometry.place(RectI(0,0,12,10),20,3f,emptyList())
        assertTrue(tiny.widthPx>0 && tiny.heightPx>0 && tiny.bottomPx<=10)
    }
    @Test fun densityAndKeyboardBoundsDoNotPutTheCapsuleOffScreen() {
        val placed=TopCapsuleGeometry.place(RectI(0,0,1080,1200),90,3f,listOf(RectI(500,0,580,90)),0f,900f)
        assertEquals(192,placed.widthPx);assertTrue(placed.bottomPx<=1200)
        assertTrue(placed.leftPx+placed.widthPx<=1080)
    }
    @Test fun draggingAfterRotationStartsAtTheVisiblePosition() {
        val rotated=RectI(0,0,844,390)
        val visible=TopCapsuleGeometry.place(rotated,30,1f,emptyList(),0f,600f)
        val base=TopCapsuleGeometry.place(rotated,30,1f,emptyList())
        val start=TopCapsuleGeometry.offsetOf(visible,base,1f)
        val moved=TopCapsuleGeometry.place(rotated,30,1f,emptyList(),start.x,start.y-20f)
        assertEquals(368,visible.topPx)
        assertEquals(visible.topPx-20,moved.topPx)
    }
    @Test fun draggingAfterKeyboardClampUsesTheVisiblePositionAtDeviceDensity() {
        val keyboardArea=RectI(0,0,1080,1200)
        val visible=TopCapsuleGeometry.place(keyboardArea,90,3f,emptyList(),0f,900f)
        val base=TopCapsuleGeometry.place(keyboardArea,90,3f,emptyList())
        val start=TopCapsuleGeometry.offsetOf(visible,base,3f)
        val moved=TopCapsuleGeometry.place(keyboardArea,90,3f,emptyList(),start.x,start.y-12f)
        assertEquals(visible.topPx-36,moved.topPx)
    }
    @Test fun quickTapClicksWithoutMovingTheCapsule() {
        val gesture=CapsuleDragGesture(500,8f)
        gesture.down(10f,20f,0)
        assertNull(gesture.move(12f,22f,80))
        assertEquals(CapsuleTouchAction.CLICK,gesture.finish(100))
    }
    @Test fun movingBeforeLongPressCancelsBothClickAndDrag() {
        val gesture=CapsuleDragGesture(500,8f)
        gesture.down(0f,0f,0)
        assertNull(gesture.move(20f,0f,100));assertFalse(gesture.hold(500))
        assertNull(gesture.move(40f,30f,600));assertEquals(CapsuleTouchAction.NONE,gesture.finish(700))
    }
    @Test fun heldDragReportsTotalDeltaAndDoesNotClickOnRelease() {
        val gesture=CapsuleDragGesture(500,8f)
        gesture.down(10f,20f,0);assertTrue(gesture.hold(500))
        assertEquals(CapsuleDelta(25f,-5f),gesture.move(35f,15f,550))
        assertEquals(CapsuleTouchAction.DRAG_END,gesture.finish(600))
        assertEquals(CapsuleTouchAction.NONE,gesture.finish(650))
    }
    @Test fun cancellationAndAnUnmovedLongPressNeverOpenThePanel() {
        val gesture=CapsuleDragGesture(500,8f)
        gesture.down(0f,0f,0);assertEquals(CapsuleTouchAction.DRAG_END,gesture.finish(600))
        gesture.down(0f,0f,1000);gesture.hold(1500);gesture.cancel()
        assertNull(gesture.move(20f,0f,1600));assertEquals(CapsuleTouchAction.NONE,gesture.finish(1700))
    }
    @Test fun lockedCapsuleStillClicksButHeldMovementNeverMovesIt() {
        val gesture=CapsuleDragGesture(500,8f)
        gesture.setLocked(true)
        gesture.down(0f,0f,0);assertEquals(CapsuleTouchAction.CLICK,gesture.finish(100))
        gesture.down(0f,0f,1000);assertFalse(gesture.hold(1500))
        assertNull(gesture.move(30f,40f,1600));assertEquals(CapsuleTouchAction.NONE,gesture.finish(1700))
        gesture.setLocked(false);gesture.down(0f,0f,2000);assertTrue(gesture.hold(2500));assertNotNull(gesture.move(20f,0f,2600))
    }
    @Test fun contentWidthAndScaleUseTightBoundsAndRemainOnScreen() {
        val small=TopCapsuleGeometry.place(display,30,1f,emptyList(),scale=.7f,contentWidthDp=50f)
        val large=TopCapsuleGeometry.place(display,30,1f,emptyList(),scale=2f,contentWidthDp=50f)
        assertEquals(35,small.widthPx);assertEquals(15,small.heightPx)
        assertEquals(100,large.widthPx);assertEquals(44,large.heightPx);assertTrue(large.topPx>=30)
        assertTrue(large.leftPx+large.widthPx<=390)
    }
}
