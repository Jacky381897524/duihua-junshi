package com.loveadvisor.core.capture
import com.loveadvisor.core.api.VisionConversation
import com.loveadvisor.core.model.*
object VisualMediaMerge {
    fun hasMedia(window:WindowSnapshot):Boolean {
        val v=window.viewport
        val bottom=window.nodes.filter { it.editable }.minOfOrNull { it.bounds.top } ?: (v.top+v.height*.91).toInt()
        return window.nodes.any { n ->
            val description=n.contentDescription.orEmpty(); val b=n.bounds
            !n.editable && n.roleHint!="avatar" && !description.contains("头像") && b.top>=v.top+v.height*.13 && b.bottom<=bottom &&
                (description.contains("表情") || description.contains("图片") || n.className.contains("ImageView") && b.width>v.width*.20 && b.height>v.height*.035)
        }
    }
    /** Native text wins; vision contributes media only, aligned to the nearest native text rows. */
    fun merge(original:ChatSnapshot,visual:VisionConversation,window:WindowSnapshot?=null):ChatSnapshot {
        var last=-1
        val native=original.copy(readAtMillis=visual.readAtMillis.takeIf { it>0 } ?: original.readAtMillis,messages=original.messages.map { message ->
            val at=(last+1 until visual.messages.size).firstOrNull { visual.messages[it].speaker==message.speaker && visual.messages[it].text==message.text }
            if(at==null)message else { last=at;message.copy(time=message.time?.takeIf { it.epochMillis!=null } ?: visual.messages[at].time ?: message.time) }
        })
        require(native.identity?.displayTitle==visual.title)
        if(window!=null && visual.mediaIds.all { it in visual.positions }) {
            val used=mutableSetOf<Int>()
            val nativeRows=native.messages.map { message ->
                val index=window.nodes.indices.firstOrNull { it !in used && window.nodes[it].text?.trim()==message.text && !window.nodes[it].editable }
                if(index!=null)used.add(index)
                index?.let { window.nodes[it].bounds.let { b->(b.top+b.bottom)/2.0 } } to message
            }
            if(nativeRows.all { it.first!=null }) {
                val rows=nativeRows.map { it.first!! to it.second }+visual.messages.filter { it.id in visual.mediaIds }.map { visual.positions.getValue(it.id) to it }
                return native.copy(messages=rows.sortedBy { it.first }.map { it.second }.takeLast(ConversationContextPolicy.forSnapshot(native)))
            }
        }
        val ordered=native.messages.mapIndexed { index,msg->index.toDouble() to msg }.toMutableList()
        visual.messages.forEachIndexed { index,message ->
            if(message.id !in visual.mediaIds)return@forEachIndexed
            fun locate(row:ChatMessage)=native.messages.indexOfFirst { it.speaker==row.speaker && it.text==row.text }.takeIf { it>=0 }
            val before=visual.messages.take(index).asReversed().firstNotNullOfOrNull { locate(it) }
            val after=visual.messages.drop(index+1).firstNotNullOfOrNull { locate(it) }
            val fraction=(index+1.0)/(visual.messages.size+1.0)
            val position=when { before!=null && after!=null && before<after -> before+(after-before)*fraction;before!=null->before+.01+fraction*.8;after!=null->after-1+fraction*.8;else->native.messages.size+fraction }
            ordered.add(position to message)
        }
        return native.copy(messages=ordered.sortedBy { it.first }.map { it.second }.takeLast(ConversationContextPolicy.forSnapshot(native)))
    }
}
