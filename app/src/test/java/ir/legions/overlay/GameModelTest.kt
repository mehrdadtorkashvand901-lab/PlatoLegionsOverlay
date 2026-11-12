package ir.legions.overlay
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class GameModelTest {
    @Test fun manualCardsRetainBaseRanksAndOwnership() {
        val card=GameModel.card(7,2,listOf(7,3,3,3))
        assertEquals("Swarm",card.getString("name")); assertEquals(2,card.getInt("player"))
        assertTrue(runCatching { GameModel.card(0,1,listOf(0,3,3,3)) }.isFailure)
    }
    @Test fun boardEditsAndSnapshotCopiesAreIndependent() {
        val s=JSONObject("{\"board\":[[null,null,null,null,null],[null,null,null,null,null],[null,null,null,null,null],[null,null,null,null,null]]}")
        val t=GameModel.clone(s); GameModel.setBoard(t,0,0,GameModel.card(0,1,listOf(6,6,4,4)))
        assertNull(GameModel.boardCard(s,0,0)); assertEquals(6,GameModel.boardCard(t,0,0)!!.getInt("top"))
    }
}
