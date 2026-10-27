package dev.dux.organizer.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Instants as UTC milliseconds; dates without time as epoch days (independent of the time zone).
 * Other enums use Room's built-in by-name converter.
 */
class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    // ISO text ("2026-10-26T09:00"): a local time has no millisecond equivalent without a zone.
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    // shortcut: an unreadable rule becomes null (stops repeating) instead of crashing; only the app writes it.
    @TypeConverter fun fromRecurrence(value: Recurrence?): String? = value?.format()
    @TypeConverter fun toRecurrence(value: String?): Recurrence? = value?.let(Recurrence::parse)

    @TypeConverter fun fromPriority(value: Priority): Int = value.level
    @TypeConverter fun toPriority(value: Int): Priority = Priority.entries.first { it.level == value }
}
