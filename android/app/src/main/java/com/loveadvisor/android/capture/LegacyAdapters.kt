package com.loveadvisor.android.capture
import com.loveadvisor.core.capture.*
/** v1.3 QQ/X/Lark rules ported to immutable nodes. New versions need device validation. */
class LegacyAdapters { private val parser=LegacyNodeParser(); fun extract(window:WindowSnapshot)=parser.extract(window) }
