package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
data class ImportDraft(val messages:List<ChatMessage>,val speakerMappingConfirmed:Boolean,val kind:ChatKind)
class ImportParser {
    fun parseUtf8(text:String):ImportDraft {
        require(text.isNotBlank()) { "文件或聊天内容为空" }
        require(text.toByteArray(Charsets.UTF_8).size<=1024*1024) { "单次导入最多 1MiB，请分段粘贴" }
        require(text.trimStart().first() !in listOf('{','[')) { "暂不支持此 JSON 格式，请导出 UTF-8 文本" }
        val messages=text.lineSequence().filter { it.isNotBlank() }.mapIndexed { index,line ->
            val match=Regex("^([^：:]{1,40})[：:]\\s*(.+)$").matchEntire(line.trim())
            val label=match?.groupValues?.get(1)?.trim(); val body=match?.groupValues?.get(2) ?: line.trim()
            val speaker=when(label) { "我","本人","Me","me" -> Speaker.ME; null -> Speaker.UNKNOWN; else -> Speaker.OTHER }
            ChatMessage("import-$index",speaker,label?.takeIf { speaker==Speaker.OTHER && it !in setOf("对方","TA","ta") },body)
        }.toList()
        val kind=if(messages.mapNotNull { it.sender }.distinct().size>1) ChatKind.GROUP else ChatKind.DIRECT
        return ImportDraft(messages,messages.none { it.speaker==Speaker.UNKNOWN },kind)
    }
}
