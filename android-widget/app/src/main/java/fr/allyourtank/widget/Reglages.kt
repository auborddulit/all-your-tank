package fr.allyourtank.widget

import android.content.Context

/**
 * Réglages saisis dans l'appli : compte Adafruit IO et noms des cuves.
 * Stockés dans la mémoire privée de l'appli (aucune autre appli n'y a accès).
 */
data class Reglages(
    val utilisateur: String,
    val cle: String,
    val noms: List<String>,
) {
    val complets: Boolean get() = utilisateur.isNotBlank() && cle.isNotBlank()

    companion object {
        private const val FICHIER = "reglages"

        fun lire(ctx: Context): Reglages {
            val p = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            return Reglages(
                utilisateur = p.getString("utilisateur", "") ?: "",
                cle = p.getString("cle", "") ?: "",
                noms = (1..NB_CUVES).map { p.getString("nom$it", null) ?: "Cuve $it" },
            )
        }

        fun ecrire(ctx: Context, r: Reglages) {
            ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().apply {
                putString("utilisateur", r.utilisateur.trim())
                putString("cle", r.cle.trim())
                r.noms.forEachIndexed { i, nom -> putString("nom${i + 1}", nom.trim().ifEmpty { "Cuve ${i + 1}" }) }
                apply()
            }
        }
    }
}

const val NB_CUVES = 3
