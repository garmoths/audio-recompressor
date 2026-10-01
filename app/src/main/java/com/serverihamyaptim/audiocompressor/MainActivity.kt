package com.serverihamyaptim.audiocompressor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serverihamyaptim.audiocompressor.data.HistoryItem
import com.serverihamyaptim.audiocompressor.model.JobStatus
import com.serverihamyaptim.audiocompressor.model.OutputFormat
import com.serverihamyaptim.audiocompressor.service.EncodeService
import java.util.UUID
import kotlinx.coroutines.launch

private val Purple = Color(0xFF5B4CF0)
private val Blue = Color(0xFF7F8EFF)
private val Green = Color(0xFF00C779)
private val Background = Color(0xFFF1F1F4)
private val Muted = Color(0xFF787981)
private val CardShape = RoundedCornerShape(30.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AudioTheme { AudioApp() } }
    }
}

@Composable private fun AudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Purple, secondary = Green,
        background = Background, surface = Color.White), content = content)
}

private data class PickedFile(val uri: Uri, val name: String, val bytes: Long)

@Composable private fun AudioApp() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val progress by EncodeService.progress.collectAsStateWithLifecycle()
    val result by EncodeService.result.collectAsStateWithLifecycle()
    val running = progress.status in setOf(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.STOPPING)
    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (!running) NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Sıkıştır") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = Color(0xFFEDEBFF)))
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Rounded.History, null) }, label = { Text("Geçmiş") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = Color(0xFFEDEBFF)))
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(targetState = tab, label = "screen") { page ->
                when (page) {
                    0 -> SetupScreen()
                    1 -> HistoryScreen()
                }
            }
            if (running) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    ProcessingScreen(progress.generation, progress.total, progress.percent,
                        progress.elapsedMs, progress.currentBytes, progress.status) {
                        ContextCompat.startForegroundService(context,
                            Intent(context, EncodeService::class.java).setAction(EncodeService.ACTION_STOP))
                    }
                }
            }
            if (!running && progress.status == JobStatus.COMPLETED && result != null) {
                ResultDialog(result!!.completedGenerations, result!!.inputBytes, result!!.outputBytes,
                    result!!.elapsedMs, result!!.finalUri, onDismiss = { EncodeService.clearResult() })
            }
            if (!running && progress.status == JobStatus.FAILED && result?.error != null) {
                ErrorDialog(result!!.error!!, onDismiss = { EncodeService.clearResult() })
            }
            if (!running && progress.status == JobStatus.CANCELLED) {
                AlertDialog(onDismissRequest = { EncodeService.clearResult() },
                    title = { Text("İşlem durduruldu") },
                    text = { Text("Aktif encode güvenle iptal edildi. Yarım geçici dosyalar temizlendi; daha önce ayrı kaydedilmiş nesiller korunur.") },
                    confirmButton = { TextButton({ EncodeService.clearResult() }) { Text("Tamam") } })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SetupScreen() {
    val context = LocalContext.current
    var source by remember { mutableStateOf<PickedFile?>(null) }
    var destination by remember { mutableStateOf<Uri?>(null) }
    var destinationLabel by remember { mutableStateOf("Klasör seçilmedi") }
    var format by rememberSaveable { mutableStateOf(OutputFormat.MP3) }
    var bitrate by rememberSaveable { mutableIntStateOf(128) }
    var oggQuality by rememberSaveable { mutableIntStateOf(5) }
    var wavBits by rememberSaveable { mutableIntStateOf(16) }
    var flacLevel by rememberSaveable { mutableIntStateOf(5) }
    var iterationsText by rememberSaveable { mutableStateOf("10") }
    var baseName by rememberSaveable { mutableStateOf("output") }
    var keepEvery by rememberSaveable { mutableStateOf(false) }
    var metadata by rememberSaveable { mutableStateOf(true) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var sampleRate by rememberSaveable { mutableIntStateOf(0) }
    var channels by rememberSaveable { mutableIntStateOf(0) }
    var showFormat by remember { mutableStateOf(false) }
    val iterations = iterationsText.toIntOrNull() ?: 0

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            source = inspectUri(context, it)
            if (baseName == "output") baseName = source!!.name.substringBeforeLast('.').ifBlank { "output" }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            destination = it; destinationLabel = it.lastPathSegment?.substringAfterLast(':') ?: "Seçilen klasör"
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(6.dp)); Text("Sesini dönüştür", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Gerçek nesilden nesile yeniden encode", color = Muted) }
        item {
            Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(Modifier.background(Brush.linearGradient(listOf(Purple, Blue, Color(0xFFE8EAFF)))).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).background(Color.White.copy(.95f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AudioFile, null, tint = Purple) }
                        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                            Text(source?.name ?: "Ses dosyası seç", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(source?.let { formatBytes(it.bytes) } ?: "WAV, MP3, OGG, FLAC, M4A ve daha fazlası", color = Color(0xFF313345), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }, modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text(if (source == null) "Dosya seç" else "Dosyayı değiştir") }
                }
            }
        }
        item {
            SectionCard("Çıktı biçimi") {
                Box {
                    Row(Modifier.fillMaxWidth().clickable { showFormat = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FormatBadge(format); Spacer(Modifier.width(14.dp)); Text(format.label, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    DropdownMenu(showFormat, { showFormat = false }) { OutputFormat.entries.forEach { item ->
                        DropdownMenuItem({ Text(item.label) }, onClick = { format = item; showFormat = false })
                    } }
                }
                Spacer(Modifier.height(12.dp)); QualitySelector(format, bitrate, oggQuality, wavBits, flacLevel) { value ->
                    when (format) { OutputFormat.MP3 -> bitrate = value; OutputFormat.OGG -> oggQuality = value; OutputFormat.WAV -> wavBits = value; OutputFormat.FLAC -> flacLevel = value }
                }
                if (format == OutputFormat.WAV || format == OutputFormat.FLAC) Text("Kayıpsız formatlarda tekrar encode belirgin nesil bozulması oluşturmaz.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item {
            SectionCard("Tekrar sayısı") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { iterationsText = (iterations.coerceAtLeast(2) - 1).toString() },
                        modifier = Modifier.size(52.dp).background(Color(0xFFF1F1F4), CircleShape)) {
                        Icon(Icons.Rounded.Remove, "Bir azalt")
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(iterationsText, { value ->
                        val digits = value.filter(Char::isDigit).take(3)
                        iterationsText = when {
                            digits.isBlank() -> ""
                            else -> digits.toInt().coerceAtMost(200).toString()
                        }
                    }, modifier = Modifier.width(132.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), suffix = { Text("kez") })
                    Spacer(Modifier.width(14.dp))
                    IconButton(onClick = { iterationsText = (iterations.coerceAtLeast(0) + 1).coerceAtMost(200).toString() },
                        modifier = Modifier.size(52.dp).background(Color(0xFFEDEBFF), CircleShape)) {
                        Icon(Icons.Rounded.Add, "Bir artır", tint = Purple)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(10, 25, 50, 100, 200).forEach { preset ->
                        TextButton(onClick = { iterationsText = preset.toString() },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (iterations == preset) Color.White else Purple),
                            modifier = Modifier.background(if (iterations == preset) Purple else Color(0xFFF1F1F4), RoundedCornerShape(12.dp))) {
                            Text(preset.toString(), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Her nesil, bir önceki neslin çıktısından üretilecek.", color = Muted, fontSize = 13.sp)
                if (iterations >= 100) Text("Uzun işlem: cihaz ısınabilir ve işlem uzun sürebilir.", color = Color(0xFFE07932), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            SectionCard("Kaydetme") {
                OutlinedTextField(baseName, { baseName = sanitizeName(it) }, label = { Text("Dosya adı") }, suffix = { Text(".${format.extension}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp)); SettingRow("Çıktı klasörü", destinationLabel, Icons.Rounded.FolderOpen) { folderPicker.launch(null) }
                ToggleRow("Her nesli ayrı kaydet", "Kapalıysa yalnız son dosya tutulur", keepEvery) { keepEvery = it }
                ToggleRow("Metadata'yı koru", "Başlık, sanatçı, albüm ve desteklenen etiketler", metadata) { metadata = it }
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Gelişmiş ayarları kapat" else "Gelişmiş ayarlar") }
                AnimatedVisibility(advanced) {
                    Column {
                        ChoiceRow("Örnekleme", listOf(0 to "Orijinal", 22050 to "22.05 kHz", 44100 to "44.1 kHz", 48000 to "48 kHz"), sampleRate) { sampleRate = it }
                        ChoiceRow("Kanallar", listOf(0 to "Orijinal", 1 to "Mono", 2 to "Stereo"), channels) { channels = it }
                    }
                }
            }
        }
        item {
            val enabled = source != null && destination != null && baseName.isNotBlank() && iterations in 1..200
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                startEncode(context, source!!, destination!!, baseName, format, bitrate, oggQuality, wavBits, flacLevel, sampleRate, channels, metadata, keepEvery, iterations)
            }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Encode işlemini başlat", fontSize = 16.sp) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable private fun ProcessingScreen(generation: Int, total: Int, progress: Float, elapsed: Long, bytes: Long, status: JobStatus, stop: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "phase")
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp)); Text("Yeniden encode ediliyor", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Uygulamadan çıksanız da işlem devam eder", color = Muted)
        Spacer(Modifier.height(50.dp))
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Color(0xFFE3E2EA), -90f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(Purple, Blue, Purple)), -90f, progress * 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("%${(progress * 100).toInt()}", fontSize = 42.sp, fontWeight = FontWeight.Bold); Text("Nesil $generation / $total", color = Muted) }
        }
        Spacer(Modifier.height(42.dp)); Card(shape = CardShape, colors = CardDefaults.cardColors(Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                    val mid = size.height / 2
                    val step = size.width / 24
                    repeat(24) { i ->
                        val height = (kotlin.math.sin(i * .72 + phase).toFloat() * .5f + .5f) * size.height * .75f + 6
                        drawLine(Brush.verticalGradient(listOf(Purple, Blue)), Offset(i * step, mid - height / 2), Offset(i * step, mid + height / 2), 5.dp.toPx(), StrokeCap.Round)
                    }
                }
                Spacer(Modifier.height(16.dp)); SummaryRow("Geçen süre", formatDuration(elapsed)); SummaryRow("Aktif dosya", formatBytes(bytes)); SummaryRow("Durum", if (status == JobStatus.STOPPING) "Durduruluyor…" else "İşleniyor")
            }
        }
        Spacer(Modifier.weight(1f)); Button(stop, enabled = status != JobStatus.STOPPING, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17171A))) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("Durdur") }
    }
}

@Composable private fun HistoryScreen() {
    val context = LocalContext.current
    val dao = (context.applicationContext as AudioCompressorApp).database.historyDao()
    val history by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Geçmiş", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Tamamlanan encode zincirleri", color = Muted); Spacer(Modifier.height(10.dp)) }
        if (history.isEmpty()) item { EmptyHistory() }
        items(history, key = { it.jobId }) { item -> HistoryCard(item, { shareUri(context, item.outputUri) }, { scope.launch { dao.delete(item.jobId) } }) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable private fun HistoryCard(item: HistoryItem, share: () -> Unit, delete: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).background(Color(0xFFEDEBFF), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Purple) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text(item.outputName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.format} • ${item.iterations} nesil • ${formatBytes(item.outputBytes)}", color = Muted, fontSize = 12.sp)
            }
            IconButton(share) { Icon(Icons.Rounded.Share, "Paylaş") }; IconButton(delete) { Icon(Icons.Rounded.DeleteOutline, "Geçmişten sil") }
        }
    }
}

