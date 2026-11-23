package fr.allyourtank.widget

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

/** Écran de réglages : compte Adafruit IO, noms des cuves, test de connexion. */
class MainActivity : Activity() {

    private lateinit var utilisateur: EditText
    private lateinit var cle: EditText
    private lateinit var noms: List<EditText>
    private lateinit var etat: TextView
    private lateinit var bouton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        utilisateur = findViewById(R.id.champ_utilisateur)
        cle = findViewById(R.id.champ_cle)
        noms = listOf(findViewById(R.id.champ_nom1), findViewById(R.id.champ_nom2), findViewById(R.id.champ_nom3))
        etat = findViewById(R.id.etat)
        bouton = findViewById(R.id.bouton_enregistrer)

        val r = Reglages.lire(this)
        utilisateur.setText(r.utilisateur)
        cle.setText(r.cle)
        noms.forEachIndexed { i, champ -> champ.setText(r.noms[i]) }

        bouton.setOnClickListener { enregistrerEtTester() }
    }

    private fun enregistrerEtTester() {
        val r = Reglages(
            utilisateur = utilisateur.text.toString(),
            cle = cle.text.toString(),
            noms = noms.map { it.text.toString() },
        )
        Reglages.ecrire(this, r)
        Dessin.mettreAJourTous(this)

        if (!r.complets) {
            etat.text = "Indiquez votre nom d'utilisateur et votre clé Adafruit IO."
            return
        }

        bouton.isEnabled = false
        etat.text = "Connexion à Adafruit IO…"
        val principal = Handler(Looper.getMainLooper())

        // Le réseau ne doit pas bloquer l'écran : on lit dans un fil à part
        Thread {
            val message = try {
                val feeds = AdafruitIO.lireFeeds(r.utilisateur, r.cle)
                Cache.enregistrer(this, feeds)
                val temperatures = (1..NB_CUVES).mapNotNull { n ->
                    feeds["cuve$n"]?.nombre?.let { "${r.noms[n - 1]} : ${String.format(Locale.FRANCE, "%.1f", it)} °C" }
                }
                if (temperatures.isEmpty()) {
                    "Connexion réussie, mais aucun feed cuve1, cuve2 ou cuve3 trouvé sur ce compte."
                } else {
                    "✓ Connexion réussie\n" + temperatures.joinToString("\n")
                }
            } catch (e: ErreurLecture) {
                Cache.enregistrerErreur(this, e.message ?: "Erreur")
                "✗ " + e.message
            } catch (e: Exception) {
                "✗ Lecture impossible (${e.javaClass.simpleName})"
            }
            principal.post {
                etat.text = message
                bouton.isEnabled = true
                Dessin.mettreAJourTous(this)
                RafraichirWorker.programmer(this)
            }
        }.start()
    }
}
