package com.serverihamyaptim.audiocompressor.engine

import com.serverihamyaptim.audiocompressor.model.EncodeProgress
import com.serverihamyaptim.audiocompressor.model.EncodeRequest
import com.serverihamyaptim.audiocompressor.model.JobStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GenerationPipeline(private val encoder: AudioEncoder) {
    suspend fun run(
        request: EncodeRequest,
        original: File,
        workDir: File,
        publish: suspend (generation: Int, file: File, isFinal: Boolean) -> String?,
        onProgress: (EncodeProgress) -> Unit
    ): String? {
        val started = System.currentTimeMillis()
        var previous = original
        var finalUri: String? = null
        try {
            for (generation in 1..request.iterations) {
                currentCoroutineContext().ensureActive()
                val output = File(workDir, "generation_${generation % 2}.${request.format.extension}")
                if (output == previous) error("Girdi ve çıktı aynı dosya olamaz")
                output.delete()
                onProgress(EncodeProgress(request.jobId, generation - 1, request.iterations,
                    (generation - 1f) / request.iterations, System.currentTimeMillis() - started,
                    previous.length(), JobStatus.RUNNING))
                encoder.encode(previous, output, request.format, request.settings).getOrElse { cause ->
                    throw IllegalStateException("Nesil $generation encode edilemedi: ${cause.message}", cause)
                }
                check(output.length() > 0) { "Nesil $generation boş çıktı üretti" }
                val isFinal = generation == request.iterations
                if (request.keepEveryGeneration || isFinal) {
                    finalUri = publish(generation, output, isFinal) ?: finalUri
                }
                if (previous != original) previous.delete()
                previous = output
                onProgress(EncodeProgress(request.jobId, generation, request.iterations,
                    generation.toFloat() / request.iterations, System.currentTimeMillis() - started,
                    output.length(), JobStatus.RUNNING))
            }
            return finalUri
        } catch (cancelled: CancellationException) {
            encoder.cancel()
            throw cancelled
        } finally {
            workDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun cancel() = encoder.cancel()
}
