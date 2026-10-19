package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import com.loveadvisor.core.advisor.NicknameText
import kotlin.math.abs
object TextSceneParser {
    private val time=Regex("^(\\d{1,2}[:：]\\d{2}|(?:昨天|今天|星期[一二三四五六日天])(?:\\s*(?:上午|下午|晚上|凌晨|早上)?\\s*\\d{1,2}[:：]\\d{2})?|\\d{1,2}月\\d{1,2}日(?:\\s*\\d{1,2}[:：]\\d{2})?)$")
    fun isUi(text:String)=text.isBlank() || time.matches(text.trim()) || text.trim() in setOf("发送","按住 说话","已读","未读","语音","更多","返回","微信","搜索") || text.startsWith("群公告") || text.startsWith("以下为新消息")
    fun title(nodes:List<NodeRecord>,v:RectI):String?=nodes.filter {
        val t=it.text.orEmpty(); val r=it.bounds
        !it.editable && !isUi(t) && NicknameText.valid(t) && r.top>=v.top+v.height*.025 && r.bottom<v.top+v.height*.17 && (r.left+r.right)/2 in (v.left+v.width*.25).toInt()..(v.left+v.width*.75).toInt()
    }.sortedWith(compareByDescending<NodeRecord> { Regex("[（(]\\d+[）)]$").containsMatchIn(it.text.orEmpty()) }.thenBy { it.bounds.top }).firstOrNull()?.text
    fun kind(title:String?)=when { title==null -> ChatKind.UNKNOWN; Regex("[（(]\\d+[）)]$").containsMatchIn(title) -> ChatKind.GROUP; else -> ChatKind.DIRECT }
    fun speaker(r:RectI,v:RectI):Speaker {
        val dl=abs(r.left-(v.left+v.width*.13)); val dr=abs(r.right-(v.right-v.width*.13))
        if(abs(dl-dr)<v.width*.05 || r.width>v.width*.9) return Speaker.UNKNOWN
        return if(dl<dr) Speaker.OTHER else Speaker.ME
    }
}
