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
        fun constrain(rawX:Int,rawY:Int):PanelBounds {
            var y=rawY.coerceIn(display.top,maxOf(display.top,display.bottom-height))
            if(!fits && y<display.top+bar)y=(display.top+bar+px(6f)).coerceAtMost(maxOf(display.top,display.bottom-height))
            val inBar=fits && y<display.top+bar
            val minX=if(inBar)clockRight else display.left
            val maxX=if(inBar)zoneRight-width else display.right-width
            return PanelBounds(rawX.coerceIn(minX,minOf(maxX,display.right-width).coerceAtLeast(minX)),y,width,height)
        }
        fun clear(b:PanelBounds)=cutouts.none { b.leftPx<it.right && b.leftPx+b.widthPx>it.left && b.topPx<it.bottom && b.bottomPx>it.top }
        fun avoidCutouts(preferred:PanelBounds):PanelBounds {
            if(clear(preferred))return preferred
            // A left corner or landscape notch can extend below the status bar.
            val xs=listOf(preferred.leftPx,display.left,display.right-width)+cutouts.flatMap { listOf(it.left-gap-width,it.right+gap) }
            val ys=listOf(preferred.topPx,display.top+bar+gap,display.bottom-height)+cutouts.flatMap { listOf(it.top-gap-height,it.bottom+gap) }
            return xs.asSequence().flatMap { cx -> ys.asSequence().map { cy -> constrain(cx,cy) } }
                .filter { clear(it) }
                .minByOrNull { b -> val dx=b.leftPx.toLong()-preferred.leftPx;val dy=b.topPx.toLong()-preferred.topPx;dx*dx+dy*dy } ?: preferred
        }
        // Dragging and persistence both use place(0,0); offsets must start from that
        // same, already avoided anchor or the capsule jumps when a drag is saved.
        val anchor=avoidCutouts(constrain(baseX,baseY))
        return avoidCutouts(constrain(anchor.leftPx+px(ox),anchor.topPx+px(oy)))
    }
}
