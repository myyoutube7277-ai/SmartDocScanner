package com.smartdocscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File

object ScanProcessor {

    fun decode(file: File): Bitmap? {
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun autoCrop(source: Bitmap): Bitmap {
        // Stable build-first version.
        // Returns a safe, slightly trimmed copy instead of experimental edge detection.
        val margin = (minOf(source.width, source.height) * 0.02f).toInt()

        if (margin <= 0 ||
            source.width <= margin * 2 ||
            source.height <= margin * 2
        ) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        return Bitmap.createBitmap(
            source,
            margin,
            margin,
            source.width - margin * 2,
            source.height - margin * 2
        )
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

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = when (mode) {
            "Gray" -> {
                ColorMatrix().apply {
                    setSaturation(0f)
                }
            }

            "B&W" -> {
                ColorMatrix(
                    floatArrayOf(
                        1.7f, 0f, 0f, 0f, -90f,
                        0f, 1.7f, 0f, 0f, -90f,
                        0f, 0f, 1.7f, 0f, -90f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            "High Contrast" -> {
                ColorMatrix(
                    floatArrayOf(
                        1.5f, 0f, 0f, 0f, -60f,
                        0f, 1.5f, 0f, 0f, -60f,
                        0f, 0f, 1.5f, 0f, -60f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            else -> ColorMatrix()
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        Canvas(output).drawBitmap(
            source,
            0f,
            0f,
            paint
        )

        return output
    }
}
