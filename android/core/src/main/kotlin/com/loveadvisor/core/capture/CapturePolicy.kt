package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
object CapturePolicy {
    fun pauseForHistory(result:CaptureResult,browsingHistory:Boolean,forced:Boolean)=browsingHistory && !forced && (result is CaptureResult.Readable || result is CaptureResult.NeedsImage)
    fun overlayVisible(enabled:Boolean,locked:Boolean,projecting:Boolean)=enabled && !locked && !projecting
    fun overlayVisible(enabled:Boolean,locked:Boolean,projecting:Boolean,wechatForeground:Boolean)=wechatForeground && overlayVisible(enabled,locked,projecting)
    fun viewport(windowBounds:RectI,rootBounds:RectI)=windowBounds.takeIf { it.width>0 && it.height>0 } ?: rootBounds
    val supportedPackages=ChatApps.packages+setOf("com.twitter.android","com.ss.android.lark")
    fun invalidateForDisabledCapture(token:SessionToken?)=token?.packageName in supportedPackages
    fun invalidateForForeground(token:SessionToken?,packageName:String?)=token?.packageName in supportedPackages && packageName!=token?.packageName
    fun retainOcr(snapshot:ChatSnapshot?,window:WindowSnapshot,identity:ConversationIdentity?)=snapshot?.source==CaptureSource.OCR && identity?.confirmed==true && snapshot.identity?.id==identity.id && snapshot.packageName==window.packageName && snapshot.windowId==window.windowId
}
