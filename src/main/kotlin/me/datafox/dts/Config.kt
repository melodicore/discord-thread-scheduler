package me.datafox.dts

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class Config(val timezone: TimeZone = TimeZone.UTC, val channels: Map<String, ChannelConfig>)

@Serializable data class ChannelConfig(val threads: Map<String, ThreadConfig>)

@Serializable data class ThreadConfig(val title: String, val pin: PinConfig = PinConfig(), val period: PeriodConfig)

@Serializable data class PinConfig(val pin: Boolean = false, val unpin: Boolean = false)

@Serializable sealed interface PeriodConfig

@Serializable @SerialName("daily") data class DailyConfig(val time: TimeConfig) : PeriodConfig

@Serializable @SerialName("weekly") data class WeeklyConfig(val time: TimeConfig, val day: DayOfWeek) : PeriodConfig

@Serializable @SerialName("monthly") data class MonthlyConfig(val time: TimeConfig, val day: Int) : PeriodConfig

@Serializable data class TimeConfig(val hour: Int, val minute: Int = 0, val second: Int = 0)
