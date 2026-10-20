package com.loveadvisor.core.input

/** Focus acknowledgement is separate from the later arrival of a usable input connection. */
enum class ComposerFocusResult(val failureMessage:String?=null) {
    REQUESTED,
    KEYBOARD_VISIBLE,
    NO_REFERENCE("本次读取未保留输入框位置，请先点聊天输入框后重试"),
    NO_COMPOSER("本次读取未定位到文字输入框，请先点聊天输入框后重试"),
    CONTEXT_CHANGED("当前聊天已变化，已保留原稿"),
    UNVERIFIED_LAYOUT("无法确认输入框位置，请先点聊天输入框后重试"),
    REJECTED("系统未接受输入框聚焦，请点聊天输入框后重试"),
    CANCELLED("输入框聚焦被中断，请点聊天输入框后重试"),
    TIMED_OUT("输入框聚焦超时，请点聊天输入框后重试")
}
