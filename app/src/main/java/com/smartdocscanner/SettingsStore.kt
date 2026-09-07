package com.smartdocscanner

import android.content.Context

object SettingsStore {
    private const val PREF = "smartdoc_settings"
    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun darkTheme(c: Context): Boolean = p(c).getBoolean("dark_theme", true)
    fun setDarkTheme(c: Context, value: Boolean) = p(c).edit().putBoolean("dark_theme", value).apply()

    fun autoCrop(c: Context): Boolean = p(c).getBoolean("auto_crop", true)
    fun setAutoCrop(c: Context, value: Boolean) = p(c).edit().putBoolean("auto_crop", value).apply()

    fun hindiOcr(c: Context): Boolean = p(c).getBoolean("hindi_ocr", true)
    fun setHindiOcr(c: Context, value: Boolean) = p(c).edit().putBoolean("hindi_ocr", value).apply()

    fun pdfMode(c: Context): PdfEngine.SizeMode =
        if (p(c).getString("pdf_mode", "QUALITY") == "MAXIMUM") PdfEngine.SizeMode.MAXIMUM
        else PdfEngine.SizeMode.QUALITY

    fun setPdfMode(c: Context, value: PdfEngine.SizeMode) =
        p(c).edit().putString("pdf_mode", value.name).apply()

    fun maxSizeChoice(c: Context): String =
        p(c).getString("max_size", "5 MB") ?: "5 MB"

    fun setMaxSizeChoice(c: Context, value: String) =
        p(c).edit().putString("max_size", value).apply()

    fun maxBytes(c: Context): Long = parseSize(maxSizeChoice(c))

    fun maxMb(c: Context): Int = (maxBytes(c) / (1024L * 1024L)).toInt().coerceAtLeast(1)
    fun setMaxMb(c: Context, value: Int) = setMaxSizeChoice(c, "${value.coerceAtLeast(1)} MB")

    fun paperSize(c: Context): String =
        p(c).getString("paper_size", "Auto") ?: "Auto"

    fun setPaperSize(c: Context, value: String) =
        p(c).edit().putString("paper_size", value).apply()

    fun filter(c: Context): String =
        p(c).getString("filter", "B&W") ?: "B&W"

    fun setFilter(c: Context, value: String) =
        p(c).edit().putString("filter", value).apply()

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
}
