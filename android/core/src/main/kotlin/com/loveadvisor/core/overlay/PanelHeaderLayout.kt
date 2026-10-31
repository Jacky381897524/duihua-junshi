package com.loveadvisor.core.overlay
data class HeaderActionLayout(val summaryWidth:Int,val actionWidth:Int,val actionHeight:Int,val gap:Int,val shortLabels:Boolean)
object PanelHeaderLayout {
    fun measure(widthPx:Int,density:Float,fontScale:Float):HeaderActionLayout {
        val width=widthPx.coerceAtLeast(1);val d=density.coerceAtLeast(.5f)
        val gap=(4*d).toInt().coerceAtMost(width/10)
        val action=minOf((56*d).toInt(),((width-2*gap)*.19f).toInt()).coerceAtLeast(1)
        return HeaderActionLayout((width-2*gap-2*action).coerceAtLeast(1),action,maxOf((40*d).toInt(),kotlin.math.ceil((28*d*fontScale.coerceAtLeast(1f)).toDouble()).toInt()),gap,fontScale>1.3f || action/d<49f)
    }
}
