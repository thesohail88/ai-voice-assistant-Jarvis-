package com.example.aiassistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

class SpeakerVerifier(private val context: Context) {

    private var enrolledEmbedding: FloatArray? = null
    private val embeddingFileName = "user_voice_profile.bin"

    init {
        loadEnrolledVoiceprint()
    }

    /**
     * Calculates the Cosine Similarity between two voice embedding vectors.
     * Returns a score between -1.0 and 1.0 (1.0 = exact match).
     */
    fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Checks if the incoming voice embedding matches the stored owner profile.
     */
    fun isAuthorizedUser(liveEmbedding: FloatArray, threshold: Float = 0.75f): Boolean {
        val enrolled = enrolledEmbedding ?: return true // If not enrolled yet, allow all
        val similarity = computeCosineSimilarity(liveEmbedding, enrolled)
        return similarity >= threshold
    }

    /**
     * Saves your vocal embedding to private internal storage.
     */
    fun saveEnrolledVoiceprint(embedding: FloatArray) {
        enrolledEmbedding = embedding
        val file = File(context.filesDir, embeddingFileName)
        file.outputStream().use { output ->
            val byteBuffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
            embedding.forEach { byteBuffer.putFloat(it) }
            output.write(byteBuffer.array())
        }
    }

    private fun loadEnrolledVoiceprint() {
        val file = File(context.filesDir, embeddingFileName)
        if (!file.exists()) return

        val bytes = file.readBytes()
        val floatBuffer = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer()
        val array = FloatArray(floatBuffer.remaining())
        floatBuffer.get(array)
        enrolledEmbedding = array
    }

    fun isEnrolled(): Boolean = enrolledEmbedding != null
}
