package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fast, conservative document processing. All heavy work is expected off the UI thread. */
object ScanProcessor {
    data class Quad(val p: Array<PointF>, val width: Int, val height: Int)

    fun decode(file: File): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val maxSide = 2200
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > maxSide) sample *= 2
        val actual = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, actual)
    }

    fun autoCrop(src: Bitmap): Bitmap {
        if (src.width < 80 || src.height < 80) return src.copy(Bitmap.Config.ARGB_8888, false)
        val q = detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val tl = q.p[0]; val tr = q.p[1]; val br = q.p[2]; val bl = q.p[3]
        val w = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(2)
        val h = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(2)
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        val srcPts = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPts = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            dst.recycle()
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        Canvas(dst).drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    private fun detectQuad(src: Bitmap): Quad? {
        val maxSide = 720
        val scale = min(1f, maxSide.toFloat() / max(src.width, src.height).toFloat())
        val w = max(40, (src.width * scale).toInt())
        val h = max(40, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            fun gray(x: Int, y: Int): Int {
                val c = pixels[y * w + x]
                return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
            }
            fun vScore(x: Int): Float {
                if (x < 2 || x >= w - 2) return 0f
                var strong = 0; var sum = 0
                for (y in 6 until h - 6 step 3) {
                    val g = abs(gray(x, y) - gray(x - 1, y)); sum += g
                    if (g >= 16) strong++
                }
                return strong * 3f + sum.toFloat() / max(1, (h - 12) / 3)
            }
            fun hScore(y: Int): Float {
                if (y < 2 || y >= h - 2) return 0f
                var strong = 0; var sum = 0
                for (x in 6 until w - 6 step 3) {
                    val g = abs(gray(x, y) - gray(x, y - 1)); sum += g
                    if (g >= 16) strong++
                }
                return strong * 3f + sum.toFloat() / max(1, (w - 12) / 3)
            }
            fun bestV(a: Int, b: Int): Pair<Int, Float> {
                var bx = a; var bs = 0f
                for (x in a..b) { val s = vScore(x); if (s > bs) { bx = x; bs = s } }
                return bx to bs
            }
            fun bestH(a: Int, b: Int): Pair<Int, Float> {
                var by = a; var bs = 0f
                for (y in a..b) { val s = hScore(y); if (s > bs) { by = y; bs = s } }
                return by to bs
            }
            val (lx, ls) = bestV((w * .03f).toInt(), (w * .47f).toInt())
            val (rx, rs) = bestV((w * .53f).toInt(), (w * .97f).toInt())
            val (ty, ts) = bestH((h * .03f).toInt(), (h * .47f).toInt())
            val (by, bs) = bestH((h * .53f).toInt(), (h * .97f).toInt())
            val reliable = ls >= 28f && rs >= 28f && ts >= 28f && bs >= 28f && rx - lx >= w * .30f && by - ty >= h * .30f
            if (!reliable) return null
            return Quad(arrayOf(PointF(lx / scale, ty / scale), PointF(rx / scale, ty / scale), PointF(rx / scale, by / scale), PointF(lx / scale, by / scale)), rx - lx, by - ty)
        } finally { small.recycle() }
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun rotate(src: Bitmap): Bitmap = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)

    fun filter(src: Bitmap, mode: String): Bitmap {
        if (mode == "Color" || mode == "Original") return src.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width; val h = src.height
        val input = IntArray(w * h); val output = IntArray(w * h); val lum = IntArray(w * h); val hist = IntArray(256)
        src.getPixels(input, 0, w, 0, 0, w, h)
        for (i in input.indices) { val c = input[i]; val v = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt().coerceIn(0,255); lum[i]=v; hist[v]++ }
        val global=otsu(hist,lum.size)
        for(y in 0 until h) for(x in 0 until w){ val i=y*w+x; var v=lum[i]; when(mode){"B&W"->v=if(localThreshold(lum,w,h,x,y,global)>=v)0 else 255;"High Contrast","Clean White","Enhance"->v=(((v-128)*1.28f)+128).toInt().coerceIn(0,255)};output[i]=Color.rgb(v,v,v)}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{it.setPixels(output,0,w,0,0,w,h)}
    }

    private fun localThreshold(l:IntArray,w:Int,h:Int,x:Int,y:Int,global:Int):Int{var sum=0;var n=0;val r=12;var yy=max(0,y-r);while(yy<=min(h-1,y+r)){var xx=max(0,x-r);while(xx<=min(w-1,x+r)){sum+=l[yy*w+xx];n++;xx+=3};yy+=3};return((sum/max(1,n))*.92f).toInt().coerceIn(global-45,global+45)}
    private fun otsu(hist:IntArray,total:Int):Int{var sum=0.0;for(i in 0..255)sum+=i.toDouble()*hist[i];var sumB=0.0;var wB=0;var best=0.0;var threshold=128;for(t in 0..255){wB+=hist[t];if(wB==0)continue;val wF=total-wB;if(wF==0)break;sumB+=t.toDouble()*hist[t];val mB=sumB/wB;val mF=(sum-sumB)/wF;val between=wB.toDouble()*wF*(mB-mF)*(mB-mF);if(between>best){best=between;threshold=t}};return threshold.coerceIn(45,210)}
}
