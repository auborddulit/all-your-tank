package fr.allyourtank.app

import android.content.Context
import android.content.res.Configuration

/**
 * Les couleurs « Ambre » (res/values/colors.xml pour le mode clair,
 * res/values-night/colors.xml pour le mode sombre), lues pour un mode donné.
 */
data class Palette(
    val fond: Int,
    val carte: Int,
    val encre: Int,
    val attenue: Int,
    val piste: Int,
    val bande: Int,
    val alerte: Int,
    val froid: Int,
    val puce: Int,
    val surEncre: Int,
) {
    companion object {
        fun estNuit(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        /** Les couleurs du mode actuel du téléphone. */
        fun actuelle(ctx: Context): Palette = pour(ctx, estNuit(ctx))

        /** Les couleurs du mode clair (nuit = false) ou sombre (nuit = true). */
        fun pour(ctx: Context, nuit: Boolean): Palette {
            val conf = Configuration(ctx.resources.configuration)
            conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (nuit) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            val c = ctx.createConfigurationContext(conf)
            return Palette(
                fond = c.getColor(R.color.fond),
                carte = c.getColor(R.color.carte),
                encre = c.getColor(R.color.encre),
                attenue = c.getColor(R.color.attenue),
                piste = c.getColor(R.color.piste),
                bande = c.getColor(R.color.bande),
                alerte = c.getColor(R.color.alerte),
                froid = c.getColor(R.color.froid),
                puce = c.getColor(R.color.puce),
                surEncre = c.getColor(R.color.sur_encre),
            )
        }
    }
}
