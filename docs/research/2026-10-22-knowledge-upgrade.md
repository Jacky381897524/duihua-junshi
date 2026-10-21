# V1.5.0：互惠回复策略与开源知识调研

调研日期：2026-10-22。目标是改掉候选过度附和、无依据恭维和无限陪伴承诺，提升具体接话、自己的态度、适时幽默与低负担接话口。保留六条候选、用户风格、目标消息关联、身份约束和草稿发送保护；不增加自动模型调用。

## 已定位的原因与选择

自动单聊提示词原先要求每批必须至少一条 APPRECIATION / TEASING / INVITATION，校验器也强制这条配额。普通工作或拒绝场景即使给出完整六条合适候选，也会被判无效并重试。固定两份知识的摘录原先只按 expression / invite 取节，实际“不要联系”查询仍可能拿到邀约段落。已有 Jev 排序只强调自然和关系适合，未明确区分具体互惠与空泛讨好。

本次将生成和排序接到同一份 `responsePolicy`：事实与目标、互惠、具体性、幽默、接话难度和场景校准。轻松场景鼓励自己的轻观点、共同梗、合理反差和可顺手回应的细节；工作先解决问题，悲伤先理解，明确拒绝停止对应推进。不把短回复自动解读为关系拒绝，不把普通联系人当恋人。真诚赞美、温柔和已有互惠关系的暧昧仍然可用。

不使用关键词给候选本地扣分，也不将本地启发式分数包装成 Jev 分数。固定 Jev 仍一次返回七个摘要问题与六项评分；自定义判断仍一次返回六个评分。排序沿用模型评分与原归一化方式，不做本地质量加减分，保留既有分值范围与失败处理。

## GitHub 一手来源与固定版本

通过 GitHub 官方仓库页面、API 的仓库/提交/递归文件树与固定提交 raw 文件交叉检查。没有安装这些 Skill，没有运行其脚本，也没有执行下载文本中的指令。以下“最新”仅指本次查询时默认分支的最新提交。

