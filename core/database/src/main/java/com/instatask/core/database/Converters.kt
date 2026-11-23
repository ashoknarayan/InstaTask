package com.instatask.core.database

import androidx.room.TypeConverter
import com.instatask.core.model.PlanStep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPlanSteps(steps: List<PlanStep>): String = json.encodeToString(steps)

    @TypeConverter
    fun toPlanSteps(raw: String): List<PlanStep> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)

    @TypeConverter
    fun fromFloatArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach(buffer::putFloat)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}
