package fr.allyourtank.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Réglages : compte Adafruit IO, noms des cuves, liste des préréglages. */
class ReglagesActivity : Activity() {

    private lateinit var utilisateur: EditText
    private lateinit var cle: EditText
    private lateinit var noms: List<EditText>
    private lateinit var echelleMin: EditText
    private lateinit var echelleMax: EditText
    private lateinit var liste: LinearLayout
    private lateinit var etat: TextView
    private lateinit var bouton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reglages)

        utilisateur = findViewById(R.id.champ_utilisateur)
        cle = findViewById(R.id.champ_cle)
        noms = listOf(findViewById(R.id.champ_nom1), findViewById(R.id.champ_nom2), findViewById(R.id.champ_nom3))
        echelleMin = findViewById(R.id.champ_echelle_min)
        echelleMax = findViewById(R.id.champ_echelle_max)
        liste = findViewById(R.id.liste_prereglages)
        etat = findViewById(R.id.etat)
        bouton = findViewById(R.id.bouton_enregistrer)

        val r = Reglages.lire(this)
        utilisateur.setText(r.utilisateur)
        cle.setText(r.cle)
        noms.forEachIndexed { i, champ -> champ.setText(r.noms[i]) }
        echelleMin.setText(Format.envoi(r.echelleMin))
        echelleMax.setText(Format.envoi(r.echelleMax))
        Prereglages.lire(this).forEach { ajouterLigne(it) }

        findViewById<Button>(R.id.ajouter).setOnClickListener { ajouterLigne(null) }
        bouton.setOnClickListener { enregistrerEtTester() }
    }

    private fun ajouterLigne(p: Prereglage?) {
        val ligne = LayoutInflater.from(this).inflate(R.layout.ligne_prereglage, liste, false)
        if (p != null) {
            ligne.findViewById<EditText>(R.id.nom).setText(p.nom)
            ligne.findViewById<EditText>(R.id.cible).setText(Format.envoi(p.cible))
            ligne.findViewById<EditText>(R.id.fourchette).setText(Format.envoi(p.fourchette))
        }
        ligne.findViewById<Button>(R.id.supprimer).setOnClickListener { liste.removeView(ligne) }
        liste.addView(ligne)
        if (p == null) ligne.findViewById<EditText>(R.id.nom).requestFocus()
    }

    /** Lit les lignes de l'éditeur ; ignore celles qui sont incomplètes. */
    private fun lirePrereglages(): Pair<List<Prereglage>, Int> {
        val resultat = mutableListOf<Prereglage>()
        var ignorees = 0
        for (k in 0 until liste.childCount) {
            val ligne: View = liste.getChildAt(k)
            val nom = ligne.findViewById<EditText>(R.id.nom).text.toString().trim()
            val c = Format.lireNombre(ligne.findViewById<EditText>(R.id.cible).text.toString())
            val f = Format.lireNombre(ligne.findViewById<EditText>(R.id.fourchette).text.toString())
            if (nom.isEmpty() && c == null && f == null) continue          // ligne vide
            if (nom.isEmpty() || c == null || f == null || f <= 0) { ignorees++; continue }
            resultat.add(Prereglage(nom, c, f))
        }
        return resultat to ignorees
    }

    /**
     * Lit l'échelle saisie. Si elle n'est pas valable, garde l'ancienne et
     * renvoie un message pour l'expliquer.
     */
    private fun lireEchelle(ancienne: Reglages): Triple<Double, Double, String?> {
        val mini = Format.lireNombre(echelleMin.text.toString())
        val maxi = Format.lireNombre(echelleMax.text.toString())
        val probleme = when {
            mini == null || maxi == null -> "indiquez un minimum et un maximum"
            mini < ECHELLE_LIMITE_BASSE || maxi > ECHELLE_LIMITE_HAUTE ->
                "elle doit rester entre ${Format.court(ECHELLE_LIMITE_BASSE)} et ${Format.court(ECHELLE_LIMITE_HAUTE)} °C"
            maxi - mini < ECHELLE_ECART_MINI -> "il faut au moins ${Format.court(ECHELLE_ECART_MINI)} °C entre le minimum et le maximum"
            else -> null
        }
        if (probleme != null) {
            echelleMin.setText(Format.envoi(ancienne.echelleMin))
            echelleMax.setText(Format.envoi(ancienne.echelleMax))
            return Triple(ancienne.echelleMin, ancienne.echelleMax, "\n⚠ Échelle du cadran non modifiée : $probleme.")
        }
        return Triple(mini!!, maxi!!, null)
    }

    private fun enregistrerEtTester() {
        val (mini, maxi, alerteEchelle) = lireEchelle(Reglages.lire(this))
        val r = Reglages(
            utilisateur = utilisateur.text.toString(),
            cle = cle.text.toString(),
            noms = noms.map { it.text.toString() },
            echelleMin = mini,
            echelleMax = maxi,
        )
        Reglages.ecrire(this, r)
        val (prereglages, ignorees) = lirePrereglages()
        Prereglages.ecrire(this, prereglages)
        Dessin.mettreAJourTous(this)

        val avertissement = (if (ignorees > 0) "\n⚠ $ignorees préréglage(s) incomplet(s) ignoré(s)" else "") +
            (alerteEchelle ?: "")
        if (!r.complets) {
            etat.text = "Enregistré. Indiquez votre nom d'utilisateur et votre clé Adafruit IO.$avertissement"
            return
        }

        bouton.isEnabled = false
        etat.text = "Connexion à Adafruit IO…"
        val principal = Handler(Looper.getMainLooper())
        Thread {
            val message = try {
                val feeds = AdafruitIO.lireFeeds(r.utilisateur, r.cle)
                Cache.enregistrer(this, feeds)
                val n = (1..NB_CUVES).count { feeds["cuve$it"] != null }
                if (n == 0) "Connexion réussie, mais aucun feed cuve1, cuve2 ou cuve3 sur ce compte."
                else "✓ Enregistré, connexion réussie ($n cuve(s) trouvée(s))."
            } catch (e: ErreurLecture) {
                Cache.enregistrerErreur(this, e.message ?: "Erreur")
                "✗ " + e.message
            } catch (e: Exception) {
                "✗ Lecture impossible (${e.javaClass.simpleName})"
            }
            principal.post {
                etat.text = message + avertissement
                bouton.isEnabled = true
                Dessin.mettreAJourTous(this)
                RafraichirWorker.programmer(this)
            }
        }.start()
    }
}
