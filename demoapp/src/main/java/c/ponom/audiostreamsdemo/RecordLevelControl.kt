@file:Suppress("unused", "PackageName")

package c.ponom.audiostreamsdemo


import kotlin.math.abs
import kotlin.math.sign

object RecordLevelControl {



    fun doSimpleProcessing(dataToProcess: ShortArray,k:Float): ShortArray {
        val processedData = ShortArray(dataToProcess.size)
        with(dataToProcess) {
            forEachIndexed { index, _ ->
                apply {
                    processedData[index] = changeVolume(dataToProcess[index],k)
                }
            }
        }
        return processedData
    }


    /*
    * Level limiter, sample value cannot be more than Short.MAX_VALUE
    *
    */
    private fun changeVolume(sample: Short,k:Float): Short {
        val increment = k - 1f
        val sign = sign(sample.toDouble())
        val value = abs(sample.toInt())
        val resultValue =(value + value * linearLimit(value) * increment)
                .coerceAtMost(Short.MAX_VALUE - 1.0)
        return (resultValue * sign).toInt().toShort()
    }


    /*
    * Level limiter
    * x+x*(k)*inc, k = 1.0 at  16384 and drop to 0.0 at Short.MAX_VALUE
    */
    private fun linearLimit(x: Int): Double {
        if (x < Short.MAX_VALUE / 2) return 1.0
        val rest = x - Short.MAX_VALUE / 2
        return 1.0 - (rest.toDouble() / (Short.MAX_VALUE / 2))
            .coerceAtLeast(0.0)
            .coerceAtMost(1.0)
    }
}