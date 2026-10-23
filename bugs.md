# Bug Report — V1.5.2 去AI味开关完整调用链 — 2026-10-23

## Summary
- Critical: 0 open, 0 fixed
- Intermediate: 1 open, 0 fixed
- Normal: 1 open, 0 fixed

本轮审查应用代码6f80e8b，覆盖开关持久化与同步、手动读取到生成/评分/显示、刷新、指定回复、完整咨询、逐句判断、建议、异常终态及其网络传输。只记录结论，未修改产品规则或业务代码。发现数量不代表真机与真实模型没有其他问题。

## 🔴 Critical

本轮未确认新的Critical缺陷。

## 🟡 Intermediate

### BUG-001: 模型调用缺少整次操作的总时限，持续慢响应可能长期占用加载状态
- **File:** android/core/src/main/kotlin/com/loveadvisor/core/api/JdkHttpTransport.kt:22；android/app/src/main/java/com/loveadvisor/android/advisor/AdvisorController.kt:33
- **Issue:** 只有15秒连接超时和90秒读取间隔超时。传输循环在JdkHttpTransport.kt:38持续读取，控制器也没有覆盖整次生成/评分的截止时间。读取超时并非整次请求90秒内结束的保证。
- **Trigger:** 远端服务或代理持续间歇返回少量数据，每次间隔小于readTimeout，但迟迟不结束响应；或者请求包含多个顺序阶段，每阶段分别消耗较长时间。
- **Impact:** 两种模式都可能长期显示生成/刷新中。同一会话的读取和刷新在加载时被阻止（ChatCaptureService.kt:492、568），缺少整次操作终止上限。不能把所有等待问题都归因于去AI味规则。
- **Suggested Fix:** 为整次生成/判断操作设置可取消的总截止时间，超时后终止连接、使对应请求进入失败终态并通知面板，保留旧候选；明确区分用户取消、网络间隔超时和总时限。不通过自动追加模型重试解决等待。
- **Confidence:** 源码确认缺少总截止时间；本地JVM/回环HTTP复现：读取间隔超时250ms，50ms一次持续响应，整次成功耗时1330ms；静默响应258ms按预期超时。该测试验证应用传输实现，不能代表所有Android机型的底层额外超时策略，也未证明用户前次卡住由此触发。
- **Status:** Open

## 🟢 Normal

### BUG-002: 已有候选的建议会按当前开关生成，与该批候选的生成模式可能不一致
- **File:** android/app/src/main/java/com/loveadvisor/android/capture/ChatCaptureService.kt:585
- **Issue:** 建议请求重新调用graph.inputFor读取当前设置，没有采用当前VisibleBatch.batch.naturalRepliesEnabled。新生成的候选已保留本批模式，建议却没有沿用；ReplyCardView.kt:37、80只按卡片缓存是否已获取过建议，不把模式纳入缓存条件。
- **Trigger:** 关闭去AI味生成一批候选，切换为开启但不刷新，再首次查看其中一条“建议”：候选是原模式，建议使用新模式。若切换前某张卡片已获取建议，该卡片仍显示旧缓存，另一张首次获取的建议可能采用新规则。反向切换同理。
- **Impact:** 同一批候选与不同卡片建议的表达规则可能不一致；与开关提示“下次生成或刷新生效”的候选批次边界不够一致。没有发现此处会改写候选正文、目标、事实或触发自动刷新。
- **Suggested Fix:** 优先让建议继承对应候选批次的生成模式；若产品决定建议采用当前设置，需要明确提示，并按模式处理建议缓存。不要为了同步开关自动重写现有候选或请求模型。
- **Confidence:** 调用链已确认。提示语的“生成”是否包含另行生成建议存在产品语义空间；本条按“同一候选批次保持规则一致”的使用预期归为Normal，非数据丢失或安全问题。
- **Status:** Open

## ✅ Resolved

本轮未实施修复。此前V1.5.1“候选生成/排序成功，最终校验拒绝后不通知面板”的问题已在V1.5.2修复，本轮复核：AdvisorBatch保留本次模式，AdvisorValidator.kt:5以该模式校验；BatchCoordinator.kt:47区分接受/拒绝/过期；AdvisorController.kt:53在拒绝后通知面板。相关端到端回归在本次459项核心测试中通过，不作为本轮新缺陷重复登记。

详细工作原理、软硬约束、请求次数及验证边界见docs/verification/2026-10-23-natural-mode-audit.md。
