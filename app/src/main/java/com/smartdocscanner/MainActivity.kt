package com.smartdocscanner

import android.Manifest
import android.content.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFF27E0B3),
            onPrimary = Color(0xFF06110F),
            secondary = Color(0xFF8B6CFF),
            tertiary = Color(0xFF4DB6FF),
            background = Color(0xFF07090C),
            surface = Color(0xFF11151A),
            surfaceVariant = Color(0xFF1A2027)
        ) else lightColorScheme(
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
                onIdScan = { screen = "idscan" },
                onSettings = { screen = "settings" },
                onDocuments = { screen = "documents" },
                onHelp = { screen = "help" },
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
            "idscan" -> IdScanScreen(onBack = { screen = "home" }, onSaved = { refresh++; screen = "home" })
            "pdf" -> PdfToolsScreen(onBack = { screen = "home" })
            "viewer" -> selectedFile?.let { ViewerScreen(it, onBack = { screen = "home" }) }
            "help" -> HelpScreen(onBack = { screen = "home" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Color(0xFF05080C),
        topBar = { TopAppBar(title = { Text("Help & Support", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } }) }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(Color(0xFF05080C)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("SmartDoc Scanner", color = Color(0xFF27E0B3), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D151C))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Help", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("• Scan Document: camera se document capture karke crop, enhance aur PDF save karein.", color = Color(0xFFB6C3CF))
                Text("• Gallery to PDF: ek ya kai images select karke PDF banayein.", color = Color(0xFFB6C3CF))
                Text("• OCR: image se text read karke Word export karein.", color = Color(0xFFB6C3CF))
                Text("• ID Card Scan: front aur back ko ek A4 PDF me save karein.", color = Color(0xFFB6C3CF))
                Text("• PDF Tools: PDF ko images me convert, split, merge aur compress karein.", color = Color(0xFFB6C3CF))
            } } }
            item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820))) { Column(Modifier.padding(16.dp)) {
                Text("Important", color = Color(0xFF8B6CFF), fontWeight = FontWeight.Bold)
                Text("Editable Word/Excel conversion document ke layout aur OCR quality par depend karta hai; scanned page ko image ke roop me preserve karna aur fully editable reconstruction alag results de sakte hain.", color = Color(0xFFB6C3CF))
            } } }
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
    onIdScan: () -> Unit,
    onSettings: () -> Unit,
    onDocuments: () -> Unit,
    onHelp: () -> Unit,
    refresh: Int,
    onOpen: (File) -> Unit
) {
    val c = LocalContext.current
    var query by remember { mutableStateOf("") }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapIndexedNotNull { i, u ->
                copyUriToCache(c, u, "gallery_${System.currentTimeMillis()}_$i.jpg")?.let { prepareScanFile(c, it, "gallery_adjusted") }
            }
            if (files.isNotEmpty()) {
                val pdf = PdfEngine.createPdfAuto(c.filesDir, files, SettingsStore.pdfMode(c), SettingsStore.maxSizeChoice(c), SettingsStore.paperSize(c), "Gallery_Scan")
                if (pdf != null) { DocumentStore.add(c, DocumentRecord(System.currentTimeMillis(), "Gallery Scan", pdf.absolutePath)); Toast.makeText(c, "PDF saved", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var listRefresh by remember { mutableIntStateOf(0) }
    val docs = remember(refresh, listRefresh, query, selectedTab) {
        DocumentStore.all(c).filter { it.name.contains(query, true) }.filter { selectedTab == 0 || it.favorite }
    }

    Scaffold(
        containerColor = Color(0xFF05080C),
        topBar = {
            Column(Modifier.fillMaxWidth().background(Color(0xFF05080C)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDocuments) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("Smart", color = Color.White, fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold)
                        Text("DocScanner", color = Color(0xFF27E0B3), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color(0xFF9DB0C2)) }
                }
                Text("Scan  •  Convert  •  Organize  •  Share", color = Color(0xFFB6C3CF), style = MaterialTheme.typography.labelMedium)
                Text("Your documents, our smart solution", color = Color(0xFF718294), style = MaterialTheme.typography.labelSmall)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1117)) {
                NavigationBarItem(true, {}, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(false, onDocuments, { Icon(Icons.Default.Folder, null) }, label = { Text("My Files") })
                NavigationBarItem(false, onSettings, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFF05080C)).padding(pad).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(18.dp), placeholder = { Text("Search documents", color = Color(0xFF718294)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF27E0B3)) },
                    trailingIcon = if (query.isNotEmpty()) { { IconButton({ query = "" }) { Icon(Icons.Default.Clear, null) } } } else null,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF1D2A35), focusedBorderColor = Color(0xFF27E0B3), unfocusedContainerColor = Color(0xFF0C131A), focusedContainerColor = Color(0xFF0C131A))
                )
            }
            item {
                Card(onClick = onScan, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF27E0B3)), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF06110F)) { Icon(Icons.Default.CameraAlt, null, Modifier.padding(13.dp), tint = Color(0xFF27E0B3)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text("Scan Document", color = Color(0xFF06110F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Auto Capture • Auto Crop • HD Scan", color = Color(0xFF14362F), style = MaterialTheme.typography.labelSmall) }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF06110F))
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeActionCard("Gallery to PDF", Icons.Default.PhotoLibrary, Color(0xFF17C7A1), { gallery.launch(arrayOf("image/*")) }, Modifier.weight(1f))
                HomeActionCard("ID Card Scan", Icons.Default.CreditCard, Color(0xFF3B82F6), onIdScan, Modifier.weight(1f))
            }}
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeActionCard("OCR (Text)", Icons.Default.TextFields, Color(0xFF9B6CFF), onOcr, Modifier.weight(1f))
                HomeActionCard("PDF Tools", Icons.Default.PictureAsPdf, Color(0xFFFF4F5E), onPdf, Modifier.weight(1f))
            }}
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeActionCard("Image to Word", Icons.Default.Description, Color(0xFF2F80ED), onConvert, Modifier.weight(1f))
                HomeActionCard("Image to Excel", Icons.Default.GridOn, Color(0xFF19B96B), onConvert, Modifier.weight(1f))
            }}
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeActionCard("PDF to Images", Icons.Default.Image, Color(0xFFB57BFF), onPdf, Modifier.weight(1f))
                HomeActionCard("My Files", Icons.Default.Folder, Color(0xFF31B9E8), onDocuments, Modifier.weight(1f))
            }}
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeActionCard("Settings", Icons.Default.Settings, Color(0xFF91A4B8), onSettings, Modifier.weight(1f))
                HomeActionCard("Help & Support", Icons.Default.HelpOutline, Color(0xFF4DB6FF), onHelp, Modifier.weight(1f))
            }}
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedTab == 0) "Recent Documents" else "Favorites", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ selectedTab = if (selectedTab == 0) 1 else 0 }) { Text(if (selectedTab == 0) "View All" else "Recent", color = Color(0xFF27E0B3)) }
                }
            }
            if (docs.isEmpty()) item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1117))) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Description, null, tint = Color(0xFF526779), modifier = Modifier.size(42.dp)); Text("No documents yet", color = Color.White); Text("Your scanned PDFs will appear here.", color = Color(0xFF718294), style = MaterialTheme.typography.bodySmall) } } }
            items(docs.take(10)) { r -> DocumentCard(r, onOpen = { onOpen(File(r.path)) }, onChanged = { listRefresh++ }, onDelete = { listRefresh++ }) }
        }
    }
}

