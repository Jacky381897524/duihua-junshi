package com.loveadvisor.android.memory
import com.google.gson.Gson
import com.loveadvisor.android.storage.AndroidDocumentStore
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.knowledge.KnowledgeDocument
data class SavedChat(val objectId:String,val messages:List<ChatMessage>,val createdAt:Long=System.currentTimeMillis())
data class ProfileData(val self:RelationshipProfile=RelationshipProfile("self","本人"),val objects:List<RelationshipProfile> = emptyList(),val notes:List<KnowledgeDocument> = emptyList(),val history:List<SavedChat> = emptyList())
class ProfileStore(private val store:AndroidDocumentStore) {
    private val gson=Gson()
    @Synchronized fun load():ProfileData=store.read("profiles")?.let { gson.fromJson(it,ProfileData::class.java) } ?: ProfileData()
    @Synchronized fun save(data:ProfileData)=store.write("profiles",gson.toJson(data))
    fun find(id:String?)=load().objects.find { it.id==id }
    @Synchronized fun withExisting(id:String?,operation:(RelationshipProfile)->Unit) { load().objects.find { it.id==id }?.let(operation) }
    @Synchronized fun put(profile:RelationshipProfile) {
        val data=load(); save(if(profile.id=="self") data.copy(self=profile) else data.copy(objects=data.objects.filter { it.id!=profile.id }+profile))
    }
    @Synchronized fun delete(id:String) { val d=load(); save(d.copy(objects=d.objects.filter { it.id!=id },history=d.history.filter { it.objectId!=id })) }
    @Synchronized fun rememberChat(id:String,snapshot:ChatSnapshot) {
        val d=load(); if(d.objects.none { it.id==id }) return
        val previous=d.history.filter { it.objectId==id }
        if(previous.lastOrNull()?.messages==snapshot.messages) return
        save(d.copy(history=d.history.filter { it.objectId!=id }+(previous+SavedChat(id,snapshot.messages.takeLast(80))).takeLast(30)))
    }
}
class EncryptedMemoryPersistence(private val store:AndroidDocumentStore):MemoryPersistence {
    private val gson=Gson()
    override fun load():MemoryState=store.read("memory")?.let { gson.fromJson(it,MemoryState::class.java) } ?: MemoryState()
    override fun save(state:MemoryState)=store.write("memory",gson.toJson(state))
}
