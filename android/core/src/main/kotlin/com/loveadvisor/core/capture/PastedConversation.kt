package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import java.util.UUID

object PastedConversation {
    /** With no verified chat, a local draft session is copy-only; never invent a remote recipient. */
    fun create(text:String,localPackage:String,verified:ChatSnapshot?=null,now:Long=System.currentTimeMillis()):ChatSnapshot {
        val value=text.trim();require(value.isNotEmpty() && value.length<=4000) { "请粘贴1至4000字的单句内容" }
        val id="paste-"+UUID.randomUUID()
        val base=verified?.takeIf { ChatApps.supported(it.packageName) && it.identity?.confirmed==true && it.identity.kind==ChatKind.DIRECT }
        return ChatSnapshot(base?.packageName ?: localPackage,base?.windowId ?: -1,base?.identity ?: ConversationIdentity(id,"复制单句",ChatKind.DIRECT,true),listOf(ChatMessage(id,Speaker.OTHER,null,value,observedAtMillis=now)),source=CaptureSource.PASTE,readAtMillis=now)
    }
}
