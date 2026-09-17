package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.items
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
    var filter by remember { mutableStateOf(SettingsStore.filter(context).ifBlank { "B&W" }) }
    var name by remember { mutableStateOf("Scanned Document") }
    LaunchedEffect(pages) { if (pages.isNotEmpty()) DraftStore.save(context, pages) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.mapIndexedNotNull { index, uri -> copyUriToCache(context, uri, "gallery_${System.currentTimeMillis()}_$index.jpg") }
        if (added.isNotEmpty()) pages = pages + added
    }
    if (captured != null) {
        ScannerEditFixed(file = captured!!, initialFilter = filter, onFilter = { filter = it; SettingsStore.setFilter(context, it) }, onAdd = { file -> pages = pages + file; captured = null }, onCancel = { captured = null }, onFinish = { file -> pages = pages + file; captured = null; showSave = true })
        return
    }
    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Scan Document", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }, bottomBar = {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
            Button(onClick = { if (pages.isNotEmpty()) showSave = true }, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text("Finish") }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                SafeCameraPreview(Modifier.fillMaxSize(), onCaptured = { captured = it })
                Surface(Modifier.align(Alignment.TopCenter).padding(14.dp), color = Color.Black.copy(alpha = .70f), shape = RoundedCornerShape(18.dp)) { Text("HD • ${pages.size + 1} page", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                Box(Modifier.align(Alignment.Center).size(290.dp, 400.dp).border(2.dp, Color(0xFF27E0B3), RoundedCornerShape(12.dp)))
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0B1218)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "Color", onClick = { filter = "Color" }, label = { Text("Color") }, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == "B&W", onClick = { filter = "B&W" }, label = { Text("B&W") }, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == "Clean White", onClick = { filter = "Clean White" }, label = { Text("Clean White") }, modifier = Modifier.weight(1f))
            }
        }
    }
    if (showSave) AlertDialog(onDismissRequest = { showSave = false }, title = { Text("Save Document") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Document name") }) },
        confirmButton = { Button(onClick = {
            val safeName = name.ifBlank { "Scanned Document" }
            Thread { val output = PdfEngine.createPdfAuto(context.filesDir, pages, PdfEngine.SizeMode.MAXIMUM, SettingsStore.maxSizeChoice(context), SettingsStore.paperSize(context), safeName); android.os.Handler(android.os.Looper.getMainLooper()).post { if (output != null) { DocumentStore.add(context, DocumentRecord(System.currentTimeMillis(), safeName, output.absolutePath)); DraftStore.clear(context); showSave = false; savedFile = output } else Toast.makeText(context, "PDF save failed. Please try again.", Toast.LENGTH_LONG).show() } }.start()
        }) { Text("Save PDF") } }, dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } })
    if (savedFile != null) AlertDialog(onDismissRequest = { savedFile = null; onSaved() }, title = { Text("Saved in My Files") }, text = { Text("PDF has been saved inside SmartDocScanner. You can share the saved file now.") },
        confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } }, dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerEditFixed(file: File, initialFilter: String, onFilter: (String) -> Unit, onAdd: (File) -> Unit, onCancel: () -> Unit, onFinish: (File) -> Unit) {
    val context = LocalContext.current
    val original = remember(file) { ScanProcessor.decode(file) }
    val base = remember(file, original) { original?.let { if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(it) else it } }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var processed by remember(file, base) { mutableStateOf(base) }
    var showCrop by remember { mutableStateOf(false) }
    LaunchedEffect(base, mode, rotation) { base?.let { source -> val filtered = ScanProcessor.filter(source, mode); processed = when ((rotation / 90) % 4) { 1 -> ScanProcessor.rotate(filtered); 2 -> ScanProcessor.rotate(ScanProcessor.rotate(filtered)); 3 -> ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(filtered))); else -> filtered } } }
    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Edit & Enhance", color = Color.White) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) { processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Original", "Color", "B&W", "Clean White")) { label -> FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) }) } }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCrop = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(5.dp)); Text("Crop") }
                OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(5.dp)); Text("Rotate") }
                OutlinedButton(onClick = { mode = "High Contrast"; onFilter(mode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("Enhance") }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mode = "Original"; rotation = 0; onFilter("Original") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Reset") }
                OutlinedButton(onClick = { saveScannerPage(context, processed)?.let(onAdd) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add Page") }
                Button(onClick = { saveScannerPage(context, processed)?.let(onFinish) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(5.dp)); Text("Finish") }
            }
        }
    }
    if (showCrop && processed != null) ManualCropDialog(processed!!, onDismiss = { showCrop = false }) { cropped -> processed = cropped; showCrop = false }
}

private fun saveScannerPage(context: Context, bitmap: Bitmap?): File? = runCatching {
    if (bitmap == null) return@runCatching null
    val file = File(context.cacheDir, "scan_page_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) { "bitmap compression failed" } }
    file
}.getOrNull()

@Composable
private fun SafeCameraPreview(modifier: Modifier, onCaptured: (File) -> Unit) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }; var camera by remember { mutableStateOf<Camera?>(null) }; var flashOn by remember { mutableStateOf(false) }; var ready by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) Toast.makeText(context, "Camera permission is required for scanning", Toast.LENGTH_LONG).show() }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(hasPermission) {
        if (!hasPermission) { permissionLauncher.launch(Manifest.permission.CAMERA); return@LaunchedEffect }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ runCatching { val provider = future.get(); val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }; val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build(); provider.unbindAll(); val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture); imageCapture = capture; camera = bound; ready = true }.onFailure { Toast.makeText(context, "Camera could not start", Toast.LENGTH_LONG).show() } }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(camera) { if (camera != null) previewView.setOnTouchListener { _, event -> if (event.action == android.view.MotionEvent.ACTION_UP) runCatching { val point = previewView.meteringPointFactory.createPoint(event.x, event.y); camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) }; true else true } else Unit; onDispose { previewView.setOnTouchListener(null) } }
    DisposableEffect(Unit) { onDispose { executor.shutdownNow() } }
    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (!ready) Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .65f), shape = RoundedCornerShape(12.dp)) { Text("Starting camera…", color = Color.White, modifier = Modifier.padding(14.dp)) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { flashOn = !flashOn; imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White) }
            FilledIconButton(onClick = { val capture = imageCapture ?: return@FilledIconButton; val file = File(context.cacheDir, "raw_scan_${System.currentTimeMillis()}.jpg"); capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback { override fun onError(exception: ImageCaptureException) { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() } }; override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { android.os.Handler(android.os.Looper.getMainLooper()).post { onCaptured(file) } } }) }, Modifier.size(78.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(Icons.Default.CameraAlt, null, Modifier.size(34.dp)) }
            IconButton(onClick = { Toast.makeText(context, "Tap anywhere on the page to focus", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.CenterFocusStrong, null, tint = Color.White) }
        }
    }
}
