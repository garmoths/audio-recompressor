package com.serverihamyaptim.audiocompressor.engine

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.serverihamyaptim.audiocompressor.model.CodecSettings
import com.serverihamyaptim.audiocompressor.model.OutputFormat
import java.io.File
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

interface AudioEncoder {
    suspend fun encode(input: File, output: File, format: OutputFormat, settings: CodecSettings): Result<Unit>
    fun cancel()
}

class FfmpegAudioEncoder : AudioEncoder {
    @Volatile private var activeSessionId: Long? = null

    override suspend fun encode(
        input: File,
        output: File,
        format: OutputFormat,
        settings: CodecSettings
    ): Result<Unit> {
        val first = execute(input, output, format, settings, settings.preserveMetadata)
        if (first.isSuccess || !settings.preserveMetadata) return first
        Log.w(TAG, "Metadata ile encode başarısız; güvenli metadata'sız geçiş deneniyor", first.exceptionOrNull())
        return execute(input, output, format, settings, false)
    }

    private suspend fun execute(
        input: File,
        output: File,
        format: OutputFormat,
        settings: CodecSettings,
        preserveMetadata: Boolean
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        output.delete()
        val args = buildList {
            addAll(listOf("-hide_banner", "-nostdin", "-y", "-i", input.absolutePath,
                "-map", "0:a:0", "-vn", "-sn", "-dn"))
            addAll(if (preserveMetadata) listOf("-map_metadata", "0") else listOf("-map_metadata", "-1"))
            settings.sampleRate?.let { addAll(listOf("-ar", it.toString())) }
            settings.channels?.let { addAll(listOf("-ac", it.toString())) }
            when (format) {
                OutputFormat.MP3 -> addAll(listOf("-c:a", "libmp3lame", "-b:a", "${settings.bitrateKbps}k"))
                OutputFormat.OGG -> addAll(listOf("-c:a", "libvorbis", "-q:a", settings.oggQuality.toString()))
                OutputFormat.WAV -> addAll(listOf("-c:a", "pcm_s${settings.wavBits}le"))
                OutputFormat.FLAC -> addAll(listOf("-c:a", "flac", "-compression_level", settings.flacLevel.toString()))
            }
            add(output.absolutePath)
        }
        val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { completed ->
            activeSessionId = null
            if (!continuation.isActive) return@executeWithArgumentsAsync
            val valid = ReturnCode.isSuccess(completed.returnCode) && output.isFile && output.length() > 0
            val diagnostic = completed.output?.takeLast(3000)?.trim()
            if (!valid) Log.e(TAG, "FFmpeg rc=${completed.returnCode}: $diagnostic")
            continuation.resume(
                if (valid) Result.success(Unit)
                else Result.failure(IllegalStateException(
                    completed.failStackTrace?.takeIf { it.isNotBlank() }
                        ?: diagnostic
                        ?: "FFmpeg encode işlemi başarısız oldu"
                ))
            )
        }
        activeSessionId = session.sessionId
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    override fun cancel() {
        activeSessionId?.let(FFmpegKit::cancel)
    }

    companion object { private const val TAG = "AudioEncoder" }
}
