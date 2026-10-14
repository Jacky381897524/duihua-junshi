# 原生灵动岛入口与三分之一贴边 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 普通悬浮球露出三分之一，并增加经过真实点击验证的vivo原生岛入口和专用可拖动候选面板。

**Architecture:** 保留现有捕获、候选和填入链路；新增本地原生通知适配与入口状态策略，协调球/测试/原生模式。原生通知专用点击通过私有回调打开现有面板，只有窗口创建成功才记录验证，普通通知点击不计入验证。原生模式使用独立面板锚点与顶部三点把手，普通模式沿用共同锚点。

**Tech Stack:** Kotlin、Android原生View/WindowManager/Notification/PendingIntent、现有AndroidX Core、JUnit4；compile/target SDK35、min SDK30、JDK17、Gradle8.9。无新云服务或动画依赖。

**Spec:** `docs/superpowers/specs/2026-10-14-native-island-design.md`（用户已确认）。

## Global Constraints

- 只接入vivo系统原生岛，不绘制仿制胶囊。首先验证vivo X300/V2509A、Android16；其他品牌回退普通球。
- 普通球40dp、贴边露宽度约三分之一、默认15秒且可自定义、250毫秒动画；原有普通拖动方式保留。
- 原生开关默认关闭，首测保留球。只有真实原生专用点击成功打开面板才能确认成功；普通通知、notify返回成功不能确认。
- 仅微信、QQ、抖音前台、解锁、总开关开启且无障碍服务可用时展示入口；锁屏、离开应用、失效时取消。
- 原生模式每次从关闭状态打开都顶部居中；仅正中三点把手可拖动。不保存原生面板上次拖动位置。
- Logo及待读取/分析中/6条候选/读取失败等状态；不在通知中加入聊天正文、对象名、候选全文。
- 六条候选、约三条可见、普通不透明UI、手动读取/刷新、复制后收起和已有填入行为保持。
- 不新增通知读取权限；仅请求本应用POST_NOTIFICATIONS。不新增模型调用、自动发送或后台正文收集。
- SDK/签名/私钥遵守现有脚本；保存已有V1.2.13安装包。正式新包计划V1.2.14、versionCode17，原生尚未验证的中间包明确标为测试。

## Review Focus

1. 静默忽略原生参数或普通通知点击：不得隐藏唯一球入口；任务1、3验证独立回调和确认条件。
2. 锁屏/切换应用后的排队旧回调：不得打开面板或确认能力；任务1、3以租约和当前前台资格拒绝。
3. 截图临时移除入口、系统更新、通知权限关闭：不丢失回退入口，也不把旧验证永久沿用；任务2、3验证暂停与失效。
4. 键盘/横屏/侧边系统栏/字体放大：露球比例按物理屏幕，面板不遮输入框；任务4覆盖几何与实际窗口。
5. 普通标题与原生正文误拖、关闭后再次打开：普通模式不退化，原生仅把手拖动且重新居中；任务4验证View实际触摸。

## 文件与职责

- `android/core/.../overlay/BubbleEdgeGeometry.kt`与对应测试：修改露出比例。
- 新增core `overlay/NativeIslandEntryPolicy.kt`与`NativeIslandEntryPolicyTest.kt`：资格、验证和模式决策。
- 新增core `overlay/IslandPanelGeometry.kt`与`IslandPanelGeometryTest.kt`：独立顶部居中和安全区域定位。
- 新增app `island/NativeIslandNotification.kt`、`NativeIslandReceiver.kt`：厂商通知结构、生命周期、私有原生回调；完整路径前缀为`android/app/src/main/java/com/loveadvisor/android/`。
- 新增app `island/NativeIslandCoordinator.kt`：前台租约、状态合并、验证持久化及回退。诊断不记录令牌/正文。
- 修改app `capture/ChatCaptureService.kt`、`overlay/OverlayController.kt`、`overlay/AdvisorPanelView.kt`：挂接入口资格及两种面板布局；只在任务4新增原生专用拖动行为。
- 修改app `storage/AppSettings.kt`、`settings/OverlaySettingsActivity.kt`、`ui/DiagnosticsActivity.kt`与Manifest：开关、权限、首次测试和状态；迁移测试用现有`AutomaticSettingsTest.kt`。
- 新增设备测试`android/app/src/androidTest/java/com/loveadvisor/android/island/NativeIslandDeviceTest.kt`、`overlay/IslandPanelDeviceTest.kt`；复用受DUMP保护的debug探针做脱敏诊断。

