package com.loveadvisor.android.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.loveadvisor.android.storage.AppSettings
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*

class ReplyRealitySwitch(context:Context,private val settings:AppSettings,compact:Boolean=false,
    ink:Int=ViewStyles.themed(context,ViewStyles.INK),muted:Int=ViewStyles.themed(context,ViewStyles.MUTED)):LinearLayout(context) {
    private var updating=false
    private var unsubscribe:(()->Unit)?=null
    private val toggle=Switch(context).apply { text=android.text.SpannableString("真人感（更接近你的口吻）").apply { setSpan(android.text.style.RelativeSizeSpan(.68f),3,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);setSpan(android.text.style.ForegroundColorSpan(muted),3,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) };textSize=if(compact)14f else 16f;setTextColor(ink);minHeight=ViewStyles.dp(context,44f) }
    init {
        orientation=VERTICAL;addView(toggle,LayoutParams(-1,-2))
        render()
        toggle.setOnCheckedChangeListener { _,enabled -> if(!updating)try {
            settings.update { it.copy(realisticRepliesEnabled=enabled) }
            Logs.event(LogStep.SETTING_CHANGE,LogState.OK,LogReason.UI_REALISTIC_REPLIES,metrics=mapOf(LogMetric.ENABLED to if(enabled)1L else 0L))
            Toast.makeText(context,if(enabled)"真人感已开启，下次生成或刷新生效" else "真人感已关闭；记忆保留，暂停学习与使用",Toast.LENGTH_SHORT).show()
        } catch(error:Exception) { render();Logs.failed(LogStep.CONFIG_SAVE,error);Toast.makeText(context,"开关保存失败，已恢复原状态",Toast.LENGTH_SHORT).show() } }
    }
    private fun render() { updating=true;try { toggle.isChecked=settings.load().realisticRepliesEnabled } finally { updating=false } }
    override fun onAttachedToWindow() { super.onAttachedToWindow();unsubscribe=settings.subscribe { post { if(isAttachedToWindow)render() } };render() }
    override fun onDetachedFromWindow() { unsubscribe?.invoke();unsubscribe=null;super.onDetachedFromWindow() }
}