@Composable private fun EmptyHistory() { Card(shape = CardShape, colors = CardDefaults.cardColors(Color.White), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(34.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.History, null, tint = Purple, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp)); Text("Henüz işlem yok", fontWeight = FontWeight.SemiBold); Text("Tamamlanan dosyalar burada görünür", color = Muted) } } }

@Composable private fun ResultDialog(generations: Int, input: Long, output: Long, elapsed: Long, uri: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = {}, icon = { Box(Modifier.size(58.dp).background(Green.copy(.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Green) } },
        title = { Text("Encode tamamlandı") }, text = { Column { SummaryRow("Tamamlanan", "$generations / $generations"); SummaryRow("İlk boyut", formatBytes(input)); SummaryRow("Son boyut", formatBytes(output)); SummaryRow("Toplam süre", formatDuration(elapsed)) } },
        confirmButton = { Button({ uri?.let { shareUri(context, it) } }, enabled = uri != null) { Text("Paylaş") } }, dismissButton = { TextButton(onDismiss) { Text("Yeni işlem") } })
}

@Composable private fun ErrorDialog(error: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Encode tamamlanamadı") }, text = { Text(error) }, confirmButton = { TextButton(onDismiss) { Text("Kapat") } }) }

@Composable private fun SectionCard(title: String, content: @Composable () -> Unit) { Card(shape = CardShape, colors = CardDefaults.cardColors(Color.White), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(14.dp)); content() } } }
@Composable private fun FormatBadge(format: OutputFormat) { Box(Modifier.size(48.dp).background(if (format == OutputFormat.MP3) Purple else Color(0xFFEDEBFF), CircleShape), contentAlignment = Alignment.Center) { Text(format.extension.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (format == OutputFormat.MP3) Color.White else Purple) } }

