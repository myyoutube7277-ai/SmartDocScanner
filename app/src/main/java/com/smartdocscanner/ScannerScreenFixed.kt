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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
fun ScannerScreenFixed(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var captured by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf(DraftStore.load(context)) }
    var showSave by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SettingsStore.filter(context).ifBlank { "B&W" }) }
    var name by remember { mutableStateOf("Scanned Document") }
    val main = remember { Handler(Looper.getMainLooper()) }
    LaunchedEffect(pages) { if (pages.isNotEmpty()) DraftStore.save(context, pages) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        Thread {
            val result = uris.mapIndexedNotNull { index, uri ->
                val raw = copyUriToCache(context, uri, "gallery_${System.currentTimeMillis()}_$index.jpg") ?: return@mapIndexedNotNull null
                val bmp = ScanProcessor.decode(raw) ?: return@mapIndexedNotNull null
                val cropped = if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(bmp) else bmp
                val filtered = ScanProcessor.filter(cropped, filter)
                saveScannerPage(context, filtered)
            }
            main.post { if (result.isNotEmpty()) pages = pages + result }
        }.start()
    }

    if (captured != null) {
        ScannerEditFixed(
            file = captured!!,
            initialFilter = filter,
            onFilter = { filter = it; SettingsStore.setFilter(context, it) },
            onAdd = { file -> pages = pages + file; captured = null },
            onCancel = { captured = null },
            onFinish = { file -> pages = pages + file; captured = null; showSave = true }
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF05080C),
        topBar = {
            TopAppBar(title = { Text("Scan Document", color = Color.White) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) }
            })
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery")
                }
                Button(onClick = { if (pages.isNotEmpty()) showSave = true }, enabled = pages.isNotEmpty() && !saving, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text("Finish")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                SafeCameraPreview(Modifier.fillMaxSize(), onCaptured = { captured = it })
                Surface(Modifier.align(Alignment.TopCenter).padding(14.dp), color = Color.Black.copy(alpha = .70f), shape = RoundedCornerShape(18.dp)) {
                    Text("HD • ${pages.size + 1} page", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Box(Modifier.align(Alignment.Center).size(290.dp, 400.dp).border(2.dp, Color(0xFF27E0B3), RoundedCornerShape(12.dp)))
            }
            if (pages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages) { index, file ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF18222B)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                                Text("${index + 1}", color = Color.White)
                                IconButton(onClick = { pages = pages.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.Delete, null, tint = Color.White) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0B1218)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "Color", onClick = { filter = "Color"; SettingsStore.setFilter(context, filter) }, label = { Text("Color") }, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == "B&W", onClick = { filter = "B&W"; SettingsStore.setFilter(context, filter) }, label = { Text("B&W") }, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == "Clean White", onClick = { filter = "Clean White"; SettingsStore.setFilter(context, filter) }, label = { Text("Clean White") }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showSave) AlertDialog(
        onDismissRequest = { if (!saving) showSave = false },
        title = { Text("Save Document") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, enabled = !saving, label = { Text("Document name") })
                if (saving) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                val safeName = name.trim().removeSuffix(".pdf").ifBlank { "Scanned Document" }
                saving = true
                Thread {
                    val output = PdfEngine.createPdfAuto(context.filesDir, pages, PdfEngine.SizeMode.MAXIMUM, SettingsStore.maxSizeChoice(context), SettingsStore.paperSize(context), safeName)
                    main.post {
                        saving = false
                        if (output != null && output.exists() && output.length() > 0) {
                            val persistent = DocumentStore.register(context, output, "$safeName.pdf")
                            if (persistent != null) { DraftStore.clear(context); showSave = false; savedFile = persistent }
                            else Toast.makeText(context, "Could not store PDF in My Files", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(context, "PDF save failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }) { Text(if (saving) "Saving…" else "Save PDF") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { showSave = false }) { Text("Cancel") } }
    )

    if (savedFile != null) AlertDialog(
        onDismissRequest = { savedFile = null; onSaved() },
        title = { Text("Saved in My Files") },
        text = { Text("Your PDF is saved inside SmartDocScanner. You can share it whenever you want.") },
        confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } },
        dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerEditFixed(file: File, initialFilter: String, onFilter: (String) -> Unit, onAdd: (File) -> Unit, onCancel: () -> Unit, onFinish: (File) -> Unit) {
    val context = LocalContext.current
    var original by remember(file) { mutableStateOf<Bitmap?>(null) }
    var base by remember(file) { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var processed by remember { mutableStateOf<Bitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(true) }
    val main = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(file) {
        Thread {
            val src = ScanProcessor.decode(file)
            val cropped = src?.let { if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(it) else it }
            main.post { original = src; base = cropped; processing = false }
        }.start()
    }
    LaunchedEffect(base, mode, rotation) {
        val source = base ?: return@LaunchedEffect
        processing = true
        Thread {
            val filtered = ScanProcessor.filter(source, mode)
            val rotated = when ((rotation / 90) % 4) {
                1 -> ScanProcessor.rotate(filtered)
                2 -> ScanProcessor.rotate(ScanProcessor.rotate(filtered))
                3 -> ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(filtered)))
                else -> filtered
            }
            main.post { processed = rotated; processing = false }
        }.start()
    }

    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Edit & Enhance", color = Color.White) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
                if (processing) CircularProgressIndicator()
                else processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Original", "Color", "B&W", "Clean White")) { label ->
                    FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (processed != null) showCrop = true }, enabled = processed != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(5.dp)); Text("Crop") }
                OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }, enabled = processed != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(5.dp)); Text("Rotate") }
                OutlinedButton(onClick = { mode = "High Contrast"; onFilter(mode) }, enabled = processed != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("Enhance") }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mode = "Original"; rotation = 0; onFilter("Original") }, enabled = processed != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Reset") }
                OutlinedButton(onClick = { saveScannerPage(context, processed)?.let(onAdd) }, enabled = processed != null && !processing, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add Page") }
                Button(onClick = { saveScannerPage(context, processed)?.let(onFinish) }, enabled = processed != null && !processing, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(5.dp)); Text("Finish") }
            }
        }
    }
    if (showCrop && processed != null) ManualCropDialog(processed!!, onDismiss = { showCrop = false }) { cropped -> base = cropped; rotation = 0; showCrop = false }
}

