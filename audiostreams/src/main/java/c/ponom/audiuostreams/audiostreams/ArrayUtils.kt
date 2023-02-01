@file:Suppress("unused")

package c.ponom.audiuostreams.audiostreams

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @author Sergey Ponomarev,2022, 461300@mail.ru
 * MIT licence
 */

const val TAG = "Audio Streams"
object ArrayUtils {

    @JvmStatic
    fun shortToByteArray(arr: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(arr.size * 2)
        byteBuffer.asShortBuffer().put(arr)
        return byteBuffer.array()
    }

    @JvmStatic
    fun byteToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).asShortBuffer()[shorts]
        return shorts
    }

    /** Convert a byte array to the short array using little-endian ordering.<BR>
     * In an android sound subsystem and most native 16-bit audio formats audio samples in
     * the form of signed short values, stored and processed by hardware as little-endian byte
     * arrays.<BR>
     * Mono samples:<BR>
     * [Short value,...] - > [byte1,byte2,...]
     * <BR>
     * Stereo samples: <BR>
     * [Short value left,value right,...] - > [byte1 left,byte2 left, byte1 right,byte2 right]
     *
     *
     */
    @JvmStatic
    fun byteToShortArrayLittleEndian(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
        return shorts
    }

    /** Convert a short array to the byte array using little-endian ordering.<BR>
     * In the android sound subsystem and most of native 16-bit audio formats audio samples
     * in the form of signed short values stored and processed by hardware as little-endian byte
     * arrays.<BR>
     * Mono samples:<BR>
     * [Short value,...] - > [byte1,byte2,...]
     * <BR>
     * Stereo samples: <BR>
     * [Short value left,value right,...] - > [byte1 left,byte2 left, byte1 right,byte2 right]
     *
     */
    @JvmStatic
    fun shortToByteArrayLittleEndian(shorts: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(shorts)
        return byteBuffer.array()
    }

    /** Return array with size winLength containing last winLength Short samples of the data Array.
     *  If winLength > data.size, last winLength samples located at the end of the resulting
     *  array and the beginning of the array is filled with zeroes.
     * <p>
     *  @return Shorts array filled with last winLength samples of the data array
     */
    @JvmStatic
    fun getSlidingWindow(data: ShortArray, winLength: Int): ShortArray {
        val copyWindowSize = winLength.coerceAtMost(data.size)
        val resultingArray = ShortArray(winLength)
        val startingPosSource = (data.size - copyWindowSize).coerceAtLeast(0)
        System.arraycopy(
            data, startingPosSource, resultingArray,
            resultingArray.size - copyWindowSize, copyWindowSize
        )
        return resultingArray
    }


}