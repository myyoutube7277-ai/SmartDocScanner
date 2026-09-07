package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

object ScanProcessor {

    data class Quad(
        val p: Array<PointF>,
        val width: Int,
        val height: Int
    )

    fun decode(file: File): Bitmap? =
        BitmapFactory.decodeFile(file.absolutePath)


    fun autoCrop(src: Bitmap): Bitmap {

        val q = detectQuad(src)
            ?: return safeCrop(src)

        val tl = q.p[0]
        val tr = q.p[1]
        val br = q.p[2]
        val bl = q.p[3]

        val w = max(
            distance(tl, tr),
            distance(bl, br)
        )
            .roundToInt()
            .coerceAtLeast(1)

        val h = max(
            distance(tl, bl),
            distance(tr, br)
        )
            .roundToInt()
            .coerceAtLeast(1)

        val dst =
            Bitmap.createBitmap(
                w,
                h,
                Bitmap.Config.ARGB_8888
            )

        val m = Matrix()

        val srcPts = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val dstPts = floatArrayOf(
            0f, 0f,
            w.toFloat(), 0f,
            w.toFloat(), h.toFloat(),
            0f, h.toFloat()
        )

        if (
            !m.setPolyToPoly(
                srcPts,
                0,
                dstPts,
                0,
                4
            )
        ) {
            return safeCrop(src)
        }

        Canvas(dst).drawBitmap(
            src,
            m,
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )
        )

