package com.smartdocscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

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
    if (captured != null) {
        ScannerEditFixed(captured!!, filter, { filter = it; SettingsStore.setFilter(context, it) },
            { pages = pages + it; captured = null }, { pages = pages + it; captured = null; showSave = true }, { captured = null })
        return
    }
    Scaffold(containerColor = Color(0xFF05080C),
        topBar = { TopAppBar(title = { Text("Scan Document", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White) } }) },
        bottomBar = { Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp)) { Button(enabled = pages.isNotEmpty() && !saving, onClick = { showSave = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text("Finish") } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f)) { ScannerCameraPreview(Modifier.fillMaxSize()) { captured = it }; Surface(Modifier.align(Alignment.TopCenter).padding(14.dp), color = Color.Black.copy(.7f), shape = RoundedCornerShape(18.dp)) { Text("HD • ${pages.size + 1} page", color = Color.White, modifier = Modifier.padding(10.dp)) } }
            if (pages.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(pages.indices.toList()) { i -> Surface(color = Color(0xFF18222B), shape = RoundedCornerShape(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Page ${i + 1}", color = Color.White, modifier = Modifier.padding(start = 10.dp)); IconButton(onClick = { pages = pages.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Default.Delete, null, tint = Color.White) } } } } }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Color", "B&W", "Clean White").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label; SettingsStore.setFilter(context, label) }, label = { Text(label) }, modifier = Modifier.weight(1f)) } }
        }
    }
    if (showSave) AlertDialog(onDismissRequest = { if (!saving) showSave = false }, title = { Text("Save Document") },
        text = { Column { OutlinedTextField(name, { name = it }, enabled = !saving, singleLine = true, label = { Text("Document name") }); if (saving) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } } },
        confirmButton = { Button(enabled = !saving, onClick = { val safe = name.trim().removeSuffix(".pdf").ifBlank { "Scanned Document" }; saving = true; Thread { val out = PdfEngine.createPdfAuto(context.filesDir, pages, PdfEngine.SizeMode.MAXIMUM, SettingsStore.maxSizeChoice(context), SettingsStore.paperSize(context), safe); android.os.Handler(android.os.Looper.getMainLooper()).post { saving = false; if (out != null && out.exists() && out.length() > 0) { val r = DocumentStore.register(context, out, "$safe.pdf"); if (r != null) { DraftStore.clear(context); showSave = false; savedFile = File(r.path) } else Toast.makeText(context, "Could not save in My Files", Toast.LENGTH_LONG).show() } else Toast.makeText(context, "PDF save failed", Toast.LENGTH_LONG).show() } }.start() }) { Text(if (saving) "Saving…" else "Save PDF") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { showSave = false }) { Text("Cancel") } })
    if (savedFile != null) AlertDialog(onDismissRequest = { savedFile = null; onSaved() }, title = { Text("Saved in My Files") }, text = { Text("PDF saved inside SmartDocScanner. You can share it now.") }, confirmButton = { Button(onClick = { ShareUtil.share(context, savedFile!!, "application/pdf") }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") } }, dismissButton = { TextButton(onClick = { savedFile = null; onSaved() }) { Text("Done") } })
}

@Composable
private fun ScannerEditFixed(file: File, initialFilter: String, onFilter: (String) -> Unit, onAdd: (File) -> Unit, onFinish: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var original by remember(file) { mutableStateOf<Bitmap?>(null) }
    var base by remember(file) { mutableStateOf<Bitmap?>(null) }
    var processed by remember(file) { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(initialFilter) }
    var rotation by remember { mutableIntStateOf(0) }
    var loading by remember(file) { mutableStateOf(true) }
    var cropOpen by remember { mutableStateOf(false) }
    var autoApplied by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) { Thread { val src = ScanProcessor.decode(file); val c = src?.let { ScanProcessor.autoCrop(it) }; android.os.Handler(android.os.Looper.getMainLooper()).post { original = src; base = c ?: src; autoApplied = c != null; loading = false } }.start() }
    LaunchedEffect(base, mode, rotation) { val b = base ?: return@LaunchedEffect; loading = true; Thread { val f = ScanProcessor.filter(b, mode); val r = when(rotation){90->ScanProcessor.rotate(f);180->ScanProcessor.rotate(ScanProcessor.rotate(f));270->ScanProcessor.rotate(ScanProcessor.rotate(ScanProcessor.rotate(f)));else->f}; android.os.Handler(android.os.Looper.getMainLooper()).post { processed = r; loading = false } }.start() }
    Scaffold(containerColor = Color(0xFF05080C), topBar = { TopAppBar(title = { Text("Edit & Enhance", color = Color.White) }, navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp), contentAlignment = Alignment.Center) { if (loading) CircularProgressIndicator() else processed?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(listOf("Original", "Color", "B&W", "Clean White")) { label -> FilterChip(selected = mode == label, onClick = { mode = label; onFilter(label) }, label = { Text(label) }) } }
            if (autoApplied) Text("Auto Crop applied", color = Color(0xFF27E0B3), modifier = Modifier.padding(start = 12.dp, top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = original != null && !loading, onClick = { val src = original ?: return@OutlinedButton; Thread { val c = ScanProcessor.autoCrop(src); android.os.Handler(android.os.Looper.getMainLooper()).post { base = c; rotation = 0; autoApplied = true; Toast.makeText(context, "Auto Crop applied", Toast.LENGTH_SHORT).show() } }.start() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CropFree, null); Spacer(Modifier.width(3.dp)); Text("Auto Crop") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { cropOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(3.dp)); Text("Manual Crop") }
                OutlinedButton(enabled = processed != null && !loading, onClick = { rotation = (rotation + 90) % 360 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(3.dp)); Text("Rotate") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "High Contrast"; onFilter(mode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(3.dp)); Text("Enhance") }; OutlinedButton(enabled = processed != null && !loading, onClick = { mode = "Original"; rotation = 0; base = original; autoApplied = false; onFilter("Original") }, modifier = Modifier.weight(1f)) { Text("Reset") } }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onAdd) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add Page") }; Button(enabled = processed != null && !loading, onClick = { saveScannerPage(context, processed)?.let(onFinish) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Done, null); Text("Finish") } }
        }
    }
    if (cropOpen && processed != null) ManualCropDialog(processed!!, { cropOpen = false }) { b -> base = b; rotation = 0; autoApplied = true; cropOpen = false }
}

