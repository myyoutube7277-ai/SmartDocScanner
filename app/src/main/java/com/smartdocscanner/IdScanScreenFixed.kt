package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdScanScreenFixed(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var front by remember { mutableStateOf<File?>(null) }
    var back by remember { mutableStateOf<File?>(null) }
    var side by remember { mutableStateOf("Front") }
    var cameraOpen by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var name by remember { mutableStateOf("ID Card") }
    var busy by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            val f = uris.getOrNull(0)?.let { copyIdUri(context, it, "id_front_${System.currentTimeMillis()}.jpg") }?.let { prepareIdFile(context, it, "front") }
            val b = uris.getOrNull(1)?.let { copyIdUri(context, it, "id_back_${System.currentTimeMillis()}.jpg") }?.let { prepareIdFile(context, it, "back") }
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (f != null) front = f; if (b != null) back = b; busy = false }
        }.start()
    }

    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("ID Card Scan", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Front", "Back", "Preview").forEach { tab -> FilterChip(selected = side == tab, onClick = { side = tab }, label = { Text(tab) }, modifier = Modifier.weight(1f)) }
            }
            Card(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0C141B))) {
                if (side == "Preview") {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { IdPreviewFixed(front, "Front"); IdPreviewFixed(back, "Back") }
                } else {
                    val selected = if (side == "Front") front else back
                    if (selected != null) BitmapFactory.decodeFile(selected.absolutePath)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$side side not captured", color = Color.White) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (side != "Preview") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = selectedFile(front, back, side) != null && !busy, onClick = { cropBitmap = selectedFile(front, back, side)?.let { BitmapFactory.decodeFile(it.absolutePath) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(5.dp)); Text("Manual Crop") }
                    OutlinedButton(enabled = selectedFile(front, back, side) != null && !busy, onClick = {
                        val selected = selectedFile(front, back, side) ?: return@OutlinedButton
                        busy = true
                        Thread {
                            val processed = prepareIdFile(context, selected, side.lowercase())
                            android.os.Handler(android.os.Looper.getMainLooper()).post { if (side == "Front") front = processed else back = processed; busy = false }
                        }.start()
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("Auto Crop") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("Gallery") }
                Button(enabled = !busy, onClick = { cameraOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(5.dp)); Text("Camera") }
            }
            Text("Front + Back → one A4 PDF", color = Color(0xFF27E0B3), modifier = Modifier.padding(8.dp))
            Button(enabled = front != null && back != null && !busy, onClick = { saveOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Create A4 PDF") }
        }
    }

    if (cropBitmap != null) IdManualCropDialog(cropBitmap!!, { cropBitmap = null }) { cropped ->
        val out = File(context.cacheDir, "id_manual_${side.lowercase()}_${System.currentTimeMillis()}.jpg")
        runCatching { FileOutputStream(out).use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 94, it)) } }.onSuccess { if (side == "Front") front = out else back = out }
        cropBitmap = null
    }

    if (cameraOpen) IdCameraCaptureFixed(side, onClose = { cameraOpen = false }) { file ->
        busy = true
        Thread {
            val processed = prepareIdFile(context, file, side.lowercase())
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (side == "Front") front = processed else back = processed; busy = false; cameraOpen = false }
        }.start()
    }

    if (saveOpen) AlertDialog(
        onDismissRequest = { if (!busy) saveOpen = false }, title = { Text("Save ID PDF") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy, singleLine = true, label = { Text("Document name") }) },
        confirmButton = { Button(enabled = !busy, onClick = {
            val safe = name.trim().removeSuffix(".pdf").ifBlank { "ID Card" }
            busy = true
            Thread {
                val pdf = PdfEngine.createIdCardPdf(context.filesDir, front!!, back!!, safe)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    busy = false
                    if (pdf != null && pdf.exists() && pdf.length() > 0) {
                        val record = DocumentStore.register(context, pdf, "$safe.pdf")
                        if (record != null) { saveOpen = false; savedFile = File(record.path) } else Toast.makeText(context, "Could not save in My Files", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(context, "ID PDF save failed", Toast.LENGTH_LONG).show()
                }
            }.start()
        }) { Text("Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { saveOpen = false }) { Text("Cancel") } }
    )

    if (savedFile != null) AlertDialog(onDismissRequest = { savedFile = null; onSaved() }, title = { Text("Saved in My Files") }, text = { Text("ID PDF is saved inside SmartDocScanner. You can share it now.") }, confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } }, dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } })
}

