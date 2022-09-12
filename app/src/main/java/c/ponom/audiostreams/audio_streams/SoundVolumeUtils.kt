@file:Suppress("MemberVisibilityCanBePrivate")

package c.ponom.audiostreams.audio_streams


import kotlin.math.abs
import kotlin.math.sqrt
@Suppress("unused")
object SoundVolumeUtils {

    /**
     * @return Peak volume for audio data chunk scaled from 0 to  Short.MAX_VALUE
     * */
    fun getMaxVolume(data: ShortArray): Short = data.maxOf { abs(it.toInt()) }.toShort()

    /**
     * @return peak volume for audio data chunk scaled from 0.0f to  1.0f
     * */

    fun getMaxVolumeFloat(data: ShortArray): Float = getMaxVolume(data).toFloat() / Short.MAX_VALUE


    /**
     * @return average RMS volume level for audio data chunk scaled from 0 to  Short.MAX_VALUE
     * */
    fun getRMSVolume(data: ShortArray): Short {
        var sum = 0.0
        for (element in data) sum += (element * element)
        return sqrt(sum / data.size).coerceAtMost(Short.MAX_VALUE.toDouble()).toInt().toShort()
    }


    /**
     * @return average RMS volume level for audio data chunk scaled from 0.0f to  1.0f
     * */
    fun getRMSFloat(data: ShortArray): Float {
        return (getRMSVolume(data).toFloat() / Short.MAX_VALUE)
    }
}