## Task 0：保护现有基线

**Files:** 上次V1.2.13修改的app/core文件、`tools/build-android.ps1`、`tools/package-apk.ps1`、已存在的V1.2.13验证说明；不加入output、私钥、工具缓存或其他历史未跟踪文件。

- [ ] 检查Git差异，明确V1.2.13的未提交改动都是此前已交付内容；记录`output/verification-1.2.13.json`的APK哈希及签名，保留旧包。
- [ ] 按用户选择在当前目录或隔离工作树执行。若使用新工作树，先仅将V1.2.13基线文件做精确提交，避免新树缺少现有计时功能；沿用现有受忽略的工具链/签名配置，不重新生成密钥。
- [ ] 运行`./tools/test-core.ps1`及`./tools/test-settings.ps1`。预期当前基线217项core、8项settings通过；环境失败先修复环境，不禁用检查。
- [ ] 如尚未提交V1.2.13，以明确文件列表提交基线，不使用`git add .`。本任务不发布远程Release、不改手机数据。

## Task 1：原生显示与独立点击的最小验证

**Files:** 新增app `island/NativeIslandNotification.kt`、`island/NativeIslandReceiver.kt`；修改Manifest、`ui/DiagnosticsActivity.kt`、`capture/ChatCaptureService.kt`；新增`island/NativeIslandDeviceTest.kt`，必要时在debug探针增加只检查本应用岛状态的模式。

**Interfaces:** `NativeIslandNotification.publish(status:String,lease:IslandLease):IslandPublishResult`、`end():Unit`；`IslandLease(id:String,packageName:String,windowId:Int,generation:Long)`；`IslandPublishResult`为`POSTED`或包含脱敏原因的`FAILED`。`ChatCaptureService.onNativeIslandTap(leaseId:String):Boolean`返回“校验租约且面板注册成功”，不调用读取/模型。

- [ ] 先写设备测试：原生和普通通知使用不同PendingIntent；`assertNotEquals(nativeIntent,ordinaryIntent)`，普通Intent不进入`onNativeIslandTap`；过期租约、锁屏、非支持应用均返回false并保持球入口。用实际Notification构建结果和私有Receiver，不以mock存在作为通过依据。
- [ ] 编译并运行目标设备测试，观察新增功能尚不存在/拒绝条件尚未实现的失败；准备实际上岛观察流程。无法连接时提供用户可自行安装的测试包，不把编译当作真机通过。
- [ ] 实现本地通知构建及显式不可变PendingIntent。原生专用回调放在`notification.superx.clickResp`，普通`contentIntent`只打开诊断、不确认成功。Receiver不导出；回调含本次随机租约，原生回调不启动首页或从广播跳转Activity。原生回调先用现有面板验证可打开，测试期间始终保留球。
- [ ] 先尝试已公开的本地通知模板结构，记录实际设备接受的字段/模板、原生专用回调和结束行为。图标使用现有资源，避免把2048像素Bitmap直接塞入Binder。通知取消先发送结束operation=2再cancel；不重复响铃。不得伪装成其他App或将测试场景当作正式能力授权。
- [ ] 验证系统顶部实际显示及点击：仅普通通知出现、字段被忽略或原生点击未回调，都不通过；保存系统版本、显示/点击结果和脱敏失败原因。若厂商不允许当前接入，继续核对通知权限、模板及正式授权条件；无法跨过外部条件时明确报告，而非将岛设为唯一入口。此时任务4的球比例可独立交付，原生开关保持测试/回退状态。
- [ ] 原生链路通过后记录可用协议与设备边界，再提交该验证任务；不提前写入正式能力确认。

## Task 2：入口状态与配置迁移

