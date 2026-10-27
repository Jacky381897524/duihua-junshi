package com.loveadvisor.android.overlay

import android.content.Context
import android.content.ClipboardManager
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles

class PastedReplyView(context:Context,onStart:(String)->Unit,onBack:()->Unit,requestFieldFocus:(EditText)->Unit):LinearLayout(context),PanelViewportPage {
    override val preferredViewportHeightDp=300
    init {
        orientation=VERTICAL;val palette=PanelPalette(context)
        addView(palette.text("复制单句回复",17f,bold=true))
        addView(palette.text("粘贴对方的话，生成六条回复。无需截图；只分析这段内容。",11f,palette.muted),LayoutParams(-1,-2).apply { bottomMargin=ViewStyles.dp(context,10f) })
        val row=LinearLayout(context)
        val input=ViewStyles.field(context,"粘贴或输入想回复的内容",multiline=true).apply { minLines=3;maxLines=7;filters=arrayOf(android.text.InputFilter.LengthFilter(4000)) }
        fun pasteWhenFocused(attempt:Int) {
            if(!isAttachedToWindow || !isShown)return
            if(!input.hasWindowFocus()) {
                if(attempt<5)input.postDelayed({ pasteWhenFocused(attempt+1) },80) else input.error="未取得输入焦点，请点输入框后再粘贴"
                return
            }
            try { val clip=context.getSystemService(ClipboardManager::class.java)?.primaryClip;val text=if(clip!=null && clip.itemCount>0)clip.getItemAt(0).coerceToText(context) else null;if(text.isNullOrBlank())input.error="剪贴板没有文字" else input.setText(text.take(4000)) }
            catch(error:Exception) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.LOCAL_TASK,error);input.error="暂时无法访问剪贴板，可长按输入框粘贴" }
        }
        row.addView(input,LayoutParams(0,-2,1f))
        row.addView(palette.button("粘贴") {
            requestFieldFocus(input);input.post { pasteWhenFocused(0) }
        },LayoutParams(-2,ViewStyles.dp(context,40f)).apply { leftMargin=ViewStyles.dp(context,6f) })
        addView(row,LayoutParams(-1,-2))
        addView(palette.button("开始分析",true) { if(input.text.isNullOrBlank())input.error="请先粘贴内容" else onStart(input.text.toString()) },LayoutParams(-1,-2).apply { topMargin=ViewStyles.dp(context,12f) })
        addView(palette.button("返回候选面板",action=onBack),LayoutParams(-1,-2))
    }
}
