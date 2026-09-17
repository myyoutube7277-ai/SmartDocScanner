package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.math.max
import kotlin.math.min

private val mainHandler = Handler(Looper.getMainLooper())

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

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) DraftStore.save(context, pages)
    }

    if (captured != null) {
        ScannerEditor(
            file = captured!!,
            initialFilter = filter,
            onFilter = { filter = it; SettingsStore.setFilter(context, it) },
            onAddPage = { f -> pages = pages + f; captured = null },
            onFinish = { f -> pages = pages + f; captured = null; showSave = true },
            onCancel = { captured = null }
        )
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Scan Document", color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0B1117)).padding(10.dp)) {
                Button(
                    enabled = pages.isNotEmpty() && !saving,
                    onClick = { showSave = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Done, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Finish")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ScannerCameraPreview(Modifier.fillMaxSize()) { captured = it }
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(12.dp),
                    color = Color.Black.copy(alpha = .65f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("HD • Page ${pages.size + 1}", color = Color.White, modifier = Modifier.padding(10.dp))
                }
            }
            if (pages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pages.indices.toList()) { i ->
                        Surface(color = Color(0xFF17212A), shape = RoundedCornerShape(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Page ${i + 1}", color = Color.White, modifier = Modifier.padding(start = 10.dp))
                                IconButton(onClick = { pages = pages.toMutableList().also { it.removeAt(i) } }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Original", "Color", "B&W", "Clean White")) { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = label; SettingsStore.setFilter(context, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { if (!saving) showSave = false },
            title = { Text("Save Document") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !saving,
                        singleLine = true,
                        label = { Text("Document name") }
                    )
                    if (saving) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    val safe = name.trim().removeSuffix(".pdf").ifBlank { "Scanned Document" }
                    saving = true
                    Thread {
                        val out = PdfEngine.createPdfAuto(
                            context.filesDir,
                            pages,
                            PdfEngine.SizeMode.MAXIMUM,
                            SettingsStore.maxSizeChoice(context),
                            SettingsStore.paperSize(context),
                            safe
                        )
                        mainHandler.post {
                            saving = false
                            if (out != null && out.exists() && out.length() > 0) {
                                val record = DocumentStore.register(context, out, "$safe.pdf")
                                if (record != null) {
                                    DraftStore.clear(context)
                                    showSave = false
                                    savedFile = File(record.path)
                                } else {
                                    Toast.makeText(context, "Could not save in My Files", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "PDF save failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                }) { Text(if (saving) "Saving…" else "Save PDF") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { showSave = false }) { Text("Cancel") } }
        )
    }

    if (savedFile != null) {
        AlertDialog(
            onDismissRequest = { savedFile = null; onSaved() },
            title = { Text("Saved in My Files") },
            text = { Text("PDF saved inside SmartDocScanner. You can share it now.") },
            confirmButton = {
                Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Share")
                }
            },
            dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerEditor(
    file: File,
    initialFilter: String,
    onFilter: (String) -> Unit,
    onAddPage: (File) -> Unit,
    onFinish: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var original by remember(file) { mutableStateOf<Bitmap?>(null) }
    var base by remember(file) { mutableStateOf<Bitmap?>(null) }
    var processed by remember(file) { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var loading by remember(file) { mutableStateOf(true) }
    var cropOpen by remember { mutableStateOf(false) }
    var autoApplied by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        Thread {
            val src = ScanProcessor.decode(file)
            val cropped = src?.let { ScanProcessor.autoCrop(it) }
            mainHandler.post {
                original = src
                base = cropped ?: src
                autoApplied = cropped != null
                loading = false
            }
        }.start()
    }

    LaunchedEffect(base, mode, rotation) {
        val b = base ?: return@LaunchedEffect
        loading = true
        Thread {
            val filtered = ScanProcessor.filter(b, mode)
            val result = when (rotation) {
                90 -> ScanProcessor.rotate(filtered)
                180 -> ScanProcessor.rotate(ScanProcessor.rotate(filtered))
                270 -> ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(filtered)))
                else -> filtered
            }
            mainHandler.post { processed = result; loading = false }
        }.start()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Edit & Enhance", color = Color.White) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf("Original", "Color", "B&W", "Clean White")) { label ->
                    FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) })
                }
            }
            if (autoApplied) Text("Auto Crop applied", color = Color(0xFF27E0B3), modifier = Modifier.padding(start = 12.dp, top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = original != null && !loading,
                    onClick = {
                        val src = original ?: return@OutlinedButton
                        Thread {
                            val c = ScanProcessor.autoCrop(src)
                            mainHandler.post {
                                base = c
                                rotation = 0
                                autoApplied = true
                                Toast.makeText(context, "Auto Crop applied", Toast.LENGTH_SHORT).show()
                            }
                        }.start()
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.CropFree, null); Spacer(Modifier.width(3.dp)); Text("Auto Crop") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { cropOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Crop, null); Spacer(Modifier.width(3.dp)); Text("Manual Crop")
                }
                OutlinedButton(enabled = processed != null && !loading, onClick = { rotation = (rotation + 90) % 360 }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(3.dp)); Text("Rotate")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "High Contrast"; onFilter(mode) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(3.dp)); Text("Enhance")
                }
                OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "Original"; rotation = 0; base = original; autoApplied = false; onFilter("Original") }, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onAddPage) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(3.dp)); Text("Add Page")
                }
                Button(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onFinish) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Done, null); Spacer(Modifier.width(3.dp)); Text("Finish")
                }
            }
        }
    }

    if (cropOpen && processed != null) {
        ScannerManualCropDialog(processed!!, onDismiss = { cropOpen = false }) { cropped ->
            base = cropped
            rotation = 0
            autoApplied = false
            cropOpen = false
        }
    }
}

