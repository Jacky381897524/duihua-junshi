package com.loveadvisor.android.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.analysis.*
import com.loveadvisor.core.advisor.ConversationLevel
import com.loveadvisor.core.model.*

/** A message and its judgment share one card; the original is never styled as an input. */
class MessageJudgmentView(context:Context,message:ChatMessage,index:Int,group:Boolean,onReply:()->Unit,onRetry:()->Unit):LinearLayout(context) {
    private val palette=PanelPalette(context)
    private val resultColumn=LinearLayout(context).apply { orientation=VERTICAL }
    private val retry=palette.button("重试判断",action=onRetry)
    init {
        orientation=VERTICAL;setPadding(dp(11),dp(11),dp(11),dp(11))
        background=ViewStyles.shape(context,palette.card,16f)
        val header=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.TOP }
        val original=LinearLayout(context).apply { orientation=VERTICAL }
        val type=when(message.kind) { MessageKind.IMAGE->"图片";MessageKind.STICKER->"表情包";MessageKind.VOICE->"语音/转写";else->"文字" }
        original.addView(palette.text("${index+1} · ${if(group)(message.sender ?: "成员（昵称未知）")+" · " else ""}$type",11f,palette.muted))
        original.addView(palette.text(message.text,14f,bold=true).apply { setTextIsSelectable(true) },LayoutParams(-1,-2).apply { topMargin=dp(5) })
        header.addView(original,LayoutParams(0,-2,1f))
        header.addView(palette.button("指定回复",true,onReply).apply {
            textSize=11f;minimumHeight=dp(34);setPadding(dp(10),dp(7),dp(10),dp(7))
            contentDescription="指定回复：${message.text}"
        },LayoutParams(-2,-2).apply { leftMargin=dp(12) })
        addView(header,LayoutParams(-1,-2))
        addView(View(context).apply { setBackgroundColor(palette.border);alpha=.55f },LayoutParams(-1,dp(1)).apply { topMargin=dp(10);bottomMargin=dp(8) })
        addView(resultColumn,LayoutParams(-1,-2))
        addView(retry,LayoutParams(-2,dp(32)).apply { topMargin=dp(7) })
        show(null,"显示后自动判断")
    }
    fun show(result:MessageJudgment?,status:String?=null,failed:Boolean=false) {
        resultColumn.removeAllViews();retry.visibility=if(failed)View.VISIBLE else View.GONE
        fun line(label:String,value:String,color:Int=palette.ink) {
            val row=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.TOP }
            row.addView(palette.text(label,11f,palette.blue,true),LayoutParams(dp(62),-2))
            row.addView(palette.text(value,12f,color).apply { setTextIsSelectable(true) },LayoutParams(0,-2,1f))
            resultColumn.addView(row,LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
        }
        if(result==null) { resultColumn.addView(palette.text(status ?: "等待判断",12f,palette.muted));return }
        line("字面判断",result.literal?.let { "字面 ${percent(it)} · 言外 ${percent(1-it)}" } ?: "未提供权重")
        fun options(label:String,items:List<DecisionOption>)=line(label,items.joinToString("\n") { "${it.label}${it.weight?.let { w -> " · ${percent(w)}" }.orEmpty()}" }.ifBlank { "未提供判断" })
        options("可能意图",result.intents);options("对方需要",result.needs);options("建议动作",result.actions)
        val level=ConversationLevel.from(result.level)
        line("等级",level.label+level.value?.let { " $it/9" }.orEmpty(),palette.levelColor(level))
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
    private fun percent(value:Double)="${kotlin.math.round(value*100).toInt()}%"
}
