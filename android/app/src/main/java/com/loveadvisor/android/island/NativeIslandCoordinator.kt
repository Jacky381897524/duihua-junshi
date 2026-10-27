package com.loveadvisor.android.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.overlay.OverlayController
import com.loveadvisor.core.advisor.BatchState
import com.loveadvisor.core.overlay.*
import java.util.UUID

/** Main-thread owner of our notification. Never reads chat nodes or calls a model. */
class NativeIslandCoordinator(private val service:ChatCaptureService,private val overlay:OverlayController) {
    private val graph=service.graph
    private val sender=NativeIslandNotification(service)
    private val main=Handler(Looper.getMainLooper())
    private val session=IslandSessionState()
    private var environment=IslandEnvironment(false,false,false,"")
    var lease:IslandLease?=null;private set
    var diagnostic="原生岛已关闭";private set
    private var desired="待读取"
    private var displayed:String?=null
    private var lastPosted=0L
    private var reading=false
    private var state=BatchState()
    private val listener:(BatchState)->Unit={ state=it;updateStatus(reading,it,isCurrent(it)) }
    private fun isCurrent(value:BatchState)=value.current?.let { graph.tracker.sameConversation(it.ticket.session) && it.batch.replies.size==6 }==true
    private val update=Runnable { publish() }
    private val watch=object:Runnable {
        override fun run() {
            if(lease==null) return
            val env=service.islandEnvironment()
            if(!env.allowed || !env.notificationsEnabled) { refreshEligibility(env);return }
            if(!sender.active()) { fail("通知已结束，已恢复悬浮球");return }
            if(session.expired(SystemClock.uptimeMillis())) { fail("尚未收到原生岛点击，已结束测试并保留悬浮球");return }
            main.postDelayed(this,2000)
        }
    }
    init { graph.controller.listen(listener) }
    fun refreshEligibility(value:IslandEnvironment) {
        environment=value
        val settings=graph.settings.load()
        if(!settings.nativeIslandEnabled || !value.allowed || !value.vivo || !value.notificationsEnabled) {
            NativeIslandEntryPolicy.fallback(value.allowed,{ stop();if(!value.allowed)overlay.suppress() },{ overlay.showBubble() })
            diagnostic=when {
                !settings.nativeIslandEnabled -> "原生岛已关闭"
                !value.vivo -> "当前设备不支持 vivo 原生岛，使用悬浮球"
                !value.notificationsEnabled -> "通知权限或通道未开启，使用悬浮球"
                else -> "等待已支持的聊天应用前台；其他界面不显示入口"
            }
            if(!value.notificationsEnabled && settings.nativeIslandConfirmedBuild!=null) graph.settings.update { it.copy(nativeIslandConfirmedBuild=null) }
            return
        }
        val scope=service.islandScope() ?: run { stop();return }
        val key="${scope.packageName}:${scope.windowId}:${scope.generation}:${value.osBuild}"
        if(session.enter(key,UUID.randomUUID().toString(),SystemClock.uptimeMillis())) {
            sender.end();main.removeCallbacksAndMessages(null);displayed=null
            lease=scope.copy(id=session.id!!)
            desired=NativeIslandEntryPolicy.status(reading,state,isCurrent(state))
            publish();main.postDelayed(watch,2000)
        }
        applyMode()
    }
    private fun applyMode() {
        val build=if(session.confirmed)environment.osBuild else null
        overlay.setEntryMode(NativeIslandEntryPolicy.mode(graph.settings.load().nativeIslandEnabled,build,environment,session.published))
        if(environment.allowed) overlay.showBubble()
    }
    private fun publish() {
        val current=lease ?: return
        environment=service.islandEnvironment()
        if(!environment.allowed || !environment.notificationsEnabled || !graph.settings.load().nativeIslandEnabled) { refreshEligibility(environment);return }
        when(val result=sender.publish(desired,current)) {
            is IslandPublishResult.FAILED -> fail(result.reason+"，已恢复悬浮球")
            IslandPublishResult.POSTED -> {
                session.published=true;displayed=desired;lastPosted=SystemClock.uptimeMillis()
                if(!session.confirmed) diagnostic="等待本次原生岛点击验证（60秒）；普通通知不算成功，球保持可用"
                applyMode()
            }
        }
    }
    fun updateStatus(reading:Boolean,state:BatchState,current:Boolean) {
        this.reading=reading;this.state=state
        desired=NativeIslandEntryPolicy.status(reading,state,current)
        if(lease==null || desired==displayed) return
        main.removeCallbacks(update)
        // Conservative official rate limit until local behavior is verified on target hardware.
        main.postDelayed(update,(lastPosted+10000L-SystemClock.uptimeMillis()).coerceAtLeast(0))
    }
    fun handleNativeTap(leaseId:String):Boolean {
        val current=lease ?: return false
        val env=service.islandEnvironment()
        val scope=service.islandScope() ?: return false
        val valid=NativeIslandEntryPolicy.currentLease(leaseId,current.id,current.packageName,current.windowId,current.generation,scope.packageName,scope.windowId,scope.generation,env.allowed) && sender.enabled() && sender.active()
        if(!valid || !graph.settings.load().nativeIslandEnabled) return false
        val shown=runCatching { overlay.showFromIsland() }.getOrDefault(false)
        if(!NativeIslandEntryPolicy.canConfirm(true,valid,shown,env) || !session.confirm(leaseId,shown)) return false
        environment=env
        graph.settings.update { it.copy(nativeIslandConfirmedBuild=env.osBuild) }
        diagnostic="本次原生专用点击已打开面板；球已隐藏"
        applyMode();return true
    }
    private fun fail(message:String) {
        main.removeCallbacksAndMessages(null);sender.end();lease=null;session.fail()
        graph.settings.update { it.copy(nativeIslandConfirmedBuild=null) }
        diagnostic=message;applyMode()
    }
    fun suspend() { main.removeCallbacksAndMessages(null);sender.end();lease=null;session.stop();displayed=null }
    fun stop() {
        main.removeCallbacksAndMessages(null);sender.end();lease=null;session.stop();displayed=null
        overlay.setEntryMode(OverlayEntryMode.BUBBLE)
    }
    fun destroy() { stop();graph.controller.unlisten(listener) }
}
