package com.loveadvisor.android.overlay
import android.content.Context
import android.widget.*
import android.view.View
import android.view.Gravity
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*
class ReplyCardView(context:Context,index:Int,plan:ReplyPlan,onCopy:()->Unit,onFill:(()->Unit)?,onDetails:(((ReplyDetail?,String?)->Unit)->Unit)?=null,compact:Boolean=false,scoreLabel:String="Jev",frosted:Boolean=false,onContentChanged:()->Unit={}):LinearLayout(context) {
    val fillButton:Button?
    val copyButton:Button
    init {
        val palette=PanelPalette(context,frosted)
        orientation=VERTICAL; val d=ViewStyles.dp(context,if(compact) 6f else 11f); val h=ViewStyles.dp(context,if(compact) 9f else 11f); setPadding(h,d,h,d)
        background=ViewStyles.shape(context,if(index==0) palette.selected else palette.card,15f)
        layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,if(compact) 6f else 10f) }
        fun actionButton(label:String,primary:Boolean=false,action:()->Unit)=palette.button(label,primary,action).apply {
            if(compact)textSize=11f
            minimumHeight=ViewStyles.dp(context,if(compact)25f else 32f)
            setPadding(ViewStyles.dp(context,6f),ViewStyles.dp(context,3f),ViewStyles.dp(context,6f),ViewStyles.dp(context,3f))
            layoutParams=LayoutParams(0,-2,1f).apply { rightMargin=ViewStyles.dp(context,6f) }
        }
        val romance=plan.strategy in setOf(com.loveadvisor.core.advisor.Strategy.APPRECIATION,com.loveadvisor.core.advisor.Strategy.TEASING,com.loveadvisor.core.advisor.Strategy.INVITATION)
        addView(palette.text("${index+1} · ${plan.strategy.label}${if(romance) " · 恋爱大师" else ""}${plan.score?.let { " · $scoreLabel ${(it*100).toInt()}" } ?: ""}",if(compact) 11f else 12f,palette.blue,true).apply { layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,3f) } })
        plan.topic?.let { addView(palette.text("话题：$it · ${plan.targetLabel ?: "成员（昵称未知）"}",11f,palette.muted)) }
        addView(palette.text(plan.text,if(compact) 13f else 18f,bold=false).apply { setTextIsSelectable(true); minimumHeight=0; layoutParams=LayoutParams(-1,-2) })
        val actions=ViewStyles.row(context).apply {
            gravity=Gravity.TOP
            layoutParams=LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,if(compact) 4f else 6f) }
        }; copyButton=actionButton("复制",false,onCopy);actions.addView(copyButton)
        fillButton=onFill?.let { action -> actionButton("填入",true,action).also { actions.addView(it) } }; addView(actions)
        val details=LinearLayout(context).apply { orientation=VERTICAL;visibility=View.GONE }
        var fetched=plan.reason.isNotBlank();var busy=false
        fun show(value:ReplyDetail?,error:String?) {
            details.removeAllViews()
            if(value==null)details.addView(palette.text(error ?: "正在解释这条方案…",12f,palette.muted))
            else listOf("为什么" to value.why,"适合何时" to value.whenToUse,"注意" to value.caution,"积极回应后" to value.positive,"含糊回应后" to value.ambiguous,"明确拒绝后" to value.rejection).forEach { (label,text) ->
                details.addView(palette.text(label,11f,palette.blue,true).apply { setPadding(0,ViewStyles.dp(context,8f),0,ViewStyles.dp(context,3f)) })
                details.addView(palette.text(text,12f).apply { setTextIsSelectable(true) })
            }
        }
        if(fetched)show(ReplyDetail(plan.reason,plan.suitableWhen,plan.cost,plan.branches.positive,plan.branches.ambiguous,plan.branches.rejection),null)
        actions.addView(actionButton("详情") {
            val opening=details.visibility==View.GONE;details.visibility=if(opening)View.VISIBLE else View.GONE
            if(opening && !fetched && !busy) {
                busy=true;show(null,null)
                onDetails?.invoke { result,error -> busy=false;fetched=result!=null;show(result,error);onContentChanged() } ?: run { busy=false;show(null,"请重新读取后查看详情") }
            }
            onContentChanged()
        });addView(details)
    }
}
