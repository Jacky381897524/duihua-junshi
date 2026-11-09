## 对话军师 V1.7.24 正式版

版本码：71。V1.7.24 将顶部胶囊和切换聊天对象时保留、恢复候选回复设为默认开启。升级时只应用一次新默认值；此后手动更改的开关状态会保留。对象切换时复用本地候选缓存，不因此调用生成模型。

本版保留此前的对象候选缓存、关系与记忆、选句回复、建议、外语候选和中文释义等功能。对象标题和无障碍信息由聊天应用提供，无法确认对象时不会承诺恢复成功；各机型仍需实际使用验证。

### 安装与校验

下载附件 duihua-junshi-V1.7.24.apk，覆盖安装。APK SHA-256：

e1906e07c950e6976ad949f365c7c43b84d9573482da2fc22a2c03f50356217c

完整校验值见 SHA256SUMS.txt。附件另含 THIRD-PARTY-LICENSES.zip，包含七份 MIT 许可及来源清单。

### 闭源发行范围

V1.5.0 起的自研更新采用闭源二进制发布，包括界面、设置和功能实现。本次发布使用仅含发行文档与第三方许可的分支；GitHub 自动生成的 Source code 归档也不含保护版源码。历史公开内容与第三方既有许可不被撤回。APK 混淆和客户端资源加密不能保证绝对不可逆向。

### 构建与验证

release 构建、R8 混淆、资源缩减和 APK 签名校验通过；包元数据确认为 V1.7.24 / 71；ARM64 原生库 16 KB 对齐检查通过。本次未运行自动化测试，也未在用户手机上完成真机验收。请在实际设备上验证悬浮权限、顶部胶囊显示和对象切换后的候选恢复。

### V1.7.13 图片集

下列图片来自用户此前提供并已附于旧版本发布说明的图片，现作为历史功能展示。个别图片带有旧版本标记；旧图中的“好感度”不属于当前版本。

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

源码、私有补丁、调试包、映射、构建密钥、签名私钥和用户原始资料均不作为发布附件。