package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI

/** The default anchor is beside the left clock. It never defaults to the center cutout. */
object TopCapsuleGeometry {
    /** Resume a drag from rendered bounds, including any rotation or keyboard clamp. */
    fun offsetOf(visible:PanelBounds,base:PanelBounds,density:Float):CapsuleDelta {
        val d=density.takeIf { it.isFinite() && it>0 } ?: 1f
        return CapsuleDelta((visible.leftPx-base.leftPx)/d,(visible.topPx-base.topPx)/d)
    }
    fun place(display:RectI,statusBarHeight:Int,density:Float,cutouts:List<RectI>,offsetXdp:Float=0f,offsetYdp:Float=0f,scale:Float=1f,contentWidthDp:Float=64f):PanelBounds {
        val d=density.takeIf { it.isFinite() && it>0 } ?: 1f
        fun px(value:Float)=(value*d).toInt()
        val factor=scale.takeIf { it.isFinite() }?.coerceIn(.7f,2f) ?: 1f
        val content=contentWidthDp.takeIf { it.isFinite() }?.coerceIn(28f,160f) ?: 50f
        val width=px(content*factor).coerceIn(1,display.width.coerceAtLeast(1))
        val height=px(22f*factor).coerceIn(1,display.height.coerceAtLeast(1))
        val bar=statusBarHeight.coerceAtLeast(0)
        val gap=px(8f)
        val clockRight=display.left+px(56f)
        val cutoutLeft=cutouts.filter { it.top<display.top+bar && it.bottom>display.top && it.right>clockRight }.minOfOrNull { it.left }
        val zoneRight=minOf(display.left+display.width/2-gap,cutoutLeft?.minus(gap) ?: display.right)
        val fits=clockRight+width<=zoneRight && height<=bar
        val baseX=if(fits)clockRight else display.left+px(16f)
        val baseY=if(fits)display.top+(bar-height)/2 else display.top+bar+px(6f)
        val ox=offsetXdp.takeIf { it.isFinite() } ?: 0f
        val oy=offsetYdp.takeIf { it.isFinite() } ?: 0f
        var y=(baseY+px(oy)).coerceIn(display.top,maxOf(display.top,display.bottom-height))
        if(!fits && y<display.top+bar) y=(display.top+bar+px(6f)).coerceAtMost(maxOf(display.top,display.bottom-height))
        val inBar=fits && y<display.top+bar
        val minX=if(inBar)clockRight else display.left
        val maxX=if(inBar)zoneRight-width else display.right-width
        val x=(baseX+px(ox)).coerceIn(minX,minOf(maxX,display.right-width).coerceAtLeast(minX))
        return PanelBounds(x,y,width,height)
    }
}
