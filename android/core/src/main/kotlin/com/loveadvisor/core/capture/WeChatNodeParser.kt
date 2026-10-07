package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
class WeChatNodeParser {
    fun extract(window:WindowSnapshot):CaptureResult {
        if(window.packageName!="com.tencent.mm" || window.viewport.height==0) return CaptureResult.NotChat
        val v=window.viewport; val nodes=window.nodes.take(5000)
        val input=nodes.filter { it.editable && it.bounds.top>=v.top+v.height*.55 && !it.text.orEmpty().contains("搜索") }.maxByOrNull { it.bounds.top }
        val legacy=nodes.any { it.id=="com.tencent.mm:id/bkl" }
        if(input==null && !legacy) return CaptureResult.NotChat
        val bottom=input?.bounds?.top ?: (v.bottom-v.height*.13).toInt()
        val title=TextSceneParser.title(nodes,v); val kind=TextSceneParser.kind(title)
        val bodies=nodes.filter { !it.editable && it.roleHint !in setOf("sender","announcement","system") && !TextSceneParser.isUi(it.text.orEmpty()) && it.className.contains("Text") && it.bounds.top>=v.top+v.height*.17 && it.bounds.bottom<=bottom && it.bounds.width<v.width*.92 }.distinctBy { Triple(it.text,it.bounds.left,it.bounds.top) }.sortedBy { it.bounds.top }
        if(bodies.isEmpty()) return CaptureResult.NeedsImage("聊天正文未暴露，可手动截图识别")
        val messages=bodies.mapIndexed { index,n ->
            val sender=if(kind==ChatKind.GROUP) nodes.filter { it.roleHint=="sender" && it.bounds.bottom<=n.bounds.top && n.bounds.top-it.bounds.bottom<v.height*.055 && (n.parentKey==null || it.parentKey==n.parentKey) }.maxByOrNull { it.bounds.top }?.text else null
            ChatMessage("node-$index",TextSceneParser.speaker(n.containerBounds ?: n.bounds,v),sender,n.text!!.trim())
        }
        val draft=ConversationDraft(window.packageName,window.windowId,title,kind,messages,messages.none { it.speaker==Speaker.UNKNOWN })
        val identity=window.confirmedIdentity?.takeIf { it.confirmed && title!=null && it.displayTitle==title && draft.speakerMappingConfirmed }
        return if(identity!=null) CaptureResult.Readable(ChatSnapshot(window.packageName,window.windowId,identity,messages)) else CaptureResult.NeedsPreview(draft)
    }
}
