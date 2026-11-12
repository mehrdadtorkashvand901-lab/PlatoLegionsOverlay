package ir.legions.overlay

import org.json.JSONObject

object EngineBridge {
    private val loadError: String? = try { System.loadLibrary("legions_ai"); null } catch (e: UnsatisfiedLinkError) { e.message ?: "کتابخانه یافت نشد" }
    private external fun request(json: String): String
    fun call(input: JSONObject): JSONObject {
        check(loadError == null) { "کتابخانهٔ ARM64 بارگذاری نشد: $loadError" }
        val result = JSONObject(request(input.toString()))
        check(result.optBoolean("ok")) { result.optString("error", "خطای نامشخص موتور") }
        return result
    }
}
