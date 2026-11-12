package ir.legions.overlay

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

class VisionStore(private val context: Context) {
    private val prefs=context.getSharedPreferences("vision",0)
    fun calibration(): JSONObject? = prefs.getString("calibration",null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun revision()=prefs.getLong("revision",0)
    fun saveCalibration(c: JSONObject) { prefs.edit().putString("calibration",c.toString()).putLong("revision",System.nanoTime()).apply() }
    fun regions(c: JSONObject): Map<String,Region> {
        val result=linkedMapOf<String,Region>(); val r=c.getJSONObject("regions")
        r.keys().forEach { key -> val a=r.getJSONArray(key); result[key]=Region(a.getDouble(0).toFloat(),a.getDouble(1).toFloat(),a.getDouble(2).toFloat(),a.getDouble(3).toFloat()) }
        return result
    }
    fun regionArray(r: Region)=JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom)
    fun samples(): List<Sample> {
        val data=JSONArray(prefs.getString("samples","[]")); return (0 until data.length()).map { i ->
            val a=data.getJSONObject(i); val f=a.getJSONArray("features")
            Sample(a.getString("kind"),a.getString("label"),FloatArray(f.length()){f.getDouble(it).toFloat()})
        }
    }
    fun addSample(sample: Sample) {
        val data=JSONArray(prefs.getString("samples","[]"))
        require(data.length()<256) { "حداکثر ۲۵۶ نمونه؛ ابتدا نمونه‌های قبلی را پاک کنید" }
        data.put(JSONObject().put("kind",sample.kind).put("label",sample.label).put("features",JSONArray(sample.features.toList())))
        prefs.edit().putString("samples",data.toString()).putLong("revision",System.nanoTime()).apply()
    }
    fun clearSamples() { prefs.edit().remove("samples").putLong("revision",System.nanoTime()).apply() }
    fun features(bitmap: Bitmap,r: Region): FloatArray {
        val region=r.inset(0.06f); val result=FloatArray(32*32*3); var i=0
        for(y in 0 until 32) for(x in 0 until 32) {
            val px=((region.left+(x+0.5f)/32*(region.right-region.left))*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
            val py=((region.top+(y+0.5f)/32*(region.bottom-region.top))*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
            val rgb=bitmap.getPixel(px,py)
            result[i++]=((rgb shr 16) and 255)/255f; result[i++]=((rgb shr 8) and 255)/255f; result[i++]=(rgb and 255)/255f
        }
        return result
    }
}

object VisionDecoder {
    fun kind(key: String)=when { key.startsWith("b_") -> "cell"; key.startsWith("blue_") -> "hand_blue"; key.startsWith("red_") -> "hand_red"; key=="turn" -> "turn"; else -> error("نام ناحیه نامعتبر است") }
    val requiredKeys=(0..3).flatMap { y -> (0..4).map { x -> "b_${y}_$x" } }+(0..7).map { "blue_$it" }+(0..7).map { "red_$it" }+"turn"
    fun decode(labels: Map<String,String>): JSONObject {
        require(labels.keys.containsAll(requiredKeys)) { "زمین، ۱۶ جایگاه دسته و نشانگر نوبت باید همگی تشخیص داده شوند" }
        val board=JSONArray(); val bombs=JSONArray()
        for(y in 0..3) {
            val row=JSONArray(); val br=JSONArray()
            for(x in 0..4) {
                val v=JSONObject(labels.getValue("b_${y}_$x")); val card=v.optJSONObject("card"); val bomb=v.getInt("bomb")
                require(bomb in 0..16 && (card==null || bomb==0)) { "بمب یا کارت ناحیه نامعتبر است" }
                row.put(card ?: JSONObject.NULL); br.put(bomb)
            }; board.put(row); bombs.put(br)
        }
        val s=JSONObject().put("board",board).put("bombs",bombs)
        for(key in listOf("blue","red")) {
            val a=JSONArray()
            for(i in 0..7) { val c=JSONObject(labels.getValue("${key}_$i")).optJSONObject("card"); if(c!=null) a.put(c) }
            s.put(key,a)
        }
        val turn=JSONObject(labels.getValue("turn")).getInt("turn"); require(turn in 1..2)
        return s.put("turn",turn)
    }
}
