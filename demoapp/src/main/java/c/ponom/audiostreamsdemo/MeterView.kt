package c.ponom.audiostreamsdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import kotlin.math.log

private const val SILENCE_THRESHOLD_VOLUME = 36.0

class MeterView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )


    private var backGroundColor = run {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        typedValue.data

    }
        set(value) {
            field = value
            invalidate()
        }


    var level = 0f
        set(value) {
            field = logOfLevel(value.toDouble()) //db scale
            invalidate()
        }


    private fun logOfLevel(baseLevel: Double): Float {
        val logBase = 10.0
        val modifiedLevel = baseLevel.coerceAtLeast(SILENCE_THRESHOLD_VOLUME)
        val levelLog = log(modifiedLevel, logBase)
        val baseLog = log(SILENCE_THRESHOLD_VOLUME, logBase)
        val levelFromBase = levelLog - baseLog
        return (levelFromBase / log(Short.MAX_VALUE.toDouble(), logBase) * 1.5).toFloat()
    }


    private val paintLevelTop: Paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        color = backGroundColor
        strokeWidth = 2f
    }

    private val paintLevelBorder: Paint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.apply {
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintLevelBorder)
            drawRect(0f, 0f, width.toFloat(), height * (1f - level), paintLevelBorder)
            drawRect(0f, 0f, width.toFloat(), height * (1f - level), paintLevelTop)
        }
    }
}



