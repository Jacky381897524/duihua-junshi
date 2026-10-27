package com.loveadvisor.android.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.loveadvisor.android.diagnostics.DiagnosticLogs
import com.loveadvisor.android.storage.AppSettings
import com.loveadvisor.core.diagnostics.*

/** Both settings and the overlay menu edit the same saved opt-in, without a model request. */
class ReplyNaturalnessSwitch(context:Context,private val settings:AppSettings,compact:Boolean=false,
    ink:Int=ViewStyles.themed(context,ViewStyles.INK),muted:Int=ViewStyles.themed(context,ViewStyles.MUTED)):LinearLayout(context) {
    private var updating=false
    private var unsubscribe:(()->Unit)?=null
    private val toggle=Switch(context).apply {
        text=android.text.SpannableString("去AI味（减少生硬感）").apply { setSpan(android.text.style.RelativeSizeSpan(.68f),4,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);setSpan(android.text.style.ForegroundColorSpan(muted),4,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) };textSize=if(compact)14f else 16f;setTextColor(ink)
        minHeight=ViewStyles.dp(context,44f);setPadding(0,ViewStyles.dp(context,4f),0,ViewStyles.dp(context,4f))
    }
    init {
        orientation=VERTICAL
        addView(toggle,LayoutParams(-1,-2))
        renderSavedValue()
        toggle.setOnCheckedChangeListener { _,enabled ->
            if(!updating) {
                try {
                    settings.update { it.copy(naturalRepliesEnabled=enabled) }
                    DiagnosticLogs.event(LogStep.SETTING_CHANGE,LogState.OK,LogReason.UI_NATURAL_REPLIES,metrics=mapOf(LogMetric.ENABLED to if(enabled)1L else 0L))
                    Toast.makeText(context,if(enabled)"去AI味已开启，真人感开启时优先使用真人感" else "去AI味已关闭；真人感关闭时使用原版规则",Toast.LENGTH_SHORT).show()
                } catch(error:Exception) {
                    DiagnosticLogs.failed(LogStep.CONFIG_SAVE,error)
                    renderSavedValue()
                    Toast.makeText(context,"开关保存失败，已恢复原状态",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun renderSavedValue() {
        updating=true
        try { toggle.isChecked=settings.load().naturalRepliesEnabled } finally { updating=false }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        unsubscribe=settings.subscribe { post { if(isAttachedToWindow)renderSavedValue() } }
        renderSavedValue()
    }
    override fun onDetachedFromWindow() {
        unsubscribe?.invoke();unsubscribe=null
        super.onDetachedFromWindow()
    }
}
