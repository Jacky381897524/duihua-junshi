package com.loveadvisor.core.diagnostics

data class ProcessExitRecord(val timestampMillis:Long,val pid:Int,val reason:Int,val status:Int)

object ProcessExitSummary {
    fun describe(records:List<ProcessExitRecord>,formatTime:(Long)->String):String {
        val rows=records.filter { it.timestampMillis>=0 && it.pid>0 }.sortedByDescending { it.timestampMillis }.take(5)
        if(rows.isEmpty())return "没有可用的进程退出记录；不能据此判断没有发生故障。"
        return rows.joinToString("\n") {
            "${formatTime(it.timestampMillis)} · PID ${it.pid} · 原因码 ${it.reason} · ${reason(it.reason)} · 状态码 ${it.status}"
        }+"\n记录对应的军师版本未知，可能来自覆盖安装前。进程退出不等于无障碍服务崩溃；需结合故障时间核对。Android 14之前，更新或组件变化也可能记为用户请求终止。"
    }

    private fun reason(value:Int)=when(value) {
        1 -> "进程自行退出"
        2 -> "系统信号终止"
        3 -> "内存不足"
        4 -> "Java 崩溃"
        5 -> "原生代码崩溃"
        6 -> "无响应（ANR）"
        7 -> "进程初始化失败"
        8 -> "运行时权限变化"
        9 -> "资源使用限制"
        10 -> "用户请求终止"
        11 -> "所属系统用户停止"
        12 -> "依赖进程退出"
        13 -> "系统记录的其他原因"
        14 -> "冻结进程相关终止"
        15 -> "软件包状态变化"
        16 -> "安装更新"
        else -> "未知原因（$value）"
    }
}
