package com.smartdocscanner

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficeExporter {
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .replace("\"","&quot;").replace("'","&apos;")

    fun docx(context:Context, title:String, text:String):File {
        val f=File(context.filesDir,"${safe(title)}.docx")
        ZipOutputStream(FileOutputStream(f)).use{z->
            put(z,"[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put(z,"_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            val ps=text.lines().joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r></w:p>" }
            put(z,"word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$ps<w:sectPr/></w:body></w:document>""")
        }
        return f
    }

    fun xlsx(context:Context,title:String,text:String):File {
        val f=File(context.filesDir,"${safe(title)}.xlsx")
        val rows=text.lines().map{ line ->
            val cells=if(line.contains("\t")) line.split("\t") else if(line.contains(Regex("\\s{2,}"))) line.trim().split(Regex("\\s{2,}")) else listOf(line)
            cells
        }
        ZipOutputStream(FileOutputStream(f)).use{z->
            put(z,"[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put(z,"_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z,"xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put(z,"xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val xml=rows.mapIndexed{ri,row->
                val cells=row.mapIndexed{ci,v->
                    val col=column(ci)
                    "<c r=\"$col${ri+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"
                }.joinToString("")
                "<row r=\"${ri+1}\">$cells</row>"
            }.joinToString("")
            put(z,"xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$xml</sheetData></worksheet>""")
        }
        return f
    }

    private fun column(i:Int):String { var n=i+1; var s=""; while(n>0){val r=(n-1)%26;s=('A'.code+r).toChar()+s;n=(n-1)/26};return s }
    private fun safe(s:String)=s.replace(Regex("[^A-Za-z0-9._-]"),"_").take(50).ifBlank{"export"}
    private fun put(z:ZipOutputStream,name:String,data:String){z.putNextEntry(ZipEntry(name));z.write(data.toByteArray(Charsets.UTF_8));z.closeEntry()}
}
