package com.loveadvisor.android.overlay

import android.content.Context
import android.graphics.Rect
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.analysis.MessageJudgment
import com.loveadvisor.core.model.*

class MessageAnalysisListView(context:Context,snapshot:ChatSnapshot,onReply:(ChatMessage)->Unit,onBack:()->Unit,onAutomatic:(()->Unit)?=null,
    private val onAnalyze:(ChatMessage,(MessageJudgment?,String?)->Unit)->(()->Unit)):LinearLayout(context),PanelViewportPage {
    override val preferredViewportHeightDp=540
    private val viewport=ScrollView(context)
    private val rows=snapshot.messages.filter { it.speaker==Speaker.OTHER }
    private val cards=linkedMapOf<String,MessageJudgmentView>()
    private val attempted=mutableSetOf<String>()
    private var active:String?=null
    private var cancelActive:(()->Unit)?=null
    private var epoch=0L
    private var closed=false
    private var ready=false
    private var opened=false
    private val next=Runnable { startVisible() }
    init {
        orientation=VERTICAL;val palette=PanelPalette(context)
        addView(palette.text("判断分析 · 选句回复",17f,bold=true))
        addView(palette.text("结合全部已读对话；判断为模型推测，权重不代表统计准确率。",11f,palette.muted),LayoutParams(-1,-2).apply { topMargin=dp(5);bottomMargin=dp(10) })
        val content=LinearLayout(context).apply { orientation=VERTICAL }
        rows.forEachIndexed { index,message ->
            val card=MessageJudgmentView(context,message,index,snapshot.identity?.kind==ChatKind.GROUP,{ onReply(message) },{
                attempted.remove(message.id);schedule()
            })
            cards[message.id]=card
            content.addView(card,LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
        }
        if(rows.isEmpty())content.addView(palette.text("本次没有读到可判断的对方消息",13f))
        viewport.addView(content);viewport.isFillViewport=false
        viewport.setOnScrollChangeListener { _,_,_,_,_->schedule() }
        viewport.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_->schedule() }
        content.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_->schedule() }
        addView(viewport,LayoutParams(-1,0,1f))
        val footer=LinearLayout(context).apply { orientation=HORIZONTAL }
        onAutomatic?.let { footer.addView(palette.button("恢复自动接话",action=it).apply { minimumHeight=dp(36) },LayoutParams(0,-2,1f).apply { rightMargin=dp(8) }) }
        footer.addView(palette.button("返回候选面板",action=onBack).apply { minimumHeight=dp(36) },LayoutParams(0,-2,1f))
        addView(footer,LayoutParams(-1,-2).apply { topMargin=dp(8) })
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { if(!closed && isAttachedToWindow) { if(!opened) { viewport.fullScroll(ScrollView.FOCUS_DOWN);opened=true };ready=true;schedule() } }
    }
    // Backgrounding retains the request already paid for; only visible-page work is queued.
    override fun onDetachedFromWindow() { ready=false;removeCallbacks(next);super.onDetachedFromWindow() }
    private fun schedule() { removeCallbacks(next);if(!closed && ready)post(next) }
    private fun startVisible() {
        if(closed || !ready || active!=null || !isAttachedToWindow)return
        val message=rows.asReversed().firstOrNull { it.id !in attempted && cards[it.id]?.getGlobalVisibleRect(Rect())==true } ?: return
        val card=cards.getValue(message.id);val requestEpoch=epoch
        active=message.id;card.show(null,"正在判断…")
        val cancellation=onAnalyze(message) { result,error ->
            if(!closed && epoch==requestEpoch && active==message.id) {
                active=null;cancelActive=null;attempted.add(message.id)
                card.show(result,error,failed=result==null);schedule()
            }
        }
        if(epoch==requestEpoch && active==message.id)cancelActive=cancellation
    }
    private fun pause() { ready=false;epoch++;removeCallbacks(next);cancelActive?.invoke();cancelActive=null;active=null }
    fun close() { closed=true;pause() }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
}
