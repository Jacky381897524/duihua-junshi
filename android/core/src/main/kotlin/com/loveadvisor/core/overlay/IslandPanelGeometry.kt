package com.loveadvisor.core.overlay
import com.loveadvisor.core.model.RectI

object IslandPanelGeometry {
    fun open(size:PanelBounds,available:RectI,topGapPx:Int):PanelBounds {
        val width=size.widthPx.coerceIn(1,available.width.coerceAtLeast(1))
        val top=(available.top+topGapPx.coerceAtLeast(0)).coerceAtMost(maxOf(available.top,available.bottom-1))
        val height=size.heightPx.coerceIn(1,(available.bottom-top).coerceAtLeast(1))
        return PanelBounds(available.left+(available.width-width).coerceAtLeast(0)/2,top,width,height)
    }
}
