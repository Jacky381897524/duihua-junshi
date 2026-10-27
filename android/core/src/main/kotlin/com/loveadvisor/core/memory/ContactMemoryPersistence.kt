package com.loveadvisor.core.memory

import com.google.gson.Gson

/** Android supplies authenticated encrypted documents; no plaintext database or external files. */
interface PersonalDocumentStore {
    fun read(name:String):String?
    fun write(name:String,text:String)
    fun remove(name:String)
    fun names():Set<String>
}

/** Immutable per-object records, committed by one atomic encrypted index replacement. */
class ContactMemoryPersistence(private val store:PersonalDocumentStore,private val onCleanupFailure:(Exception)->Unit={}):RealityPersistence {
    private val gson=Gson()
    private data class Order(val evidence:List<String>,val memories:List<String>,val signals:List<String>,val corrections:List<String>)
    private data class Partition(val refs:List<String>,val order:Order)
    private data class Index(val format:Int=2,val state:RealityMemoryState,val current:Partition,val previous:Partition?)
    override fun load():RealityMemoryState? {
        val index=store.read("personal-memory-index")
        if(index==null)return store.read("personal-memory-v1")?.let { RealityMemoryCodec.decode(it).also(::save) }
        val saved=gson.fromJson(index,Index::class.java);require(saved.format==2)
        val state=RealityMemoryCodec.decode(com.google.gson.JsonParser.parseString(index).asJsonObject["state"].toString())
        val restored=state.copy(data=join(state.data,saved.current),undo=saved.previous?.let { join(requireNotNull(state.undo),it) })
        cleanup((saved.current.refs+saved.previous?.refs.orEmpty()).toSet())
        return restored
    }
    private fun join(meta:PersonalData,partition:Partition):PersonalData {
        val parts=partition.refs.map { ref -> require(Regex("pm-[a-f0-9]{32}").matches(ref));gson.fromJson(requireNotNull(store.read(ref)) { "对象资料缺失" },PersonalData::class.java) }
        fun <T> ordered(values:List<T>,ids:List<String>,key:(T)->String):List<T> { val byId=values.associateBy(key);return ids.map { requireNotNull(byId[it]) { "对象资料索引不完整" } } }
        return meta.copy(contacts=parts.flatMap { it.contacts },evidence=ordered(parts.flatMap { it.evidence },partition.order.evidence) { it.id },memories=ordered(parts.flatMap { it.memories },partition.order.memories) { it.id },signals=ordered(parts.flatMap { it.signals },partition.order.signals) { it.sourceId },corrections=ordered(parts.flatMap { it.corrections },partition.order.corrections) { it.id })
    }
    override fun save(state:RealityMemoryState) {
        require(state.schema==1)
        val needed=mutableSetOf<String>()
        fun partition(data:PersonalData):Partition {
            val ids=data.contacts.map { it.id }.toSet()
            val records=data.contacts.map { c -> PersonalData(contacts=listOf(c),evidence=data.evidence.filter { it.peerId==c.id },memories=data.memories.filter { it.ownerId==c.id },signals=data.signals.filter { it.contactId==c.id },corrections=data.corrections.filter { it.peerId==c.id }) }+
                PersonalData(evidence=data.evidence.filter { it.peerId !in ids },memories=data.memories.filter { it.ownerId !in ids },signals=data.signals.filter { it.contactId !in ids },corrections=data.corrections.filter { it.peerId !in ids })
            val refs=records.map { record ->
                val json=gson.toJson(record);require(json.toByteArray(Charsets.UTF_8).size<=24*1024*1024) { "该对象资料已达容量上限，请在记忆页管理" }
                val ref="pm-"+PersonalIdentity.digest(json).take(32)
                if(store.read(ref)==null)store.write(ref,json)
                needed.add(ref);ref
            }
            return Partition(refs,Order(data.evidence.map { it.id },data.memories.map { it.id },data.signals.map { it.sourceId },data.corrections.map { it.id }))
        }
        fun meta(data:PersonalData)=data.copy(contacts=emptyList(),evidence=emptyList(),memories=emptyList(),signals=emptyList(),corrections=emptyList())
        val current=partition(state.data);val previous=state.undo?.let(::partition)
        val index=Index(state=state.copy(data=meta(state.data),undo=state.undo?.let(::meta)),current=current,previous=previous)
        store.write("personal-memory-index",gson.toJson(index))
        // Cleanup follows the commit. A failed index write leaves the complete old snapshot recoverable.
        cleanup(needed)
    }
    private fun cleanup(needed:Set<String>) {
        // Cleanup is after the durable commit: report it, but do not report a committed save as failed.
        // The next successful load/save retries, including uncommitted orphan records after interruption.
        try {
            val names=store.names()
            if("personal-memory-v1" in names)store.remove("personal-memory-v1")
            names.filter { Regex("pm-[a-f0-9]{32}").matches(it) && it !in needed }.forEach(store::remove)
        } catch(error:Exception) { onCleanupFailure(error) }
    }
}

object PersonalConversation {
    fun evidence(data:PersonalData,id:String):List<PersonalEvidence> {
        if(id==PersonalIdentity.SELF)return data.evidence.filter { it.ownerId==id }
        val c=data.contacts.find { it.id==id }
        val ids=if(c?.groupRoom==true)data.contacts.filter { it.platform==c.platform && it.group==c.group }.map { it.id }.toSet() else setOf(id)
        return data.evidence.filter { it.peerId in ids }
    }
}
