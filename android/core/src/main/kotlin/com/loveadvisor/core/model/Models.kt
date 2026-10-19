package com.loveadvisor.core.model

data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
}
data class NodeRecord(val id: String?, val text: String?, val className: String, val editable: Boolean, val bounds: RectI, val containerBounds:RectI?=null, val parentKey:String?=null, val roleHint:String?=null,val contentDescription:String?=null,val key:String?=null)
enum class Speaker { ME, OTHER, UNKNOWN }
enum class ChatKind { DIRECT, GROUP, UNKNOWN }
enum class CaptureSource { NODES, OCR, PASTE, IMPORT }
/** Quoted history is context attached to a message, never a fresh speaker turn. */
data class ChatQuote(val text:String,val author:String?)
data class ChatMessage(val id: String, val speaker: Speaker, val sender: String?, val text: String,val quote:ChatQuote?=null)
data class ConversationIdentity(val id: String, val displayTitle: String?, val kind: ChatKind, val confirmed: Boolean)
data class ChatSnapshot(val packageName: String, val windowId: Int, val identity: ConversationIdentity?, val messages: List<ChatMessage>, val contentRevision: Long = 0, val source: CaptureSource = CaptureSource.NODES,val replyStyleTitle:String?=null)
data class SessionToken(val packageName: String, val windowId: Int, val conversationId: String, val revision: Long, val epoch: Long)
data class RequestTicket(val session: SessionToken, val generation: Long)
