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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
            primary = Color(0xFFFFD21F),
            onPrimary = Color(0xFF171717),
            secondary = Color(0xFFFFB800),
            tertiary = Color(0xFFFFD95A),
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

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery Scan")
                    }
                    OutlinedButton(onClick = onIdScan, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CreditCard, null); Spacer(Modifier.width(6.dp)); Text("ID Scan")
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
                DocumentCard(r, onOpen = { onOpen(File(r.path)) }, onChanged = { listRefresh++ }, onDelete = { listRefresh++ })
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
fun SettingsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val c = LocalContext.current
    var dark by remember { mutableStateOf(SettingsStore.darkTheme(c)) }
    var crop by remember { mutableStateOf(SettingsStore.autoCrop(c)) }
    var hindi by remember { mutableStateOf(SettingsStore.hindiOcr(c)) }
    var mode by remember { mutableStateOf(SettingsStore.pdfMode(c)) }
    var filter by remember { mutableStateOf(SettingsStore.filter(c).ifBlank { "B&W" }) }
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
                var expandedPaper by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick={expandedPaper=true}, modifier=Modifier.fillMaxWidth()) {
                        Text("Paper size: ${SettingsStore.paperSize(c)}"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown,null)
                    }
                    DropdownMenu(expanded=expandedPaper,onDismissRequest={expandedPaper=false}) {
                        listOf("Auto","A4","A5","Letter","Legal").forEach { value -> DropdownMenuItem(text={Text(value)},onClick={SettingsStore.setPaperSize(c,value);expandedPaper=false;changed()}) }
                    }
                }
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick={expanded=true}, modifier=Modifier.fillMaxWidth()) {
                        Text("Maximum PDF size: ${SettingsStore.maxSizeChoice(c)}"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown,null)
                    }
                    DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                        listOf("500 KB","1 MB","2 MB","5 MB","10 MB","20 MB","50 MB").forEach { size ->
                            DropdownMenuItem(text={Text(size)},onClick={SettingsStore.setMaxSizeChoice(c,size);expanded=false;changed()})
                        }
                    }
                }
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
                    listOf("B&W", "Color", "Gray", "High Contrast").forEach { value ->
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
    var mode by remember{mutableStateOf(SettingsStore.pdfMode(c))}
    var maxChoice by remember{mutableStateOf(SettingsStore.maxSizeChoice(c))}
    var paperSize by remember{mutableStateOf(SettingsStore.paperSize(c))}
    var pages by remember{mutableStateOf(listOf<File>())}
    var showName by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("Scanned Document")}
    var folder by remember{mutableStateOf("")}
    var autoSave by remember{mutableStateOf(true)}
    var draftId by remember{mutableStateOf<Long?>(null)}
    var showSize by remember{mutableStateOf(false)}
    var showPaper by remember{mutableStateOf(false)}
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris ->
        val added=uris.mapIndexedNotNull{idx,u->copyUriToCache(c,u,"gallery_${System.currentTimeMillis()}_$idx.jpg")?.let{prepareScanFile(c,it,"gallery_adjusted")}}
        if(added.isNotEmpty()) {
            pages=pages+added
            if (autoSave) {
                val draft = PdfEngine.createPdfAuto(c.filesDir, pages, PdfEngine.SizeMode.QUALITY, maxChoice, paperSize, "AutoSaved_Draft")
                if (draft != null) {
                    val id = draftId
                    if (id == null) {
                        val newId = System.currentTimeMillis(); draftId = newId
                        DocumentStore.add(c, DocumentRecord(newId, "Auto Saved Draft", draft.absolutePath, folder.trim()))
                    } else {
                        DocumentStore.all(c).firstOrNull { it.id == id }?.let { old -> File(old.path).delete(); DocumentStore.update(c, old.copy(path=draft.absolutePath, folder=folder.trim())) }
                    }
                }
            }
        }
    }
    fun saveDraftNow(current: List<File>) {
        if (!autoSave || current.isEmpty()) return
        val draft = PdfEngine.createPdfAuto(c.filesDir, current, PdfEngine.SizeMode.QUALITY, maxChoice, paperSize, "AutoSaved_Draft") ?: return
        val id = draftId
        if (id == null) {
            val newId = System.currentTimeMillis(); draftId = newId
            DocumentStore.add(c, DocumentRecord(newId, "Auto Saved Draft", draft.absolutePath, folder.trim()))
        } else {
            DocumentStore.all(c).firstOrNull { it.id == id }?.let { old ->
                File(old.path).delete()
                DocumentStore.update(c, old.copy(path = draft.absolutePath, folder = folder.trim()))
            }
        }
    }
    if(captured!=null){
        ScanEditor(
            file=captured!!,
            onBack={
                val current = pages + captured!!
                saveDraftNow(current)
                pages = current
                captured = null
            },
            onAdd={f->
                val all = pages + f
                pages = all
                captured=null
                if (autoSave) {
                    val draft = PdfEngine.createPdfAuto(c.filesDir, all, PdfEngine.SizeMode.QUALITY, maxChoice, paperSize, "AutoSaved_Draft")
                    if (draft != null) {
                        val id = draftId
                        if (id == null) {
                            val newId = System.currentTimeMillis()
                            draftId = newId
                            DocumentStore.add(c, DocumentRecord(newId, "Auto Saved Draft", draft.absolutePath, folder.trim()))
                        } else {
                            val old = DocumentStore.all(c).firstOrNull { it.id == id }
                            old?.let { File(it.path).delete(); DocumentStore.update(c, it.copy(path = draft.absolutePath, folder = folder.trim())) }
                        }
                    }
                }
            },
            onFinish={f->
                pages=pages+f
                captured=null
                showName=true
            },
            pages=pages.size
        )
        return
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(!granted) Toast.makeText(c,"Camera permission is required",Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit){permission.launch(Manifest.permission.CAMERA)}
    if(showName){
        AlertDialog(
            onDismissRequest={showName=false},
            title={Text("Save PDF")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(name,{name=it},label={Text("Document name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(folder,{folder=it},label={Text("Folder (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth(),placeholder={Text("e.g. Office / 2026")})
                Text("${pages.size} page(s) • ${SettingsStore.paperSize(c)} • ${if(mode==PdfEngine.SizeMode.QUALITY)"Quality" else maxChoice}",style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Auto save draft", Modifier.weight(1f))
                    Switch(autoSave, { autoSave = it })
                }
            }},
            confirmButton={Button(onClick={
                val finalName=name.trim().ifBlank{"Scanned Document"}
                val pdf=PdfEngine.createPdfAuto(c.filesDir,pages,mode,maxChoice,paperSize,finalName)
                if(pdf!=null){
                    draftId?.let { id ->
                        DocumentStore.all(c).firstOrNull { it.id == id }?.let { draft ->
                            File(draft.path).delete()
                            DocumentStore.delete(c, draft.copy(path = ""))
                        }
                    }
                    DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),finalName,pdf.absolutePath,folder.trim()))
                    draftId=null
                    Toast.makeText(c,"PDF saved",Toast.LENGTH_SHORT).show();showName=false;onSaved()
                } else Toast.makeText(c,"Could not create PDF within selected size",Toast.LENGTH_LONG).show()
            }){Text("Save")}},
            dismissButton={TextButton(onClick={showName=false}){Text("Cancel")}}
        )
    }
    Scaffold(topBar={TopAppBar(title={Text("Document Scanner")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).fillMaxSize()){
            CameraCapture(Modifier.fillMaxWidth().weight(1f)){f ->
                captured = f
                // Save a draft immediately when the shutter is pressed.
                saveDraftNow(pages + f)
            }
            Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(5.dp));Text("Gallery")}
                Button(onClick={if(pages.isNotEmpty() || captured!=null)showName=true},modifier=Modifier.weight(1f)){Text("Finish (${pages.size + if(captured!=null)1 else 0})")}
            }
            Row(Modifier.padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={showPaper=true},modifier=Modifier.weight(1f)){Text(if(paperSize=="Auto") "Paper: Auto Detect" else "Paper: ${paperSize}");Spacer(Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)}
                OutlinedButton(onClick={showSize=true},modifier=Modifier.weight(1f)){Text(if(mode==PdfEngine.SizeMode.QUALITY)"Quality Based" else maxChoice);Spacer(Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)}
            }
            if (pages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages.size) { index ->
                        val pageFile = pages[index]
                        Card {
                            Column(Modifier.width(120.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ScanProcessor.decode(pageFile)?.let { preview ->
                                    Image(preview.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth().height(140.dp))
                                }
                                Text("Page ${index + 1}", style = MaterialTheme.typography.labelSmall)
                                Row {
                                    IconButton(enabled = index > 0, onClick = { pages = pages.toMutableList().apply { add(index - 1, removeAt(index)) } }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                    IconButton(onClick = { pages = pages.toMutableList().apply { removeAt(index) } }) { Icon(Icons.Default.Delete, "Delete page") }
                                    IconButton(enabled = index < pages.lastIndex, onClick = { pages = pages.toMutableList().apply { add(index + 1, removeAt(index)) } }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                }
                            }
                        }
                    }
                }
            }
            Text("Pages queued: ${pages.size}",Modifier.padding(16.dp))
        }
    }
    if(showSize)AlertDialog(onDismissRequest={showSize=false},title={Text("PDF output size")},text={Column{listOf("500 KB","1 MB","2 MB","5 MB","10 MB","20 MB","50 MB").forEach{choice->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=choice==maxChoice,onClick={maxChoice=choice;SettingsStore.setMaxSizeChoice(c,choice);mode=PdfEngine.SizeMode.MAXIMUM;showSize=false});Text(choice)}}}},confirmButton={TextButton(onClick={showSize=false}){Text("Close")}})
    if(showPaper)AlertDialog(onDismissRequest={showPaper=false},title={Text("Paper size")},text={Column{listOf("Auto","A4","A5","Letter","Legal").forEach{choice->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=choice==paperSize,onClick={paperSize=choice;SettingsStore.setPaperSize(c,choice);showPaper=false});Text(choice)}}}},confirmButton={TextButton(onClick={showPaper=false}){Text("Close")}})
}

