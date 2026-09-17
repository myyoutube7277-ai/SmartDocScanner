package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenFixed(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(DraftStore.load(context)) }
    var captured by remember { mutableStateOf<File?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Scanned Document") }
    var filter by remember { mutableStateOf(SettingsStore.filter(context).ifBlank { "B&W" }) }
    LaunchedEffect(pages) { if (pages.isNotEmpty()) DraftStore.save(context, pages) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        Thread {
            val added = uris.mapIndexedNotNull { index, uri ->
                val raw = copyUriToCache(context, uri, "gallery_${System.currentTimeMillis()}_$index.jpg") ?: return@mapIndexedNotNull null
                val source = ScanProcessor.decode(raw) ?: return@mapIndexedNotNull null
                val base = if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(source) else source
                saveScannerPage(context, ScanProcessor.filter(base, filter))
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { pages = pages + added }
        }.start()
    }

    if (captured != null) {
        ScannerEditFixed(
            captured!!, filter,
            { filter = it; SettingsStore.setFilter(context, it) },
            { file -> pages = pages + file; captured = null },
            { file -> pages = pages + file; captured = null; showSave = true },
            { captured = null }
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF05080C),
        topBar = { TopAppBar(title = { Text("Scan Document", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) } }) },
        bottomBar = { Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
            Button(enabled = pages.isNotEmpty() && !saving, onClick = { showSave = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text("Finish") }
        }}
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                SafeCameraPreview(Modifier.fillMaxSize()) { captured = it }
                Surface(Modifier.align(Alignment.TopCenter).padding(14.dp), color = Color.Black.copy(alpha = .7f), shape = RoundedCornerShape(18.dp)) { Text("HD • ${pages.size + 1} page", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            if (pages.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pages.indices.toList()) { index ->
                    Surface(color = Color(0xFF18222B), shape = RoundedCornerShape(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                            Text("Page ${index + 1}", color = Color.White)
                            IconButton(onClick = { pages = pages.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.Delete, null, tint = Color.White) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Color", "B&W", "Clean White").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label; SettingsStore.setFilter(context, label) }, label = { Text(label) }, modifier = Modifier.weight(1f)) }
            }
        }
    }

    if (showSave) AlertDialog(
        onDismissRequest = { if (!saving) showSave = false }, title = { Text("Save Document") },
        text = { Column { OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true, label = { Text("Document name") }); if (saving) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } } },
        confirmButton = { Button(enabled = !saving, onClick = {
            val safe = name.trim().removeSuffix(".pdf").ifBlank { "Scanned Document" }
            saving = true
            Thread {
                val out = PdfEngine.createPdfAuto(context.filesDir, pages, PdfEngine.SizeMode.MAXIMUM, SettingsStore.maxSizeChoice(context), SettingsStore.paperSize(context), safe)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    saving = false
                    if (out != null && out.exists() && out.length() > 0) {
                        val record = DocumentStore.register(context, out, "$safe.pdf")
                        if (record != null) { DraftStore.clear(context); showSave = false; savedFile = File(record.path) }
                        else Toast.makeText(context, "Could not save in My Files", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(context, "PDF save failed", Toast.LENGTH_LONG).show()
                }
            }.start()
        }) { Text(if (saving) "Saving…" else "Save PDF") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { showSave = false }) { Text("Cancel") } }
    )

    if (savedFile != null) AlertDialog(onDismissRequest = { savedFile = null; onSaved() }, title = { Text("Saved in My Files") }, text = { Text("PDF saved inside SmartDocScanner. You can share it now.") }, confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } }, dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerEditFixed(file: File, initialFilter: String, onFilter: (String) -> Unit, onAdd: (File) -> Unit, onFinish: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var original by remember(file) { mutableStateOf<Bitmap?>(null) }
    var base by remember(file) { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var processed by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showCrop by remember { mutableStateOf(false) }
    var autoCropped by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        Thread {
            val src = ScanProcessor.decode(file)
            val b = src?.let { if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(it) else it }
            android.os.Handler(android.os.Looper.getMainLooper()).post { original = src; base = b; autoCropped = SettingsStore.autoCrop(context); loading = false }
        }.start()
    }
    LaunchedEffect(base, mode, rotation) {
        val b = base ?: return@LaunchedEffect
        loading = true
        Thread {
            val filtered = ScanProcessor.filter(b, mode)
            val r = when (rotation) {
                90 -> ScanProcessor.rotate(filtered)
                180 -> ScanProcessor.rotate(ScanProcessor.rotate(filtered))
                270 -> ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(filtered)))
                else -> filtered
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { processed = r; loading = false }
        }.start()
    }

    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Edit & Enhance", color = Color.White) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Original", "Color", "B&W", "Clean White")) { label -> FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) }) }
            }
            if (autoCropped) Text("Auto Crop applied", color = Color(0xFF27E0B3), modifier = Modifier.padding(start = 12.dp, top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = original != null && !loading, onClick = {
                    Thread {
                        val cropped = original?.let { ScanProcessor.autoCrop(it) }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { if (cropped != null) { base = cropped; rotation = 0; autoCropped = true; Toast.makeText(context, "Auto Crop applied", Toast.LENGTH_SHORT).show() } }
                    }
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CropFree, null); Spacer(Modifier.width(4.dp)); Text("Auto Crop") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { showCrop = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(4.dp)); Text("Manual Crop") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { rotation = (rotation + 90) % 360 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(4.dp)); Text("Rotate") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "High Contrast"; onFilter(mode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("Enhance") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "Original"; rotation = 0; original?.let { base = it }; autoCropped = false; onFilter("Original") }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onAdd) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add Page") }
                Button(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onFinish) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Text("Finish") }
            }
        }
    }
    if (showCrop && processed != null) ManualCropDialog(processed!!, { showCrop = false }) { cropped -> base = cropped; rotation = 0; autoCropped = true; showCrop = false }
}