@Composable
private fun ScannerManualCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var tl by remember(bitmap) { mutableStateOf(Offset(.06f, .06f)) }
    var tr by remember(bitmap) { mutableStateOf(Offset(.94f, .06f)) }
    var br by remember(bitmap) { mutableStateOf(Offset(.94f, .94f)) }
    var bl by remember(bitmap) { mutableStateOf(Offset(.06f, .94f)) }
    var active by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Crop") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Drag the four corners like a document scanner.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(320.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        fun p(o: Offset) = Offset(o.x * w, o.y * h)
                        val a = p(tl); val b = p(tr); val c = p(br); val d = p(bl)
                        drawLine(Color(0xFF27E0B3), a, b, 4f)
                        drawLine(Color(0xFF27E0B3), b, c, 4f)
                        drawLine(Color(0xFF27E0B3), c, d, 4f)
                        drawLine(Color(0xFF27E0B3), d, a, 4f)
                        listOf(a, b, c, d).forEach {
                            drawCircle(Color(0xFF27E0B3), 22f, it)
                            drawCircle(Color.White, 8f, it)
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().pointerInput(bitmap) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    val points = listOf(tl, tr, br, bl)
                                    active = points.indices.minByOrNull { i ->
                                        val dx = pos.x - points[i].x * w
                                        val dy = pos.y - points[i].y * h
                                        dx * dx + dy * dy
                                    } ?: -1
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    fun move(p: Offset) = Offset(
                                        (p.x + amount.x / w).coerceIn(.02f, .98f),
                                        (p.y + amount.y / h).coerceIn(.02f, .98f)
                                    )
                                    when (active) {
                                        0 -> tl = move(tl)
                                        1 -> tr = move(tr)
                                        2 -> br = move(br)
                                        3 -> bl = move(bl)
                                    }
                                }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val minX = (minOf(tl.x, tr.x, br.x, bl.x) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val maxX = (maxOf(tl.x, tr.x, br.x, bl.x) * bitmap.width).toInt().coerceIn(minX + 1, bitmap.width)
                val minY = (minOf(tl.y, tr.y, br.y, bl.y) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val maxY = (maxOf(tl.y, tr.y, br.y, bl.y) * bitmap.height).toInt().coerceIn(minY + 1, bitmap.height)
                onApply(Bitmap.createBitmap(bitmap, minX, minY, max(1, maxX - minX), max(1, maxY - minY)))
            }) { Text("Apply Crop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ScannerCameraPreview(modifier: Modifier, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var torch by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        previewView = view
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setJpegQuality(96)
                                    .build()
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                imageCapture = capture
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Camera failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        view.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                camera?.let { cam ->
                                    val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                                }
                            }
                            true
                        }
                    }
                },
                update = {}
            )
        } else {
            Text("Camera permission is required", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                camera?.cameraControl?.enableTorch(!torch)
                torch = !torch
            }) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White)
            }
            FloatingActionButton(onClick = {
                val capture = imageCapture ?: return@FloatingActionButton
                val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (file.exists() && file.length() > 0) onCaptured(file)
                        else Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }) {
                Icon(Icons.Default.CameraAlt, null)
            }
        }
    }
}

private fun saveScannerPage(context: Context, bitmap: Bitmap?): File? {
    if (bitmap == null) return null
    return try {
        val dir = File(context.cacheDir, "scan_pages").apply { mkdirs() }
        val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }
        if (file.exists() && file.length() > 0) file else null
    } catch (_: Exception) {
        null
    }
}
