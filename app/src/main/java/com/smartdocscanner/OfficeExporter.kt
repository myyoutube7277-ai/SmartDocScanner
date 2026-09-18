package com.smartdocscanner

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Offline Office exporters.
 *
 * The OCR pipeline supplies page text. Word keeps that text editable with page-sized
 * paragraphs; Excel reconstructs rows and columns from tabs / repeated spacing instead
 * of putting the entire OCR result into one cell. Every export is also registered in
 * SmartDoc Scanner's My Files area.
 */
object OfficeExporter {
    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun safe(s: String): String = s
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(70)
        .ifBlank { "export" }

    private fun output(context: Context, title: String, ext: String): File {
        val dir = DocumentStore.documentsDir(context)
        var file = File(dir, "${safe(title)}.$ext")
        var n = 2
        while (file.exists()) file = File(dir, "${safe(title)}_$n.$ext").also { n++ }
        return file
    }

    private fun put(z: ZipOutputStream, name: String, data: String) {
        z.putNextEntry(ZipEntry(name))
        z.write(data.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }

    private fun register(context: Context, file: File, title: String) {
        if (file.exists() && file.length() > 0L) {
            DocumentStore.register(context, file, file.name)
        }
    }

    fun docx(context: Context, title: String, text: String): File {
        val file = output(context, title, "docx")
        val paragraphs = text.replace("\r", "").split("\n")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            put(z, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="jpeg" ContentType="image/jpeg"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                </Types>
            """.trimIndent())
            put(z, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                </Relationships>
            """.trimIndent())
            put(z, "docProps/core.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dc:title>${esc(title)}</dc:title><dc:creator>SmartDoc Scanner</dc:creator>
                </cp:coreProperties>
            """.trimIndent())
            put(z, "word/styles.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
                </w:styles>
            """.trimIndent())
            val body = buildString {
                paragraphs.forEach { line ->
                    append("<w:p><w:pPr><w:spacing w:after=\"100\"/></w:pPr><w:r><w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r></w:p>")
                }
                append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr>")
            }
            put(z, "word/document.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>$body</w:body>
                </w:document>
            """.trimIndent())
        }
        register(context, file, title)
        return file
    }

    fun xlsx(context: Context, title: String, text: String): File {
        val file = output(context, title, "xlsx")
        val rows = text.replace("\r", "").split("\n").map { line ->
            when {
                line.contains("\t") -> line.split("\t")
                line.contains(Regex("\\s{2,}")) -> line.trim().split(Regex("\\s{2,}"))
                else -> listOf(line)
            }
        }
        val maxCol = rows.maxOfOrNull { it.size } ?: 1
        ZipOutputStream(FileOutputStream(file)).use { z ->
            put(z, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                </Types>
            """.trimIndent())
            put(z, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                </Relationships>
            """.trimIndent())
            put(z, "docProps/core.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${esc(title)}</dc:title><dc:creator>SmartDoc Scanner</dc:creator></cp:coreProperties>
            """.trimIndent())
            put(z, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Scanned Data" sheetId="1" r:id="rId1"/></sheets></workbook>
            """.trimIndent())
            put(z, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>
            """.trimIndent())

            val sheet = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:${column(maxCol)}${rows.size.coerceAtLeast(1)}\"/><sheetData>")
                rows.forEachIndexed { r, row ->
                    append("<row r=\"${r + 1}\">")
                    row.forEachIndexed { c, value ->
                        append("<c r=\"${column(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value.trim())}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            put(z, "xl/worksheets/sheet1.xml", sheet)
        }
        register(context, file, title)
        return file
    }

    /** Visual Word export for callers that already have rendered PDF pages. */
    fun docxFromImages(context: Context, title: String, images: List<File>): File {
        val file = output(context, title + "_visual", "docx")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            val rels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            val body = StringBuilder()
            images.forEachIndexed { i, img ->
                val n = i + 1
                val rid = "rImg$n"
                rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image$n.jpg\"/>")
                z.putNextEntry(ZipEntry("word/media/image$n.jpg")); img.inputStream().use { it.copyTo(z) }; z.closeEntry()
                body.append("<w:p><w:r><w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><wp:extent cx=\"5486400\" cy=\"7772400\"/><wp:docPr id=\"$n\" name=\"Page $n\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"$n\" name=\"image$n.jpg\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"$rid\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"5486400\" cy=\"7772400\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
                if (i != images.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
            rels.append("</Relationships>")
            put(z, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>
            """.trimIndent())
            put(z, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>
            """.trimIndent())
            put(z, "word/_rels/document.xml.rels", rels.toString())
            put(z, "word/styles.xml", """<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/></w:rPr></w:rPrDefault></w:docDefaults></w:styles>""")
            put(z, "word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0"/></w:sectPr></w:body></w:document>""")
        }
        register(context, file, title)
        return file
    }

    fun xlsxFromImages(context: Context, title: String, images: List<File>): File {
        // A visual workbook is intentionally separate from the editable OCR workbook.
        val file = output(context, title + "_visual", "xlsx")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            put(z, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Pages" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put(z, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val rows = images.indices.joinToString("") { "<row r=\"${it + 1}\"><c r=\"A${it + 1}\" t=\"inlineStr\"><is><t>Page ${it + 1}: ${images[it].name}</t></is></c></row>" }
            put(z, "xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""")
        }
        register(context, file, title)
        return file
    }

    /**
     * Builds an editable workbook from OCR rows while preserving detected table columns.
     * Each inner list is one visual OCR row, ordered left-to-right.
     */
    fun xlsxFromRows(context: Context, title: String, rows: List<List<String>>): File {
        val file = output(context, title, "xlsx")
        val safeRows = if (rows.isEmpty()) listOf(listOf("")) else rows
        val maxCol = safeRows.maxOfOrNull { it.size } ?: 1
        fun escCell(v: String): String = esc(v).replace("\n", " ")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            put(z, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                </Types>
            """.trimIndent())
            put(z, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                </Relationships>
            """.trimIndent())
            put(z, "docProps/core.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>\${esc(title)}</dc:title><dc:creator>SmartDoc Scanner</dc:creator>
                </cp:coreProperties>
            """.trimIndent())
            put(z, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Scanned Data" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent())
            put(z, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent())
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:\${column(maxCol - 1)}\${safeRows.size}"/><sheetFormatPr defaultRowHeight="20"/><sheetData>""")
                safeRows.forEachIndexed { r, row ->
                    append("<row r=\"\${r + 1}\">")
                    row.forEachIndexed { c, value ->
                        append("<c r=\"\${column(c)}\${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">\${escCell(value.trim())}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            put(z, "xl/worksheets/sheet1.xml", sheet)
        }
        register(context, file, title)
        return file
    }

    private fun column(index: Int): String {
        var n = index + 1
        var out = ""
        while (n > 0) { val r = (n - 1) % 26; out = ('A'.code + r).toChar() + out; n = (n - 1) / 26 }
        return out
    }
}
