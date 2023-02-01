package c.ponom.audiuostreams.audiostreams

import android.media.AudioFormat
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaFormat
import androidx.annotation.IntRange
import java.io.IOException
import java.io.InputStream


/**
 * @author Sergey Ponomarev,2022, 461300@mail.ru
 * MIT licence
 * This abstract class is the superclass for classes representing
 * a stream of bytes implementing standard for android binary representation of low-level
 * sound stream: 16bit, little endian, left channel than right channel signed shorts in two
 * octet pairs.
 * Android audio subsystems use such audio format for raw audio data as the input
 * and output formats for audio devices and media codecs. Other raw audio formats
 * (more than 2 channels, 8, 24 or 32 bit samples) used in limited circumstances and
 * usually not for audio music of voice playback or recording.
 * <p> Applications that need to define a subclass of <code>AudioInputStream</code>
 * must always provide method <code>read(b: ByteArray?, off: Int, len: Int): Int </code>
 * A method that returns the next byte of input must be provided, but for many real implementations
 * won't have sense and can just throw UnsupportedOperationException.
 *
 * @see     java.io.ByteArrayInputStream
 * @see     java.io.InputStream#read(byte b[], int off, int len)
 */

abstract class AudioInputStream protected constructor() :    InputStream(), AutoCloseable {


    /**  Class constructor.
     * @param samplingRate the source sample rate expressed in Hz.
     * @param channelsNumber describes the number of the audio channels. It is NOT configuration of
     * the audio channels:
     *   See {@link AudioFormat#CHANNEL_OUT_MONO} and
     *   {@link AudioFormat#CHANNEL_OUT_STEREO}
     *  @param streamDuration is duration in ms of input stream if known, for example, for streams
     *  from audio files.
     * The constructor could also be used for creating potentially endless streams.
     *
     */
    @JvmOverloads
    constructor(  @IntRange(from = 2400, to= 96000) samplingRate:Int,
                  @IntRange(from = 1, to=2)channelsNumber: Int,
                  streamDuration: Long = 0) : this() {
        duration=streamDuration
        channelsCount=channelsNumber
        sampleRate=samplingRate
        channelConfig=channelConfig(channelsNumber)
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        frameSize=bytesPerSample*channelsCount
    }



    /** Class constructor.
     *  This constructor can be used, for example, when stream created with data from
     * AudioDataInfo, MediaExtractor or MediaEncoder classes.<BR>
     * @param format valid MediaFormat of media stream source.
     * todo - переделать что можно на этот конструктор
     */
    @Throws(IllegalArgumentException::class)
    constructor(format: MediaFormat) : this() {
        mediaFormat=format
        val duration = mediaFormat?.getLong("durationUs")?.div(1000)
        val sampleRate = mediaFormat?.getInteger("sample-rate")
        val channelsCount = mediaFormat?.getInteger("channel-count")

        var encoding:Int?
        try {
            encoding= mediaFormat?.getInteger("pcm-encoding")
            if (encoding!= ENCODING_PCM_16BIT)
                throw IllegalArgumentException ("Only PCM 16 bit encoding currently supported")
        }catch (e:NullPointerException){
            // если ключа нет, метод бросает это исключение, то все в порядке, 16 битная кодировка
            encoding = ENCODING_PCM_16BIT
        }
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        if (duration != null) this.duration=duration
        if (sampleRate != null) this.sampleRate=sampleRate
        if (channelsCount != null) {
            this.channelsCount=channelsCount
            channelConfig=channelConfig(channelsCount)
        }
        if (this.sampleRate<=0||channelsCount !in 1..2) throw
            IllegalArgumentException ("Need valid sampleRate and channelsCount parameters in MediaFormat" )

        frameSize=bytesPerSample*this.channelsCount
   }

    /**
     * The sample rate of an audio format, in Hz
     * The associated value is an integer. Android hardware typically supports sample rates
     * from 8000 to 48000, with standard values 8000,11025,12000,16000,22050,24000,32000,
     * 44100,48000.
     */
    var sampleRate:Int =0
        protected set

    /**
     * The audio format of stream as MediaFormat if known or null
     * */
    var mediaFormat:MediaFormat?=null
        protected set

    /** Must be set in constructor of implementing class
    * The duration in ms of finite audio stream if known or 0
    * */
    var duration: Long = 0
        protected set