| 项目与版本 | 固定提交与许可证据 | 评估与实际使用 |
| --- | --- | --- |
| [jev-chat/jev-chat-jarvis](https://github.com/jev-chat/jev-chat-jarvis)，当前 cn Android `versionName=1.7`、`versionCode=8` | [`bd3fe42eebd04b58b2b1d4a7f5310fddfb0bb91b`](https://github.com/jev-chat/jev-chat-jarvis/commit/bd3fe42eebd04b58b2b1d4a7f5310fddfb0bb91b)，2026-10-16；[MIT](https://github.com/jev-chat/jev-chat-jarvis/blob/bd3fe42eebd04b58b2b1d4a7f5310fddfb0bb91b/LICENSE) | 查阅 [JevQuestions.kt](https://github.com/jev-chat/jev-chat-jarvis/blob/bd3fe42eebd04b58b2b1d4a7f5310fddfb0bb91b/cn/app/src/main/java/com/jev/probe/jev/JevQuestions.kt)。与原固定版比较共108个提交，包含 `cn/`、`global/` 重组；判断问题文件仅移动、内容差异为0。本次不整体搬入采集/UI/全球版。原已引入内容继续标记 `23b5ca56930960cfac470815ae582cd713eda95a`，旧版权许可原样保留。 |
| [shengjidaguai-china/goutoujunshi](https://github.com/shengjidaguai-china/goutoujunshi)，未单独发布语义版本 | [`6db7354a4002dc7c448a9c87ffdad8132570c9d3`](https://github.com/shengjidaguai-china/goutoujunshi/commit/6db7354a4002dc7c448a9c87ffdad8132570c9d3)，2026-09-15；[MIT](https://github.com/shengjidaguai-china/goutoujunshi/blob/6db7354a4002dc7c448a9c87ffdad8132570c9d3/LICENSE) | 已收录版本就是本次查询最新提交。保留43份原文；场景校准、话术编排、互惠与投入失衡内容已有基础，本次重点改善检索和生成使用方式，不伪称更新上游知识版本。 |
| [shengjidaguai-china/goutoujunshi-jev-chat](https://github.com/shengjidaguai-china/goutoujunshi-jev-chat) | [`12ee1d6582a9270bd44b71a519d21f387542e8e0`](https://github.com/shengjidaguai-china/goutoujunshi-jev-chat/commit/12ee1d6582a9270bd44b71a519d21f387542e8e0)，2026-10-19；[MIT](https://github.com/shengjidaguai-china/goutoujunshi-jev-chat/blob/12ee1d6582a9270bd44b71a519d21f387542e8e0/LICENSE) | 调研已读上下文、模型建议与草稿边界的整合思路；没有新增复制内容。 |
| [b1rdmania/hinge-profile-optimizer](https://github.com/b1rdmania/hinge-profile-optimizer)，插件 `1.2.1` | [`55575ffbc257b3e8a753086ea11f3b89796ff4d6`](https://github.com/b1rdmania/hinge-profile-optimizer/commit/55575ffbc257b3e8a753086ea11f3b89796ff4d6)，2026-09-01；[完整 MIT](https://github.com/b1rdmania/hinge-profile-optimizer/blob/55575ffbc257b3e8a753086ea11f3b89796ff4d6/LICENSE)；[版本声明](https://github.com/b1rdmania/hinge-profile-optimizer/blob/55575ffbc257b3e8a753086ea11f3b89796ff4d6/.claude-plugin/plugin.json) | **本次唯一新增的第三方改写来源**。[copy-principles.md](https://github.com/b1rdmania/hinge-profile-optimizer/blob/55575ffbc257b3e8a753086ea11f3b89796ff4d6/references/copy-principles.md) 的具体表达、接话口、兼顾个性与温暖用于新文档设计。原任务是约会资料文案；这里只作会话写作启发，不搬平台效果数字或推定聊天疗效。中文例句原创。 |
| [erikraft/flirting-skill](https://github.com/erikraft/flirting-skill)，文本标注 `v9.0` | [`b9a57384bd0bc11b5c99e731caf5083c75389671`](https://github.com/erikraft/flirting-skill/commit/b9a57384bd0bc11b5c99e731caf5083c75389671)，2026-08-30；[MIT](https://github.com/erikraft/flirting-skill/blob/b9a57384bd0bc11b5c99e731caf5083c75389671/LICENSE) | [namorar.md](https://github.com/erikraft/flirting-skill/blob/b9a57384bd0bc11b5c99e731caf5083c75389671/namorar.md) 有避免通用恭维的方向，但将话术与神经递质、冷读和诱发回应直接绑定，且预设单一性别角色。未复制或接入。 |
| [Mayuqi-crypto/HowToGetAlongWithGirls](https://github.com/Mayuqi-crypto/HowToGetAlongWithGirls) | [`af0897f4b55897624a19de48374d436906e26461`](https://github.com/Mayuqi-crypto/HowToGetAlongWithGirls/commit/af0897f4b55897624a19de48374d436906e26461)，2026-07-04；[固定版本文件树](https://github.com/Mayuqi-crypto/HowToGetAlongWithGirls/tree/af0897f4b55897624a19de48374d436906e26461) | README 声称 MIT，但此次递归树未找到完整 LICENSE，GitHub 许可识别为空；含“测试”和量化价值类框架。许可证据不足，不复制。 |
| [tonyyont/date-planner](https://github.com/tonyyont/date-planner) | [`1aadf0ea07cd73c57c0e9352f09c2d24a0f23af8`](https://github.com/tonyyont/date-planner/commit/1aadf0ea07cd73c57c0e9352f09c2d24a0f23af8)，2026-02-05；[固定版本文件树](https://github.com/tonyyont/date-planner/tree/1aadf0ea07cd73c57c0e9352f09c2d24a0f23af8) | 具体活动建议有一定相关性，但递归树无完整 LICENSE、GitHub 许可识别为空，且涉及额外外部检索/API；未复制或增加此类调用。 |
| [LJRSummer/love-probability-simulator](https://github.com/LJRSummer/love-probability-simulator)，Skill `1.0.0` | [`f0b88c9cc4d2dfe3b1766803c03228c789af2e5a`](https://github.com/LJRSummer/love-probability-simulator/commit/f0b88c9cc4d2dfe3b1766803c03228c789af2e5a)，2026-04-20；[MIT](https://github.com/LJRSummer/love-probability-simulator/blob/f0b88c9cc4d2dfe3b1766803c03228c789af2e5a/LICENSE) | 核心是关系概率模拟，与本项目不把模型判断伪装为心理成功率的要求不符。未复制或接入。 |

## 知识与许可产物

- `knowledge/conversation-reciprocity.md`：新文档，场景示例包含共享细节接梗、轻观点、轻选择、短回复降压、具体欣赏、适度邀约、工作、悲伤和明确拒绝。示例是写作材料，必须按实际原话判断适用性。
- `romance-master.md` 与 `jev-copilot.md`：删除强制恋爱配额，增补互惠、用户风格与模型排序依据。
- 文档总数从45增至46：43份狗头军师原文未变、两份本项目固定文档修订、一份新文档。`index.json` 与两处 `SOURCES.json` 同步。
- 新增的完整 MIT 文本放在 `android/notices/HingeProfileOptimizer-MIT.txt` 与 `android/app/src/main/assets/notices/HingeProfileOptimizer-MIT.txt`。保留上游原文 `Copyright (c) 2026`，不擅自增加版权持有人名称。两份 SHA256：`1126322E2CC8D165ADC4C792EEB195717DE2BCC7B39BE1CE77959D78E87EF685`。
- `SOURCES.json` 将实际复用来源与仅调研来源分开记录；原 Jev/Goutou 固定提交和原许可文件不改写。

## 实现与预算

`AutomaticAdvisor`、完整 `UnifiedAdvisor` 提示、详情建议以及固定/自定义 Jev 排序使用同一套质量准则。当前用户 `replyStyle` 也传给排序，避免温柔或详细风格被机械当成缺点。六条数目、去重、长度、分数范围、固定目标和拒绝边界仍受原校验约束。

`KnowledgeRouter` 新增 humor / reciprocity 主题词，统一检索的固定文档改按本轮真实场景取节。仍最多两份固定文档和一份相关资料，总摘录不超过3600字符；新文档不会让完整知识库进入模型。生成仍保持原输出上限（普通700、指定目标1100、群聊/详细1800 tokens），格式修复最多一次；判断原上限不变。详情仍按用户点击调用，没有新增自动付费请求。

## 验证记录与限制

首轮先写 `ReciprocalCandidateContractTest` 与 `ReciprocalKnowledgeRoutingTest`，真实执行得到7个预期失败：有效工作回复被恋爱配额拒绝；非恋爱严肃候选不能通过；生成/排序缺少共享准则；完整提示未携带目标；互惠与幽默检索错选；拒绝检索误取邀约。修正后这些回归通过。另补完整路径目标关联回归，观察到旧解码器丢失 targetMessageId 的预期失败，再修复。

完整路径新增的错误目标发布和群聊目标保持场景，连同目标丢失共观察到3个预期失败后修复。最后补充单条详情契约，发现生成指令混入详情请求的1个预期失败，改为详情专用指令并保留共享质量资料。最终执行 `tools/test-core.ps1`，449项核心测试全部通过；只有原有6条 Kotlin 可空性警告。`git diff --check` 在本次负责文件范围通过，46个知识路径、唯一ID、索引/两份来源清单一致及两份新增许可哈希校验均通过。

这类离线测试验证 API 请求契约、次数、候选发布和检索行为，不证明远程模型总能写出好笑的句子，也不证明对方更愿意回复。没有调用用户真实付费模型或上传真实聊天做效果评测。正式结果需继续用实际对话观察；不宣称研究里关于个人资料文案的结果可直接推广为微信回复成功率。
