package com.loveadvisor.android.overlay
import android.content.Context
import android.widget.*
import com.loveadvisor.core.analysis.*
import com.loveadvisor.core.advisor.ConversationLevel
import com.loveadvisor.core.model.ChatMessage
class MessageJudgmentView(context:Context,private val message:ChatMessage,private val onBack:()->Unit,private val onReply:(()->Unit)?):LinearLayout(context) {
    private val palette=PanelPalette(context)
    init { orientation=VERTICAL;show(null,"正在判断这句话…") }
    fun show(result:MessageJudgment?,error:String?,retry:(()->Unit)?=null) {
        removeAllViews()
        fun line(title:String,text:String,size:Float=13f):TextView {
            addView(palette.text(title,12f,palette.blue,true),LayoutParams(-1,-2).apply { topMargin=14;bottomMargin=6 })
            return palette.text(text,size).apply { setTextIsSelectable(true);addView(this) }
        }
        line("原话${message.sender?.let { " · $it" }.orEmpty()}",message.text,16f)
        if(result!=null) {
            line("字面意思还是话里有话",result.literal?.let { "字面 ${percent(it)} · 有言外之意 ${percent(1-it)}" } ?: "模型未提供权重")
            fun choices(title:String,items:List<DecisionOption>) { line(title,if(items.isEmpty())"模型未提供判断" else items.joinToString("\n") { "• ${it.label} · ${it.weight?.let(::percent) ?: "未提供权重"}" }) }
            choices("可能意图",result.intents);choices("对方可能需要",result.needs);choices("建议动作",result.actions)
            val level=ConversationLevel.from(result.level);line("等级",level.label+level.value?.let { " $it/9" }.orEmpty()).setTextColor(palette.levelColor(level))
            line("参考原文",result.evidence.joinToString("\n") { "${if(it.speaker==com.loveadvisor.core.model.Speaker.ME)"我" else it.sender ?: "对方"}：${it.text}" })
            line("来源",result.source+"\n以上权重为模型估计，不是统计准确率。",11f)
            onReply?.let { addView(palette.actionButton("围绕这句给我六条回复",true,action=it),LayoutParams(-1,-2).apply { topMargin=16 }) }
        } else line("判断状态",error ?: "尚未完成")
        if(retry!=null)addView(palette.actionButton("重试判断",action=retry))
        addView(palette.actionButton("返回消息列表",heightDp=44,action=onBack),LayoutParams(-1,-2).apply { topMargin=14 })
    }
    private fun percent(v:Double)="${(v*1000).toInt()/10.0}%"
}
