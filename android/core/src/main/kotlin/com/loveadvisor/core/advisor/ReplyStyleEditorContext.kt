package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.ChatKind
import com.loveadvisor.core.model.ChatSnapshot

/** A fresh nickname can reuse cached chat kind only for the same platform and title. */
data class ReplyStyleEditorContext(val title:String,val kind:ChatKind?) {
    companion object {
        fun resolve(snapshot:ChatSnapshot?,packageName:String,title:String?):ReplyStyleEditorContext {
            val matching=snapshot?.takeIf {
                it.packageName==packageName && !title.isNullOrBlank() && it.identity?.confirmed==true &&
                    it.identity.displayTitle==title && it.identity.kind in setOf(ChatKind.DIRECT,ChatKind.GROUP)
            }
            return ReplyStyleEditorContext(matching?.replyStyleTitle ?: title.orEmpty(),matching?.identity?.kind)
        }
    }
}