@Composable
private fun HomeActionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier.height(78.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0C141B)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF182631)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = 0.18f)) { Icon(icon, null, Modifier.padding(9.dp), tint = accent) }
            Spacer(Modifier.width(9.dp)); Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
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
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapIndexedNotNull { i, u ->
                copyUriToCache(c, u, "gallery_${System.currentTimeMillis()}_$i.jpg")?.let { prepareScanFile(c, it, "gallery_adjusted") }
            }
            if (files.isNotEmpty()) {
                val pdf = PdfEngine.createPdfAuto(c.filesDir, files, SettingsStore.pdfMode(c), SettingsStore.maxSizeChoice(c), SettingsStore.paperSize(c), "Gallery_Scan")
                if (pdf != null) { DocumentStore.add(c, DocumentRecord(System.currentTimeMillis(), "Gallery Scan", pdf.absolutePath)); Toast.makeText(c, "PDF saved", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    var onlyFavorites by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf("All folders") }
    var folderExpanded by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var listRefresh by remember { mutableIntStateOf(0) }
    val allDocs = remember(refresh, listRefresh) { DocumentStore.all(c) }
    val folders = remember(allDocs, listRefresh) { listOf("All folders") + (FolderStore.all(c) + allDocs.map { it.folder.trim() }).filter { it.isNotBlank() }.distinct().sorted() }
    val docs = remember(allDocs, query, onlyFavorites, folderFilter) {
        allDocs.filter { it.name.contains(query, true) }
            .filter { !onlyFavorites || it.favorite }
            .filter { folderFilter == "All folders" || it.folder.equals(folderFilter, true) }
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showNewFolder = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("New Folder")
                    }
                    OutlinedButton(onClick = { folderFilter = "All folders" }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("All Documents")
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick={folderExpanded=true}, modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Folder,null); Spacer(Modifier.width(8.dp)); Text(folderFilter); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown,null)
                    }
                    DropdownMenu(expanded=folderExpanded,onDismissRequest={folderExpanded=false}) {
                        folders.forEach { folder -> DropdownMenuItem(text={Text(folder)},onClick={folderFilter=folder;folderExpanded=false}) }
                    }
                }
            }
            if (docs.isEmpty()) item { Text("No matching documents") }
            items(docs) { r -> DocumentCard(r, { onOpen(File(r.path)) }, { listRefresh++ }, { listRefresh++ }) }
        }
    }
    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("Create folder") },
            text = { OutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true, label = { Text("Folder name") }) },
            confirmButton = {
                Button(onClick = {
                    if (FolderStore.add(c, newFolderName)) { newFolderName = ""; listRefresh++ }
                    showNewFolder = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack:()->Unit,onChanged:()->Unit){
    val c=LocalContext.current;var dark by remember{mutableStateOf(SettingsStore.darkTheme(c))};var crop by remember{mutableStateOf(SettingsStore.autoCrop(c))};var hindi by remember{mutableStateOf(SettingsStore.hindiOcr(c))};var filter by remember{mutableStateOf(SettingsStore.filter(c).ifBlank{"B&W"})};var paper by remember{mutableStateOf(SettingsStore.paperSize(c))};var size by remember{mutableStateOf(SettingsStore.maxSizeChoice(c))};var theme by remember{mutableStateOf("Dark")}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Settings",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->LazyColumn(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Appearance",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        item{SettingsRow("Theme",theme,false,onClick={theme=if(theme=="Dark")"System" else "Dark";SettingsStore.setDarkTheme(c,theme=="Dark");dark=theme=="Dark";onChanged()})}
        item{SettingsRow("Accent Colour","Teal Green + Purple",false,onClick={})}
        item{SettingsRow("Language","English / Hindi OCR",false,onClick={})}
        item{Text("Scan Settings",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
        item{SettingsRow("Default Filter",filter,false,onClick={filter=if(filter=="B&W")"Color" else "B&W";SettingsStore.setFilter(c,filter);onChanged()})}
        item{SettingsRow("Paper Size",paper,false,onClick={paper=if(paper=="A4")"Auto" else "A4";SettingsStore.setPaperSize(c,paper);onChanged()})}
        item{SettingsRow("Image Quality","High / JPEG 98%",false,onClick={})}
        item{SettingsRow("Auto Crop","Detect document edges after capture",crop,{v->crop=v;SettingsStore.setAutoCrop(c,v);onChanged()})}
        item{SettingsRow("Auto Save to Draft","Save immediately after capture",true,{})}
        item{SettingsRow("Maximum PDF Size",size,false,onClick={size=when(size){"500 KB"->"1 MB";"1 MB"->"2 MB";"2 MB"->"5 MB";"5 MB"->"10 MB";else->"500 KB"};SettingsStore.setMaxSizeChoice(c,size);onChanged()})}
        item{SettingsRow("Hindi / Devanagari OCR","Recognize Hindi text",hindi,{v->hindi=v;SettingsStore.setHindiOcr(c,v);onChanged()})}
        item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF101820))){Column(Modifier.padding(16.dp)){Text("SmartDocScanner",color=Color.White,fontWeight=FontWeight.Bold);Text("Dark modern document workspace",color=Color(0xFF8192A3))}}}
    }}
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

