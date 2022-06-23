package c.ponom.audiostreams

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import kotlin.math.log

private const val PAUSE_THRESHOLD_VOLUME =36

class MeterView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) :
            super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,attrs,defStyleAttr)

    // получаем фон экрана с учетом дня/ночи
    var backGroundColor = run{
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        typedValue.data

    }

    set(value) {
        field=value
        invalidate()
    }

    // уровень для отрисовки - от 0 до 1.0
    var level = 0f
    set(value) {
        field = logOfLevel(value.toDouble(), 10.0)
        invalidate()
    } //переделано под основание логарифма  10, как положено дл децибеллов


    private fun logOfLevel(baseLevel: Double, logBase: Double): Float {
        // функция подобрана так, что бы проходить через 0 в условной точке тишины и 1.0 в
        // Max.Short, сохраняя при этом логарифмический характер и разрешение.
        val silenceLevel = PAUSE_THRESHOLD_VOLUME.toDouble()
        // Нам не надо проблем с log(0) = там будет ошибочное значение
        val modifiedLevel = baseLevel.coerceAtLeast(silenceLevel)
        val levelLog = log(modifiedLevel, logBase)
        val baseLog = log(silenceLevel, logBase)
        val levelFromBase = levelLog - baseLog
        // почему надо отнормировать на 1,5 я не понял, но иначе 100% при Max.Short не выходит.
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



