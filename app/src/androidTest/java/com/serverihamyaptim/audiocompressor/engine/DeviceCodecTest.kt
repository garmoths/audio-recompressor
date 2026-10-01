package com.serverihamyaptim.audiocompressor.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serverihamyaptim.audiocompressor.model.CodecSettings
import com.serverihamyaptim.audiocompressor.model.EncodeRequest
import com.serverihamyaptim.audiocompressor.model.OutputFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCodecTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun allOutputFormatsProduceReadableFilesOnDevice() {
        runBlocking {
            val dir = File(context.cacheDir, "device-codec-test").apply { deleteRecursively(); mkdirs() }
            val input = File(dir, "input.wav").also { writeTestWav(it) }
            val encoder = FfmpegAudioEncoder()
            OutputFormat.entries.forEach { format ->
                val output = File(dir, "output.${format.extension}")
                val result = encoder.encode(input, output, format, CodecSettings())
                assertTrue("$format failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
                assertTrue("$format output is empty", output.length() > 100)
            }
            dir.deleteRecursively()
        }
    }

    @Test fun mp3PipelineUsesPreviousGenerationOnPhysicalDevice() {
        runBlocking {
            val dir = File(context.cacheDir, "device-chain-test").apply { deleteRecursively(); mkdirs() }
            val input = File(dir, "input.wav").also { writeTestWav(it) }
            val request = EncodeRequest("device", "source", "input.wav", "unused", "chain",
                OutputFormat.MP3, CodecSettings(bitrateKbps = 128), 3, false)
            var publishedSize = 0L
            val uri = GenerationPipeline(FfmpegAudioEncoder()).run(request, input, dir,
                publish = { generation, file, final ->
                    if (final) publishedSize = file.length()
                    "device://generation/$generation"
                }, onProgress = {})
            assertTrue(uri == "device://generation/3")
            assertTrue(publishedSize > 100)
            dir.deleteRecursively()
        }
    }

    @Test fun activeTwoHundredGenerationJobStopsAndCleansFilesOnPhysicalDevice() {
        runBlocking {
            val dir = File(context.cacheDir, "device-cancel-test").apply { deleteRecursively(); mkdirs() }
            val input = File(dir, "input.wav").also { writeTestWav(it) }
            val request = EncodeRequest("cancel-device", "source", "input.wav", "unused", "cancel",
                OutputFormat.MP3, CodecSettings(bitrateKbps = 128), 200, false)
            val running = async {
                GenerationPipeline(FfmpegAudioEncoder()).run(request, input, dir,
                    publish = { _, _, _ -> "device://unexpected" }, onProgress = {})
            }
            delay(250)
            running.cancelAndJoin()
            assertTrue("Encode job remained active after cancellation", running.isCancelled)
            assertTrue("Temporary files remained after cancellation", dir.listFiles().orEmpty().isEmpty())
            dir.deleteRecursively()
        }
    }

    private fun writeTestWav(file: File) {
        val sampleRate = 44_100
        val seconds = 2
        val samples = sampleRate * seconds
        val dataSize = samples * 2
        file.outputStream().use { out ->
            fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            fun short(value: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
            out.write("RIFF".toByteArray()); out.write(int(36 + dataSize)); out.write("WAVEfmt ".toByteArray())
            out.write(int(16)); out.write(short(1)); out.write(short(1)); out.write(int(sampleRate))
            out.write(int(sampleRate * 2)); out.write(short(2)); out.write(short(16)); out.write("data".toByteArray()); out.write(int(dataSize))
            repeat(samples) { index ->
                val value = (kotlin.math.sin(2.0 * Math.PI * 440.0 * index / sampleRate) * 12_000).toInt()
                out.write(short(value))
            }
        }
    }
}
