package ir.legions.overlay

import android.content.Context
import android.os.PowerManager
import org.json.JSONObject
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

/** One worker across Activity and Overlay. Results are tied to an immutable revision. */
object Session {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var state: JSONObject? = null; private set
    var revision = 0L; private set
    var analysis: JSONObject? = null; private set
    var busy = false; private set
    private var sourceFromCapture=false
    var message = "وضعیت بازی را وارد کنید"; private set
    val listeners = mutableSetOf<() -> Unit>()
    fun notifyChange() { listeners.toList().forEach { it() } }
    fun set(s: JSONObject, context: Context, fromCapture: Boolean=false) {
        sourceFromCapture=fromCapture
        CaptureRuntime.acceptedSequence=-1
        revision++; state = GameModel.clone(s); analysis = null; message = "وضعیت تغییر کرد؛ صحت آن را بررسی کنید"
        context.getSharedPreferences("game",0).edit().putString("state",s.toString()).apply(); notifyChange()
    }
    fun error(text: String) { analysis = null; message = text; notifyChange() }
    fun restore(context: Context) {
        if (state != null) return
        context.getSharedPreferences("game",0).getString("state",null)?.let {
            runCatching { state=JSONObject(it) }.onFailure { message="وضعیت ذخیره‌شده خوانده نشد" }
        }
    }
    fun stop() { revision++; analysis=null; message="تحلیل متوقف شد"; notifyChange() }
    fun analyze(context: Context) {
        if (busy) return
        if(sourceFromCapture && !CaptureRuntime.manualSnapshot && !CaptureRuntime.currentObservationIsAccepted()) {
            return error("تصویر تأییدشده دیگر تازه نیست؛ دوباره بازبینی کنید یا حالت دستی را صریحاً انتخاب کنید")
        }
        val snapshot=state?.let(GameModel::clone) ?: return error("ابتدا وضعیت بازی را وارد کنید")
        val pm=context.getSystemService(PowerManager::class.java)
        if (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return error("گوشی گرم است؛ پس از خنک‌شدن تحلیل کنید")
        val budget=if (pm.isPowerSaveMode || pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) 150 else 700
        val version=revision
        busy=true; analysis=null; message="در حال محاسبه…"; notifyChange()
        worker.execute {
            val r=runCatching { EngineBridge.call(JSONObject().put("op","analyze").put("state",snapshot).put("budget_ms",budget).put("max_depth",16)).getJSONObject("analysis") }
            main.post {
                busy=false
                if (revision == version) {
                    r.onSuccess { a -> analysis=a; message=describe(snapshot,a) }.onFailure { message=it.message ?: "خطای تحلیل" }
                }
                notifyChange()
            }
        }
    }
    fun describe(s: JSONObject, a: JSONObject): String {
        val m=a.optJSONObject("best_move") ?: return "بازی تمام شده است"
        val deck=s.getJSONArray(if (s.getInt("turn")==1) "blue" else "red")
        val card=deck.getJSONObject(m.getInt("card"))
        val label=GameModel.label(card).substringBefore('\n')
        val depth=a.getInt("completed_depth")
        return "کارت ${m.getInt("card")+1}: $label\nبالا ${card.getInt("top")}، راست ${card.getInt("right")}\nپایین ${card.getInt("bottom")}، چپ ${card.getInt("left")}\nردیف ${m.getInt("row")+1}، ستون ${m.getInt("col")+1}\nعمق $depth | ${a.getLong("elapsed_ms")} ms | ${a.getLong("nodes")} گره" +
            if (depth==0) "\nفرصت جست‌وجو کافی نبود؛ این فقط حرکت قانونی اضطراری است" else if (a.optBoolean("solved")) "\nتا پایان بازی بررسی شد" else "\nبهترین حرکت در عمق کامل‌شده؛ تضمین برد ندارد"
    }
}
