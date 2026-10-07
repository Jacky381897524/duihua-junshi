package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
object PreviewBindingPolicy {
    fun canBind(confirmed:ChatSnapshot,current:WindowSnapshot,parsed:ConversationDraft?):Boolean {
        if(confirmed.source!=CaptureSource.NODES || confirmed.identity?.confirmed!=true || parsed==null) return false
        if(current.packageName!=confirmed.packageName || current.windowId!=confirmed.windowId || parsed.packageName!=current.packageName || parsed.windowId!=current.windowId) return false
        return parsed.title==confirmed.identity.displayTitle && parsed.kind==confirmed.identity.kind &&
            parsed.messages.map { Triple(it.speaker,it.sender,it.text) }==confirmed.messages.map { Triple(it.speaker,it.sender,it.text) }
    }
    fun independent(confirmed:ChatSnapshot,appPackage:String)=confirmed.copy(packageName=appPackage,windowId=-1,source=CaptureSource.IMPORT)
}
