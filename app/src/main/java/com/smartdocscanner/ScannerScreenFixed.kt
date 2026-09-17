package com.smartdocscanner
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenFixed(onBack:()->Unit,onSaved:()->Unit){
    val c=LocalContext.current
    var captured by remember{mutableStateOf<File?>(null)}
    var pages by remember{mutableStateOf(listOf<File>())}
    var filter by remember{mutableStateOf("Color")}
    var showSave by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("Scanned Document")}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        val fs=uris.mapIndexedNotNull{i,u->copyUriToCache(c,u,"gallery_${System.currentTimeMillis()}_$i.jpg")}
        if(fs.isNotEmpty()) pages=pages+fs
    }
    if(captured!=null){
        ScannerEditFixed(captured!!,filter,{filter=it},{f->pages=pages+f;captured=null},{captured=null},{f->pages=pages+f;captured=null;showSave=true})
        return
    }
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Scan Document",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.Close,null,tint=Color.White)}})},bottomBar={
        Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({gallery.launch(arrayOf("image/*"))},Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Text("Gallery")}
            Button({if(pages.isNotEmpty())showSave=true},Modifier.weight(1f)){Icon(Icons.Default.Done,null);Text("Finish")}
        }
    }){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)){
        Box(Modifier.fillMaxWidth().weight(1f)){
            CameraCaptureFixed(Modifier.fillMaxSize()){captured=it}
            Surface(Modifier.align(Alignment.TopCenter).padding(14.dp),color=Color.Black.copy(.70f),shape=RoundedCornerShape(18.dp)){Text("HD • Document",color=Color.White,modifier=Modifier.padding(10.dp))}
            Box(Modifier.align(Alignment.Center).size(290.dp,400.dp).border(2.dp,Color(0xFF27E0B3),RoundedCornerShape(12.dp)))
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF0B1218)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(filter=="Color",{filter="Color"},label={Text("Color")},modifier=Modifier.weight(1f))
            FilterChip(filter=="B&W",{filter="B&W"},label={Text("B&W")},modifier=Modifier.weight(1f))
            FilterChip(filter=="High Contrast",{filter="High Contrast"},label={Text("Clean White")},modifier=Modifier.weight(1f))
        }
    }}
    if(showSave){AlertDialog(onDismissRequest={showSave=false},title={Text("Save Document")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Document name")})},confirmButton={Button({
        val out=PdfEngine.createPdfAuto(c.filesDir,pages,PdfEngine.SizeMode.MAXIMUM,SettingsStore.maxSizeChoice(c),SettingsStore.paperSize(c),name.ifBlank{"Scanned Document"})
        if(out!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),name.ifBlank{"Scanned Document"},out.absolutePath));showSave=false;onSaved()}
    }){Text("Save PDF")}},dismissButton={TextButton({showSave=false}){Text("Cancel")}})}
}

@Composable
private fun ScannerEditFixed(file:File,initialFilter:String,onFilter:(String)->Unit,onAdd:(File)->Unit,onCancel:()->Unit,onFinish:(File)->Unit){
    val c=LocalContext.current
    val original=remember(file){ScanProcessor.decode(file)}
    val auto=remember(file,original){original?.let{if(SettingsStore.autoCrop(c))ScanProcessor.autoCrop(it) else it}}
    var mode by remember{mutableStateOf(initialFilter)}
    var bmp by remember(file){mutableStateOf(auto)}
    fun apply(m:String){mode=m;onFilter(m);bmp=auto?.let{ScanProcessor.filter(it,m)}}
    LaunchedEffect(auto){if(auto!=null)bmp=ScanProcessor.filter(auto,mode)}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Edit & Enhance",color=Color.White)},navigationIcon={IconButton({onCancel()}){Icon(Icons.Default.Close,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)){
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp),contentAlignment=Alignment.Center){bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}}
        LazyRow(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(listOf("Original","Color","B&W","Clean White")){m->val x=if(m=="Original")"Color" else if(m=="Clean White")"High Contrast" else m;FilterChip(mode==x,{apply(x)},label={Text(m)})}
        }
        Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({bmp=bmp?.let{ScanProcessor.rotate(it)}},Modifier.weight(1f)){Icon(Icons.Default.RotateRight,null);Text("Rotate")}
            OutlinedButton({bmp=auto},Modifier.weight(1f)){Icon(Icons.Default.Refresh,null);Text("Reset")}
        }
        Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({saveScannerPage(c,bmp)?.let(onAdd)},Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text("Add Page")}
            Button({saveScannerPage(c,bmp)?.let(onFinish)},Modifier.weight(1f)){Icon(Icons.Default.Done,null);Text("Finish")}
        }
    }}
}

private fun saveScannerPage(c:Context,bmp:Bitmap?):File?=runCatching{val f=File(c.cacheDir,"scan_page_${System.currentTimeMillis()}.jpg");FileOutputStream(f).use{bmp?.compress(Bitmap.CompressFormat.JPEG,97,it)};f}.getOrNull()

@Composable
private fun CameraCaptureFixed(modifier:Modifier,onCaptured:(File)->Unit){
    val c=LocalContext.current
    val preview=remember{PreviewView(c).apply{scaleType=PreviewView.ScaleType.FILL_CENTER}}
    val executor=remember{Executors.newSingleThreadExecutor()}
    var capture by remember{mutableStateOf<ImageCapture?>(null)}
    var flash by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){runCatching{
        val provider=ProcessCameraProvider.getInstance(c).get()
        val p=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider}
        val cap=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(100).build()
        capture=cap;provider.unbindAll();provider.bindToLifecycle(c as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,p,cap)
    }}
    Box(modifier.background(Color.Black)){
        AndroidView({preview},Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){
            IconButton({flash=!flash;capture?.flashMode=if(flash)ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF}){Icon(if(flash)Icons.Default.FlashOn else Icons.Default.FlashOff,null,tint=Color.White)}
            FilledIconButton({capture?.let{cap->val f=File(c.cacheDir,"raw_scan_${System.currentTimeMillis()}.jpg");cap.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(),executor,object:ImageCapture.OnImageSavedCallback{override fun onError(e:ImageCaptureException){Toast.makeText(c,"Capture failed",Toast.LENGTH_SHORT).show()};override fun onImageSaved(r:ImageCapture.OutputFileResults){android.os.Handler(android.os.Looper.getMainLooper()).post{onCaptured(f)}}})}},Modifier.size(78.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)){Icon(Icons.Default.CameraAlt,null,Modifier.size(34.dp))}
            IconButton({Toast.makeText(c,"Keep phone parallel to the page",Toast.LENGTH_SHORT).show()}){Icon(Icons.Default.Info,null,tint=Color.White)}
        }
    }
    DisposableEffect(Unit){onDispose{executor.shutdown()}}
}
