package ir.legions.overlay

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private val refresh: () -> Unit = { render() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left,b.top,b.right,b.bottom); insets
        }
        Session.restore(this)
        if (Session.state == null) runCatching { newGame(arrayOf(0,1,2,3)) }.onFailure { Session.error(it.message ?: "خطای کتابخانه") }
        render()
    }
    override fun onStart() { super.onStart(); CaptureRuntime.appVisible=true; Session.listeners.add(refresh); render() }
    override fun onStop() {
        Session.listeners.remove(refresh); CaptureRuntime.appVisible=false
        if(CaptureRuntime.running) {
            CaptureRuntime.awaitingFreshFrame=true
            CaptureRuntime.status="در انتظار مشاهدهٔ تازهٔ بازی"
            Session.notifyChange()
        }
        super.onStop()
    }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private fun text(s: String, size: Float=16f)=TextView(this).apply { text=s; textSize=size; setPadding(dp(6),dp(6),dp(6),dp(6)); setTextColor(Color.rgb(23,46,48)) }
    private fun button(s: String, parent: LinearLayout=body, click: () -> Unit) = Button(this).apply { text=s; isAllCaps=false; setOnClickListener { safely(click) }; parent.addView(this) }
    private fun safely(f: () -> Unit) { runCatching(f).onFailure { AlertDialog.Builder(this).setTitle("نیاز به اصلاح").setMessage(it.message ?: "خطای نامشخص").setPositiveButton("باشه",null).show() } }
    private fun newGame(indices: Array<Int>) {
        val units=JSONArray().put(JSONArray().put(GameModel.units[indices[0]]).put(GameModel.units[indices[1]])).put(JSONArray().put(GameModel.units[indices[2]]).put(GameModel.units[indices[3]]))
        Session.set(EngineBridge.call(JSONObject().put("op","new").put("units",units)).getJSONObject("state"),this)
    }
    private fun selectGame() {
        val content=column(); val inputs=Array(4) { i ->
            content.addView(text("${if (i<2) "آبی" else "قرمز"}، مجموعهٔ ${i%2+1}"))
            spinner(GameModel.names.toList(),content).apply { setSelection(i) }
        }
        AlertDialog.Builder(this).setTitle("بازی جدید").setView(content).setPositiveButton("ایجاد") { _,_ -> safely { newGame(Array(4){inputs[it].selectedItemPosition}); render() } }.setNegativeButton("لغو",null).show()
    }
    private fun spinner(items: List<String>, parent: LinearLayout)=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items); parent.addView(this) }
    private fun render() {
        val scroll=ScrollView(this); body=column().apply { setPadding(dp(12),dp(12),dp(12),dp(24)); setBackgroundColor(Color.rgb(237,244,241)) }; scroll.addView(body); setContentView(scroll)
        body.addView(text("دستیار لژیون",25f)); body.addView(text("تحلیل آفلاین با موتور اصلی Rust • نسخهٔ آزمایشی"))
        body.addView(text(Session.message))
        button("بازی جدید و انتخاب دو مجموعهٔ هر بازیکن") { selectGame() }
        val s=Session.state ?: return
        val controls=LinearLayout(this); body.addView(controls)
        button(if (Session.busy) "در حال محاسبه…" else "پیشنهاد حرکت",controls) { Session.analyze(this) }.isEnabled=!Session.busy
        button("توقف",controls) { Session.stop() }
        button("اعتبارسنجی و امتیاز") {
            val r=EngineBridge.call(JSONObject().put("op","validate").put("state",s))
            AlertDialog.Builder(this).setMessage("امتیاز آبی و قرمز: ${r.getJSONArray("scores")}\n${if(r.optBoolean("finished")) "بازی پایان یافته" else "وضعیت از نظر ساختار معتبر است"}").setPositiveButton("باشه",null).show()
        }
        button("نوبت: ${if (s.getInt("turn")==1) "آبی" else "قرمز"} — تغییر دستی") { val t=GameModel.clone(s).put("turn",3-s.getInt("turn")); Session.set(t,this) }
        body.addView(text("زمین ۴×۵؛ ستون ۱ در سمت چپ، ردیف ۱ بالا. هر خانه را برای اصلاح لمس کنید."))
        val mark=Session.analysis?.optJSONObject("best_move")
        for (y in 0..3) {
            val row=LinearLayout(this).apply { layoutDirection=View.LAYOUT_DIRECTION_LTR }; body.addView(row)
            for (x in 0..4) {
                val c=GameModel.boardCard(s,y,x); val bomb=s.getJSONArray("bombs").getJSONArray(y).getInt(x)
                val b=Button(this).apply {
                    text="${y+1},${x+1}\n"+(c?.let(GameModel::label) ?: "خالی\nبمب $bomb")
                    textSize=10f; minWidth=0; minimumWidth=0; setPadding(0,0,0,0); isAllCaps=false
                    setTextColor(if(c?.optInt("player")==2) Color.rgb(154,34,39) else Color.rgb(25,68,142))
                    if (mark?.optInt("row")==y && mark.optInt("col")==x) setBackgroundColor(Color.rgb(255,211,91))
                    setOnClickListener { editCell(y,x) }
                }; row.addView(b,LinearLayout.LayoutParams(0,dp(100),1f))
            }
        }
        for (owner in 1..2) {
            val key=if(owner==1) "blue" else "red"; val deck=s.getJSONArray(key)
            body.addView(text("دستهٔ ${if(owner==1) "آبی" else "قرمز"} (${deck.length()} کارت)"))
            for(i in 0 until deck.length()) button("${i+1}. ${GameModel.label(deck.getJSONObject(i)).replace('\n',' ')}") { editCard(deck.getJSONObject(i),owner) { c ->
                val t=GameModel.clone(s); val a=t.getJSONArray(key); if(c==null) a.remove(i) else a.put(i,c); Session.set(t,this)
            } }
            button("افزودن کارت به دستهٔ ${if(owner==1) "آبی" else "قرمز"}") { editCard(null,owner) { c -> if(c!=null) { val t=GameModel.clone(s); t.getJSONArray(key).put(c); Session.set(t,this) } } }
        }
        button("ثبت حرکت انجام‌شده در بازی") { recordMove() }
        button("ویرایش یا کپی JSON کامل وضعیت") { jsonEditor() }
        addCaptureControls()
        button("مجوز MIT و مالکیت موتور") { AlertDialog.Builder(this).setMessage(assets.open("ENGINE_LICENSE.txt").bufferedReader().readText()).setPositiveButton("بستن",null).show() }
    }
    private fun editCell(y: Int,x: Int) {
        val s=Session.state ?: return
        AlertDialog.Builder(this).setTitle("خانهٔ ${y+1},${x+1}").setItems(arrayOf("ویرایش کارت / خالی‌کردن","تنظیم تعداد بمب")) { _,which -> safely {
            if(which==0) editCard(GameModel.boardCard(s,y,x),s.getInt("turn")) { c -> val t=GameModel.clone(s); GameModel.setBoard(t,y,x,c); if(c!=null) t.getJSONArray("bombs").getJSONArray(y).put(x,0); Session.set(t,this) }
            else { val input=EditText(this).apply { inputType=2; setText(s.getJSONArray("bombs").getJSONArray(y).getInt(x).toString()) }
                AlertDialog.Builder(this).setTitle("بمب، ۰ تا ۱۶").setView(input).setPositiveButton("ثبت") { _,_ -> safely { val n=input.text.toString().toInt(); require(n in 0..16); val t=GameModel.clone(s); t.getJSONArray("bombs").getJSONArray(y).put(x,n); Session.set(t,this) } }.setNegativeButton("لغو",null).show()
            }
        }}.show()
    }
    private fun editCard(old: JSONObject?, owner: Int, done: (JSONObject?) -> Unit) {
        val content=column(); val unit=spinner(GameModel.names.toList(),content); unit.setSelection(old?.let { GameModel.units.indexOf(it.getString("name")) }?.coerceAtLeast(0) ?: 0)
        val player=spinner(listOf("آبی","قرمز"),content); player.setSelection((old?.optInt("player") ?: owner)-1)
        val inputs=listOf("top","right","bottom","left").mapIndexed { i,key ->
            content.addView(text(listOf("بالا","راست","پایین","چپ")[i])); EditText(this).apply { inputType=2; setText((old?.optInt(key) ?: 5).toString()); content.addView(this) }
        }
        content.addView(text("اعداد پایه را وارد کنید؛ برای Swarm پاداش موقت متحدان را اضافه نکنید."))
        AlertDialog.Builder(this).setTitle("کارت و جهت‌ها").setView(ScrollView(this).apply { addView(content) }).setPositiveButton("ثبت") { _,_ -> safely { done(GameModel.card(unit.selectedItemPosition,player.selectedItemPosition+1,inputs.map { it.text.toString().toInt() })) } }.setNeutralButton("حذف / خالی") { _,_ -> done(null) }.setNegativeButton("لغو",null).show()
    }
    private fun recordMove() {
        val content=column(); val fields=listOf("شمارهٔ کارت (از ۱)","ردیف (۱ تا ۴)","ستون (۱ تا ۵)").map { title -> content.addView(text(title)); EditText(this).apply { inputType=2; setText("1"); content.addView(this) } }
        content.addView(text("پس از اجرای واقعی حرکت در Plato ثبت کنید. پیشنهاد خودکار روی زمین اعمال نمی‌شود."))
        AlertDialog.Builder(this).setTitle("ثبت حرکت واقعی").setView(content).setPositiveButton("ثبت") { _,_ -> safely {
            val m=JSONObject().put("card",fields[0].text.toString().toInt()-1).put("row",fields[1].text.toString().toInt()-1).put("col",fields[2].text.toString().toInt()-1)
            Session.set(EngineBridge.call(JSONObject().put("op","apply").put("state",Session.state).put("move",m)).getJSONObject("state"),this)
        } }.setNegativeButton("لغو",null).show()
    }
    private fun jsonEditor() {
        val input=EditText(this).apply { setText(Session.state?.toString(2)); textSize=12f; layoutDirection=View.LAYOUT_DIRECTION_LTR; setSelectAllOnFocus(false) }
        AlertDialog.Builder(this).setTitle("وضعیت کامل").setView(ScrollView(this).apply { addView(input) }).setPositiveButton("اعتبارسنجی و ذخیره") { _,_ -> safely {
            val s=JSONObject(input.text.toString()); EngineBridge.call(JSONObject().put("op","validate").put("state",s)); Session.set(s,this)
        } }.setNegativeButton("بستن",null).show()
    }
    private fun addCaptureControls() {
        body.addView(text("ضبط صفحه و پنجرهٔ شناور",20f))
        body.addView(text(CaptureRuntime.status+"\nتشخیص تصویر آزمایشی است و هر وضعیت نیاز به بازبینی دارد. برای Overlay دستی، پس از هر حرکت وضعیت را اصلاح کنید."))
        button("شروع ضبط با مجوز سیستم و نمایش Overlay") { startCapture() }
        button("توقف ضبط و بستن Overlay") { stopService(android.content.Intent(this,CaptureService::class.java)) }
        button("کالیبراسیون جدول، دسته‌ها و نشانگر نوبت") { VisionUi(this).calibrate() }
        button("آموزش نمونه از تصویر واقعی") { VisionUi(this).teach() }
        button("بازبینی وضعیت شناسایی‌شده") { VisionUi(this).review() }
        button("استفاده از وضعیت دستی فعلی در Overlay") {
            EngineBridge.call(JSONObject().put("op","validate").put("state",Session.state))
            CaptureRuntime.manualSnapshot=true; Session.stop()
            Toast.makeText(this,"حالت دستی: بعد از هر حرکت وضعیت را اصلاح کنید",Toast.LENGTH_LONG).show()
        }
        button("پاک‌کردن نمونه‌های تصویری") { VisionUi(this).clear() }
    }
    private fun startCapture() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")))
            Toast.makeText(this,"نمایش روی برنامه‌ها را مجاز کنید و سپس دوباره دکمهٔ شروع را بزنید",Toast.LENGTH_LONG).show(); return
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),101)
        val manager=getSystemService(android.media.projection.MediaProjectionManager::class.java)
        val request=if(android.os.Build.VERSION.SDK_INT>=34) manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
        startActivityForResult(request,102)
    }
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: android.content.Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==102) {
            if(resultCode==RESULT_OK && data!=null) safely {
                startForegroundService(android.content.Intent(this,CaptureService::class.java).putExtra("code",resultCode).putExtra("data",data))
            } else Session.error("ضبط صفحه مجاز نشد؛ ورود دستی همچنان قابل استفاده است")
        }
    }
}
