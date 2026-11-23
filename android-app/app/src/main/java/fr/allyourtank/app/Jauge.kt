package fr.allyourtank.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le cadran : un demi-cercle de 0 à 30 °C, la fourchette en bande ambre,
 * l'aiguille sur la température. Même dessin dans l'appli et dans le widget.
 */
object Jauge {
    private const val MAX = 30.0

    /** Hauteur du dessin pour une largeur donnée. */
    fun hauteur(largeur: Float): Float = largeur * 0.62f

    fun dessiner(canvas: Canvas, largeur: Float, e: EtatCuve, p: Palette) {
        // Géométrie pensée sur une largeur de 100, puis mise à l'échelle
        val s = largeur / 100f
        val cx = 50f * s
        val cy = 54f * s
        val r = 40f * s
        val cercle = RectF(cx - r, cy - r, cx + r, cy + r)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f * s }

        // Piste de fond : de 180° (gauche) à 360° (droite), en passant par le haut
        trait.color = p.piste
        trait.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(cercle, 180f, 180f, false, trait)

        // Fourchette autour de la cible
        if (e.cible != null && e.fourchette != null) {
            val debut = (e.cible - e.fourchette).coerceIn(0.0, MAX)
            val fin = (e.cible + e.fourchette).coerceIn(0.0, MAX)
            if (fin > debut) {
                trait.color = p.bande
                trait.strokeCap = Paint.Cap.BUTT
                canvas.drawArc(cercle, 180f + angle(debut), angle(fin) - angle(debut), false, trait)
            }
        }

        // Aiguille
        if (e.temp != null) {
            val couleur = when {
                e.ancienne -> p.attenue
                e.horsFourchette -> p.alerte
                else -> p.encre
            }
            val a = Math.PI * (1 - e.temp.coerceIn(0.0, MAX) / MAX)
            val aiguille = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = couleur
                strokeWidth = 3.5f * s
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx, cy, cx + 34f * s * cos(a).toFloat(), cy - 34f * s * sin(a).toFloat(), aiguille)
            aiguille.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 4.5f * s, aiguille)
        }
    }

    // ------------------------------------------------------------------ version fine du widget

    /** Hauteur du cadran à graduations pour une largeur donnée. */
    fun hauteurGraduations(largeur: Float): Float = largeur * 0.58f

    /**
     * Cadran à graduations (widget) : un trait par degré, plus long tous les 5 °C,
     * les traits de la fourchette en ambre, une aiguille courte et fine.
     */
    fun dessinerGraduations(canvas: Canvas, largeur: Float, e: EtatCuve, p: Palette) {
        val s = largeur / 100f
        val cx = 50f * s
        val cy = 52f * s
        val r = 40f * s
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }

        for (v in 0..MAX.toInt()) {
            val degre = v.toDouble()
            val dansFourchette = e.cible != null && e.fourchette != null &&
                degre >= e.cible - e.fourchette - 1e-6 && degre <= e.cible + e.fourchette + 1e-6
            val long = v % 5 == 0
            val a = Math.PI * (1 - v / MAX)
            val r1 = r - (if (long) 7f else 4f) * s
            trait.color = if (dansFourchette) p.bande else p.piste
            trait.strokeWidth = (if (dansFourchette) 1.4f else 1f) * s
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
            val a = Math.PI * (1 - e.temp.coerceIn(0.0, MAX) / MAX)
            val aiguille = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = couleur
                strokeWidth = 1.4f * s
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx, cy, cx + 28f * s * cos(a).toFloat(), cy - 28f * s * sin(a).toFloat(), aiguille)
            aiguille.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 2.2f * s, aiguille)
        }
    }

    /** Image du cadran à graduations, pour le widget. */
    fun image(largeurPx: Int, e: EtatCuve, p: Palette): Bitmap {
        val b = Bitmap.createBitmap(largeurPx, hauteurGraduations(largeurPx.toFloat()).toInt(), Bitmap.Config.ARGB_8888)
        dessinerGraduations(Canvas(b), largeurPx.toFloat(), e, p)
        return b
    }

    private fun angle(v: Double): Float = (v / MAX * 180.0).toFloat()
}

/** Le grand cadran de l'écran d'une cuve. */
class JaugeView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {
    private val palette = Palette.actuelle(ctx)

    var etat: EtatCuve? = null
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
        etat?.let { Jauge.dessiner(canvas, width.toFloat(), it, palette) }
    }
}