private fun prepareScanFile(context: android.content.Context, source: File, tag: String): File {
    return runCatching {
        val original = ScanProcessor.decode(source) ?: return@runCatching source
        val cropped = if (SettingsStore.autoCrop(context)) ScanProcessor.autoCrop(original) else original
        val filtered = ScanProcessor.filter(cropped, SettingsStore.filter(context))
        val out = File(context.cacheDir, "${tag}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { filtered.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        if (filtered !== cropped) filtered.recycle()
        if (cropped !== original) cropped.recycle()
        original.recycle()
        source.delete()
        out
    }.getOrElse { source }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onBack:()->Unit,onSaved:()->Unit){
    val c=LocalContext.current
    var captured by remember{mutableStateOf<File?>(null)}
    var pages by remember{mutableStateOf(listOf<File>())}
    var showName by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("Scanned Document")}
    var folder by remember{mutableStateOf("")}
    var autoSave by remember{mutableStateOf(true)}
    var draftId by remember{mutableStateOf<Long?>(null)}
    var filter by remember{mutableStateOf(SettingsStore.filter(c).ifBlank{"B&W"})}
    var paper by remember{mutableStateOf(SettingsStore.paperSize(c))}
    var size by remember{mutableStateOf(SettingsStore.maxSizeChoice(c))}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        val added=uris.mapIndexedNotNull{idx,u->copyUriToCache(c,u,"gallery_${System.currentTimeMillis()}_$idx.jpg")?.let{prepareScanFile(c,it,"gallery_adjusted")}}
        if(added.isNotEmpty()) pages=pages+added
    }
    fun autosave(){
        if(!autoSave) return
        val all=pages+(captured?:return)
        val draft=PdfEngine.createPdfAuto(c.filesDir,all,PdfEngine.SizeMode.MAXIMUM,size,paper,"AutoSaved_Draft") ?: return
        val id=draftId
        if(id==null){ val newId=System.currentTimeMillis(); draftId=newId; DocumentStore.add(c,DocumentRecord(newId,"Auto Saved Draft",draft.absolutePath,folder.trim())) }
        else DocumentStore.all(c).firstOrNull{it.id==id}?.let{old->File(old.path).delete();DocumentStore.update(c,old.copy(path=draft.absolutePath,folder=folder.trim()))}
    }
    if(captured!=null){
        ScanEditor(file=captured!!,pages=pages.size,onBack={autosave();captured=null},onAdd={f->pages=pages+f;captured=null;autosave()},onFinish={f->pages=pages+f;captured=null;showName=true})
        return
    }
    LaunchedEffect(Unit){ }
    if(showName){
        AlertDialog(onDismissRequest={showName=false},title={Text("Save Document")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Document name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(folder,{folder=it},label={Text("Folder (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("${pages.size} page(s) • $paper • $size",color=Color(0xFF9CAFC0))
        }},confirmButton={Button(onClick={
            val n=name.trim().ifBlank{"Scanned Document"}; val pdf=PdfEngine.createPdfAuto(c.filesDir,pages,PdfEngine.SizeMode.MAXIMUM,size,paper,n)
            if(pdf!=null){draftId?.let{id->DocumentStore.all(c).firstOrNull{it.id==id}?.let{d->File(d.path).delete();DocumentStore.delete(c,d.copy(path=""))}};DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),n,pdf.absolutePath,folder.trim()));showName=false;onSaved()}
        }){Text("Save PDF")}},dismissButton={TextButton({showName=false}){Text("Cancel")}})
    }
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Scan Document",color=Color.White)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.Close,null,tint=Color.White)}},actions={
        Surface(color=Color(0xFF101820),shape=RoundedCornerShape(14.dp)){Text("HD",color=Color(0xFF27E0B3),fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))}
    })},bottomBar={
        Row(Modifier.fillMaxWidth().background(Color(0xFF090F14)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(6.dp));Text("Gallery")}
            Button(onClick={if(pages.isNotEmpty())showName=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.Done,null);Spacer(Modifier.width(6.dp));Text("Finish")}
            OutlinedButton(onClick={showName=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(5.dp));Text("Save")}
        }
    }){pad->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)){
            Box(Modifier.fillMaxWidth().weight(1f)){
                CameraCapture(Modifier.fillMaxSize()){f->captured=f;autosave()}
                Column(Modifier.align(Alignment.TopCenter).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Surface(color=Color.Black.copy(.72f),shape=RoundedCornerShape(20.dp)){Text("Auto Detecting…",color=Color.White,modifier=Modifier.padding(horizontal=18.dp,vertical=8.dp))}
                    Spacer(Modifier.height(8.dp));Text("Place document inside the frame",color=Color.White,style=MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("Auto","Document","ID Card","Whiteboard").forEach{m->Surface(color=if(m=="Document")Color(0xFF27E0B3) else Color.Black.copy(.65f),shape=RoundedCornerShape(18.dp)){Text(m,color=if(m=="Document")Color.Black else Color.White,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))}}
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={filter="B&W";SettingsStore.setFilter(c,filter)},modifier=Modifier.weight(1f)){Text("B&W")}
                OutlinedButton(onClick={filter="Color";SettingsStore.setFilter(c,filter)},modifier=Modifier.weight(1f)){Text("Color")}
                OutlinedButton(onClick={paper=if(paper=="A4")"Auto" else "A4"},modifier=Modifier.weight(1f)){Text(paper)}
                OutlinedButton(onClick={showName=true},modifier=Modifier.weight(1f)){Text("${pages.size} Pages")}
            }
        }
    }
}

