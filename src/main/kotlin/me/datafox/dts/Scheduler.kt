package me.datafox.dts

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
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
        return title
            .parse("%yy") { now.year.toString().takeLast(2) }
            .parse("%y") { now.year }
            .parse("%mm") { now.month.number.toString().padStart(2, '0') }
            .parse("%m") { now.month.number }
            .parse("%MM") { now.month.toString().substring(0, 3).capitalize() }
            .parse("%M") { now.month.toString().capitalize() }
            .parse("%dd") { now.day.toString().padStart(2, '0') }
            .parse("%d") { now.day }
            .parse("%DD") { now.dayOfWeek.toString().substring(0, 3).capitalize() }
            .parse("%D") { now.dayOfWeek.toString().capitalize() }
    }

    fun String.parse(code: String, replacement: () -> Any): String =
        if (contains(code)) replace(code.toRegex(), replacement().toString()) else this

    fun String.capitalize(): String = lowercase().replaceFirstChar { it.uppercase() }
}
