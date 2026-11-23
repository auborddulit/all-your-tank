package fr.allyourtank.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/** Dernière valeur d'un feed et date de réception (texte ISO 8601, ex. 2026-11-23T08:20:33Z). */
data class Valeur(val texte: String?, val date: String?) {
    val nombre: Double? get() = texte?.trim()?.replace(',', '.')?.toDoubleOrNull()
}

/** Erreur à afficher telle quelle à l'utilisateur. */
class ErreurLecture(message: String) : IOException(message)

object AdafruitIO {

    /**
     * Lit la dernière valeur de TOUS les feeds du compte en une seule requête
     * (GET /api/v2/{utilisateur}/feeds). Renvoie : clé du feed → valeur.
     */
    fun lireFeeds(utilisateur: String, cle: String): Map<String, Valeur> {
        val nom = URLEncoder.encode(utilisateur.trim(), "UTF-8")
        val connexion = URL("https://io.adafruit.com/api/v2/$nom/feeds").openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = 15_000
            connexion.readTimeout = 15_000
            connexion.setRequestProperty("X-AIO-Key", cle.trim())
            connexion.setRequestProperty("Accept", "application/json")

            val code = try {
                connexion.responseCode
            } catch (e: UnknownHostException) {
                throw ErreurLecture("Pas de connexion internet")
            }
            when (code) {
                200 -> {}
                401, 403 -> throw ErreurLecture("Nom d'utilisateur ou clé Adafruit IO incorrects")
                404 -> throw ErreurLecture("Compte Adafruit IO introuvable")
                429 -> throw ErreurLecture("Trop de demandes, réessayez dans une minute")
                else -> throw ErreurLecture("Adafruit IO a répondu : erreur $code")
            }

            val corps = connexion.inputStream.bufferedReader().use { it.readText() }
            val feeds = JSONArray(corps)
            val resultat = mutableMapOf<String, Valeur>()
            for (i in 0 until feeds.length()) {
                val f = feeds.getJSONObject(i)
                val cleFeed = f.optString("key")
                if (cleFeed.isEmpty()) continue
                resultat[cleFeed] = Valeur(
                    texte = f.optStringOuNull("last_value"),
                    date = f.optStringOuNull("updated_at"),
                )
            }
            return resultat
        } finally {
            connexion.disconnect()
        }
    }

    /**
     * Envoie une valeur dans un feed (POST /api/v2/{utilisateur}/feeds/{feed}/data).
     * Le boîtier la reçoit quelques secondes plus tard.
     */
    fun envoyer(utilisateur: String, cle: String, feed: String, valeur: String) {
        val nom = URLEncoder.encode(utilisateur.trim(), "UTF-8")
        val connexion = URL("https://io.adafruit.com/api/v2/$nom/feeds/$feed/data").openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = 15_000
            connexion.readTimeout = 15_000
            connexion.requestMethod = "POST"
            connexion.doOutput = true
            connexion.setRequestProperty("X-AIO-Key", cle.trim())
            connexion.setRequestProperty("Content-Type", "application/json")
            val corps = JSONObject().put("value", valeur).toString()
            try {
                connexion.outputStream.use { it.write(corps.toByteArray(Charsets.UTF_8)) }
            } catch (e: UnknownHostException) {
                throw ErreurLecture("Pas de connexion internet")
            }
            when (val code = connexion.responseCode) {
                200, 201 -> {}
                401, 403 -> throw ErreurLecture("Nom d'utilisateur ou clé Adafruit IO incorrects")
                404 -> throw ErreurLecture("Feed $feed introuvable sur Adafruit IO")
                429 -> throw ErreurLecture("Trop d'envois, réessayez dans une minute")
                else -> throw ErreurLecture("Adafruit IO a répondu : erreur $code")
            }
        } finally {
            connexion.disconnect()
        }
    }

    private fun JSONObject.optStringOuNull(nom: String): String? =
        if (isNull(nom)) null else optString(nom).ifEmpty { null }
}

/**
 * Dernières valeurs reçues, gardées sur le téléphone pour que le widget
 * s'affiche tout de suite, même sans réseau.
 */
object Cache {
    private const val FICHIER = "cache"

    fun enregistrer(ctx: Context, feeds: Map<String, Valeur>) {
        val json = JSONObject()
        feeds.forEach { (cle, v) ->
            json.put(cle, JSONObject().put("v", v.texte ?: JSONObject.NULL).put("d", v.date ?: JSONObject.NULL))
        }
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putString("feeds", json.toString())
            .putLong("heure", System.currentTimeMillis())
            .remove("erreur")
            .apply()
    }

    /** Après un envoi : met à jour les valeurs gardées, sans attendre la prochaine lecture. */
    fun mettreAJour(ctx: Context, valeurs: Map<String, String>) {
        val feeds = feeds(ctx).toMutableMap()
        val maintenant = java.time.Instant.now().toString()
        valeurs.forEach { (cle, v) -> feeds[cle] = Valeur(v, maintenant) }
        val heure = heure(ctx)
        enregistrer(ctx, feeds)
        // garder l'heure de la dernière vraie lecture
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putLong("heure", heure).apply()
    }

    fun enregistrerErreur(ctx: Context, message: String) {
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putString("erreur", message)
            .apply()
    }

    fun feeds(ctx: Context): Map<String, Valeur> {
        val texte = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString("feeds", null)
            ?: return emptyMap()
        val json = JSONObject(texte)
        return json.keys().asSequence().associateWith { cle ->
            val o = json.getJSONObject(cle)
            Valeur(
                texte = if (o.isNull("v")) null else o.getString("v"),
                date = if (o.isNull("d")) null else o.getString("d"),
            )
        }
    }

    /** Heure de la dernière lecture réussie (0 = jamais). */
    fun heure(ctx: Context): Long =
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getLong("heure", 0)

    fun erreur(ctx: Context): String? =
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString("erreur", null)
}
