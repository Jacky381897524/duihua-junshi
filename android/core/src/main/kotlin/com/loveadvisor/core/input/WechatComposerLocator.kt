package com.loveadvisor.core.input

import com.loveadvisor.core.model.RectI
import com.loveadvisor.core.capture.ChatApps
import kotlin.math.abs

/** Local visual evidence only; never supplies a fixed fallback coordinate. */
object WechatComposerLocator {
    fun locate(packageName:String,width:Int,height:Int,pixel:(Int,Int)->Int):RectI? {
        if(packageName!=ChatApps.WECHAT || width<200 || height<width*1.2)return null
        fun distance(a:Int,b:Int):Int = maxOf(abs((a shr 16 and 255)-(b shr 16 and 255)),abs((a shr 8 and 255)-(b shr 8 and 255)),abs((a and 255)-(b and 255)))
        fun neutral(c:Int)=maxOf(c shr 16 and 255,c shr 8 and 255,c and 255)-minOf(c shr 16 and 255,c shr 8 and 255,c and 255)<12
        val step=maxOf(1,width/360);val gap=maxOf(2,width/180);val x=width/2
        val found=mutableListOf<RectI>()
        var y=(height-width*.4).toInt().coerceAtLeast(0)
        val end=height-gap
        while(y<end) {
            val fill=pixel(x,y)
            var top=y;var bottom=y+1
            while(top>0 && distance(pixel(x,top-1),fill)<=4)top--
            while(bottom<height && distance(pixel(x,bottom),fill)<=4)bottom++
            y=bottom+step
            val h=bottom-top
            if(!neutral(fill) || h<width*.05 || h>width*.13 || height-bottom !in (width*.025).toInt()..(width*.19).toInt())continue
            val row=top+maxOf(gap,h/10)
            var left=x;var right=x+1
            while(left>0 && distance(pixel(left-1,row),fill)<=4)left--
            while(right<width && distance(pixel(right,row),fill)<=4)right++
            if(left<width*.075 || left>width*.24 || right<width*.6 || right>width*.84 || right-left<width*.42 || top<gap || bottom+gap>=height)continue
            val mid=(top+bottom)/2
            val surround=pixel(left-gap,mid)
            if(!neutral(surround) || distance(surround,fill)<6 ||
                listOf(pixel(right+gap,mid),pixel(x,top-gap),pixel(x,bottom+gap)).any { distance(it,surround)>5 })continue
            // Reject center labels such as hold-to-talk and filled/ambiguous controls.
            var flat=0;var total=0
            for(py in top+gap until bottom-gap step step)for(px in left+gap until left+((right-left)*.8).toInt() step step) {
                total++;if(distance(pixel(px,py),fill)<=6)flat++
            }
            if(total==0 || flat.toDouble()/total<.995)continue
            fun controlInk(start:Int,finish:Int):Boolean {
                var ink=0;var samples=0
                for(py in top until bottom step step)for(px in start until finish step step) {
                    samples++;if(distance(pixel(px,py),surround)>35)ink++
                }
                return samples>0 && ink>=maxOf(6,samples/100) && ink<samples/2
            }
            if(!controlInk(gap,left-gap) || !controlInk(right+gap,width-gap))continue
            val candidate=RectI(left+gap,top+gap,right-gap,bottom-gap)
            if(found.none { abs(it.top-candidate.top)<h/2 })found.add(candidate)
        }
        return found.singleOrNull()
    }
}
