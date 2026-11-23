package fr.allyourtank.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** Le widget : reçoit les événements d'Android et des appuis sur le widget. */
class TankWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(ctx: Context) {
        // Premier widget posé sur l'écran d'accueil
        RafraichirWorker.programmer(ctx)
        RafraichirWorker.maintenant(ctx)
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        // Redessine avec les dernières valeurs connues, et s'assure que le
        // rafraîchissement régulier est programmé (sans en relancer un autre).
        Dessin.mettreAJourTous(ctx)
        RafraichirWorker.programmer(ctx)
    }

    override fun onDisabled(ctx: Context) {
        // Dernier widget retiré : plus besoin de lire Adafruit IO
        RafraichirWorker.arreter(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_RAFRAICHIR) {
            Dessin.afficherChargement(ctx)
            RafraichirWorker.maintenant(ctx)
        }
    }

    companion object {
        const val ACTION_RAFRAICHIR = "fr.allyourtank.app.RAFRAICHIR"
    }
}
