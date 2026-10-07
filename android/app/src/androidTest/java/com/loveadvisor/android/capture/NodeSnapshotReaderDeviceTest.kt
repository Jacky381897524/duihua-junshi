package com.loveadvisor.android.capture
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeSnapshotReaderDeviceTest {
    @Test fun invisibleZeroSizedRootUsesApplicationFrameAndStillReachesVision() {
        @Suppress("DEPRECATION") val root=AccessibilityNodeInfo.obtain()
        root.packageName="com.tencent.mm"; root.className="android.widget.FrameLayout"
        root.isVisibleToUser=false; root.setBoundsInScreen(Rect(0,0,0,0))
        val frame=RectI(0,80,1080,2280)
        val snapshot=NodeSnapshotReader.snapshotRoot(root,windowBounds=frame).window
        assertEquals(frame,snapshot.viewport)
        assertEquals(1,snapshot.nodes.size)
        assertTrue(AutomaticWeChatParser().extract(snapshot) is CaptureResult.NeedsImage)
    }
}
