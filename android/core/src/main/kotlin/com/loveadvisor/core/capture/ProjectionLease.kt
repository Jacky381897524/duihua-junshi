package com.loveadvisor.core.capture
class ProjectionLease(private val packageName:String,private val windowId:Int,private val width:Int,private val height:Int) {
    private var active=true
    @Synchronized fun resize(width:Int,height:Int):Boolean { if(width!=this.width || height!=this.height) active=false; return active }
    @Synchronized fun accepts(window:WindowSnapshot?):Boolean = active && window?.packageName==packageName && window.windowId==windowId
    @Synchronized fun cancel() { active=false }
}
