package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

/** Lightweight document processing. Detection uses a down-scaled copy to avoid large bitmap work on the UI thread. */
object ScanProcessor {
    data class Quad(val p: Array<PointF>, val width: Int, val height: Int)

    fun decode(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    fun autoCrop(src: Bitmap): Bitmap {
        val q = detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val tl = q.p[0]; val tr = q.p[1]; val br = q.p[2]; val bl = q.p[3]
        val w = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(1)
        val h = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(1)
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        val sp = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dp = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        if (!matrix.setPolyToPoly(sp, 0, dp, 0, 4)) {
            dst.recycle(); return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        Canvas(dst).drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    private fun detectQuad(src: Bitmap): Quad? {
        val maxSide = 900
        val scale = min(1f, maxSide.toFloat() / max(src.width, src.height).toFloat())
        val w = (src.width * scale).roundToInt().coerceAtLeast(40)
        val h = (src.height * scale).roundToInt().coerceAtLeast(40)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            fun gray(x: Int, y: Int): Float {
                val c = pixels[y * w + x]
                return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
            }
            fun fit(points: List<PointF>): Pair<Float, Float>? {
                if (points.size < 8) return null
                var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
                points.forEach { sx += it.x; sy += it.y; sxx += it.x * it.x; sxy += it.x * it.y }
                val n = points.size.toDouble()
                val d = n * sxx - sx * sx
                if (abs(d) < 1e-6) return null
                val a = ((n * sxy - sx * sy) / d).toFloat()
                val b = ((sy - a * sx) / n).toFloat()
                return a to b
            }
            val top = mutableListOf<PointF>(); val bottom = mutableListOf<PointF>()
            val left = mutableListOf<PointF>(); val right = mutableListOf<PointF>()
            val sxStep = max(3, w / 100); val syStep = max(3, h / 100)
            for (x in 8 until w - 8 step sxStep) {
                var topBest = 0f; var topY = 1
                for (y in 3 until (h / 2).coerceAtLeast(4)) {
                    val g = abs(gray(x, y) - gray(x, y - 1))
                    if (g > topBest) { topBest = g; topY = y }
                }
                var bottomBest = 0f; var bottomY = h - 2
                for (y in (h / 2).coerceAtLeast(3) until h - 2) {
                    val g = abs(gray(x, y) - gray(x, y - 1))
                    if (g > bottomBest) { bottomBest = g; bottomY = y }
                }
                if (topBest > 14f) top += PointF(x.toFloat(), topY.toFloat())
                if (bottomBest > 14f) bottom += PointF(x.toFloat(), bottomY.toFloat())
            }
            for (y in 8 until h - 8 step syStep) {
                var leftBest = 0f; var leftX = 1
                for (x in 3 until (w / 2).coerceAtLeast(4)) {
                    val g = abs(gray(x, y) - gray(x - 1, y))
                    if (g > leftBest) { leftBest = g; leftX = x }
                }
                var rightBest = 0f; var rightX = w - 2
                for (x in (w / 2).coerceAtLeast(3) until w - 2) {
                    val g = abs(gray(x, y) - gray(x - 1, y))
                    if (g > rightBest) { rightBest = g; rightX = x }
                }
                if (leftBest > 14f) left += PointF(leftX.toFloat(), y.toFloat())
                if (rightBest > 14f) right += PointF(rightX.toFloat(), y.toFloat())
            }
            val t = fit(top) ?: return null
            val b = fit(bottom) ?: return null
            val l = fit(left.map { PointF(it.y, it.x) }) ?: return null
            val r = fit(right.map { PointF(it.y, it.x) }) ?: return null

            fun intersection(m1: Float, c1: Float, m2: Float, c2: Float): PointF? {
                val den = 1f - m1 * m2
                if (abs(den) < 0.02f) return null
                val x = (m2 * c1 + c2) / den
                return PointF(x, m1 * x + c1)
            }
            val tl = intersection(t.first, t.second, l.first, l.second) ?: return null
            val tr = intersection(t.first, t.second, r.first, r.second) ?: return null
            val bl = intersection(b.first, b.second, l.first, l.second) ?: return null
            val br = intersection(b.first, b.second, r.first, r.second) ?: return null
            val pts = arrayOf(tl, tr, br, bl)
            if (!pts.all { it.x in 0f..w.toFloat() && it.y in 0f..h.toFloat() }) return null
            val areaRatio = abs(polygonArea(pts)) / (w.toFloat() * h.toFloat())
            if (areaRatio < 0.30f) return null
            val topLen = distance(tl, tr); val bottomLen = distance(bl, br)
            val leftLen = distance(tl, bl); val rightLen = distance(tr, br)
            if (topLen < w * 0.30f || bottomLen < w * 0.30f || leftLen < h * 0.30f || rightLen < h * 0.30f) return null
            return Quad(pts.map { PointF(it.x / scale, it.y / scale) }.toTypedArray(), max(topLen, bottomLen).roundToInt(), max(leftLen, rightLen).roundToInt())
        } finally {
            small.recycle()
        }
    }

    private fun polygonArea(p: Array<PointF>): Float {
        var s = 0.0
        for (i in p.indices) {
            val j = (i + 1) % p.size
            s += p[i].x.toDouble() * p[j].y - p[j].x.toDouble() * p[i].y
        }
        return (s / 2.0).toFloat()
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
                "High Contrast", "Clean White" -> v = (((v - 128) * 1.28f) + 128).roundToInt().coerceIn(0, 255)
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
        var sum = 0.0; for (i in 0..255) sum += i.toDouble() * hist[i]
        var sb = 0.0; var wb = 0; var best = 0.0; var threshold = 128
        for (t in 0..255) {
            wb += hist[t]; if (wb == 0) continue
            val wf = total - wb; if (wf == 0) break
            sb += t.toDouble() * hist[t]
            val mb = sb / wb; val mf = (sum - sb) / wf
            val variance = wb.toDouble() * wf * (mb - mf) * (mb - mf)
            if (variance > best) { best = variance; threshold = t }
        }
        return threshold.coerceIn(45, 210)
    }
}