    /** Must be set in constructor of implementing class
     * Value = 1 for mono and 2 for stereo audio streams.
     * Values outside 1..2 range mean unfinished initialisation or non-standard audio stream
     * */
    var channelsCount:Int = 0
        protected set

    /**
     * Must be set in constructor of implementing class as CHANNEL_IN_MONO or CHANNEL_IN_STEREO.
     * Any other range means unfinished initialisation or non-standard audio stream.
     * @see channelConfig(channels: Int)
     * */
    var channelConfig:Int= AudioFormat.CHANNEL_INVALID
        protected set

    /** Always 2, as ENCODING_PCM_8BIT currently not supported
     * */
    var bytesPerSample: Int =2
        protected set

    /** Must be set in constructor of implementing class to 4 for stereo and 2 for mono streams
     * */
    var frameSize: Int=bytesPerSample*channelsCount
        protected set

    /** Always ENCODING_PCM_16BIT as ENCODING_PCM_8BIT currently not supported
    * */
    var encoding:Int= ENCODING_PCM_16BIT
        protected set

    /**
     * The current time position of audio stream in ms from begin, calculated from the number
     * of bytes read from stream
     */
    @Volatile
    open var timestamp=0L //пересчет выведенных байтов в мс.
        protected set


    /**
     * The number of bytes already read from stream
     * */
    @Volatile
    open var bytesRead = 0L
    @Synchronized
    protected set(value) {
            field=value
            timestamp=(frameTimeMs(sampleRate)*value).toLong()
    }

    /**
     * A callback will be called on each read(...) and readShorts(...)
     *
     */
    open var onReadCallback: ((sentBytes:Long) -> Unit)? ={  }



    /**
     * @return estimated total number of bytes in stream, calculated from the stream duration,
     * if known, or -1 if duration unknown
     *
     * Override the method to return -1 if there is no estimated stream length (for example,
     * for endless streams)
     */
    open fun totalBytesEstimate():Long{
        val bytesEstimate=((this.sampleRate*this.duration*bytesPerSample*
                this.channelsCount)/1000.0).toLong()
        return if (bytesEstimate==0L) -1L else bytesEstimate
    }

    /**
     * @return -1 if there is no estimated stream length (for example, for endless streams)
     * or estimated number of unplayed bytes in the stream
     */
    open fun bytesRemainingEstimate():Long{
        return if (totalBytesEstimate()<0) -1 else
            (totalBytesEstimate()-bytesRead).coerceAtLeast(0)
    }

    /**
    * @return an estimate of the number of bytes that can be read (or skipped over) from this input
     stream without blocking by the next invocation of a method for this input stream single read
     or skip of this many bytes will not block, but may read or skip fewer bytes.
    Note that while some implementations of AudioInputStream will return the total number of bytes
    in the stream, many will not. It is never correct to use the return value of this method
    to allocate a buffer intended to hold all data in this stream.
     */
    override fun available(): Int {
        return (totalBytesEstimate()-bytesRead).toInt().coerceAtLeast(0)
    }


    /**
     * Closes this input stream and releases any system resources associated.
     * Read(...) calls are no longer valid after this call and can throw exception, be ignored
     * or return -1
     * @exception  IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract override fun close()

    /**
     * Read the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data are available, the end of the stream is detected,
     * or an exception is thrown.
     *     * <p> A subclass must provide an implementation of this method.
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract override fun read(): Int

    /**
     * Reads up to <code>len</code> bytes of data from the audio stream into
     * an array of bytes.  An attempt is made to read as many as
     * <code>len</code> bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     *
     * <p> This method blocks until input data are available, end of file is
     * detected, or an exception is thrown.
     *
     * <p> If <code>len</code> is zero, then no bytes are read and
     * <code>0</code> is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at the end of
     * file, the value <code>-1</code> is returned; otherwise, at least one
     * byte is read and stored into <code>b</code>.
     *
     * <p> The first byte read is stored into element <code>b[off]</code>, the
     * next one into <code>b[off+1]</code>, and so on. The number of bytes read
     * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
     * bytes actually read; these bytes will be stored in elements
     * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
     * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the array <code>b</code>
     *                   at which the data are written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException If the first byte cannot be read for any reason
     * other than the end of the file, or if the input stream has been closed, or if
     * some other I/O error occurs.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IllegalArgumentException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>b.length - off</code>
     * @see        java.io.InputStream#read()
     */
    @Throws(IOException::class)
    abstract override fun read(b: ByteArray?, off: Int, len: Int): Int


