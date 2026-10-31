package com.loveadvisor.android.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.*
import android.widget.*
import com.loveadvisor.android.overlay.PanelPalette
import com.loveadvisor.android.storage.AppSettings
import com.loveadvisor.core.advisor.ExpressionMode
import com.loveadvisor.core.advisor.RealityReplyPolicy
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** One persisted selection for both menu and settings. Gestures never generate a reply. */
class ReplyModeSelector(context:Context,private val settings:AppSettings,compact:Boolean=false):LinearLayout(context) {
    private val track=Track(context)
    private var unsubscribe:(()->Unit)?=null
    init {
        orientation=VERTICAL
        addView(track,LayoutParams(-1,if(compact)ViewStyles.dp(context,34f*resources.configuration.fontScale.coerceAtLeast(1f)) else dp(48)))
        addView(PanelPalette(context).text("祛味：减少AI生硬感\n真实：模仿更接近你的口吻",if(compact)9.5f else 11f,PanelPalette(context).muted),LayoutParams(-1,-2).apply { topMargin=dp(3) })
        render(false)
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
    private fun render(animate:Boolean=true) { val s=settings.load();track.select(RealityReplyPolicy.mode(s.realisticRepliesEnabled,s.naturalRepliesEnabled).ordinal,animate) }
    private fun choose(index:Int) {
        val mode=ExpressionMode.entries[index.coerceIn(0,2)]
        try {
            val previous=settings.load()
            if(previous.realisticRepliesEnabled==(mode==ExpressionMode.REALISTIC) && previous.naturalRepliesEnabled==(mode==ExpressionMode.NATURAL)) { render();return }
            settings.update { it.copy(realisticRepliesEnabled=mode==ExpressionMode.REALISTIC,naturalRepliesEnabled=mode==ExpressionMode.NATURAL) }
            render();performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Toast.makeText(context,"已选${listOf("默认","袪味","真实")[index.coerceIn(0,2)]}，下次生成或刷新生效",Toast.LENGTH_SHORT).show()
            Logs.event(LogStep.SETTING_CHANGE,LogState.OK,if(mode==ExpressionMode.REALISTIC)LogReason.UI_REALISTIC_REPLIES else LogReason.UI_NATURAL_REPLIES,metrics=mapOf(LogMetric.ENABLED to mode.ordinal.toLong()))
        } catch(error:Exception) { Logs.failed(LogStep.CONFIG_SAVE,error);render(false);Toast.makeText(context,"设置保存失败，已恢复原选项",Toast.LENGTH_SHORT).show() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();unsubscribe=settings.subscribe { post { if(isAttachedToWindow)render() } };render(false) }
    override fun onDetachedFromWindow() { unsubscribe?.invoke();unsubscribe=null;track.stop();super.onDetachedFromWindow() }
    private inner class Track(context:Context):FrameLayout(context) {
        private val palette=PanelPalette(context)
        private val pill=View(context).apply { background=ViewStyles.shape(context,palette.selected,12f,palette.blue) }
        private val labels=mutableListOf<TextView>()
        private var selected=-1
        private var position=0f
        private var animation:ValueAnimator?=null
        private var downX=0f;private var downY=0f;private var start=0f;private var dragging=false
        private val slop=ViewConfiguration.get(context).scaledTouchSlop
        init {
            background=ViewStyles.shape(context,palette.card,14f,palette.border)
            addView(pill,LayoutParams(1,-1).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            val row=LinearLayout(context)
            listOf("默认","袪味","真实").forEachIndexed { index,label ->
                val text=palette.text(label,13f).apply {
                    gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription="$label，回复表达模式"
                    setOnClickListener { choose(index) }
                }
                labels.add(text);row.addView(text,LinearLayout.LayoutParams(0,-1,1f))
            }
            addView(row,LayoutParams(-1,-1))
        }
        fun select(value:Int,animate:Boolean) {
            if(selected==value && !dragging && (animate || position==value.toFloat()))return
            val initial=selected<0;selected=value
            labels.forEachIndexed { i,label -> label.isSelected=i==value;label.stateDescription=if(i==value)"已选中" else "未选中";label.setTextColor(if(i==value)palette.blue else palette.muted) }
            stop()
            if(animate && !initial && isAttachedToWindow && !settings.load().reducedMotion && ValueAnimator.areAnimatorsEnabled()) {
                animation=ValueAnimator.ofFloat(position,value.toFloat()).apply { duration=180;addUpdateListener { position=it.animatedValue as Float;place() };start() }
            } else { position=value.toFloat();place() }
        }
        private fun place() { if(width<=0)return;val segment=width/3f;val next=(segment-dp(6)).roundToInt().coerceAtLeast(1);if(pill.layoutParams.width!=next) { val p=pill.layoutParams;p.width=next;pill.layoutParams=p };pill.translationX=segment*position }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);place() }
        override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=e.x;downY=e.y;start=position;dragging=false }
                MotionEvent.ACTION_MOVE -> if(abs(e.x-downX)>slop && abs(e.x-downX)>abs(e.y-downY)) { dragging=true;stop();parent.requestDisallowInterceptTouchEvent(true);return true }
            }
            return super.onInterceptTouchEvent(e)
        }
        override fun onTouchEvent(e:MotionEvent):Boolean {
            when(e.actionMasked) {
                MotionEvent.ACTION_MOVE -> { position=(start+(e.x-downX)/(width/3f).coerceAtLeast(1f)).coerceIn(0f,2f);place() }
                MotionEvent.ACTION_UP -> { val chosen=position.roundToInt();choose(chosen);dragging=false;parent.requestDisallowInterceptTouchEvent(false);performClick() }
                MotionEvent.ACTION_CANCEL -> { render(false);dragging=false;parent.requestDisallowInterceptTouchEvent(false) }
            }
            return true
        }
        override fun performClick():Boolean { super.performClick();return true }
        fun stop() { animation?.cancel();animation=null }
    }
}