@Composable private fun QualitySelector(format: OutputFormat, bitrate: Int, ogg: Int, wav: Int, flac: Int, select: (Int) -> Unit) {
    val values = when (format) { OutputFormat.MP3 -> listOf(64,96,128,192,256,320); OutputFormat.OGG -> (0..10).toList(); OutputFormat.WAV -> listOf(16,24,32); OutputFormat.FLAC -> (0..12).toList() }
    val current = when (format) { OutputFormat.MP3 -> bitrate; OutputFormat.OGG -> ogg; OutputFormat.WAV -> wav; OutputFormat.FLAC -> flac }
    var expanded by remember { mutableStateOf(false) }
    Box { SettingRow("Kalite", when (format) { OutputFormat.MP3 -> "$current kbps"; OutputFormat.OGG -> "Kalite $current"; OutputFormat.WAV -> "$current-bit PCM"; OutputFormat.FLAC -> "Sıkıştırma $current" }, Icons.Rounded.MoreVert) { expanded = true }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(when (format) { OutputFormat.MP3 -> "$value kbps"; OutputFormat.OGG -> "Kalite $value"; OutputFormat.WAV -> "$value-bit PCM"; OutputFormat.FLAC -> "Sıkıştırma $value" }) }, onClick = { select(value); expanded = false }) } }
    }
}

