package com.omplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils

/** Draws playlists (names and song titles) as a dark card in the app's colours. Uses text layout, so Arabic and other scripts shape correctly. */
internal object PlaylistImage {
    private const val W = 1080
    private const val PAD = 72
    private const val MAX_TRACKS = 40
    private const val MAX_HEIGHT = 12_000

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun layout(text: String, p: TextPaint, lines: Int = 1) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, W - 2 * PAD)
            .setEllipsize(TextUtils.TruncateAt.END).setMaxLines(lines)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    fun render(lists: List<SharedPlaylist>): Bitmap {
        val brand = layout("OM PLAYER", paint(34f, 0xFFF43F5E.toInt(), true))
        val blocks = ArrayList<Pair<StaticLayout, Int>>() // layout, gap before
        lists.forEachIndexed { i, pl ->
            blocks += layout(pl.name, paint(60f, Color.WHITE, true), 2) to (if (i == 0) 56 else 88)
            blocks += layout("${pl.tracks.size} ${if (pl.tracks.size == 1) "track" else "tracks"}", paint(34f, 0xFF8A8A96.toInt())) to 8
            pl.tracks.take(MAX_TRACKS).forEachIndexed { n, t -> blocks += layout("${n + 1}.  $t", paint(40f, 0xFFD4D4DC.toInt())) to (if (n == 0) 36 else 18) }
            if (pl.tracks.size > MAX_TRACKS) blocks += layout("+ ${pl.tracks.size - MAX_TRACKS} more", paint(36f, 0xFF8A8A96.toInt())) to 18
        }
        var h = PAD + brand.height + PAD
        for ((l, gap) in blocks) h += gap + l.height
        val height = h.coerceAtMost(MAX_HEIGHT)
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0A0A0E.toInt())
        c.drawRect(0f, 0f, 14f, height.toFloat(), Paint().apply { color = 0xFFE11D48.toInt() })
        var y = PAD.toFloat()
        fun draw(l: StaticLayout) { c.save(); c.translate(PAD.toFloat(), y); l.draw(c); c.restore(); y += l.height }
        draw(brand)
        for ((l, gap) in blocks) {
            y += gap
            if (y + l.height > height) break
            draw(l)
        }
        return bmp
    }
}
