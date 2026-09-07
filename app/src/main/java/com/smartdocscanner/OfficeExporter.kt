package com.smartdocscanner

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Office export helpers.
 * Text exports are editable. Visual PDF export keeps each rendered page intact as an image
 * inside the Office document; scanned PDF text cannot be made perfectly editable/layout-identical
 * offline without a full document-layout engine.
 */
object OfficeExporter {
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .replace("\"","&quot;").replace("'","&apos;")

    fun docx(context:Context,title:String,text:String):File {
        val f=File(context.filesDir,"${safe(title)}.docx")
        ZipOutputStream(FileOutputStream(f)).use{z->
            put(z,"[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put(z,"_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            val ps=text.lines().joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r></w:p>" }
            put(z,"word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$ps<w:sectPr/></w:body></w:document>""")
        }
        return f
    }

    /** Creates a Word document with one full-page image per rendered PDF page. */
    fun docxFromImages(context: Context, title: String, images: List<File>): File {
        val f=File(context.filesDir,"${safe(title)}_visual.docx")
        ZipOutputStream(FileOutputStream(f)).use { z ->
            val rels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            val body = StringBuilder()
            images.forEachIndexed { i, img ->
                val rid="rImg${i+1}"
                rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${i+1}.jpg\"/>")
                z.putNextEntry(ZipEntry("word/media/image${i+1}.jpg")); img.inputStream().use{it.copyTo(z)}; z.closeEntry()
                body.append("<w:p><w:r><w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><wp:extent cx=\"5486400\" cy=\"7772400\"/><wp:docPr id=\"${i+1}\" name=\"Page ${i+1}\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"${i+1}\" name=\"image${i+1}.jpg\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"$rid\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"5486400\" cy=\"7772400\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
            rels.append("</Relationships>")
            put(z,"[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put(z,"_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            put(z,"word/_rels/document.xml.rels",rels.toString())
            put(z,"word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0"/></w:sectPr></w:body></w:document>""")
        }
        return f
    }

    fun xlsx(context:Context,title:String,text:String):File {
        val f=File(context.filesDir,"${safe(title)}.xlsx")
        val rows=text.lines().map{ line ->
            if(line.contains("\t")) line.split("\t") else if(line.contains(Regex("\\s{2,}"))) line.trim().split(Regex("\\s{2,}")) else listOf(line)
        }
        ZipOutputStream(FileOutputStream(f)).use{z->
            put(z,"[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put(z,"_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z,"xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put(z,"xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val xml=rows.mapIndexed{ri,row->val cells=row.mapIndexed{ci,v->"<c r=\"${column(ci)}${ri+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"}.joinToString("");"<row r=\"${ri+1}\">$cells</row>"}.joinToString("")
            put(z,"xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$xml</sheetData></worksheet>""")
        }
        return f
    }

    /** Creates an XLSX workbook with each rendered PDF page embedded as a full-size image on a separate worksheet. */
    fun xlsxFromImages(context: Context, title: String, images: List<File>): File {
        val f = File(context.filesDir, "${safe(title)}_visual.xlsx")
        ZipOutputStream(FileOutputStream(f)).use { z ->
            val sheetEntries = StringBuilder()
            val workbookRels = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            val contentOverrides = StringBuilder()
            images.forEachIndexed { i, img ->
                val n = i + 1
                sheetEntries.append("<sheet name=\"Page $n\" sheetId=\"$n\" r:id=\"rId$n\"/>")
                workbookRels.append("<Relationship Id=\"rId$n\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$n.xml\"/>")
                contentOverrides.append("<Override PartName=\"/xl/worksheets/sheet$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/drawings/drawing$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            }
            workbookRels.append("</Relationships>")
            put(z, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$contentOverrides</Types>""")
            put(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" + sheetEntries + """</sheets></workbook>""")
            put(z, "xl/_rels/workbook.xml.rels", workbookRels.toString())
            images.forEachIndexed { i, img ->
                val n = i + 1
                put(z, "xl/worksheets/sheet$n.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheetData/><drawing r:id="rId1"/></worksheet>""")
                // Each sheet gets its own drawing relationship.
                put(z, "xl/drawings/drawing$n.xml", """<?xml version="1.0" encoding="UTF-8"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:ext cx="10058400" cy="14274800"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="$n" name="Page $n"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rImg$n" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>""")
                put(z, "xl/drawings/_rels/drawing$n.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rImg$n" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$n.jpg"/></Relationships>""")
                z.putNextEntry(ZipEntry("xl/media/image$n.jpg")); img.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
            // Fix sheet drawing targets to the sheet-specific drawings.
            images.forEachIndexed { i, _ ->
                val n=i+1
                put(z, "xl/worksheets/_rels/sheet$n.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../../drawings/drawing$n.xml"/></Relationships>""")
            }
        }
        return f
    }

    private fun column(i:Int):String { var n=i+1; var s=""; while(n>0){val r=(n-1)%26;s=('A'.code+r).toChar()+s;n=(n-1)/26};return s }
    private fun safe(s:String)=s.replace(Regex("[^A-Za-z0-9._-]"),"_").take(50).ifBlank{"export"}
    private fun put(z:ZipOutputStream,name:String,data:String){z.putNextEntry(ZipEntry(name));z.write(data.toByteArray(Charsets.UTF_8));z.closeEntry()}
}
