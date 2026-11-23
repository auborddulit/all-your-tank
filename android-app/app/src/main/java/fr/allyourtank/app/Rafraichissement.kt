package fr.allyourtank.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond : lit Adafruit IO, garde le résultat et redessine le widget.
 * Android la lance environ toutes les 15 minutes (le minimum qu'il autorise),
 * et tout de suite quand on touche le widget.
 */
class RafraichirWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val r = Reglages.lire(ctx)
        if (!r.complets) {
            Cache.enregistrerErreur(ctx, "Ouvrez l'appli pour la régler")
        } else {
            try {
                Cache.enregistrer(ctx, AdafruitIO.lireFeeds(r.utilisateur, r.cle))
            } catch (e: ErreurLecture) {
                Cache.enregistrerErreur(ctx, e.message ?: "Erreur")
            } catch (e: Exception) {
                Cache.enregistrerErreur(ctx, "Lecture impossible")
            }
        }
        Dessin.mettreAJourTous(ctx)
        return Result.success()
    }

    companion object {
        private const val PERIODIQUE = "rafraichir-periodique"
        private const val IMMEDIAT = "rafraichir-maintenant"

        private val reseau = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Programme le rafraîchissement régulier (sans le dupliquer s'il existe déjà). */
        fun programmer(ctx: Context) {
            val tache = PeriodicWorkRequestBuilder<RafraichirWorker>(15, TimeUnit.MINUTES)
                .setConstraints(reseau)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIQUE, ExistingPeriodicWorkPolicy.KEEP, tache)
        }

        /** Demande une lecture tout de suite. */
        fun maintenant(ctx: Context) {
            val tache = OneTimeWorkRequestBuilder<RafraichirWorker>()
                .setConstraints(reseau)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(IMMEDIAT, ExistingWorkPolicy.REPLACE, tache)
        }

        fun arreter(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIQUE)
        }
    }
}
