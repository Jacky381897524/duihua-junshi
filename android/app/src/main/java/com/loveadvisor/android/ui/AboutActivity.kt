package com.loveadvisor.android.ui
import android.os.Bundle
class AboutActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("关于对话军师","V1.2.12 · 微信 · QQ · 抖音单聊按需读取")
        body.addView(ViewStyles.text(this,"基于 Jev v1.3 源码与狗头军师固定资料开发。融合聊天判断、关系知识与原创恋爱表达策略，每批六条回复，不使用模式切换。\n\nJev 源码：23b5ca56930960cfac470815ae582cd713eda95a\n狗头军师资料：6db7354a4002dc7c448a9c87ffdad8132570c9d3\n恋爱大师策略：本项目原创。\n\n目标设备为 vivo X300 / Android 16 / 微信 8.0.78；只有完成真机报告的场景才属于实测通过。\n\n资料与密钥加密保存在本机。聊天与选定资料会发送到你配置的判断/回复服务；点击读取信息进行视觉识别时，压缩的聊天窗口截图会发送到你选择的视觉服务。截图不保存为聊天档案。每个独立会话保留最近 50 条文字，不按时间过期；每 12 小时清理超出范围的数据。\n\n候选回复由模型产生，核对事实、口吻与承诺后再发送。",15f))
        body.addView(ViewStyles.text(this,"当前悬浮窗使用普通原生面板，不持续采样屏幕。只有点击读取信息才收集聊天并请求模型；玻璃组件的历史许可保留。",14f,ViewStyles.MUTED))
        for(file in listOf("Jev-MIT.txt","Goutou-MIT.txt","LiquidGlass-MIT.txt")) body.addView(ViewStyles.text(this,assets.open("notices/$file").bufferedReader().use { it.readText() },12f,ViewStyles.MUTED))
    }
}
