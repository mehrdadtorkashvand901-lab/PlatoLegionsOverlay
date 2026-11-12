package ir.legions.overlay

import kotlin.math.abs

data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(listOf(left,top,right,bottom).all { it.isFinite() && it in 0f..1f } && right>left && bottom>top) { "ناحیه باید داخل تصویر و دارای مساحت باشد" } }
    fun cell(row: Int,col: Int): Region {
        require(row in 0..3 && col in 0..4)
        val w=(right-left)/5; val h=(bottom-top)/4
        return Region(left+col*w,top+row*h,left+(col+1)*w,top+(row+1)*h)
    }
    fun inset(ratio: Float): Region {
        val w=(right-left)*ratio; val h=(bottom-top)*ratio
        return Region(left+w,top+h,right-w,bottom-h)
    }
    companion object {
        fun sameAspect(width: Int,height: Int,referenceWidth: Int,referenceHeight: Int): Boolean {
            if(minOf(width,height,referenceWidth,referenceHeight)<=0) return false
            return abs(width.toDouble()/height-referenceWidth.toDouble()/referenceHeight) < 0.005
        }
    }
}

data class Sample(val kind: String, val label: String, val features: FloatArray)
data class Match(val label: String?, val error: Float, val margin: Float)
object TemplateMatcher {
    /** Scores are distances, never probabilities. Reject close competing labels. */
    fun classify(features: FloatArray, samples: List<Sample>, kind: String, maxError: Float=0.035f, minMargin: Float=0.012f): Match {
        val candidates=samples.filter { it.kind==kind && it.features.size==features.size }
            .groupBy { it.label }.mapValues { (_,group) -> group.minOf { sample ->
                features.indices.sumOf { i -> kotlin.math.abs(features[i]-sample.features[i]).toDouble() }.toFloat()/features.size
            }}.entries.sortedBy { it.value }
        val best=candidates.firstOrNull() ?: return Match(null,Float.POSITIVE_INFINITY,0f)
        // A single class cannot demonstrate distinction from an unknown class.
        if(candidates.size<2) return Match(null,best.value,0f)
        val margin=candidates[1].value-best.value
        return Match(if(best.value<=maxError && margin>=minMargin) best.key else null,best.value,margin)
    }
}
