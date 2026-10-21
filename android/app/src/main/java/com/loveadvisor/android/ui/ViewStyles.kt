package com.loveadvisor.android.ui
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
object ViewStyles {
    const val BLUE=0xFF3979FF.toInt(); const val INK=0xFF172236.toInt(); const val MUTED=0xFF7E899B.toInt(); const val BG=0xFFF4F7FC.toInt()
    fun night(c:Context)=c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
    fun themed(c:Context,color:Int)=if(!night(c)) color else when(color) { INK->0xFFF2F5FC.toInt();MUTED->0xFFB4C2D8.toInt();BLUE->0xFF94BBFF.toInt();Color.WHITE->0xFF1B2536.toInt();BG->0xFF10151F.toInt();else->color }
    fun dp(c:Context,n:Float)=(n*c.resources.displayMetrics.density).toInt()
    fun shape(c:Context,color:Int=Color.WHITE,radius:Float=22f,stroke:Int?=null)=GradientDrawable().apply { setColor(if(color==Color.WHITE && night(c)) 0xFF1B2536.toInt() else color); cornerRadius=dp(c,radius).toFloat(); stroke?.let { setStroke(dp(c,1f),it) } }
    fun column(c:Context,padding:Int=0)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL; if(padding>0) setPadding(dp(c,padding.toFloat()),dp(c,padding.toFloat()),dp(c,padding.toFloat()),dp(c,padding.toFloat())) }
    fun text(c:Context,value:String,size:Float=16f,color:Int=INK,bold:Boolean=false)=TextView(c).apply { text=value; textSize=size; setTextColor(themed(c,color)); if(bold) setTypeface(typeface,Typeface.BOLD); setPadding(0,dp(c,5f),0,dp(c,5f)) }
    fun button(c:Context,value:String,primary:Boolean=false,action:()->Unit)=Button(c).apply {
        stateListAnimator=null; elevation=0f; translationZ=0f; backgroundTintList=null
        text=value; textSize=15f; isAllCaps=false; setTextColor(if(primary) Color.WHITE else themed(c,BLUE)); background=shape(c,if(primary) BLUE else Color.WHITE,18f,if(primary) null else 0xFFDCE3EE.toInt())
        minimumHeight=dp(c,46f); setPadding(dp(c,14f),dp(c,6f),dp(c,14f),dp(c,6f)); setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); com.loveadvisor.android.diagnostics.DiagnosticLogs.dispatch(value,action) }
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(c,8f); bottomMargin=dp(c,4f) }
    }
    fun field(c:Context,hintValue:String,value:String="",multiline:Boolean=false)=EditText(c).apply {
        hint=hintValue; setText(value); textSize=16f; setTextColor(themed(c,INK)); setHintTextColor(themed(c,MUTED)); background=shape(c,if(night(c)) 0xFF253146.toInt() else 0xFFF0F4FA.toInt(),16f); setPadding(dp(c,14f),dp(c,10f),dp(c,14f),dp(c,10f)); isSingleLine=!multiline; if(multiline) { minLines=3; maxLines=8; gravity=android.view.Gravity.TOP }; layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(c,8f) }
    }
    fun row(c:Context)=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
    fun compactButton(c:Context,value:String,primary:Boolean=false,action:()->Unit)=button(c,value,primary,action).apply {
        stateListAnimator=null; elevation=0f; translationZ=0f
        textSize=13f; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
        setPadding(dp(c,8f),0,dp(c,8f),0); background=shape(c,if(primary) BLUE else Color.WHITE,15f,if(primary) null else 0xFFDCE3EE.toInt())
        layoutParams=LinearLayout.LayoutParams(dp(c,56f),dp(c,32f)).apply { topMargin=dp(c,6f); rightMargin=dp(c,7f) }
    }
    fun card(c:Context)=column(c,18).apply { background=shape(c); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(c,12f); bottomMargin=dp(c,4f) } }
    fun switch(c:Context,title:String,checked:Boolean,action:(Boolean)->Unit)=Switch(c).apply { text=title; isChecked=checked; setTextColor(themed(c,INK)); setPadding(0,dp(c,10f),0,dp(c,10f)); setOnCheckedChangeListener { _,value -> com.loveadvisor.android.diagnostics.DiagnosticLogs.event(com.loveadvisor.core.diagnostics.LogStep.SETTING_CHANGE,metrics=mapOf(com.loveadvisor.core.diagnostics.LogMetric.ENABLED to if(value)1L else 0L));action(value) } }
}
