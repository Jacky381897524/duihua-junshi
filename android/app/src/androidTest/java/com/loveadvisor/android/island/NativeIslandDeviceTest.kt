package com.loveadvisor.android.island

import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import org.junit.Assert.*
import org.junit.Test

class NativeIslandDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun ordinaryNotificationCannotUseTheNativeClickCapability() {
        val sender=NativeIslandNotification(context)
        val notification=sender.build("待读取",IslandLease("test-one","com.tencent.mm",42,1),0)
        val native=notification.extras.getParcelable<android.app.PendingIntent>("notification.superx.clickResp")
        assertNotNull(native)
        assertNotEquals(notification.contentIntent,native)
        assertEquals("待读取",notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT))
        assertFalse(context.packageManager.getReceiverInfo(ComponentName(context,NativeIslandReceiver::class.java),0).exported)
    }
    @Test fun leaseReplacementRevokesTheOldPendingIntent() {
        val sender=NativeIslandNotification(context)
        val a=sender.build("待读取",IslandLease("old","com.tencent.mm",42,1),0).extras.getParcelable<android.app.PendingIntent>("notification.superx.clickResp")!!
        sender.build("待读取",IslandLease("new","com.tencent.mm",42,2),0)
        assertThrows(android.app.PendingIntent.CanceledException::class.java) { a.send() }
        sender.end()
    }
    @Test fun disconnectedServiceCannotAcceptAnyNativeTap() {
        var result:Boolean?=null
        ChatCaptureService().onNativeIslandTap("expired") { result=it }
        assertEquals(false,result)
    }
}
