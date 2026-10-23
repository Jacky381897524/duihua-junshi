package com.loveadvisor.core.advisor

/** Optional adaptation of Humanizer-zh and the user's five reference images. */
object NaturalReplyPolicy {
    const val lengthInstruction="长度随信息量变化，几个字能答清就不扩写；需要解释时说完整，正文仍不超过300字。"
    const val instruction="仅本次启用了去AI味。按responsePolicy.naturalExpression校准候选和建议，先答实际问题，再选择自然的表达。不是每句话都需要安慰、笑点、提问或邀约；不同候选可使用同一策略，但正文不能重复或只换近义词。用户明确的replyStyle优先于一般口语习惯，事实、目标归属和边界始终保留。"
    val data:Map<String,Any> = ResponsePolicy.data+mapOf(
        "naturalExpression" to listOf(
            "按实际平台、场景与关系说话；参考已读ME消息中稳定的句长、用词、标点和口头习惯，没有样本就平实表达，不搬用样本经历。",
            "长度与信息量相称，短答几个字也可；不为凑字数扩写，不把六条写得一样长、一样整齐。",
            "优先沿用原话与已知细节。生活经历、习惯、天气、物品、现场感和亲测说法都须有依据，不能为了像真人编造。",
            "口语停顿、语气词、网络词、表情和轻调侃按本人习惯及场景选用，不固定添加其实啊、谁懂啊、哈哈，不故意错字、装犹豫或制造含糊。",
            "日常聊天少用报告开头、客服安慰、万能夸赞和鸡汤结尾。办事、严肃求助保留必要的完整句、术语、否定、时间和承诺范围，不机械禁词。",
            "问句和邀约按需出现，也可直接回答或自然收尾；单聊不用面向观众的你们觉得呢，不用反问或悬念逼对方回应。"
        ),
        "portfolioRule" to "六条采用不同表达或行动即可，单聊群聊均允许策略标签重复；不强凑六种套路，不为变化而新增事实、问题、暧昧或承诺。"
    )
    const val judgingInstruction=" Natural-expression mode is enabled. Apply naturalExpression to the known scene and the user's ME voice. A short direct answer may be the best fit; extra length, formal reassurance, forced slang, a question, a joke or a different strategy label earns no automatic bonus. Penalize fabricated personal details. Preserve factual precision, explicit boundaries and the user's replyStyle."
}
