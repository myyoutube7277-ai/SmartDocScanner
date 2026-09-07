package com.smartdocscanner

import android.Manifest
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartDocApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDocApp() {
    val c = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var settingsVersion by remember { mutableIntStateOf(0) }
    val dark = remember(settingsVersion) { SettingsStore.darkTheme(c) }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(
            primary = Color(0xFF315F9A),
            secondary = Color(0xFF526B82),
            tertiary = Color(0xFF6A5B8C),
            surface = Color(0xFFF8F9FC)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        when (screen) {
            "home" -> HomeScreen(
                onScan = { screen = "scan" },
                onOcr = { screen = "ocr" },
                onConvert = { screen = "convert" },
                onBarcode = { screen = "barcode" },
                onPdf = { screen = "pdf" },
                onSettings = { screen = "settings" },
                onDocuments = { screen = "documents" },
                refresh = refresh,
                onOpen = { selectedFile = it; screen = "viewer" }
            )
            "documents" -> DocumentsScreen(
                refresh = refresh,
                onBack = { screen = "home" },
                onOpen = { selectedFile = it; screen = "viewer" }
            )
            "settings" -> SettingsScreen(
                onBack = { screen = "home" },
                onChanged = { settingsVersion++ }
            )
            "scan" -> ScannerScreen(
                onBack = { screen = "home" },
                onSaved = { refresh++; screen = "home" }
            )
            "ocr" -> OcrScreen(onBack = { screen = "home" })
            "convert" -> ConvertScreen(onBack = { screen = "home" })
            "barcode" -> BarcodeScreen(onBack = { screen = "home" })
            "pdf" -> PdfToolsScreen(onBack = { screen = "home" })
            "viewer" -> selectedFile?.let { ViewerScreen(it, onBack = { screen = "home" }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOcr: () -> Unit,
    onConvert: () -> Unit,
    onBarcode: () -> Unit,
    onPdf: () -> Unit,
    onSettings: () -> Unit,
    onDocuments: () -> Unit,
    refresh: Int,
    onOpen: (File) -> Unit
) {
    val c = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val docs = remember(refresh, query, selectedTab) {
        DocumentStore.all(c)
            .filter { it.name.contains(query, true) }
            .filter { selectedTab == 0 || it.favorite }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SmartDoc", fontWeight = FontWeight.Bold)
                        Text("Document Scanner", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDocuments) {
                        Icon(Icons.Default.Folder, "Documents")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onDocuments,
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("Documents") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.DocumentScanner, null) },
                text = { Text("Scan") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search documents") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) } }
                    } else null
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Scan documents professionally", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Capture, enhance, save as PDF and extract text.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Document")
                        }
                    }
                }
            }

            item { Text("Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard("OCR", "Hindi + English", Icons.Default.TextFields, onOcr, Modifier.weight(1f))
                    ToolCard("Convert", "Image to Office", Icons.Default.Description, onConvert, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard("PDF Tools", "Images / PDF", Icons.Default.PictureAsPdf, onPdf, Modifier.weight(1f))
                    ToolCard("QR / Barcode", "Scan instantly", Icons.Default.QrCodeScanner, onBarcode, Modifier.weight(1f))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (selectedTab == 0) "Recent" else "Favorites"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedTab = if (selectedTab == 0) 1 else 0 }) {
                        Text(if (selectedTab == 0) "Favorites" else "All")
                    }
                }
            }

            if (docs.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Description, null, Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (selectedTab == 0) "No documents yet" else "No favorite documents")
                            Text("Your scanned PDFs will appear here.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            items(docs.take(10)) { r ->
                DocumentCard(r, onOpen = { onOpen(File(r.path)) }, onChanged = { }, onDelete = { })
            }
        }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier.height(116.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentCard(r: DocumentRecord, onOpen: () -> Unit, onChanged: () -> Unit, onDelete: () -> Unit) {
    val c = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(r.name) }

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(r.folder.ifBlank { "Root" }, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = {
                val copy = r.copy(favorite = !r.favorite)
                DocumentStore.update(c, copy)
                onChanged()
            }) {
                Icon(if (r.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite")
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { showMenu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; rename = true })
                    DropdownMenuItem(text = { Text(if (r.favorite) "Remove favorite" else "Add favorite") }, onClick = {
                        showMenu = false
                        DocumentStore.update(c, r.copy(favorite = !r.favorite))
                        onChanged()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        showMenu = false
                        DocumentStore.delete(c, r)
                        onDelete()
                    })
                }
            }
        }
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Document name") })
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) DocumentStore.update(c, r.copy(name = newName.trim()))
                    rename = false
                    onChanged()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(refresh: Int, onBack: () -> Unit, onOpen: (File) -> Unit) {
    val c = LocalContext.current
    var query by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    val docs = remember(refresh, query, onlyFavorites) {
        DocumentStore.all(c).filter { it.name.contains(query, true) }.filter { !onlyFavorites || it.favorite }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("My Documents") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorites only", Modifier.weight(1f))
                    Switch(onlyFavorites, { onlyFavorites = it })
                }
            }
            if (docs.isEmpty()) item { Text("No matching documents") }
            items(docs) { r -> DocumentCard(r, { onOpen(File(r.path)) }, {}, {}) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val c = LocalContext.current
    var dark by remember { mutableStateOf(SettingsStore.darkTheme(c)) }
    var crop by remember { mutableStateOf(SettingsStore.autoCrop(c)) }
    var hindi by remember { mutableStateOf(SettingsStore.hindiOcr(c)) }
    var mode by remember { mutableStateOf(SettingsStore.pdfMode(c)) }
    var maxMb by remember { mutableIntStateOf(SettingsStore.maxMb(c)) }
    var filter by remember { mutableStateOf(SettingsStore.filter(c)) }
    var showMode by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    fun changed() = onChanged()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Scanning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                SettingsRow(
                    "Automatic crop",
                    "Trim document margins after capture",
                    crop,
                    onCheckedChange = { value ->
                        crop = value
                        SettingsStore.setAutoCrop(c, value)
                        changed()
                    }
                )
            }
            item {
                SettingsRow("Default filter", filter, false, onClick = { showFilter = true })
            }
            item {
                SettingsRow("PDF mode", if (mode == PdfEngine.SizeMode.QUALITY) "Quality Based" else "Maximum Size", false, onClick = { showMode = true })
            }
            item {
                OutlinedTextField(
                    value = maxMb.toString(),
                    onValueChange = { value ->
                        val n = value.toIntOrNull() ?: 2
                        maxMb = n.coerceAtLeast(2)
                        SettingsStore.setMaxMb(c, maxMb)
                        changed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Maximum PDF size (MB, minimum 2)") }
                )
            }
            item { HorizontalDivider() }
            item { Text("OCR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                SettingsRow(
                    "Hindi / Devanagari OCR",
                    "Recognize Hindi text when available",
                    hindi,
                    onCheckedChange = { value ->
                        hindi = value
                        SettingsStore.setHindiOcr(c, value)
                        changed()
                    }
                )
            }
            item { HorizontalDivider() }
            item { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                SettingsRow(
                    "Dark theme",
                    "Use dark appearance",
                    dark,
                    onCheckedChange = { value ->
                        dark = value
                        SettingsStore.setDarkTheme(c, value)
                        changed()
                    }
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("About SmartDoc", fontWeight = FontWeight.SemiBold)
                        Text("Professional document scanning, OCR and PDF tools.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (showMode) {
            AlertDialog(onDismissRequest = { showMode = false }, title = { Text("Default PDF mode") }, text = {
                Column {
                    listOf(PdfEngine.SizeMode.QUALITY to "Quality Based", PdfEngine.SizeMode.MAXIMUM to "Maximum Size").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == value, onClick = {
                                mode = value; SettingsStore.setPdfMode(c, value); showMode = false; changed()
                            })
                            Text(label)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showMode = false }) { Text("Close") } })
        }

        if (showFilter) {
            AlertDialog(onDismissRequest = { showFilter = false }, title = { Text("Default scan filter") }, text = {
                Column {
                    listOf("Color", "Gray", "B&W", "High Contrast").forEach { value ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = filter == value, onClick = {
                                filter = value; SettingsStore.setFilter(c, value); showFilter = false; changed()
                            })
                            Text(value)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showFilter = false }) { Text("Close") } })
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Card(onClick = { onClick?.invoke() }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (onCheckedChange != null) Switch(checked, onCheckedChange) else Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onBack:()->Unit,onSaved:()->Unit){
    val c=LocalContext.current
    var captured by remember{mutableStateOf<File?>(null)}
    var mode by remember{mutableStateOf(SettingsStore.pdfMode(c))}
    var maxMb by remember{mutableIntStateOf(SettingsStore.maxMb(c))}
    var pages by remember{mutableStateOf(listOf<File>())}
    if(captured!=null){
        ScanEditor(captured!!,onBack={captured=null},onAdd={f->pages=pages+f;captured=null},onFinish={
            val pdf=PdfEngine.createPdf(c.filesDir,pages,mode,maxMb,"SmartDoc")
            if(pdf!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),"Scanned Document",pdf.absolutePath));Toast.makeText(c,"PDF saved",Toast.LENGTH_SHORT).show();onSaved()}
            else Toast.makeText(c,"Maximum size target could not be met",Toast.LENGTH_LONG).show()
        },pages=pages.size)
        return
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(!granted) Toast.makeText(c,"Camera permission is required",Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit){permission.launch(Manifest.permission.CAMERA)}
    Scaffold(topBar={TopAppBar(title={Text("Document Scanner")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).fillMaxSize()){
            CameraCapture(Modifier.fillMaxWidth().weight(1f)){captured=it}
            Text("PDF output size",Modifier.padding(horizontal=16.dp))
            Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(mode==PdfEngine.SizeMode.QUALITY,{mode=PdfEngine.SizeMode.QUALITY},label={Text("Quality Based")})
                FilterChip(mode==PdfEngine.SizeMode.MAXIMUM,{mode=PdfEngine.SizeMode.MAXIMUM},label={Text("Maximum Size")})
            }
            if(mode==PdfEngine.SizeMode.MAXIMUM){
                OutlinedTextField(maxMb.toString(),{maxMb=(it.toIntOrNull()?:2).coerceAtLeast(2)},Modifier.fillMaxWidth().padding(horizontal=16.dp),label={Text("Maximum MB (minimum 2)")})
            }
            Text("Pages queued: ${pages.size}",Modifier.padding(16.dp))
        }
    }
}

