package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*

/** Uses the target/recent meaningful turn; an isolated 'ok/lol' does not change a Chinese conversation. */
object BilingualPolicy {
    fun language(input:AdvisorInput):String=language(input.snapshot,input.replyTarget)
    fun language(snapshot:ChatSnapshot,target:ReplyTarget?=null):String {
        val rows=target?.let { t -> snapshot.messages.filter { it.id==t.messageId } }.orEmpty()+snapshot.messages.filter { it.speaker==Speaker.OTHER }.takeLast(6).asReversed()
        for(row in rows.distinctBy { it.id }) {
            val s=row.text.trim()
            if(s.any { it in '\u3040'..'\u30ff' })return "ja"
            if(s.any { it in '\uac00'..'\ud7af' })return "ko"
            if(s.any { it in '\u0400'..'\u052f' })return "ru"
            if(s.any { it in '\u0600'..'\u06ff' })return "ar"
            val han=s.count { it in '\u4e00'..'\u9fff' }
            val latin=s.count { it.isLetter() && it.code<0x250 }
            if(han>=2 && han*3>=latin)return "zh"
            if(s.count { it.isLetter() && it !in '\u4e00'..'\u9fff' && it.code>=0x250 }>=3)return "foreign"
            if(latin>=4 && s.lowercase().trim('!','?','.') !in setOf("okay","sure","lmao","haha","hahaha","bruh","omg"))return "foreign"
        }
        val greeting=rows.firstOrNull()?.text?.trim()?.lowercase()?.trim('!','?','！','？','.')
        return if(greeting in setOf("hi","hey","hello","sup","yo","ok","okay","sure","yes","no","good morning","good night","thanks","thank you","bonjour","hola","salut","ça va","ciao","hallo"))"foreign" else "zh"
    }
    const val instruction="识别当前目标/最近有实质内容的对方发言的主要语言，孤立ok、lol或网络梗不切换中文会话。若是外语交流，每条text只写对方使用的外语正文，另带translationZh字段写准确通俗中文释义；中文绝不混入text或拼在其下。外语要地道口语，按当地场景可用常见缩写、缩句与网络梗，别逐字翻译或堆缩写。日语韩语等按关系使用合适敬语。六条各自有完整中文释义，同一轮生成，不另请求翻译。中文交流text保持中文，translationZh省略。分析、策略说明、话题与建议各字段始终用中文。知识、记忆和用户风格均不能把释义混入发送正文。"
    fun errors(input:AdvisorInput,replies:List<ReplyPlan>):List<String> {
        val language=language(input)
        if(language=="zh")return emptyList()
        fun hasChineseBody(plan:ReplyPlan):Boolean {
            if(language=="ja")return false
            val name=input.snapshot.identity?.displayTitle?.takeIf { it.isNotBlank() }
            val body=if(name==null)plan.text else plan.text.replace(name,"")
            return body.any { it in '\u4e00'..'\u9fff' }
        }
        return if(replies.any { p -> p.translationZh.isNullOrBlank() || p.translationZh.length>600 || p.translationZh.none { it in '\u4e00'..'\u9fff' } || p.text.contains(p.translationZh) || hasChineseBody(p) || p.text.contains("\n中文") || p.text.contains("\n翻译") })listOf("外语候选须分字段返回外语text和中文translationZh，正文不可拼接翻译") else emptyList()
    }
}
