package com.loveadvisor.android.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.Button
import com.loveadvisor.android.ui.ViewStyles

/** Opaque native surfaces: readable on any chat wallpaper, with no backdrop sampling. */
class PanelPalette(private val context:Context) {
    private val dark=context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    val ink=if(dark) 0xFFF2F5FC.toInt() else 0xFF17202C.toInt()
    val muted=if(dark) 0xFFB4C2D8.toInt() else 0xFF526174.toInt()
    val blue=if(dark) 0xFF94BBFF.toInt() else 0xFF246AF0.toInt()
    val border=if(dark) 0xFF3D4B60.toInt() else 0xFFDCE3EE.toInt()
    val card=if(dark) 0xFF253146.toInt() else 0xFFF2F4F7.toInt()
    val selected=if(dark) 0xFF233B61.toInt() else 0xFFEAF2FF.toInt()
    val surface=if(dark) 0xFF1B2536.toInt() else 0xFFFAFBFD.toInt()
    fun text(value:String,size:Float,color:Int=ink,bold:Boolean=false)=ViewStyles.text(context,value,size,color,bold).apply {
        setPadding(0,0,0,0);includeFontPadding=false;setLineSpacing(0f,1.22f)
    }
    fun button(value:String,primary:Boolean=false,action:()->Unit):Button=ViewStyles.compactButton(context,value,primary,action).apply {
        setTextColor(if(primary) Color.WHITE else blue)
        background=ViewStyles.shape(context,if(primary) 0xFF3478F4.toInt() else if(dark) 0xFF29384D.toInt() else Color.WHITE,14f,border)
        textSize=11f;includeFontPadding=false;minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
        setPadding(ViewStyles.dp(context,10f),0,ViewStyles.dp(context,10f),0)
    }
}
