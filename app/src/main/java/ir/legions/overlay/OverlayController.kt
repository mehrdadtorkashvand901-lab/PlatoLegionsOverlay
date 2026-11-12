package ir.legions.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.Settings
import kotlin.math.abs

class OverlayController(private val service: CaptureService) {
    private val wm=service.getSystemService(WindowManager::class.java)
    private var panel: LinearLayout?=null
    private var indicator: View?=null
    private var params: WindowManager.LayoutParams?=null
    private var summary: TextView?=null
    private var expanded=true
    private val listener: () -> Unit={ update() }
    private fun dp(n: Int)=(n*service.resources.displayMetrics.density).toInt()
    fun show() {
        if(!Settings.canDrawOverlays(service)) return
        val root=LinearLayout(service).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(6),dp(6),dp(6),dp(6)); setBackgroundColor(Color.rgb(237,244,241))
        }
        val handle=TextView(service).apply { text="لژیون  ↕  لمس برای کوچک‌کردن"; textSize=14f; setTextColor(Color.BLACK); setPadding(dp(4),dp(10),dp(4),dp(10)) }
        root.addView(handle)
        val details=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL }; root.addView(details)
        summary=TextView(service).apply { textSize=13f; setTextColor(Color.BLACK) }; details.addView(summary)
        fun action(title: String,f: () -> Unit) { details.addView(Button(service).apply { text=title; textSize=12f; isAllCaps=false; setOnClickListener { f() } }) }
        action("تحلیل وضعیت تأییدشده") {
            val fresh=CaptureRuntime.manualSnapshot || CaptureRuntime.currentObservationIsAccepted()
            if(!fresh) Session.error("ابتدا وضعیت شناسایی‌شده را در برنامه بازبینی و تأیید کنید") else Session.analyze(service)
        }
        action("مکث / ادامهٔ ضبط و تشخیص") {
            CaptureRuntime.paused=!CaptureRuntime.paused; CaptureRuntime.invalidate(); Session.stop(); update()
        }
        action("بازبینی در برنامه") { service.startActivity(Intent(service,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        action("توقف و بستن پنجره") { service.stopSelf() }
        val metrics=wm.currentWindowMetrics.bounds
        val saved=service.getSharedPreferences("overlay",0)
        params=WindowManager.LayoutParams(dp(225),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.LEFT; x=saved.getInt("x",0).coerceIn(0,(metrics.width()-dp(225)).coerceAtLeast(0)); y=saved.getInt("y",dp(60)).coerceIn(0,(metrics.height()-dp(80)).coerceAtLeast(0))
        }
        var downX=0f; var downY=0f; var startX=0; var startY=0
        handle.setOnTouchListener { _,e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=params!!.x; startY=params!!.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x=(startX+(e.rawX-downX).toInt()).coerceIn(0,(metrics.width()-root.width).coerceAtLeast(0))
                    params!!.y=(startY+(e.rawY-downY).toInt()).coerceIn(0,(metrics.height()-root.height).coerceAtLeast(0))
                    wm.updateViewLayout(root,params); true
                }
                MotionEvent.ACTION_UP -> {
                    handle.performClick()
                    if(abs(e.rawX-downX)+abs(e.rawY-downY)<dp(8)) {
                        expanded=!expanded; details.visibility=if(expanded) View.VISIBLE else View.GONE
                        handle.text=if(expanded) "لژیون  ↕  لمس برای کوچک‌کردن" else "لژیون  ⤢"
                        params!!.width=dp(if(expanded)225 else 90); wm.updateViewLayout(root,params)
                    }
                    saved.edit().putInt("x",params!!.x).putInt("y",params!!.y).apply(); true
                }
                else -> false
            }
        }
        panel=root
        wm.addView(root,params)
        Session.listeners.add(listener); update()
    }
    fun update() {
        val fresh=CaptureRuntime.manualSnapshot || CaptureRuntime.currentObservationIsAccepted()
        summary?.text="${CaptureRuntime.status}\n${if(CaptureRuntime.paused) "مکث" else if(fresh) Session.message else "برای تحلیل، وضعیت را بازبینی کنید"}"
        clearIndicator()
        if(!fresh) return
        val m=Session.analysis?.optJSONObject("best_move") ?: return
        val frame=CaptureRuntime.frame ?: return
        val c=VisionStore(service).calibration() ?: return
        if(!Region.sameAspect(frame.screenWidth,frame.screenHeight,c.getInt("width"),c.getInt("height"))) return
        val region=VisionStore(service).regions(c)["b_${m.getInt("row")}_${m.getInt("col")}"] ?: return
        val screen=wm.currentWindowMetrics.bounds
        if(screen.width()!=frame.screenWidth || screen.height()!=frame.screenHeight) return
        val view=object: View(service) {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(255,190,0); style=Paint.Style.STROKE; strokeWidth=dp(4).toFloat() }
            override fun onDraw(canvas: Canvas) { canvas.drawRect(dp(3).toFloat(),dp(3).toFloat(),width-dp(3).toFloat(),height-dp(3).toFloat(),paint) }
        }
        val p=WindowManager.LayoutParams(((region.right-region.left)*screen.width()).toInt().coerceAtLeast(1),((region.bottom-region.top)*screen.height()).toInt().coerceAtLeast(1),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.LEFT; x=(region.left*screen.width()).toInt(); y=(region.top*screen.height()).toInt(); alpha=.65f
        }
        // Small non-touchable window; alpha stays below Android's obscuring opacity limit.
        indicator=view; wm.addView(view,p)
    }
    fun hideForFrame(hidden: Boolean) { panel?.visibility=if(hidden) View.INVISIBLE else View.VISIBLE; indicator?.visibility=if(hidden) View.INVISIBLE else View.VISIBLE }
    private fun clearIndicator() { indicator?.let { runCatching { wm.removeView(it) } }; indicator=null }
    fun close() { Session.listeners.remove(listener); clearIndicator(); panel?.let { runCatching { wm.removeView(it) } }; panel=null }
}