    /**
     * Reads up to <code>b.len</code> bytes of data from the input stream into
     * an array of bytes calling read(b,0, b.size)
     */
     @Throws(IOException::class)
    override fun read(b: ByteArray?): Int {
        if (b == null) throw NullPointerException("Null byte array passed") else
            return read(b,0, b.size)
    }

    /**
     * Reads up to <code>len</code> samples of data from the audio stream into
     * an array of shorts if implementing class can do that
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the array <code>b</code>
     *                   at which the data are written.
     * @param      len   the maximum number of short values to read.
     * @return     the total number of shorts read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException If the first sample cannot be read for any reason
     * other than the end of the file, or if the input stream has been closed, or if
     * some other I/O error occurs.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IllegalArgumentException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>b.length - off</code>
     * @see        java.io.InputStream#read()
     */
    @Throws(IOException::class)
    open fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        throw NoSuchMethodException("Check canReadShorts() value. Implementing class  must override " +
                "readShorts(b: ShortArray, off: Int, len: Int) and canReadShorts()")
    }

    /**
     * Reads up to <code>b.len</code> bytes of data from the input stream into
     * an array of bytes calling read(b,0, b.size)
     *
     * @param      b     the buffer into which the data is read.
     * @return     the total number of shorts read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException If the first sample cannot be read for any reason
     * other than the end of the file, or if the input stream has been closed, or if
     * some other I/O error occurs.
     * @exception  NullPointerException If <code>b</code> is <code>null</code>.
     * @exception  IllegalArgumentException If <code>off</code> is negative,
     * <code>len</code> is negative, or <code>len</code> is greater than
     * <code>b.length - off</code>
     * @see        java.io.InputStream#read()
     */
    @Throws(IOException::class)
    open fun readShorts(b: ShortArray): Int {
        throw NoSuchMethodException("Check canReadShorts() value. Implementing class  must override " +
                "readShorts(b: ShortArray) and canReadShorts()")
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from this audio
     * stream. The <code>skip</code> method may, for a variety of reasons, end
     * up skipping over some smaller number of bytes, possibly <code>0</code>.
     * The implementation may depend on the ability to seek for concrete type
     * of audio stream
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if the stream does not support seek,
     *                          or if some other I/O error occurs.
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        throw IllegalArgumentException("Skip not supported ")
    }

    /**
     * Repositions this stream to the position at the time the
     * <code>mark</code> method was last called on this input stream if implementation of class
     * support mark() and reset()
     * @see mark()
     * @see reset()
     * @exception  IOException  if this stream has not been marked or if the
     *               mark has been invalidated.
     */
    override fun mark(readlimit: Int) {
        throw IllegalArgumentException("Mark/reset not supported ")
    }

    /**
     * Repositions this audio stream to the position at the time the
     * <code>mark</code> method was last called on this input stream if the implementation of class
     * support mark() and reset()
     * @exception  IOException  if this stream has not been marked or if the
     *               mark has been invalidated.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.IOException
     */
    @Throws(IOException::class)
    override fun reset() {
        throw IllegalArgumentException("Mark/reset not supported ")
    }

    /**
     * Tests if this input stream supports the <code>mark</code> and
     * <code>reset</code> methods. Whether or not <code>mark</code> and
     * <code>reset</code> are supported is an invariant property of a
     * particular input stream instance. The <code>markSupported</code> method
     * of <code>InputStream</code> returns <code>false</code>.
     *
     * @return  <code>true</code> if this stream instance supports the mark
     *          and reset methods; <code>false</code> otherwise.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    override fun markSupported(): Boolean {
        return false
    }

    /**
     * True if readShorts(b: ShortArray) and readShorts(b: ShortArray, off: Int, len: Int)
     * methods supported by class
     */
    open fun canReadShorts():Boolean = false


    private fun frameTimeMs(rate:Int):Double{
        return 1000.0/(rate*frameSize.toDouble())
    }

    fun channelConfig(channels: Int) = when (channels) {
        1-> AudioFormat.CHANNEL_IN_MONO
        2-> AudioFormat.CHANNEL_IN_STEREO
        else ->{
            AudioFormat.CHANNEL_INVALID
        }
    }

    override fun toString(): String {
        return "AudioInputStream(sampleRate=$sampleRate, duration=$duration, channelsCount=$channelsCount, encoding=$encoding)"
    }


}