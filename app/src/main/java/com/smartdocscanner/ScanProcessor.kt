package com.smartdocscanner

import android.graphics.*
import java.io.File
import kotlin.math.*

/** Fast, conservative document processing. Detection runs on a down-scaled copy. */
object ScanProcessor {
    data class Quad(val p:Array<PointF>,val width:Int,val height:Int)
    fun decode(file:File):Bitmap?=BitmapFactory.decodeFile(file.absolutePath)

    fun autoCrop(src:Bitmap):Bitmap{
        val q=detectQuad(src) ?: return src.copy(Bitmap.Config.ARGB_8888,false)
        val tl=q.p[0];val tr=q.p[1];val br=q.p[2];val bl=q.p[3]
        val w=max(distance(tl,tr),distance(bl,br)).roundToInt().coerceAtLeast(1)
        val h=max(distance(tl,bl),distance(tr,br)).roundToInt().coerceAtLeast(1)
        val dst=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val m=Matrix()
        val sp=floatArrayOf(tl.x,tl.y,tr.x,tr.y,br.x,br.y,bl.x,bl.y);val dp=floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat())
        if(!m.setPolyToPoly(sp,0,dp,0,4)){dst.recycle();return src.copy(Bitmap.Config.ARGB_8888,false)}
        Canvas(dst).drawBitmap(src,m,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG));return dst
    }

    private fun detectQuad(src:Bitmap):Quad?{
        val maxSide=1000;val scale=min(1f,maxSide.toFloat()/max(src.width,src.height));val w=(src.width*scale).roundToInt().coerceAtLeast(20);val h=(src.height*scale).roundToInt().coerceAtLeast(20);val b=Bitmap.createScaledBitmap(src,w,h,true)
        try{
            val pix=IntArray(w*h);b.getPixels(pix,0,w,0,0,w,h)
            fun gray(x:Int,y:Int):Float{val c=pix[y*w+x];return .299f*Color.red(c)+.587f*Color.green(c)+.114f*Color.blue(c)}
            fun fit(ps:List<PointF>):Pair<Float,Float>?{if(ps.size<10)return null;var sx=0.0;var sy=0.0;var sxx=0.0;var sxy=0.0;ps.forEach{sx+=it.x;sy+=it.y;sxx+=it.x*it.x;sxy+=it.x*it.y};val n=ps.size.toDouble();val d=n*sxx-sx*sx;if(abs(d)<1e-6)return null;val a=((n*sxy-sx*sy)/d).toFloat();return a to ((sy-a*sx)/n).toFloat()}
            val top=mutableListOf<PointF>();val bottom=mutableListOf<PointF>();val left=mutableListOf<PointF>();val right=mutableListOf<PointF>()
            for(x in 8 until w-8 step max(3,w/120)){
                var bt=0f;var yt=1;for(y in 4 until max(5,h/3)){val g=abs(gray(x,y)-gray(x,y-1));if(g>bt){bt=g;yt=y}}
                var bb=0f;var yb=h-2;for(y in (h*2/3).coerceAtLeast(2) until h-2){val g=abs(gray(x,y)-gray(x,y-1));if(g>bb){bb=g;yb=y}}
                if(bt>18)top+=PointF(x.toFloat(),yt.toFloat());if(bb>18)bottom+=PointF(x.toFloat(),yb.toFloat())
            }
            for(y in 8 until h-8 step max(3,h/120)){
                var blv=0f;var xl=1;for(x in 4 until max(5,w/3)){val g=abs(gray(x,y)-gray(x-1,y));if(g>blv){blv=g;xl=x}}
                var brv=0f;var xr=w-2;for(x in (w*2/3).coerceAtLeast(2) until w-2){val g=abs(gray(x,y)-gray(x-1,y));if(g>brv){brv=g;xr=x}}
                if(blv>18)left+=PointF(xl.toFloat(),y.toFloat());if(brv>18)right+=PointF(xr.toFloat(),y.toFloat())
            }
            val t=fit(top)?:return null;val bo=fit(bottom)?:return null;val l=fit(left.map{PointF(it.y,it.x)})?:return null;val r=fit(right.map{PointF(it.y,it.x)})?:return null
            fun ix(a:Float,b0:Float,c:Float,d:Float):PointF?{val den=1f-c*a;if(abs(den)<.02f)return null;val x=(c*b0+d)/den;return PointF(x,a*x+b0)}
            val tl=ix(t.first,t.second,l.first,l.second)?:return null;val tr=ix(t.first,t.second,r.first,r.second)?:return null;val bl=ix(bo.first,bo.second,l.first,l.second)?:return null;val br=ix(bo.first,bo.second,r.first,r.second)?:return null;val pts=arrayOf(tl,tr,br,bl)
            if(!pts.all{it.x in 0f..w.toFloat()&&it.y in 0f..h.toFloat()})return null
            val area=abs(area(pts))/(w.toFloat()*h.toFloat());if(area<.42f)return null
            if(tl.x>w*.42f||tl.y>h*.42f||tr.x<w*.58f||tr.y>h*.42f||br.x<w*.58f||br.y<h*.58f||bl.x>w*.42f||bl.y<h*.58f)return null
            val tlx=distance(tl,tr);val blx=distance(bl,br);val ly=distance(tl,bl);val ry=distance(tr,br)
            if(tlx<w*.35f||blx<w*.35f||ly<h*.35f||ry<h*.35f)return null
            return Quad(pts.map{PointF(it.x/scale,it.y/scale)}.toTypedArray(),max(tlx,blx).roundToInt(),max(ly,ry).roundToInt())
        }finally{b.recycle()}
    }
    private fun area(p:Array<PointF>):Float{var s=0.0;for(i in p.indices){val j=(i+1)%p.size;s+=p[i].x.toDouble()*p[j].y-p[j].x.toDouble()*p[i].y};return(s/2).toFloat()}
    private fun distance(a:PointF,b:PointF)=hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble()).toFloat()
    fun rotate(src:Bitmap):Bitmap=Bitmap.createBitmap(src,0,0,src.width,src.height,Matrix().apply{postRotate(90f)},true)

    fun filter(src:Bitmap,mode:String):Bitmap{
        if(mode=="Color"||mode=="Original")return src.copy(Bitmap.Config.ARGB_8888,false)
        val w=src.width;val h=src.height;val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val input=IntArray(w*h);val output=IntArray(w*h);val lum=IntArray(w*h);src.getPixels(input,0,w,0,0,w,h);val hist=IntArray(256)
        for(i in input.indices){val c=input[i];val v=(.299f*Color.red(c)+.587f*Color.green(c)+.114f*Color.blue(c)).roundToInt().coerceIn(0,255);lum[i]=v;hist[v]++}
        val t=otsu(hist,lum.size)
        for(y in 0 until h)for(x in 0 until w){val i=y*w+x;var v=lum[i];when(mode){"B&W"->{v=if(localThreshold(lum,w,h,x,y,t)>=v)0 else 255};"High Contrast"->v=(((v-128)*1.28f)+128).roundToInt().coerceIn(0,255)};output[i]=Color.rgb(v,v,v)}
        out.setPixels(output,0,w,0,0,w,h);return out
    }
    private fun localThreshold(l:Int,w:Int,h:Int,x:Int,y:Int,global:Int):Int{var sum=0;var n=0;val r=12;for(yy=max(0,y-r)..min(h-1,y+r) step 3)for(xx in max(0,x-r)..min(w-1,x+r) step 3){sum+=l[yy*w+xx];n++};return ((sum/max(1,n))*0.92f).roundToInt().coerceIn(global-45,global+45)}
    private fun otsu(hist:IntArray,total:Int):Int{var sum=0.0;for(i in 0..255)sum+=i.toDouble()*hist[i];var sb=0.0;var wb=0;var best=0.0;var th=128;for(t in 0..255){wb+=hist[t];if(wb==0)continue;val wf=total-wb;if(wf==0)break;sb+=t.toDouble()*hist[t];val mb=sb/wb;val mf=(sum-sb)/wf;val v=wb.toDouble()*wf*(mb-mf)*(mb-mf);if(v>best){best=v;th=t}};return th.coerceIn(45,210)}
}
