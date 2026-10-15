package com.loveadvisor.android.island
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loveadvisor.android.capture.ChatCaptureService
class NativeIslandReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=NativeIslandNotification.ACTION_OPEN) return
        val lease=intent.getStringExtra(NativeIslandNotification.EXTRA_LEASE) ?: return
        ChatCaptureService.instance?.onNativeIslandTap(lease)
    }
}
