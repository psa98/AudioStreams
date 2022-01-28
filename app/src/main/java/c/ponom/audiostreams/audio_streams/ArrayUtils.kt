package c.ponom.audiostreams.audio_streams

import java.nio.ByteBuffer
import java.nio.ByteOrder


object ArrayUtils {


    fun shortToByteArray(arr: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(arr.size * 2)
        byteBuffer.asShortBuffer().put(arr)
        return byteBuffer.array()
    }


    fun byteToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).asShortBuffer()[shorts]
        return shorts
    }

    fun byteToShortArrayLittleEndian(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
        return shorts
    }


    fun shortToByteArrayLittleEndian(shorts: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(shorts)
        return byteBuffer.array()
    }


    // если массив меньше - возвращенное окно дополняется нулями в начале? с этим сделать второй метод
    fun getSlidingWindow(data: ShortArray, len: Int): ShortArray {
        val copyWindowSize = len.coerceAtMost(data.size)
        // todo -  разобраться и документировать, у меня массив режется дo до _меньшего_ из двух
        // я хотел если меньше дополнять нулями
        val resultingArray = ShortArray(len)
        val startingPosSource = (data.size - copyWindowSize).coerceAtLeast(0)
        System.arraycopy(
            data, startingPosSource, resultingArray,
            resultingArray.size - copyWindowSize, copyWindowSize
        )
        return resultingArray
        // нужны тесты для основных вариантов покрытия, включая окно =1, 0, = размеру буфера
    }


}