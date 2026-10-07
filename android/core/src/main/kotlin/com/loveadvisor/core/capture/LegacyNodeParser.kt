package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
class LegacyNodeParser {
    fun extract(window:WindowSnapshot):CaptureResult {
        val pkg=window.packageName; val v=window.viewport; val nodes=window.nodes.take(5000)
        val input=nodes.firstOrNull { it.editable && it.bounds.top>v.top+v.height*.55 }
        val title=nodes.firstOrNull { it.id?.endsWith("/371")==true || it.id?.endsWith("/group_name")==true }?.text ?: TextSceneParser.title(nodes,v)
        val messages=when(pkg) {
            "com.tencent.mobileqq" -> {
                val bodies=nodes.filter { it.id?.endsWith("/mjn")==true && !it.text.isNullOrBlank() }.sortedBy { it.bounds.top }
                if(input==null && bodies.isEmpty()) return CaptureResult.NotChat
                bodies.mapIndexed { index,n -> val sender=nodes.filter { it.id?.endsWith("/mjq")==true && it.bounds.bottom<=n.bounds.top && n.bounds.top-it.bounds.bottom<v.height*.06 }.maxByOrNull { it.bounds.top }?.text; ChatMessage("qq-$index",TextSceneParser.speaker(n.bounds,v),sender,n.text!!) }
            }
            "com.twitter.android" -> {
                if(input==null || nodes.any { it.contentDescription in setOf("新私信","New message") }) return CaptureResult.NotChat
                nodes.filter { it.className=="android.view.View" && it.bounds.width>=v.width*.95 && !it.contentDescription.orEmpty().contains(", @") }.sortedBy { it.bounds.top }.mapNotNull { n ->
                    val pair=parseX(n.contentDescription.orEmpty()) ?: return@mapNotNull null
                    ChatMessage("x-${n.bounds.top}",if(pair.first in setOf("你","You","you")) Speaker.ME else Speaker.OTHER,pair.first,pair.second)
                }
            }
            "com.ss.android.lark" -> {
                val bubbles=nodes.filter { it.id?.endsWith("/bubble_content_container")==true }
                if(bubbles.isEmpty() && nodes.none { it.id?.endsWith("/kb_rich_text_content")==true || it.id?.endsWith("/message")==true }) return CaptureResult.NotChat
                val chrome=listOf("/group_name","/name_tv","/date_tv","/system_label","/kb_rich_text_content","/thread_title_tv","/thread_subtitle_tv")
                nodes.filter { it.className.contains("TextView") && !it.text.isNullOrBlank() && !it.editable && chrome.none { suffix -> it.id.orEmpty().endsWith(suffix) } && !TextSceneParser.isUi(it.text) && it.bounds.top>v.top+v.height*.14 && it.bounds.bottom<(input?.bounds?.top ?: (v.top+v.height*.84).toInt()) }.sortedBy { it.bounds.top }.mapIndexed { index,n ->
                    val bubble=bubbles.firstOrNull { it.bounds.left<=n.bounds.left && it.bounds.right>=n.bounds.right && it.bounds.top<=n.bounds.top && it.bounds.bottom>=n.bounds.bottom }
                    val mine=bubble?.key?.let { key -> nodes.any { it.id?.endsWith("time_read_state_container_align_bubble")==true && it.parentKey.orEmpty().startsWith(key) } }
                    ChatMessage("lark-$index",when(mine) { true -> Speaker.ME; false -> Speaker.OTHER; null -> Speaker.UNKNOWN },null,n.text!!)
                }
            }
            else -> return CaptureResult.NotChat
        }
        if(messages.isEmpty()) return CaptureResult.NeedsImage("沿用的旧平台规则未读到正文，可手动 OCR；此平台需真机验证")
        val kind=if(messages.mapNotNull { it.sender }.distinct().size>1 || TextSceneParser.kind(title)==ChatKind.GROUP) ChatKind.GROUP else ChatKind.DIRECT
        val identity=window.confirmedIdentity?.takeIf { it.confirmed && it.displayTitle==title && messages.none { m -> m.speaker==Speaker.UNKNOWN } }
        return if(identity!=null) CaptureResult.Readable(ChatSnapshot(pkg,window.windowId,identity,messages)) else CaptureResult.NeedsPreview(ConversationDraft(pkg,window.windowId,title,kind,messages,messages.none { it.speaker==Speaker.UNKNOWN }))
    }
    private fun parseX(desc:String):Pair<String,String>? {
        val match=Regex("^([^：:]+)(?:：|: )(.+)$").matchEntire(desc) ?: return null
        var text=match.groupValues[2].trim()
        for(tail in listOf("Read。","Read","已读。","已读")) if(text.endsWith(tail)) { text=text.removeSuffix(tail).trim(); break }
        text=text.trimEnd('。'); text=Regex("\\d{1,2}[:：]\\d{2}\\s*(上午|下午|AM|PM|am|pm)?$").replace(text,"").trim().trimEnd('。')
        return if(text.isBlank()) null else match.groupValues[1].trim() to text
    }
}
