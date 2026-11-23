package fr.allyourtank.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Dessine le widget à partir des dernières valeurs gardées en mémoire. */
object Dessin {

    /** Au-delà de ce délai sans nouvelle mesure, la cuve est grisée. */
    private const val MINUTES_ANCIENNE = 10L

    private val LIGNES = intArrayOf(R.id.ligne1, R.id.ligne2, R.id.ligne3)
    private val NOMS = intArrayOf(R.id.nom1, R.id.nom2, R.id.nom3)
    private val TEMPS = intArrayOf(R.id.temp1, R.id.temp2, R.id.temp3)
    private val CIBLES = intArrayOf(R.id.cible1, R.id.cible2, R.id.cible3)

    private fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, TankWidgetProvider::class.java))

    fun mettreAJourTous(ctx: Context) {
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, construire(ctx))
    }

    /** Petit signe « mise à jour en cours » dans le coin du widget. */
    fun afficherChargement(ctx: Context) {
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        val vue = RemoteViews(ctx.packageName, R.layout.widget_cuves)
        vue.setTextViewText(R.id.heure, "mise à jour…")
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(ids, vue)
    }

    private fun construire(ctx: Context): RemoteViews {
        val vue = RemoteViews(ctx.packageName, R.layout.widget_cuves)
        val reglages = Reglages.lire(ctx)
        val feeds = Cache.feeds(ctx)

        // En-tête : heure de la dernière lecture, ou message d'erreur
        val erreur = Cache.erreur(ctx)
        val heure = Cache.heure(ctx)
        vue.setTextViewText(
            R.id.heure,
            when {
                erreur != null -> "⚠ $erreur"
                heure > 0 -> "à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(heure))
                else -> ""
            },
        )

        for (i in 0 until NB_CUVES) {
            val n = i + 1
            val mesure = feeds["cuve$n"]
            val temp = mesure?.nombre
            val cible = feeds["cible-cuve$n"]?.nombre
            val fourchette = feeds["fourchette-cuve$n"]?.nombre
            val minutes = minutesDepuis(mesure?.date)

            vue.setTextViewText(NOMS[i], reglages.noms[i])
            vue.setTextViewText(TEMPS[i], if (temp == null) "--" else String.format(Locale.FRANCE, "%.1f °C", temp))

            val texteCible = if (cible != null && fourchette != null) {
                "cible ${court(cible)} ±${court(fourchette)}"
            } else {
                "cible non réglée"
            }

            val (fond, detail) = when {
                temp == null ->
                    R.drawable.ligne_grise to "pas de mesure"
                minutes != null && minutes > MINUTES_ANCIENNE ->
                    R.drawable.ligne_grise to "dernière mesure il y a ${duree(minutes)}"
                cible != null && fourchette != null && temp > cible + fourchette ->
                    R.drawable.ligne_alerte to "$texteCible · trop chaud"
                cible != null && fourchette != null && temp < cible - fourchette ->
                    R.drawable.ligne_alerte to "$texteCible · trop froid"
                else ->
                    R.drawable.ligne_ok to texteCible
            }
            vue.setInt(LIGNES[i], "setBackgroundResource", fond)
            vue.setTextViewText(CIBLES[i], detail)
        }

        // Toucher le widget : relire tout de suite
        val rafraichir = Intent(ctx, TankWidgetProvider::class.java)
            .setAction(TankWidgetProvider.ACTION_RAFRAICHIR)
        vue.setOnClickPendingIntent(
            R.id.racine,
            PendingIntent.getBroadcast(ctx, 0, rafraichir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        // Toucher le titre : ouvrir les réglages
        vue.setOnClickPendingIntent(
            R.id.titre,
            PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return vue
    }

    /** 19.0 → "19" ; 18.5 → "18,5" */
    private fun court(v: Double): String =
        if (abs(v - v.roundToLong()) < 0.05) v.roundToLong().toString()
        else String.format(Locale.FRANCE, "%.1f", v)

    private fun minutesDepuis(dateIso: String?): Long? = try {
        if (dateIso == null) null else Duration.between(Instant.parse(dateIso), Instant.now()).toMinutes()
    } catch (e: Exception) {
        null
    }

    private fun duree(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} jours"
    }
}
