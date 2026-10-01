package com.serverihamyaptim.audiocompressor.engine

import com.serverihamyaptim.audiocompressor.model.CodecSettings
import com.serverihamyaptim.audiocompressor.model.EncodeRequest
import com.serverihamyaptim.audiocompressor.model.OutputFormat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerationPipelineTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `each generation consumes previous output for one hundred generations`() = runTest {
        val inputs = mutableListOf<String>()
        val encoder = object : AudioEncoder {
            override suspend fun encode(input: File, output: File, format: OutputFormat, settings: CodecSettings): Result<Unit> {
                val content = input.readText()
                inputs += content
                output.writeText("$content>${inputs.size}")
                return Result.success(Unit)
            }
            override fun cancel() = Unit
        }
        val original = temp.newFile("original.wav").apply { writeText("original") }
        val work = temp.newFolder("work")
        val request = request(iterations = 100, keepEvery = false)
        var published = ""
        val uri = GenerationPipeline(encoder).run(request, original, work, { generation, file, final ->
            assertTrue(final)
            assertEquals(100, generation)
            published = file.readText()
            "content://final"
        }, {})

        assertEquals("content://final", uri)
        assertEquals(100, inputs.size)
        assertEquals("original", inputs.first())
        assertEquals("original>1", inputs[1])
        assertTrue(published.endsWith(">99>100"))
        assertFalse(work.listFiles().orEmpty().isNotEmpty())
    }

    @Test fun `keep every generation publishes each completed file`() = runTest {
        val encoder = object : AudioEncoder {
            override suspend fun encode(input: File, output: File, format: OutputFormat, settings: CodecSettings): Result<Unit> =
                Result.success(Unit).also { output.writeBytes(input.readBytes() + 1) }
            override fun cancel() = Unit
        }
        val original = temp.newFile().apply { writeBytes(byteArrayOf(0)) }
        val published = mutableListOf<Int>()
        GenerationPipeline(encoder).run(request(3, true), original, temp.newFolder(), { generation, _, _ ->
            published += generation; "uri:$generation"
        }, {})
        assertEquals(listOf(1, 2, 3), published)
    }

    @Test fun `cancelling an active generation stops encoder and cleans temporary output`() = runTest {
        val entered = CompletableDeferred<Unit>()
        var encoderCancelled = false
        val encoder = object : AudioEncoder {
            override suspend fun encode(input: File, output: File, format: OutputFormat, settings: CodecSettings): Result<Unit> {
                output.writeText("partial")
                entered.complete(Unit)
                awaitCancellation()
            }
            override fun cancel() { encoderCancelled = true }
        }
        val original = temp.newFile("cancel.wav").apply { writeText("source") }
        val work = temp.newFolder("cancel-work")
        val running = async {
            GenerationPipeline(encoder).run(request(100, false), original, work, { _, _, _ -> "never" }, {})
        }
        entered.await()
        running.cancelAndJoin()

        assertTrue(encoderCancelled)
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test fun `iteration limit accepts 200 and rejects 201`() {
        request(200, false)
        assertThrows(IllegalArgumentException::class.java) { request(201, false) }
    }

    private fun request(iterations: Int, keepEvery: Boolean) = EncodeRequest(
        "job", "source", "source.wav", "tree", "audio", OutputFormat.MP3,
        CodecSettings(), iterations, keepEvery
    )
}
