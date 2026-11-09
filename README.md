# 对话军师 V1.7.24 正式版

读取当前聊天后，生成六条不同思路的候选回复，提供选句回复、接话建议、人物记忆、单句分析和多语言支持。是否复制或填入由你决定，内容需自行核对并发送。

## 下载

- [下载 V1.7.24 正式版 APK](https://github.com/Jacky381897524/duihua-junshi/releases/download/v1.7.24/duihua-junshi-V1.7.24.apk)
- [V1.7.24 发布说明、校验文件与第三方许可](https://github.com/Jacky381897524/duihua-junshi/releases/tag/v1.7.24)
- [所有历史版本](https://github.com/Jacky381897524/duihua-junshi/releases)

版本号：V1.7.24（版本码 71）；Android 11+、ARM64。

## 本版默认设置

- 顶部胶囊默认开启。
- 切换聊天对象时保留并尝试恢复各对象最近一批候选，默认开启；切换时在本地核对和恢复，不因此调用生成模型。
- 从旧版升级时，这两个默认值仅应用一次；之后你手动调整的开关状态会保存。

对象识别仍受聊天应用显示标题和无障碍信息限制，个别界面或设备状态下可能无法确认对象。生成候选需要你配置的模型服务，模型用量按服务商规则计费。

## 版本展示图

下面包含 V1.7.13 用户提供的实操截图和早期功能示意图。图片可能标注 V1.7.0 或 V1.7.3，展示的是历史版本界面；当前行为以 V1.7.24 为准。旧图中的“好感度”功能已从当前版本移除。

<table>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/819debf2-2332-4cff-b362-8ea08403abf5" alt="候选回复与建议 · 历史实操截图" width="220" /><br /><sub>候选回复与建议 · 历史实操截图</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/b6f8ae21-0901-4d0f-b0c5-1a68b655efa2" alt="六条候选、复制与填入 · 历史实操截图" width="220" /><br /><sub>六条候选、复制与填入 · 历史实操截图</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/1a591343-f2dc-4a85-8e45-92f0ae283950" alt="回复风格设置 · 历史界面" width="220" /><br /><sub>回复风格设置 · 历史界面</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/d02b7d2c-68eb-411c-b0e7-9c8f5928e04a" alt="六条候选与选句分析" width="220" /><br /><sub>六条候选与选句分析</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/02e059aa-b30d-4681-b4d8-c675882d5395" alt="三类知识来源协同" width="220" /><br /><sub>三类知识来源协同</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/59780ef9-8555-491e-b461-e8c5a4441837" alt="默认、祛味与真实表达模式 · 旧版示意" width="220" /><br /><sub>默认、祛味与真实表达模式 · 旧版示意</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/777d9359-f8bc-44d6-b611-a83b461aa5f1" alt="双方记忆与关系依据 · 旧版示意" width="220" /><br /><sub>双方记忆与关系依据 · 旧版示意</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/8864d0f9-97c1-4fa7-9231-6a1fa8f8bba2" alt="群聊目标与聊天时间" width="220" /><br /><sub>群聊目标与聊天时间</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/d66be1f0-7996-41b7-a3c7-4f5d6a9dd247" alt="双语候选与中文建议" width="220" /><br /><sub>双语候选与中文建议</sub></td>
</tr>
<tr>
  <td align="center"><img src="https://github.com/user-attachments/assets/be9f78b1-f9ea-4985-a5a3-7d2ab3d4bea1" alt="多平台入口与顶部胶囊" width="220" /><br /><sub>外语分析与单句快捷分析</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/1c720530-2286-42ef-b54c-ecda39511240" alt="对象缓存与用量优化" width="220" /><br /><sub>多平台入口与顶部胶囊</sub></td>
  <td align="center"><img src="https://github.com/user-attachments/assets/3eccb34b-6458-4e3d-b21e-ef2ad0bd5efc" alt="外语分析与单句快捷分析" width="220" /><br /><sub>对象缓存与用量优化</sub></td>
</tr>
</table>

## 闭源发行与验证

V1.5.0 起的自研更新以闭源二进制方式发布，范围包含界面与设置。V1.7.24 发布资产仅包括经验证的 APK、SHA-256 校验文件、版本说明和第三方许可；对应发行分支及 GitHub 自动生成的源码归档只含发行文档与许可，不是可构建源码包。历史公开源码和第三方既有权利不受影响。

本版 release 构建、R8 混淆和资源缩减通过；安装包签名、版本信息及 ARM64 原生库 16 KB 对齐检查通过。本次未运行自动化测试，也未在用户手机上完成真机验收。混淆和客户端资源加密不能保证绝对无法逆向。

第三方版权与许可见 [LICENSES](https://github.com/Jacky381897524/duihua-junshi/tree/codex/v1.7.24-distribution/LICENSES) 和发布附件 THIRD-PARTY-LICENSES.zip。