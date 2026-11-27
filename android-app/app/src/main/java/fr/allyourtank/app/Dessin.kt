package fr.allyourtank.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/**
 * Dessine le widget à partir des dernières valeurs gardées en mémoire :
 * pour chaque cuve, un cadran à graduations, la température et le nom.
 *
 * Le widget remplit la place qu'on lui donne : cadrans et textes grandissent
 * avec lui, en gardant leurs proportions. Trois dispositions selon la forme :
 *  - normale : les 3 cuves côte à côte, texte sur deux lignes ;
 *  - très large et bas : côte à côte, texte sur une ligne (cadrans plus grands) ;
 *  - haut et étroit : une ligne par cuve.
 *
 * Les textes suivent le mode clair/sombre grâce à res/values(-night)/colors.xml ;
 * les cadrans et les couleurs d'état sont fournis dans les deux versions
 * (Android 12 et plus récent choisit tout seul ; avant, on prend le mode actuel).
 */
object Dessin {

    private val COLONNES = intArrayOf(R.id.col1, R.id.col2, R.id.col3)
    private val JAUGES = intArrayOf(R.id.jauge1, R.id.jauge2, R.id.jauge3)
    private val TEMPS = intArrayOf(R.id.temp1, R.id.temp2, R.id.temp3)
    private val NOMS = intArrayOf(R.id.nom1, R.id.nom2, R.id.nom3)

    /** Taille utilisée tant qu'Android n'a pas indiqué celle du widget (en dp). */
    private const val LARGEUR_DEFAUT = 250f
    private const val HAUTEUR_DEFAUT = 110f

    /** Tout ce qu'il faut pour dessiner, lu une seule fois par mise à jour. */
    private class Donnees(
        val noms: List<String>,
        val etats: List<EtatCuve>,
        val mini: Double,
        val maxi: Double,
        val jour: Palette,
        val nuit: Palette,
        val nuitActuelle: Boolean,
    )

    /** Disposition et tailles (en dp) pour une place donnée. */
    private class Mise(
        val disposition: Int,
        val cadran: Float,
        val texteTemp: Float,
        val texteNom: Float,
        val margeVerticale: Float,
    )