@Composable
private fun ManualCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap) -> Unit) {
    var tl by remember(bitmap) { mutableStateOf(Offset(.08f,.08f)) }; var tr by remember(bitmap) { mutableStateOf(Offset(.92f,.08f)) }; var br by remember(bitmap) { mutableStateOf(Offset(.92f,.92f)) }; var bl by remember(bitmap) { mutableStateOf(Offset(.08f,.92f)) }; var active by remember { mutableIntStateOf(-1) }; val pts=listOf(tl,tr,br,bl)
    AlertDialog(onDismissRequest=onDismiss,title={Text("Manual Crop")},text={Column(Modifier.fillMaxWidth()){Text("Drag the four corners like a document scanner.",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(8.dp));Box(Modifier.fillMaxWidth().height(320.dp).background(Color.Black,RoundedCornerShape(12.dp))){Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit);Canvas(Modifier.fillMaxSize()){val w=size.width;val h=size.height;fun p(o:Offset)=Offset(o.x*w,o.y*h);val a=p(tl);val b=p(tr);val c=p(br);val d=p(bl);drawLine(Color(0xFF27E0B3),a,b,4f);drawLine(Color(0xFF27E0B3),b,c,4f);drawLine(Color(0xFF27E0B3),c,d,4f);drawLine(Color(0xFF27E0B3),d,a,4f);pts.forEach{drawCircle(Color(0xFF27E0B3),20f,p(it));drawCircle(Color.White,8f,p(it))}};Box(Modifier.fillMaxSize().pointerInput(bitmap){detectDragGestures(onDragStart={pos->val w=size.width.coerceAtLeast(1f);val h=size.height.coerceAtLeast(1f);val ds=listOf(tl,tr,br,bl).map{(pos.x-it.x*w)*(pos.x-it.x*w)+(pos.y-it.y*h)*(pos.y-it.y*h)};active=ds.withIndex().minByOrNull{it.value}?.index?:-1},onDragEnd={active=-1},onDragCancel={active=-1},onDrag={change,amount->change.consume();val w=size.width.coerceAtLeast(1f);val h=size.height.coerceAtLeast(1f);fun mv(o:Offset)=Offset((o.x+amount.x/w).coerceIn(.02f,.98f),(o.y+amount.y/h).coerceIn(.02f,.98f));when(active){0->tl=mv(tl);1->tr=mv(tr);2->br=mv(br);3->bl=mv(bl)}})})}}},confirmButton={Button(onClick={val minX=(minOf(tl.x,tr.x,br.x,bl.x)*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val maxX=(maxOf(tl.x,tr.x,br.x,bl.x)*bitmap.width).toInt().coerceIn(minX+1,bitmap.width);val minY=(minOf(tl.y,tr.y,br.y,bl.y)*bitmap.height).toInt().coerceIn(0,bitmap.height-1);val maxY=(maxOf(tl.y,tr.y,br.y,bl.y)*bitmap.height).toInt().coerceIn(minY+1,bitmap.height);onApply(Bitmap.createBitmap(bitmap,minX,minY,max(1,maxX-minX),max(1,maxY-minY)))}){Text("Apply Crop")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
private fun ScannerCameraPreview(modifier: Modifier,onCaptured:(File)->Unit){
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current; var camera by remember{mutableStateOf<Camera?>(null)};var capture by remember{mutableStateOf<ImageCapture?>(null)};var torch by remember{mutableStateOf(false)};var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)};val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it};LaunchedEffect(Unit){if(!granted)launcher.launch(Manifest.permission.CAMERA)}
    Box(modifier.background(Color.Black)){if(granted) AndroidView(factory={ctx->PreviewView(ctx).apply{scaleType=PreviewView.ScaleType.FILL_CENTER;implementationMode=PreviewView.ImplementationMode.COMPATIBLE;setOnTouchListener{_,e->if(e.action==MotionEvent.ACTION_UP){val c=camera;if(c!=null){val p=meteringFactory.createPoint(e.x,e.y);c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(p).build())}};true};val ex=Executors.newSingleThreadExecutor();val future=ProcessCameraProvider.getInstance(ctx);future.addListener({try{val provider=future.get();val preview=Preview.Builder().build().also{it.setSurfaceProvider(surfaceProvider)};val ic=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build();provider.unbindAll();camera=provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,ic);capture=ic}catch(e:Exception){Toast.makeText(ctx,"Camera failed: ${e.message}",Toast.LENGTH_LONG).show()}},ContextCompat.getMainExecutor(ctx));tag=ex}},modifier=Modifier.fillMaxSize(),update={}) else Text("Camera permission is required",color=Color.White,modifier=Modifier.align(Alignment.Center));Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={torch=!torch;camera?.cameraControl?.enableTorch(torch)}){Icon(if(torch)Icons.Default.FlashOn else Icons.Default.FlashOff,null,tint=Color.White)};FloatingActionButton(onClick={val ic=capture?:return@FloatingActionButton;val f=File(context.cacheDir,"scan_${System.currentTimeMillis()}.jpg");ic.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(),ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback{override fun onImageSaved(r:ImageCapture.OutputFileResults){onCaptured(f)};override fun onError(e:ImageCaptureException){Toast.makeText(context,"Capture failed: ${e.message}",Toast.LENGTH_LONG).show()}})}){Icon(Icons.Default.CameraAlt,null)};Spacer(Modifier.width(48.dp))}}
}
private val meteringFactory=SurfaceOrientedMeteringPointFactory(1f,1f)
private fun saveScannerPage(context:Context,bitmap:Bitmap?):File?{if(bitmap==null)return null;return try{val f=File(context.cacheDir,"page_${System.currentTimeMillis()}.jpg");FileOutputStream(f).use{bitmap.compress(Bitmap.CompressFormat.JPEG,94,it)};f}catch(_:Exception){null}}
