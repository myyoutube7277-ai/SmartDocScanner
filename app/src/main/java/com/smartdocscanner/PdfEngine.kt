package com.smartdocscanner

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.*
import kotlin.math.abs
import kotlin.math.roundToInt

object PdfEngine {
    enum class SizeMode { QUALITY, MAXIMUM }

    fun createPdf(contextDir: File, images: List<File>, mode: SizeMode, maxMb: Int, name: String="SmartDoc"): File? =
        createPdfWithLimit(contextDir, images, mode, maxMb * 1024L * 1024L, name)

    fun createPdfAuto(
        contextDir: File,
        images: List<File>,
        mode: SizeMode,
        maxChoice: String,
        paperSize: String,
        name: String = "SmartDoc"
    ): File? {
        val maxBytes = parseSize(maxChoice)
        return createPdfWithLimit(contextDir, images, mode, maxBytes, name, paperSize)
    }

    fun createPdfWithLimit(
        contextDir: File,
        images: List<File>,
        mode: SizeMode,
        maxBytes: Long,
        name: String="SmartDoc",
        paperSize: String="Auto"
    ): File? {
        if(images.isEmpty()) return null
        val out=File(contextDir,"${safe(name)}_${System.currentTimeMillis()}.pdf")
        var factor=1.0
        val attempts = if(mode==SizeMode.QUALITY) 1 else 11
        repeat(attempts) {
            val ok=writePdf(out,images,factor,paperSize)
            if(ok && (mode==SizeMode.QUALITY || out.length() <= maxBytes)) return out
            factor*=0.78
        }
        out.delete()
        return null
    }

    private fun writePdf(out:File,images:List<File>,factor:Double,paperSize:String):Boolean {
        val pdf=PdfDocument()
        try {
            images.forEachIndexed { idx,f ->
                val src=BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
                val base = pagePoints(src,paperSize)
                val pageW=(base.first*factor).roundToInt().coerceAtLeast(220)
                val pageH=(base.second*factor).roundToInt().coerceAtLeast(220)
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(pageW,pageH,idx+1).create())
                val scale=minOf(pageW.toFloat()/src.width,pageH.toFloat()/src.height)
                val dw=src.width*scale; val dh=src.height*scale
                val left=(pageW-dw)/2f; val top=(pageH-dh)/2f
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(src,null,RectF(left,top,left+dw,top+dh),Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
                src.recycle()
            }
            FileOutputStream(out).use{pdf.writeTo(it)}
            return out.exists() && out.length() > 0
        } finally { pdf.close() }
    }

    private fun pagePoints(src: Bitmap, choice: String): Pair<Int,Int> {
        if(choice.equals("A4",true)) return 595 to 842
        if(choice.equals("A5",true)) return 420 to 595
        if(choice.equals("Letter",true)) return 612 to 792
        if(choice.equals("Legal",true)) return 612 to 1008
        val ratio = src.width.toFloat() / src.height.coerceAtLeast(1)
        val candidates = listOf(595 to 842, 420 to 595, 612 to 792, 612 to 1008)
        return candidates.minByOrNull { abs(ratio - it.first.toFloat()/it.second) } ?: (595 to 842)
    }

    fun pdfToImages(pdf:File,outDir:File):List<File> {
        val result=mutableListOf<File>()
        val pfd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)
        val r=PdfRenderer(pfd)
        for(i in 0 until r.pageCount){
            val p=r.openPage(i)
            val b=Bitmap.createBitmap(p.width*2,p.height*2,Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE); p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val f=File(outDir,"${pdf.nameWithoutExtension}_${i+1}.jpg")
            FileOutputStream(f).use{b.compress(Bitmap.CompressFormat.JPEG,92,it)}
            b.recycle(); p.close(); result+=f
        }
        r.close(); pfd.close(); return result
    }

    private fun parseSize(value:String):Long = when {
        value.startsWith("500") -> 500L*1024L
        value.startsWith("1 MB") -> 1L*1024L*1024L
        value.startsWith("2 MB") -> 2L*1024L*1024L
        value.startsWith("5 MB") -> 5L*1024L*1024L
        value.startsWith("10 MB") -> 10L*1024L*1024L
        value.startsWith("20 MB") -> 20L*1024L*1024L
        value.startsWith("50 MB") -> 50L*1024L*1024L
        else -> 5L*1024L*1024L
    }

    private fun safe(s:String)=s.replace(Regex("[^A-Za-z0-9._-]"),"_").take(50).ifBlank{"export"}
}
