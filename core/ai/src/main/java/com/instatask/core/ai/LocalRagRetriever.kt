package com.instatask.core.ai

import com.instatask.core.database.InstaTaskDao
import com.instatask.core.database.MemoryChunkEntity
import com.instatask.core.model.MemorySource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface OnDeviceTextEmbedder {
    suspend fun embed(text: String): FloatArray
}

@Singleton
class DeterministicTextEmbedder @Inject constructor() : OnDeviceTextEmbedder {
    override suspend fun embed(text: String): FloatArray {
        val vector = FloatArray(384)
        text.lowercase().forEachIndexed { index, char ->
            val bucket = (char.code * 37 + index) % vector.size
            vector[bucket] += 1f
        }
        val norm = sqrt(vector.fold(0f) { sum, value -> sum + value * value })
        if (norm > 0f) for (i in vector.indices) vector[i] /= norm
        return vector
    }
}

@Singleton
class LocalRagRetriever @Inject constructor(
    private val dao: InstaTaskDao,
    private val textEmbedder: OnDeviceTextEmbedder,
) {
    suspend fun indexMemory(source: MemorySource, text: String) {
        val cleaned = text.trim().take(300)
        if (cleaned.isBlank()) return
        dao.insertMemoryChunk(
            MemoryChunkEntity(
                chunkId = UUID.randomUUID().toString(),
                sourceType = source,
                contentText = cleaned,
                embedding = textEmbedder.embed(cleaned),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun retrieveTopK(queryText: String, k: Int = 3): List<String> {
        val chunks = dao.getAllMemoryChunks()
        if (chunks.isEmpty()) return emptyList()
        val queryVec = textEmbedder.embed(queryText)
        return chunks
            .map { it.contentText to cosineSimilarity(queryVec, it.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
