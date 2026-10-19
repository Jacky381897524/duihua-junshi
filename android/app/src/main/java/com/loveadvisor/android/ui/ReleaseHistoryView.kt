package com.loveadvisor.android.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Local release notes: no network request or account is needed to read the history. */
class ReleaseHistoryView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        addView(ViewStyles.text(context, "从首次开发到 V1.3.11 · 共 ${releases.size} 个版本，点击版本展开", 13f, ViewStyles.MUTED))
        releases.forEachIndexed { index, release ->
            if (index > 0) addView(View(context).apply {
                setBackgroundColor(ViewStyles.themed(context, ViewStyles.BG))
            }, LayoutParams(-1, ViewStyles.dp(context, 1f)))
            val header = ViewStyles.row(context).apply {
                minimumHeight = ViewStyles.dp(context, 52f)
                isFocusable = true
            }
            val labels = ViewStyles.column(context)
            labels.addView(ViewStyles.text(context, release.version + if (index == 0) " · 当前版本" else "", 15f, bold = true))
            labels.addView(ViewStyles.text(context, release.title, 13f, ViewStyles.MUTED).apply {
                setPadding(0, 0, 0, ViewStyles.dp(context, 8f))
            })
            header.addView(labels, LayoutParams(0, -2, 1f))
            val indicator = ViewStyles.text(context, "", 22f, ViewStyles.BLUE).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            header.addView(indicator, LayoutParams(ViewStyles.dp(context, 28f), -2))
            addView(header, LayoutParams(-1, -2))
            val details = ViewStyles.column(context).apply {
                setPadding(0, 0, 0, ViewStyles.dp(context, 10f))
            }
            release.changes.forEach { details.addView(ViewStyles.text(context, "• $it", 14f, ViewStyles.MUTED)) }
            addView(details, LayoutParams(-1, -2))
            fun expanded(value: Boolean) {
                details.visibility = if (value) View.VISIBLE else View.GONE
                indicator.text = if (value) "−" else "+"
                header.contentDescription = "${release.version}，${release.title}"
                header.stateDescription = if (value) "已展开，点击收起" else "已收起，点击展开"
            }
            expanded(index == 0)
            header.setOnClickListener { expanded(details.visibility != View.VISIBLE) }
        }
    }

    private data class Release(val version: String, val title: String, val changes: List<String>)

    companion object {
        // Includes the early 0.x test packages; numbering changed to V1.x at V1.2.12.
        private val releases = listOf(
            Release("V1.3.11", "特殊昵称与面板内风格设置", listOf(
                "昵称匹配保留外文、花体、符号和组合表情，兼容等价字符编码，不再排除含标点或较长表情的标题。",
                "面板菜单可直接设置当前对象专属风格或全对象风格，无需跳转主页；昵称可粘贴校正，也可选择已保存对象关联。",
                "确认后的识别别名随专属风格保存在本机，清除缓存不会删除；相似昵称只提供选择，不自动合并。",
                "只按昵称匹配，不读取头像或账号，不打开对方资料页。识别仍可能漏字，未匹配时继续使用全对象风格。"
            )),
            Release("V1.3.10", "清除缓存与安卓兼容增强", listOf(
                "主页新增“清除缓存”，显示缓存大小和清理结果，保留密钥、模型配置、回复风格、关系记忆与聊天历史。",
                "页面和候选面板避让横屏侧边挖孔、刘海及系统导航区域，胶囊位置增加挖孔碰撞避让。",
                "候选操作按钮随宽度分配空间，菜单与部分控件适应大字体及窄屏；保留较薄的底部收起区。",
                "权限入口增加标准系统设置备用路径，保留 Android 11+、ARM64 范围及毛玻璃自动降级；具体机型仍需实测。"
            )),
            Release("V1.3.09", "减轻毛玻璃模糊强度", listOf(
                "降低背景模糊半径，让面板后方的聊天轮廓更清楚，保留轻毛玻璃的柔和质感。",
                "继续使用系统原生模糊与布局缓存，可在悬浮窗设置中恢复普通面板。"
            )),
            Release("V1.3.08", "轻毛玻璃与面板性能优化", listOf(
                "新增系统原生轻毛玻璃：仅面板内背景柔和模糊，文字和按钮保持清晰；悬浮窗设置可切回普通面板。",
                "限制模糊强度，不使用持续截图、WebView或背景轮询；面板隐藏后停止效果监听，省电或系统不支持时使用清晰底色。",
                "缓存面板高度测量，拖动时复用；候选、详情、菜单和文字布局变化时更新尺寸。"
            )),
            Release("V1.3.07", "收起箭头居中与底部留白", listOf(
                "收起箭头改为在浅色横线下方的区域居中，移除叠加的向下偏移。",
                "底部收起区域略微加高，增加箭头下方留白，保持整条横向可点击。"
            )),
            Release("V1.3.06", "独立更新记录页面与收起按钮微调", listOf(
                "主页仅保留“更新记录”按钮，点击进入独立页面查看全部历史更新，减少主页占用。",
                "面板底部浅色横线与刷新按钮的距离稍增；上箭头缩小、变细，保留较薄底栏与整条横向点击范围。"
            )),
            Release("V1.3.05", "收起按钮样式与完整更新记录", listOf(
                "面板底部改为较粗的蓝色上箭头，增加淡色分隔线，保持较薄的高度与整条横向点击区域。",
                "主页“关于与许可”下方新增更新记录，收录从初版到当前版的每次更新，可逐个展开查看。"
            )),
            Release("V1.3.04", "恢复 V1.2.13 识别流程", listOf(
                "恢复 V1.2.13 的原生文字读取与视觉识别流程，撤回新增的强制 OCR、气泡及头像位置校验。",
                "保留最后一句由我发出时，六条候选都以我的身份接着说的规则。",
                "保留胶囊锁定、大小调节、菜单自适应与较薄的底部收起区域。"
            )),
            Release("V1.3.03", "识别核验测试版（识别方案后已撤回）", listOf(
                "尝试用本地中文 OCR、气泡和头像位置核验视觉识别，区分正文、前文引用及语音转文字。",
                "边缘裁切的消息单独排除，避免影响完整消息；改进历史上下文与当前最后一句的衔接。",
                "缩薄面板底部收起区域。此版识别仍出现误拒绝，新增识别方案已在 V1.3.04 撤回。"
            )),
            Release("V1.3.02", "菜单完整展示与读取提示", listOf(
                "短面板打开菜单时自动加长，关闭菜单后恢复；可用空间不足时菜单支持滚动。",
                "胶囊右侧留白略微增加，读取失败提示增加具体原因。",
                "调整当时的视觉坐标兼容处理，便于定位读取失败。"
            )),
            Release("V1.3.01", "胶囊控制与对话身份", listOf(
                "菜单加入锁定胶囊：锁定为红色、解锁为绿色，记住选择；大小可在 70%—200% 连续调节。",
                "缩短胶囊右侧留白，面板底部加入横向大范围可点击的收起按钮。",
                "加强左侧为对方、右侧为我的区分；最后一句是我时，候选继续我的表达。",
                "填入失败明确提示，复制备用时说明失败；回复风格设置移到主页和菜单顶部，移除权限页重复的悬浮球开启按钮。"
            )),
            Release("V1.2.15", "左上角常驻胶囊", listOf(
                "新增时钟右侧、摄像头左侧的应用胶囊入口，无需等待系统通知即可显示。",
                "仅在微信、QQ、抖音前台显示，点击打开面板，长按可拖动；开启后替代普通悬浮球，关闭后恢复。",
                "面板仅通过顶部三点拖动；修正屏幕边界限制后再次拖动的起点。"
            )),
            Release("V1.2.14", "原子岛实验入口与拖动手柄", listOf(
                "新增实验性系统原子岛入口及开关，通过持续通知尝试接入；能否显示受系统能力与权限影响。",
                "系统入口不可用时恢复原有入口，并区分等待与失败状态。",
                "悬浮球贴边后露出约三分之一；面板增加顶部三点手柄，仅按住指定区域拖动。"
            )),
            Release("V1.2.13", "悬浮球自动贴边隐藏", listOf(
                "悬浮球闲置后自动隐藏到屏幕边缘，点击后完整露出，带隐藏和展开的过渡动画。",
                "默认等待 15 秒，用户可自定义隐藏等待时间；减少动效设置继续有效。"
            )),
            Release("V1.2.12", "作者信息与全对象回复风格", listOf(
                "版本名称改为 V1.2.12，主页底部加入作者海街寺庙与圆形头像。",
                "新增全对象回复风格，无需匹配联系人名称；有专属风格时优先专属，否则使用全对象风格。",
                "全对象风格可清空，专属风格继续独立保存、查看和删除。"
            )),
            Release("0.2.12", "缩短空草稿填入等待", listOf(
                "减少空输入框填入时重复截图和等待，优化写入后的读回时机。",
                "保留已有草稿替换确认、会话验证和写后核验；填入不自动发送。"
            )),
            Release("0.2.11", "修复有效输入连接被拒绝", listOf(
                "兼容系统返回“绝对偏移未知”的有效草稿，修复有输入连接却不能填入的情况。",
                "保留选区、完整草稿、当前聊天与超时检查。"
            )),
            Release("0.2.10", "微信、QQ、抖音与键盘避让", listOf(
                "支持 QQ、抖音原生一对一私信的悬浮窗及按需读取，不同应用的上下文和对象风格分别保存。",
                "键盘弹出时面板上移并缩短，预留聊天输入栏；修复展开更多菜单后面板消失。",
                "点击复制自动收起面板；修正输入框焦点事件导致会话失效、填入失败的问题。"
            )),
            Release("0.2.9", "对话军师、独立风格与自定义判断", listOf(
                "软件名称改为“对话军师”，回复风格独立成设置页；对象列表支持查看、编辑和逐个删除。",
                "判断模型新增自定义接口，OpenRouter Jev 仍为默认与首选。",
                "增加无障碍输入连接备用填入路径、草稿替换确认和输入法出现时的面板调整。"
            )),
            Release("0.2.8", "恢复普通候选面板", listOf(
                "候选窗与草稿确认恢复不透明原生面板，停止使用液态玻璃和背景采样。",
                "取消候选正文的固定最小高度，按钮紧跟正文，按实际行数自动下移并修复底部裁切。"
            )),
            Release("0.2.7", "三条可见候选与回复风格", listOf(
                "面板按前三条候选的实际高度显示，六条仍可滑动选择；缩小字体与悬浮球。",
                "新增按对象保存的自由描述回复风格，删除后恢复默认；填入后可选自动收起或保持展开。",
                "曾加入液态玻璃与背景折射效果，后按反馈在 0.2.8 恢复普通面板。"
            )),
            Release("0.2.6", "点击读取与新羽扇图标", listOf(
                "改为点悬浮图标展开、再点“读取信息”采集和生成；进入聊天或聊天变化不再自动请求模型。",
                "刷新使用已读上下文，保留六条候选与最近 50 条上下文。",
                "更换明黄底羽扇图标，调整面板高度、卡片及按钮边缘。"
            )),
            Release("0.2.5", "修复无障碍服务崩溃", listOf(
                "修复悬浮图标重复添加到系统窗口导致无障碍服务故障的问题。",
                "改进窗口添加、隐藏、恢复与失败后重试的状态管理。"
            )),
            Release("0.2.4", "图标面板联动与前台显示", listOf(
                "图标与面板顶部对齐、贴边并联动拖动，缩短面板、缩小字体，更换羽扇图标。",
                "只在微信前台显示，退出自动隐藏；排除小程序和网页窗口。",
                "改进空节点树、窗口范围与慢速读取的处理，诊断增加节点数量和读取阶段。"
            )),
            Release("0.2.3", "微信单聊识别兼容", listOf(
                "输入框缺失或节点树为空时尝试视觉识别，不再直接判为非聊天页面。",
                "减少对旧控件 ID 的依赖，改进当前窗口选择及群聊、主页、资料页的排除。",
                "区分无窗口、其他应用与非单聊提示，改进历史记录浏览和迟到结果处理。"
            )),
            Release("0.2.2", "悬浮窗显示与恢复", listOf(
                "无障碍服务连接后显示悬浮球，展开面板仍保留球，关闭按钮只收起面板。",
                "当时支持离开微信保留悬浮窗并暂停读取；后在 0.2.4 改为仅微信前台显示。",
                "改进锁屏、解锁与手动截图期间的窗口隐藏和恢复。"
            )),
            Release("0.2.1", "悬浮窗性能与连接测试", listOf(
                "移除图标和面板的黑色抬升阴影，修复关闭及前后台切换的处理。",
                "节点读取移到后台，合并频繁事件，减少重复渲染。",
                "增加判断、回复、视觉接口的独立连接测试与耗时提示，改进模型目录展示。"
            )),
            Release("0.2.0", "自动微信聊天测试版", listOf(
                "接入微信单聊节点读取与必要时的视觉识别，区分左右说话人，保留最近 50 条上下文。",
                "生成六条候选，由 Jev 判断意图、风险和适合程度并排序；加入会话隔离与旧结果失效处理。",
                "这一版曾按对话变化自动读取，后在 0.2.6 改为手动点击读取。"
            )),
            Release("0.1.0", "初始开发版本", listOf(
                "结合 jev-chat-jarvis、goutoujunshi-jev-chat 的开源资料与原创恋爱大师策略，内置 45 份知识及实用文档。",
                "提供六种思路的候选回复、详情、刷新、完整咨询与草稿润色。",
                "加入模型配置、聊天与截图导入、逐轮模拟练习、多对象比较、关系档案、记忆和笔记。",
                "建立悬浮窗、读取与填入流程，本地敏感数据加密保存；用户自行决定发送。"
            ))
        )
    }
}
