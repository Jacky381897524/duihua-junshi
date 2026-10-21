package com.loveadvisor.android.island

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import com.loveadvisor.android.R
import com.loveadvisor.android.ui.DiagnosticsActivity

data class IslandLease(val id:String,val packageName:String,val windowId:Int,val generation:Long)
sealed class IslandPublishResult {
    data object POSTED:IslandPublishResult()
    data class FAILED(val reason:String):IslandPublishResult()
}

/** Experimental vendor extension; notify success does not prove native display.
 * https://help.aliyun.com/zh/document_detail/3030718.html (local notification example).
 * Our own scene does not impersonate an approved train/delivery category.
 */
class NativeIslandNotification(private val context:Context) {
    private val manager=context.getSystemService(NotificationManager::class.java)
    private var nativeIntent:PendingIntent?=null
    private var leaseId:String?=null
    private var last:Notification?=null
    fun enabled():Boolean=manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    fun build(status:String,lease:IslandLease,operation:Int):Notification {
        if(leaseId!=lease.id) {
            nativeIntent?.cancel()
            nativeIntent=PendingIntent.getBroadcast(context,0,Intent(context,NativeIslandReceiver::class.java).apply {
                action=ACTION_OPEN;data=Uri.parse("loveadvisor-island://open/${Uri.encode(lease.id)}")
                putExtra(EXTRA_LEASE,lease.id)
            },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            leaseId=lease.id
        }
        val ordinary=PendingIntent.getActivity(context,0,Intent(context,DiagnosticsActivity::class.java).apply {
            action="${context.packageName}.ISLAND_DIAGNOSTICS"
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val label=status.takeIf { it in STATUSES } ?: "待读取"
        val extras=Bundle().apply {
            putInt("notification.superx.operation",operation);putBoolean("notification.superx.showNotify",true)
            putInt("notification.superx.template",1);putString("notification.superx.scene","CHAT_ASSISTANT")
            putParcelable("notification.superx.clickResp",nativeIntent)
            putBundle("notification.superx.baseInfos",Bundle().apply {
                putParcelable("notification.superx.baseInfos.icon",Icon.createWithResource(context,R.drawable.advisor_logo_new))
                putCharSequence("notification.superx.baseInfos.title","对话军师")
                putCharSequence("notification.superx.baseInfos.content",label)
            })
        }
        return Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_island_notification)
            .setContentTitle("对话军师 · 原生岛测试").setContentText(label).setContentIntent(ordinary)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(Notification.CATEGORY_STATUS).addExtras(extras).build()
    }
    fun publish(status:String,lease:IslandLease):IslandPublishResult=try {
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"原生岛入口（测试）",NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null,null);enableVibration(false);setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_SECRET
        })
        if(!enabled()) IslandPublishResult.FAILED("通知权限或原生岛通知通道未开启") else {
            val notification=build(status,lease,if(last==null)0 else 1)
            manager.notify(ID,notification);last=notification;IslandPublishResult.POSTED
        }
    } catch(error:SecurityException) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.OVERLAY_SHOW,error);IslandPublishResult.FAILED("系统拒绝通知权限") }
      catch(error:RuntimeException) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.OVERLAY_SHOW,error);IslandPublishResult.FAILED("系统未接受原生岛通知") }
    fun active():Boolean=runCatching { manager.activeNotifications.any { it.id==ID } }.getOrDefault(false)
    fun end() {
        last?.let { n -> runCatching {
            val extras=Bundle(n.extras).apply { putInt("notification.superx.operation",2) }
            manager.notify(ID,Notification.Builder.recoverBuilder(context,n).setExtras(extras).build())
        } }
        runCatching { manager.cancel(ID) };last=null
        nativeIntent?.cancel();nativeIntent=null;leaseId=null
    }
    companion object {
        const val CHANNEL="native_island_test_v1"
        const val ID=14014
        const val ACTION_OPEN="com.loveadvisor.android.NATIVE_ISLAND_OPEN"
        const val EXTRA_LEASE="nativeIslandLease"
        private val STATUSES=setOf("待读取","分析中","6条候选","读取失败")
    }
}
