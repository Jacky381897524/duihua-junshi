package com.loveadvisor.core.memory

/** Relationship labels require direct evidence or explicit correction, never an affection-score threshold. */
object PersonalRelations {
    fun contradicts(label:String,text:String):Boolean {
        val words=when(label) { "伴侣"->"伴侣|情侣|夫妻|恋人|老婆|老公";else->Regex.escape(label) }
        return text.contains(Regex("(?:不是|不算|并非|不再是|还不是).{0,4}(?:$words)")) || label=="伴侣" && text.contains(Regex("已经分手|分手了|离婚了|没有在一起"))
    }
    fun explicitLabel(claim:String):String? {
        if(claim.contains("只是朋友"))return "朋友"
        if(claim.contains(Regex("不是|并非|不算|还没|没有在一起|已经分手|分手了|离婚|不再是")))return null
        return when {
            claim.contains("同事")->"同事"
            claim.contains("同学")->"同学"
            claim.contains("家人")->"家人"
            claim.contains(Regex("老婆|老公|媳妇|夫妻|恋人|结婚"))->"伴侣"
            claim.contains("朋友")->"朋友"
            else->null
        }
    }
    fun context(contact:PersonalContact,memories:List<PersonalMemory>):PersonalContact {
        if(contact.relationshipConfirmed)return contact
        val memory=memories.filter { it.ownerId==contact.id && it.kind==PersonalKind.RELATIONSHIP }.sortedWith(compareByDescending<PersonalMemory> { it.locked || it.status==EvidenceStatus.CONFIRMED }.thenByDescending { it.interpretation==null && explicitLabel(it.text)!=null }.thenByDescending { it.updatedAt }).firstOrNull() ?: return contact
        val claim=memory.text
        if(memory.locked || memory.status==EvidenceStatus.CONFIRMED)return contact.copy(relationship=explicitLabel(claim) ?: claim.take(80),relationshipConfirmed=true)
        val label=memory.interpretation ?: explicitLabel(claim) ?: "未知"
        return contact.copy(relationship=if(label=="未知")label else "可能是$label（待确认）")
    }
}
