

@file:Suppress("unused")

package c.ponom.recorder2.audio_streams

import android.media.AudioFormat.*
import java.io.IOException
import java.lang.Integer.min
import kotlin.math.PI
import kotlin.math.sin


class TestSoundInputStream private constructor() : AbstractSoundInputStream()  {



    private var prepared: Boolean=false
    private var closed: Boolean=false
    var testFrequency = 0.0
    var volume: Short = 0
    var periodDuration =0.0
    var samplesInPeriod=0.0

    @JvmOverloads
    @Throws(IllegalArgumentException::class,IOException::class)
    constructor (
        testFreq: Short, volume: Short,
        sampleRate: Int = 16000,
        channelConfig: Int = CHANNEL_IN_MONO,
        encoding: Int = ENCODING_PCM_16BIT,
    ) : this() {
        if (!(channelConfig== CHANNEL_IN_MONO ||channelConfig== CHANNEL_IN_STEREO))
            throw IllegalArgumentException("Only CHANNEL_IN_MONO and CHANNEL_IN_STEREO supported")
        if (!(encoding== ENCODING_PCM_8BIT ||encoding== ENCODING_PCM_16BIT))
            throw IllegalArgumentException("Only 16 and 8 bit encodings supported")
        // кинуть предупреждение если частоты в неслышимом диапазоне
        channelsCount = if (channelConfig== CHANNEL_IN_MONO) 1 else  2
        this.sampleRate = sampleRate
        this.volume = volume
        testFrequency=testFreq.toDouble()
        periodDuration=1.0/testFrequency
        samplesInPeriod=sampleRate.toDouble()/testFrequency
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме 16,22 и 44к
        //  и исключение для совсем левых
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2 else  1
        frameSize=bytesPerSample*channelsCount
        prepared=true

}

    //доработать вызов после закрытия потока -

    // тестить
   override fun read(): Int {
       val b=ByteArray(1)
       if (closed) return -1
       return read(b)+128
   }

   @Throws(NullPointerException::class)
   override fun read(b: ByteArray?): Int {
       if (b==null) throw NullPointerException ("Null array passed")
       return read(b,0,b.size)
   }



    @Volatile
   override var bytesSent: Long = 0

   /**
    * Return -1 when there is no estimated stream length (for example,for endless streams)
    * or estimated rest of bytes in stream
    */
   override fun totalBytesEstimate(): Long {
       return -1
   }

    /**
     * Return -1 when there is no estimated stream length (for example,for endless streams)
     * or estimated rest of bytes in stream
     */
   override fun bytesRemainingEstimate(): Long {
       return -1L
   }

    /**
     * смотри описание исходного. Теоретически тут надо сообщить сколько можно отдать без
     * блокирования из буферов, подумать реализуемо ли это
     */
   override fun available(): Int {
       return 0
   }


    /**
     * Params:
     * audioData – the array to which the recorded audio data is written.
     * offset – 0
     * Must not be negative, or cause the data access to go out of bounds of the array.
     * size – the number of requested shorts. Must not be negative, or cause the data
     * access to go out of bounds of the array.
     * Returns:
     * zero or the positive number of bytes that were read, or one of the following error codes.
     * Minus 1 if stream is closed (ended)
     * The number of shorts will be a multiple of the channel count not to exceed sizeInShorts.
     *AudioRecord.ERROR_INVALID_OPERATION if the object isn't properly initialized
     * AudioRecord.ERROR_BAD_VALUE if the parameters don't resolve to valid data and indexes
     * AudioRecord.ERROR_DEAD_OBJECT if the object is not valid anymore and needs to be recreated.
     * The dead object error code is not returned if some data was successfully transferred.
     * In this case, the error is returned at the next read() ERROR in case of other error
     */

    @Synchronized
    @Throws(NullPointerException::class,IllegalArgumentException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
        if (closed) return -1
        val newBytes = readNextBytes(min(len,b.size))
        newBytes.copyInto(b)
        val bytes= newBytes.size
        bytesSent+=bytes
        onReadCallback?.invoke(bytesSent)
        return bytes
   }

    private fun readNextBytes(len: Int): ByteArray {
        return ByteArray(len)
    }

    /**
     *
     */

    @Synchronized
    @Throws(NullPointerException::class)
    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (closed) return -1
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
        val length = min(b.size,len)
        val dataArray=ShortArray(length)
        dataArray.forEachIndexed { index, value ->
            dataArray[index] = calculateSampleValue(index.toLong())
        }
        dataArray.copyInto(b)
        bytesSent+=len*2
        return len
    }

    @Synchronized
    override fun readShorts(b: ShortArray): Int {
        if (closed) return -1
        return if (b.isEmpty()) 0 else readShorts(b, 0, b.size)
    }

    override fun canReturnShorts():Boolean =true

    //todo - как идея - можно унифицировать поведение скип и клоуз с filestream - то есть для
    // закрытых потоков кидать исключение а не игнорить
    @Synchronized
    override fun close() {
       prepared=false
       closed=true
    }

    private fun calculateSampleValue(sampleNum:Long):Short{
        val x =(sampleNum/samplesInPeriod*2*PI)
        return (sin(x)*volume).toInt().toShort()
    }



}


