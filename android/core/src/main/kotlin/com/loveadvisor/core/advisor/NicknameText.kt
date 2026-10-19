package com.loveadvisor.core.advisor

import java.text.Normalizer

/** Normalize canonically equivalent Unicode without removing nickname symbols. */
object NicknameText {
    fun canonical(value:String)=Normalizer.normalize(value.trim(),Normalizer.Form.NFC)
    fun valid(value:String)=value.isNotBlank() && value.length<=4096 && value.codePointCount(0,value.length)<=512
    /** For suggestions only: compatibility folds must never silently select a contact. */
    fun suggestionKey(value:String)=Normalizer.normalize(canonical(value),Normalizer.Form.NFKC)
        .replace(Regex("[\\p{Z}\\s]+")," ").replace("\uFE0E","").replace("\uFE0F","")
}
