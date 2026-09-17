package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

/**
 * Local document-image processing. The important rule is SAFETY FIRST:
 * if document detection is not confident, the original image is returned
 * instead of making a bad crop.
 */
object ScanProcessor {
    data class Quad(val p: Array<PointF>, val width: Int, val height: Int)

    fun decode(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    fun autoCrop(src: Bitmap): Bitmap {
        val q = detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val tl = q.p[0]; val tr = q.p[1]; val br = q.p[2]; val bl = q.p[3]
        val w = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(2)
        val h = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(2)
        if (w < 300 || h < 300) return src.copy(Bitmap.Config.ARGB_8888, false)

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

    /**
     * Conservative edge detector. It only accepts a quadrilateral when all
     * four sides have enough support and the resulting polygon is sensible.
     */
    private fun detectQuad(src: Bitmap): Quad? {
        val maxSide = 900
        val scale = min(1f, maxSide.toFloat() / max(src.width, src.height).toFloat())
        val w = (src.width * scale).roundToInt().coerceAtLeast(20)
        val h = (src.height * scale).roundToInt().coerceAtLeast(20)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val gray = IntArray(w * h)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) gray[i] = (0.299f * Color.red(px[i]) + 0.587f * Color.green(px[i]) + 0.114f * Color.blue(px[i])).roundToInt()

            fun v(x: Int, y: Int): Int = gray[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
            fun edgeH(x: Int, y: Int): Int = abs(v(x, y) - v(x, y - 2))
            fun edgeV(x: Int, y: Int): Int = abs(v(x, y) - v(x - 2, y))

            val top = mutableListOf<PointF>(); val bottom = mutableListOf<PointF>()
            val left = mutableListOf<PointF>(); val right = mutableListOf<PointF>()
            val xStep = max(3, w / 90); val yStep = max(3, h / 90)

            for (x in (w / 20) until (w * 19 / 20) step xStep) {
                var best = 0; var bestY = -1
                for (y in (h / 30)..(h * 2 / 5)) {
                    val e = edgeH(x, y)
                    if (e > best) { best = e; bestY = y }
                }
                if (best >= 28) top += PointF(x.toFloat(), bestY.toFloat())

                best = 0; bestY = -1
                for (y in (h * 3 / 5)..(h * 29 / 30)) {
                    val e = edgeH(x, y)
                    if (e > best) { best = e; bestY = y }
                }
                if (best >= 28) bottom += PointF(x.toFloat(), bestY.toFloat())
            }

            for (y in (h / 20) until (h * 19 / 20) step yStep) {
                var best = 0; var bestX = -1
                for (x in (w / 30)..(w * 2 / 5)) {
                    val e = edgeV(x, y)
                    if (e > best) { best = e; bestX = x }
                }
                if (best >= 28) left += PointF(bestX.toFloat(), y.toFloat())

                best = 0; bestX = -1
                for (x in (w * 3 / 5)..(w * 29 / 30)) {
                    val e = edgeV(x, y)
                    if (e > best) { best = e; bestX = x }
                }
                if (best >= 28) right += PointF(bestX.toFloat(), y.toFloat())
            }

            if (top.size < 10 || bottom.size < 10 || left.size < 10 || right.size < 10) return null

            fun median(points: List<PointF>, y: Boolean): Float {
                val a = points.map { if (y) it.y else it.x }.sorted()
                return a[a.size / 2]
            }
            // Use robust medians for a near-rectangular document. Perspective
            // correction is attempted only when opposite sides are supported.
            val tx = median(top, false); val bx = median(bottom, false)
            val ly = median(left, true); val ry = median(right, true)
            val topY = median(top, true); val bottomY = median(bottom, true)
            val leftX = median(left, false); val rightX = median(right, false)

            val marginX = w * 0.06f; val marginY = h * 0.06f
            if (leftX < marginX || rightX > w - marginX || topY < marginY || bottomY > h - marginY) return null
            if (rightX - leftX < w * 0.45f || bottomY - topY < h * 0.45f) return null

            // If the detected edges are essentially straight, use the four
            // intersections of the fitted side lines; otherwise reject.
            fun fitY(points: List<PointF>): Pair<Float, Float>? {
                if (points.size < 6) return null
                var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
                points.forEach { sx += it.x; sy += it.y; sxx += it.x * it.x; sxy += it.x * it.y }
                val n = points.size.toDouble(); val d = n * sxx - sx * sx
                if (abs(d) < 1e-8) return null
                val a = (n * sxy - sx * sy) / d
                val b = (sy - a * sx) / n
                return a.toFloat() to b.toFloat()
            }
            fun fitX(points: List<PointF>): Pair<Float, Float>? {
                val swapped = points.map { PointF(it.y, it.x) }
                return fitY(swapped)
            }
            fun intersect(topLine: Pair<Float, Float>, sideLine: Pair<Float, Float>): PointF? {
                val den = 1f - sideLine.first * topLine.first
                if (abs(den) < 0.03f) return null
                val x = (sideLine.first * topLine.second + sideLine.second) / den
                val y = topLine.first * x + topLine.second
                return PointF(x, y)
            }

            val t = fitY(top) ?: return null
            val bo = fitY(bottom) ?: return null
            val l = fitX(left) ?: return null
            val r = fitX(right) ?: return null
            val tl = intersect(t, l) ?: return null
            val tr = intersect(t, r) ?: return null
            val bl = intersect(bo, l) ?: return null
            val br = intersect(bo, r) ?: return null
            val pts = arrayOf(tl, tr, br, bl)

            if (!pts.all { it.x in 0f..w.toFloat() && it.y in 0f..h.toFloat() }) return null
            val area = polygonArea(pts).absoluteValue
            if (area < w.toDouble() * h * 0.55 || area > w.toDouble() * h * 0.99) return null
            if (!isConvex(pts)) return null

            val scaled = pts.map { PointF(it.x / scale, it.y / scale) }.toTypedArray()
            val outW = max(distance(scaled[0], scaled[1]), distance(scaled[3], scaled[2])).roundToInt()
            val outH = max(distance(scaled[0], scaled[3]), distance(scaled[1], scaled[2])).roundToInt()
            return Quad(scaled, outW, outH)
        } finally {
            small.recycle()
        }
    }

    private fun polygonArea(p: Array<PointF>): Double {
        var s = 0.0
        for (i in p.indices) { val j = (i + 1) % p.size; s += p[i].x * p[j].y - p[j].x * p[i].y }
        return s / 2.0
    }

    private fun isConvex(p: Array<PointF>): Boolean {
        var sign = 0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = p[(i + 2) % p.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1f) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (sign != s) return false
        }
        return true
    }