@Composable
fun CameraCapture(modifier: Modifier, onCaptured: (File) -> Unit) {
    val c=LocalContext.current
    val previewView=remember{PreviewView(c).apply{scaleType=PreviewView.ScaleType.FILL_CENTER;implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    val executor=remember{Executors.newSingleThreadExecutor()}; var capState by remember{mutableStateOf<ImageCapture?>(null)}; var busy by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){runCatching{val provider=ProcessCameraProvider.getInstance(c).get();val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider};val cap=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(98).build();capState=cap;provider.unbindAll();provider.bindToLifecycle(c as ComponentActivity,CameraSelector.DEFAULT_BACK_CAMERA,preview,cap)}}
    Box(modifier.background(Color.Black)){
        AndroidView({previewView},Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Surface(color=Color.Black.copy(.72f),shape=RoundedCornerShape(16.dp)){Text("AUTO • DOCUMENT",color=Color.White,modifier=Modifier.padding(10.dp))};Surface(color=Color.Black.copy(.72f),shape=RoundedCornerShape(16.dp)){Text("HD",color=Color(0xFF27E0B3),fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp))}}
        Box(Modifier.align(Alignment.Center).size(260.dp,340.dp)){Text("",Modifier.fillMaxSize().border(2.dp,Color(0xFF27E0B3),RoundedCornerShape(8.dp)))}
        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=88.dp),color=Color.Black.copy(.78f),shape=RoundedCornerShape(20.dp)){Text(if(busy)"Enhancing & auto-cropping…" else "Tap to capture • Auto adjust",color=Color.White,modifier=Modifier.padding(horizontal=18.dp,vertical=9.dp))}
        FilledIconButton(onClick={val cap=capState ?: return@FilledIconButton;if(busy)return@FilledIconButton;busy=true;val f=File(c.cacheDir,"scan_${System.currentTimeMillis()}.jpg");cap.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(),executor,object:ImageCapture.OnImageSavedCallback{override fun onError(e:ImageCaptureException){busy=false;Toast.makeText(c,e.message?:"Capture failed",Toast.LENGTH_SHORT).show()};override fun onImageSaved(r:ImageCapture.OutputFileResults){android.os.Handler(android.os.Looper.getMainLooper()).post{busy=false;onCaptured(prepareScanFile(c,f,"camera_adjusted"))}}})},Modifier.align(Alignment.BottomCenter).padding(bottom=18.dp).size(78.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)){Icon(Icons.Default.CameraAlt,"Capture",Modifier.size(34.dp))}
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=24.dp,bottom=22.dp),horizontalArrangement=Arrangement.SpaceBetween){IconButton(onClick={Toast.makeText(c,"Gallery is available below",Toast.LENGTH_SHORT).show()}){Icon(Icons.Default.PhotoLibrary,null,tint=Color.White)};IconButton(onClick={}){Icon(Icons.Default.FlashOn,null,tint=Color.White)}}
    }
    DisposableEffect(Unit){onDispose{executor.shutdown()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanEditor(file:File,onBack:()->Unit,onAdd:(File)->Unit,onFinish:(File)->Unit,pages:Int){
    val c=LocalContext.current; var bmp by remember(file){mutableStateOf(ScanProcessor.decode(file))}; var filter by remember{mutableStateOf(SettingsStore.filter(c).ifBlank{"B&W"})}; var showCrop by remember{mutableStateOf(false)}
    fun savePage():File?=runCatching{val out=File(c.cacheDir,"page_${System.currentTimeMillis()}.jpg");FileOutputStream(out).use{bmp?.compress(Bitmap.CompressFormat.JPEG,95,it)};out}.getOrNull()
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Edit & Enhance",color=Color.White)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}},actions={Button(onClick={savePage()?.let(onFinish)}){Text("Save")}})}){pad->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)){
            Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp),contentAlignment=Alignment.Center){bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}}
            LazyRow(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Original","Auto Enhance","B&W","Color").forEach{label->Card(onClick={filter=if(label=="Original")"Color" else if(label=="Auto Enhance")"High Contrast" else label;SettingsStore.setFilter(c,filter);bmp=bmp?.let{ScanProcessor.filter(it,filter)}},colors=CardDefaults.cardColors(containerColor=if((label=="B&W"&&filter=="B&W")||(label=="Color"&&filter=="Color")||(label=="Auto Enhance"&&filter=="High Contrast") )Color(0xFF27E0B3) else Color(0xFF111820)),modifier=Modifier.width(105.dp)){Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Photo,null,tint=Color.White);Text(label,color=Color.White,style=MaterialTheme.typography.labelSmall)}}}}
            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton({showCrop=true}){Icon(Icons.Default.Crop,null);Text("Crop")};TextButton({bmp=bmp?.let{ScanProcessor.rotate(it)}}){Icon(Icons.Default.RotateRight,null);Text("Rotate")};TextButton({bmp=bmp?.let{ScanProcessor.filter(it,"High Contrast")}}){Icon(Icons.Default.AutoAwesome,null);Text("Enhance")};TextButton({}){Icon(Icons.Default.Tune,null);Text("Filter")};TextButton({bmp=ScanProcessor.decode(file)}){Icon(Icons.Default.Refresh,null);Text("Reset")}}
            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={savePage()?.let(onAdd)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text("Add Page")};Button(onClick={savePage()?.let(onFinish)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Done,null);Text("Finish & Save")}}
        }
    }
    if(showCrop&&bmp!=null)ManualCropDialog(bmp!!,{showCrop=false},{x->bmp=x;showCrop=false})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(onBack:()->Unit){
    val c=LocalContext.current; var text by remember{mutableStateOf("")}; var image by remember{mutableStateOf<Bitmap?>(null)}
    fun runOcr(file:File){val src=runCatching{InputImage.fromFilePath(c,Uri.fromFile(file))}.getOrNull()?:return;TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(src).addOnSuccessListener{en->text=en.text;if(SettingsStore.hindiOcr(c)){TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(src).addOnSuccessListener{hi->if(hi.text.isNotBlank())text=hi.text}}}.addOnFailureListener{Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{copyUriToCache(c,it,"ocr_${System.currentTimeMillis()}.jpg")?.let{f->image=ScanProcessor.decode(f);runOcr(f)}}}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){b->b?.let{image=it;val f=File(c.cacheDir,"ocr_camera_${System.currentTimeMillis()}.jpg");FileOutputStream(f).use{out->it.compress(Bitmap.CompressFormat.JPEG,95,out)};runOcr(f)}}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("OCR (Text)",color=Color.White)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({gallery.launch("image/*")},Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Text("Gallery")};OutlinedButton({camera.launch(null)},Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Text("Camera")}}
        Card(Modifier.fillMaxWidth().weight(1f),colors=CardDefaults.cardColors(containerColor=Color(0xFF0D151C))){Column(Modifier.padding(14.dp)){Text("Recognized Text",color=Color(0xFF27E0B3),fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));if(image!=null)Image(image!!.asImageBitmap(),null,Modifier.fillMaxWidth().height(150.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit);Text(if(text.isBlank())"Select or capture a document to extract text." else text,color=Color.White,modifier=Modifier.verticalScroll(rememberScrollState()))}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({Toast.makeText(c,"Text copied",Toast.LENGTH_SHORT).show()},Modifier.weight(1f)){Icon(Icons.Default.ContentCopy,null);Text("Copy")};OutlinedButton({exportOcrPages(c,listOfNotNull(image?.let{val f=File(c.cacheDir,"ocr_export.jpg");FileOutputStream(f).use{out->it.compress(Bitmap.CompressFormat.JPEG,95,out)};f}),"OCR_Editable",false){it?.let{ShareUtil.share(c,it,"application/vnd.openxmlformats-officedocument.wordprocessingml.document")}}},Modifier.weight(1f)){Text("To Word")};OutlinedButton({Toast.makeText(c,"Use PDF/scan for table-aware Excel conversion",Toast.LENGTH_SHORT).show()},Modifier.weight(1f)){Text("To Excel")}}
    }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack:()->Unit){
    val c=LocalContext.current; var chosen by remember{mutableStateOf<File?>(null)}; var mode by remember{mutableStateOf("Word")}; var status by remember{mutableStateOf("Choose an image or PDF")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->chosen=uri?.let{copyUriToCache(c,it,"convert_${System.currentTimeMillis()}.jpg")};status=if(chosen!=null)"Ready for OCR + layout conversion" else "Choose an image or PDF"}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("Convert",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Card(Modifier.fillMaxWidth().height(180.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF101820))){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.Description,null,tint=Color(0xFF27E0B3),modifier=Modifier.size(54.dp));Text(status,color=Color.White);Text("OCR + layout reconstruction",color=Color(0xFF8192A3))}}
        Spacer(Modifier.height(16.dp));Button({picker.launch("image/*")},Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoLibrary,null);Text("Select Image")};Spacer(Modifier.height(12.dp));Text("Export format",color=Color.White,fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode=="Word",{mode="Word"},label={Text("Word")},modifier=Modifier.weight(1f));FilterChip(mode=="Excel",{mode="Excel"},label={Text("Excel")},modifier=Modifier.weight(1f))};Spacer(Modifier.height(12.dp));Button({chosen?.let{exportOcrPages(c,listOf(it),"Converted_Editable",mode=="Excel"){out->if(out!=null)ShareUtil.share(c,out,if(mode=="Excel")"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")}}},Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Text("Convert to Editable $mode")}
    }}
}

