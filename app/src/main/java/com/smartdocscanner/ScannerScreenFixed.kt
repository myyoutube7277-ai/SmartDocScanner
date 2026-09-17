package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenFixed(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf(DraftStore.load(context)) }
    var captured by remember { mutableStateOf<File?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Scanned Document") }
    var filter by remember { mutableStateOf(SettingsStore.filter(context).ifBlank { "B&W" }) }
    LaunchedEffect(pages) { if (pages.isNotEmpty()) DraftStore.save(context, pages) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        Thread {
            val added = uris.mapIndexedNotNull { i, uri ->
                val raw = copyUriToCache(context, uri, "gallery_${System.currentTimeMillis()}_$i.jpg") ?: return@mapIndexedNotNull null
                val src = ScanProcessor.decode(raw) ?: return@mapIndexedNotNull null
                val base = if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(src) else src
                savePage(context, ScanProcessor.filter(base, filter))
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { pages = pages + added }
        }.start()
    }

    if (captured != null) {
        ScannerEditor(captured!!, filter, { filter = it; SettingsStore.setFilter(context, it) },
            { f -> pages = pages + f; captured = null },
            { f -> pages = pages + f; captured = null; showSave = true },
            { captured = null })
        return
    }

    Scaffold(
        containerColor = Color(0xFF05080C),
        topBar = { TopAppBar(title = { Text("Scan Document", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) } }) },
        bottomBar = { Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("Gallery") }
            Button(enabled = pages.isNotEmpty(), onClick = { showSave = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(5.dp)); Text("Finish") }
        }}
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CameraPreview(Modifier.fillMaxSize()) { captured = it }
                Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), color = Color.Black.copy(.7f), shape = RoundedCornerShape(18.dp)) {
                    Text("HD • ${pages.size + 1} page", color = Color.White, modifier = Modifier.padding(10.dp))
                }
            }
            if (pages.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pages.indices.toList()) { index ->
                    Surface(color = Color(0xFF18222B), shape = RoundedCornerShape(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Page ${index + 1}", color = Color.White, modifier = Modifier.padding(start = 10.dp))
                            IconButton(onClick = { pages = pages.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.Delete, null, tint = Color.White) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Color", "B&W", "Clean White").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label; SettingsStore.setFilter(context, label) }, label = { Text(label) }, modifier = Modifier.weight(1f)) }
            }
        }
    }

    if (showSave) AlertDialog(onDismissRequest = { if (!saving) showSave = false }, title = { Text("Save Document") },
        text = { OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true, label = { Text("Document name") }) },
        confirmButton = { Button(enabled = !saving, onClick = {
            val safe = name.trim().removeSuffix(".pdf").ifBlank { "Scanned Document" }; saving = true
            Thread {
                val out = PdfEngine.createPdfAuto(context.filesDir, pages, PdfEngine.SizeMode.MAXIMUM, SettingsStore.maxSizeChoice(context), SettingsStore.paperSize(context), safe)
                val rec = if (out != null && out.exists() && out.length() > 0) DocumentStore.register(context, out, "$safe.pdf") else null
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    saving = false
                    if (rec != null) { DraftStore.clear(context); showSave = false; saved = File(rec.path) }
                    else Toast.makeText(context, "PDF save failed", Toast.LENGTH_LONG).show()
                }
            }.start()
        }) { Text(if (saving) "Saving…" else "Save PDF") },
        dismissButton = { TextButton(enabled = !saving, onClick = { showSave = false }) { Text("Cancel") } })

    if (saved != null) AlertDialog(onDismissRequest = { saved = null; onSaved() }, title = { Text("Saved in My Files") }, text = { Text("PDF saved inside SmartDocScanner.") },
        confirmButton = { Button(onClick = { ShareUtil.share(context, saved!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } },
        dismissButton = { TextButton(onClick = { saved = null; onSaved() }) { Text("Done") } })
}

@Composable
private fun ScannerEditor(file: File, initialFilter: String, onFilter: (String) -> Unit, onAdd: (File) -> Unit, onFinish: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var original by remember(file) { mutableStateOf<Bitmap?>(null) }
    var base by remember(file) { mutableStateOf<Bitmap?>(null) }
    var processed by remember(file) { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var cropOpen by remember { mutableStateOf(false) }
    var autoDone by remember { mutableStateOf(false) }

    LaunchedEffect(file) { Thread {
        val src = ScanProcessor.decode(file)
        val b = src?.let { if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(it) else it }
        android.os.Handler(android.os.Looper.getMainLooper()).post { original = src; base = b; autoDone = SettingsStore.autoCrop(context); busy = false }
    }.start() }

    LaunchedEffect(base, mode, rotation) { val b = base ?: return@LaunchedEffect; busy = true; Thread {
        val f = ScanProcessor.filter(b, mode)
        val r = when(rotation) { 90 -> ScanProcessor.rotate(f); 180 -> ScanProcessor.rotate(ScanProcessor.rotate(f)); 270 -> ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(f))); else -> f }
        android.os.Handler(android.os.Looper.getMainLooper()).post { processed = r; busy = false }
    }.start() }

    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Edit & Enhance", color = Color.White) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) { if (busy) CircularProgressIndicator() else processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(listOf("Original", "Color", "B&W", "Clean White")) { label -> FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) }) } }
            if (autoDone) Text("Auto Crop applied", color = Color(0xFF27E0B3), modifier = Modifier.padding(start = 12.dp, top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = original != null && !busy, onClick = { val src = original ?: return@OutlinedButton; Thread { val c = ScanProcessor.autoCrop(src); android.os.Handler(android.os.Looper.getMainLooper()).post { base = c; rotation = 0; autoDone = true; Toast.makeText(context, "Auto Crop applied", Toast.LENGTH_SHORT).show() } }.start() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CropFree, null); Spacer(Modifier.width(3.dp)); Text("Auto Crop") }
                OutlinedButton(enabled = processed != null && !busy, onClick = { cropOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(3.dp)); Text("Manual Crop") }
                OutlinedButton(enabled = processed != null && !busy, onClick = { rotation = (rotation + 90) % 360 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(3.dp)); Text("Rotate") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !busy, onClick = { mode = "High Contrast"; onFilter(mode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(3.dp)); Text("Enhance") }
                OutlinedButton(enabled = processed != null && !busy, onClick = { original?.let { base = it }; rotation = 0; autoDone = false; mode = "Original"; onFilter("Original") }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = processed != null && !busy, onClick = { savePage(context, processed)?.let(onAdd) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add Page") }
                Button(enabled = processed != null && !busy, onClick = { savePage(context, processed)?.let(onFinish) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Text("Finish") }
            }
        }
    }
    if (cropOpen && processed != null) ManualCropDialogFixed(processed!!, { cropOpen = false }) { b -> base = b; rotation = 0; autoDone = true; cropOpen = false }
}

@Composable
private fun ManualCropDialogFixed(bitmap: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var tl by remember(bitmap) { mutableStateOf(Offset(.08f, .08f)) }
    var tr by remember(bitmap) { mutableStateOf(Offset(.92f, .08f)) }
    var br by remember(bitmap) { mutableStateOf(Offset(.92f, .92f)) }
    var bl by remember(bitmap) { mutableStateOf(Offset(.08f, .92f)) }
    var active by remember { mutableIntStateOf(-1) }
    val pts = listOf(tl, tr, br, bl)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Manual Crop") }, text = {
        Column(Modifier.fillMaxWidth()) {
            Text("Drag the four corners like a document scanner.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(320.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                ComposeCanvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    fun p(o: Offset) = Offset(o.x*w, o.y*h)
                    val a=p(tl); val b=p(tr); val c=p(br); val d=p(bl)
                    drawLine(Color(0xFF27E0B3), a,b,5f); drawLine(Color(0xFF27E0B3), b,c,5f); drawLine(Color(0xFF27E0B3), c,d,5f); drawLine(Color(0xFF27E0B3), d,a,5f)
                    pts.forEach { drawCircle(Color(0xFF27E0B3), 20f, p(it)); drawCircle(Color.White, 8f, p(it)) }
                }
                Box(Modifier.fillMaxSize().pointerInput(bitmap) { detectDragGestures(onDragStart = { pos ->
                    val w=size.width.coerceAtLeast(1f); val h=size.height.coerceAtLeast(1f)
                    active=listOf(tl,tr,br,bl).mapIndexed { i,o -> i to (abs(pos.x-o.x*w)+abs(pos.y-o.y*h)) }.minByOrNull { it.second }?.first ?: -1
                }, onDragEnd={active=-1}, onDragCancel={active=-1}, onDrag={change,amount ->
                    change.consume(); val w=size.width.coerceAtLeast(1f); val h=size.height.coerceAtLeast(1f)
                    fun move(o: Offset)=Offset((o.x+amount.x/w).coerceIn(.02f,.98f),(o.y+amount.y/h).coerceIn(.02f,.98f))
                    when(active){0->tl=move(tl);1->tr=move(tr);2->br=move(br);3->bl=move(bl)}
                }) })
            }
        }
    }, confirmButton = { Button(onClick = {
        val srcPts=floatArrayOf(tl.x*bitmap.width,tl.y*bitmap.height,tr.x*bitmap.width,tr.y*bitmap.height,br.x*bitmap.width,br.y*bitmap.height,bl.x*bitmap.width,bl.y*bitmap.height)
        val top=max(1f,distF(tl,tr)*bitmap.width); val bottom=max(1f,distF(bl,br)*bitmap.width); val left=max(1f,distF(tl,bl)*bitmap.height); val right=max(1f,distF(tr,br)*bitmap.height)
        val outW=max(2f,max(top,bottom)); val outH=max(2f,max(left,right)); val dst=Bitmap.createBitmap(outW.toInt(),outH.toInt(),Bitmap.Config.ARGB_8888)
        val m=Matrix(); val dstPts=floatArrayOf(0f,0f,outW,0f,outW,outH,0f,outH)
        if(m.setPolyToPoly(srcPts,0,dstPts,0,4)) Canvas(dst).drawBitmap(bitmap,m,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) else { dst.recycle(); return@Button }
        onApply(dst)
    }) { Text("Apply Crop") } }, dismissButton = { TextButton(onClick=onDismiss){Text("Cancel")} })
}

