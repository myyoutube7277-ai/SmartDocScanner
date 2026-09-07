package com.smartdocscanner

import android.content.Context

object SettingsStore {

    private const val PREF = "smartdoc_settings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // Dark Theme
    fun darkTheme(context: Context): Boolean {
        return prefs(context).getBoolean("dark_theme", false)
    }

    fun setDarkTheme(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean("dark_theme", value)
            .apply()
    }

    // Automatic Crop
    fun autoCrop(context: Context): Boolean {
        return prefs(context).getBoolean("auto_crop", true)
    }

    fun setAutoCrop(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean("auto_crop", value)
            .apply()
    }

    // Hindi / Devanagari OCR
    fun hindiOcr(context: Context): Boolean {
        return prefs(context).getBoolean("hindi_ocr", true)
    }

    fun setHindiOcr(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean("hindi_ocr", value)
            .apply()
    }

    // PDF Size Mode
    fun pdfMode(context: Context): PdfEngine.SizeMode {
        return if (
            prefs(context).getString("pdf_mode", "QUALITY") == "MAXIMUM"
        ) {
            PdfEngine.SizeMode.MAXIMUM
        } else {
            PdfEngine.SizeMode.QUALITY
        }
    }

    fun setPdfMode(
        context: Context,
        value: PdfEngine.SizeMode
    ) {
        prefs(context).edit()
            .putString("pdf_mode", value.name)
            .apply()
    }

    // Maximum PDF Size
    fun maxMb(context: Context): Int {
        return prefs(context)
            .getInt("max_mb", 5)
            .coerceAtLeast(2)
    }

    fun setMaxMb(
        context: Context,
        value: Int
    ) {
        prefs(context).edit()
            .putInt("max_mb", value.coerceAtLeast(2))
            .apply()
    }

    // Default Image Filter
    fun filter(context: Context): String {
        return prefs(context)
            .getString("filter", "Color")
            ?: "Color"
    }

    fun setFilter(
        context: Context,
        value: String
    ) {
        prefs(context).edit()
            .putString("filter", value)
            .apply()
    }
}
