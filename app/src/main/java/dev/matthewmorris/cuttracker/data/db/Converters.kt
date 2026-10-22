package dev.matthewmorris.cuttracker.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter fun toLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromTags(value: Set<String>): String = encodeTags(value)

    @TypeConverter fun toTags(value: String): Set<String> =
        value.split(',').filter { it.isNotBlank() }.toSet()

    companion object {
        fun encodeTags(tags: Set<String>): String = tags.sorted().joinToString(",")
    }
}
