package com.smartdocscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

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
        val qualities = if (mode == SizeMode.QUALITY) listOf(0.92f) else listOf(0.92f, 0.82f, 0.72f, 0.62f, 0.52f, 0.42f, 0.34f, 0.28f, 0.22f, 0.18f)
        for (q in qualities) {
            if (writePdf(out, images, q, paperSize) && (mode == SizeMode.QUALITY || out.length() <= maxBytes)) return out
        }
        out.delete()
        return null
    }

    fun detectPaperSize(src: Bitmap): String {
        val ratio = src.width.toFloat() / src.height.coerceAtLeast(1)
        val candidates = listOf("A4" to 595f / 842f, "A5" to 420f / 595f, "Letter" to 612f / 792f, "Legal" to 612f / 1008f)
        return candidates.minByOrNull { abs(ratio - it.second) }?.first ?: "A4"
    }

    private fun writePdf(out: File, images: List<File>, quality: Float, paperSize: String): Boolean {
        val pdf = PdfDocument()
        return try {
            images.forEachIndexed { idx, f ->
                val src = BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
                val (pageW, pageH) = pagePoints(src, paperSize)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create())
                val scale = minOf(pageW.toFloat() / src.width, pageH.toFloat() / src.height)
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
        } finally {
            pdf.close()
        }
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
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val f = File(outDir, "${pdf.nameWithoutExtension}_${i + 1}.jpg")
                FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bitmap.recycle()
                page.close()
                result += f
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return result
    }

    fun mergePdfs(outputDir: File, pdfs: List<File>, name: String = "Merged_Document"): File? {
        if (pdfs.size < 2) return null
        val images = mutableListOf<File>()
        return try {
            pdfs.forEachIndexed { i, pdf ->
                images += pdfToImages(pdf, outputDir).mapIndexed { j, f ->
                    val renamed = File(outputDir, "merge_${i}_${j}_${System.currentTimeMillis()}.jpg")
                    if (f.renameTo(renamed)) renamed else f
                }
            }
            createPdfWithLimit(outputDir, images, SizeMode.QUALITY, Long.MAX_VALUE, name, "Auto")
        } finally {
            images.forEach { it.delete() }
        }
    }

    fun splitPdf(outputDir: File, pdf: File): List<File> {
        val pages = pdfToImages(pdf, outputDir)
        val result = mutableListOf<File>()
        pages.forEachIndexed { idx, image ->
            val out = createPdfWithLimit(outputDir, listOf(image), SizeMode.QUALITY, Long.MAX_VALUE, "${safe(pdf.nameWithoutExtension)}_page_${idx + 1}", "Auto")
            if (out != null) result += out
            image.delete()
        }
        return result
    }

    fun compressPdf(outputDir: File, pdf: File, name: String = pdf.nameWithoutExtension): File? {
        val pages = pdfToImages(pdf, outputDir)
        val target = (pdf.length() * 0.70).toLong().coerceAtLeast(500L * 1024L)
        val out = createPdfWithLimit(outputDir, pages, SizeMode.MAXIMUM, target, name, "Auto")
        pages.forEach { it.delete() }
        return out
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