@Composable
fun ManualCropDialog(source: Bitmap, onDismiss:()->Unit, onApply:(Bitmap)->Unit){
    var left by remember{mutableFloatStateOf(0f)}
    var top by remember{mutableFloatStateOf(0f)}
    var right by remember{mutableFloatStateOf(1f)}
    var bottom by remember{mutableFloatStateOf(1f)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Manual Crop")},text={
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text("Left");Slider(left,{left=it.coerceAtMost(right-0.05f)})
            Text("Top");Slider(top,{top=it.coerceAtMost(bottom-0.05f)})
            Text("Right");Slider(right,{right=it.coerceAtLeast(left+0.05f)})
            Text("Bottom");Slider(bottom,{bottom=it.coerceAtLeast(top+0.05f)})
        }
    },confirmButton={Button(onClick={
        val l=(source.width*left).toInt(); val t=(source.height*top).toInt()
        val w=(source.width*(right-left)).toInt().coerceAtLeast(1); val h=(source.height*(bottom-top)).toInt().coerceAtLeast(1)
        onApply(Bitmap.createBitmap(source,l,t,w,h))
    }){Text("Apply")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

fun copyUriToCache(c: android.content.Context, uri: Uri, name: String): File? = runCatching {
    val f=File(c.cacheDir,name)
    c.contentResolver.openInputStream(uri)?.use{input->f.outputStream().use{input.copyTo(it)}}
    f
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdScanScreen(onBack:()->Unit,onSaved:()->Unit){
    val c=LocalContext.current; var front by remember{mutableStateOf<File?>(null)};var back by remember{mutableStateOf<File?>(null)};var tab by remember{mutableStateOf("Front")};var showName by remember{mutableStateOf(false)};var name by remember{mutableStateOf("ID Card")}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->if(uris.isNotEmpty()){copyUriToCache(c,uris[0],"id_front.jpg")?.let{front=prepareScanFile(c,it,"id_front")};if(uris.size>1)copyUriToCache(c,uris[1],"id_back.jpg")?.let{back=prepareScanFile(c,it,"id_back")}}}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("ID Card Scan",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Front","Back","Preview").forEach{t->FilterChip(tab==t,{tab=t},label={Text(t)},modifier=Modifier.weight(1f))}}
        Card(Modifier.fillMaxWidth().weight(1f).padding(vertical=12.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0C141B))){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){val f=if(tab=="Back")back else front;if(f!=null){ScanProcessor.decode(f)?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().padding(12.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}}else{Icon(Icons.Default.CreditCard,null,tint=Color(0xFF27E0B3),modifier=Modifier.size(64.dp));Text("Scan ${tab.lowercase()} side",color=Color.White)}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({pick.launch("image/*")},Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Text("Gallery")};Button({Toast.makeText(c,"Use camera scanner for ${tab.lowercase()} side",Toast.LENGTH_SHORT).show()},Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Text("Camera")}}
        Text("A4 output • Front + Back on one page",color=Color(0xFF8192A3),modifier=Modifier.padding(8.dp));Button({if(front!=null&&back!=null)showName=true},Modifier.fillMaxWidth(),enabled=front!=null&&back!=null){Icon(Icons.Default.PictureAsPdf,null);Text("Preview & Create A4 PDF")}
    }}
    if(showName)AlertDialog(onDismissRequest={showName=false},title={Text("Save ID PDF")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("Document name")})},confirmButton={Button({val pdf=PdfEngine.createIdCardPdf(c.filesDir,front!!,back!!,name.ifBlank{"ID Card"});if(pdf!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),name.ifBlank{"ID Card"},pdf.absolutePath));showName=false;onSaved()}}){Text("Save")}},dismissButton={TextButton({showName=false}){Text("Cancel")}})
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
    val c=LocalContext.current;var message by remember{mutableStateOf("Choose a PDF tool")}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->val fs=uris.mapIndexedNotNull{i,u->copyUriToCache(c,u,"img_${System.currentTimeMillis()}_$i.jpg")};if(fs.isNotEmpty())PdfEngine.createPdfAuto(c.filesDir,fs,PdfEngine.SizeMode.MAXIMUM,SettingsStore.maxSizeChoice(c),SettingsStore.paperSize(c),"Images_to_PDF")?.let{DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),"Images to PDF",it.absolutePath));message="Images converted to PDF"}}
    val merge=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->val fs=uris.mapIndexedNotNull{i,u->copyUriToCache(c,u,"merge_${System.currentTimeMillis()}_$i.pdf")};if(fs.size>=2)PdfEngine.mergePdfs(c.filesDir,fs,"Merged_Document")?.let{message="Merged ${fs.size} PDFs"}else message="Select at least 2 PDFs"}
    val one=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{copyUriToCache(c,it,"split.pdf")?.let{f->message="Split into ${PdfEngine.splitPdf(c.filesDir,f).size} pages"}}}
    val comp=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{copyUriToCache(c,it,"compress.pdf")?.let{f->message=if(PdfEngine.compressPdf(c.filesDir,f,"Compressed")!=null)"Compressed PDF created" else "Compression failed"}}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{copyUriToCache(c,it,"export.pdf")?.let{f->val outs=PdfEngine.pdfToImages(f,c.filesDir);val z=zipFiles(c.filesDir,"PDF_Images",outs);if(z!=null)ShareUtil.share(c,z,"application/zip");message="Exported ${outs.size} page(s)"}}}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text("PDF Tools",color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}})}){pad->LazyColumn(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("All PDF tools",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolCard("Merge PDF","Combine multiple PDFs",Icons.Default.Merge,{merge.launch(arrayOf("application/pdf"))},Modifier.weight(1f));ToolCard("Split PDF","Split into pages",Icons.Default.ContentCut,{one.launch(arrayOf("application/pdf"))},Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolCard("Compress PDF","Reduce file size",Icons.Default.Compress,{comp.launch(arrayOf("application/pdf"))},Modifier.weight(1f));ToolCard("PDF to Images","Extract all pages",Icons.Default.Image,{export.launch(arrayOf("application/pdf"))},Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolCard("Images to PDF","Convert images to PDF",Icons.Default.PhotoLibrary,{imagePicker.launch(arrayOf("image/*"))},Modifier.weight(1f));ToolCard("Reorder Pages","Arrange pages",Icons.Default.Reorder,{message="Reorder available from scan editor"},Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolCard("Add Password","Protect your PDF",Icons.Default.Lock,{message="Password protection coming next"},Modifier.weight(1f));ToolCard("Remove Password","Unlock your PDF",Icons.Default.LockOpen,{message="Password removal coming next"},Modifier.weight(1f))}}
        item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF101820))){Text(message,color=Color(0xFFB6C3CF),modifier=Modifier.padding(16.dp))}}
    }}
}