@Composable
fun CameraCapture(modifier: Modifier, onCaptured: (File) -> Unit) {
    val c = LocalContext.current
    val previewView = remember {
        PreviewView(c).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(c).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val cap = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()
        imageCapture = cap
        provider.unbindAll()
        provider.bindToLifecycle(c as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
    }
    Box(modifier.background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        // Solid dark controls keep scanner actions readable over every camera scene.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(18.dp)) {
                Text("AUTO • DOCUMENT", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(18.dp)) {
                Text("HD", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            shape = RoundedCornerShape(34.dp), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .76f)
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Processing…" else "Tap to capture • Auto adjust", color = androidx.compose.ui.graphics.Color.White)
            }
        }
        Button(
            enabled = !busy,
            onClick = {
                val cap = imageCapture
                if (cap != null) {
                busy = true
                val f = File(c.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(f).build()
                cap.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(e: ImageCaptureException) {
                        busy = false
                        Toast.makeText(c, e.message ?: "Capture failed", Toast.LENGTH_SHORT).show()
                    }
                    override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            busy = false
                            onCaptured(prepareScanFile(c, f, "camera_adjusted"))
                        }
                    }
                })
                }
            },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp).size(84.dp),
            shape = RoundedCornerShape(50.dp)
        ) {
            Icon(Icons.Default.CameraAlt, "Capture", Modifier.size(34.dp))
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanEditor(file:File,onBack:()->Unit,onAdd:(File)->Unit,onFinish:(File)->Unit,pages:Int){
    val c=LocalContext.current
    var bmp by remember(file){mutableStateOf(ScanProcessor.decode(file))}
    var filter by remember{mutableStateOf(SettingsStore.filter(c))}
    var showCrop by remember{mutableStateOf(false)}
    Scaffold(topBar={TopAppBar(title={Text("Edit Scan")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
            bmp?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Color","Gray","B&W","High Contrast").forEach{FilterChip(filter==it,{filter=it;bmp=bmp?.let{x->ScanProcessor.filter(x,it)}},label={Text(it)})}
            }
            Row(modifier=Modifier.padding(top=8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={showCrop=true}){Icon(Icons.Default.Crop,null);Spacer(Modifier.width(4.dp));Text("Manual Crop")}
                OutlinedButton(onClick={bmp=bmp?.let{ScanProcessor.rotate(it)}}){Icon(Icons.Default.RotateRight,null);Spacer(Modifier.width(4.dp));Text("Rotate")}
            }
            Row(modifier=Modifier.padding(top=8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={
                    val out=File(c.cacheDir,"page_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(out).use{bmp?.compress(Bitmap.CompressFormat.JPEG,92,it)}
                    onAdd(out)
                }){Icon(Icons.Default.Add,null);Spacer(Modifier.width(4.dp));Text("Add Page")}
                Button(onClick={
                    val out=File(c.cacheDir,"page_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(out).use{bmp?.compress(Bitmap.CompressFormat.JPEG,92,it)}
                    onFinish(out)
                }){Icon(Icons.Default.Done,null);Spacer(Modifier.width(4.dp));Text("Finish & Save") }
            }
        }
    }
    if(showCrop && bmp!=null) {
        ManualCropDialog(bmp!!, onDismiss={showCrop=false}, onApply={cropped->bmp=cropped;showCrop=false})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(onBack:()->Unit){
    val c=LocalContext.current
    var result by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    var cameraMode by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
        if(uri==null)return@rememberLauncherForActivityResult
        loading=true
        val source=InputImage.fromFilePath(c,uri)
        val a=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        if (SettingsStore.hindiOcr(c)) {
            val d=TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            a.process(source).addOnSuccessListener{en->
                d.process(source).addOnSuccessListener{hi->
                    result=(if(hi.text.isNotBlank())hi.text else en.text);loading=false
                }.addOnFailureListener{result=en.text;loading=false}
            }.addOnFailureListener{loading=false;Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}
        } else {
            a.process(source).addOnSuccessListener{en-> result=en.text; loading=false}
                .addOnFailureListener{loading=false;Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}
        }
    }
    Scaffold(topBar={TopAppBar(title={Text("OCR Reader")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick={picker.launch(arrayOf("image/*"))}){Text("Gallery Image")}
            Button(onClick={cameraMode=true}){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(6.dp));Text("Camera") }
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
    if(cameraMode){
        CameraCapture(Modifier.fillMaxSize(), onCaptured={file->
            cameraMode=false; loading=true
            val source=InputImage.fromFilePath(c,Uri.fromFile(file))
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(source).addOnSuccessListener{en->
                if(SettingsStore.hindiOcr(c)){
                    TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(source).addOnSuccessListener{hi->result=if(hi.text.isNotBlank())hi.text else en.text;loading=false}.addOnFailureListener{result=en.text;loading=false}
                } else { result=en.text;loading=false }
            }.addOnFailureListener{loading=false;Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}
        })
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
    val c=LocalContext.current
    var front by remember{mutableStateOf<File?>(null)}
    var back by remember{mutableStateOf<File?>(null)}
    var side by remember{mutableStateOf("front")}
    var camera by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("ID Document")}
    var showName by remember{mutableStateOf(false)}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){u->
        if(u.size>=2){
            front=copyUriToCache(c,u[0],"id_front_${System.currentTimeMillis()}.jpg")?.let{prepareScanFile(c,it,"id_front")}
            back=copyUriToCache(c,u[1],"id_back_${System.currentTimeMillis()}.jpg")?.let{prepareScanFile(c,it,"id_back")}
        }
    }
    if(camera){
        CameraCapture(Modifier.fillMaxSize()){f->
            if(side=="front"){front=f;side="back";Toast.makeText(c,"Front saved. Now capture Back",Toast.LENGTH_SHORT).show()}
            else{back=f;camera=false;Toast.makeText(c,"Front + Back ready",Toast.LENGTH_SHORT).show()}
        }
        return
    }
    Scaffold(topBar={TopAppBar(title={Text("ID Scan")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("ID Scan",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Capture both sides. The final PDF places Front + Back together on one A4 page.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={side="front";camera=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(5.dp));Text("Front")}
                Button(onClick={side="back";camera=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(5.dp));Text("Back")}
            }
            OutlinedButton(onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(8.dp));Text("Select Front + Back")}
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(if(front==null)"Front: Not captured" else "Front: ✓ Ready", fontWeight=FontWeight.SemiBold)
                Text(if(back==null)"Back: Not captured" else "Back: ✓ Ready", fontWeight=FontWeight.SemiBold)
            }}
            if(front!=null&&back!=null) Button(onClick={showName=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PictureAsPdf,null);Spacer(Modifier.width(8.dp));Text("Preview & Create A4 PDF")}
        }
    }
    if(showName)AlertDialog(onDismissRequest={showName=false},title={Text("Save ID PDF")},text={Column{OutlinedTextField(name,{name=it},singleLine=true,label={Text("Document name")});Spacer(Modifier.height(10.dp));Text("Front + Back will be combined on one A4 page.")}},confirmButton={Button(onClick={
        val pdf=PdfEngine.createIdCardPdf(c.filesDir,front!!,back!!,name.ifBlank{"ID Document"})
        if(pdf!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),name.ifBlank{"ID Document"},pdf.absolutePath));Toast.makeText(c,"ID PDF saved",Toast.LENGTH_SHORT).show();showName=false;onSaved()}
    }){Text("Save")}},dismissButton={TextButton(onClick={showName=false}){Text("Cancel")}})
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
    var message by remember{mutableStateOf("Choose a PDF tool")}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty()){
            val fs=uris.mapIndexedNotNull{idx,u->copyUriToCache(c,u,"pdf_image_${System.currentTimeMillis()}_$idx.jpg")}
            val pdf=PdfEngine.createPdfAuto(c.filesDir,fs,SettingsStore.pdfMode(c),SettingsStore.maxSizeChoice(c),SettingsStore.paperSize(c),"Images_to_PDF")
            if(pdf!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),"Images to PDF",pdf.absolutePath));message="Created ${pdf.name}";ShareUtil.share(c,pdf,"application/pdf")}
        }
    }
    val pdfMulti=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.size>=2){
            val fs=uris.mapIndexedNotNull{idx,u->copyUriToCache(c,u,"merge_${System.currentTimeMillis()}_$idx.pdf")}
            val out=PdfEngine.mergePdfs(c.filesDir,fs,"Merged_Document")
            if(out!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),"Merged Document",out.absolutePath));message="Merged ${fs.size} PDFs";ShareUtil.share(c,out,"application/pdf")}else message="Select at least 2 valid PDFs"
        } else message="Select at least 2 PDFs to merge"
    }
    val onePdf=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri==null)return@rememberLauncherForActivityResult
        val f=copyUriToCache(c,uri,"pdf_tool_${System.currentTimeMillis()}.pdf") ?: return@rememberLauncherForActivityResult
        val outs=PdfEngine.splitPdf(c.filesDir,f)
        outs.forEachIndexed{idx,out->DocumentStore.add(c,DocumentRecord(System.currentTimeMillis()+idx,"${f.nameWithoutExtension} Page ${idx+1}",out.absolutePath))}
        message="Split into ${outs.size} page PDFs"
    }
    val compressPdf=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri==null)return@rememberLauncherForActivityResult
        val f=copyUriToCache(c,uri,"compress_${System.currentTimeMillis()}.pdf") ?: return@rememberLauncherForActivityResult
        val out=PdfEngine.compressPdf(c.filesDir,f,"Compressed_${f.nameWithoutExtension}")
        if(out!=null){DocumentStore.add(c,DocumentRecord(System.currentTimeMillis(),"Compressed PDF",out.absolutePath));message="Compressed PDF created";ShareUtil.share(c,out,"application/pdf")}else message="Compression failed"
    }
    val pageExport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri==null)return@rememberLauncherForActivityResult
        val f=copyUriToCache(c,uri,"export_${System.currentTimeMillis()}.pdf") ?: return@rememberLauncherForActivityResult
        val outs=PdfEngine.pdfToImages(f,c.filesDir)
        if (outs.isNotEmpty()) {
            val zip=zipFiles(c.filesDir, "${f.nameWithoutExtension}_images", outs)
            outs.forEach { it.delete() }
            if (zip != null) { message="Exported ${outs.size} pages"; ShareUtil.share(c,zip,"application/zip") }
            else message="Image export failed"
        } else message="No pages found"
    }
    Scaffold(topBar={TopAppBar(title={Text("PDF Tools")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->
        LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{Text("PDF tools",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
            item{Text("All common actions are available from this page.",style=MaterialTheme.typography.bodySmall)}
            item{Button(onClick={imagePicker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoLibrary,null);Spacer(Modifier.width(8.dp));Text("Images → PDF")}}
            item{Button(onClick={pdfMulti.launch(arrayOf("application/pdf"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Merge,null);Spacer(Modifier.width(8.dp));Text("Merge Multiple PDFs")}}
            item{Button(onClick={onePdf.launch(arrayOf("application/pdf"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ContentCut,null);Spacer(Modifier.width(8.dp));Text("Split PDF into Pages")}}
            item{Button(onClick={compressPdf.launch(arrayOf("application/pdf"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Compress,null);Spacer(Modifier.width(8.dp));Text("Compress PDF")}}
            item{OutlinedButton(onClick={pageExport.launch(arrayOf("application/pdf"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("PDF → Images")}}
            item{Card(Modifier.fillMaxWidth()){Text(message,Modifier.padding(16.dp))}}
        }
    }
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
            val out = if (excel) OfficeExporter.xlsx(context, title, results.joinToString("\n\n"))
            else OfficeExporter.docx(context, title, results.joinToString("\n\n"))
            onDone(out); return
        }
        val source = runCatching { InputImage.fromFilePath(context, Uri.fromFile(images[index])) }.getOrNull()
        if (source == null) { results += ""; next(index + 1); return }
        recognizer.process(source).addOnSuccessListener { en ->
            if (SettingsStore.hindiOcr(context)) {
                TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()).process(source)
                    .addOnSuccessListener { hi -> results += if (hi.text.isNotBlank()) hi.text else en.text; next(index + 1) }
                    .addOnFailureListener { results += en.text; next(index + 1) }
            } else { results += en.text; next(index + 1) }
        }.addOnFailureListener { results += ""; next(index + 1) }
    }
    next(0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(file:File,onBack:()->Unit){
    val c=LocalContext.current
    var pageIndex by remember{mutableIntStateOf(0)}
    var bitmap by remember{mutableStateOf<Bitmap?>(null)}
    var pageCount by remember{mutableIntStateOf(0)}
    var showRename by remember{mutableStateOf(false)}
    var newName by remember{mutableStateOf(file.nameWithoutExtension)}
    var showDelete by remember{mutableStateOf(false)}
    LaunchedEffect(file){
        runCatching{
            val pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer=PdfRenderer(pfd); pageCount=renderer.pageCount
            if(pageCount>0){val p=renderer.openPage(0);val b=Bitmap.createBitmap(p.width*2,p.height*2,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);bitmap=b;p.close()}
            renderer.close();pfd.close()
        }
    }
    fun render(index:Int){
        runCatching{
            val pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);val r=PdfRenderer(pfd);val p=r.openPage(index);val b=Bitmap.createBitmap(p.width*2,p.height*2,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);bitmap?.recycle();bitmap=b;p.close();r.close();pfd.close();pageIndex=index
        }
    }
    Scaffold(topBar={TopAppBar(title={Text(file.name)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}},actions={
        IconButton(onClick={showRename=true}){Icon(Icons.Default.Edit,null)}
        IconButton(onClick={ShareUtil.share(c,file,"application/pdf")}){Icon(Icons.Default.Share,null)}
        IconButton(onClick={showDelete=true}){Icon(Icons.Default.Delete,null)}
    })}){pad->
        Column(Modifier.padding(pad).fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
            bitmap?.let{Image(it.asImageBitmap(),"PDF page",Modifier.fillMaxWidth().weight(1f).padding(8.dp))}
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(enabled=pageIndex>0,onClick={render(pageIndex-1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text("Page ${if(pageCount==0)0 else pageIndex+1} / $pageCount",fontWeight=FontWeight.SemiBold)
                IconButton(enabled=pageIndex<pageCount-1,onClick={render(pageIndex+1)}){Icon(Icons.Default.ChevronRight,null)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=12.dp)){
                OutlinedButton(onClick={ShareUtil.share(c,file,"application/pdf")}){Icon(Icons.Default.Share,null);Spacer(Modifier.width(4.dp));Text("Share")}
                OutlinedButton(onClick={
                    val outs=PdfEngine.pdfToImages(file,c.filesDir)
                    Toast.makeText(c,"Exported ${outs.size} page(s) as images",Toast.LENGTH_SHORT).show()
                }){Icon(Icons.Default.Image,null);Spacer(Modifier.width(4.dp));Text("Pages")}
                Button(onClick={
                    val outs=PdfEngine.pdfToImages(file,c.filesDir)
                    if(outs.isNotEmpty()){
                        val source=InputImage.fromFilePath(c,Uri.fromFile(outs[pageIndex.coerceAtMost(outs.lastIndex)]))
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(source).addOnSuccessListener{Toast.makeText(c,it.text.ifBlank{"No text found"},Toast.LENGTH_LONG).show()}.addOnFailureListener{Toast.makeText(c,"OCR failed",Toast.LENGTH_SHORT).show()}
                    }
                }){Icon(Icons.Default.TextFields,null);Spacer(Modifier.width(4.dp));Text("OCR")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=12.dp)){
                Button(onClick={
                    val outs=PdfEngine.pdfToImages(file,c.filesDir)
                    exportOcrPages(c, outs, file.nameWithoutExtension + "_Editable", false) { out ->
                        if(out!=null) ShareUtil.share(c,out,"application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        outs.forEach{it.delete()}
                    }
                }){Text("Word (Editable)")}
                OutlinedButton(onClick={
                    val outs=PdfEngine.pdfToImages(file,c.filesDir)
                    exportOcrPages(c, outs, file.nameWithoutExtension + "_Editable", true) { out ->
                        if(out!=null) ShareUtil.share(c,out,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        outs.forEach{it.delete()}
                    }
                }){Text("Excel (Editable)")}
            }
        }
    }
    if(showRename) AlertDialog(
        onDismissRequest={showRename=false},
        title={Text("Rename PDF")},
        text={OutlinedTextField(newName,{newName=it},singleLine=true,label={Text("Document name")})},
        confirmButton={Button(onClick={
            val clean=newName.trim().ifBlank{file.nameWithoutExtension}
            val renamed=File(file.parentFile, "$clean.pdf")
            if(file.renameTo(renamed)) {
                DocumentStore.all(c).firstOrNull { it.path == file.absolutePath }?.let { DocumentStore.update(c, it.copy(name = clean, path = renamed.absolutePath)) }
                Toast.makeText(c,"Renamed",Toast.LENGTH_SHORT).show()
            } else Toast.makeText(c,"Rename failed",Toast.LENGTH_SHORT).show()
            showRename=false
        }){Text("Save")}},
        dismissButton={TextButton(onClick={showRename=false}){Text("Cancel")}}
    )
    if(showDelete) AlertDialog(
        onDismissRequest={showDelete=false}, title={Text("Delete PDF?")}, text={Text("This document will be removed from SmartDoc.")},
        confirmButton={Button(onClick={
            DocumentStore.all(c).firstOrNull{it.path==file.absolutePath}?.let{DocumentStore.delete(c,it)} ?: file.delete()
            showDelete=false; onBack()
        }){Text("Delete")}},
        dismissButton={TextButton(onClick={showDelete=false}){Text("Cancel")}}
    )

}
