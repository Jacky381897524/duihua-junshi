package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
object GroupReplyStyle {
    fun resolve(snapshot:ChatSnapshot,privateCatalog:ReplyStyleCatalog,privateGlobal:String,groupCatalog:ReplyStyleCatalog,groupGlobal:String):String {
        val group=snapshot.identity?.kind==ChatKind.GROUP
        val catalog=if(group)groupCatalog else privateCatalog
        return catalog.match(snapshot.replyStyleTitle ?: snapshot.identity?.displayTitle,snapshot.packageName)?.style.orEmpty().ifBlank { if(group)groupGlobal else privateGlobal }.take(1500)
    }
}
