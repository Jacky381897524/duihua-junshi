# 对话军师 V1.8.0 正式版

读取当前聊天后，按你设置的数量生成 1–6 条不同思路的候选回复，提供选句分析、接话建议、人物记忆、单句分析和多语言支持。是否复制、填入和发送由你决定。

## 下载

- [下载 V1.8.0 正式版 APK](https://github.com/Jacky381897524/duihua-junshi/releases/download/v1.8.0/duihua-junshi-V1.8.0.apk)
- [V1.8.0 发布说明、SHA-256 校验文件与第三方许可](https://github.com/Jacky381897524/duihua-junshi/releases/tag/v1.8.0)
- [所有历史版本](https://github.com/Jacky381897524/duihua-junshi/releases)

版本号：V1.8.0（版本码 72）；Android 11+、ARM64。

## 本版变化

- 面板菜单中的“复制单句回复”改为常驻模式开关。开启后，每次点击悬浮窗直接进入单句分析；再次点击开关返回正常候选面板。修复单句结果出现后又被恢复流程覆盖的问题。
- 首页可设置每批生成 1–6 条候选，默认 6 条；候选生成、刷新、恢复和界面数量保持一致。
- 模型、视觉和判断接口提供更多服务商预设地址，按需填写模型 ID 与密钥。服务商、模型与接口能力仍需以实际配置和连接测试为准。
- 首页移除与“真实与记忆”重复的关系资料及记忆与历史入口，并移除逐轮模拟练习、多对象独立分析与比较入口。
- 顶部胶囊默认开启；切换聊天对象时保留并尝试恢复各对象最近一批候选，默认开启。切换时在本地核对和恢复，不因此调用生成模型。

对象识别仍受聊天应用显示标题和无障碍信息限制。生成候选需要你配置的模型服务，用量按服务商规则计费。

## 版本展示图

下面包含 V1.7.13 用户提供的实操截图和早期功能示意图。图片可能标注 V1.7.0 或 V1.7.3，展示的是历史版本界面；当前行为以 V1.8.0 为准。旧图中的“好感度”功能已从当前版本移除。

<table>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/819debf2-2332-4cff-b362-8ea08403abf5" alt="候选回复与建议 · 历史实操截图" width="220" /><br /><sub>候选回复与建议 · 历史实操截图</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/b6f8ae21-0901-4d0f-b0c5-1a68b655efa2" alt="候选、复制与填入 · 历史实操截图" width="220" /><br /><sub>六条候选、复制与填入 · 历史实操截图</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/1a591343-f2dc-4a85-8e45-92f0ae283950" alt="回复风格设置 · 历史界面" width="220" /><br /><sub>回复风格设置 · 历史界面</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/d02b7d2c-68eb-411c-b0e7-9c8f5928e04a" alt="候选与选句分析" width="220" /><br /><sub>六条候选与选句分析</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/02e059aa-b30d-4681-b4d8-c675882d5395" alt="三类知识来源协同" width="220" /><br /><sub>三类知识来源协同</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/59780ef9-8555-491e-b461-e8c5a4441837" alt="默认、祛味与真实表达模式 · 旧版示意" width="220" /><br /><sub>默认、祛味与真实表达模式 · 旧版示意</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/777d9359-f8bc-44d6-b611-a83b461aa5f1" alt="双方记忆与关系依据 · 旧版示意" width="220" /><br /><sub>双方记忆与关系依据 · 旧版示意</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/8864d0f9-97c1-4fa7-9231-6a1fa8f8bba2" alt="群聊目标与聊天时间" width="220" /><br /><sub>群聊目标与聊天时间</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/d66be1f0-7996-41b7-a3c7-4f5d6a9dd247" alt="双语候选与中文建议" width="220" /><br /><sub>双语候选与中文建议</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/be9f78b1-f9ea-4985-a5a3-7d2ab3d4bea1" alt="外语分析与单句快捷分析" width="220" /><br /><sub>外语分析与单句快捷分析</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/1c720530-2286-42ef-b54c-ecda39511240" alt="多平台入口与顶部胶囊" width="220" /><br /><sub>多平台入口与顶部胶囊</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/3eccb34b-6458-4e3d-b21e-ef2ad0bd5efc" alt="对象缓存与用量优化" width="220" /><br /><sub>对象缓存与用量优化</sub></td>
</tr>
</table>

## 闭源发行与验证

V1.5.0 起的自研更新以闭源二进制方式发布，范围包含界面与设置。V1.8.0 的发行标签及 GitHub 自动生成的 Source code 归档只含发行文档与第三方许可，不包含可构建的新版本源码。历史公开内容和第三方既有权利不受影响。

本版核心测试与 release 构建通过；安装包签名、版本信息及 ARM64 原生库 16 KB 对齐检查通过。未在用户手机上完成真机验收。客户端混淆和资源保护不能保证绝对不可逆向。

第三方版权与许可见 [LICENSES](https://github.com/Jacky381897524/duihua-junshi/tree/v1.8.0/LICENSES) 和发布附件 THIRD-PARTY-LICENSES.zip。
