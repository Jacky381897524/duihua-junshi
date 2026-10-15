package com.loveadvisor.core.overlay

enum class OverlayEntryMode { BUBBLE, ISLAND_TEST, ISLAND, TOP_CAPSULE }
data class IslandEnvironment(val allowed:Boolean,val vivo:Boolean,val notificationsEnabled:Boolean,val osBuild:String)
object NativeIslandEntryPolicy {
    fun status(reading:Boolean,state:com.loveadvisor.core.advisor.BatchState,current:Boolean)=status(reading,state.loading,current,state.isFailure)
    fun fallback(allowed:Boolean,end:()->Unit,attach:()->Unit) { end();if(allowed)attach() }
    fun status(reading:Boolean,loading:Boolean,current:Boolean,error:Boolean)=when {
        reading || loading -> "分析中"
        error -> "读取失败"
        current -> "6条候选"
        else -> "待读取"
    }
    private fun eligible(e:IslandEnvironment)=e.allowed && e.vivo && e.notificationsEnabled
    fun mode(requested:Boolean,confirmedBuild:String?,environment:IslandEnvironment,published:Boolean):OverlayEntryMode=when {
        !requested || !eligible(environment) || !published -> OverlayEntryMode.BUBBLE
        confirmedBuild==environment.osBuild && !confirmedBuild.isNullOrBlank() -> OverlayEntryMode.ISLAND
        else -> OverlayEntryMode.ISLAND_TEST
    }
    fun canConfirm(nativeClick:Boolean,currentLease:Boolean,panelShown:Boolean,environment:IslandEnvironment)=nativeClick && currentLease && panelShown && eligible(environment)
    fun currentLease(received:String,active:String,leasePackage:String,leaseWindow:Int,leaseGeneration:Long,packageName:String?,window:Int?,generation:Long,allowed:Boolean)=
        allowed && received==active && leasePackage==packageName && leaseWindow==window && leaseGeneration==generation
}
