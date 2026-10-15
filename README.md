# 对话军师

<img src="docs/images/logo.png" alt="对话军师 Logo" width="96" />

**一个对话，六种思路。** 海街寺庙整合开发的 Android 聊天助手，开发版本 **V1.3.01（左上角胶囊）**，已发布版本 **V1.2.12**。

## 下载与使用

[下载 V1.2.12 APK](https://github.com/Jacky381897524/duihua-junshi/releases/download/v1.2.12/duihua-junshi-V1.2.12.apk) · [完整发布页、源码包与校验文件](https://github.com/Jacky381897524/duihua-junshi/releases/tag/v1.2.12)

在本仓库的 **Releases** 下载 APK 和 SHA256 校验文件。Android 11+、ARM64。安装后开启悬浮窗及无障碍服务，在微信、QQ或抖音单聊中点击悬浮球，再点「读取信息」。

[完整使用说明](docs/verification/user-guide.md)

## 主要功能

- **三种知识融合**：聊天副驾、狗头军师和恋爱大师共同参与，每批六条候选，滑动挑选、查看详情、刷新换一批。
- **聊天识别**：按需读取可见文字、图片与表情包，区分自己与对方。仅支持一对一私聊。
- **快捷回复**：复制后收起面板，填入草稿后由你发送。面板和圆球一起移动，键盘出现时为输入栏留出空间。
- **左上角胶囊**：时间右侧、摄像头左侧的小号入口，无需通知；点击打开候选，长按可拖动微调并保存。仅在微信、QQ、抖音前台显示，开关在首页「悬浮窗设置」。
- **边缘隐藏**：面板收起后默认15秒无操作，悬浮球平滑滑到最近的左右边缘，仅露约三分之一。轻点完整露出，再点展开面板；首页「悬浮窗设置」可自定义等待秒数。
- **回复风格**：全对象风格可统一设置；已有对象专属风格时优先采用专属风格。支持独立查看、编辑和删除。
- **自选接口与模型**：OpenRouter、DeepSeek、通义兼容、智谱及自定义兼容接口；刷新模型目录并测试实际连接。
- **判断接口**：默认 OpenRouter `typesafe/jev-1.13`，也可接入自定义判断模型。
- **少量上下文**：每段会话保留最近50条，按12小时周期清理超额数据。密钥和本机敏感资料加密存储。

进入聊天或展开面板不会自动调用模型。「读取信息」收集当前内容；「刷新换一批」使用已读取上下文。判断、回复和必要的截图会发送到你配置的服务，服务用量由相应平台计费。

## 知识资料

收录45份资料，涵盖社会发展与家庭变迁、中国法律基础、安全与危机转介、接话技巧、冲突与修复、证据分级、婚姻家庭、高情商表达、情绪调节、职场沟通等主题。

[资料目录](android/app/src/main/assets/knowledge/index.json)

## 界面与宣传

![对话军师功能与知识库海报](docs/images/poster-V1.2.12.png)

## 构建源码

使用 Android Studio 或命令行，准备 JDK17、Android SDK35。Windows请克隆到不含中文的路径。

```sh
cd android
./gradlew :core:test :app:assembleDebug
```

Windows使用 `gradlew.bat`。干净克隆默认生成本地调试签名；自行构建的 APK 与发布安装包签名可能不同。官方更新使用作者保管的签名私钥，该私钥不在仓库中。

项目开发环境的本机构建辅助脚本位于 `tools/`，可选配置存放在被忽略的 `.toolchain/config.json`；标准 Gradle 构建不需要该本机配置。

## 验证状态

核心测试207项、配置测试7项通过，构建及APK签名、知识资料完整性与ARM64原生库16KB对齐校验通过。目标手机 vivo V2509A、Android16、微信8.0.78的填入与提速已获用户实测确认；不同设备及QQ、抖音版本仍需逐场景验收。

## 来源与许可

基于 [jev-chat-jarvis](https://github.com/jev-chat/jev-chat-jarvis) 和相关 [goutoujunshi-jev-chat](https://github.com/shengjidaguai-china/goutoujunshi-jev-chat) 开源资料结合开发。实际知识资料固定取自 [goutoujunshi](https://github.com/shengjidaguai-china/goutoujunshi) 的对应版本；来源清单及许可见 [第三方说明](THIRD_PARTY_NOTICES.md)。

保留上游原文、版权和MIT许可。本项目名称与作者信息用于标识整合版，不表示上游作者参与或背书。