private fun selectedFile(front: File?, back: File?, side: String): File? = if (side == "Front") front else back

private fun prepareIdFile(context: Context, source: File, side: String): File = runCatching {
    val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return@runCatching source
    val cropped = if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(bitmap) else bitmap
    val out = File(context.cacheDir, "id_${side}_processed_${System.currentTimeMillis()}.jpg")
    FileOutputStream(out).use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
    if (cropped !== bitmap) cropped.recycle()
    bitmap.recycle()
    if (out.exists() && out.length() > 0L) source.delete()
    out
}.getOrElse { source }

private fun copyIdUri(context: Context, uri: Uri, name: String): File? = runCatching {
    val out = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
    out.takeIf { it.exists() && it.length() > 0L }
}.getOrNull()

@Composable
private fun IdPreviewFixed(file: File?, label: String) {
    Column(Modifier.fillMaxWidth()) { Text(label, color = Color(0xFF27E0B3)); if (file != null) BitmapFactory.decodeFile(file.absolutePath)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(145.dp), contentScale = ContentScale.Fit) } else Text("Not captured", color = Color.Gray) }
}

@Composable
private fun IdManualCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var left by remember { mutableFloatStateOf(0f) }; var top by remember { mutableFloatStateOf(0f) }; var right by remember { mutableFloatStateOf(1f) }; var bottom by remember { mutableFloatStateOf(1f) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Manual Crop") }, text = {
        Column {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
            Text("Left"); Slider(left, { left = it.coerceAtMost(right - .05f) }, valueRange = 0f..0.8f)
            Text("Top"); Slider(top, { top = it.coerceAtMost(bottom - .05f) }, valueRange = 0f..0.8f)
            Text("Right"); Slider(right, { right = it.coerceAtLeast(left + .05f) }, valueRange = .2f..1f)
            Text("Bottom"); Slider(bottom, { bottom = it.coerceAtLeast(top + .05f) }, valueRange = .2f..1f)
        }
    }, confirmButton = { Button(onClick = {
        val l = (bitmap.width * left).toInt().coerceIn(0, bitmap.width - 2); val t = (bitmap.height * top).toInt().coerceIn(0, bitmap.height - 2); val r = (bitmap.width * right).toInt().coerceIn(l + 1, bitmap.width); val b = (bitmap.height * bottom).toInt().coerceIn(t + 1, bitmap.height)
        onApply(Bitmap.createBitmap(bitmap, l, t, r - l, b - t))
    }) { Text("Apply Crop") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun IdCameraCaptureFixed(side: String, onClose: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current; val previewView = remember { PreviewView(context) }; val executor = remember { Executors.newSingleThreadExecutor() }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }; var camera by remember { mutableStateOf<Camera?>(null) }; var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }; var flash by remember { mutableStateOf(false) }; var ready by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) { permission.launch(Manifest.permission.CAMERA); return@LaunchedEffect }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ runCatching {
            val p = future.get(); val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }; val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build(); p.unbindAll(); val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic); provider = p; capture = ic; camera = cam; ready = true
        }.onFailure { Toast.makeText(context, "Camera could not start", Toast.LENGTH_LONG).show() } }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(camera) { previewView.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) runCatching { val point = previewView.meteringPointFactory.createPoint(e.x, e.y); camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) }; true }; onDispose { previewView.setOnTouchListener(null) } }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); executor.shutdownNow() } }
    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .97f)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Capture $side", color = Color.White, style = MaterialTheme.typography.titleLarge); IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()); if (!ready) Text("Starting camera…", color = Color.White) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { flash = !flash; capture?.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White) }
                Button(enabled = ready, onClick = { val cap = capture ?: return@Button; val file = File(context.cacheDir, "id_${side.lowercase()}_${System.currentTimeMillis()}.jpg"); cap.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback { override fun onError(exception: ImageCaptureException) { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() } }; override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { android.os.Handler(android.os.Looper.getMainLooper()).post { onCaptured(file) } } }) }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Capture") }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}
