package c.ponom.audiostreams

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class Utils {


    fun getRMSVolume(data: ShortArray): Short {
        var sum = 0.0
        for (element in data) sum += (element * element)
        return sqrt(sum / data.size).coerceAtMost(Short.MAX_VALUE - 1.0).numberToShort()
    }


    fun Number.numberToShort(): Short = (this.toInt().toShort())

    fun byteToShortArrayLittleEndian(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
        return shorts
    }

}