@Composable
fun CameraCapture(modifier:Modifier,onCaptured:(File)->Unit){
    val c=LocalContext.current
    val previewView=remember{PreviewView(c)}
    val executor=remember{Executors.newSingleThreadExecutor()}
    var imageCapture by remember{mutableStateOf<ImageCapture?>(null)}
    LaunchedEffect(Unit){
        val provider=ProcessCameraProvider.getInstance(c).get()
        val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}
        val cap=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        imageCapture=cap
        provider.unbindAll()
        provider.bindToLifecycle(c as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,preview,cap)
    }
    Box(modifier){
        AndroidView({previewView},Modifier.fillMaxSize())
        Button(onClick={
            val f=File(c.cacheDir,"scan_${System.currentTimeMillis()}.jpg")
            val opts=ImageCapture.OutputFileOptions.Builder(f).build()
            imageCapture?.takePicture(opts,executor,object:ImageCapture.OnImageSavedCallback{
                override fun onError(e:ImageCaptureException){Toast.makeText(c,e.message?:"Capture failed",Toast.LENGTH_SHORT).show()}
                override fun onImageSaved(r:ImageCapture.OutputFileResults){onCaptured(f)}
            })
        },Modifier.align(Alignment.BottomCenter).padding(24.dp)){Icon(Icons.Default.Camera,null);Spacer(Modifier.width(8.dp));Text("Capture")}
    }
}

