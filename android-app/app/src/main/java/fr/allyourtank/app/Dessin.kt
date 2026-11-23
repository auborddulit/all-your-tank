package fr.allyourtank.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews

/**
 * Dessine le widget à partir des dernières valeurs gardées en mémoire :
 * pour chaque cuve, un cadran à graduations, la température et le nom.
 * Les textes suivent le mode clair/sombre grâce à res/values(-night)/colors.xml ;
 * les cadrans et les couleurs d'état sont fournis dans les deux versions
 * (Android 12 et plus récent choisit tout seul ; avant, on prend le mode actuel).
 */
object Dessin {

    private val COLONNES = intArrayOf(R.id.col1, R.id.col2, R.id.col3)
    private val JAUGES = intArrayOf(R.id.jauge1, R.id.jauge2, R.id.jauge3)
    private val TEMPS = intArrayOf(R.id.temp1, R.id.temp2, R.id.temp3)
    private val NOMS = intArrayOf(R.id.nom1, R.id.nom2, R.id.nom3)

    /** Largeur des images de cadran, en pixels. */
    private const val LARGEUR_JAUGE = 240

    private fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, TankWidgetProvider::class.java))

    fun mettreAJourTous(ctx: Context) {
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, construire(ctx))
    }

    private fun construire(ctx: Context): RemoteViews {
        val vue = RemoteViews(ctx.packageName, R.layout.widget_cuves)
        val reglages = Reglages.lire(ctx)
        val feeds = Cache.feeds(ctx)
        val jour = Palette.pour(ctx, false)
        val nuit = Palette.pour(ctx, true)
        val recent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        for (i in 0 until NB_CUVES) {
            val e = EtatCuve.lire(feeds, i + 1)

            vue.setTextViewText(NOMS[i], reglages.noms[i])
            vue.setTextViewText(TEMPS[i], if (e.temp == null) "--" else Format.temp(e.temp) + "°")

            // Cadran à graduations, en version claire et sombre
            if (recent) {
                vue.setIcon(
                    JAUGES[i], "setImageIcon",
                    Icon.createWithBitmap(Jauge.image(LARGEUR_JAUGE, e, jour)),
                    Icon.createWithBitmap(Jauge.image(LARGEUR_JAUGE, e, nuit)),
                )
            } else {
                vue.setImageViewBitmap(JAUGES[i], Jauge.image(LARGEUR_JAUGE, e, Palette.actuelle(ctx)))
            }

            // Couleur de la température selon l'état (sinon : celle de la mise en page)
            when {
                e.ancienne -> couleur(ctx, vue, TEMPS[i], jour.attenue, nuit.attenue, recent)
                e.chaud -> couleur(ctx, vue, TEMPS[i], jour.alerte, nuit.alerte, recent)
                e.froid -> couleur(ctx, vue, TEMPS[i], jour.froid, nuit.froid, recent)
            }

            // Toucher une cuve : ouvrir l'appli sur cette cuve (elle relit tout de suite)
            vue.setOnClickPendingIntent(COLONNES[i], ouvrirAppli(ctx, i))
        }
        return vue
    }

    private fun ouvrirAppli(ctx: Context, cuve: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CUVE, cuve)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 10 + cuve, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun couleur(ctx: Context, vue: RemoteViews, id: Int, jour: Int, nuit: Int, recent: Boolean) {
        if (recent) {
            vue.setColorInt(id, "setTextColor", jour, nuit)
        } else {
            vue.setTextColor(id, if (Palette.estNuit(ctx)) nuit else jour)
        }
    }
}
