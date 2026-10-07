package com.loveadvisor.android.overlay
import android.content.Context
import android.widget.*
import android.view.View
import android.view.Gravity
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.ReplyPlan
class ReplyCardView(context:Context,index:Int,plan:ReplyPlan,onCopy:()->Unit,onFill:(()->Unit)?,onDetails:(((String)->Unit)->Unit)?=null,compact:Boolean=false,scoreLabel:String="Jev"):LinearLayout(context) {
    val fillButton:Button?
    val copyButton:Button
    init {
        val palette=PanelPalette(context)
        orientation=VERTICAL; val d=ViewStyles.dp(context,if(compact) 6f else 11f); val h=ViewStyles.dp(context,if(compact) 9f else 11f); setPadding(h,d,h,d)
        background=ViewStyles.shape(context,if(index==0) palette.selected else palette.card,15f)
        layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,if(compact) 6f else 10f) }
        fun actionButton(label:String,primary:Boolean=false,action:()->Unit)=palette.button(label,primary,action).apply {
            if(compact) { textSize=11f; layoutParams=LayoutParams(ViewStyles.dp(context,48f),ViewStyles.dp(context,25f)).apply { rightMargin=ViewStyles.dp(context,6f) } }
        }
        val romance=plan.strategy in setOf(com.loveadvisor.core.advisor.Strategy.APPRECIATION,com.loveadvisor.core.advisor.Strategy.TEASING,com.loveadvisor.core.advisor.Strategy.INVITATION)
        addView(palette.text("${index+1} · ${plan.strategy.label}${if(romance) " · 恋爱大师" else ""}${plan.score?.let { " · $scoreLabel ${(it*100).toInt()}" } ?: ""}",if(compact) 11f else 12f,palette.blue,true).apply { layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,3f) } })
        addView(palette.text(plan.text,if(compact) 13f else 18f,bold=false).apply { setTextIsSelectable(true); minimumHeight=0; layoutParams=LayoutParams(-1,-2) })
        val actions=ViewStyles.row(context).apply {
            gravity=Gravity.TOP
            layoutParams=LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,if(compact) 4f else 6f) }
        }; copyButton=actionButton("复制",false,onCopy);actions.addView(copyButton)
        fillButton=onFill?.let { action -> actionButton("填入",true,action).also { actions.addView(it) } }; addView(actions)
        val details=palette.text("理由：${plan.reason}\n适用：${plan.suitableWhen}\n代价：${plan.cost}\n\n积极反馈：${plan.branches.positive}\n含糊反馈：${plan.branches.ambiguous}\n拒绝反馈：${plan.branches.rejection}",if(compact) 11f else 14f,palette.muted).apply { visibility=View.GONE; setTextIsSelectable(true) }
        var fetched=plan.reason.isNotBlank()
        actions.addView(actionButton("详情") {
            val opening=details.visibility==View.GONE; details.visibility=if(opening) View.VISIBLE else View.GONE
            if(opening && !fetched) { fetched=true; details.text="正在解释这条方案…"; onDetails?.invoke { result -> details.text=result } ?: run { details.text="该方案采用${plan.strategy.label}策略。" } }
        }); addView(details)
    }
}
