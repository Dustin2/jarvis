package dev.dux.organizer.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Instantes en milisegundos UTC; fechas sin hora como día epoch (no dependen de la zona horaria).
 * Los demás enums usan el conversor por nombre que trae Room.
 */
class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    // Texto ISO ("2026-10-26T09:00"): una hora local no tiene equivalente en milisegundos sin zona.
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter fun fromPriority(value: Priority): Int = value.level
    @TypeConverter fun toPriority(value: Int): Priority = Priority.entries.first { it.level == value }
}
