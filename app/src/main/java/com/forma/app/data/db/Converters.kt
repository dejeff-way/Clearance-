package com.forma.app.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val ser = ListSerializer(String.serializer())

    @TypeConverter fun fromList(v: List<String>): String = Json.encodeToString(ser, v)
    @TypeConverter fun toList(v: String): List<String> = if (v.isBlank()) emptyList() else Json.decodeFromString(ser, v)
}