@Composable
private fun ManualCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var tl by remember(bitmap) { mutableStateOf(Offset(0.08f, 0.08f)) }
    var tr by remember(bitmap) { mutableStateOf(Offset(0.92f, 0.08f)) }
    var br by remember(bitmap) { mutableStateOf(Offset(0.92f, 0.92f)) }
    var bl by remember(bitmap) { mutableStateOf(Offset(0.08f, 0.92f)) }
    val points = listOf(tl, tr, br, bl)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Crop") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Drag the four corners like a document scanner.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height
                        fun p(o: Offset) = Offset(o.x * w, o.y * h)
                        val a = p(tl); val b = p(tr); val c = p(br); val d = p(bl)
                        drawLine(Color(0xFF27E0B3), a, b, 5f)
                        drawLine(Color(0xFF27E0B3), b, c, 5f)
                        drawLine(Color(0xFF27E0B3), c, d, 5f)
                        drawLine(Color(0xFF27E0B3), d, a, 5f)
                        points.forEach { drawCircle(Color(0xFF27E0B3), 18f, p(it)) }
                        points.forEach { drawCircle(Color.White, 7f, p(it)) }
                    }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(bitmap) {
                            detectDragGestures(
                                onDragEnd = {},
                                onDragCancel = {},
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val w = size.width.coerceAtLeast(1f); val h = size.height.coerceAtLeast(1f)
                                    val pos = change.position
                                    fun dist(o: Offset) = abs(pos.x - o.x * w) + abs(pos.y - o.y * h)
                                    val nearest = listOf(dist(tl), dist(tr), dist(br), dist(bl)).withIndex().minByOrNull { it.value }?.index ?: 0
                                    fun norm(o: Offset): Offset = Offset((o.x + dragAmount.x / w).coerceIn(0.02f, 0.98f), (o.y + dragAmount.y / h).coerceIn(0.02f, 0.98f))
                                    when (nearest) {
                                        0 -> tl = norm(tl)
                                        1 -> tr = norm(tr)
                                        2 -> br = norm(br)
                                        else -> bl = norm(bl)
                                    }
                                }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = {
            val minX = minOf(tl.x, tr.x, br.x, bl.x).coerceIn(0f, 1f)
            val maxX = maxOf(tl.x, tr.x, br.x, bl.x).coerceIn(minX + 0.01f, 1f)
            val minY = minOf(tl.y, tr.y, br.y, bl.y).coerceIn(0f, 1f)
            val maxY = maxOf(tl.y, tr.y, br.y, bl.y).coerceIn(minY + 0.01f, 1f)
            val l = (bitmap.width * minX).toInt().coerceIn(0, bitmap.width - 2)
            val t = (bitmap.height * minY).toInt().coerceIn(0, bitmap.height - 2)
            val r = (bitmap.width * maxX).toInt().coerceIn(l + 1, bitmap.width)
            val b = (bitmap.height * maxY).toInt().coerceIn(t + 1, bitmap.height)
            onApply(Bitmap.createBitmap(bitmap, l, t, r - l, b - t))
        }) { Text("Apply Crop") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SafeCameraPreview(modifier: Modifier, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var ready by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) { permission.launch(Manifest.permission.CAMERA); return@LaunchedEffect }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ runCatching {
            val p = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build()
            p.unbindAll()
            val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic)
            provider = p; capture = ic; camera = cam; ready = true
        }.onFailure { Toast.makeText(context, "Camera could not start", Toast.LENGTH_LONG).show() } }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(camera) {
        previewView.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) runCatching {
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); executor.shutdownNow() } }
    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (!ready) Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .7f), shape = RoundedCornerShape(12.dp)) { Text("Starting camera…", color = Color.White, modifier = Modifier.padding(14.dp)) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = ready, onClick = { flash = !flash; camera?.cameraControl?.enableTorch(flash) }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White) }
            FloatingActionButton(onClick = {
                if (!ready) return@FloatingActionButton
                val ic = capture ?: return@FloatingActionButton
                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { android.os.Handler(android.os.Looper.getMainLooper()).post { onCaptured(file) } }
                    override fun onError(exception: ImageCaptureException) { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() } }
                })
            }) { Icon(Icons.Default.CameraAlt, null) }
            IconButton(onClick = { Toast.makeText(context, "Tap on the document to focus", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.CenterFocusStrong, null, tint = Color.White) }
        }
    }
}

private fun saveScannerPage(context: Context, bitmap: Bitmap?): File? = runCatching {
    if (bitmap == null) return@runCatching null
    val file = File(context.cacheDir, "scan_page_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) }
    file.takeIf { it.exists() && it.length() > 0L }
}.getOrNull()
