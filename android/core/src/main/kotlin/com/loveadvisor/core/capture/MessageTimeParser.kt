package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import java.time.*

/** Resolves only printed chat separators, against the device date at capture, never model-supplied epochs. */
object MessageTimeParser {
    private val pattern=Regex("^(?:(今天|昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天])|(?:(\\d{4})[年/.-])?(\\d{1,2})[月/.-](\\d{1,2})日?)?\\s*(?:(凌晨|早上|上午|中午|下午|晚上)\\s*)?(?:(\\d{1,2})[:：](\\d{2}))?$")
    fun isSeparator(text:String)=pattern.matchEntire(text.trim())?.groupValues?.let { it[1].isNotEmpty() || it[3].isNotEmpty() || it[6].isNotEmpty() }==true
    fun parse(label:String,readAtMillis:Long,zone:ZoneId=ZoneId.systemDefault(),previous:MessageTime?=null):MessageTime {
        val printed=label.trim().take(64)
        val resolved=runCatching {
            val match=pattern.matchEntire(printed) ?: return@runCatching null
            if(printed.isBlank() || readAtMillis<=0)return@runCatching null
            val g=match.groupValues
            val reference=Instant.ofEpochMilli(readAtMillis).atZone(zone)
            val today=reference.toLocalDate()
            val date=when {
                g[1] in listOf("今天","昨天","前天") -> today.minusDays(listOf("今天","昨天","前天").indexOf(g[1]).toLong())
                g[1].isNotEmpty() -> {
                    val day="一二三四五六日".indexOf(g[1].last().let { if(it=='天')'日' else it })+1
                    today.minusDays(Math.floorMod(today.dayOfWeek.value-day,7).toLong())
                }
                g[3].isNotEmpty() -> {
                    val year=g[2].toIntOrNull() ?: today.year
                    var date=LocalDate.of(year,g[3].toInt(),g[4].toInt())
                    if(g[2].isEmpty() && date>today)date=date.minusYears(1)
                    date
                }
                // A bare clock elsewhere on the screen cannot establish today's date.
                else -> previous?.epochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return@runCatching null
            }
            if(g[6].isEmpty()) {
                if(g[1].isEmpty() && g[3].isEmpty())return@runCatching null
                // A date-only label cannot establish an exact hour: use the end of that day.
                (date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1).takeIf { date<=today }
            } else {
                var hour=g[6].toInt();val minute=g[7].toInt()
                if(g[5].isNotEmpty()) {
                    require(hour in 0..12)
                    if(g[5] in listOf("下午","晚上","中午") && hour<12)hour+=12
                    else if(g[5] in listOf("凌晨","早上","上午") && hour==12)hour=0
                }
                date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli().takeIf { it<=readAtMillis+60000 }
            }
        }.getOrNull()
        val dateOnly=pattern.matchEntire(printed)?.groupValues?.get(6)?.isEmpty()==true
        return MessageTime(printed,resolved,dateOnly)
    }
    /** Header/status-bar clocks and bubble text are not time separators. */
    fun before(nodes:List<NodeRecord>,messageBounds:RectI,viewport:RectI,readAtMillis:Long,zone:ZoneId=ZoneId.systemDefault()):MessageTime? = nodes.filter {
        !it.editable && it.roleHint !in setOf("sender","avatar") && isSeparator(it.text.orEmpty()) &&
            it.bounds.top>=viewport.top+viewport.height*.085 && it.bounds.bottom<=messageBounds.top &&
            it.bounds.left+it.bounds.right in (viewport.left*2+viewport.width*.65).toInt()..(viewport.left*2+viewport.width*1.35).toInt()
    }.sortedBy { it.bounds.top }.fold<NodeRecord,MessageTime?>(null) { previous,node -> parse(node.text!!,readAtMillis,zone,previous) }
}
