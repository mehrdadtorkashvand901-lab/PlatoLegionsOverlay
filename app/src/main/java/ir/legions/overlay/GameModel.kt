package ir.legions.overlay

import org.json.JSONArray
import org.json.JSONObject

object GameModel {
    val units = arrayOf("Warden", "Keeper", "Siren", "Saboteur", "Ravager", "Titan", "Slayer", "Swarm", "Lancer")
    val names = arrayOf("نگهبان", "شکارچی", "سایرن", "خرابکار", "غارتگر", "تایتان", "غول‌کش", "ازدحام", "نیزه‌دار")
    fun label(c: JSONObject): String {
        val i = units.indexOf(c.getString("name"))
        return "${if (i >= 0) names[i] else "؟"}\n${c.getInt("top")} ↑  ${c.getInt("right")} →\n${c.getInt("bottom")} ↓  ${c.getInt("left")} ←"
    }
    fun card(unit: Int, owner: Int, ranks: List<Int>): JSONObject {
        require(unit in units.indices && owner in 1..2 && ranks.size == 4 && ranks.all { it in 1..10 }) { "رتبه‌ها باید ۱ تا ۱۰ باشند" }
        return JSONObject().put("name",units[unit]).put("player",owner).put("top",ranks[0]).put("right",ranks[1]).put("bottom",ranks[2]).put("left",ranks[3])
    }
    fun clone(s: JSONObject) = JSONObject(s.toString())
    fun boardCard(s: JSONObject, y: Int, x: Int) = s.getJSONArray("board").getJSONArray(y).optJSONObject(x)
    fun setBoard(s: JSONObject, y: Int, x: Int, c: JSONObject?) { s.getJSONArray("board").getJSONArray(y).put(x,c ?: JSONObject.NULL) }
    fun removeAt(a: JSONArray, i: Int) { a.remove(i) }
}
