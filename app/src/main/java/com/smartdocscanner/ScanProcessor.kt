package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

/** Conservative, non-destructive scanner image processing. */
object ScanProcessor {
    data class Quad(val p: Array<PointF>, val width: Int, val height: Int)

    fun decode(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    /**
     * Perspective correction is deliberately conservative. If a document boundary
     * cannot be detected with strong geometric confidence, the original geometry is
     * returned unchanged instead of applying a guessed crop/rotation.
     */
    fun autoCrop(src: Bitmap): Bitmap {
        val q = detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888, false)
        val tl=q.p[0]; val tr=q.p[1]; val br=q.p[2]; val bl=q.p[3]
        val w=max(distance(tl,tr), distance(bl,br)).roundToInt().coerceAtLeast(1)
        val h=max(distance(tl,bl), distance(tr,br)).roundToInt().coerceAtLeast(1)
        val dst = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val m = Matrix()
        val srcPts=floatArrayOf(tl.x,tl.y,tr.x,tr.y,br.x,br.y,bl.x,bl.y)
        val dstPts=floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat())
        if (!m.setPolyToPoly(srcPts,0,dstPts,0,4)) {
            dst.recycle()
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        Canvas(dst).drawBitmap(src,m,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    private fun detectQuad(src: Bitmap): Quad? {
        val maxSide=800
        val scale=min(1f,maxSide.toFloat()/max(src.width,src.height).toFloat())
        val w=(src.width*scale).roundToInt().coerceAtLeast(20)
        val h=(src.height*scale).roundToInt().coerceAtLeast(20)
        val b=Bitmap.createScaledBitmap(src,w,h,true)
        try {
            val pix=IntArray(w*h); b.getPixels(pix,0,w,0,0,w,h)
            fun gray(x:Int,y:Int):Float {
                val c=pix[y*w+x]
                return 0.299f*Color.red(c)+0.587f*Color.green(c)+0.114f*Color.blue(c)
            }
            fun fit(points:List<PointF>):Pair<Float,Float>? {
                if(points.size<12) return null
                var sx=0.0; var sy=0.0; var sxx=0.0; var sxy=0.0
                points.forEach { sx+=it.x; sy+=it.y; sxx+=it.x*it.x; sxy+=it.x*it.y }
                val n=points.size.toDouble(); val d=n*sxx-sx*sx
                if(abs(d)<1e-6) return null
                val a=((n*sxy-sx*sy)/d).toFloat()
                val c=((sy-a*sx)/n).toFloat()
                return a to c
            }
            val top=mutableListOf<PointF>(); val bottom=mutableListOf<PointF>()
            for(x in 12 until w-12 step max(2,w/100)) {
                var bestT=0f; var bestY=1
                for(y in 6 until max(7,h/3)) {
                    val g=abs(gray(x,y)-gray(x,y-1))
                    if(g>bestT){bestT=g;bestY=y}
                }
                var bestB=0f; var by=h-2
                val bottomStart=(h*2/3).coerceAtLeast(2)
                for(y in bottomStart until h-2) {
                    val g=abs(gray(x,y)-gray(x,y-1))
                    if(g>bestB){bestB=g;by=y}
                }
                if(bestT>24) top+=PointF(x.toFloat(),bestY.toFloat())
                if(bestB>24) bottom+=PointF(x.toFloat(),by.toFloat())
            }
            val left=mutableListOf<PointF>(); val right=mutableListOf<PointF>()
            for(y in 12 until h-12 step max(2,h/100)) {
                var bestL=0f; var bx=1
                for(x in 6 until max(7,w/3)) {
                    val g=abs(gray(x,y)-gray(x-1,y))
                    if(g>bestL){bestL=g;bx=x}
                }
                var bestR=0f; var rx=w-2
                val rightStart=(w*2/3).coerceAtLeast(2)
                for(x in rightStart until w-2) {
                    val g=abs(gray(x,y)-gray(x-1,y))
                    if(g>bestR){bestR=g;rx=x}
                }
                if(bestL>24) left+=PointF(bx.toFloat(),y.toFloat())
                if(bestR>24) right+=PointF(rx.toFloat(),y.toFloat())
            }
            val t=fit(top) ?: return null
            val bo=fit(bottom) ?: return null
            fun fitX(ps:List<PointF>):Pair<Float,Float>? = fit(ps.map{PointF(it.y,it.x)})
            val l=fitX(left) ?: return null
            val r=fitX(right) ?: return null
            fun intersect(a:Float,b0:Float,c:Float,d:Float):PointF? {
                val den=1f-c*a
                if(abs(den)<0.03f)return null
                val x=(c*b0+d)/den; val y=a*x+b0
                return PointF(x,y)
            }
            val tl=intersect(t.first,t.second,l.first,l.second) ?: return null
            val tr=intersect(t.first,t.second,r.first,r.second) ?: return null
            val bl=intersect(bo.first,bo.second,l.first,l.second) ?: return null
            val br=intersect(bo.first,bo.second,r.first,r.second) ?: return null
            val pts=arrayOf(tl,tr,br,bl)
            if(!pts.all{it.x in 0f..w.toFloat() && it.y in 0f..h.toFloat()}) return null

            // Strong geometry checks prevent text/table lines from becoming a fake page.
            val area=abs(polygonArea(pts))/(w.toFloat()*h.toFloat())
            if(area < 0.55f) return null
            if(tl.x>w*.30f || tl.y>h*.30f || tr.x<w*.70f || tr.y>h*.30f ||
                br.x<w*.70f || br.y<h*.70f || bl.x>w*.30f || bl.y<h*.70f) return null
            val topLen=distance(tl,tr); val bottomLen=distance(bl,br)
            val leftLen=distance(tl,bl); val rightLen=distance(tr,br)
            if(topLen< w*.45f || bottomLen< w*.45f || leftLen<h*.45f || rightLen<h*.45f) return null
            if(max(topLen,bottomLen)/max(1f,min(topLen,bottomLen))>1.35f) return null
            if(max(leftLen,rightLen)/max(1f,min(leftLen,rightLen))>1.35f) return null
            if(angleDeg(tl,tr,br) !in 65f..115f || angleDeg(tr,br,bl) !in 65f..115f ||
                angleDeg(br,bl,tl) !in 65f..115f || angleDeg(bl,tl,tr) !in 65f..115f) return null

            return Quad(pts.map{PointF(it.x/scale,it.y/scale)}.toTypedArray(),
                max(topLen,bottomLen).roundToInt(),max(leftLen,rightLen).roundToInt())
        } finally {
            b.recycle()
        }
    }

    private fun polygonArea(p:Array<PointF>):Float {
        var s=0.0
        for(i in p.indices){val j=(i+1)%p.size;s += p[i].x.toDouble()*p[j].y - p[j].x.toDouble()*p[i].y}
        return (s/2.0).toFloat()
    }

    private fun angleDeg(a:PointF,b:PointF,c:PointF):Float {
        val ux=a.x-b.x; val uy=a.y-b.y; val vx=c.x-b.x; val vy=c.y-b.y
        val den=hypot(ux.toDouble(),uy.toDouble())*hypot(vx.toDouble(),vy.toDouble())
        if(den<1e-6)return 0f
        return Math.toDegrees(acos(((ux*vx+uy*vy)/den).coerceIn(-1.0,1.0))).toFloat()
    }

    private fun distance(a:PointF,b:PointF)=hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble()).toFloat()

    fun rotate(src:Bitmap):Bitmap {
        val m=Matrix().apply{postRotate(90f)}
        return Bitmap.createBitmap(src,0,0,src.width,src.height,m,true)
    }

    /** Grayscale/contrast processing with Otsu threshold for B&W; never introduces a color cast. */
    fun filter(src: Bitmap, mode: String): Bitmap {
        if (mode == "Color") return src.copy(Bitmap.Config.ARGB_8888, false)
        val w=src.width; val h=src.height
        val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val input=IntArray(w*h); val output=IntArray(w*h); val lum=IntArray(w*h); src.getPixels(input,0,w,0,0,w,h)
        val hist=IntArray(256)
        for(i in input.indices){
            val c=input[i]
            val v=(0.299f*Color.red(c)+0.587f*Color.green(c)+0.114f*Color.blue(c)).roundToInt().coerceIn(0,255)
            lum[i]=v; hist[v]++
        }
        var threshold=128
        if(mode=="B&W") threshold=otsu(hist,lum.size)
        for(i in lum.indices){
            var v=lum[i]
            if(mode=="B&W") v=if(v>=threshold)255 else 0
            else if(mode=="High Contrast") v=(((v-128)*1.20f)+128f).roundToInt().coerceIn(0,255)
            output[i]=Color.rgb(v,v,v)
        }
        out.setPixels(output,0,w,0,0,w,h); return out
    }

    private fun otsu(hist:IntArray,total:Int):Int {
        var sum=0.0
        for(i in 0..255) sum += i.toDouble()*hist[i]
        var sumB=0.0; var wB=0; var best=0.0; var threshold=128
        for(t in 0..255){
            wB += hist[t]; if(wB==0) continue
            val wF=total-wB; if(wF==0) break
            sumB += t.toDouble()*hist[t]
            val mB=sumB/wB; val mF=(sum-sumB)/wF
            val between=wB.toDouble()*wF*(mB-mF)*(mB-mF)
            if(between>best){best=between;threshold=t}
        }
        return threshold.coerceIn(50,205)
    }
}
