package ir.legions.overlay

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class RegionPicker(context: Context,private val bitmap: Bitmap): View(context) {
    var selected: Region?=null
    private var start: PointF?=null
    private val bounds=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.YELLOW; style=Paint.Style.STROKE; strokeWidth=4f }
    override fun onDraw(canvas: Canvas) {
        val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        bounds.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
        canvas.drawBitmap(bitmap,null,bounds,null)
        selected?.let { canvas.drawRect(bounds.left+it.left*w,bounds.top+it.top*h,bounds.left+it.right*w,bounds.top+it.bottom*h,paint) }
        start?.let { canvas.drawCircle(bounds.left+it.x*w,bounds.top+it.y*h,8f,paint) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action==MotionEvent.ACTION_UP && bounds.contains(event.x,event.y)) {
            val p=PointF((event.x-bounds.left)/bounds.width(),(event.y-bounds.top)/bounds.height())
            val a=start
            if(a==null) { start=p; selected=null } else {
                selected=runCatching { Region(minOf(a.x,p.x),minOf(a.y,p.y),maxOf(a.x,p.x),maxOf(a.y,p.y)) }.getOrNull(); start=null
            }
            invalidate(); performClick(); return true
        }
        return event.action==MotionEvent.ACTION_DOWN
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