private fun distF(a: Offset,b: Offset):Float { val x=a.x-b.x; val y=a.y-b.y; return kotlin.math.sqrt(x*x+y*y) }

@Composable
private fun CameraPreview(modifier: Modifier, onCaptured: (File)->Unit) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    val view=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }; var capture by remember { mutableStateOf<ImageCapture?>(null) }; var ready by remember { mutableStateOf(false) }; var flash by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permission=it}
    val executor=remember { Executors.newSingleThreadExecutor() }
    LaunchedEffect(permission){ if(!permission){launcher.launch(Manifest.permission.CAMERA);return@LaunchedEffect}; val future=ProcessCameraProvider.getInstance(context); future.addListener({runCatching{val p=future.get(); val preview=Preview.Builder().build().also{it.surfaceProvider=view.surfaceProvider}; val ic=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build(); p.unbindAll(); camera=p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,ic); capture=ic; ready=true}.onFailure{Toast.makeText(context,"Camera could not start",Toast.LENGTH_LONG).show()}},ContextCompat.getMainExecutor(context)) }
    DisposableEffect(camera){ view.setOnTouchListener{_,e->if(e.action==MotionEvent.ACTION_UP)runCatching{val pt=view.meteringPointFactory.createPoint(e.x,e.y);camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())};true}; onDispose{view.setOnTouchListener(null)} }
    DisposableEffect(Unit){onDispose{camera?.let{it.cameraControl.enableTorch(false)};executor.shutdownNow()} }
    Box(modifier.background(Color.Black)){ AndroidView(factory={view},modifier=Modifier.fillMaxSize()); Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){ IconButton(enabled=ready,onClick={flash=!flash;camera?.cameraControl?.enableTorch(flash)}){Icon(if(flash)Icons.Default.FlashOn else Icons.Default.FlashOff,null,tint=Color.White)}; FloatingActionButton(enabled=ready,onClick={val ic=capture?:return@FloatingActionButton;val f=File(context.cacheDir,"camera_${System.currentTimeMillis()}.jpg");ic.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(),executor,object:ImageCapture.OnImageSavedCallback{override fun onImageSaved(r:ImageCapture.OutputFileResults){android.os.Handler(android.os.Looper.getMainLooper()).post{onCaptured(f)}};override fun onError(e:ImageCaptureException){android.os.Handler(android.os.Looper.getMainLooper()).post{Toast.makeText(context,"Capture failed",Toast.LENGTH_SHORT).show()}}})}){Icon(Icons.Default.CameraAlt,null)}; IconButton(onClick={Toast.makeText(context,"Tap document to focus",Toast.LENGTH_SHORT).show()}){Icon(Icons.Default.CenterFocusStrong,null,tint=Color.White)} } }
}

private fun savePage(context: Context, bitmap: Bitmap?): File? = runCatching { if(bitmap==null) return@runCatching null; val f=File(context.cacheDir,"scan_page_${System.currentTimeMillis()}.jpg"); FileOutputStream(f).use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,94,it))}; f.takeIf{it.exists()&&it.length()>0} }.getOrNull()
