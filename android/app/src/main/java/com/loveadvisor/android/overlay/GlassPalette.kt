package com.loveadvisor.android.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.loveadvisor.android.ui.ViewStyles

/** Native text stays above the optical layer and follows the system's appearance. */
class GlassPalette(private val context:Context) {
    val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    val ink=if(dark) 0xFFF2F5FC.toInt() else 0xFF17202C.toInt()
    val muted=if(dark) 0xFFB4C2D8.toInt() else 0xFF526174.toInt()
    val blue=if(dark) 0xFF94BBFF.toInt() else 0xFF246AF0.toInt()
    val border=if(dark) 0x38EEF7FF else 0x9EFFFFFF.toInt()
    val card=if(dark) 0x11E7F0FF else 0x63FFFFFF
    val selected=if(dark) 0x305D96FF else 0x70B8D7FF
    val scrim=if(dark) 0xAF151D2C.toInt() else 0x70EFF5FF
    val fallback=if(dark) 0xF51B2536.toInt() else 0xF5F5F9FF.toInt()
    fun text(value:String,size:Float,color:Int=ink,bold:Boolean=false)=ViewStyles.text(context,value,size,color,bold).apply {
        setPadding(0,0,0,0); includeFontPadding=false; setLineSpacing(0f,1.22f)
    }
    fun button(value:String,primary:Boolean=false,action:()->Unit):Button=ViewStyles.compactButton(context,value,primary,action).apply {
        setTextColor(if(primary) Color.WHITE else blue)
        background=ViewStyles.shape(context,if(primary) 0xFF3478F4.toInt() else if(dark) 0x1BE6F0FF else 0x8CFFFFFF.toInt(),14f,border)
        textSize=11f; includeFontPadding=false; minHeight=0; minimumHeight=0; minWidth=0; minimumWidth=0
        setPadding(ViewStyles.dp(context,10f),0,ViewStyles.dp(context,10f),0)
    }
}
