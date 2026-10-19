package com.loveadvisor.android.settings

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import com.loveadvisor.android.overlay.PanelPalette
import com.loveadvisor.android.ui.ViewStyles

/** Inline searchable selection shared by provider, model and range pickers. */
class ModelPickerView(context:Context,title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit,onBack:()->Unit):LinearLayout(context) {
    private val palette=PanelPalette(context)
    init {
        orientation=VERTICAL
        addView(palette.text(title,18f,bold=true),row())
        val search=EditText(context).apply {
            hint="搜索";setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=14f;setSingleLine(true)
            minimumHeight=dp(48);setPadding(dp(12),dp(10),dp(12),dp(10));background=ViewStyles.shape(context,palette.card,12f)
        };addView(search,row())
        val list=LinearLayout(context).apply { orientation=VERTICAL };addView(list,row())
        fun render() {
            list.removeAllViews()
            val matched=items.withIndex().filter { search.text.isBlank() || it.value.contains(search.text.toString(),true) }
            if(matched.isEmpty())list.addView(palette.text("没有匹配项",13f,palette.muted),row())
            matched.take(100).forEach { item ->
                list.addView(palette.actionButton((if(item.index==selected)"✓ " else "")+item.value,heightDp=44) { onSelected(item.index) }.apply { maxLines=4 },row())
            }
            if(matched.size>100)list.addView(palette.text("显示前100项，请搜索模型 ID。",12f,palette.muted),row())
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=render()
            override fun afterTextChanged(s:Editable?) {}
        })
        addView(palette.actionButton("返回",heightDp=44,action=onBack),row());render()
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
    private fun row()=LayoutParams(-1,-2).apply { bottomMargin=dp(9) }
}
