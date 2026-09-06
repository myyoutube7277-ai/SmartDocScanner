package com.smartdocscanner

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.*
import kotlin.math.roundToInt

object PdfEngine {
    enum class SizeMode { QUALITY, MAXIMUM }

    fun createPdf(contextDir: File, images: List<File>, mode: SizeMode, maxMb: Int, name: String="SmartDoc"): File? {
        if(images.isEmpty()) return null
        val out=File(contextDir,"${name}_${System.currentTimeMillis()}.pdf")
        var factor=if(mode==SizeMode.QUALITY) 1.0 else 1.0
        repeat(if(mode==SizeMode.QUALITY) 1 else 8) {
            val ok=writePdf(out,images,factor)
            if(ok && (mode==SizeMode.QUALITY || out.length() <= maxMb*1024L*1024L)) return out
            factor*=0.78
        }
        out.delete()
        return null
    }

    private fun writePdf(out:File, images:List<File>, factor:Double):Boolean {
        val pdf=PdfDocument()
        try {
            images.forEachIndexed { idx,f ->
                val src=BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
                val baseW=1240; val baseH=1754
                val pageW=(baseW*factor).roundToInt().coerceAtLeast(300)
                val pageH=(baseH*factor).roundToInt().coerceAtLeast(300)
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(pageW,pageH,idx+1).create())
                val scale=minOf(pageW.toFloat()/src.width,pageH.toFloat()/src.height)
                val dw=src.width*scale; val dh=src.height*scale
                val left=(pageW-dw)/2f; val top=(pageH-dh)/2f
                page.canvas.drawBitmap(src,null,RectF(left,top,left+dw,top+dh),Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
                src.recycle()
            }
            FileOutputStream(out).use{pdf.writeTo(it)}
            return out.exists()
        } finally { pdf.close() }
    }

    fun pdfToImages(pdf:File, outDir:File):List<File> {
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
        r.close(); pfd.close()
        return result
    }
}
