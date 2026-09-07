package com.smartdocscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScanProcessor {

    data class Quad(
        val points: Array<PointF>,
        val width: Int,
        val height: Int
    )

    fun decode(file: File): Bitmap? =
        BitmapFactory.decodeFile(file.absolutePath)

    fun autoCrop(src: Bitmap): Bitmap {
        val quad = detectQuad(src) ?: return safeCrop(src)

        val tl = quad.points[0]
        val tr = quad.points[1]
        val br = quad.points[2]
        val bl = quad.points[3]

        val outWidth = max(
            distance(tl, tr),
            distance(bl, br)
        ).roundToInt().coerceAtLeast(1)

        val outHeight = max(
            distance(tl, bl),
            distance(tr, br)
        ).roundToInt().coerceAtLeast(1)

        val destination = Bitmap.createBitmap(
            outWidth,
            outHeight,
            Bitmap.Config.ARGB_8888
        )

        val matrix = Matrix()
        val sourcePoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val destinationPoints = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )

        if (!matrix.setPolyToPoly(
                sourcePoints, 0,
                destinationPoints, 0,
                4
            )
        ) {
            destination.recycle()
            return safeCrop(src)
        }

        Canvas(destination).drawBitmap(
            src,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        return destination
    }

    private fun safeCrop(bitmap: Bitmap): Bitmap {
        val margin = (min(bitmap.width, bitmap.height) * 0.035f)
            .roundToInt()
            .coerceAtLeast(0)

        val width = (bitmap.width - margin * 2).coerceAtLeast(1)
        val height = (bitmap.height - margin * 2).coerceAtLeast(1)

        return Bitmap.createBitmap(
            bitmap,
            margin,
            margin,
            width,
            height
        )
    }

    private fun detectQuad(source: Bitmap): Quad? {
        val maxSide = 700
        val sourceMaxSide = max(source.width, source.height)

        if (sourceMaxSide <= 0) return null

        val scale = min(
            1f,
            maxSide.toFloat() / sourceMaxSide.toFloat()
        )

        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)

        val small = Bitmap.createScaledBitmap(
            source,
            width,
            height,
            true
        )

        try {
            val pixels = IntArray(width * height)
            small.getPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
            )

            fun gray(x: Int, y: Int): Float {
                val pixel = pixels[y * width + x]
                return (
                    0.299f * Color.red(pixel) +
                    0.587f * Color.green(pixel) +
                    0.114f * Color.blue(pixel)
                )
            }

            fun lineFit(points: List<PointF>): Pair<Float, Float>? {
                if (points.size < 8) return null

                var sumX = 0.0
                var sumY = 0.0
                var sumXX = 0.0
                var sumXY = 0.0

                for (point in points) {
                    sumX += point.x
                    sumY += point.y
                    sumXX += point.x * point.x
                    sumXY += point.x * point.y
                }

                val n = points.size.toDouble()
                val denominator = n * sumXX - sumX * sumX

                if (abs(denominator) < 1e-6) return null

                val slope = (
                    (n * sumXY - sumX * sumY) / denominator
                ).toFloat()

                val intercept = (
                    (sumY - slope * sumX) / n
                ).toFloat()

                return slope to intercept
            }

            val top = mutableListOf<PointF>()
            val bottom = mutableListOf<PointF>()
            val left = mutableListOf<PointF>()
            val right = mutableListOf<PointF>()

            val xStep = max(1, width / 80)
            val yStep = max(1, height / 80)

            if (width > 20 && height > 20) {
                for (x in 10 until width - 10 step xStep) {
                    var bestTop = 0f
                    var bestTopY = 1

                    val topEnd = min(
                        height / 3,
                        height - 1
                    )

                    for (y in 1 until topEnd) {
                        val edge = abs(
                            gray(x, y) - gray(x, y - 1)
                        )

                        if (edge > bestTop) {
                            bestTop = edge
                            bestTopY = y
                        }
                    }

                    var bestBottom = 0f
                    var bestBottomY = height - 2

                    val bottomStart = max(
                        2,
                        (height * 2) / 3
                    )

                    for (y in bottomStart until height - 1) {
                        val edge = abs(
                            gray(x, y) - gray(x, y - 1)
                        )

                        if (edge > bestBottom) {
                            bestBottom = edge
                            bestBottomY = y
                        }
                    }

                    if (bestTop > 18f) {
                        top.add(
                            PointF(
                                x.toFloat(),
                                bestTopY.toFloat()
                            )
                        )
                    }

                    if (bestBottom > 18f) {
                        bottom.add(
                            PointF(
                                x.toFloat(),
                                bestBottomY.toFloat()
                            )
                        )
                    }
                }

                for (y in 10 until height - 10 step yStep) {
                    var bestLeft = 0f
                    var bestLeftX = 1

                    val leftEnd = min(
                        width / 3,
                        width - 1
                    )

                    for (x in 1 until leftEnd) {
                        val edge = abs(
                            gray(x, y) - gray(x - 1, y)
                        )

                        if (edge > bestLeft) {
                            bestLeft = edge
                            bestLeftX = x
                        }
                    }

                    var bestRight = 0f
                    var bestRightX = width - 2

                    val rightStart = max(
                        2,
                        (width * 2) / 3
                    )

                    for (x in rightStart until width - 1) {
                        val edge = abs(
                            gray(x, y) - gray(x - 1, y)
                        )

                        if (edge > bestRight) {
                            bestRight = edge
                            bestRightX = x
                        }
                    }

                    if (bestLeft > 18f) {
                        left.add(
                            PointF(
                                bestLeftX.toFloat(),
                                y.toFloat()
                            )
                        )
                    }

                    if (bestRight > 18f) {
                        right.add(
                            PointF(
                                bestRightX.toFloat(),
                                y.toFloat()
                            )
                        )
                    }
                }
            }

            val topLine = lineFit(top) ?: return null
            val bottomLine = lineFit(bottom) ?: return null

            fun verticalLineFit(
                points: List<PointF>
            ): Pair<Float, Float>? {
                val swapped = points.map {
                    PointF(it.y, it.x)
                }
                return lineFit(swapped)
            }

            val leftLine = verticalLineFit(left) ?: return null
            val rightLine = verticalLineFit(right) ?: return null

            fun intersection(
                a: Float,
                b: Float,
                c: Float,
                d: Float
            ): PointF? {
                val denominator = 1f - c * a

                if (abs(denominator) < 0.02f) {
                    return null
                }

                val x = (c * b + d) / denominator
                val y = a * x + b

                return PointF(x, y)
            }

            val topLeft = intersection(
                topLine.first,
                topLine.second,
                leftLine.first,
                leftLine.second
            ) ?: return null

            val topRight = intersection(
                topLine.first,
                topLine.second,
                rightLine.first,
                rightLine.second
            ) ?: return null

            val bottomLeft = intersection(
                bottomLine.first,
                bottomLine.second,
                leftLine.first,
                leftLine.second
            ) ?: return null

            val bottomRight = intersection(
                bottomLine.first,
                bottomLine.second,
                rightLine.first,
                rightLine.second
            ) ?: return null

            val points = arrayOf(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
            )

            val inside = points.all { point ->
                point.x >= 0f &&
                point.x <= width.toFloat() &&
                point.y >= 0f &&
                point.y <= height.toFloat()
            }

            if (!inside) return null

            val scaledPoints = points.map {
                PointF(
                    it.x / scale,
                    it.y / scale
                )
            }.toTypedArray()

            val resultWidth = max(
                distance(topLeft, topRight),
                distance(bottomLeft, bottomRight)
            ).roundToInt()

            val resultHeight = max(
                distance(topLeft, bottomLeft),
                distance(topRight, bottomRight)
            ).roundToInt()

            return Quad(
                scaledPoints,
                resultWidth,
                resultHeight
            )
        } finally {
            small.recycle()
        }
    }

    private fun distance(a: PointF, b: PointF): Float {
        return hypot(
            (a.x - b.x).toDouble(),
            (a.y - b.y).toDouble()
        ).toFloat()
    }

    fun rotate(source: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(90f)

        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
    }

    fun filter(source: Bitmap, mode: String): Bitmap {
        val output = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = when (mode) {
            "B&W" -> {
                ColorMatrix().apply {
                    setSaturation(0f)

                    val value = 1.7f
                    val translate = -0.35f * 255f

                    set(
                        floatArrayOf(
                            value, 0f, 0f, 0f, translate,
                            0f, value, 0f, 0f, translate,
                            0f, 0f, value, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                }
            }

            "High Contrast" -> {
                ColorMatrix(
                    floatArrayOf(
                        1.7f, 0f, 0f, 0f, -80f,
                        0f, 1.7f, 0f, 0f, -80f,
                        0f, 0f, 1.7f, 0f, -80f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "Gray" -> {
                ColorMatrix().apply {
                    setSaturation(0f)
                }
            }

            else -> ColorMatrix()
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }
}
