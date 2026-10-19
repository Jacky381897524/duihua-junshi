package com.loveadvisor.android.overlay

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*

class SavedStylePickerView(context:Context,private val entries:List<ReplyStyleEntry>,onPick:(ReplyStyleEntry)->Unit,onBack:()->Unit,onCurrent:()->Unit):LinearLayout(context) {
    private val palette=PanelPalette(context)
    val search=EditText(context).apply {
        hint="搜索已保存昵称";setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=14f
        setSingleLine(true);minimumHeight=dp(48);setPadding(dp(12),dp(10),dp(12),dp(10))
        background=ViewStyles.shape(context,palette.card,12f,palette.border)
    }
    init {
        orientation=VERTICAL
        addView(palette.text("关联已有对象",18f,bold=true),row())
        addView(search,row())
        addView(palette.actionButton("使用本次读取的当前对象",heightDp=44,action=onCurrent),row())
        val list=LinearLayout(context).apply { orientation=VERTICAL };addView(list,row())
        fun render() {
            list.removeAllViews()
            val query=NicknameText.suggestionKey(search.text.toString()).lowercase()
            val filtered=entries.filter { query.isBlank() || NicknameText.suggestionKey(it.title.orEmpty()).lowercase().contains(query) }
            if(filtered.isEmpty())list.addView(palette.text(if(entries.isEmpty())"还没有专属风格，返回填写当前对象后保存。" else "没有匹配对象，请换个关键词。",13f,palette.muted),row())
            filtered.take(100).forEach { entry ->
                list.addView(palette.actionButton(entry.title ?: "旧版对象 · ${entry.key.take(8)}",heightDp=48) { onPick(entry) }.apply { maxLines=3 },row())
            }
            if(filtered.size>100)list.addView(palette.text("显示前100项，请输入昵称缩小范围。",12f,palette.muted))
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=render()
            override fun afterTextChanged(s:Editable?) {}
        })
        addView(palette.actionButton("返回风格设置",heightDp=44,action=onBack),row());render()
    }
    private fun dp(n:Int)=ViewStyles.dp(context,n.toFloat())
    private fun row()=LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
}
