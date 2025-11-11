package me.datafox.dts

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import me.datafox.dts.ExitCode.Companion.logAndExit
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object Scheduler {
    private val log = LoggerFactory.getLogger(Scheduler::class.java)

    fun launch(config: Config, jda: JDA) {
        jda.awaitReady()
        runBlocking { config.channels.forEach { initChannel(jda, config.timezone, it.key, it.value) } }
    }

    fun CoroutineScope.initChannel(jda: JDA, timeZone: TimeZone, id: String, config: ChannelConfig) {
        val channel = jda.getGuildChannelById(id) ?: ExitCode.CHANNEL_NOT_FOUND.logAndExit(log, id)
        if (channel !is StandardGuildMessageChannel)
            ExitCode.NOT_MESSAGE_CHANNEL.logAndExit(log, "${channel.name}: ${channel.type}")
        config.threads.forEach { launch(Dispatchers.IO) { schedule(channel, timeZone, it.key, it.value) } }
    }

    suspend fun schedule(
        channel: StandardGuildMessageChannel,
        timeZone: TimeZone,
        threadId: String,
        config: ThreadConfig,
    ) {
        val delayFn =
            when (config.period) {
                is DailyConfig -> dailyDelay(timeZone, config.period)
                is WeeklyConfig -> weeklyDelay(timeZone, config.period)
                is MonthlyConfig -> monthlyDelay(timeZone, config.period)
            }
        log.info("dts: scheduled thread \"$threadId\"")
        while (true) {
            val now = Clock.System.now()
            val delay = delayFn(now.toLocalDateTime(timeZone))
            val next = (now + delay.milliseconds).toLocalDateTime(timeZone)
            log.info("dts: next thread \"$threadId\" creation at ${next.format(LocalDateTime.Formats.ISO)}")
            delay(delay)
            val title = parseTitle(timeZone, config.title)
            log.info("dts: creating thread \"$threadId\" with title \"$title\"")
            val message = channel.sendMessage(title).complete()
            log.info("dts: thread \"$threadId\" message sent")
            message.createThreadChannel(title).complete()
            log.info("dts: thread \"$threadId\" created")
            if (config.pin.pin) {
                val path = Path(System.getProperty("user.dir"), "$threadId.pin")
                if (config.pin.unpin && SystemFileSystem.exists(path)) {
                    val id = SystemFileSystem.source(path).buffered().use { it.readString().trim() }
                    if (!id.isBlank()) log.info("dts: thread \"$threadId\" found previous pinned message $id")
                    try {
                        channel.retrieveMessageById(id).complete().unpin().complete()
                        log.info("dts: thread \"$threadId\" previous message $id unpinned")
                    } catch (e: ErrorResponseException) {
                        log.warn("dts: ${e.message}")
                    }
                }
                log.info("dts: thread \"$threadId\" pinning message ${message.id}")
                SystemFileSystem.sink(path).buffered().use { it.writeString(message.id) }
                message.pin().complete()
                log.info("dts: thread \"$threadId\" message ${message.id} pinned")
            }
            delay(2.hours)
        }
    }

    fun dailyDelay(timeZone: TimeZone, period: DailyConfig): (LocalDateTime) -> Long = {
        val time = LocalDateTime(it.year, it.month, it.day, period.time.hour, period.time.minute, period.time.second)
        var delay = time.toInstant(timeZone) - it.toInstant(timeZone)
        if (delay.isNegative()) delay += 1.days
        delay.inWholeMilliseconds
    }

    fun weeklyDelay(timeZone: TimeZone, period: WeeklyConfig): (LocalDateTime) -> Long = {
        var time = LocalDateTime(it.year, it.month, it.day, period.time.hour, period.time.minute, period.time.second)
        if (time.dayOfWeek != period.day) {
            var days = period.day.isoDayNumber - time.dayOfWeek.isoDayNumber
            if (days < 0) days += 7
            time = LocalDateTime(time.year, time.month, time.day + days, time.hour, time.minute, time.second)
        }
        var delay = time.toInstant(timeZone) - it.toInstant(timeZone)
        if (delay.isNegative()) delay += 7.days
        delay.inWholeMilliseconds
    }

    fun monthlyDelay(timeZone: TimeZone, period: MonthlyConfig): (LocalDateTime) -> Long = {
        var time =
            LocalDateTime(it.year, it.month, period.day, period.time.hour, period.time.minute, period.time.second)
        var delay = time.toInstant(timeZone) - it.toInstant(timeZone)
        if (delay.isNegative()) {
            val month = if (it.month == Month.DECEMBER) Month.JANUARY else Month.entries[it.month.ordinal + 1]
            val year = if (it.month == Month.DECEMBER) it.year + 1 else it.year
            time = LocalDateTime(year, month, time.day, time.hour, time.minute, time.second)
            delay = time.toInstant(timeZone) - it.toInstant(timeZone)
        }
        delay.inWholeMilliseconds
    }

    fun parseTitle(timeZone: TimeZone, title: String): String {
        val now = Clock.System.now().toLocalDateTime(timeZone)
        var title = title
        if (title.contains("%yy")) title = title.replace("%yy".toRegex(), now.year.toString().takeLast(2))
        if (title.contains("%y")) title = title.replace("%y".toRegex(), now.year.toString())
        if (title.contains("%mm")) title = title.replace("%mm".toRegex(), now.month.number.toString().padStart(2, '0'))
        if (title.contains("%m")) title = title.replace("%m".toRegex(), now.month.number.toString())
        if (title.contains("%M"))
            title = title.replace("%M".toRegex(), now.month.toString().lowercase().replaceFirstChar { it.uppercase() })
        if (title.contains("%dd")) title = title.replace("%dd".toRegex(), now.day.toString().padStart(2, '0'))
        if (title.contains("%d")) title = title.replace("%d".toRegex(), now.day.toString())
        if (title.contains("%D"))
            title =
                title.replace("%D".toRegex(), now.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() })
        return title
    }
}
