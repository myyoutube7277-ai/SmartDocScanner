package com.smartdocscanner
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

import android.Manifest
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDocApp()
        }
    }
}


@Composable
fun SmartDocApp() {

    var screen by remember { mutableStateOf("home") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    when (screen) {

        "home" -> HomeScreen(
            onScan = { screen = "scan" },
            onOcr = { screen = "ocr" },
            onConvert = { screen = "convert" },
            onBarcode = { screen = "barcode" },
            onPdf = { screen = "pdf" },
            refresh = refresh,
            onOpen = {
                selectedFile = it
                screen = "viewer"
            }
        )

        "scan" -> ScannerScreen(
            onBack = { screen = "home" },
            onSaved = {
                refresh++
                screen = "home"
            }
        )

        "ocr" -> OcrScreen(
            onBack = { screen = "home" }
        )

        "convert" -> ConvertScreen(
            onBack = { screen = "home" }
        )

        "barcode" -> BarcodeScreen(
            onBack = { screen = "home" }
        )

        "pdf" -> PdfToolsScreen(
            onBack = { screen = "home" }
        )

        "viewer" -> {
            selectedFile?.let {
                ViewerScreen(
                    file = it,
                    onBack = { screen = "home" }
                )
            }
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
    refresh: Int,
    onOpen: (File) -> Unit
) {

    val c = LocalContext.current

    var query by remember {
        mutableStateOf("")
    }

    val docs = remember(refresh, query) {
        DocumentStore
            .all(c)
            .filter {
                it.name.contains(query, true)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("SmartDoc Scanner")
                }
            )
        }
    ) { pad ->

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Search documents")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )
            }

            item {

                Card {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            "Create",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            FeatureButton(
                                text = "Scan",
                                icon = Icons.Default.CameraAlt,
                                onClick = onScan
                            )

                            FeatureButton(
                                text = "OCR",
                                icon = Icons.Default.TextFields,
                                onClick = onOcr
                            )

                            FeatureButton(
                                text = "QR",
                                icon = Icons.Default.QrCode,
                                onClick = onBarcode
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            FeatureButton(
                                text = "Convert",
                                icon = Icons.Default.Description,
                                onClick = onConvert
                            )

                            FeatureButton(
                                text = "PDF",
                                icon = Icons.Default.PictureAsPdf,
                                onClick = onPdf
                            )
                        }
                    }
                }
            }

            item {

                Text(
                    "Documents",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (docs.isEmpty()) {

                item {
                    Text(
                        "No documents yet. Tap Scan to create one."
                    )
                }
            }

            items(docs) { r ->

                Card {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(12.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(r.name)

                            Text(
                                r.folder.ifBlank {
                                    "Root"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = {
                                onOpen(File(r.path))
                            }
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null
                            )
                        }

                        IconButton(
                            onClick = {
                                DocumentStore.delete(c, r)
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun RowScope.FeatureButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {

        Icon(
            icon,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.width(4.dp)
        )

        Text(text)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {

    val c = LocalContext.current

    var captured by remember {
        mutableStateOf<File?>(null)
    }

    var mode by remember {
        mutableStateOf(PdfEngine.SizeMode.QUALITY)
    }

    var maxMb by remember {
        mutableIntStateOf(2)
    }

    var pages by remember {
        mutableStateOf(listOf<File>())
    }

    if (captured != null) {

        ScanEditor(
            file = captured!!,

            onBack = {
                captured = null
            },

            onAdd = { f ->
                pages = pages + f
                captured = null
            },

            onFinish = {

                val pdf = PdfEngine.createPdf(
                    c.filesDir,
                    pages,
                    mode,
                    maxMb,
                    "SmartDoc"
                )

                if (pdf != null) {

                    DocumentStore.add(
                        c,
                        DocumentRecord(
                            System.currentTimeMillis(),
                            "Scanned Document",
                            pdf.absolutePath
                        )
                    )

                    Toast.makeText(
                        c,
                        "PDF saved",
                        Toast.LENGTH_SHORT
                    ).show()

                    onSaved()

                } else {

                    Toast.makeText(
                        c,
                        "Maximum size target could not be met",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            pages = pages.size
        )

        return
    }

    val permission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (!granted) {

                Toast.makeText(
                    c,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    LaunchedEffect(Unit) {
        permission.launch(
            Manifest.permission.CAMERA
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Document Scanner")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {

            CameraCapture(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onCaptured = {
                    captured = it
                }
            )

            Text(
                "PDF output size",
                modifier = Modifier.padding(
                    horizontal = 16.dp
                )
            )

            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(
                    selected = mode == PdfEngine.SizeMode.QUALITY,
                    onClick = {
                        mode = PdfEngine.SizeMode.QUALITY
                    },
                    label = {
                        Text("Quality Based")
                    }
                )

                FilterChip(
                    selected = mode == PdfEngine.SizeMode.MAXIMUM,
                    onClick = {
                        mode = PdfEngine.SizeMode.MAXIMUM
                    },
                    label = {
                        Text("Maximum Size")
                    }
                )
            }

            if (mode == PdfEngine.SizeMode.MAXIMUM) {

                OutlinedTextField(
                    value = maxMb.toString(),
                    onValueChange = {
                        maxMb = (
                            it.toIntOrNull() ?: 2
                        ).coerceAtLeast(2)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    label = {
                        Text("Maximum MB (minimum 2)")
                    }
                )
            }

            Text(
                "Pages queued: ${pages.size}",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}


@Composable
fun CameraCapture(
    modifier: Modifier,
    onCaptured: (File) -> Unit
) {

    val c = LocalContext.current

    val previewView = remember {
        PreviewView(c)
    }

    val executor = remember {
        Executors.newSingleThreadExecutor()
    }

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    LaunchedEffect(Unit) {

        val provider =
            ProcessCameraProvider
                .getInstance(c)
                .get()

        val preview =
            Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider =
                        previewView.surfaceProvider
                }

        val cap =
            ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .build()

        imageCapture = cap

        provider.unbindAll()

        provider.bindToLifecycle(
            c as ComponentActivity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            cap
        )
    }

    Box(modifier) {

        AndroidView(
            factory = {
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {

                val f = File(
                    c.cacheDir,
                    "scan_${System.currentTimeMillis()}.jpg"
                )

                val opts =
                    ImageCapture.OutputFileOptions
                        .Builder(f)
                        .build()

                imageCapture?.takePicture(
                    opts,
                    executor,
                    object :
                        ImageCapture.OnImageSavedCallback {

                        override fun onError(
                            e: ImageCaptureException
                        ) {

                            Toast.makeText(
                                c,
                                e.message ?: "Capture failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onImageSaved(
                            r: ImageCapture.OutputFileResults
                        ) {

                            onCaptured(f)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {

            Icon(
                Icons.Default.Camera,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text("Capture")
        }
    }
}


@Composable
fun ScanEditor(
    file: File,
    onBack: () -> Unit,
    onAdd: (File) -> Unit,
    onFinish: () -> Unit,
    pages: Int
) {

    val c = LocalContext.current

    var bmp by remember(file) {
        mutableStateOf(
            ScanProcessor
                .decode(file)
                ?.let {
                    ScanProcessor.autoCrop(it)
                }
        )
    }

    var filter by remember {
        mutableStateOf("Color")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Scan")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            bmp?.let {

                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                listOf(
                    "Color",
                    "Gray",
                    "B&W",
                    "High Contrast"
                ).forEach {

                    FilterChip(
                        selected = filter == it,
                        onClick = {

                            filter = it

                            bmp = bmp?.let { x ->
                                ScanProcessor.filter(
                                    x,
                                    it
                                )
                            }
                        },
                        label = {
                            Text(it)
                        }
                    )
                }
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {

                OutlinedButton(
                    onClick = {
                        bmp = bmp?.let {
                            ScanProcessor.rotate(it)
                        }
                    }
                ) {
                    Text("Rotate 90°")
                }

                Button(
                    onClick = {

                        val out = File(
                            c.cacheDir,
                            "page_${System.currentTimeMillis()}.jpg"
                        )

                        FileOutputStream(out).use {

                            bmp?.compress(
                                Bitmap.CompressFormat.JPEG,
                                92,
                                it
                            )
                        }

                        onAdd(out)
                    }
                ) {
                    Text("Add Page")
                }

                if (pages > 0) {

                    Button(
                        onClick = onFinish
                    ) {
                        Text("Finish PDF ($pages)")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onBack: () -> Unit
) {

    val c = LocalContext.current

    var result by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            loading = true

            runCatching {

                val source =
                    InputImage.fromFilePath(c, uri)

                val a =
                    TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                    )

                val d =
                    TextRecognition.getClient(
                        DevanagariTextRecognizerOptions
                            .Builder()
                            .build()
                    )

                a.process(source)
                    .addOnSuccessListener { en ->

                        d.process(source)
                            .addOnSuccessListener { hi ->

                                result =
                                    if (hi.text.isNotBlank()) {
                                        hi.text
                                    } else {
                                        en.text
                                    }

                                loading = false
                            }
                            .addOnFailureListener {

                                result = en.text
                                loading = false
                            }
                    }
                    .addOnFailureListener {

                        loading = false

                        Toast.makeText(
                            c,
                            "OCR failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }.onFailure {

                loading = false
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("OCR Reader")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Button(
                onClick = {
                    picker.launch(
                        arrayOf("image/*")
                    )
                }
            ) {
                Text("Select Image")
            }

            if (loading) {
                CircularProgressIndicator()
            }

            OutlinedTextField(
                value = result,
                onValueChange = {
                    result = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = {
                    Text("Editable OCR text")
                }
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {

                        val clipboard =
                            c.getSystemService(
                                android.content.ClipboardManager::class.java
                            )

                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "OCR",
                                result
                            )
                        )
                    }
                ) {
                    Text("Copy")
                }

                Button(
                    onClick = {

                        val f =
                            OfficeExporter.docx(
                                c,
                                "OCR_Document",
                                result
                            )

                        ShareUtil.share(
                            c,
                            f,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    }
                ) {
                    Text("Word")
                }

                Button(
                    onClick = {

                        val f =
                            OfficeExporter.xlsx(
                                c,
                                "OCR_Sheet",
                                result
                            )

                        ShareUtil.share(
                            c,
                            f,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    }
                ) {
                    Text("Excel")
                }
            }
        }
    }
}


@Composable
fun ConvertScreen(
    onBack: () -> Unit
) {

    val c = LocalContext.current

    var text by remember {
        mutableStateOf("")
    }

    var chosen by remember {
        mutableStateOf("")
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            chosen = uri.toString()

            val source =
                InputImage.fromFilePath(c, uri)

            TextRecognition
                .getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS
                )
                .process(source)
                .addOnSuccessListener { resultText ->

                    text = resultText.text
                }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Image → Editable")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Button(
                onClick = {
                    picker.launch(
                        arrayOf("image/*")
                    )
                }
            ) {
                Text("Choose Photo")
            }

            Text(
                if (chosen.isBlank()) {
                    "No photo selected"
                } else {
                    "Photo selected"
                }
            )

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = {
                    Text("OCR + layout text")
                }
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {

                        ShareUtil.share(
                            c,
                            OfficeExporter.docx(
                                c,
                                "Editable_Document",
                                text
                            ),
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    }
                ) {
                    Text("DOCX")
                }

                Button(
                    onClick = {

                        ShareUtil.share(
                            c,
                            OfficeExporter.xlsx(
                                c,
                                "Editable_Sheet",
                                text
                            ),
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    }
                ) {
                    Text("XLSX")
                }
            }

            Text(
                "Note: complex tables, fonts and exact page geometry are reconstructed approximately; 100% pixel-identical Word/Excel conversion is not guaranteed."
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    onBack: () -> Unit
) {

    val c = LocalContext.current

    var result by remember {
        mutableStateOf(
            "Point camera at a QR/barcode"
        )
    }

    val preview = remember {
        PreviewView(c)
    }

    LaunchedEffect(Unit) {

        val provider =
            ProcessCameraProvider
                .getInstance(c)
                .get()

        val p =
            Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider =
                        preview.surfaceProvider
                }

        val analysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

        val scanner =
            BarcodeScanning.getClient()

        analysis.setAnalyzer(
            Executors.newSingleThreadExecutor()
        ) { proxy ->

            val media = proxy.image

            if (media != null) {

                scanner.process(
                    InputImage.fromMediaImage(
                        media,
                        proxy.imageInfo.rotationDegrees
                    )
                )
                    .addOnSuccessListener { codes ->

                        if (codes.isNotEmpty()) {

                            result =
                                codes.joinToString("\n") {
                                    it.rawValue
                                        ?: "(no value)"
                                }
                        }
                    }
                    .addOnCompleteListener {
                        proxy.close()
                    }

            } else {

                proxy.close()
            }
        }

        provider.unbindAll()

        provider.bindToLifecycle(
            c as ComponentActivity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            p,
            analysis
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("QR / Barcode")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier.padding(pad)
        ) {

            AndroidView(
                factory = {
                    preview
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Text(
                result,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit
) {

    val c = LocalContext.current

    var message by remember {
        mutableStateOf("")
    }

    val images =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNotEmpty()) {

                val fs =
                    uris.mapIndexedNotNull { idx, u ->

                        runCatching {

                            val f =
                                File(
                                    c.cacheDir,
                                    "img_${System.currentTimeMillis()}_$idx.jpg"
                                )

                            c.contentResolver
                                .openInputStream(u)
                                ?.use { ins ->
                                    f.outputStream()
                                        .use { out ->
                                            ins.copyTo(out)
                                        }
                                }

                            f

                        }.getOrNull()
                    }

                val pdf =
                    PdfEngine.createPdf(
                        c.filesDir,
                        fs,
                        PdfEngine.SizeMode.QUALITY,
                        2,
                        "ImagesToPDF"
                    )

                if (pdf != null) {

                    message =
                        "Created: ${pdf.name}"

                    ShareUtil.share(
                        c,
                        pdf,
                        "application/pdf"
                    )
                }
            }
        }

    val pdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            val f =
                File(
                    c.cacheDir,
                    "source_${System.currentTimeMillis()}.pdf"
                )

            c.contentResolver
                .openInputStream(uri)
                ?.use { ins ->
                    f.outputStream()
                        .use { out ->
                            ins.copyTo(out)
                        }
                }

            val outs =
                PdfEngine.pdfToImages(
                    f,
                    c.filesDir
                )

            message =
                "Exported ${outs.size} pages"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("PDF Tools")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Button(
                onClick = {
                    images.launch(
                        arrayOf("image/*")
                    )
                }
            ) {
                Text("Images → PDF")
            }

            Button(
                onClick = {
                    pdfPicker.launch(
                        arrayOf("application/pdf")
                    )
                }
            ) {
                Text("PDF → Images")
            }

            Text(message)

            Text(
                "PDF merge/split/password/compression hooks can be added with the included PDFBox dependency; the core scanner/PDF/OCR modules are ready."
            )
        }
    }
}


@Composable
fun ViewerScreen(
    file: File,
    onBack: () -> Unit
) {

    val c = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(file.name)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                "Saved PDF: ${file.absolutePath}"
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    ShareUtil.share(
                        c,
                        file,
                        "application/pdf"
                    )
                }
            ) {
                Text("Share PDF")
            }

            Button(
                onClick = {

                    c.startActivity(
                        Intent(
                            Intent.ACTION_VIEW
                        ).apply {

                            setDataAndType(
                                androidx.core.content.FileProvider
                                    .getUriForFile(
                                        c,
                                        "${c.packageName}.provider",
                                        file
                                    ),
                                "application/pdf"
                            )

                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    )
                }
            ) {
                Text("Open / Print")
            }
        }
    }
}
