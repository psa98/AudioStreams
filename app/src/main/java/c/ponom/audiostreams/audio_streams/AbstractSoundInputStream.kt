package c.ponom.recorder2.audio_streams

import android.media.MediaFormat
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


abstract class AbstractSoundInputStream :    InputStream {


    /**Sampling rate measured in samples|sec, sound stream durationString if known, for example, when we
     * work with audio file content - in ms.
     * вероятно этим конструктором следует пользоваться при создании потока из устройства
     * (или иного потока бесконечных raw data)
    */
    constructor(streamDuration: Long = 0, channels: Int = 0, samplingRate: Int = 0){
        duration=streamDuration
        channelsCount=channels
        sampleRate=samplingRate

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
        if (channelsCount != null) this.channelsCount=channelsCount

    }

    // переделать в будущем под поддержку 8 битных
    open var bytesPerSample: Int =0

    @Volatile
    open var bytesSent = 0L

    /**
     * Override the method to return -1 if there is no estimated stream length (for example,for endless
     * streams) or return estimated total count bytes in stream if known
     */
    open fun totalBytesEstimate():Long{
        val bytesEstimate=((this.sampleRate*this.duration*2.0f*this.channelsCount)/1000.0).toLong()
        return if (bytesEstimate==0L) -1L else bytesEstimate

    }


    var mediaFormat:MediaFormat?=null

    var duration: Long = 0

    var channelsCount:Int = 0

    var sampleRate:Int =0

    var frameSize: Int=0 // посчитать размер в конструкторе


    /**
     * todo Кривоват мой английский. Перевести перед выкладкой остальное в реальном классе?
     * call function before returning from read (...) methods in realisation of your classes to
     * return bytesSend or other info for getting callback method for updating info on stream
     * state and about new data after blocking reading operations
     */
    open var onReadCallback: ((alreadySent:Long) -> Unit)? ={  }


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


    abstract override fun read(): Int

    abstract override fun read(b: ByteArray?, off: Int, len: Int): Int

    @Throws(IllegalArgumentException::class)
    override fun skip(n: Long): Long {
        throw IllegalArgumentException("Skip not supported ")
    }
    @Throws(IllegalArgumentException::class)
    override fun mark(readlimit: Int) {
        throw IllegalArgumentException("Mark/reset not supported ")

    }
    @Throws(IllegalArgumentException::class)
    override fun reset() {
        throw IllegalArgumentException("Mark/reset not supported ")
    }
    override fun markSupported(): Boolean {
        return false
    }


}