**Files:** 新增core `overlay/NativeIslandEntryPolicy.kt`、`NativeIslandEntryPolicyTest.kt`；修改app `storage/AppSettings.kt`及`storage/AutomaticSettingsTest.kt`。

**Interfaces:** `enum class OverlayEntryMode { BUBBLE, ISLAND_TEST, ISLAND }`；`IslandEnvironment(allowed:Boolean,vivo:Boolean,notificationsEnabled:Boolean,osBuild:String)`；`NativeIslandEntryPolicy.mode(requested:Boolean,confirmedBuild:String?,environment:IslandEnvironment,published:Boolean):OverlayEntryMode`；`canConfirm(nativeClick:Boolean,currentLease:Boolean,panelShown:Boolean,environment:IslandEnvironment):Boolean`。设置新增`nativeIslandEnabled:Boolean=false`与`nativeIslandConfirmedBuild:String?=null`。

- [ ] 写并运行失败测试：`mode(true,null,eligible,true)==ISLAND_TEST`；notify已发布但未确认不等于ISLAND；`canConfirm(false,true,true,eligible)==false`；旧租约/面板未注册不确认；系统版本改变、权限关闭、非vivo、总开关关闭回到BUBBLE。配置`decode("{}")`默认不开启且不丢原有秒数、风格、密钥。
- [ ] 实现上述纯策略与两个设置字段；确认键取当前系统build标识，仅存在本地加密设置中。环境不符合时不调用原生发布；失败不改变原先球启用偏好。
- [ ] 运行`./tools/test-core.ps1 -Tests com.loveadvisor.core.overlay.NativeIslandEntryPolicyTest`；重编app后运行`./tools/test-settings.ps1`，预期0失败。
- [ ] 提交策略、迁移及相关测试。

## Task 3：开关、前台生命周期、状态更新与回退

**Files:** 新增app `island/NativeIslandCoordinator.kt`；修改`capture/ChatCaptureService.kt`、`settings/OverlaySettingsActivity.kt`、`ui/DiagnosticsActivity.kt`、`overlay/OverlayController.kt`与任务1设备测试。

**Interfaces:** 协调器`refreshEligibility(environment:IslandEnvironment):Unit`、`updateStatus(reading:Boolean,state:BatchState,current:Boolean):Unit`、`handleNativeTap(leaseId:String):Boolean`、`suspend():Unit`、`stop():Unit`、`destroy():Unit`；Overlay新增`showFromIsland():Boolean`、`setEntryMode(mode:OverlayEntryMode):Unit`。模式规则消费任务2策略，私有点击消费任务1租约。

- [ ] 写设备/策略行为测试并观察失败：开启但未确认仍保留球；普通通知点击不隐藏球；只有有效原生点击且面板注册后持久化确认。通知禁用/发布异常/取消/无障碍断开恢复球；已确认后系统build改变回到测试或球。旧回调不得复活已离开的应用入口。
- [ ] 在设置加入默认关闭开关和首次测试状态，按需请求POST_NOTIFICATIONS。将更新挂到现有前台资格与BatchState，而非新增聊天正文监听。构建状态只取有效当前批次：reading/loading为分析中，有有效六条为6条候选，错误为读取失败，否则待读取。
- [ ] 实现一次前台会话一个租约；更新复用租约，退出/锁屏/停止撤销。复用已接受的厂商模板，合并最新状态、遵守实际验证到的更新限制；结束立即执行，不让延迟状态回调重新上岛。截图暂停时暂时结束岛并保留已确认能力，恢复时先检查前台资格。
- [ ] 有效原生点击调用`showFromIsland()`，仅true后保存build确认并隐藏球。普通通知只诊断；未确认、失败、权限关闭时恢复球并显示可理解状态。若系统没有可靠显示反馈，保持保守验证语义，不把通知活跃状态当作岛显示证明。
- [ ] 运行完整core/settings及设备测试，确认打开、状态更新、切换、暂停都没有额外模型请求或正文采集；提交开关与生命周期集成。

## Task 4：原生专用面板与三分之一球几何