        return dst
    }


    private fun safeCrop(
        b: Bitmap
    ): Bitmap {

        val margin =
            (min(b.width, b.height) * 0.035f)
                .roundToInt()

        val w =
            (b.width - margin * 2)
                .coerceAtLeast(1)

        val h =
            (b.height - margin * 2)
                .coerceAtLeast(1)

        return Bitmap.createBitmap(
            b,
            margin,
            margin,
            w,
            h
        )
    }


    private fun detectQuad(
        src: Bitmap
    ): Quad? {

        val maxSide = 700

        val scale =
            min(
                1f,
                maxSide.toFloat() /
                    max(src.width, src.height)
            )

        val w =
            (src.width * scale)
                .roundToInt()

        val h =
            (src.height * scale)
                .roundToInt()

        val b =
            Bitmap.createScaledBitmap(
                src,
                w,
                h,
                true
            )

        val pix =
            IntArray(w * h)

        b.getPixels(
            pix,
            0,
            w,
            0,
            0,
            w,
            h
        )

        fun gray(
            x: Int,
            y: Int
        ): Float {

            val c = pix[y * w + x]

            return (
                0.299f * Color.red(c) +
                0.587f * Color.green(c) +
                0.114f * Color.blue(c)
            )
        }


        fun fit(
            points: List<PointF>
        ): Pair<Float, Float>? {

            if (points.size < 8) {
                return null
            }

            var sx = 0.0
            var sy = 0.0
            var sxx = 0.0
            var sxy = 0.0

            points.forEach {

                sx += it.x
                sy += it.y
                sxx += it.x * it.x
                sxy += it.x * it.y
            }

            val n =
                points.size.toDouble()

            val d =
                n * sxx - sx * sx

            if (abs(d) < 1e-6) {
                return null
            }

            val a =
                (
                    (n * sxy - sx * sy) / d
                ).toFloat()

            val c =
                (
                    (sy - a * sx) / n
                ).toFloat()

            return a to c
        }


        val top =
            mutableListOf<PointF>()

        val bottom =
            mutableListOf<PointF>()

        val xStep =
            max(1, w / 80)

        for (
            x in 10 until (w - 10) step xStep
        ) {

            var bestT = 0f
            var bestY = 1

            for (
                y in 5 until max(6, h / 3)
            ) {

                val g =
                    abs(
                        gray(x, y) -
                            gray(x, y - 1)
                    )

                if (g > bestT) {
                    bestT = g
                    bestY = y
                }
            }

            var bestB = 0f
            var by = h - 2

            val bottomStart =
                max(2, h * 2 / 3)

            for (
                y in bottomStart until (h - 2)
            ) {

                val g =
                    abs(
                        gray(x, y) -
                            gray(x, y - 1)
                    )

                if (g > bestB) {
                    bestB = g
                    by = y
                }
            }

            if (bestT > 18) {
                top += PointF(
                    x.toFloat(),
                    bestY.toFloat()
                )
            }

            if (bestB > 18) {
                bottom += PointF(
                    x.toFloat(),
                    by.toFloat()
                )
            }
        }


        val lf =
            mutableListOf<PointF>()

        val rt =
            mutableListOf<PointF>()

        val yStep =
            max(1, h / 80)

        for (
            y in 10 until (h - 10) step yStep
        ) {

            var bestL = 0f
            var bx = 1

            for (
                x in 5 until max(6, w / 3)
            ) {

                val g =
                    abs(
                        gray(x, y) -
                            gray(x - 1, y)
                    )

                if (g > bestL) {
                    bestL = g
                    bx = x
                }
            }

            var bestR = 0f
            var rx = w - 2

            val rightStart =
                max(2, w * 2 / 3)

            for (
                x in rightStart until (w - 2)
            ) {

                val g =
                    abs(
                        gray(x, y) -
                            gray(x - 1, y)
                    )

                if (g > bestR) {
                    bestR = g
                    rx = x
                }
            }

            if (bestL > 18) {
                lf += PointF(
                    bx.toFloat(),
                    y.toFloat()
                )
            }

            if (bestR > 18) {
                rt += PointF(
                    rx.toFloat(),
                    y.toFloat()
                )
            }
        }


        val t =
            fit(top) ?: return null

        val bo =
            fit(bottom) ?: return null


        fun fitX(
            ps: List<PointF>
        ): Pair<Float, Float>? {

            val swapped =
                ps.map {
                    PointF(it.y, it.x)
                }

            val f =
                fit(swapped)
                    ?: return null

            return f
        }


        val l =
            fitX(lf)
                ?: return null

        val r =
            fitX(rt)
                ?: return null


        fun intersectY(
            a: Float,
            b: Float,
            c: Float,
            d: Float
        ): PointF? {

            val den =
                1f - c * a

            if (abs(den) < 0.02f) {
                return null
            }

            val x =
                (c * b + d) / den

            val y =
                a * x + b

            return PointF(x, y)
        }


        val tl =
            intersectY(
                t.first,
                t.second,
                l.first,
                l.second
            ) ?: return null

        val tr =
            intersectY(
                t.first,
                t.second,
                r.first,
                r.second
            ) ?: return null

        val bl =
            intersectY(
                bo.first,
                bo.second,
                l.first,
                l.second
            ) ?: return null

        val br =
            intersectY(
                bo.first,
                bo.second,
                r.first,
                r.second
            ) ?: return null


        val pts =
            arrayOf(
                tl,
                tr,
                br,
                bl
            )

        val ok =
            pts.all {
                it.x in 0f..w.toFloat() &&
                it.y in 0f..h.toFloat()
            }

        if (!ok) {
            return null
        }

        return Quad(
            pts.map {
                PointF(
                    it.x / scale,
                    it.y / scale
                )
            }.toTypedArray(),

            max(
                distance(tl, tr),
                distance(bl, br)
            ).roundToInt(),

            max(
                distance(tl, bl),
                distance(tr, br)
            ).roundToInt()
        )
    }


    private fun distance(
        a: PointF,
        b: PointF
    ): Float {

        return hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble()
        ).toFloat()
    }


    fun rotate(
        src: Bitmap
    ): Bitmap {

        val m =
            Matrix().apply {
                postRotate(90f)
            }

        return Bitmap.createBitmap(
            src,
            0,
            0,
            src.width,
            src.height,
            m,
            true
        )
    }


    fun filter(
        src: Bitmap,
        mode: String
    ): Bitmap {

        val out =
            Bitmap.createBitmap(
                src.width,
                src.height,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(out)

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        val cm =
            when (mode) {

                "B&W" -> {

                    ColorMatrix().apply {

                        setSaturation(0f)

                        val v = 1.7f
                        val t = -0.35f * 255f

                        set(
                            floatArrayOf(
                                v, 0f, 0f, t, 0f,
                                0f, v, 0f, t, 0f,
                                0f, 0f, v, 0f, t,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    }
                }

                "High Contrast" -> {

                    ColorMatrix(
                        floatArrayOf(
                            1.7f, 0f, 0f, -80f, 0f,
                            0f, 1.7f, 0f, -80f, 0f,
                            0f, 0f, 1.7f, -80f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                }

                "Gray" -> {

                    ColorMatrix().apply {
                        setSaturation(0f)
                    }
                }

                else -> {
                    ColorMatrix()
                }
            }

        paint.colorFilter =
            ColorMatrixColorFilter(cm)

        canvas.drawBitmap(
            src,
            0f,
            0f,
            paint
        )

        return out
    }
}
