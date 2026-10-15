package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI

enum class BubbleEdge { LEFT, RIGHT }
object BubbleEdgeGeometry {
    fun nearest(bubble:PanelBounds,screen:RectI)=
        if(bubble.leftPx+bubble.widthPx/2<screen.left+screen.width/2) BubbleEdge.LEFT else BubbleEdge.RIGHT

    fun place(bubble:PanelBounds,screen:RectI,edge:BubbleEdge,hidden:Boolean,display:RectI=screen):PanelBounds {
        val width=bubble.widthPx.coerceIn(1,screen.width.coerceAtLeast(1))
        val height=bubble.heightPx.coerceIn(1,screen.height.coerceAtLeast(1))
        val visible=if(hidden) (width/4).coerceAtLeast(1) else width
        // Off-screen clipping follows the physical display, not side navigation/cutout insets.
        val horizontal=if(hidden) display else screen
        val x=if(edge==BubbleEdge.LEFT) horizontal.left-width+visible else horizontal.right-visible
        val y=bubble.topPx.coerceIn(screen.top,maxOf(screen.top,screen.bottom-height))
        return PanelBounds(x,y,width,height)
    }
}
