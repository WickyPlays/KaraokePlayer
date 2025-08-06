package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import me.wickyplays.android.karaokeplayer.R

class KaraokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var progress: Float = 0f // Progress from 0.0 to 1.0
    private val highlightPaint = Paint(paint).apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
    }
    private val normalPaint = Paint(paint).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
    }
    private val textBounds = Rect()

    fun setKaraokeProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val text = text.toString()
        if (text.isEmpty()) return

        paint.getTextBounds(text, 0, text.length, textBounds)
        val textWidth = paint.measureText(text)
        val textHeight = textBounds.height().toFloat()
        val baseline = height / 2f + textHeight / 2f - textBounds.bottom
        val startX = paddingLeft.toFloat()

        val splitX = startX + textWidth * progress

        // Draw highlighted portion (red) up to splitX
        canvas.withClip(startX, 0f, splitX, height.toFloat()) {
            drawText(text, startX, baseline, highlightPaint)
        }

        // Draw normal portion (white) from splitX to the end
        canvas.withClip(splitX, 0f, width.toFloat(), height.toFloat()) {
            drawText(text, startX, baseline, normalPaint)
        }
    }
}