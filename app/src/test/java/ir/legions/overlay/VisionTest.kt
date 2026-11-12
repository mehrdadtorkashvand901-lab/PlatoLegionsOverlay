package ir.legions.overlay

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class VisionTest {
    @Test fun boardCoordinatesAndAspectAreConsistent() {
        val r=Region(.1f,.2f,.9f,.8f).cell(3,4)
        assertEquals(.74f,r.left,.0001f); assertEquals(.65f,r.top,.0001f)
        assertTrue(Region.sameAspect(1080,2400,720,1600)); assertFalse(Region.sameAspect(2400,1080,720,1600))
    }
    @Test fun classifierRejectsUnknownAmbiguousAndSingleClass() {
        val a=Sample("cell","A",floatArrayOf(.1f,.1f)); val b=Sample("cell","B",floatArrayOf(.8f,.8f))
        assertEquals("A",TemplateMatcher.classify(floatArrayOf(.1f,.1f),listOf(a,b),"cell").label)
        assertNull(TemplateMatcher.classify(floatArrayOf(.5f,.5f),listOf(a,b),"cell").label)
        assertNull(TemplateMatcher.classify(floatArrayOf(.1f,.1f),listOf(a),"cell").label)
        assertNull(TemplateMatcher.classify(floatArrayOf(.1f,.1f),listOf(a,Sample("cell","C",floatArrayOf(.101f,.101f))),"cell").label)
    }
    @Test fun decoderRequiresAllBoardDeckAndTurnObservations() {
        runCatching { VisionDecoder.decode(emptyMap()) }.onSuccess { fail("must reject partial recognition") }
        val labels=VisionDecoder.requiredKeys.associateWith { key ->
            if(key=="turn") "{\"turn\":1}" else "{\"card\":null,\"bomb\":0}"
        }.toMutableMap()
        val s=VisionDecoder.decode(labels)
        assertEquals(4,s.getJSONArray("board").length()); assertEquals(0,s.getJSONArray("blue").length())
        // Native validation must reject this structurally decoded but incomplete 0-card game.
        assertEquals(1,s.getInt("turn"))
        labels["turn"]="{\"turn\":0}"; assertTrue(runCatching { VisionDecoder.decode(labels) }.isFailure)
    }
    @Test fun decoderRetainsBothCompleteDecksAndOwnership() {
        val labels=VisionDecoder.requiredKeys.associateWith { key ->
            when {
                key=="turn" -> "{\"turn\":1}"
                key.startsWith("blue_") -> JSONObject().put("card",GameModel.card(0,1,listOf(6,6,4,4))).put("bomb",0).toString()
                key.startsWith("red_") -> JSONObject().put("card",GameModel.card(1,2,listOf(9,5,1,5))).put("bomb",0).toString()
                else -> "{\"card\":null,\"bomb\":0}"
            }
        }
        val s=VisionDecoder.decode(labels)
        assertEquals(8,s.getJSONArray("blue").length()); assertEquals(8,s.getJSONArray("red").length())
        assertEquals(2,s.getJSONArray("red").getJSONObject(0).getInt("player"))
        assertEquals(9,s.getJSONArray("red").getJSONObject(0).getInt("top"))
    }
}
