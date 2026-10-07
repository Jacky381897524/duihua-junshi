package com.loveadvisor.core.overlay
import com.loveadvisor.core.model.RectI
data class InsetsI(val left:Int=0,val top:Int=0,val right:Int=0,val bottom:Int=0)
data class PanelBounds(val leftPx:Int,val topPx:Int,val widthPx:Int,val heightPx:Int) { val bottomPx get()=topPx+heightPx }
data class DockedBounds(val window:PanelBounds,val bubble:PanelBounds,val panel:PanelBounds)
object OverlayGeometry {
    fun composerSafeBottom(imeTop:Int,density:Float)=imeTop-(72*density).toInt()
    fun dock(bubble:PanelBounds,panelSize:PanelBounds,available:RectI,panelOnLeft:Boolean?=null):DockedBounds {
        val width=available.width.coerceAtLeast(1); val height=available.height.coerceAtLeast(1)
        val bw=bubble.widthPx.coerceIn(1,width); val bh=bubble.heightPx.coerceIn(1,height)
        val pw=panelSize.widthPx.coerceIn(0,(width-bw).coerceAtLeast(0)); val ph=panelSize.heightPx.coerceIn(1,height)
        val left=panelOnLeft ?: (bubble.leftPx+bw/2>=available.left+width/2)
        val groupWidth=bw+pw; val groupHeight=maxOf(bh,ph)
        val x=(if(left) bubble.leftPx-pw else bubble.leftPx).coerceIn(available.left,available.right-groupWidth)
        val y=bubble.topPx.coerceIn(available.top,maxOf(available.top,available.bottom-groupHeight))
        val b=PanelBounds(if(left) x+pw else x,y,bw,bh)
        val p=PanelBounds(if(left) x else x+bw,y,pw,ph)
        return DockedBounds(PanelBounds(x,y,groupWidth,groupHeight),b,p)
    }
    fun measure(screen:RectI,insets:InsetsI,keyboard:RectI?,density:Float):PanelBounds {
        val left=screen.left+insets.left; val top=screen.top+insets.top
        val right=(screen.right-insets.right).coerceAtLeast(left)
        val bottom=minOf(screen.bottom-insets.bottom,keyboard?.top?.let { composerSafeBottom(it,density) } ?: screen.bottom).coerceAtLeast(top)
        val width=minOf(((right-left)*.86).toInt(),(340*density).toInt(),(right-left-40*density).toInt()).coerceAtLeast(1).coerceAtMost((right-left).coerceAtLeast(1))
        val height=minOf((474*density).toInt(),bottom-top).coerceAtLeast(1).coerceAtMost((bottom-top).coerceAtLeast(1))
        return PanelBounds(left+((right-left)-width)/2,top+((bottom-top)-height)/2,width,height)
    }
    fun clamp(bounds:PanelBounds,screen:RectI)=bounds.copy(leftPx=bounds.leftPx.coerceIn(screen.left,maxOf(screen.left,screen.right-bounds.widthPx)),topPx=bounds.topPx.coerceIn(screen.top,maxOf(screen.top,screen.bottom-bounds.heightPx)))
}