    private fun distance(a: PointF, b: PointF) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun rotate(src: Bitmap): Bitmap {
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Non-destructive processing. Color always preserves the source pixels. */
    fun filter(src: Bitmap, mode: String): Bitmap {
        if (mode.equals("Color", true) || mode.equals("Original", true)) return src.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width; val h = src.height
        val input = IntArray(w * h); src.getPixels(input, 0, w, 0, 0, w, h)
        val gray = IntArray(input.size)
        for (i in input.indices) gray[i] = (0.299f * Color.red(input[i]) + 0.587f * Color.green(input[i]) + 0.114f * Color.blue(input[i])).roundToInt().coerceIn(0, 255)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(input.size)

        if (mode.equals("B&W", true)) {
            // Adaptive threshold keeps thin letters and avoids the old global
            // 158 threshold that was destroying light text and signatures.
            val integral = LongArray((w + 1) * (h + 1))
            for (y in 0 until h) {
                var row = 0L
                for (x in 0 until w) {
                    row += gray[y * w + x]
                    integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + row
                }
            }
            val radius = max(8, min(w, h) / 80)
            for (y in 0 until h) for (x in 0 until w) {
                val x0 = max(0, x - radius); val x1 = min(w - 1, x + radius)
                val y0 = max(0, y - radius); val y1 = min(h - 1, y + radius)
                val a = integral[y0 * (w + 1) + x0]
                val b = integral[y0 * (w + 1) + x1 + 1]
                val c = integral[(y1 + 1) * (w + 1) + x0]
                val d = integral[(y1 + 1) * (w + 1) + x1 + 1]
                val mean = (d - b - c + a).toFloat() / ((x1 - x0 + 1) * (y1 - y0 + 1))
                val value = if (gray[y * w + x] < mean - 10f) 0 else 255
                pixels[y * w + x] = Color.rgb(value, value, value)
            }
        } else {
            // Gentle contrast enhancement; no hard clipping.
            for (i in gray.indices) {
                val v = (((gray[i] - 128) * 1.18f) + 128f).roundToInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(v, v, v)
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
