package c.ponom.audiostreams.audio_streams


import kotlin.math.abs
import kotlin.math.sqrt

object SoundProcessingUtils {



    fun getMaxVolume(data: ShortArray): Short = data.maxOf { abs(it.toInt()) }.toShort()

    // должно вернуть 1.0 для макс громкости
    fun getMaxVolumeFloat(data: ShortArray): Float = getMaxVolume(data).toFloat() / Short.MAX_VALUE


    fun getRMS(data: ShortArray): Short {
        var sum = 0.0
        for (element in data) {
            sum += (element * element)
        }
        return sqrt(sum / data.size).coerceAtMost(Short.MAX_VALUE - 1.0).toInt().toShort()
    }

    fun getRMSFloat(data: ShortArray): Float {
        return (getRMS(data).toFloat() / Short.MAX_VALUE)
    }
}