package fr.allyourtank.app

import android.content.Context

/**
 * Réglages saisis dans l'appli : compte Adafruit IO, noms des cuves et
 * échelle du grand cadran (de echelleMin à echelleMax °C).
 * Stockés dans la mémoire privée de l'appli (aucune autre appli n'y a accès).
 */
data class Reglages(
    val utilisateur: String,
    val cle: String,
    val noms: List<String>,
    val echelleMin: Double = ECHELLE_MIN_DEFAUT,
    val echelleMax: Double = ECHELLE_MAX_DEFAUT,
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
                echelleMin = p.getFloat("echelle_min", ECHELLE_MIN_DEFAUT.toFloat()).toDouble(),
                echelleMax = p.getFloat("echelle_max", ECHELLE_MAX_DEFAUT.toFloat()).toDouble(),
            )
        }

        fun ecrire(ctx: Context, r: Reglages) {
            ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().apply {
                putString("utilisateur", r.utilisateur.trim())
                putString("cle", r.cle.trim())
                r.noms.forEachIndexed { i, nom -> putString("nom${i + 1}", nom.trim().ifEmpty { "Cuve ${i + 1}" }) }
                putFloat("echelle_min", r.echelleMin.toFloat())
                putFloat("echelle_max", r.echelleMax.toFloat())
                apply()
            }
        }
    }
}

const val NB_CUVES = 3

/** Échelle du grand cadran par défaut, et ses limites acceptées. */
const val ECHELLE_MIN_DEFAUT = -5.0
const val ECHELLE_MAX_DEFAUT = 35.0
const val ECHELLE_LIMITE_BASSE = -20.0
const val ECHELLE_LIMITE_HAUTE = 60.0
const val ECHELLE_ECART_MINI = 5.0
