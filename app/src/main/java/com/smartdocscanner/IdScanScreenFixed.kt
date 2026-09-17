package com.smartdocscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
    var name by remember { mutableStateOf("ID Card") }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) copyUriToCache(context, uris[0], "id_front_${System.currentTimeMillis()}.jpg")?.let { front = it }
        if (uris.size > 1) copyUriToCache(context, uris[1], "id_back_${System.currentTimeMillis()}.jpg")?.let { back = it }
    }

    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("ID Card Scan", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Front", "Back", "Preview").forEach { tab -> FilterChip(selected = side == tab, onClick = { side = tab }, label = { Text(tab) }, modifier = Modifier.weight(1f)) }
            }
            Card(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0C141B))) {
                if (side == "Preview") Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { IdPreviewFixed(front, "Front"); IdPreviewFixed(back, "Back") }
                else {
                    val selected = if (side == "Front") front else back
                    if (selected != null) BitmapFactory.decodeFile(selected.absolutePath)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$side side not captured", color = Color.White) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("Gallery") }
                Button(onClick = { cameraOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(5.dp)); Text("Camera") }
            }
            Text("Front + Back → one A4 PDF", color = Color(0xFF27E0B3), modifier = Modifier.padding(8.dp))
            Button(onClick = { saveOpen = true }, enabled = front != null && back != null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Create A4 PDF") }
        }
    }

    if (cameraOpen) IdCameraCaptureFixed(side, onClose = { cameraOpen = false }) { file -> if (side == "Front") front = file else back = file; cameraOpen = false }

    if (saveOpen) AlertDialog(
        onDismissRequest = { saveOpen = false },
        title = { Text("Save ID PDF") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Document name") }) },
        confirmButton = {
            Button(onClick = {
                val safeName = name.ifBlank { "ID Card" }
                Thread {
                    val pdf = PdfEngine.createIdCardPdf(context.filesDir, front!!, back!!, safeName)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (pdf != null) { DocumentStore.add(context, DocumentRecord(System.currentTimeMillis(), safeName, pdf.absolutePath)); saveOpen = false; savedFile = pdf }
                        else Toast.makeText(context, "ID PDF save failed", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } }
    )

    if (savedFile != null) AlertDialog(
        onDismissRequest = { savedFile = null; onSaved() },
        title = { Text("Saved in My Files") },
        text = { Text("ID PDF has been saved inside SmartDocScanner. You can share the saved file now.") },
        confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } },
        dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } }
    )
}

@Composable
private fun IdPreviewFixed(file: File?, label: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFF27E0B3))
        if (file != null) BitmapFactory.decodeFile(file.absolutePath)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(145.dp), contentScale = ContentScale.Fit) }
        else Text("Not captured", color = Color.Gray)
    }
}

@Composable
private fun IdCameraCaptureFixed(side: String, onClose: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) Toast.makeText(context, "Camera permission is required", Toast.LENGTH_LONG).show() }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasPermission) {
        if (!hasPermission) { permissionLauncher.launch(Manifest.permission.CAMERA); return@LaunchedEffect }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val image = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build()
                provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, image)
                capture = image; ready = true
            }.onFailure { Toast.makeText(context, "Camera could not start", Toast.LENGTH_LONG).show() }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) { onDispose { executor.shutdownNow() } }

    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .97f)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Capture $side", color = Color.White, style = MaterialTheme.typography.titleLarge); IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()); if (!ready) Text("Starting camera…", color = Color.White) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { flashOn = !flashOn; capture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null, tint = Color.White) }
                Button(onClick = {
                    val cap = capture ?: return@Button
                    val file = File(context.cacheDir, "id_${side.lowercase()}_${System.currentTimeMillis()}.jpg")
                    cap.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exception: ImageCaptureException) { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() } }
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { android.os.Handler(android.os.Looper.getMainLooper()).post { onCaptured(file) } }
                    })
                }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Capture") }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}
