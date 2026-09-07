package com.smartdocscanner

import android.content.Context

object SettingsStore {
    private const val PREF = "smartdoc_settings"
    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun darkTheme(c: Context): Boolean = p(c).getBoolean("dark_theme", false)
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

    fun maxMb(c: Context): Int = p(c).getInt("max_mb", 5).coerceAtLeast(2)
    fun setMaxMb(c: Context, value: Int) = p(c).edit().putInt("max_mb", value.coerceAtLeast(2)).apply()

    fun filter(c: Context): String = p(c).getString("filter", "B&W") ?: "B&W"
    fun setFilter(c: Context, value: String) = p(c).edit().putString("filter", value).apply()
}
