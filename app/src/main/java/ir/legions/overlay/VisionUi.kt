package ir.legions.overlay

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class VisionUi(private val activity: Activity) {
    private val store=VisionStore(activity)
    private fun error(t: Throwable) { AlertDialog.Builder(activity).setTitle("نیاز به اصلاح").setMessage(t.message ?: "خطای نامشخص").setPositiveButton("باشه",null).show() }
    private fun safe(f: () -> Unit) { runCatching(f).onFailure(::error) }
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL; setPadding(12,12,12,12) }
    private fun note(parent: LinearLayout,t: String) { parent.addView(TextView(activity).apply { text=t; textSize=14f; setPadding(6,6,6,6) }) }
    private fun spinner(parent: LinearLayout,items: List<String>)=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,items); parent.addView(this) }
    fun calibrate() = safe {
        val frame=CaptureRuntime.frame ?: error("ابتدا در صفحهٔ بازی ضبط را شروع کنید و سپس به برنامه برگردید")
        val body=column()
        note(body,"روی این تصویر ثابت، دو گوشهٔ بیرونی جدول را لمس کنید. برای دسته‌ها، ۸ جایگاه هر بازیکن و نشانگر نوبت را جداگانه تنظیم کنید. جایگاه خالی نیز لازم است. تغییر چیدمان بازی نیازمند کالیبراسیون تازه است.")
        val keys=listOf("board")+VisionDecoder.requiredKeys.filter { !it.startsWith("b_") }
        val choice=spinner(body,listOf("کل جدول ۴×۵")+keys.drop(1).map { displayKey(it) })
        val picker=RegionPicker(activity,frame.bitmap)
        body.addView(picker,LinearLayout.LayoutParams(-1,(activity.resources.displayMetrics.heightPixels*.5f).toInt()))
        body.addView(Button(activity).apply {
            text="ذخیرهٔ ناحیه و ادامه"; setOnClickListener { safe {
                val r=picker.selected ?: error("دو گوشهٔ ناحیه را روی تصویر لمس کنید")
                val previous=store.calibration()
                val c=if(previous!=null && Region.sameAspect(frame.screenWidth,frame.screenHeight,previous.getInt("width"),previous.getInt("height"))) previous
                    else JSONObject().put("width",frame.screenWidth).put("height",frame.screenHeight).put("regions",JSONObject())
                val regions=c.getJSONObject("regions"); val key=keys[choice.selectedItemPosition]
                if(key=="board") for(y in 0..3) for(x in 0..4) regions.put("b_${y}_$x",store.regionArray(r.cell(y,x)))
                else regions.put(key,store.regionArray(r))
                store.saveCalibration(c); picker.selected=null; picker.invalidate()
                Toast.makeText(activity,"ناحیه ذخیره شد",Toast.LENGTH_SHORT).show()
                if(choice.selectedItemPosition+1<keys.size) choice.setSelection(choice.selectedItemPosition+1)
            }}
        })
        AlertDialog.Builder(activity).setTitle("کالیبراسیون با تصویر ثابت").setView(ScrollView(activity).apply { addView(body) }).setPositiveButton("بستن",null).show()
    }
    private fun displayKey(key: String): String=when {
        key.startsWith("blue_") -> "جایگاه ${key.substringAfter('_').toInt()+1} دستهٔ آبی"
        key.startsWith("red_") -> "جایگاه ${key.substringAfter('_').toInt()+1} دستهٔ قرمز"
        key=="turn" -> "نشانگر نوبت"
        else -> { val parts=key.split('_'); "خانهٔ ${parts[1].toInt()+1},${parts[2].toInt()+1}" }
    }
    fun teach() = safe {
        val frame=CaptureRuntime.frame ?: error("تصویری دریافت نشده است")
        val c=store.calibration() ?: error("ابتدا ناحیه‌ها را کالیبره کنید")
        require(Region.sameAspect(frame.screenWidth,frame.screenHeight,c.getInt("width"),c.getInt("height"))) { "نسبت تصویر با کالیبراسیون سازگار نیست" }
        val regions=store.regions(c); val keys=VisionDecoder.requiredKeys.filter { regions.containsKey(it) }
        require(keys.isNotEmpty()) { "هیچ ناحیه‌ای تنظیم نشده است" }
        val body=column(); note(body,"نمونه از همین تصویر ثابت ذخیره می‌شود. برای هر حالت کارت، جهت، مالک، رتبه و بمب نمونهٔ جداگانه لازم است. دست‌کم دو برچسب متفاوت برای هر گروه لازم است. این روش هنوز روی تصاویر واقعی ارزیابی نشده است.")
        val image=ImageView(activity).apply { setImageBitmap(frame.bitmap); adjustViewBounds=true }; body.addView(image,LinearLayout.LayoutParams(-1,(activity.resources.displayMetrics.heightPixels*.35f).toInt()))
        val region=spinner(body,keys.map(::displayKey))
        note(body,"نوع برچسب")
        val mode=spinner(body,listOf("کارت","خالی / بمب","نوبت آبی","نوبت قرمز"))
        val unit=spinner(body,GameModel.names.toList()); val owner=spinner(body,listOf("آبی","قرمز"))
        val ranks=listOf("بالا","راست","پایین","چپ").map { key -> note(body,key); EditText(activity).apply { inputType=2; setText("5"); body.addView(this) } }
        note(body,"بمب خانهٔ خالی (۰ تا ۱۶)")
        val bomb=EditText(activity).apply { inputType=2; setText("0"); body.addView(this) }
        note(body,"رتبه‌های پایهٔ Swarm را بدون پاداش موقت وارد کنید. وضعیتِ اشباع‌شده روی A ممکن است رتبهٔ پایه را پنهان کند و باید دستی تصحیح شود.")
        AlertDialog.Builder(activity).setTitle("آموزش نمونهٔ آفلاین").setView(ScrollView(activity).apply { addView(body) }).setPositiveButton("ذخیرهٔ نمونه") { _,_ -> safe {
            val key=keys[region.selectedItemPosition]; val kind=VisionDecoder.kind(key)
            val label=when(mode.selectedItemPosition) {
                0 -> {
                    require(kind!="turn") { "برای نشانگر، برچسب نوبت را انتخاب کنید" }
                    val p=owner.selectedItemPosition+1
                    require(kind!="hand_blue" || p==1); require(kind!="hand_red" || p==2)
                    JSONObject().put("card",GameModel.card(unit.selectedItemPosition,p,ranks.map { it.text.toString().toInt() })).put("bomb",0)
                }
                1 -> { require(kind!="turn"); val n=bomb.text.toString().toInt(); require(n in 0..16 && (kind=="cell" || n==0)); JSONObject().put("card",JSONObject.NULL).put("bomb",n) }
                else -> { require(kind=="turn") { "این برچسب فقط برای نشانگر نوبت است" }; JSONObject().put("turn",mode.selectedItemPosition-1) }
            }
            store.addSample(Sample(kind,label.toString(),store.features(frame.bitmap,regions.getValue(key))))
            Toast.makeText(activity,"نمونه ذخیره شد؛ تأیید دقت هنوز لازم است",Toast.LENGTH_LONG).show()
        }}.setNegativeButton("لغو",null).show()
    }
    fun review()=safe {
        val s=CaptureRuntime.candidate?.let(GameModel::clone) ?: error("وضعیت کامل و پایدار شناسایی نشده است: ${CaptureRuntime.status}")
        val sequence=CaptureRuntime.candidateSequence
        val text=TextView(activity).apply { this.text="نسخهٔ اولیه نیازمند بازبینی هر وضعیت است. کارت‌ها، رتبه‌های پایه، مالکیت، بمب‌ها و نوبت را با بازی تطبیق دهید.\n\n"+s.toString(2); textSize=12f }
        AlertDialog.Builder(activity).setTitle("بازبینی تشخیص").setView(ScrollView(activity).apply { addView(text) }).setPositiveButton("تأیید و ورود به ویرایشگر") { _,_ -> safe {
            require(CaptureRuntime.candidateSequence==sequence && CaptureRuntime.candidate!=null) { "تصویر تغییر کرده است؛ دوباره بازبینی کنید" }
            EngineBridge.call(JSONObject().put("op","validate").put("state",s))
            Session.set(s,activity,fromCapture=true); CaptureRuntime.acceptedSequence=sequence; CaptureRuntime.manualSnapshot=false
        }}.setNegativeButton("لغو",null).show()
    }
    fun clear() { AlertDialog.Builder(activity).setMessage("همهٔ نمونه‌های تصویری محلی پاک شوند؟").setPositiveButton("پاک‌کردن") { _,_ -> store.clearSamples(); CaptureRuntime.invalidate(); Session.stop() }.setNegativeButton("لغو",null).show() }
}