@Composable private fun SettingRow(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(Color(0xFFF1F1F4), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.Black) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(value, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Muted) } }
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 12.sp) }; Switch(checked, change) } }
@Composable private fun ChoiceRow(title: String, choices: List<Pair<Int,String>>, selected: Int, change: (Int) -> Unit) { var open by remember { mutableStateOf(false) }; Box { SettingRow(title, choices.first { it.first == selected }.second, Icons.Rounded.MoreVert) { open = true }; DropdownMenu(open, { open = false }) { choices.forEach { choice -> DropdownMenuItem({ Text(choice.second) }, onClick = { change(choice.first); open = false }) } } } }
@Composable private fun SummaryRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, color = Muted, modifier = Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Medium) } }

private fun startEncode(context: Context, source: PickedFile, destination: Uri, baseName: String, format: OutputFormat, bitrate: Int, ogg: Int, wav: Int, flac: Int, sampleRate: Int, channels: Int, metadata: Boolean, keep: Boolean, iterations: Int) {
    val intent = Intent(context, EncodeService::class.java).setAction(EncodeService.ACTION_START)
        .putExtra("jobId", UUID.randomUUID().toString()).putExtra("sourceUri", source.uri.toString()).putExtra("sourceName", source.name)
        .putExtra("destinationUri", destination.toString()).putExtra("baseName", baseName).putExtra("format", format.name)
        .putExtra("bitrate", bitrate).putExtra("oggQuality", ogg).putExtra("wavBits", wav).putExtra("flacLevel", flac)
        .putExtra("sampleRate", sampleRate).putExtra("channels", channels).putExtra("metadata", metadata)
        .putExtra("keepEvery", keep).putExtra("iterations", iterations)
    ContextCompat.startForegroundService(context, intent)
}

private fun inspectUri(context: Context, uri: Uri): PickedFile { var name = uri.lastPathSegment ?: "audio"; var bytes = 0L; context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) { name = it.getString(0) ?: name; bytes = if (it.isNull(1)) 0 else it.getLong(1) } }; return PickedFile(uri, name, bytes) }
private fun sanitizeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
private fun formatBytes(bytes: Long): String = when { bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0); bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0); bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
private fun formatDuration(ms: Long): String { val seconds = ms / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
private fun shareUri(context: Context, uriString: String) { val uri = Uri.parse(uriString); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Ses dosyasını paylaş")) }
