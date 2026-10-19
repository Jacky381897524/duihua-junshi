package com.loveadvisor.android.settings
import android.content.Context
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
/** Overlay density is local to model forms; the main settings page keeps its sizing. */
class ModelFormStyle(private val context:Context,private val compact:Boolean=false) {
    private fun dp(n:Float)=ViewStyles.dp(context,n)
    fun text(value:String,size:Float=16f,color:Int=ViewStyles.INK,bold:Boolean=false)=ViewStyles.text(context,value,if(!compact)size else when { size>=20f->16f;size>=14f->12.5f;else->11f },color,bold).apply { if(compact)setPadding(0,dp(3f),0,dp(3f)) }
    fun card()=ViewStyles.card(context).apply { if(compact) { setPadding(dp(10f),dp(10f),dp(10f),dp(10f));layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6f);bottomMargin=dp(3f) } } }
    fun field(hint:String,value:String="")=ViewStyles.field(context,hint,value).apply { if(compact) { textSize=13f;minimumHeight=dp(38f);minHeight=minimumHeight;setPadding(dp(10f),dp(7f),dp(10f),dp(7f));layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5f) } } }
    fun button(value:String,primary:Boolean=false,action:()->Unit)=ViewStyles.button(context,value,primary,action).apply { if(compact) { textSize=12f;minimumHeight=dp(if(value.startsWith("保存"))48f else if(value.startsWith("返回"))44f else 36f);minHeight=minimumHeight;setPadding(dp(10f),dp(5f),dp(10f),dp(5f));layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5f);bottomMargin=dp(2f) } } }
    fun toggle(value:String,checked:Boolean,action:(Boolean)->Unit)=ViewStyles.switch(context,value,checked,action).apply { if(compact) { textSize=12f;setPadding(0,dp(6f),0,dp(6f)) } }
}
