package com.smartdocscanner

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.*
import kotlin.math.abs
import kotlin.math.roundToInt
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument

object PdfEngine {
    enum class SizeMode { QUALITY, MAXIMUM }

    val sizeChoices = listOf("500 KB", "1 MB", "2 MB", "5 MB", "10 MB", "20 MB", "50 MB")
    val paperChoices = listOf("Auto", "A4", "A5", "Letter", "Legal")

    fun createPdf(contextDir: File, images: List<File>, mode: SizeMode, maxMb: Int, name: String = "SmartDoc"): File? =
        createPdfWithLimit(contextDir, images, mode, maxMb.coerceAtLeast(1) * 1024L * 1024L, name, "Auto")

    fun createPdfAuto(contextDir: File, images: List<File>, mode: SizeMode, maxChoice: String, paperSize: String, name: String = "SmartDoc"): File? =
        createPdfWithLimit(contextDir, images, mode, parseSize(maxChoice), name, paperSize)

    fun createPdfWithLimit(contextDir: File, images: List<File>, mode: SizeMode, maxBytes: Long, name: String = "SmartDoc", paperSize: String = "Auto"): File? {
        if (images.isEmpty()) return null
        val out = File(contextDir, "${safe(name)}_${System.currentTimeMillis()}.pdf")
        val factors = if (mode == SizeMode.QUALITY) listOf(1.0) else listOf(1.0, .82, .68, .56, .46, .38, .31, .25, .20, .16, .13, .10, .08)
        for (factor in factors) {
            if (writePdf(out, images, factor, paperSize) && (mode == SizeMode.QUALITY || out.length() <= maxBytes)) return out
        }
        out.delete()
        return null
    }

    fun detectPaperSize(src: Bitmap): String {
        val ratio = src.width.toFloat() / src.height.coerceAtLeast(1)
        val candidates = listOf("A4" to 595f / 842f, "A5" to 420f / 595f, "Letter" to 612f / 792f, "Legal" to 612f / 1008f)
        return candidates.minByOrNull { abs(ratio - it.second) }?.first ?: "A4"
    }

    private fun writePdf(out: File, images: List<File>, factor: Double, paperSize: String): Boolean {
        val pdf = PdfDocument()
        return try {
            images.forEachIndexed { idx, f ->
                val src = BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
                val base = pagePoints(src, paperSize)
                val pageW = base.first
                val pageH = base.second
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create())
                val scale = minOf(pageW.toFloat() / src.width, pageH.toFloat() / src.height) * factor
                val dw = src.width * scale
                val dh = src.height * scale
                val left = (pageW - dw) / 2f
                val top = (pageH - dh) / 2f
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                pdf.finishPage(page)
                src.recycle()
            }
            FileOutputStream(out).use { pdf.writeTo(it) }
            out.exists() && out.length() > 0
        } catch (_: Exception) {
            false
        } finally { pdf.close() }
    }

    private fun pagePoints(src: Bitmap, choice: String): Pair<Int, Int> = when {
        choice.equals("A4", true) -> 595 to 842
        choice.equals("A5", true) -> 420 to 595
        choice.equals("Letter", true) -> 612 to 792
        choice.equals("Legal", true) -> 612 to 1008
        else -> when (detectPaperSize(src)) {
            "A5" -> 420 to 595
            "Letter" -> 612 to 792
            "Legal" -> 612 to 1008
            else -> 595 to 842
        }
    }

    fun pdfToImages(pdf: File, outDir: File): List<File> {
        val result = mutableListOf<File>()
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        try {
            for (i in 0 until r.pageCount) {
                val p = r.openPage(i)
                val b = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val f = File(outDir, "${pdf.nameWithoutExtension}_${i + 1}.jpg")
                FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                b.recycle(); p.close(); result += f
            }
        } finally { r.close(); pfd.close() }
        return result
    }

    fun mergePdfs(outputDir: File, pdfs: List<File>, name: String = "Merged_Document"): File? {
        if (pdfs.size < 2) return null
        val out = File(outputDir, "${safe(name)}_${System.currentTimeMillis()}.pdf")
        return try {
            val merger = PDFMergerUtility()
            pdfs.forEach(merger::addSource)
            merger.destinationFileName = out.absolutePath
            merger.mergeDocuments(null)
            if (out.exists() && out.length() > 0) out else null
        } catch (_: Exception) { out.delete(); null }
    }

    fun splitPdf(outputDir: File, pdf: File): List<File> {
        val result = mutableListOf<File>()
        return try {
            PDDocument.load(pdf).use { source ->
                for (i in 0 until source.numberOfPages) {
                    PDDocument().use { one ->
                        one.addPage(source.getPage(i))
                        val out = File(outputDir, "${safe(pdf.nameWithoutExtension)}_page_${i + 1}.pdf")
                        one.save(out); result += out
                    }
                }
            }
            result
        } catch (_: Exception) { result }
    }

    fun compressPdf(outputDir: File, pdf: File, name: String = pdf.nameWithoutExtension): File? {
        val pages = pdfToImages(pdf, outputDir)
        val target = (pdf.length() * 0.70).toLong().coerceAtLeast(500L * 1024L)
        return createPdfWithLimit(outputDir, pages, SizeMode.MAXIMUM, target, name, "Auto")
    }

    private fun parseSize(value: String): Long = when {
        value.startsWith("500") -> 500L * 1024L
        value.startsWith("1 MB") -> 1L * 1024L * 1024L
        value.startsWith("2 MB") -> 2L * 1024L * 1024L
        value.startsWith("5 MB") -> 5L * 1024L * 1024L
        value.startsWith("10 MB") -> 10L * 1024L * 1024L
        value.startsWith("20 MB") -> 20L * 1024L * 1024L
        value.startsWith("50 MB") -> 50L * 1024L * 1024L
        else -> 5L * 1024L * 1024L
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50).ifBlank { "export" }
}
