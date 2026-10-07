package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
data class WindowSnapshot(val packageName:String,val windowId:Int,val viewport:RectI,val nodes:List<NodeRecord>,val confirmedIdentity:ConversationIdentity?=null,val windowClassName:String?=null)
data class ConversationDraft(val packageName:String,val windowId:Int,val title:String?,val kind:ChatKind,val messages:List<ChatMessage>,val speakerMappingConfirmed:Boolean,val source:CaptureSource=CaptureSource.NODES)
sealed class CaptureResult {
    data object NotChat:CaptureResult()
    data class Readable(val snapshot:ChatSnapshot):CaptureResult()
    data class NeedsPreview(val draft:ConversationDraft):CaptureResult()
    data class NeedsImage(val reason:String):CaptureResult()
}
data class OcrTextBox(val text:String,val bounds:RectI,val bubble:RectI?=null,val sender:String?=null)
data class CaptureGeometry(val viewport:RectI,val inputTop:Int?=null,val packageName:String="com.tencent.mm",val windowId:Int=0)
