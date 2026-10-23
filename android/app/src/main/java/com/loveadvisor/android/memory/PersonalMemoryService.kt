package com.loveadvisor.android.memory

import com.google.gson.Gson
import com.loveadvisor.android.storage.AndroidDocumentStore
import com.loveadvisor.android.storage.AppSettings
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.api.*
import com.loveadvisor.core.diagnostics.*
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.model.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PersonalMemoryPersistence(private val documents:AndroidDocumentStore):RealityPersistence {
    private val gson=Gson()
    override fun load():RealityMemoryState?=documents.read("personal-memory-v1")?.let { gson.fromJson(it,RealityMemoryState::class.java).also { value -> require(value.schema==1) { "记忆数据版本不受支持" } } }
    override fun save(state:RealityMemoryState) {
        val json=gson.toJson(state)
        require(json.toByteArray(Charsets.UTF_8).size<=24*1024*1024) { "本地记忆已达容量上限，请在真人感与记忆中管理资料" }
        documents.write("personal-memory-v1",json)
    }
}

/** Foreground generation always takes priority. Learning is optional and bounded, not part of its completion path. */
class PersonalMemoryService(private val settings:AppSettings,documents:AndroidDocumentStore) {
    val repository=RealityMemoryRepository(PersonalMemoryPersistence(documents))
    private val worker=Executors.newSingleThreadExecutor { task -> Thread(task,"personal-memory-learning").apply { priority=Thread.MIN_PRIORITY } }
    private val timer=Executors.newSingleThreadScheduledExecutor { task -> Thread(task,"personal-memory-deadline").apply { isDaemon=true } }
    private val client=PersonalLearningClient(ChatCompletionClient(com.loveadvisor.android.diagnostics.DiagnosticTransport(step=LogStep.MEMORY_LEARN)))
    private val priority=LearningPriority()
    private var dirty=false
    private var scheduled:java.util.concurrent.ScheduledFuture<*>?=null
    @Volatile var status="尚未学习聊天";private set
    // Settings listeners run while the settings store is locked; never take our lock inline there.
    init { settings.subscribe { timer.execute { if(!settings.load().realisticRepliesEnabled)cancelLearning() } } }
    @Synchronized fun beginForeground():()->Unit {
        val release=priority.enterForeground();scheduled?.cancel(false);scheduled=null;dirty=false
        return { release();synchronized(this) { schedule() } }
    }
    @Synchronized fun cancelLearning() { scheduled?.cancel(false);scheduled=null;priority.cancel();dirty=false }
    /** Called on the advisor worker, never from passive accessibility events. */
    fun observe(snapshot:ChatSnapshot) {
        if(!settings.load().realisticRepliesEnabled || !com.loveadvisor.core.capture.ChatApps.supported(snapshot.packageName))return
        val receipt=try { repository.ingest(snapshot) } catch(error:Exception) { status="本地记忆暂未保存，请查看诊断日志；候选仍可生成";Logs.failed(LogStep.MEMORY_SAVE,error);return }
        Logs.event(LogStep.MEMORY_READ,LogState.OK,metrics=mapOf(LogMetric.COUNT to receipt.added.toLong()))
        if(receipt.added>0) synchronized(this) { dirty=true;status="本地已整理${receipt.added}条新消息；候选完成后补充分类";schedule() }
    }
    @Synchronized private fun schedule() {
        if(!dirty || !priority.canStart() || scheduled!=null || !settings.load().realisticRepliesEnabled)return
        scheduled=timer.schedule({ synchronized(this) { scheduled=null;if(priority.canStart() && dirty)startLearning() } },900,TimeUnit.MILLISECONDS)
    }
    /** Worker scheduling does not read memory or touch encrypted storage on the main thread. */
    private fun startLearning() {
        val ticket=priority.start() ?: return
        val cancel=ticket.cancel
        worker.execute {
            val log=Logs.begin(LogStep.MEMORY_LEARN)
            val deadline=timer.schedule({ cancel.cancel() },25,TimeUnit.SECONDS)
            try {
                cancel.check()
                val config=settings.load();val slice=repository.pending()
                if(!config.realisticRepliesEnabled || slice.evidence.isEmpty()) { status="本地整理完成";Logs.end(log,LogState.SKIP);return@execute }
                val proposals=Logs.within(log) { client.classify(config.profile,slice,cancel) }
                cancel.check()
                // Never hold the foreground-priority monitor across encrypted disk I/O.
                if(!priority.current(ticket) || !settings.load().realisticRepliesEnabled)throw java.util.concurrent.CancellationException()
                val saved=repository.apply(slice,proposals)
                status=if(saved)"新消息分类完成；尚有${repository.pending(Int.MAX_VALUE).evidence.size}条原话未分类，下次读取或手动重试可继续" else "资料已被编辑，本次后台结果已丢弃"
                Logs.event(LogStep.MEMORY_SAVE,if(saved)LogState.OK else LogState.SKIP,if(saved)LogReason.NONE else LogReason.RESULT_STALE,metrics=mapOf(LogMetric.COUNT to proposals.size.toLong()))
                Logs.end(log,LogState.OK)
            } catch(_:java.util.concurrent.CancellationException) { status="后台学习已让出或超时；已保留本地原文";Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) }
            catch(error:Exception) { status="后台分类未完成；已保留本地原文，可在记忆页重试";Logs.failed(LogStep.MEMORY_LEARN,error,log);Logs.end(log,LogState.FAIL,LogReason.STEP_FAILED) }
            finally {
                deadline.cancel(false)
                synchronized(this) { if(priority.finish(ticket))dirty=false }
            }
        }
    }
    fun context(snapshot:ChatSnapshot,target:ReplyTarget?):PersonalContext=try {
        val result=PersonalContextBuilder.build(repository.state(),snapshot,target);Logs.event(LogStep.MEMORY_RETRIEVE,LogState.OK,metrics=mapOf(LogMetric.COUNT to result.memories.size.toLong()));result
    } catch(error:Exception) { Logs.failed(LogStep.MEMORY_RETRIEVE,error);PersonalContext() }
    /** An explicit retry is the only extra request after a failed/background-cancelled job. */
    @Synchronized fun retry() { dirty=true;schedule() }
}
