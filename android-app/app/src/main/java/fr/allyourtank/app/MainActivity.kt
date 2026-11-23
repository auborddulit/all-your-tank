package fr.allyourtank.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran d'une cuve : onglets, cadran, réglage de la consigne et préréglages. */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_CUVE = "cuve"

        /** Délai avant d'envoyer un réglage fait avec − / + (on attend la fin des appuis). */
        private const val DELAI_ENVOI_MS = 1500L
    }

    private val principal = Handler(Looper.getMainLooper())
    private lateinit var palette: Palette
    private lateinit var reglages: Reglages

    private var feeds: Map<String, Valeur> = emptyMap()
    private var cuve = 0
    private val cibles = arrayOfNulls<Double>(NB_CUVES)
    private val fourchettes = arrayOfNulls<Double>(NB_CUVES)
    private var envoiEnAttente: Runnable? = null

    private lateinit var onglets: List<Button>
    private lateinit var jauge: JaugeView
    private lateinit var temperature: TextView
    private lateinit var etatCuve: TextView
    private lateinit var texteCible: TextView
    private lateinit var texteFourchette: TextView
    private lateinit var etatConnexion: TextView
    private lateinit var etatEnvoi: TextView
    private lateinit var zonePrereglages: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        palette = Palette.actuelle(this)

        onglets = listOf(findViewById(R.id.onglet1), findViewById(R.id.onglet2), findViewById(R.id.onglet3))
        jauge = findViewById(R.id.jauge)
        temperature = findViewById(R.id.temperature)
        etatCuve = findViewById(R.id.etat_cuve)
        texteCible = findViewById(R.id.cible)
        texteFourchette = findViewById(R.id.fourchette)
        etatConnexion = findViewById(R.id.etat_connexion)
        etatEnvoi = findViewById(R.id.etat_envoi)
        zonePrereglages = findViewById(R.id.prereglages)

        onglets.forEachIndexed { i, b -> b.setOnClickListener { choisirCuve(i) } }
        findViewById<Button>(R.id.cible_moins).setOnClickListener { modifier(cible = true, delta = -0.5) }
        findViewById<Button>(R.id.cible_plus).setOnClickListener { modifier(cible = true, delta = 0.5) }
        findViewById<Button>(R.id.fourchette_moins).setOnClickListener { modifier(cible = false, delta = -0.5) }
        findViewById<Button>(R.id.fourchette_plus).setOnClickListener { modifier(cible = false, delta = 0.5) }
        findViewById<Button>(R.id.bouton_actualiser).setOnClickListener {
            if (reglages.complets) lire()
        }
        demanderNotifications()
        findViewById<Button>(R.id.bouton_reglages).setOnClickListener {
            startActivity(Intent(this, ReglagesActivity::class.java))
        }

        cuve = intent.getIntExtra(EXTRA_CUVE, 0).coerceIn(0, NB_CUVES - 1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        choisirCuve(intent.getIntExtra(EXTRA_CUVE, cuve).coerceIn(0, NB_CUVES - 1))
    }

    override fun onResume() {
        super.onResume()
        reglages = Reglages.lire(this)
        feeds = Cache.feeds(this)
        chargerConsignes()
        afficher()
        if (reglages.complets) {
            lire()
        } else {
            etatConnexion.text = "Touchez « Réglages » pour entrer votre compte Adafruit IO."
        }
    }

    override fun onPause() {
        super.onPause()
        envoyerMaintenant()   // ne pas perdre un réglage en cours
    }

    /** Android 13 et plus : demander une fois l'autorisation d'afficher les alertes. */
    private fun demanderNotifications() {
        Alertes.creerCanal(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // ------------------------------------------------------------------ données

    private fun chargerConsignes() {
        for (n in 1..NB_CUVES) {
            cibles[n - 1] = feeds["cible-cuve$n"]?.nombre
            fourchettes[n - 1] = feeds["fourchette-cuve$n"]?.nombre
        }
    }

    /** Relit Adafruit IO en arrière-plan, puis met l'écran et le widget à jour. */
    private fun lire() {
        etatConnexion.text = "Mise à jour…"
        val r = reglages
        Thread {
            val message = try {
                val f = AdafruitIO.lireFeeds(r.utilisateur, r.cle)
                Cache.enregistrer(this, f)
                Alertes.verifier(this, f)
                principal.post {
                    feeds = f
                    if (envoiEnAttente == null) chargerConsignes()
                    afficher()
                }
                "Mis à jour à " + SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
            } catch (e: ErreurLecture) {
                Cache.enregistrerErreur(this, e.message ?: "Erreur")
                "⚠ " + e.message
            } catch (e: Exception) {
                "⚠ Lecture impossible"
            }
            principal.post {
                etatConnexion.text = message
                Dessin.mettreAJourTous(this)
            }
        }.start()
    }

    // ------------------------------------------------------------------ actions

    private fun choisirCuve(i: Int) {
        envoyerMaintenant()
        cuve = i
        etatEnvoi.text = ""
        afficher()
    }

    private fun modifier(cible: Boolean, delta: Double) {
        val i = cuve
        if (cible) {
            cibles[i] = ((cibles[i] ?: 19.0) + delta).coerceIn(0.0, 30.0)
        } else {
            fourchettes[i] = ((fourchettes[i] ?: 1.0) + delta).coerceIn(0.5, 5.0)
        }
        afficher()
        // On attend la fin des appuis avant d'envoyer (limite de 30 envois par minute)
        envoiEnAttente?.let { principal.removeCallbacks(it) }
        val envoi = Runnable {
            envoiEnAttente = null
            envoyer(i, cibles[i], fourchettes[i], null)
        }
        envoiEnAttente = envoi
        principal.postDelayed(envoi, DELAI_ENVOI_MS)
        etatEnvoi.text = "Réglage modifié, envoi dans un instant…"
    }

    private fun envoyerMaintenant() {
        envoiEnAttente?.let {
            principal.removeCallbacks(it)
            it.run()
        }
    }

    private fun appliquer(p: Prereglage) {
        envoiEnAttente?.let { principal.removeCallbacks(it) }
        envoiEnAttente = null
        cibles[cuve] = p.cible
        fourchettes[cuve] = p.fourchette
        afficher()
        envoyer(cuve, p.cible, p.fourchette, p.nom)
    }

    /** Envoie la cible et la fourchette de la cuve i à Adafruit IO (le boîtier suit). */
    private fun envoyer(i: Int, cible: Double?, fourchette: Double?, nomPrereglage: String?) {
        if (!reglages.complets) {
            etatEnvoi.text = "⚠ Entrez d'abord votre compte dans les réglages"
            return
        }
        val r = reglages
        val n = i + 1
        val valeurs = mutableMapOf<String, String>()
        cible?.let { valeurs["cible-cuve$n"] = Format.envoi(it) }
        fourchette?.let { valeurs["fourchette-cuve$n"] = Format.envoi(it) }
        if (valeurs.isEmpty()) return

        etatEnvoi.text = "Envoi…"
        Thread {
            val message = try {
                valeurs.forEach { (feed, v) -> AdafruitIO.envoyer(r.utilisateur, r.cle, feed, v) }
                Cache.mettreAJour(this, valeurs)
                val consigne = "${cible?.let { Format.court(it) } ?: "?"} ±${fourchette?.let { Format.court(it) } ?: "?"}"
                if (nomPrereglage != null) "✓ ${r.noms[i]} : $nomPrereglage ($consigne) envoyé"
                else "✓ ${r.noms[i]} : $consigne envoyé"
            } catch (e: ErreurLecture) {
                "⚠ " + e.message
            } catch (e: Exception) {
                "⚠ Envoi impossible"
            }
            principal.post {
                etatEnvoi.text = message
                feeds = Cache.feeds(this)
                Dessin.mettreAJourTous(this)
            }
        }.start()
    }

    // ------------------------------------------------------------------ affichage

    private fun afficher() {
        // Onglets
        onglets.forEachIndexed { i, b ->
            b.text = reglages.noms[i]
            val actif = i == cuve
            b.setBackgroundResource(if (actif) R.drawable.onglet_actif else R.drawable.onglet_inactif)
            b.setTextColor(if (actif) palette.surEncre else palette.encre)
        }

        // Cadran, température, état
        val mesure = EtatCuve.lire(feeds, cuve + 1)
        val e = mesure.copy(cible = cibles[cuve], fourchette = fourchettes[cuve])
        jauge.etat = e
        temperature.text = if (e.temp == null) "--" else Format.temp(e.temp) + " °C"
        val couleur = when {
            e.ancienne -> palette.attenue
            e.chaud -> palette.alerte
            e.froid -> palette.froid
            else -> palette.encre
        }
        temperature.setTextColor(couleur)
        etatCuve.setTextColor(couleur)
        etatCuve.text = when {
            e.temp == null -> "Pas de mesure"
            e.ancienne -> "Dernière mesure il y a ${Format.duree(e.minutes!!)}"
            e.chaud -> "Trop chaud"
            e.froid -> "Trop froid"
            e.cible == null || e.fourchette == null -> "Cible non réglée"
            // Tout va bien : pas de message (la place reste réservée, pour que
            // l'écran ne saute pas quand un message apparaît)
            else -> " "
        }

        // Consigne
        texteCible.text = cibles[cuve]?.let { Format.court(it) + "°" } ?: "--"
        texteFourchette.text = fourchettes[cuve]?.let { "±" + Format.court(it) } ?: "--"

        afficherPrereglages()
    }

    /** Les tuiles de préréglages, deux par ligne. */
    private fun afficherPrereglages() {
        zonePrereglages.removeAllViews()
        val liste = Prereglages.lire(this)
        liste.chunked(2).forEachIndexed { ligne, paire ->
            val rangee = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (ligne > 0) topMargin = dp(10) }
            }
            paire.forEachIndexed { k, p -> rangee.addView(tuile(p, k > 0)) }
            if (paire.size == 1) rangee.addView(espace())
            zonePrereglages.addView(rangee)
        }
    }

    private fun tuile(p: Prereglage, decalee: Boolean): LinearLayout {
        val active = cibles[cuve] == p.cible && fourchettes[cuve] == p.fourchette
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(if (active) R.drawable.tuile_active else R.drawable.tuile)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "Appliquer ${p.nom}, ${p.texte}"
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { if (decalee) marginStart = dp(10) }
            addView(texte(p.nom, 15f, palette.encre, gras = true))
            addView(texte(p.texte, 13f, palette.attenue, gras = false))
            setOnClickListener { appliquer(p) }
        }
    }

    private fun espace() = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(10) }
    }

    private fun texte(t: String, taille: Float, couleur: Int, gras: Boolean) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, taille)
        setTextColor(couleur)
        maxLines = 1
        if (gras) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
