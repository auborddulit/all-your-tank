package fr.allyourtank.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Un préréglage : une cible et une fourchette prêtes à appliquer à une cuve. */
data class Prereglage(val nom: String, val cible: Double, val fourchette: Double) {
    val texte: String get() = "${Format.court(cible)} °C ±${Format.court(fourchette)}"
}

/** La liste des préréglages, gardée sur le téléphone et modifiable dans les réglages. */
object Prereglages {
    private const val FICHIER = "prereglages"

    val PAR_DEFAUT = listOf(
        Prereglage("Garde à froid", 2.0, 1.0),
        Prereglage("Ale", 19.0, 1.0),
        Prereglage("Repos diacétyle", 21.0, 1.0),
        Prereglage("Lager", 11.0, 1.0),
    )

    fun lire(ctx: Context): List<Prereglage> {
        val texte = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString("liste", null)
            ?: return PAR_DEFAUT
        return try {
            val json = JSONArray(texte)
            (0 until json.length()).map {
                val o = json.getJSONObject(it)
                Prereglage(o.getString("nom"), o.getDouble("cible"), o.getDouble("fourchette"))
            }
        } catch (e: Exception) {
            PAR_DEFAUT
        }
    }

    fun ecrire(ctx: Context, liste: List<Prereglage>) {
        val json = JSONArray()
        liste.forEach { json.put(JSONObject().put("nom", it.nom).put("cible", it.cible).put("fourchette", it.fourchette)) }
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString("liste", json.toString()).apply()
    }
}
