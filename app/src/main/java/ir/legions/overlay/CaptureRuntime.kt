package ir.legions.overlay

import android.graphics.Bitmap
import org.json.JSONObject

data class CapturedFrame(val bitmap: Bitmap, val screenWidth: Int, val screenHeight: Int, val sequence: Long)
object CaptureRuntime {
    @Volatile var frame: CapturedFrame? = null
    @Volatile var candidate: JSONObject? = null
    @Volatile var candidateSequence: Long = -1
    @Volatile var acceptedSequence: Long = -1
    @Volatile var status: String = "ضبط صفحه خاموش است"
    @Volatile var running = false
    @Volatile var paused = false
    @Volatile var manualSnapshot = false
    fun invalidate() { candidate=null; candidateSequence=-1; acceptedSequence=-1 }
}
