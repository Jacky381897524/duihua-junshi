package com.loveadvisor.android.overlay
import android.content.Context
import android.widget.*
import android.view.View
import android.view.Gravity
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*
class ReplyCardView(context:Context,index:Int,plan:ReplyPlan,onCopy:()->Unit,onFill:(()->Unit)?,onDetails:(((ReplyDetail?,String?)->Unit)->Unit)?=null,compact:Boolean=false,scoreLabel:String="Jev",frosted:Boolean=false,private val onContentChanged:()->Unit={},onAdviceOpened:(ReplyCardView)->Unit={}):LinearLayout(context) {
    val fillButton:Button?
    val copyButton:Button
    private val details=LinearLayout(context).apply { orientation=VERTICAL;visibility=View.GONE }
    val expandedAdviceHeight get()=if(details.visibility==View.VISIBLE)details.measuredHeight else 0
    fun collapseAdvice() { if(details.visibility!=View.GONE) { details.visibility=View.GONE;onContentChanged() } }
    init {
        val palette=PanelPalette(context,frosted)
        orientation=VERTICAL; val d=ViewStyles.dp(context,if(compact) 6f else 11f); val h=ViewStyles.dp(context,if(compact) 9f else 11f); setPadding(h,d,h,d)
        background=ViewStyles.shape(context,if(index==0) palette.selected else palette.card,15f)
        layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,if(compact) 6f else 10f) }
        fun actionButton(label:String,primary:Boolean=false,action:()->Unit)=palette.button(label,primary,action).apply {
            if(compact)textSize=11f
            minimumHeight=ViewStyles.dp(context,if(compact)25f else 32f)
            minimumWidth=ViewStyles.dp(context,if(compact)56f else 0f);minWidth=minimumWidth
            val horizontal=ViewStyles.dp(context,if(compact)12f else 6f)
            setPadding(horizontal,ViewStyles.dp(context,3f),horizontal,ViewStyles.dp(context,3f))
            // Panel actions keep their natural width instead of stretching across the card.
            layoutParams=(if(compact)LayoutParams(-2,-2) else LayoutParams(0,-2,1f)).apply { rightMargin=ViewStyles.dp(context,6f) }
        }
        val romance=plan.strategy in setOf(com.loveadvisor.core.advisor.Strategy.APPRECIATION,com.loveadvisor.core.advisor.Strategy.TEASING,com.loveadvisor.core.advisor.Strategy.INVITATION)
        addView(palette.text("${index+1} · ${plan.strategy.label}${if(romance) " · 恋爱大师" else ""}${plan.score?.let { " · $scoreLabel ${(it*100).toInt()}" } ?: ""}",if(compact) 11f else 12f,palette.blue,true).apply { layoutParams=LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,3f) } })
        plan.topic?.let { addView(palette.text("话题：$it · ${plan.targetLabel ?: "成员（昵称未知）"}",11f,palette.muted)) }
        addView(palette.text(plan.text,if(compact) 13f else 18f,bold=false).apply { setTextIsSelectable(true); minimumHeight=0; layoutParams=LayoutParams(-1,-2) })
        plan.translationZh?.takeIf { it.isNotBlank() }?.let { addView(palette.text(it,if(compact)11f else 14f,palette.muted).apply { contentDescription="中文释义：$it";setTextIsSelectable(true);setPadding(0,ViewStyles.dp(context,4f),0,0) }) }
        val actions=ViewStyles.row(context).apply {
            gravity=Gravity.TOP
            layoutParams=LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,if(compact) 4f else 6f) }
        }; copyButton=actionButton("复制",false,onCopy);actions.addView(copyButton)
        fillButton=onFill?.let { action -> actionButton("填入",true,action).also { actions.addView(it) } }; addView(actions)
        var fetched=plan.reason.isNotBlank();var busy=false
        fun show(value:ReplyDetail?,error:String?) {
            details.removeAllViews()
            if(value==null)details.addView(palette.text(error ?: "正在整理建议…",12f,palette.muted).apply { setPadding(0,ViewStyles.dp(context,7f),0,0) })
            else if(compact) {
                fun line(label:String,text:String) {
                    val row=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.TOP }
                    row.addView(palette.text(label,11f,palette.blue,true),LayoutParams(ViewStyles.dp(context,46f),-2))
                    row.addView(palette.text(text,12f).apply { setTextIsSelectable(true) },LayoutParams(0,-2,1f))
                    details.addView(row,LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,7f) })
                }
                line("怎么接",value.why);line("注意",value.caution)
                val branches=ViewStyles.row(context).apply { gravity=Gravity.CENTER_VERTICAL }
                branches.addView(palette.text("后续",11f,palette.blue,true),LayoutParams(ViewStyles.dp(context,46f),-2))
                val followUp=palette.text(value.positive,12f).apply { setTextIsSelectable(true) }
                val tabs=mutableListOf<Button>()
                fun select(tab:Button) { tabs.forEach {
                    val selected=it===tab;it.isSelected=selected
                    it.setTextColor(if(selected)android.graphics.Color.WHITE else palette.blue)
                    it.background=ViewStyles.shape(context,if(selected)0xFF3478F4.toInt() else palette.card,12f,palette.border)
                } }
                listOf("积极" to value.positive,"含糊" to value.ambiguous,"拒绝" to value.rejection).forEach { (label,text) ->
                    val tab=palette.button(label) {}.apply { textSize=10.5f }
                    tab.setOnClickListener {
                        followUp.text=text
                        select(tab)
                        onContentChanged()
                    }
                    tabs.add(tab)
                    branches.addView(tab,LayoutParams(-2,ViewStyles.dp(context,27f*resources.configuration.fontScale.coerceAtLeast(1f))).apply { rightMargin=ViewStyles.dp(context,5f) })
                }
                select(tabs.first())
                details.addView(branches,LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,8f) })
                details.addView(followUp,LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,5f) })
            } else listOf("怎么接" to value.why,"适合何时" to value.whenToUse,"注意" to value.caution,"积极回应后" to value.positive,"含糊回应后" to value.ambiguous,"明确拒绝后" to value.rejection).forEach { (label,text) ->
                details.addView(palette.text(label,11f,palette.blue,true).apply { setPadding(0,ViewStyles.dp(context,8f),0,ViewStyles.dp(context,3f)) })
                details.addView(palette.text(text,12f).apply { setTextIsSelectable(true) })
            }
        }
        if(fetched)show(ReplyDetail(plan.reason,plan.suitableWhen,plan.cost,plan.branches.positive,plan.branches.ambiguous,plan.branches.rejection),null)
        actions.addView(actionButton("建议") {
            val opening=details.visibility==View.GONE;details.visibility=if(opening)View.VISIBLE else View.GONE
            if(opening)onAdviceOpened(this)
            if(opening && !fetched && !busy) {
                busy=true;show(null,null)
                onDetails?.invoke { result,error -> busy=false;fetched=result!=null;show(result,error);onContentChanged() } ?: run { busy=false;show(null,"请重新读取后查看建议") }
            }
            onContentChanged()
        });addView(details)
    }
}
