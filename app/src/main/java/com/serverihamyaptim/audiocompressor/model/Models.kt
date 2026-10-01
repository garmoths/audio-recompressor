package com.serverihamyaptim.audiocompressor.model

enum class OutputFormat(val extension: String, val label: String) {
    MP3("mp3", "MP3"), OGG("ogg", "OGG Vorbis"), WAV("wav", "WAV"), FLAC("flac", "FLAC")
}

enum class JobStatus { IDLE, QUEUED, RUNNING, STOPPING, COMPLETED, FAILED, CANCELLED }

data class CodecSettings(
    val bitrateKbps: Int = 128,
    val oggQuality: Int = 5,
    val wavBits: Int = 16,
    val flacLevel: Int = 5,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val preserveMetadata: Boolean = true
)

data class EncodeRequest(
    val jobId: String,
    val sourceUri: String,
    val sourceName: String,
    val destinationTreeUri: String,
    val baseName: String,
    val format: OutputFormat,
    val settings: CodecSettings,
    val iterations: Int,
    val keepEveryGeneration: Boolean
) {
    init { require(iterations in 1..200); require(baseName.isNotBlank()) }
}

data class EncodeProgress(
    val jobId: String = "",
    val generation: Int = 0,
    val total: Int = 0,
    val percent: Float = 0f,
    val elapsedMs: Long = 0,
    val currentBytes: Long = 0,
    val status: JobStatus = JobStatus.IDLE,
    val message: String? = null
)

data class EncodeResult(
    val jobId: String,
    val completedGenerations: Int,
    val finalUri: String?,
    val inputBytes: Long,
    val outputBytes: Long,
    val elapsedMs: Long,
    val error: String? = null
)