**Files:** 修改core `overlay/BubbleEdgeGeometry.kt`、`overlay/BubbleIdlePolicyTest.kt`；新增`overlay/IslandPanelGeometry.kt`、`IslandPanelGeometryTest.kt`；修改app `overlay/OverlayController.kt`、`overlay/AdvisorPanelView.kt`，新增`overlay/IslandPanelDeviceTest.kt`。

**Interfaces:** `IslandPanelGeometry.open(size:PanelBounds,available:RectI,topGapPx:Int):PanelBounds`；`enum class PanelDragMode { TITLE, TOP_GRIP }`；`AdvisorPanelView`追加默认`TITLE`的参数，暴露实际用于绑定的`dragHandle:View`；原生`showFromIsland()`传TOP_GRIP、独立锚点，普通show()保持TITLE和共同锚点。

- [ ] 更新球比例回归并观察失败：40像素宽球露14像素，物理右界390时hidden.left=376，物理左界0时hidden.left=-26；侧边导航栏不改变物理隐藏边界，极小区域至少1像素。保持已有默认15秒、自定义与动画取消测试。
- [ ] 添加失败几何测试：`open(PanelBounds(0,0,320,400),RectI(0,30,390,790),12)==PanelBounds(35,42,320,400)`；输入栏安全底边490时面板bottom<=490；短区域宽高为正。拖动、关闭再打开的结果重新等于此初始位置，不使用旧锚点。
- [ ] 实现隐藏露出宽度向上取整三分之一；普通面板不改drag绑定。原生面板加正中蓝色三点把手，触摸目标至少64dp宽、24dp高，只有把手绑定drag；原生标题不绑定drag，右上菜单与关闭仍可点击。
- [ ] 设备测试实际分发/注入触摸：原生标题/正文/按钮拖动不能改变窗口坐标，把手拖动能改变；正文滚动可以改变候选列表scrollY。普通球/标题仍共同移动。原生关闭再打开居中，已展开时再次点击岛只保持展开。键盘、方向与字体变化按实际可用区域调整，不覆盖聊天输入框。
- [ ] 运行完整core以及目标设备触摸/窗口用例；改设置和文案中的“四分之一”为“三分之一”，提交专用布局及比例修改。

## Task 5：回归、签名打包与交付

**Files:** `android/app/build.gradle.kts`、首页/关于的版本、构建/打包脚本、`docs/verification/user-guide.md`和本次验证说明；输出为`output/对话军师-V1.2.14.apk`及校验记录。

- [ ] 更新versionName=V1.2.14、versionCode=17和输出文件名。保存既有V1.2.13包；不更换应用ID或签名、不卸载/清除手机数据。
- [ ] 运行`./tools/test-core.ps1`、`./tools/build-android.ps1 -Tasks ':core:test',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug'`及`./tools/test-settings.ps1`，要求0测试失败、构建成功、Lint0错误；如有新警告说明影响。
- [ ] 仅在目标手机验证原生确认/状态/退出/回退/拖动/键盘，以及普通模式复制收起、填入草稿和风格。使用文件传输助手的空草稿；任何测试不点击发送。USB不稳定时交付明确测试APK让用户自行覆盖安装，不声称已真机通过。
- [ ] 在ASCII构建路径校验APK版本、原签名证书、16KB原生库对齐、45份知识资料；将已验证文件复制成中文交付名称并比较SHA256。
- [ ] 做一次独立整体代码审查，修复实际P1/P2后只针对新修改重测；记录原生是否真正通过、哪些设备行为尚未验证。APK的描述不能把普通通知或编译成功当作上岛成功。
- [ ] 精确提交源码和验证说明，交付APK链接与简短设置说明。是否上传新GitHub Release按用户后续发布指令处理，不自动替换已发布V1.2.12。

## 计划自检

规格的普通球、原生入口、首测回退、前台资格、状态、拖动、手动调用和回归分别由任务1–5覆盖；现有V1.2.13由任务0保护。接口在后续任务沿用相同名称；厂商协议须先通过真实设备，不能用猜测的字段放行正式唯一入口。此计划尚未执行，不代表设备支持或软件已修改。
