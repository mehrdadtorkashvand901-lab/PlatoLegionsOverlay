package ir.legions.overlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import org.json.JSONObject

class CaptureService: Service() {
    private val main=Handler(Looper.getMainLooper())
    private var thread: HandlerThread?=null
    private var frameHandler: Handler?=null
    private var projection: MediaProjection?=null
    private var display: VirtualDisplay?=null
    private var reader: ImageReader?=null
    private var overlay: OverlayController?=null
    private var lastFrame=0L; private var sequence=0L
    private var lastState: String?=null; private var stable=0
    private var priorRevision=Long.MIN_VALUE
    private var calibration: JSONObject?=null
    private var regions: Map<String,Region> = emptyMap()
    private var samples: List<Sample> = emptyList()
    private var width=0; private var height=0
    @Volatile private var closed=false
    private val callback=object: MediaProjection.Callback() {
        override fun onStop() { main.post { stopSelf() } }
        override fun onCapturedContentResize(w: Int,h: Int) {
            if(w!=width || h!=height) main.post { Session.error("ابعاد ضبط تغییر کرد؛ ضبط را دوباره با کل صفحه شروع کنید"); stopSelf() }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="stop") { stopSelf(); return START_NOT_STICKY }
        if(projection!=null) return START_NOT_STICKY
        try {
            require(intent!=null && intent.getIntExtra("code",Activity.RESULT_CANCELED)==Activity.RESULT_OK) { "مجوز ضبط صفحه دریافت نشد" }
            val data=intent.getParcelableExtra("data",Intent::class.java) ?: error("دادهٔ رضایت ضبط موجود نیست")
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("capture","ضبط و تحلیل لژیون",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
            val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            val notification=Notification.Builder(this,"capture").setContentTitle("دستیار لژیون فعال است").setContentText("ضبط با اجازهٔ شما؛ برای توقف از این اعلان استفاده کنید")
                .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(null,"توقف",stop).build()).build()
            startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val bounds=getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds; width=bounds.width(); height=bounds.height()
            require(width>0 && height>0)
            thread=HandlerThread("LegionsFrames",Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
            val handler=Handler(thread!!.looper)
            frameHandler=handler
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data)
            projection!!.registerCallback(callback,main)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            val captureTick=object: Runnable {
                override fun run() {
                    if(closed) return
                    if(CaptureRuntime.paused) { lastState=null; stable=0; handler.postDelayed(this,1500); return }
                    // Hide our own windows before acquiring the compositor's next frame.
                    main.post {
                        if(closed) return@post
                        overlay?.hideForFrame(true)
                        main.postDelayed({ handler.post capture@{
                            if(closed) return@capture
                            try { captureImage(reader ?: return@capture) }
                            finally {
                                main.post { if(!closed) overlay?.hideForFrame(false) }
                                if(!closed) handler.postDelayed(this,1500)
                            }
                        } },150)
                    }
                }
            }
            handler.postDelayed(captureTick,500)
            display=projection!!.createVirtualDisplay("LegionsScreen",width,height,resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
            CaptureRuntime.running=true; CaptureRuntime.paused=false; CaptureRuntime.invalidate(); CaptureRuntime.status="ضبط فعال؛ کالیبراسیون و نمونه‌ها را تنظیم کنید"
            Session.restore(this); overlay=OverlayController(this).also { it.show() }
        } catch(e: Exception) { Session.error("ضبط شروع نشد: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    private fun captureImage(source: ImageReader) {
                val image=source.acquireLatestImage() ?: return
                try {
                    val now=SystemClock.elapsedRealtime()
                    if(CaptureRuntime.paused || now-lastFrame<1500) return
                    val pm=getSystemService(PowerManager::class.java)
                    if(pm.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE) {
                        main.post { CaptureRuntime.invalidate(); CaptureRuntime.status="گوشی گرم است؛ تشخیص موقتاً متوقف شد"; Session.stop() }; return
                    }
                    lastFrame=now
                    val plane=image.planes[0]; val paddedWidth=plane.rowStride/plane.pixelStride
                    val raw=Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888); raw.copyPixelsFromBuffer(plane.buffer)
                    val cropped=Bitmap.createBitmap(raw,0,0,width,height)
                    val targetWidth=minOf(width,720); val bitmap=Bitmap.createScaledBitmap(cropped,targetWidth,(height.toFloat()*targetWidth/width).toInt().coerceAtLeast(1),true)
                    if(cropped!==bitmap) cropped.recycle(); if(raw!==cropped && raw!==bitmap) raw.recycle()
                    sequence++; val frame=CapturedFrame(bitmap,width,height,sequence); CaptureRuntime.frame=frame
                    detect(frame)
                } catch(e: Exception) { main.post { CaptureRuntime.invalidate(); CaptureRuntime.status="خطای دریافت تصویر: ${e.message}"; Session.stop() } }
                finally { image.close() }
    }
    private fun detect(frame: CapturedFrame) {
        val store=VisionStore(this)
        val revision=store.revision()
        if(revision!=priorRevision) {
            priorRevision=revision; calibration=store.calibration(); regions=calibration?.let(store::regions) ?: emptyMap(); samples=store.samples(); lastState=null; stable=0
        }
        val c=calibration
        if(c==null || !Region.sameAspect(width,height,c.getInt("width"),c.getInt("height"))) return reject("کالیبراسیون این نسبت تصویر موجود نیست")
        if(!regions.keys.containsAll(VisionDecoder.requiredKeys)) return reject("همهٔ ناحیه‌های زمین، دسته‌ها و نوبت را کالیبره کنید")
        val labels=linkedMapOf<String,String>()
        for(key in VisionDecoder.requiredKeys) {
            val match=TemplateMatcher.classify(store.features(frame.bitmap,regions.getValue(key)),samples,VisionDecoder.kind(key))
            if(match.label==null) return reject("تشخیص نامطمئن در $key؛ بازبینی یا نمونهٔ بیشتر لازم است")
            labels[key]=match.label
        }
        val state=VisionDecoder.decode(labels)
        EngineBridge.call(JSONObject().put("op","validate").put("state",state))
        val serialized=state.toString()
        if(serialized==lastState) stable++ else { stable=1; lastState=serialized }
        if(stable<2) return reject("در انتظار دو تصویر یکسان و معتبر",false)
        main.post {
            if(CaptureRuntime.paused || !CaptureRuntime.running) return@post
            val changed=CaptureRuntime.candidate?.toString()!=serialized
            CaptureRuntime.candidate=state
            // Keep sequence for the same complete observed state; changed frames invalidate advice.
            if(changed) {
                CaptureRuntime.candidateSequence=frame.sequence; CaptureRuntime.acceptedSequence=-1
                if(!CaptureRuntime.manualSnapshot) Session.stop()
            }
            CaptureRuntime.status="وضعیت پایدار شناسایی شد؛ بازبینی در برنامه لازم است"
            overlay?.update()
        }
    }
    private fun reject(reason: String,resetStability: Boolean=true) {
        if(resetStability) { stable=0; lastState=null }
        main.post {
            if(closed) return@post
            val hadAdvice=CaptureRuntime.candidate!=null || Session.analysis!=null
            CaptureRuntime.invalidate(); CaptureRuntime.status=reason
            if(hadAdvice && !CaptureRuntime.manualSnapshot) Session.stop() else overlay?.update()
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); Session.error("جهت صفحه تغییر کرد؛ ضبط و کالیبراسیون را دوباره شروع کنید"); stopSelf()
    }
    override fun onDestroy() {
        closed=true; main.removeCallbacksAndMessages(null)
        frameHandler?.removeCallbacksAndMessages(null)
        CaptureRuntime.running=false; CaptureRuntime.paused=false; CaptureRuntime.invalidate(); CaptureRuntime.status="ضبط صفحه متوقف شد"
        val oldDisplay=display; val oldReader=reader; display=null; reader=null
        // Release images after any in-flight pixel copy finishes on the same worker.
        val cleanup=Runnable { oldDisplay?.release(); oldReader?.close(); thread?.quitSafely() }
        if(frameHandler==null) cleanup.run() else frameHandler!!.post(cleanup)
        projection?.unregisterCallback(callback); projection?.stop(); projection=null
        overlay?.close(); Session.stop(); stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
