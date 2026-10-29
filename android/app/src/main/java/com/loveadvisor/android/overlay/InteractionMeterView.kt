package com.loveadvisor.android.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.memory.InteractionPresentation

/** Updates from local evidence changes only; no timers, model requests or artificial score growth. */
class InteractionMeterView(context:Context,private val palette:PanelPalette,private val reduced:()->Boolean,private val resized:()->Unit):LinearLayout(context) {
    private val title=palette.text("好感度 · 待读取",11f,palette.blue,true)
    private val detail=palette.text("",10f,palette.muted)
    private val bar=Meter(context)
    private var shown=InteractionPresentation()
    private var expanded=false
    init {
        orientation=VERTICAL;isClickable=true;isFocusable=true
        background=ViewStyles.shape(context,palette.card,12f,palette.border)
        setPadding(dp(10),dp(7),dp(10),dp(7));minimumHeight=dp(48)
        addView(title,LayoutParams(-1,-2));addView(bar,LayoutParams(-1,dp(4)).apply { topMargin=dp(6) })
        addView(detail,LayoutParams(-1,-2).apply { topMargin=dp(6) });detail.visibility=GONE
        setOnClickListener { expanded=!expanded;detail.visibility=if(expanded)VISIBLE else GONE;renderDetail();performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);resized() }
    }
    fun render(value:InteractionPresentation) {
        val changed=value!=shown;val same=shown.contactId==value.contactId
        shown=value
        val estimate=value.estimate
        title.text=when { value.group && value.contactId==null->"好感度 · 选句后查看群成员";value.contactId==null->"好感度 · 待读取对象";estimate==null->"好感度 · 待积累依据";else->"好感度 ${estimate.toInt()}/100 · 互动估计" }
        contentDescription=title.text.toString()+"，点按查看依据"
        stateDescription=if(expanded)"已展开" else "已收起"
        bar.set(value.estimate?.toFloat()?.div(100f) ?: 0f,changed && same && !reduced())
        renderDetail()
    }
    private fun renderDetail() {
        detail.text="关系：${shown.relationship} · ${shown.evidenceCount}条互动依据\n"+if(shown.paused)"学习已暂停，保留已有估计。" else "读取或记忆更新后刷新；是模型与规则的估计，不是对方真实心理。重复材料不累计。"
    }
    fun stop()=bar.stop()
    private fun dp(n:Int)=ViewStyles.dp(context,n.toFloat())
    private class Meter(context:Context):View(context) {
        private var fraction=0f;private var animation:ValueAnimator?=null
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect=RectF()
        fun set(value:Float,animate:Boolean) {
            animation?.cancel();animation=null
            if(animate && ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow) {
                animation=ValueAnimator.ofFloat(fraction,value.coerceIn(0f,1f)).apply { duration=450;addUpdateListener { fraction=it.animatedValue as Float;invalidate() };start() }
            } else { fraction=value.coerceIn(0f,1f);invalidate() }
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas);rect.set(0f,0f,width.toFloat(),height.toFloat());paint.color=0x334C78AF;canvas.drawRoundRect(rect,height/2f,height/2f,paint)
            rect.right=width*fraction;paint.color=0xFF72CBB2.toInt();canvas.drawRoundRect(rect,height/2f,height/2f,paint)
        }
        fun stop(){ animation?.cancel();animation=null }
        override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
    }
}
