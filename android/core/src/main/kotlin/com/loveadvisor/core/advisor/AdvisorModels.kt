package com.loveadvisor.core.advisor
import com.loveadvisor.core.api.ApiProfile
import com.loveadvisor.core.knowledge.KnowledgeExcerpt
import com.loveadvisor.core.model.*
enum class Strategy(val label:String,val romance:Boolean) {
    ACKNOWLEDGE("稳妥承接",false), HUMOR("轻松幽默",false), APPRECIATION("真诚欣赏",true), TEASING("自然调侃",true), INVITATION("具体邀约",true), CLARIFY("主动澄清",true), CALM("降压",true), REPAIR("冲突修复",true), BOUNDARY("边界表达",true), CLOSE("体面收线",true), TONE("口吻校准",true), PACING("节奏把握",true)
}
data class ReplyBranches(val positive:String,val ambiguous:String,val rejection:String)
data class ReplyPlan(val id:String,val strategy:Strategy,val text:String,val reason:String,val suitableWhen:String,val cost:String,val branches:ReplyBranches,val score:Double?=null,val targetMessageId:String?=null,val topic:String?=null,val targetLabel:String?=null)
data class AdvisorAnalysis(val facts:List<String>,val inferences:List<String>,val unknowns:List<String>,val intent:String,val risk:Int?,val action:String,val strategies:List<Strategy> = emptyList())
data class AdvisorBatch(val analysis:AdvisorAnalysis,val replies:List<ReplyPlan>,val knowledgeIds:List<String> = emptyList(),val compact:Boolean=false,val kind:ChatKind=ChatKind.DIRECT)
data class VisibleBatch(val ticket:RequestTicket,val batch:AdvisorBatch)
data class AdvisorInput(val profile:ApiProfile,val snapshot:ChatSnapshot,val goal:String="",val background:String="",val knowledge:List<KnowledgeExcerpt> = emptyList(),val replyStyle:String="",val replyTarget:ReplyTarget?=null,val naturalRepliesEnabled:Boolean=false)
data class ValidationResult(val valid:Boolean,val errors:List<String> = emptyList())
