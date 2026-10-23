package com.loveadvisor.core.memory

/** Relationship labels require direct evidence or explicit correction, never an affection-score threshold. */
object PersonalRelations {
    fun context(contact:PersonalContact,memories:List<PersonalMemory>):PersonalContact {
        if(contact.relationshipConfirmed)return contact
        val claim=memories.lastOrNull { it.ownerId==contact.id && it.kind==PersonalKind.RELATIONSHIP }?.text ?: return contact
        val label=when {
            claim.contains("只是朋友")->"朋友"
            claim.contains("同事")->"同事"
            claim.contains("同学")->"同学"
            claim.contains("家人")->"家人"
            claim.contains(Regex("老婆|老公|夫妻|恋人"))->"伴侣"
            claim.contains("朋友")->"朋友"
            else->"未知"
        }
        return contact.copy(relationship=if(label=="未知")label else "可能是$label（待确认）")
    }
}