private fun zipFiles(dir: File, baseName: String, files: List<File>): File? = runCatching {
    val out = File(dir, "${baseName}_${System.currentTimeMillis()}.zip")
    ZipOutputStream(FileOutputStream(out)).use { z ->
        files.forEachIndexed { i, f ->
            z.putNextEntry(ZipEntry("page_${i + 1}.jpg"))
            f.inputStream().use { it.copyTo(z) }
            z.closeEntry()
        }
    }
    out
}.getOrNull()

private fun exportOcrPages(context: android.content.Context, images: List<File>, title: String, excel: Boolean, onDone: (File?) -> Unit) {
    if (images.isEmpty()) { onDone(null); return }
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val results = mutableListOf<String>()

    fun next(index: Int) {
        if (index >= images.size) {
            val text = results.joinToString("\n\n")
            val out = if (excel) {
                OfficeExporter.xlsx(context, title, text)
            } else {
                OfficeExporter.docx(context, title, text)
            }
            onDone(out)
            return
        }

        val source = runCatching {
            InputImage.fromFilePath(context, Uri.fromFile(images[index]))
        }.getOrNull()

        if (source == null) {
            results += ""
            next(index + 1)
            return
        }

        recognizer.process(source)
            .addOnSuccessListener { en ->
                if (SettingsStore.hindiOcr(context)) {
                    TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                        .process(source)
                        .addOnSuccessListener { hi ->
                            results += if (hi.text.isNotBlank()) hi.text else en.text
                            next(index + 1)
                        }
                        .addOnFailureListener {
                            results += en.text
                            next(index + 1)
                        }
                } else {
                    results += en.text
                    next(index + 1)
                }
            }
            .addOnFailureListener {
                results += ""
                next(index + 1)
            }
    }

    next(0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(file:File,onBack:()->Unit){
    val c=LocalContext.current;var page by remember{mutableIntStateOf(0)};var count by remember{mutableIntStateOf(0)};var bmp by remember{mutableStateOf<Bitmap?>(null)};var rename by remember{mutableStateOf(false)};var newName by remember{mutableStateOf(file.nameWithoutExtension)}
    fun render(i:Int){runCatching{val pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);val r=PdfRenderer(pfd);count=r.pageCount;val p=r.openPage(i);val b=Bitmap.createBitmap(p.width*2,p.height*2,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();r.close();pfd.close();bmp=b;page=i}}
    LaunchedEffect(file){render(0)}
    Scaffold(containerColor=Color(0xFF05080C),topBar={TopAppBar(title={Text(file.name,color=Color.White)},navigationIcon={IconButton({onBack()}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}},actions={IconButton({rename=true}){Icon(Icons.Default.Edit,null,tint=Color.White)};IconButton({ShareUtil.share(c,file,"application/pdf")}){Icon(Icons.Default.Share,null,tint=Color.White)};IconButton({}){Icon(Icons.Default.MoreVert,null,tint=Color.White)}})}){pad->Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)){
        Card(Modifier.fillMaxWidth().weight(1f).padding(10.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF101820))){bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize().padding(8.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)}}
        Text("${page+1}/$count",color=Color.White,modifier=Modifier.align(Alignment.CenterHorizontally))
        LazyRow(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(count){i->Card(onClick={render(i)},border=if(i==page)androidx.compose.foundation.BorderStroke(2.dp,Color(0xFF27E0B3)) else null){Text("${i+1}",color=Color.White,modifier=Modifier.padding(16.dp))}}}}
        Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({ShareUtil.share(c,file,"application/pdf")},Modifier.weight(1f)){Icon(Icons.Default.Share,null);Text("Share")};OutlinedButton({Toast.makeText(c,"Rename from the edit menu",Toast.LENGTH_SHORT).show()},Modifier.weight(1f)){Icon(Icons.Default.Edit,null);Text("Rename")};Button({val outs=PdfEngine.pdfToImages(file,c.filesDir);Toast.makeText(c,"Exported ${outs.size} images",Toast.LENGTH_SHORT).show()},Modifier.weight(1f)){Icon(Icons.Default.Image,null);Text("Pages")}}
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({val outs=PdfEngine.pdfToImages(file,c.filesDir);exportOcrPages(c,outs,file.nameWithoutExtension+"_Editable",false){o->if(o!=null)ShareUtil.share(c,o,"application/vnd.openxmlformats-officedocument.wordprocessingml.document")}},Modifier.weight(1f)){Text("Text (OCR)")};OutlinedButton({val outs=PdfEngine.pdfToImages(file,c.filesDir);exportOcrPages(c,outs,file.nameWithoutExtension+"_Editable",true){o->if(o!=null)ShareUtil.share(c,o,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}},Modifier.weight(1f)){Text("More")}}
    }
    if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Rename PDF")},text={OutlinedTextField(newName,{newName=it},singleLine=true)},confirmButton={Button({val n=newName.trim().ifBlank{file.nameWithoutExtension};val out=File(file.parentFile,"$n.pdf");file.renameTo(out);rename=false}){Text("Save")}},dismissButton={TextButton({rename=false}){Text("Cancel")}})
}
