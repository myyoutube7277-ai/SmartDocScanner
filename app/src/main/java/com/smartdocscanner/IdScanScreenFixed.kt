package com.smartdocscanner
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.concurrent.Executors
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun IdScanScreenFixed(onBack:()->Unit,onSaved:()->Unit){
    val c=LocalContext.current;var front by remember{mutableStateOf<File?>(null)};var back by remember{mutableStateOf<File?>(null)};var side by remember{mutableStateOf("Front")};var show by remember{mutableStateOf(false)};var name by remember{mutableStateOf("ID Card")};var camera by remember{mutableStateOf(false)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){u->if(u.isNotEmpty()){copyUriToCache(c,u[0],"id_front_${System.currentTimeMillis()}.jpg")?.let{front=it};if(u.size>1)copyUriToCache(c,u[1],"id_back_${System.currentTimeMillis()}.jpg")?.let{back=it}}}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("ID Card Scan",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Front","Back","Preview").forEach{t->FilterChip(side==t,{side=t},label={Text(t)},modifier=Modifier.weight(1f))}}
        Card(Modifier.fillMaxWidth().weight(1f).padding(vertical=12.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0C141B))){if(side=="Preview"){Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){IdPreviewFixed(front,"Front");IdPreviewFixed(back,"Back")} } else {val f=if(side=="Front")front else back;if(f!=null){BitmapFactory.decodeFile(f.absolutePath)?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().fillMaxHeight(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}}else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("${side} side not captured",color=Color.White)}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({pick.launch("image/*")},Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Text("Gallery")};Button({camera=true},Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Text("Camera")}}
        Text("Front + Back → one A4 PDF",color=Color(0xFF27E0B3),modifier=Modifier.padding(8.dp));Button({if(front!=null&&back!=null)show=true},Modifier.fillMaxWidth(),enabled=front!=null&&back!=null){Icon(Icons.Default.PictureAsPdf,null);Text("Create A4 PDF")}
    }}
    if(camera){IdCameraCaptureFixed(side,{camera=false}, {f->if(side=="Front")front=f else back=f;camera=false})}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text("Save ID PDF")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Document name")})},confirmButton={Button({val pdf=PdfEngine.createIdCardPdf(c.filesDir,front!!,back!!,name.ifBlank{"ID Card"});if(pdf!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),name.ifBlank{"ID Card"},pdf.absolutePath));show=false;onSaved()}}){Text("Save")}},dismissButton={TextButton({show=false}){Text("Cancel")}})
}
@Composable private fun IdPreviewFixed(f:File?,label:String){Column(Modifier.fillMaxWidth()){Text(label,color=Color(0xFF27E0B3));if(f!=null)BitmapFactory.decodeFile(f.absolutePath)?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().height(145.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}else Text("Not captured",color=Color.Gray)}}
@Composable private fun IdCameraCaptureFixed(side:String,onClose:()->Unit,onCaptured:(File)->Unit){val c=LocalContext.current;val p=remember{PreviewView(c)};val ex=remember{Executors.newSingleThreadExecutor()};var cap by remember{mutableStateOf<ImageCapture?>(null)};LaunchedEffect(Unit){runCatching{val pr=ProcessCameraProvider.getInstance(c).get();val pv=Preview.Builder().build().also{it.surfaceProvider=p.surfaceProvider};val cp=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(100).build();cap=cp;pr.unbindAll();pr.bindToLifecycle(c as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,pv,cp)}};Surface(color=Color.Black.copy(.96f),shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Capture $side",color=Color.White);IconButton({onClose()}){Icon(Icons.Default.Close,null,tint=Color.White)}};AndroidView({p},Modifier.fillMaxWidth().height(430.dp));Button({cap?.let{f->val out=File(c.cacheDir,"id_${side.lowercase()}_${System.currentTimeMillis()}.jpg");f.takePicture(ImageCapture.OutputFileOptions.Builder(out).build(),ex,object:ImageCapture.OnImageSavedCallback{override fun onError(e:ImageCaptureException){Toast.makeText(c,"Capture failed",Toast.LENGTH_SHORT).show()};override fun onImageSaved(r:ImageCapture.OutputFileResults){android.os.Handler(android.os.Looper.getMainLooper()).post{onCaptured(out)}}})}},Modifier.fillMaxWidth()){Icon(Icons.Default.CameraAlt,null);Text("Capture $side")}}}}
