package fr.allyourtank.widget

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Ce qu'on sait d'une cuve : température, consigne, âge de la dernière mesure. */
data class EtatCuve(
    val temp: Double?,
    val cible: Double?,
    val fourchette: Double?,
    val minutes: Long?,
) {
    private val reglee: Boolean get() = cible != null && fourchette != null

    val chaud: Boolean get() = temp != null && reglee && temp > cible!! + fourchette!!
    val froid: Boolean get() = temp != null && reglee && temp < cible!! - fourchette!!
    val horsFourchette: Boolean get() = chaud || froid

    /** Pas de nouvelle mesure depuis plus de 10 minutes : le boîtier ne répond peut-être plus. */
    val ancienne: Boolean get() = minutes != null && minutes > 10

    /** Texte court pour le widget. */
    fun detail(): String = when {
        temp == null -> "pas de mesure"
        ancienne -> "il y a ${Format.duree(minutes!!)}"
        chaud -> "Trop chaud"
        froid -> "Trop froid"
        reglee -> "${Format.court(cible!!)} ±${Format.court(fourchette!!)}"
        else -> "cible non réglée"
    }

    companion object {
        /** Lit l'état de la cuve n (1, 2 ou 3) dans les feeds Adafruit IO. */
        fun lire(feeds: Map<String, Valeur>, n: Int): EtatCuve {
            val mesure = feeds["cuve$n"]
            return EtatCuve(
                temp = mesure?.nombre,
                cible = feeds["cible-cuve$n"]?.nombre,
                fourchette = feeds["fourchette-cuve$n"]?.nombre,
                minutes = minutesDepuis(mesure?.date),
            )
        }

        private fun minutesDepuis(dateIso: String?): Long? = try {
            if (dateIso == null) null else Duration.between(Instant.parse(dateIso), Instant.now()).toMinutes()
        } catch (e: Exception) {
            null
        }
    }
}

object Format {
    private fun entier(v: Double) = abs(v - v.roundToLong()) < 0.05

    /** 19.0 → "19" ; 18.5 → "18,5" (pour l'affichage) */
    fun court(v: Double): String =
        if (entier(v)) v.roundToLong().toString() else String.format(Locale.FRANCE, "%.1f", v)

    /** 19.0 → "19" ; 18.5 → "18.5" (pour l'envoi à Adafruit IO et au boîtier) */
    fun envoi(v: Double): String =
        if (entier(v)) v.roundToLong().toString() else String.format(Locale.US, "%.1f", v)

    /** 18.66 → "18,7" */
    fun temp(v: Double): String = String.format(Locale.FRANCE, "%.1f", v)

    fun duree(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} jours"
    }

    /** Lit un nombre tapé à la main : « 18,5 » ou « 18.5 ». */
    fun lireNombre(texte: String): Double? = texte.trim().replace(',', '.').toDoubleOrNull()
}
