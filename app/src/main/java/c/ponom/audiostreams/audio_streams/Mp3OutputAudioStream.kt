package c.ponom.audiostreams.audio_streams

import android.media.AudioFormat
import c.ponom.recorder2.audio_streams.AbstractSoundOutputStream
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.IOException
import java.io.OutputStream

class Mp3OutputAudioStream private constructor() : AbstractSoundOutputStream(){



    lateinit var androidLame: AndroidLame
    private var finished = false
    private val channelsNumber:Int=1
    lateinit var  outputStream:OutputStream


    @JvmOverloads
    @Throws(IllegalArgumentException::class, UnsupportedOperationException::class)
    constructor(outStream:OutputStream, inSampleRate: Int, channelConfig: Int,encoding:Int, minBufferInMs:Int=0) : this() {
        sampleRate = inSampleRate
        outputStream=outStream
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме
        //  8, 16,22, 32 и 44 - 48к
        //  и исключение для совсем левых
        if (!(channelConfig== AudioFormat.CHANNEL_OUT_MONO ||channelConfig== AudioFormat.CHANNEL_OUT_STEREO))
            throw IllegalArgumentException("Only CHANNEL_OUT_MONO and CHANNEL_OUT_STEREO supported")
        channelsCount = when (channelConfig) {
            AudioFormat.CHANNEL_OUT_MONO -> 1
            AudioFormat.CHANNEL_OUT_STEREO -> 2
            else -> 0
        }
        if (!(encoding== AudioFormat.ENCODING_PCM_8BIT ||encoding== AudioFormat.ENCODING_PCM_16BIT))
            throw IllegalArgumentException("Only 16 and 8 bit encodings supported")
        this.encoding=encoding
        when (encoding){
            AudioFormat.ENCODING_PCM_8BIT -> frameSize = channelsCount
            AudioFormat.ENCODING_PCM_16BIT -> frameSize= channelsCount*2
        }
    }



    /**Важно! OutBitrate - это уровень сжатого потока и он задается  в килобитах/сек! то есть
     * стандартные значения от 32 до 320, а не от 8000 до 48000
     *
     */

    constructor(inSampleRate:Int, outBitrate:Int, outStereoMode: LameBuilder.Mode, outChannelsNum:Int) : this() {

        androidLame= LameBuilder()
            .setInSampleRate(inSampleRate)
            .setMode(outStereoMode )
            .setOutChannels(outChannelsNum)
            .setOutBitrate(outBitrate)
            .build()
    }


    @Throws(IllegalArgumentException::class,NullPointerException::class,
        IllegalStateException::class, IOException::class)
    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (finished) throw IllegalStateException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong write(...) params")
        val interleavedFrames=0
        //посчитать - должно быть для 16 bit (меньшее из размера b,len)-off /2 вероятно

        val result=encodeInterleavedStream(byteToShortArrayLittleEndian(b))
        outputStream.write(result)
        bytesSent += result.size

    }


    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class, IOException::class)
    fun writeShorts(b: ShortArray) {
        write(shortToByteArrayLittleEndian(b),0,b.size*2)
    }


    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class, IOException::class)
    fun writeShorts(b: ShortArray, off: Int, len: Int) {
        write(shortToByteArrayLittleEndian(b),off,b.size*2)
    }

    @Synchronized
    override fun close() {
        val result=encodeEofFrame()
        outputStream.write(result)
        finished=true
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        throw NoSuchMethodException("Not implemented-use write (byte[]....)")
    }

    fun encodeMonoStream(inArray: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        val outBuff = ByteArray(inArray.size)
        val resultBytes = androidLame.encode(inArray, inArray, inArray.size, outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }

    fun encodeStereoStream(leftArray: ShortArray, rightArray: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        if (leftArray.size != rightArray.size) throw IllegalStateException("Both samples have  same length")
        val outBuff = ByteArray(leftArray.size)
        val resultBytes = androidLame.encode(leftArray, rightArray, leftArray.size, outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }

    // убедиться что оно берет little ended  shorts
    fun encodeInterleavedStream(samples: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        val size = samples.size
        val outBuff = ByteArray(size)
        val resultBytes = androidLame.encodeBufferInterLeaved(samples, size, outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }


    //todo надо сначала убедиться что encodeBufferInterLeaved в принципе для входного МОНО понимает
    // что его надо жать как моно и жмет его ровно так же как МОНО до байта.
    // в противном случае придется если у нас на входе шорты моно -
    // отдавать их в оба канала одинаковые, если стерео левый правый - то использовать
    // encodeBufferInterLeaved

    fun encodeEofFrame(): ByteArray {
        if (finished) throw IllegalStateException("Already finished")
        finished = true
        val outBuff = ByteArray(16 * 1024)
        // вообще оно заведомо до 2048 + ограниченный размер тегов, но пусть
        val resultBytes = androidLame.flush(outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }


}

