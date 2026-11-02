package com.loveadvisor.core.capture

/** Repeated focus/state notifications from one Activity are not contact navigation. */
class HeaderActivityTracker {
    private var last:Triple<String,Int,String>?=null
    fun observe(pkg:String?,window:Int,name:String):Boolean {
        if(!CaptureEventPolicy.navigationChanged(pkg,name) || pkg==null)return false
        // The first observation establishes a baseline; it does not prove a switch.
        val next=Triple(pkg,window,name);val changed=last!=null && last!=next;last=next;return changed
    }
}
