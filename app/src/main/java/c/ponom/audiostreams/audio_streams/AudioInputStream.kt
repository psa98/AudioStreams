package c.ponom.recorder2.audio_streams

import android.media.AudioFormat
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.media.MediaFormat
import java.io.IOException
import java.io.InputStream


/**
 * This abstract class is the superclass for classes representing
 * an  stream of bytes implementing standard for android binary representation of low level
 * sound stream: 16bit, little  endian, left channel than right channel two octet pairs.
 * Android audio subsystems use such audio format for raw audio data as input for media codecs
 * and as input and output format for audio devices. Other raw audio formats (more than 2 channels,
 * 8, 24 or 32 bit samples) used in very limited circumstances
 *
 * <p> Applications that need to define a subclass of <code>AudioInputStream</code>
 * must always provide a method that returns the next byte of input.
 *
 *
 * @see     java.io.BufferedInputStream
 * @see     java.io.ByteArrayInputStream
 * @see     java.io.InputStream#read()

 */


abstract class AudioInputStream :    InputStream, AutoCloseable {


    /**Sampling rate measured in samples|sec, sound stream durationString if known, for example, when we
     * work with audio file content - in ms.
     * вероятно этим конструктором следует пользоваться при создании потока из устройства
     * (или иного потока бесконечных raw data)
    */
    constructor(streamDuration: Long = 0, channels: Int = 0, samplingRate: Int = 0){
        duration=streamDuration
        channelsCount=channels
        sampleRate=samplingRate
        channelConfig=channelConfig(channels)

    }

    constructor()

    /** This constructor can be used, for example, when stream created with data from
     * MediaExtractor or MediaEncoder classes
     */
    constructor(format: MediaFormat){
        mediaFormat=format
        val duration = mediaFormat?.getLong("durationUs")?.div(1000)
        val sampleRate = mediaFormat?.getInteger("sample-rate")
        val channelsCount = mediaFormat?.getInteger("channel-count")
        if (duration != null) this.duration=duration
        if (sampleRate != null) this.sampleRate=sampleRate
        if (channelsCount != null) {
            this.channelsCount=channelsCount
            channelConfig=channelConfig(channelsCount)
        }

    }


    var bytesPerSample: Int =2 // для 16 бит всегда 2
        protected set
    var encoding:Int= ENCODING_PCM_16BIT
        protected set

    @Volatile
    open var timestamp=0L //todo  пересчет выведенных байтов в мс.
        protected set
    @Volatile
    open var bytesSent = 0L
        protected set(value) {
            field=value
            timestamp=(frameTimeMs(encoding,sampleRate,channelsCount)*value).toLong()
        }


    /**
     * Override the method to return -1 if there is no estimated stream length (for example,for endless
     * streams) or return estimated total count bytes in stream if known
     */
    open fun totalBytesEstimate():Long{
        val bytesEstimate=((this.sampleRate*this.duration*2.0f*this.channelsCount)/1000.0).toLong()
        return if (bytesEstimate==0L) -1L else bytesEstimate

    }

    // будет содержать валидное значение если это возможно, к примеру для потока из файла
    var mediaFormat:MediaFormat?=null
        protected set
    // будет содержать валидное значение если это возможно, к примеру для потока из файла
    var duration: Long = 0
        protected set
    // значение 0 не валидно, его использование указывает на незавершенность инциализации
    var channelsCount:Int = 0
        protected set

    // не путать с числом каналов! см. channelConfig(channels: Int)
    // одно из законных значений - CHANNEL_IN_MONO,CHANNEL_IN_STEREO.
    //должно быть выставлено при создании канала
    var channelConfig:Int= AudioFormat.CHANNEL_INVALID
        protected set
    // типичный поддерживаемый аппаратурой диапазон - 16000 - 48000, стандартные значения:
    // 8000,11025,12000,16000,22050,24000,32000,44100,48000, гарантированно
    // поддерживается 44100
    var sampleRate:Int =0
        protected set

    var frameSize: Int=0 // всегда считать размер в конструкторе
        protected set


    // переопределите коллбэк для возможности учета вызовов методов чтения из стороннего API,
    // к примеру для реализации индиктора прогресса
    open var onReadCallback: ((sentBytes:Long) -> Unit)? ={  }


    /**
     * Returns -1 if there is no estimated stream length (for example,for endless streams)
     * or estimated number of rest of bytes in the stream
     */
    open fun bytesRemainingEstimate():Long{
        return if (totalBytesEstimate()<0) -1 else
            (totalBytesEstimate()-bytesSent).coerceAtLeast(0)
    }

    /*
    Returns an estimate of the number of bytes that can be read (or skipped over) from this input
     stream without blocking by the next invocation of a method for this input stream.
     The next invocation might be the same thread or another thread. A single read or skip of this
      many bytes will not block, but may read or skip fewer bytes.

    Note that while some implementations of InputStream will return the total number of bytes
    in the stream, many will not. It is never correct to use the return value of this method
    to allocate a buffer intended to hold all data in this stream.
     */
    override fun available(): Int {
        return (totalBytesEstimate()-bytesSent).toInt().coerceAtLeast(0)
    }

    //Для бесконечных потоков всегда 0
    open fun bytesRemaining():Int{
        return 0
    }

    /**
     * Closes this input stream and releases any system resources associated
     * переопределение обязательно - поскольку надо освободить все ресурсы
     * @exception  IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract override fun close()

    @Throws(IOException::class)
    abstract override fun read(): Int


    @Throws(IOException::class)
    abstract override fun read(b: ByteArray?, off: Int, len: Int): Int



    @Throws(IOException::class)
    open fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        throw NoSuchMethodException("Check canReadShorts() value. Implementing class  must override " +
                "readShorts(b: ShortArray, off: Int, len: Int) and canReadShorts()")
    }

    @Throws(IOException::class)
    open fun readShorts(b: ShortArray): Int {
        throw NoSuchMethodException("Check canReadShorts() value. Implementing class  must override " +
                "readShorts(b: ShortArray) and canReadShorts()")
    }



    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        throw IllegalArgumentException("Skip not supported ")
    }

    override fun mark(readlimit: Int) {
        throw IllegalArgumentException("Mark/reset not supported ")

    }
    @Throws(IOException::class)
    override fun reset() {
        throw IllegalArgumentException("Mark/reset not supported ")
    }
    override fun markSupported(): Boolean {
        return false
    }

    open fun canWriteShorts():Boolean =false


    private fun frameTimeMs(encoding:Int, rate:Int,channels: Int):Double{
        val bytesInFrame:Int = when (encoding){
            ENCODING_PCM_8BIT -> channels
            ENCODING_PCM_16BIT -> channels*2
            else-> 0
        }
        return 1000.0/(rate*bytesInFrame.toDouble())
    }

    fun channelConfig(channels: Int) = when (channels) {
        1-> AudioFormat.CHANNEL_IN_MONO
        2-> AudioFormat.CHANNEL_IN_STEREO
        else ->{
            AudioFormat.CHANNEL_INVALID
        }
    }
}