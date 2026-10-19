package com.loveadvisor.android.overlay
import android.content.Context
import android.widget.*
import com.loveadvisor.core.model.*
class MessageAnalysisListView(context:Context,snapshot:ChatSnapshot,onPick:(ChatMessage)->Unit,onBack:()->Unit,onAutomatic:(()->Unit)?=null):LinearLayout(context) {
    init {
        orientation=VERTICAL;val p=PanelPalette(context)
        fun add(v:android.view.View) { addView(v,LayoutParams(-1,-2).apply { bottomMargin=12 }) }
        add(p.text("判断分析",19f,bold=true));add(p.text("选择一条已读的对方消息，结合它之前的对话判断。模型推测不等于对方真实想法。",12f,p.muted))
        val rows=snapshot.messages.filter { it.speaker==Speaker.OTHER }
        rows.forEachIndexed { i,m ->
            val name=if(snapshot.identity?.kind==ChatKind.GROUP)"${m.sender ?: "成员（昵称未知）"} · " else ""
            val type=when(m.kind) { MessageKind.IMAGE->"图片";MessageKind.STICKER->"表情包";MessageKind.VOICE->"语音/屏幕转写";else->"文字" }
            add(p.actionButton("${i+1} · $name$type\n${m.text}${m.quote?.let { "\n前文引用：${it.text}" }.orEmpty()}") { onPick(m) })
        }
        if(rows.isEmpty())add(p.text("本次没有读到可判断的对方消息",13f))
        onAutomatic?.let { add(p.actionButton("返回群聊自动接话（共六条）",action=it)) }
        add(p.actionButton("返回候选面板",heightDp=44,action=onBack))
        post { (parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
