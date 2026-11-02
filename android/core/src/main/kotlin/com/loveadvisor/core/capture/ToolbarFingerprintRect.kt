package com.loveadvisor.core.capture

import com.loveadvisor.core.model.RectI

/** Exclude clocks, recording timers and the scrollable chat body from ownership proof. */
object ToolbarFingerprintRect {
    fun resolve(width:Int,height:Int,viewport:RectI,statusBarBottom:Int,density:Float):RectI? {
        if(width<=0 || height<=0 || viewport.width<=0 || viewport.height<=0 || density<=0)return null
        val scale=height.toDouble()/viewport.height
        val top=(maxOf(0,statusBarBottom-viewport.top)*scale).toInt().coerceAtMost(height)
        val bottom=minOf(height,top+(56*density*scale).toInt())
        return RectI((width*.18).toInt(),top,(width*.82).toInt(),bottom).takeIf { it.width>0 && it.height>0 }
    }
}
