package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import java.util.UUID
import kotlin.math.abs

/** Reads geometry and original text; uncertain layouts go to vision, never a confirmation screen. */
class AutomaticWeChatParser {
    fun extract(window:WindowSnapshot,readAtMillis:Long=System.currentTimeMillis()):CaptureResult {
        val v=window.viewport
        val page=WeChatPageClassifier.classify(window)
        if(page==WeChatPageKind.GROUP)return CaptureResult.NeedsImage("核对当前聊天类型与群成员归属")
        if(page!=WeChatPageKind.CHAT_OR_UNKNOWN) return CaptureResult.NotChat
        val nodes=WeChatPageClassifier.visibleNodes(window).take(5000)
        val title=TextSceneParser.title(nodes,v)?.trim()
        if(TextSceneParser.kind(title)==ChatKind.GROUP) return CaptureResult.NeedsImage("核对群成员与聊天标题")
        val input=nodes.filter { (it.editable || it.className.contains("EditText") || it.text.orEmpty().replace(" ","").trim()=="按住说话") && it.bounds.top>=v.top+v.height*.55 && !it.text.orEmpty().contains("搜索") }.maxByOrNull { it.bounds.top }
        if(input==null) {
            return CaptureResult.NeedsImage("输入区未完整暴露，自动用视觉判断单聊和双方")
        }
        if(title.isNullOrBlank()) return CaptureResult.NeedsImage("正在自动识别聊天标题和双方")
        val headerBottom=nodes.filter { it.text?.trim()==title && it.bounds.bottom<v.top+v.height*.17 }.minByOrNull { it.bounds.top }?.bounds?.bottom ?: (v.top+v.height*.12).toInt()
        val chatTop=headerBottom+v.height*.01
        if(nodes.any { it.roleHint=="sender" && it.bounds.top>=chatTop }) return CaptureResult.NeedsImage("原生布局提示不确定，自动用视觉判断单聊和双方")
        val bottom=input.bounds.top
        val avatars=nodes.filter { !it.editable && (it.roleHint=="avatar" || it.contentDescription.orEmpty().contains("头像")) }
        val bodies=nodes.filter {
            !it.editable && it.roleHint !in setOf("sender","announcement","system","avatar") &&
                !TextSceneParser.isUi(it.text.orEmpty()) && it.bounds.top>=chatTop &&
                it.bounds.bottom<=bottom && it.bounds.width>0 && it.bounds.width<v.width*.95
        }.distinctBy { Triple(it.text,it.bounds.left,it.bounds.top) }.sortedWith(compareBy<NodeRecord> { it.bounds.top }.thenBy { it.bounds.left })
        if(bodies.isEmpty()) return CaptureResult.NeedsImage("正在自动读取聊天文字")
        val messages=bodies.mapIndexed { index,node ->
            val aligned=avatars.filter { abs(it.bounds.top-node.bounds.top)<v.height*.035 }
            val sides=aligned.mapNotNull {
                when {
                    it.bounds.right<v.left+v.width*.2 -> Speaker.OTHER
                    it.bounds.left>v.right-v.width*.2 -> Speaker.ME
                    else -> null
                }
            }.distinct()
            val speaker=if(sides.size==1) sides.single() else if(sides.size>1) Speaker.UNKNOWN else TextSceneParser.speaker(node.containerBounds ?: node.bounds,v)
            ChatMessage("node-$index",speaker,null,node.text!!.trim(),time=MessageTimeParser.before(nodes,node.bounds,v,readAtMillis))
        }
        if(messages.any { it.speaker==Speaker.UNKNOWN }) return CaptureResult.NeedsImage("正在自动识别消息左右归属")
        val identity=window.confirmedIdentity?.takeIf { it.displayTitle==title && it.kind==ChatKind.DIRECT }
            ?: ConversationIdentity("wechat-${UUID.randomUUID()}",title,ChatKind.DIRECT,true)
        return CaptureResult.Readable(ChatSnapshot(window.packageName,window.windowId,identity,messages,readAtMillis=readAtMillis))
    }
}
