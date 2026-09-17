package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

/** Background-safe document processing. Auto crop uses long edge scoring so it is not dependent on four perfect lines. */
object ScanProcessor {
    data class Quad(val p: Array<PointF>, val width: Int, val height: Int)

    fun decode(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    fun autoCrop(src: Bitmap): Bitmap {
        if (src.width < 40 || src.height < 40) return src.copy(Bitmap.Config.ARGB_8888, false)
        val q = detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val tl = q.p[0]; val tr = q.p[1]; val br = q.p[2]; val bl = q.p[3]
        val w = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(2)
        val h = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(2)
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        val sp = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dp = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        if (!matrix.setPolyToPoly(sp, 0, dp, 0, 4)) {
            dst.recycle()
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        Canvas(dst).drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    private fun detectQuad(src: Bitmap): Quad? {
        val maxSide = 720
        val scale = min(1f, maxSide.toFloat() / max(src.width, src.height).toFloat())
        val w = (src.width * scale).roundToInt().coerceAtLeast(40)
        val h = (src.height * scale).roundToInt().coerceAtLeast(40)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            fun gray(x: Int, y: Int): Int {
                val c = pixels[y * w + x]
                return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).roundToInt()
            }
            fun verticalScore(x: Int): Float {
                if (x <= 1 || x >= w - 1) return 0f
                var strong = 0; var sum = 0
                for (y in 8 until h - 8 step 2) {
                    val g = abs(gray(x, y) - gray(x - 1, y))
                    sum += g
                    if (g >= 18) strong++
                }
                return strong * 3.0f + sum.toFloat() / max(1, (h - 16) / 2)
            }
            fun horizontalScore(y: Int): Float {
                if (y <= 1 || y >= h - 1) return 0f
                var strong = 0; var sum = 0
                for (x in 8 until w - 8 step 2) {
                    val g = abs(gray(x, y) - gray(x, y - 1))
                    sum += g
                    if (g >= 18) strong++
                }
                return strong * 3.0f + sum.toFloat() / max(1, (w - 16) / 2)
            }
            fun bestVertical(from: Int, to: Int): Pair<Int, Float> {
                var bx = from; var bs = 0f
                for (x in from..to) {
                    val s = verticalScore(x)
                    if (s > bs) { bs = s; bx = x }
                }
                return bx to bs
            }
            fun bestHorizontal(from: Int, to: Int): Pair<Int, Float> {
                var by = from; var bs = 0f
                for (y in from..to) {
                    val s = horizontalScore(y)
                    if (s > bs) { bs = s; by = y }
                }
                return by to bs
            }

            val leftRange = (w * 0.04f).roundToInt()..(w * 0.46f).roundToInt()
            val rightRange = (w * 0.54f).roundToInt()..(w * 0.96f).roundToInt()
            val topRange = (h * 0.04f).roundToInt()..(h * 0.46f).roundToInt()
            val bottomRange = (h * 0.54f).roundToInt()..(h * 0.96f).roundToInt()
            val (lx, ls) = bestVertical(leftRange.first, leftRange.last)
            val (rx, rs) = bestVertical(rightRange.first, rightRange.last)
            val (ty, ts) = bestHorizontal(topRange.first, topRange.last)
            val (by, bs) = bestHorizontal(bottomRange.first, bottomRange.last)

            val minWidth = w * 0.30f
            val minHeight = h * 0.30f
            val reliable = ls >= 32f && rs >= 32f && ts >= 32f && bs >= 32f &&
                rx - lx >= minWidth && by - ty >= minHeight
            if (!reliable) return null

            val tl = PointF(lx.toFloat(), ty.toFloat())
            val tr = PointF(rx.toFloat(), ty.toFloat())
            val br = PointF(rx.toFloat(), by.toFloat())
            val bl = PointF(lx.toFloat(), by.toFloat())
            return Quad(
                arrayOf(
                    PointF(tl.x / scale, tl.y / scale),
                    PointF(tr.x / scale, tr.y / scale),
                    PointF(br.x / scale, br.y / scale),
                    PointF(bl.x / scale, bl.y / scale)
                ),
                (rx - lx).roundToInt(),
                (by - ty).roundToInt()
            )
        } finally {
            small.recycle()
        }
    }

    private fun distance(a: PointF, b: PointF): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun rotate(src: Bitmap): Bitmap = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)

    fun filter(src: Bitmap, mode: String): Bitmap {
        if (mode == "Color" || mode == "Original") return src.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width; val h = src.height
        val input = IntArray(w * h); val output = IntArray(w * h); val lum = IntArray(w * h); val hist = IntArray(256)
        src.getPixels(input, 0, w, 0, 0, w, h)
        for (i in input.indices) {
            val c = input[i]
            val v = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).roundToInt().coerceIn(0, 255)
            lum[i] = v; hist[v]++
        }
        val global = otsu(hist, lum.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var v = lum[i]
            when (mode) {
                "B&W" -> v = if (localThreshold(lum, w, h, x, y, global) >= v) 0 else 255
                "High Contrast", "Clean White", "Enhance" -> v = (((v - 128) * 1.28f) + 128).roundToInt().coerceIn(0, 255)
            }
            output[i] = Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, w, 0, 0, w, h) }
    }

    private fun localThreshold(l: IntArray, w: Int, h: Int, x: Int, y: Int, global: Int): Int {
        var sum = 0; var n = 0; val r = 12
        for (yy in max(0, y - r)..min(h - 1, y + r) step 3) for (xx in max(0, x - r)..min(w - 1, x + r) step 3) { sum += l[yy * w + xx]; n++ }
        return ((sum / max(1, n)) * 0.92f).roundToInt().coerceIn(global - 45, global + 45)
    }

    private fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sb = 0.0; var wb = 0; var best = 0.0; var threshold = 128
        for (t in 0..255) {
            wb += hist[t]
            if (wb == 0) continue
            val wf = total - wb
            if (wf == 0) break
            sb += t.toDouble() * hist[t]
            val mb = sb / wb; val mf = (sum - sb) / wf
            val variance = wb.toDouble() * wf * (mb - mf) * (mb - mf)
            if (variance > best) { best = variance; threshold = t }
        }
        return threshold.coerceIn(45, 210)
    }
}
