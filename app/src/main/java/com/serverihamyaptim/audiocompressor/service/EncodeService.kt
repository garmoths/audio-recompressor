package com.serverihamyaptim.audiocompressor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.documentfile.provider.DocumentFile
import com.serverihamyaptim.audiocompressor.AudioCompressorApp
import com.serverihamyaptim.audiocompressor.MainActivity
import com.serverihamyaptim.audiocompressor.data.HistoryItem
import com.serverihamyaptim.audiocompressor.engine.FfmpegAudioEncoder
import com.serverihamyaptim.audiocompressor.engine.GenerationPipeline
import com.serverihamyaptim.audiocompressor.model.CodecSettings
import com.serverihamyaptim.audiocompressor.model.EncodeProgress
import com.serverihamyaptim.audiocompressor.model.EncodeRequest
import com.serverihamyaptim.audiocompressor.model.EncodeResult
import com.serverihamyaptim.audiocompressor.model.JobStatus
import com.serverihamyaptim.audiocompressor.model.OutputFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EncodeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var pipeline: GenerationPipeline? = null

    override fun onCreate() { super.onCreate(); createChannel() }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _progress.value = _progress.value.copy(status = JobStatus.STOPPING, message = "İşlem durduruluyor…")
            pipeline?.cancel(); job?.cancel(); return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val request = intent?.toRequest() ?: return START_NOT_STICKY
        _result.value = null
        _progress.value = EncodeProgress(request.jobId, 0, request.iterations, 0f, status = JobStatus.QUEUED,
            message = "Dosya hazırlanıyor…")
        startForeground(NOTIFICATION_ID, notification(0, request.iterations, false))
        job = scope.launch { execute(request) }
        return START_NOT_STICKY
    }

    private suspend fun execute(request: EncodeRequest) {
        val started = System.currentTimeMillis()
        val jobDir = File(cacheDir, "encode_${request.jobId}").apply { mkdirs() }
        val sourceExtension = request.sourceName.substringAfterLast('.', "input")
            .lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "input" }
        val original = File(jobDir, "original.$sourceExtension")
        var inputBytes = 0L
        try {
            contentResolver.openInputStream(Uri.parse(request.sourceUri)).use { source ->
                requireNotNull(source) { "Kaynak dosya açılamadı" }
                original.outputStream().use { target -> copyCancellable(source, target) }
            }
            inputBytes = original.length()
            require(inputBytes > 0) { "Seçilen kaynak dosya boş veya okunamıyor" }
            val destination = DocumentFile.fromTreeUri(this, Uri.parse(request.destinationTreeUri))
            require(destination != null && destination.exists() && destination.isDirectory && destination.canWrite()) {
                "Seçilen çıktı klasörüne yazma izni yok. Klasörü yeniden seçin."
            }
            val engine = GenerationPipeline(FfmpegAudioEncoder()).also { pipeline = it }
            val finalUri = engine.run(request, original, jobDir, { generation, file, isFinal ->
                publish(request, generation, file, isFinal)
            }) { updateProgress(it) }
            val outputBytes = finalUri?.let { uriSize(Uri.parse(it)) } ?: 0L
            val result = EncodeResult(request.jobId, request.iterations, finalUri, inputBytes, outputBytes,
                System.currentTimeMillis() - started)
            _result.value = result
            _progress.value = _progress.value.copy(status = JobStatus.COMPLETED, percent = 1f, message = "Tamamlandı")
            saveHistory(request, result)
            notifyFinished(true, "${request.iterations} nesil tamamlandı")
        } catch (cancelled: CancellationException) {
            _progress.value = _progress.value.copy(status = JobStatus.CANCELLED, message = "İşlem durduruldu")
            notifyFinished(false, "İşlem durduruldu")
        } catch (error: Throwable) {
            Log.e(TAG, "Encode job ${request.jobId} failed", error)
            val message = friendlyError(error).take(3000)
            _result.value = EncodeResult(request.jobId, _progress.value.generation, null, inputBytes, 0,
                System.currentTimeMillis() - started, message)
            _progress.value = _progress.value.copy(status = JobStatus.FAILED, message = message)
            notifyFinished(false, "Hata: ${message.take(80)}")
        } finally {
            pipeline = null
            jobDir.deleteRecursively()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun updateProgress(value: EncodeProgress) {
        _progress.value = value
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(value.generation, value.total, true))
    }

    private suspend fun publish(request: EncodeRequest, generation: Int, file: File, isFinal: Boolean): String? =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(this@EncodeService, Uri.parse(request.destinationTreeUri))
                ?: error("Çıktı klasörüne erişilemiyor")
            val digits = maxOf(3, request.iterations.toString().length)
            val name = if (request.keepEveryGeneration)
                "${request.baseName}_${generation.toString().padStart(digits, '0')}.${request.format.extension}"
            else "${request.baseName}.${request.format.extension}"
            val safeName = uniqueName(root, name, request.format.extension)
            val target = root.createFile(mime(request.format), safeName) ?: error("Çıktı dosyası oluşturulamadı")
            try {
                contentResolver.openOutputStream(target.uri, "w").use { out ->
                    requireNotNull(out); file.inputStream().use { copyCancellable(it, out) }
                }
            } catch (error: Throwable) { target.delete(); throw error }
            if (isFinal) target.uri.toString() else null
        }

    private suspend fun saveHistory(request: EncodeRequest, result: EncodeResult) {
        val uri = result.finalUri ?: return
        (application as AudioCompressorApp).database.historyDao().insert(
            HistoryItem(request.jobId, request.sourceName, displayName(Uri.parse(uri)) ?: "${request.baseName}.${request.format.extension}", uri,
                request.format.label, qualityLabel(request), request.iterations, result.inputBytes,
                result.outputBytes, result.elapsedMs)
        )
    }

    private fun notification(done: Int, total: Int, running: Boolean) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(if (running) "Ses yeniden encode ediliyor" else "İşlem hazırlanıyor")
        .setContentText(if (total > 0) "Nesil $done / $total" else "Başlatılıyor")
        .setProgress(total.coerceAtLeast(1), done, total == 0)
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(android.R.drawable.ic_delete, "Durdur", PendingIntent.getService(this, 1,
            Intent(this, EncodeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()

    private fun notifyFinished(success: Boolean, text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL).setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "Encode tamamlandı" else "Encode sona erdi")
                .setContentText(text).setAutoCancel(true).build())
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ses encode işlemleri", NotificationManager.IMPORTANCE_LOW))
    }
    private fun uriSize(uri: Uri) = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
        ?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    private fun displayName(uri: Uri) = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
    private fun uniqueName(root: DocumentFile, requested: String, extension: String): String {
        if (root.findFile(requested) == null) return requested
        val stem = requested.removeSuffix(".$extension")
        var index = 1
        while (index < 10_000) {
            val candidate = "$stem ($index).$extension"
            if (root.findFile(candidate) == null) return candidate
            index++
        }
        error("Bu adla çok fazla çıktı dosyası var. Farklı bir dosya adı seçin.")
    }

    private suspend fun copyCancellable(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.flush()
    }
    private fun mime(format: OutputFormat) = when (format) { OutputFormat.MP3 -> "audio/mpeg"; OutputFormat.OGG -> "audio/ogg"; OutputFormat.WAV -> "audio/wav"; OutputFormat.FLAC -> "audio/flac" }
    private fun qualityLabel(r: EncodeRequest) = when (r.format) { OutputFormat.MP3 -> "${r.settings.bitrateKbps} kbps"; OutputFormat.OGG -> "q${r.settings.oggQuality}"; OutputFormat.WAV -> "${r.settings.wavBits}-bit"; OutputFormat.FLAC -> "Seviye ${r.settings.flacLevel}" }

    private fun friendlyError(error: Throwable): String {
        val raw = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        return when {
            raw.contains("Permission denied", true) || raw.contains("EACCES", true) ->
                "Dosya erişim izni reddedildi. Kaynak dosyayı ve çıktı klasörünü yeniden seçin.\n\n$raw"
            raw.contains("No space left", true) || raw.contains("ENOSPC", true) ->
                "Cihazda yeterli boş alan yok. Yer açıp yeniden deneyin.\n\n$raw"
            raw.contains("Invalid data", true) ->
                "Dosyanın içindeki ses codec'i okunamadı veya dosya bozuk.\n\n$raw"
            raw.contains("Encoder", true) && raw.contains("not found", true) ->
                "Seçilen formatın encoder'ı bu cihaz paketinde bulunamadı.\n\n$raw"
            raw.isBlank() -> "Bilinmeyen encode hatası"
            else -> raw
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "encode.start"
        const val ACTION_STOP = "encode.stop"
        private const val CHANNEL = "encode_jobs"
        private const val NOTIFICATION_ID = 4100
        private const val TAG = "EncodeService"
        private val _progress = MutableStateFlow(EncodeProgress())
        val progress = _progress.asStateFlow()
        private val _result = MutableStateFlow<EncodeResult?>(null)
        val result = _result.asStateFlow()
        fun clearResult() {
            _result.value = null
            _progress.value = EncodeProgress()
        }
    }
}

private fun Intent.toRequest(): EncodeRequest = EncodeRequest(
    jobId = getStringExtra("jobId") ?: UUID.randomUUID().toString(),
    sourceUri = requireNotNull(getStringExtra("sourceUri")), sourceName = getStringExtra("sourceName") ?: "audio",
    destinationTreeUri = requireNotNull(getStringExtra("destinationUri")), baseName = getStringExtra("baseName") ?: "output",
    format = OutputFormat.valueOf(getStringExtra("format") ?: "MP3"),
    settings = CodecSettings(getIntExtra("bitrate", 128), getIntExtra("oggQuality", 5),
        getIntExtra("wavBits", 16), getIntExtra("flacLevel", 5), getIntExtra("sampleRate", 0).takeIf { it > 0 },
        getIntExtra("channels", 0).takeIf { it > 0 }, getBooleanExtra("metadata", true)),
    iterations = getIntExtra("iterations", 10).coerceIn(1, 200),
    keepEveryGeneration = getBooleanExtra("keepEvery", false)
)