    private val recent: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, TankWidgetProvider::class.java))

    fun mettreAJourTous(ctx: Context) {
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        val feeds = Cache.feeds(ctx)
        val reglages = Reglages.lire(ctx)
        val d = Donnees(
            noms = reglages.noms,
            mini = reglages.echelleMin,
            maxi = reglages.echelleMax,
            etats = (1..NB_CUVES).map { EtatCuve.lire(feeds, it) },
            jour = Palette.pour(ctx, false),
            nuit = Palette.pour(ctx, true),
            nuitActuelle = Palette.estNuit(ctx),
        )
        val manager = AppWidgetManager.getInstance(ctx)
        for (id in ids) manager.updateAppWidget(id, pourWidget(ctx, manager, id, d))
    }

    /** Une vue par taille possible du widget (Android 12+), sinon celle du mode portrait. */
    private fun pourWidget(ctx: Context, manager: AppWidgetManager, id: Int, d: Donnees): RemoteViews {
        val options = manager.getAppWidgetOptions(id)
        if (recent) {
            @Suppress("DEPRECATION")
            val tailles: List<SizeF>? = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!tailles.isNullOrEmpty()) {
                return RemoteViews(tailles.take(16).associateWith { construire(ctx, it.width, it.height, d) })
            }
        }
        // Avant Android 12 : en portrait, le widget a sa largeur minimale et sa hauteur maximale
        val largeur = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat().takeIf { it > 0 } ?: LARGEUR_DEFAUT
        val hauteur = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat().takeIf { it > 0 } ?: HAUTEUR_DEFAUT
        return construire(ctx, largeur, hauteur, d)
    }

    /** Choisit la disposition et calcule les tailles, comme sur la maquette. */
    private fun calculer(largeurWidget: Float, hauteurWidget: Float): Mise {
        val margeH = 14f
        val margeV = if (hauteurWidget * 3.2f < largeurWidget) 8f else 12f
        val largeur = largeurWidget - 2 * margeH
        val hauteur = hauteurWidget - 2 * margeV
        val empile = hauteurWidget > largeurWidget * 1.1f
        val tresLarge = !empile && largeurWidget > hauteurWidget * 3.2f

        return when {
            empile -> {
                val g = min((hauteur / 3f) / (Jauge.RATIO_WIDGET + 0.04f), largeur * 0.5f).coerceAtLeast(30f)
                Mise(R.layout.widget_cuves_empile, g, max(13f, g * 0.34f), max(9f, g * 0.15f), margeV)
            }
            tresLarge -> {
                val t = max(13f, hauteur * 0.2f)
                val g = min(largeur / 3f - 10f, (hauteur - t * 1.2f) / Jauge.RATIO_WIDGET).coerceAtLeast(30f)
                Mise(R.layout.widget_cuves_large, g, t, max(9f, t * 0.6f), margeV)
            }
            else -> {
                val g = min(largeur / 3f - 6f, hauteur / (Jauge.RATIO_WIDGET + 0.40f)).coerceAtLeast(30f)
                Mise(R.layout.widget_cuves, g, max(13f, g * 0.21f), max(9f, g * 0.105f), margeV)
            }
        }
    }

    private fun construire(ctx: Context, largeurWidget: Float, hauteurWidget: Float, d: Donnees): RemoteViews {
        val mise = calculer(largeurWidget, hauteurWidget)
        val densite = ctx.resources.displayMetrics.density
        val vue = RemoteViews(ctx.packageName, mise.disposition)
        val mh = (14 * densite).toInt()
        val mv = (mise.margeVerticale * densite).toInt()
        vue.setViewPadding(R.id.racine, mh, mv, mh, mv)
        val cadranPx = (mise.cadran * densite).toInt()

        for (i in 0 until NB_CUVES) {
            val e = d.etats[i]

            vue.setTextViewText(NOMS[i], d.noms[i])
            vue.setTextViewText(TEMPS[i], if (e.temp == null) "--" else Format.temp(e.temp) + "°")
            vue.setTextViewTextSize(TEMPS[i], TypedValue.COMPLEX_UNIT_DIP, mise.texteTemp)
            vue.setTextViewTextSize(NOMS[i], TypedValue.COMPLEX_UNIT_DIP, mise.texteNom)

            // Cadran à graduations dessiné à la bonne taille, en version claire et sombre
            if (recent) {
                vue.setIcon(
                    JAUGES[i], "setImageIcon",
                    donneesIcone(Jauge.png(cadranPx, e, d.jour, d.mini, d.maxi)),
                    donneesIcone(Jauge.png(cadranPx, e, d.nuit, d.mini, d.maxi)),
                )
            } else {
                vue.setImageViewBitmap(JAUGES[i], Jauge.image(cadranPx, e, if (d.nuitActuelle) d.nuit else d.jour, d.mini, d.maxi))
            }

            // Couleur de la température selon l'état. Toujours la donner, même quand tout
            // va bien : Android réapplique la mise à jour sur le widget déjà affiché, donc
            // une couleur d'alerte resterait sinon après le retour à la normale.
            when {
                e.ancienne -> couleur(vue, TEMPS[i], d.jour.attenue, d.nuit.attenue, d.nuitActuelle)
                e.chaud -> couleur(vue, TEMPS[i], d.jour.alerte, d.nuit.alerte, d.nuitActuelle)
                e.froid -> couleur(vue, TEMPS[i], d.jour.froid, d.nuit.froid, d.nuitActuelle)
                else -> couleur(vue, TEMPS[i], d.jour.encre, d.nuit.encre, d.nuitActuelle)
            }

            // Toucher une cuve : ouvrir l'appli sur cette cuve (elle relit tout de suite)
            vue.setOnClickPendingIntent(COLONNES[i], ouvrirAppli(ctx, i))
        }
        return vue
    }

    private fun donneesIcone(png: ByteArray): Icon = Icon.createWithData(png, 0, png.size)

    private fun ouvrirAppli(ctx: Context, cuve: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CUVE, cuve)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 10 + cuve, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun couleur(vue: RemoteViews, id: Int, jour: Int, nuit: Int, nuitActuelle: Boolean) {
        if (recent) {
            vue.setColorInt(id, "setTextColor", jour, nuit)
        } else {
            vue.setTextColor(id, if (nuitActuelle) nuit else jour)
        }
    }
}
