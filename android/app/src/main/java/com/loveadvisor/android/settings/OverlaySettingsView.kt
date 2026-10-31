package com.loveadvisor.android.settings

import android.content.Context
import android.text.InputType
import android.widget.*
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.overlay.PanelPalette
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.BubbleIdlePolicy

class OverlaySettingsView(context:Context,private val graph:AppGraph,private val onSaved:()->Unit,private val onCancel:()->Unit={},private val compact:Boolean=false):LinearLayout(context) {
    private val palette=PanelPalette(context)
    init {
        orientation=VERTICAL
        val original=graph.settings.load()
        var frosted=original.frostedPanelEnabled;var capsule=original.topCapsuleEnabled
        var reduced=original.reducedMotion;var locked=original.capsuleLocked
        var scale=original.capsuleScale;var resetPosition=false
        var keep=original.fillPanelBehavior=="keep";var limit=original.groupContextLimit
        fun row()=LayoutParams(-1,-2).apply { bottomMargin=dp(if(compact)6 else 12) }
        fun heading(title:String) { addView(palette.text(title,if(compact)14f else 16f,bold=true),row()) }
        fun toggle(title:String,checked:Boolean,changed:(Boolean)->Unit) { addView(ViewStyles.switch(context,title,checked,changed).apply {
            if(compact) { textSize=12.5f;setPadding(0,dp(3),0,dp(3));minimumHeight=dp(36) }
        },row()) }
        fun button(title:String,primary:Boolean=false,heightDp:Int=48,action:()->Unit)=palette.actionButton(title,primary,heightDp=if(compact)38 else heightDp,action=action).apply {
            if(compact) { textSize=12.5f;setPadding(dp(10),dp(5),dp(10),dp(5)) }
        }
        addView(palette.text("悬浮窗设置",if(compact)17f else 19f,bold=true),row())
        heading("外观与入口")
        toggle("使用轻毛玻璃",frosted) { frosted=it }
        toggle("使用顶部胶囊",capsule) { capsule=it }
        toggle("减少动效",reduced) { reduced=it }
        toggle("锁定胶囊位置",locked) { locked=it }
        addView(com.loveadvisor.android.ui.CapsuleScaleControl(context,scale) { value,_ -> scale=value },row())
        val position=button("重置胶囊位置",heightDp=44) { resetPosition=true };addView(position,row())
        heading("普通悬浮球")
        addView(palette.text("无操作后贴边，露出约三分之一；胶囊不使用此计时。",12f,palette.muted),row())
        val seconds=EditText(context).apply {
            hint="自动隐藏秒数（1–3600）";setText(original.bubbleAutoHideSeconds.toString());inputType=InputType.TYPE_CLASS_NUMBER
            setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=if(compact)13f else 14f;minimumHeight=dp(if(compact)36 else 48)
            setPadding(dp(12),dp(if(compact)6 else 10),dp(12),dp(if(compact)6 else 10));background=ViewStyles.shape(context,palette.card,12f)
        };addView(seconds,row())
        toggle("填入后保持面板展开",keep) { keep=it }
        heading("群聊已读上下文")
        val ranges=RadioGroup(context).apply { orientation=RadioGroup.HORIZONTAL }
        for(n in listOf(50,100,200)) {
            val button=RadioButton(context).apply { id=n;text="${n}条";setTextColor(palette.ink);minimumHeight=dp(if(compact)36 else 44);if(compact)textSize=12.5f }
            ranges.addView(button,RadioGroup.LayoutParams(0,-2,1f))
        }
        ranges.check(limit);ranges.setOnCheckedChangeListener { _,id -> limit=id }
        addView(ranges,row());addView(palette.text("只累计已手动读取的内容，不自动翻聊天记录。",12f,palette.muted),row())
        heading("候选回复保留")
        val cacheLimit=EditText(context).apply {
            hint="保留对象数量（1–20）";setText(original.replyCacheLimit.toString());inputType=InputType.TYPE_CLASS_NUMBER
            setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=if(compact)13f else 14f;minimumHeight=dp(if(compact)36 else 48)
            setPadding(dp(12),dp(if(compact)6 else 10),dp(12),dp(if(compact)6 else 10));background=ViewStyles.shape(context,palette.card,12f)
        };addView(cacheLimit,row())
        addView(palette.text("单聊和群聊共用此数量；超过上限时移除最久未使用的候选，不影响聊天记忆。",12f,palette.muted),row())
        val status=palette.text("",12f,palette.muted).apply { visibility=GONE };addView(status,row())
        val save=button("保存设置",true) {
            val value=seconds.text.toString().trim().toIntOrNull()
            if(value==null || value !in 1..BubbleIdlePolicy.MAX_SECONDS) { seconds.error="请输入1–3600的整数秒数";return@button }
            val cacheValue=cacheLimit.text.toString().trim().toIntOrNull()
            if(cacheValue==null || cacheValue !in 1..20) { cacheLimit.error="请输入1–20之间的整数";return@button }
            try {
                graph.settings.update { it.copy(frostedPanelEnabled=frosted,topCapsuleEnabled=capsule,nativeIslandEnabled=false,nativeIslandConfirmedBuild=null,reducedMotion=reduced,capsuleLocked=locked,capsuleScale=scale.coerceIn(.7f,2f),capsuleOffsetXdp=if(resetPosition)0f else it.capsuleOffsetXdp,capsuleOffsetYdp=if(resetPosition)0f else it.capsuleOffsetYdp,bubbleAutoHideSeconds=value,fillPanelBehavior=if(keep)"keep" else "collapse",groupContextLimit=limit,replyCacheLimit=cacheValue) }
                graph.replySessions.enforceLimit()
                onSaved()
            } catch(error:Exception) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.CONFIG_SAVE,error);status.text="保存失败，请重试";status.visibility=VISIBLE }
        }
        val back=button("返回",heightDp=44,action=onCancel)
        if(compact) {
            val actions=ViewStyles.row(context)
            actions.addView(back,LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
            actions.addView(save,LayoutParams(0,-2,1.4f));addView(actions,row())
        } else { addView(save,row());addView(back,row()) }
    }
    private fun dp(n:Int)=ViewStyles.dp(context,n.toFloat())
}
