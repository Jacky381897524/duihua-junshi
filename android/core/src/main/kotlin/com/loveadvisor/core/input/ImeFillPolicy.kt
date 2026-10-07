package com.loveadvisor.core.input
import com.loveadvisor.core.model.RectI
import com.loveadvisor.core.capture.ChatApps
object ImeFillPolicy {
    fun editorAllowed(packageName:String?,inputType:Int,imeOptions:Int):Boolean {
        val variation=inputType and 0xff0
        return ChatApps.supported(packageName) && inputType and 0xf==1 && variation !in setOf(0x80,0x90,0xe0) && imeOptions and 0xff!=3 && (inputType and 0x20000!=0 || imeOptions and 0xff==4)
    }
    fun composerAllowed(r:RectI,v:RectI)=r.left>=v.left && r.right<=v.right && r.top>=v.top+v.height*.55 && r.bottom<=v.bottom && r.width>=v.width*.25 && r.width<=v.width*.85 && r.height in 20..(v.height*.15).toInt()
    fun completeDraft(text:String,offset:Int,start:Int,end:Int):Boolean {
        if(text.length>=8192 || start !in 0..text.length || end !in 0..text.length)return false
        val suffix=text.length-maxOf(start,end)
        // InputConnection's standard fallback reports offset=-1. Both returned sides must
        // be shorter than the requested 4096 limit before that result is treated as whole.
        return suffix<4096 && (offset==0 || offset==-1 && minOf(start,end)<4096)
    }
}
