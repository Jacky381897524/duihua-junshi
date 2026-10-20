package com.loveadvisor.core.startup

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor

/** Fixed metadata only. No caller-provided diagnostic text, settings, or Intent contents. */
enum class StartupStage {
    PROCESS_ATTACHED, APPLICATION_CREATED, MAIN_CREATE_ENTERED, MAIN_CREATED, MAIN_RESUMED,
    FIRST_DRAW, GRAPH_BEGIN, GRAPH_READY, SETTINGS_READ_BEGIN, SETTINGS_READ_READY,
    STARTUP_READY, STARTUP_SLOW, STARTUP_FAILED
}

data class StartupTraceEvent(val stage:StartupStage,val elapsedMs:Long)
data class StartupAttempt(val version:String,val pid:Int,val startedAtMillis:Long,val events:List<StartupTraceEvent>)
data class StartupTraceSnapshot(
    val current:StartupAttempt,
    val previous:StartupAttempt?=null,
    val historyLoaded:Boolean=false,
    val persistenceAvailable:Boolean?=null
)

/**
 * First occurrence of each process milestone, with one previous process retained.
 * The supplied serial worker owns ALL path resolution and disk access. A stopped
 * process may lose its queued last write; absence of a marker is not proof of a hang.
 */
class StartupTrace(
    location:()->File,
    private val worker:Executor,
    version:String,
    pid:Int,
    startedAtMillis:Long,
    private val elapsedRealtime:()->Long
) {
    private val file by lazy(location)
    private val began=elapsedRealtime()
    @Volatile var snapshot=StartupTraceSnapshot(StartupAttempt(
        safeVersion(version),pid,startedAtMillis,listOf(StartupTraceEvent(StartupStage.PROCESS_ATTACHED,0))
    ));private set

    init {
        enqueue {
            val previous=runCatching { readPrevious() }.getOrNull()
            synchronized(this) { snapshot=snapshot.copy(previous=previous,historyLoaded=true) }
            persist()
        }
    }

    @Synchronized fun mark(stage:StartupStage):Boolean {
        if(snapshot.current.events.any { it.stage==stage })return false
        val event=StartupTraceEvent(stage,(elapsedRealtime()-began).coerceAtLeast(0))
        snapshot=snapshot.copy(current=snapshot.current.copy(events=snapshot.current.events+event))
        enqueue { persist() }
        return true
    }

    private fun enqueue(operation:()->Unit) {
        try { worker.execute(operation) }
        catch(_:Exception) { synchronized(this) { snapshot=snapshot.copy(historyLoaded=true,persistenceAvailable=false) } }
    }

    private fun readPrevious():StartupAttempt? {
        if(!file.isFile || file.length() !in 1..MAX_BYTES)return null
        val root=JsonParser.parseString(file.readText()).asJsonObject
        val value=root.getAsJsonObject("current") ?: return null
        val version=value.get("version").asString
        require(version=="unknown" || safeVersion(version)==version)
        val pid=value.get("pid").asInt;require(pid>0)
        val started=value.get("startedAtMillis").asLong;require(started>=0)
        val rows=value.getAsJsonArray("events");require(rows.size() in 1..StartupStage.values().size)
        val events=rows.map { row ->
            val item=row.asJsonObject
            val stage=StartupStage.valueOf(item.get("stage").asString)
            val elapsed=item.get("elapsedMs").asLong;require(elapsed>=0)
            StartupTraceEvent(stage,elapsed)
        }
        require(events.first()==StartupTraceEvent(StartupStage.PROCESS_ATTACHED,0))
        require(events.map { it.stage }.distinct().size==events.size)
        return StartupAttempt(version,pid,started,events)
    }

    private fun persist() {
        val available=runCatching {
            val value=snapshot
            val text=Gson().toJson(mapOf("current" to value.current,"previous" to value.previous))
            val bytes=text.toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_BYTES)
            val parent=file.parentFile ?: error("No trace directory")
            require(parent.isDirectory || parent.mkdirs())
            val pending=File(parent,file.name+".pending")
            pending.outputStream().use { it.write(bytes) }
            try { Files.move(pending.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch(_:AtomicMoveNotSupportedException) { Files.move(pending.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        }.isSuccess
        synchronized(this) { snapshot=snapshot.copy(persistenceAvailable=available) }
    }

    companion object {
        private const val MAX_BYTES=8192L
        private fun safeVersion(value:String)=value.takeIf {
            it.length<=32 && Regex("V?\\d+(?:\\.\\d+){1,3}(?:[-+][A-Za-z0-9.]{1,12})?").matches(it)
        } ?: "unknown"
    }
}
