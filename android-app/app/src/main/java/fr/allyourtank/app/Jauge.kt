package fr.allyourtank.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Les cadrans.
 *  Les deux cadrans font un arc de 240° (ARC), ouvert vers le bas.
 *  - Appli : cadran gradué (un trait par degré, plus long tous les
 *    5 °C, les nombres tous les 10 °C), la fourchette en ambre, une aiguille fine.
 *    L'échelle va de echelleMin à echelleMax (Réglages).
 *  - Widget : cadran à graduations fin, même échelle (plus bas dans ce fichier).
 */
object Jauge {
    /** Ouverture des cadrans, en degrés (180 = demi-cercle). */
    private const val ARC = 240.0

    /** Hauteur du petit cadran du widget, en proportion de sa largeur. */
    const val RATIO_WIDGET = 0.76f

    /** Angle (en radians) d'une température sur un cadran de mini à maxi °C. */
    private fun angleDe(v: Double, mini: Double, maxi: Double): Double {
        val f = ((v - mini) / (maxi - mini).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return Math.toRadians(90 + ARC / 2 - f * ARC)
    }

    /** Hauteur du grand cadran pour une largeur donnée. */
    fun hauteur(largeur: Float): Float = largeur * 0.82f

    fun dessiner(canvas: Canvas, largeur: Float, e: EtatCuve, p: Palette, mini: Double, maxi: Double) {
        // Géométrie pensée sur une largeur de 100, puis mise à l'échelle
        val s = largeur / 100f
        val cx = 50f * s
        val cy = 56f * s
        val r = 46f * s
        val etendue = (maxi - mini).coerceAtLeast(1.0)
        fun angle(v: Double): Double = angleDe(v, mini, maxi)
        fun x(a: Double, rayon: Float) = cx + rayon * cos(a).toFloat()
        fun y(a: Double, rayon: Float) = cy - rayon * sin(a).toFloat()

        // Graduations : un trait par degré, plus long tous les 5 °C ; en ambre dans la fourchette
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        val premier = Math.ceil(mini).toInt()
        val dernier = Math.floor(maxi).toInt()
        for (v in premier..dernier) {
            val degre = v.toDouble()
            val dansFourchette = e.cible != null && e.fourchette != null &&
                degre >= e.cible - e.fourchette - 1e-6 && degre <= e.cible + e.fourchette + 1e-6
            val long = v % 5 == 0
            val a = angle(degre)
            val r1 = r - (if (long) 8f else 4.5f) * s
            trait.color = if (dansFourchette) p.bande else p.graduation
            trait.strokeWidth = (if (dansFourchette) 1.6f else 1.0f) * s
            canvas.drawLine(x(a, r1), y(a, r1), x(a, r), y(a, r), trait)
        }

        // Nombres tous les 10 °C (tous les 5 °C si l'échelle est courte)
        val pas = if (etendue <= 15) 5 else 10
        val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.attenue
            textSize = 5.2f * s
            textAlign = Paint.Align.CENTER
        }
        for (v in premier..dernier) {
            if (v % pas != 0) continue
            val a = angle(v.toDouble())
            val rt = r - 14f * s
            canvas.drawText(v.toString(), x(a, rt), y(a, rt) + 1.9f * s, texte)
        }

        // Aiguille fine, avec un petit bout de l'autre côté du centre, et un moyeu creux
        if (e.temp != null) {
            val couleur = when {
                e.ancienne -> p.attenue
                e.chaud -> p.alerte
                e.froid -> p.froid
                else -> p.encre
            }
            val a = angle(e.temp)
            val aiguille = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = couleur
                strokeWidth = 1.3f * s
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(x(a, -5f * s), y(a, -5f * s), x(a, r - 5f * s), y(a, r - 5f * s), aiguille)
            val moyeu = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.fond; style = Paint.Style.FILL }
            canvas.drawCircle(cx, cy, 2.8f * s, moyeu)
            aiguille.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, 2.8f * s, aiguille)
        }
    }

    // ------------------------------------------------------------------ version fine du widget

    /** Hauteur du cadran à graduations pour une largeur donnée. */
    fun hauteurGraduations(largeur: Float): Float = largeur * RATIO_WIDGET

    /**
     * Cadran à graduations (widget) : un trait par degré, plus long tous les 5 °C,
     * les traits de la fourchette en ambre, une aiguille courte et fine.
     */
    fun dessinerGraduations(canvas: Canvas, largeur: Float, e: EtatCuve, p: Palette, mini: Double, maxi: Double) {
        val s = largeur / 100f
        val cx = 50f * s
        val cy = 52f * s
        val r = 40f * s
        fun angle(v: Double): Double = angleDe(v, mini, maxi)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }

        for (v in Math.ceil(mini).toInt()..Math.floor(maxi).toInt()) {
            val degre = v.toDouble()
            val dansFourchette = e.cible != null && e.fourchette != null &&
                degre >= e.cible - e.fourchette - 1e-6 && degre <= e.cible + e.fourchette + 1e-6
            val long = v % 5 == 0
            val a = angle(degre)
            val r1 = r - (if (long) 9f else 5f) * s
            trait.color = if (dansFourchette) p.bande else p.graduation
            trait.strokeWidth = (if (dansFourchette) 2.6f else 1.8f) * s
            canvas.drawLine(
                cx + r1 * cos(a).toFloat(), cy - r1 * sin(a).toFloat(),
                cx + r * cos(a).toFloat(), cy - r * sin(a).toFloat(),
                trait,
            )
        }

        if (e.temp != null) {
            val couleur = when {
                e.ancienne -> p.attenue
                e.chaud -> p.alerte
                e.froid -> p.froid
                else -> p.encre
            }
            val a = angle(e.temp)
            val aiguille = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = couleur
                strokeWidth = 1.4f * s
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx, cy, cx + 26f * s * cos(a).toFloat(), cy - 26f * s * sin(a).toFloat(), aiguille)
            aiguille.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 2.2f * s, aiguille)
        }
    }

    /** Image du cadran à graduations, pour le widget. */
    fun image(largeurPx: Int, e: EtatCuve, p: Palette, mini: Double, maxi: Double): Bitmap {
        val l = largeurPx.coerceAtLeast(16)
        val b = Bitmap.createBitmap(l, hauteurGraduations(l.toFloat()).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        dessinerGraduations(Canvas(b), l.toFloat(), e, p, mini, maxi)
        return b
    }

    /** Même image, compressée en PNG : quelques ko, pour ne pas alourdir le widget. */
    fun png(largeurPx: Int, e: EtatCuve, p: Palette, mini: Double, maxi: Double): ByteArray {
        val flux = java.io.ByteArrayOutputStream()
        image(largeurPx, e, p, mini, maxi).compress(Bitmap.CompressFormat.PNG, 100, flux)
        return flux.toByteArray()
    }

}

/** Le grand cadran de l'écran d'une cuve. */
class JaugeView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val palette = Palette.actuelle(ctx)

    var etat: EtatCuve? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Échelle du cadran (Réglages). */
    var echelle: Pair<Double, Double> = ECHELLE_MIN_DEFAUT to ECHELLE_MAX_DEFAUT
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(largeur: Int, hauteur: Int) {
        val l = MeasureSpec.getSize(largeur)
        setMeasuredDimension(l, Jauge.hauteur(l.toFloat()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        etat?.let { Jauge.dessiner(canvas, width.toFloat(), it, palette, echelle.first, echelle.second) }
    }
}
