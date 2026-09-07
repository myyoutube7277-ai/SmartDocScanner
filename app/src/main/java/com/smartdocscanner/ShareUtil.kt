package com.smartdocscanner

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareUtil {
    fun share(context:Context,file:File,mime:String) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.provider",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
            type=mime; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"Share"))
    }
}
