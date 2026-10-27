package com.loveadvisor.core.advisor

/** Shared quality contract for drafting and the existing single judging request. */
object ResponsePolicy {
    fun dataFor(naturalRepliesEnabled:Boolean)=if(naturalRepliesEnabled)NaturalReplyPolicy.data else data
    fun judgingInstructionFor(naturalRepliesEnabled:Boolean)=judgingInstruction+if(naturalRepliesEnabled)NaturalReplyPolicy.judgingInstruction else ""
    val data:Map<String,Any> = mapOf(
        "objective" to "有来有回、自然具体、有自己的表达，让对方容易接话；不以讨好换回应",
        "dimensions" to linkedMapOf(
            "grounding" to "接住目标原话的具体信息，先回答实际问题；不虚构经历、关系、承诺或心理",
            "reciprocity" to "表达自己的看法或当下反应，投入与对话相称；温暖可以，空泛吹捧、自贬求回应、无限承诺不加分",
            "specificity" to "优先沿用当前细节、共同梗和真实偏好；避免能复制给任何人的客服腔",
            "playfulness" to "轻松场景可接梗、反差或微夸张；笑点落在共同场景，不贬低对方，不用嫉妒、激将或故意冷落",
            "responseEase" to "可留一个容易接的细节、轻选择或小问题，不必每条都问；不抛空悬念、不连环盘问",
            "calibration" to "明确拒绝就停止对应推进；忙碌或短回复先降压；悲伤、认真求助、工作、群聊按真实关系回应，不强行恋爱"
        ),
        "portfolio" to listOf("具体承接或回答","适时接梗","自己的轻观点","低负担接话口","有依据的具体行动","节奏或边界"),
        "portfolioRule" to "按场景选六种不同表达或行动，不是六个固定模板。严肃场景可用澄清、修复、降压替代玩笑；明确拒绝不留邀约或索要回复的钩子。",
        "styleRule" to "用户replyStyle控制长度和口吻；允许温柔、真诚赞美和已有互惠关系的暧昧。单个词、短句、问号或策略标签都不是扣分依据。"
    )
    const val instruction="responsePolicy是生成与排序共用的质量准则。先判断场景，再按它组织六条真正不同的行动与表达。自然表达自己的态度，避免六条都附和、夸赞或陪伴承诺；轻松且有依据时敢接梗、轻调侃和推进。有具体依据的欣赏和体贴是高质量回应，不因怕讨好而删除温暖；六条不能全是博弈和收线。按原话识别网络梗与反讽，合适时给少量可接的梗与平实选项。普通联系人不必凑欣赏、调侃或邀约；悲伤、工作、明确拒绝和群聊不强行调情。接话口要贴近原话且容易回答，每条最多一个自然小问题，也可完全不问。明确拒绝后停止对应推进，不追问、不用悬念逼回；一次没空或短回复不等于关系拒绝。遵守用户风格与真实身份，不保证对方会回应。"
    const val detailInstruction="依据responsePolicy，只解释当前这条候选的作用、适用条件与后续分支，不生成新的候选批次。关注具体原话、平等投入、自己的态度和自然接话口；轻松场景允许接梗，严肃场景先解决具体问题。积极回应后只推进一小步；含糊时不连续追问；明确拒绝后停止对应推进。保持用户的长度与口吻偏好，不预测成功率。"
    const val judgingInstruction="Use state.responsePolicy (or responsePolicy for chat-format requests) and replyStyle as the same quality rubric used to draft. Prefer concrete, reciprocal replies with a natural voice and an easy optional conversational opening when the scene supports it. Do not automatically rank flattery, unconditional availability, generic reassurance or apologies highest. Context and boundaries outrank cleverness: explicit refusal means stop that advance; grief, conflict and practical tasks may need no joke or question. Warmth, tact, responsive listening and specific sincere appreciation are positive qualities, never penalize them as neediness without contextual evidence. Do not rank coldness, strategic withdrawal or teasing above a useful warm answer by default. Recognize contextual memes and idioms rather than literal readings; use culturally natural language. Established mutual flirting is valid. Do not score by keyword, length or strategy label alone. Scores remain your suitability judgment, never a probability of attraction or response."
}