private fun saveScannerPage(context: Context, bitmap: Bitmap?): File? = runCatching {
    if (bitmap == null) return@runCatching null
    val file = File(context.cacheDir, "scan_page_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) }
    file.takeIf { it.exists() && it.length() > 0 }
}.getOrNull()

@Composable
private fun SafeCameraPreview(modifier: Modifier, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) { permissionLauncher.launch(Manifest.permission.CAMERA); return@LaunchedEffect }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build()
                p.unbindAll()
                val bound = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                provider = p; imageCapture = capture; camera = bound; ready = true
            }.onFailure { Toast.makeText(context, "Camera could not start", Toast.LENGTH_LONG).show() }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(camera) {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) runCatching {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); executor.shutdownNow() } }

    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (!ready) Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .65f), shape = RoundedCornerShape(12.dp)) { Text("Starting camera…", color = Color.White, modifier = Modifier.padding(14.dp)) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { flashOn = !flashOn; imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White) }
            FilledIconButton(onClick = {
                val capture = imageCapture ?: return@FilledIconButton
                val file = File(context.cacheDir, "raw_scan_${System.currentTimeMillis()}.jpg")
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) { Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() } }
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { Handler(Looper.getMainLooper()).post { onCaptured(file) } }
                })
            }, Modifier.size(78.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(Icons.Default.CameraAlt, null, Modifier.size(34.dp)) }
            IconButton(onClick = { Toast.makeText(context, "Tap anywhere on the page to focus", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.CenterFocusStrong, null, tint = Color.White) }
        }
    }
}
