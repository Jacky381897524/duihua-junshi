package com.loveadvisor.core.capture

data class AppWindow(val id:Int,val focused:Boolean,val active:Boolean,val layer:Int)
object ForegroundWindowSelector {
    fun select(windows:List<AppWindow>,overlayActive:Boolean=false):AppWindow?=windows.filter { it.focused }.maxByOrNull { it.layer }
        ?: windows.filter { it.active }.maxByOrNull { it.layer }
        ?: windows.takeIf { overlayActive }?.maxByOrNull { it.layer }
}
object CaptureEventPolicy {
    fun foregroundAppChanged(pkg:String?,windowClass:String,imePackages:Set<String>):Boolean=
        pkg!=null && !ChatApps.supported(pkg) && pkg!="com.android.systemui" && pkg !in imePackages && windowClass.startsWith("$pkg.")
    fun shouldObserve(pkg:String?,structural:Boolean,ownPackage:String)=pkg!=ownPackage && (ChatApps.supported(pkg) || structural)
    fun navigationChanged(pkg:String?,windowClass:String)=ChatApps.supported(pkg) && windowClass.startsWith("$pkg.") &&
        (windowClass.endsWith("Activity") || windowClass.endsWith("UI")) && !windowClass.contains(".widget.")
}
data class ReadLease(val epoch:Long,val revision:Long)
data class ReadCompletion(val accept:Boolean,val readAgain:Boolean)
class CaptureReadGate {
    private var epoch=0L; private var revision=0L; private var active:ReadLease?=null; private var pending=false
    @Synchronized fun begin():ReadLease? {
        if(active!=null) { pending=true; return null }
        revision++
        return ReadLease(epoch,revision).also { active=it }
    }
    @Synchronized fun complete(lease:ReadLease):ReadCompletion {
        if(active!=lease) return ReadCompletion(false,false)
        active=null; val result=ReadCompletion(lease.epoch==epoch,pending)
        pending=false; return result
    }
    @Synchronized fun invalidate() { epoch++; revision++; pending=false }
}
