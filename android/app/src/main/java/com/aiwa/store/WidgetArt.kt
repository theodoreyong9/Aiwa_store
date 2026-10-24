package com.aiwa.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat

/**
 * The widget drawn as PICTURES (Canvas), page by page: the glass of a button (a body in two colours, a sheen on the upper half, a shade on the lower, a lit edge, a glow when it is active),
 * its icon and its word, all with the freedom of a drawing and none of the limits of widget views. The pictures are laid under invisible touch zones of the same proportions
 * (the layout of the page of the widget), so that a tap lands on what is drawn. Only the page « Build » is made this way for now: the person judges it before the others follow.
 */
object WidgetArt {
    /** One button: its icon, its word, its colour (grey, green, red, orange, violet, blue) and whether it answers a tap (an unavailable one is drawn dim). */
    class Btn(val icon: Int, val label: String, val tone: String = "grey", val enabled: Boolean = true)

    // top, bottom, edge, glow (0 = none)
    private fun colours(tone: String): IntArray = when (tone) {
        "green" -> intArrayOf(0xFF3FA37E.toInt(), 0xFF22664A.toInt(), 0xFF8FE3BE.toInt(), 0x9952D6A0.toInt())
        "red" -> intArrayOf(0xFFE5605B.toInt(), 0xFFA8302D.toInt(), 0xFFFFA39F.toInt(), 0x99F27A75.toInt())
        "orange" -> intArrayOf(0xFFE09A7C.toInt(), 0xFFB5603F.toInt(), 0xFFFFCDB6.toInt(), 0x00000000)
        "violet" -> intArrayOf(0xFFA77BD6.toInt(), 0xFF6F4A9E.toInt(), 0xFFDCC3FA.toInt(), 0x00000000)
        "blue" -> intArrayOf(0xFF6B9BF5.toInt(), 0xFF2F5DB8.toInt(), 0xFFC4D3FF.toInt(), 0x663A6ECD.toInt())
        else -> intArrayOf(0xFF716CAE.toInt(), 0xFF45408A.toInt(), 0xFFB3AFE8.toInt(), 0x00000000)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** A page: rows of buttons, each with its weight; the picture is `widthPx` x `heightPx` and has no background of its own (the card of the widget is behind it). */
    fun page(context: Context, widthPx: Int, heightPx: Int, rows: List<List<Pair<Btn, Float>>>): Bitmap {
        val d = context.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val gap = 5f * d
        val rowH = heightPx.toFloat() / rows.size
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.second.toDouble() }.toFloat()
            var x = 0f
            row.forEach { (btn, weight) ->
                val slotW = widthPx * weight / total
                val rect = RectF(x + gap / 2, r * rowH + gap * 0.4f, x + slotW - gap / 2, (r + 1) * rowH - gap * 0.4f)
                button(context, canvas, rect, btn, d)
                x += slotW
            }
        }
        return bmp
    }

    private fun button(context: Context, canvas: Canvas, rect: RectF, btn: Btn, d: Float) {
        val c = colours(btn.tone)
        val radius = minOf(rect.height() / 2f, 22f * d)
        val alpha = if (btn.enabled) 255 else 110
        // the glow of an active button: a blurred halo of its colour behind the body
        if (c[3] != 0 && btn.enabled) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c[3]; maskFilter = BlurMaskFilter(9f * d, BlurMaskFilter.Blur.NORMAL) }
            canvas.drawRoundRect(RectF(rect.left - 1.5f * d, rect.top + 1f * d, rect.right + 1.5f * d, rect.bottom + 2f * d), radius, radius, glow)
        }
        // the body: two colours, top to bottom
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, rect.top, 0f, rect.bottom, withAlpha(c[0], alpha), withAlpha(c[1], alpha), Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(rect, radius, radius, body)
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        // the sheen on the upper half, the shade on the lower half
        val sheen = Paint().apply { shader = LinearGradient(0f, rect.top, 0f, rect.centerY() + rect.height() * 0.1f, withAlpha(0xFFFFFFFF.toInt(), if (btn.enabled) 96 else 40), 0x00FFFFFF, Shader.TileMode.CLAMP) }
        canvas.drawRect(rect, sheen)
        val shade = Paint().apply { shader = LinearGradient(0f, rect.centerY(), 0f, rect.bottom, 0x00000000, withAlpha(0xFF000000.toInt(), 70), Shader.TileMode.CLAMP) }
        canvas.drawRect(rect, shade)
        canvas.restore()
        // the edge: lit at the top, fading toward the bottom
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.3f * d
            shader = LinearGradient(0f, rect.top, 0f, rect.bottom, withAlpha(c[2], if (btn.enabled) 235 else 100), withAlpha(c[2], if (btn.enabled) 70 else 40), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(rect.left + 0.65f * d, rect.top + 0.65f * d, rect.right - 0.65f * d, rect.bottom - 0.65f * d), radius, radius, edge)
        // the icon and the word: the icon above the word, or side by side when the button is low
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textSize = 11.5f * d; letterSpacing = 0.02f
            color = withAlpha(0xFFF4F4FF.toInt(), alpha); textAlign = Paint.Align.LEFT
        }
        val icon = ContextCompat.getDrawable(context, btn.icon)?.mutate()
        val iconSize = (22f * d).toInt()
        val label = TextUtils.ellipsize(btn.label, text, rect.width() - 14f * d, TextUtils.TruncateAt.END).toString()
        val textW = text.measureText(label)
        val low = rect.height() < 40f * d
        if (low) {
            val total = iconSize + 6f * d + textW
            val left = rect.centerX() - total / 2f
            icon?.setBounds(left.toInt(), (rect.centerY() - iconSize / 2f).toInt(), left.toInt() + iconSize, (rect.centerY() + iconSize / 2f).toInt())
            icon?.alpha = alpha; icon?.draw(canvas)
            canvas.drawText(label, left + iconSize + 6f * d, rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
        } else {
            val block = iconSize + 2f * d + (text.descent() - text.ascent())
            val top = rect.centerY() - block / 2f
            icon?.setBounds((rect.centerX() - iconSize / 2f).toInt(), top.toInt(), (rect.centerX() + iconSize / 2f).toInt(), top.toInt() + iconSize)
            icon?.alpha = alpha; icon?.draw(canvas)
            canvas.drawText(label, rect.centerX() - textW / 2f, top + iconSize + 2f * d - text.ascent(), text)
        }
    }
}
