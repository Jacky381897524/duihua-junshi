package com.loveadvisor.core.advisor
import com.loveadvisor.core.api.*
class UnifiedAdvisor(private val client:ChatCompletionClient) {
    private val codec=AdvisorCodec()
    private val validator=AdvisorValidator()
    fun analyze(input:AdvisorInput,cancel:Cancellation):AdvisorBatch = generate(input,null,emptyList(),cancel)
    fun alternatives(input:AdvisorInput,analysis:AdvisorAnalysis,previous:List<String>,cancel:Cancellation):AdvisorBatch = generate(input,analysis,previous,cancel)
    private fun generate(original:AdvisorInput,common:AdvisorAnalysis?,previous:List<String>,cancel:Cancellation):AdvisorBatch {
        val input=original.copy(snapshot=ConversationTimePolicy.forReply(original.snapshot,original.replyTarget))
        val currentAnalysis=common.takeIf { input.snapshot.messages==original.snapshot.messages }
        val messages=mutableListOf(PromptMessage("system",UnifiedPrompt.systemFor(input)),PromptMessage("user",UnifiedPrompt.data(input,currentAnalysis,previous)))
        var problems=listOf("格式无效")
        repeat(2) { attempt ->
            cancel.check()
            val raw=client.complete(input.profile,messages,cancel)
            val parsed=codec.parse(raw)
            if(parsed!=null) {
                val batch=parsed.copy(analysis=currentAnalysis ?: parsed.analysis,knowledgeIds=input.knowledge.map { it.id },kind=input.snapshot.identity?.kind ?: com.loveadvisor.core.model.ChatKind.DIRECT,naturalRepliesEnabled=input.naturalRepliesEnabled,realisticRepliesEnabled=input.realisticRepliesEnabled,personalContext=input.personalContext.takeIf { input.realisticRepliesEnabled })
                problems=validator.validate(batch,input.naturalExpression).errors+BilingualPolicy.errors(input,batch.replies)
                if(!ReplyTargets.valid(input,batch.replies)) problems=problems+"六条候选未对应选中原话或已读目标"
                if(previous.isNotEmpty() && batch.replies.all { plan -> previous.any { AdvisorValidator.normalize(it)==AdvisorValidator.normalize(plan.text) } }) problems=problems+"六条全部重复历史候选，必须换新的表达或行动"
                if(problems.isEmpty()) return batch
            } else problems=listOf("缺少字段或不是要求的 JSON 格式")
            if(attempt==0) {
                messages.add(PromptMessage("assistant",raw.take(18000)))
                messages.add(PromptMessage("user","本次输出无效：${problems.joinToString("；")}。请修复并只输出符合格式的六条候选 JSON，遵守原始对话与策略约束。"))
            }
        }
        throw ApiException(message="模型连续两次未返回合格的六条候选：${problems.joinToString("；")}")
    }
}
