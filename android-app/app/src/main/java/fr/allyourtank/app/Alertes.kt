package fr.allyourtank.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Notifications d'alerte, vérifiées à chaque relevé (environ toutes les 15 minutes).
 *
 *  - cuve hors fourchette (trop chaude / trop froide) ;
 *  - sonde en erreur : UNE cuve n'a plus de mesure depuis plus de 10 min
 *    (le boîtier n'envoie rien pour une sonde débranchée) ;
 *  - boîtier silencieux : AUCUNE cuve n'a de mesure depuis plus de 10 min.
 *
 * Une notification par problème, avec le son, la vibration et le pop-up réglés
 * dans le téléphone. Si le problème dure, un rappel toutes les heures.
 * Quand tout redevient normal, la notification s'efface sans bruit.
 */
object Alertes {
    private const val CANAL = "alertes"
    private const val PREFS = "alertes"
    private const val ID_BOITIER = 100
    /** Un peu moins d'une heure : les relevés tombent environ tous les quarts d'heure. */
    private const val RAPPEL_MS = 55 * 60 * 1000L

    private enum class Type { CHAUD, FROID, SONDE, BOITIER }

    /** À appeler après une lecture réussie d'Adafruit IO. */
    fun verifier(ctx: Context, feeds: Map<String, Valeur>) {
        val r = Reglages.lire(ctx)
        if (!r.complets) return
        creerCanal(ctx)
        val cuves = (1..NB_CUVES).map { EtatCuve.lire(feeds, it) }

        // Le boîtier est silencieux si toutes les cuves qui ont déjà mesuré sont muettes.
        val connues = cuves.filter { it.minutes != null }
        val silencieux = connues.isNotEmpty() && connues.all { it.ancienne }

        if (silencieux) {
            val minutes = connues.minOf { it.minutes!! }
            signaler(ctx, ID_BOITIER, Type.BOITIER, null) { rappel, _ ->
                Texte(
                    if (rappel) "Le boîtier n'envoie toujours rien" else "Le boîtier n'envoie plus rien",
                    "Aucune mesure depuis ${Format.duree(minutes)}. Courant ou WiFi coupé ?",
                )
            }
            // Pas la peine d'annoncer aussi chaque cuve : c'est le boîtier.
            for (i in 0 until NB_CUVES) effacer(ctx, i)
            return
        }
        effacer(ctx, ID_BOITIER)

        cuves.forEachIndexed { i, e ->
            val nom = r.noms[i]
            val type = when {
                e.ancienne -> Type.SONDE
                e.chaud -> Type.CHAUD
                e.froid -> Type.FROID
                else -> null
            }
            if (type == null) { effacer(ctx, i); return@forEachIndexed }
            signaler(ctx, i, type, i) { rappel, depuis ->
                when (type) {
                    Type.SONDE -> Texte(
                        if (rappel) "$nom : la sonde ne mesure toujours plus" else "$nom : la sonde ne mesure plus",
                        "Dernière mesure il y a ${Format.duree(e.minutes!!)}. " +
                            "Les autres cuves vont bien : vérifiez le câble de cette sonde.",
                    )
                    else -> {
                        val sens = if (type == Type.CHAUD) "trop chaude" else "trop froide"
                        val consigne = "cible ${Format.court(e.cible!!)} ± ${Format.court(e.fourchette!!)} °C"
                        Texte(
                            "$nom ${if (rappel) "toujours " else ""}$sens : ${Format.temp(e.temp!!)} °C",
                            if (rappel) "Hors fourchette depuis ${Format.duree(depuis)} · $consigne."
                            else "${consigne.replaceFirstChar { it.uppercase() }}. Touchez pour ouvrir la cuve.",
                        )
                    }
                }
            }
        }
    }

    private class Texte(val titre: String, val detail: String)

    /**
     * Affiche la notification si le problème est nouveau, ou si le dernier
     * rappel date d'au moins une heure. Sinon, ne fait rien.
     */
    private fun signaler(
        ctx: Context, id: Int, type: Type, cuve: Int?,
        texte: (rappel: Boolean, depuisMinutes: Long) -> Texte,
    ) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val maintenant = System.currentTimeMillis()
        val ancien = p.getString("type$id", null)
        val nouveau = ancien != type.name
        val debut = if (nouveau) maintenant else p.getLong("debut$id", maintenant)
        val dernier = p.getLong("dernier$id", 0L)
        if (!nouveau && maintenant - dernier < RAPPEL_MS) return

        val t = texte(!nouveau, (maintenant - debut) / 60_000)
        val ouvrir = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (cuve != null) ouvrir.putExtra(MainActivity.EXTRA_CUVE, cuve)
        val clic = PendingIntent.getActivity(
            ctx, 1000 + id, ouvrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Palette.actuelle(ctx).bande)
            .setContentTitle(t.titre)
            .setContentText(t.detail)
            .setStyle(Notification.BigTextStyle().bigText(t.detail))
            .setContentIntent(clic)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Effacer d'abord, pour que le rappel sonne et s'affiche à nouveau en pop-up.
        nm.cancel(id)
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // Notifications refusées dans les réglages du téléphone : rien à faire.
        }
        p.edit()
            .putString("type$id", type.name)
            .putLong("debut$id", debut)
            .putLong("dernier$id", maintenant)
            .apply()
    }

    private fun effacer(ctx: Context, id: Int) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("type$id", null) == null) return
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
        p.edit().remove("type$id").remove("debut$id").remove("dernier$id").apply()
    }

    /** Le « canal » Android : c'est lui qui suit les réglages de son, vibration et pop-up. */
    fun creerCanal(ctx: Context) {
        val canal = NotificationChannel(CANAL, "Alertes des cuves", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cuve hors fourchette, sonde en erreur, boîtier silencieux"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
