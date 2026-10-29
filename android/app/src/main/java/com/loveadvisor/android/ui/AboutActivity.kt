package com.loveadvisor.android.ui
import android.os.Bundle
import com.loveadvisor.android.BuildConfig
class AboutActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("关于对话军师","${BuildConfig.VERSION_NAME} · 多平台单聊与群聊按需读取")
        body.addView(ViewStyles.text(this,"基于 Jev v1.3 源码与狗头军师固定资料开发。融合聊天判断、关系知识与原创恋爱表达策略，每批六条回复，不使用模式切换。\n\nJev 源码：23b5ca56930960cfac470815ae582cd713eda95a\n狗头军师资料：6db7354a4002dc7c448a9c87ffdad8132570c9d3\n恋爱大师策略：本项目原创。\n\n支持Android 11及以上ARM64设备；只有完成真机报告的场景才属于实测通过。\n\n密钥、聊天档案与个人资料加密保存在本机。聊天与选定资料会发送到你配置的判断/回复服务；逐句判断结合全部已读上下文并标明所选消息；点击读取信息进行视觉识别时，压缩的聊天窗口截图会发送到你选择的视觉服务。截图不保存为聊天档案。单聊保留最近50条，群聊默认保留最近100条（可设50/100/200），只累计手动读取的消息，不按时间过期；每 12 小时清理超出范围的数据。\n\n候选回复由模型产生，核对事实、口吻与承诺后再发送。",15f))
        body.addView(ViewStyles.text(this,"候选面板支持系统原生轻毛玻璃和普通样式，不持续截图或采样背景。手动读取或确认导入后收集聊天并请求模型；真实开启时，候选完成后可用所选回复模型对新材料进行后台分类，下一次生成只取相关记忆；暂停学习或关闭真实可以停止分类。资料保存在本地加密文件，清除缓存保留；清除应用数据或卸载会删除。打开面板内风格设置会读取当前昵称，必要时在本机识别标题截图，不调用网络模型。省电模式或系统关闭模糊时使用清晰底色；早期玻璃组件的历史许可保留。",14f,ViewStyles.MUTED))
        body.addView(ViewStyles.text(this,"V1.5.0新增互惠表达资料，参考 hinge-profile-optimizer@55575ff 的具体表达与接话口原则，中文例句原创。内置知识是公开资料，脱敏诊断日志在本机独立保存。",14f,ViewStyles.MUTED))
        body.addView(ViewStyles.text(this,"V1.6真实独立实现有来源的口吻学习、对象资料与渐进互动估计，参考女娲（MIT）、溪语AI（MIT）、Humanizer-zh；wechatbot-new仅参考通用分层思路，未复制其GPL代码或提示词。好感度不是对真人内心的测量，记忆不会训练或改变云端模型权重。",14f,ViewStyles.MUTED))
        for(file in listOf("Nuwa-MIT.txt","XiYu-MIT.txt","Jev-MIT.txt","Goutou-MIT.txt","HingeProfileOptimizer-MIT.txt","LiquidGlass-MIT.txt","HumanizerZh-MIT.txt")) body.addView(ViewStyles.text(this,assets.open("notices/$file").bufferedReader().use { it.readText() },12f,ViewStyles.MUTED))
    }
}
