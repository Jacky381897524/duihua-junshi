package com.loveadvisor.core.diagnostics

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor

/** One process owns one journal. Disk access is restricted to tasks submitted to worker. */
class DiagnosticJournal(
    private val location:()->File,
    private val worker:Executor,
    version:String,
    build:Int,
    pid:Int,
    private val now:()->Long=System::currentTimeMillis,
    private val delayPersist:((()->Unit)->Unit)?=null,
    maxEvents:Int=10000,
    maxBytes:Int=4194304,
    retentionMillis:Long=2592000000
) {
    private data class Stored(val record:LogRecord,val json:String,val bytes:Int)
    private data class History(val state:LogHistoryState,val rows:List<Stored> = emptyList(),val evicted:Long=0,val rejected:Long=0,val historyFailures:Long=0,val writeFailures:Long=0,val rewrite:Boolean=false)
    private data class Write(val revision:Long,val rows:List<Stored>,val evicted:Long,val rejected:Long,val historyFailures:Long,val writeFailures:Long)

    private val lock=Any()
    private val owner=Any()
    private val session=UUID.randomUUID().toString().replace("-","")
    private val safeVersion=version.takeIf(::validVersion) ?: "unknown"
    private val safeBuild=build.coerceAtLeast(0)
    private val safePid=pid.coerceAtLeast(0)
    private val eventLimit=maxEvents.coerceIn(1,10000)
    private val byteLimit=maxBytes.coerceIn(512,4194304)
    private val retention=retentionMillis.coerceAtLeast(0)
    private val rows=ArrayDeque<Stored>()
    // Increasing minima of the retained insertion order. Clock rollback is allowed.
    // Each append/removal updates these candidates in amortized O(1).
    private val expiryCandidates=ArrayDeque<Stored>()
    private var rowBytes=0
    private var sequence=0L
    private var revision=0L
    private var persistedRevision=-1L
    private var loaded=false
    private var historyState=LogHistoryState.LOADING
    private var evicted=0L
    private var rejected=0L
    private var historyFailures=0L
    private var writeFailures=0L
    private var scheduled=false
    private var writing=false
    private var requested=false

    init {
        try { worker.execute { load() } }
        catch(_:Throwable) { synchronized(lock) { loaded=true;historyState=LogHistoryState.UNAVAILABLE;historyFailures=1 } }
    }

    fun event(step:LogStep,state:LogState=LogState.STEP,reason:LogReason=LogReason.NONE,operation:LogOperation?=null,metrics:Map<LogMetric,Long> = emptyMap(),fault:LogFault?=null) {
        if(operation!=null && operation.owner!==owner) return
        synchronized(lock) { appendLocked(step,state,reason,operation,metrics,fault) }
        schedule()
    }

    fun begin(step:LogStep,parent:LogOperation?=null,metrics:Map<LogMetric,Long> = emptyMap()):LogOperation {
        val operation=synchronized(lock) {
            val op=LogOperation(owner,"$session:o${++sequence}",parent?.takeIf { it.owner===owner }?.id,step,clock())
            appendLocked(step,LogState.START,LogReason.NONE,op,metrics,null)
            op
        }
        schedule()
        return operation
    }

    fun end(operation:LogOperation?,state:LogState,reason:LogReason=LogReason.NONE,fault:LogFault?=null,metrics:Map<LogMetric,Long> = emptyMap()) {
        if(operation==null || operation.owner!==owner || state !in terminal || !operation.ended.compareAndSet(false,true)) return
        val duration=(clock()-operation.startedAt).coerceIn(0,86_400_000)
        synchronized(lock) { appendLocked(operation.step,state,reason,operation,metrics+(LogMetric.DURATION_MS to duration),fault) }
        schedule()
    }

    private fun appendLocked(step:LogStep,state:LogState,reason:LogReason,operation:LogOperation?,metrics:Map<LogMetric,Long>,fault:LogFault?) {
        val clean=Collections.unmodifiableMap(metrics.mapValues { (key,value) -> metric(key,value) })
        val safeFault=fault?.let(LogFault::sanitized)
        val record=LogRecord("$session:e${++sequence}",clock(),session,safeVersion,safeBuild,safePid,operation?.id,operation?.parentId,step,state,reason,clean,safeFault)
        val stored=store(record)
        retainLocked(stored);revision++
        trimLocked()
    }

    private fun schedule() {
        val queue=synchronized(lock) { if(scheduled) false else { scheduled=true;true } }
        if(!queue) return
        try {
            val persist={ synchronized(lock) { scheduled=false };flush() }
            if(delayPersist!=null) delayPersist.invoke(persist) else worker.execute { persist() }
        } catch(_:Throwable) { synchronized(lock) { scheduled=false;writeFailures=add(writeFailures,1);revision++ } }
    }

    /** Queues a write; it never waits for loading or for a filesystem operation. */
    fun flush() {
        val queue=synchronized(lock) {
            requested=true
            if(!loaded || writing) false else { writing=true;requested=false;true }
        }
        if(!queue) return
        try { worker.execute { persist() } }
        catch(_:Throwable) { synchronized(lock) { writing=false;writeFailures=add(writeFailures,1);revision++ } }
    }

    fun snapshot():LogSnapshot {
        val (result,changed)=synchronized(lock) {
            val oldRevision=revision
            trimLocked()
            val events=rows.map { it.record }
            LogSnapshot(events,historyState,evicted,rejected,historyFailures,writeFailures,persistedRevision==revision && loaded,incomplete(events).size) to (oldRevision!=revision)
        }
        if(changed) schedule()
        return result
    }

    fun text(limit:Int=200):String {
        val snapshot=snapshot()
        val incomplete=incomplete(snapshot.events)
        val history=when(snapshot.historyState) {
            LogHistoryState.LOADING -> "加载中"
            LogHistoryState.EMPTY -> "没有历史文件"
            LogHistoryState.LOADED -> "已加载"
            LogHistoryState.CORRUPT -> "历史损坏，已拒绝"
            LogHistoryState.OVERSIZED -> "历史超出容量，已拒绝"
            LogHistoryState.UNAVAILABLE -> "历史读取失败"
        }
        return buildString {
            append("诊断日志 · 当前版本 ").append(safeVersion).append(" / ").append(safeBuild).append(" · PID ").append(safePid).append('\n')
            append("历史：").append(history).append("；保留 ").append(snapshot.events.size).append("；淘汰 ").append(snapshot.evicted).append("；拒绝记录 ").append(snapshot.rejected).append('\n')
            append("持久化：").append(if(snapshot.persisted) "已写入" else "待写入或写入未确认").append("；写入失败 ").append(snapshot.writeFailures).append("；读取失败 ").append(snapshot.historyFailures).append('\n')
            append("上次未完成 INCOMPLETE_PREVIOUS：").append(incomplete.size).append("；缺少结束回调的原因未知，不能据此确定崩溃。窗口外或未写入的记录可能不可用。\n")
            val date=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z",java.util.Locale.ROOT)
            for(row in snapshot.events.takeLast(limit.coerceIn(0,10000))) {
                append(date.format(java.util.Date(row.timestampMillis))).append(" [").append(row.timestampMillis).append("] · ").append(row.version).append('/').append(row.build).append(" PID=").append(row.pid).append(" session=").append(row.session)
                append(" · 步骤=").append(row.step.name).append(" 状态=").append(row.state.name).append(" 原因=").append(row.reason.name)
                row.operationId?.let { append(" op=").append(it) }
                row.parentOperationId?.let { append(" parent=").append(it) }
                if(row.operationId in incomplete && row.state==LogState.START) append(" INCOMPLETE_PREVIOUS")
                for((key,value) in row.metrics) append(' ').append(key.name).append('=').append(value)
                row.fault?.let { fault ->
                    append(" 异常分类=").append(fault.reason.name)
                    fault.httpStatus?.let { append(" HTTP_STATUS=").append(it) }
                    append(" 类型=").append(fault.types.joinToString(" <- "))
                    for(frame in fault.frames) append("\n  at ").append(frame)
                }
                append('\n')
            }
        }
    }

    private fun incomplete(events:List<LogRecord>):Set<String> {
        val open=LinkedHashMap<String,LogStep>()
        for(row in events) {
            val id=row.operationId ?: continue
            if(row.session==session) continue
            if(row.state==LogState.START) open[id]=row.step
        }
        // Wall-clock corrections can place a completion before its start in the timeline.
        for(row in events) {
            val id=row.operationId ?: continue
            if(row.state in terminal && open[id]==row.step) open.remove(id)
        }
        return open.keys.toSet()
    }

    private fun load() {
        val history=readHistory()
        val needsWrite=synchronized(lock) {
            val pending=rows.toList()
            rows.clear();expiryCandidates.clear();rowBytes=0
            val combined=(history.rows+pending).sortedBy { it.record.timestampMillis }
            for(row in combined) retainLocked(row)
            evicted=add(evicted,history.evicted);rejected=add(rejected,history.rejected)
            historyFailures=add(historyFailures,history.historyFailures);writeFailures=add(writeFailures,history.writeFailures)
            historyState=history.state;loaded=true;revision++
            val oldEvicted=evicted
            trimLocked()
            val dirty=pending.isNotEmpty() || history.rewrite || oldEvicted!=evicted || requested
            if(!dirty && history.state==LogHistoryState.LOADED) persistedRevision=revision
            dirty
        }
        if(needsWrite) flush()
    }

    private fun readHistory():History {
        val bytes=try {
            val file=location()
            if(!file.exists()) return History(LogHistoryState.EMPTY)
            if(file.length()>byteLimit) return History(LogHistoryState.OVERSIZED,historyFailures=1,rewrite=true)
            val raw=file.inputStream().use { input ->
                val buffer=ByteArray(8192)
                val output=ByteArrayOutputStream()
                while(output.size()<=byteLimit) {
                    val count=input.read(buffer,0,minOf(buffer.size,byteLimit+1-output.size()))
                    if(count<0) break
                    if(count==0) continue
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            if(raw.size>byteLimit) return History(LogHistoryState.OVERSIZED,historyFailures=1,rewrite=true)
            raw
        } catch(_:Throwable) { return History(LogHistoryState.UNAVAILABLE,historyFailures=1) }
        return try {
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val root=JsonParser.parseString(text).asJsonObject
            require(root.keySet()==rootKeys && number(root["schema"])==1L)
            val oldEvicted=counter(root,"evicted");val oldRejected=counter(root,"rejected")
            val oldRead=counter(root,"historyFailures");val oldWrite=counter(root,"writeFailures")
            val entries=root["events"].asJsonArray
            val clean=ArrayList<Stored>()
            var dropped=0L
            val ids=HashSet<String>()
            for(entry in entries) {
                val record=try { parseRecord(entry.asJsonObject) } catch(_:Throwable) { null }
                if(record==null || !ids.add(record.id)) dropped++ else clean.add(store(record))
            }
            History(LogHistoryState.LOADED,clean,oldEvicted,add(oldRejected,dropped),oldRead,oldWrite,dropped>0)
        } catch(_:Throwable) { History(LogHistoryState.CORRUPT,historyFailures=1,rewrite=true) }
    }

    private fun parseRecord(value:JsonObject):LogRecord {
        require(value.keySet().containsAll(requiredRowKeys) && rowKeys.containsAll(value.keySet()))
        val session=string(value,"session").also { require(sessionToken.matches(it)) }
        val id=string(value,"id").also { require(it.startsWith("$session:e") && eventToken.matches(it)) }
        fun operation(key:String):String?=value[key]?.let { element -> string(element).also { require(it.startsWith("$session:o") && operationToken.matches(it)) } }
        val timestamp=number(value["time"]).also { require(it in 0..253402300799999) }
        val version=string(value,"version").also { require(validVersion(it)) }
        val build=number(value["build"]).also { require(it in 0..Int.MAX_VALUE) }.toInt()
        val pid=number(value["pid"]).also { require(it in 0..Int.MAX_VALUE) }.toInt()
        val metrics=LinkedHashMap<LogMetric,Long>()
        for((key,element) in value["metrics"].asJsonObject.entrySet()) {
            val name=LogMetric.valueOf(key);val number=number(element)
            require(metric(name,number)==number);metrics[name]=number
        }
        val fault=value["fault"]?.let { element ->
            val obj=element.asJsonObject
            require(obj.keySet().containsAll(setOf("reason","types","frames")) && faultKeys.containsAll(obj.keySet()))
            val types=obj["types"].asJsonArray.map(::string)
            val frames=obj["frames"].asJsonArray.map(::string)
            require(types.size<=4 && types.all(LogFault::validType) && frames.size<=8 && frames.all(LogFault::validFrame))
            val status=obj["httpStatus"]?.let { number(it).also { code -> require(code in 100..599) }.toInt() }
            LogFault.sanitized(LogFault(LogReason.valueOf(string(obj,"reason")),types,frames,status))
        }
        return LogRecord(id,timestamp,session,version,build,pid,operation("operation"),operation("parent"),LogStep.valueOf(string(value,"step")),LogState.valueOf(string(value,"state")),LogReason.valueOf(string(value,"reason")),Collections.unmodifiableMap(metrics),fault)
    }

    private fun persist() {
        val write=synchronized(lock) {
            trimLocked()
            Write(revision,rows.toList(),evicted,rejected,historyFailures,writeFailures)
        }
        var temp:File?=null
        val success=try {
            val file=location().absoluteFile
            val parent=file.parentFile ?: throw IllegalStateException()
            if(!parent.isDirectory && !parent.mkdirs()) throw IllegalStateException()
            temp=File(parent,"${file.name}.$session.tmp")
            val prefix="{\"schema\":1,\"evicted\":${write.evicted},\"rejected\":${write.rejected},\"historyFailures\":${write.historyFailures},\"writeFailures\":${write.writeFailures},\"events\":["
            val bytes=(prefix+write.rows.joinToString(",") { it.json }+"]}").toByteArray(Charsets.UTF_8)
            check(bytes.size<=byteLimit)
            FileOutputStream(temp).use { output -> output.write(bytes);output.fd.sync() }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            true
        } catch(_:Throwable) { false }
        finally { try { temp?.delete() } catch(_:Throwable) {} }
        val again=synchronized(lock) {
            writing=false
            if(success) persistedRevision=write.revision else { writeFailures=add(writeFailures,1);revision++ }
            val follow=success && (revision!=write.revision || requested)
            requested=false
            follow
        }
        if(again) flush()
    }

    private fun trimLocked() {
        val time=clock()
        val cutoff=if(time<=retention) 0 else time-retention
        if((expiryCandidates.peekFirst()?.record?.timestampMillis ?: Long.MAX_VALUE)<cutoff) {
            // Expiry can remove middle rows after a wall-clock correction. Rebuild minima
            // during this necessary scan so previously dominated timestamps remain known.
            expiryCandidates.clear()
            val iterator=rows.iterator()
            while(iterator.hasNext()) {
                val entry=iterator.next()
                if(entry.record.timestampMillis<cutoff) { rowBytes-=entry.bytes;iterator.remove();evicted=add(evicted,1);revision++ }
                else trackExpiryLocked(entry)
            }
        }
        // Reserve space for the fixed envelope and counters even when all counters reach Long.MAX_VALUE.
        while(rows.isNotEmpty() && (rows.size>eventLimit || rowBytes>byteLimit-512)) {
            val removed=rows.removeFirst()
            if(expiryCandidates.peekFirst()===removed) expiryCandidates.removeFirst()
            rowBytes-=removed.bytes;evicted=add(evicted,1);revision++
        }
    }

    private fun retainLocked(entry:Stored) {
        rows.addLast(entry);rowBytes+=entry.bytes
        trackExpiryLocked(entry)
    }

    private fun trackExpiryLocked(entry:Stored) {
        while(expiryCandidates.isNotEmpty() && expiryCandidates.peekLast().record.timestampMillis>=entry.record.timestampMillis) expiryCandidates.removeLast()
        expiryCandidates.addLast(entry)
    }

    private fun clock():Long=(try { now() } catch(_:Throwable) { 0L }).coerceIn(0,253402300799999)

    private fun store(row:LogRecord):Stored {
        val obj=JsonObject()
        obj.addProperty("id",row.id);obj.addProperty("time",row.timestampMillis);obj.addProperty("session",row.session)
        obj.addProperty("version",row.version);obj.addProperty("build",row.build);obj.addProperty("pid",row.pid)
        row.operationId?.let { obj.addProperty("operation",it) };row.parentOperationId?.let { obj.addProperty("parent",it) }
        obj.addProperty("step",row.step.name);obj.addProperty("state",row.state.name);obj.addProperty("reason",row.reason.name)
        val metrics=JsonObject();for((key,value) in row.metrics) metrics.addProperty(key.name,value);obj.add("metrics",metrics)
        row.fault?.let { fault ->
            val detail=JsonObject();detail.addProperty("reason",fault.reason.name)
            val types=JsonArray();fault.types.forEach(types::add);detail.add("types",types)
            val frames=JsonArray();fault.frames.forEach(frames::add);detail.add("frames",frames)
            fault.httpStatus?.let { detail.addProperty("httpStatus",it) };obj.add("fault",detail)
        }
        val json=obj.toString()
        return Stored(row,json,json.toByteArray(Charsets.UTF_8).size+1)
    }

    companion object {
        private val terminal=setOf(LogState.OK,LogState.FAIL,LogState.CANCELLED,LogState.SKIP,LogState.CRASH)
        private val versionToken=Regex("V?[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5}")
        private val sessionToken=Regex("[0-9a-f]{32}")
        private val eventToken=Regex("[0-9a-f]{32}:e[1-9][0-9]{0,18}")
        private val operationToken=Regex("[0-9a-f]{32}:o[1-9][0-9]{0,18}")
        private val integerToken=Regex("-?(?:0|[1-9][0-9]{0,18})")
        private val rootKeys=setOf("schema","evicted","rejected","historyFailures","writeFailures","events")
        private val requiredRowKeys=setOf("id","time","session","version","build","pid","step","state","reason","metrics")
        private val rowKeys=requiredRowKeys+setOf("operation","parent","fault")
        private val faultKeys=setOf("reason","types","frames","httpStatus")
        private fun validVersion(value:String)=value=="unknown" || versionToken.matches(value)
        private fun string(value:JsonElement):String { require(value.isJsonPrimitive && value.asJsonPrimitive.isString);return value.asString }
        private fun string(obj:JsonObject,key:String)=string(obj[key])
        private fun number(value:JsonElement):Long { require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && integerToken.matches(value.asString));return value.asString.toLong() }
        private fun counter(obj:JsonObject,key:String)=number(obj[key]).also { require(it>=0) }
        private fun add(a:Long,b:Long)=if(Long.MAX_VALUE-a<b) Long.MAX_VALUE else a+b
        private fun metric(key:LogMetric,value:Long):Long=when(key) {
            LogMetric.HAS_INPUT,LogMetric.HAS_DRAFT,LogMetric.CACHED,LogMetric.ENABLED,LogMetric.READY,LogMetric.TRANSIENT_FOCUSED,LogMetric.EXTERNAL_FOCUSED,LogMetric.EDITOR_ACTIVE -> value.coerceIn(0,1)
            LogMetric.DURATION_MS -> value.coerceIn(0,86_400_000)
            LogMetric.WIDTH,LogMetric.HEIGHT -> value.coerceIn(0,32768)
            LogMetric.HTTP_STATUS -> value.coerceIn(0,599)
            LogMetric.SCREENSHOT_CODE -> value.coerceIn(-1,1_000_000_000)
            LogMetric.EVENT_AGE_MS -> value.coerceIn(0,86_400_000)
            LogMetric.LEFT,LogMetric.TOP -> value.coerceIn(-32768,32768)
            else -> value.coerceIn(0,1_000_000_000)
        }
    }
}