@Composable
fun ScanEditor(file:File,onBack:()->Unit,onAdd:(File)->Unit,onFinish:()->Unit,pages:Int){
    val c=LocalContext.current
    var bmp by remember(file){mutableStateOf(ScanProcessor.decode(file)?.let{ if (SettingsStore.autoCrop(c)) ScanProcessor.autoCrop(it) else it })}
    var filter by remember{mutableStateOf(SettingsStore.filter(c))}
    LaunchedEffect(file) { if (filter != "Color") bmp = bmp?.let { ScanProcessor.filter(it, filter) } }
    Scaffold(topBar={TopAppBar(title={Text("Edit Scan")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
            bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Color","Gray","B&W","High Contrast").forEach{FilterChip(filter==it,{filter=it;bmp=bmp?.let{x->ScanProcessor.filter(x,it)}},label={Text(it)})}
            }
            Row(modifier=Modifier.padding(top=8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={bmp=bmp?.let{ScanProcessor.rotate(it)}}){Text("Rotate 90°")}
                Button(onClick={
                    val out=File(c.cacheDir,"page_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(out).use{bmp?.compress(Bitmap.CompressFormat.JPEG,92,it)}
                    onAdd(out)
                }){Text("Add Page")}
                if(pages>0) Button(onClick=onFinish){Text("Finish PDF ($pages)")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(onBack:()->Unit){
    val c=LocalContext.current
    var result by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
        if(uri==null)return@rememberLauncherForActivityResult
        loading=true
        val source=InputImage.fromFilePath(c,uri)
        val a=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val d=TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        a.process(source).addOnSuccessListener{en->
            d.process(source).addOnSuccessListener{hi->
                result=(if(hi.text.isNotBlank())hi.text else en.text);loading=false
            }.addOnFailureListener{result=en.text;loading=false}
        }.addOnFailureListener{loading=false;Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}
    }
    Scaffold(topBar={TopAppBar(title={Text("OCR Reader")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick={picker.launch(arrayOf("image/*","application/pdf"))}){Text("Select Image")}
            if(loading)CircularProgressIndicator()
            OutlinedTextField(result,{result=it},Modifier.fillMaxWidth().weight(1f),label={Text("Editable OCR text")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={c.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("OCR",result))}){Text("Copy")}
                Button(onClick={
                    val f=OfficeExporter.docx(c,"OCR_Document",result);ShareUtil.share(c,f,"application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                }){Text("Word")}
                Button(onClick={
                    val f=OfficeExporter.xlsx(c,"OCR_Sheet",result);ShareUtil.share(c,f,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }){Text("Excel")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack:()->Unit){
    val c=LocalContext.current
    var text by remember{mutableStateOf("")}
    var chosen by remember{mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri==null)return@rememberLauncherForActivityResult
        chosen=uri.toString()
        val source=InputImage.fromFilePath(c,uri)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(source).addOnSuccessListener{recognized->
            text=recognized.text
        }
    }
    Scaffold(topBar={TopAppBar(title={Text("Image → Editable")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick={picker.launch(arrayOf("image/*"))}){Text("Choose Photo")}
            Text(if(chosen.isBlank())"No photo selected" else "Photo selected")
            OutlinedTextField(text,{text=it},Modifier.fillMaxWidth().weight(1f),label={Text("OCR + layout text")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={ShareUtil.share(c,OfficeExporter.docx(c,"Editable_Document",text),"application/vnd.openxmlformats-officedocument.wordprocessingml.document")}){Text("DOCX")}
                Button(onClick={ShareUtil.share(c,OfficeExporter.xlsx(c,"Editable_Sheet",text),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}){Text("XLSX")}
            }
            Text("Note: complex tables, fonts and exact page geometry are reconstructed approximately; 100% pixel-identical Word/Excel conversion is not guaranteed.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(onBack:()->Unit){
    val c=LocalContext.current
    var result by remember{mutableStateOf("Point camera at a QR/barcode")}
    val preview=remember{PreviewView(c)}
    LaunchedEffect(Unit){
        val provider=ProcessCameraProvider.getInstance(c).get()
        val p=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider}
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val scanner=BarcodeScanning.getClient()
        analysis.setAnalyzer(Executors.newSingleThreadExecutor()){proxy->
            val media=proxy.image
            if(media!=null){
                scanner.process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener{codes->if(codes.isNotEmpty())result=codes.joinToString("\n"){it.rawValue?:"(no value)"}}
                    .addOnCompleteListener{proxy.close()}
            }else proxy.close()
        }
        provider.unbindAll();provider.bindToLifecycle(c as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis)
    }
    Scaffold(topBar={TopAppBar(title={Text("QR / Barcode")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad)){
            AndroidView({preview},Modifier.fillMaxWidth().weight(1f))
            Text(result,Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(onBack:()->Unit){
    val c=LocalContext.current
    var message by remember{mutableStateOf("")}
    val images=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty()){
            val fs=uris.mapIndexedNotNull{idx,u->
                runCatching{
                    val f=File(c.cacheDir,"img_${System.currentTimeMillis()}_$idx.jpg")
                    c.contentResolver.openInputStream(u)?.use{ins->f.outputStream().use{ins.copyTo(it)}}
                    f
                }.getOrNull()
            }
            val pdf=PdfEngine.createPdf(c.filesDir,fs,PdfEngine.SizeMode.QUALITY,2,"ImagesToPDF")
            if(pdf!=null){message="Created: ${pdf.name}";ShareUtil.share(c,pdf,"application/pdf")}
        }
    }
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri==null)return@rememberLauncherForActivityResult
        val f=File(c.cacheDir,"source_${System.currentTimeMillis()}.pdf")
        c.contentResolver.openInputStream(uri)?.use{ins->f.outputStream().use{ins.copyTo(it)}}
        val outs=PdfEngine.pdfToImages(f,c.filesDir)
        message="Exported ${outs.size} pages"
    }
    Scaffold(topBar={TopAppBar(title={Text("PDF Tools")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={images.launch(arrayOf("image/*"))}){Text("Images → PDF")}
            Button(onClick={pdfPicker.launch(arrayOf("application/pdf"))}){Text("PDF → Images")}
            Button(onClick={pdfPicker.launch(arrayOf("application/pdf"))}){Text("Open PDF / Export Pages")}
            Text(message)
            Text("PDF merge/split/password/compression hooks can be added with the included PDFBox dependency; the core scanner/PDF/OCR modules are ready.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(file:File,onBack:()->Unit){
    val c=LocalContext.current
    Scaffold(topBar={TopAppBar(title={Text(file.name)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("Saved PDF: ${file.absolutePath}")
            Spacer(Modifier.height(16.dp))
            Button(onClick={ShareUtil.share(c,file,"application/pdf")}){Text("Share PDF")}
            Button(onClick={
                c.startActivity(Intent(Intent.ACTION_VIEW).apply{
                    setDataAndType(androidx.core.content.FileProvider.getUriForFile(c,"${c.packageName}.provider",file),"application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }){Text("Open / Print")}
        }
    }
}
