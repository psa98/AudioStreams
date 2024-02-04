package c.ponom.audiuostreams.audiostreams

import android.media.AudioFormat.*
import android.media.MediaFormat
import androidx.annotation.IntRange
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

abstract class AudioOutputStream :
    OutputStream,  AutoCloseable{

    /**  Class constructor.
     * @author Sergey Ponomarev, 2022, 461300@mail.ru
     * MIT licence
     * @param samplingRate the source sample rate expressed in Hz.
     * @param channelsNumber describes the number of the audio channels. It is NOT configuration of
     * the audio channels: See <code>AudioFormat.CHANNEL_OUT_MONO</code> and  <code>AudioFormat.CHANNEL_OUT_STEREO</code>
     *
     */
    @Suppress("unused")
    constructor(@IntRange(from = 2400, to = 96000) samplingRate:Int,
                @IntRange(from = 1, to = 2)channelsNumber: Int) : this() {
        channelsCount=channelsNumber
        sampleRate=samplingRate
        channelConfig=channelConfig(channelsNumber)
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        frameSize =bytesPerSample*channelsCount
    }
    protected constructor()


    /**
     * The audio format of stream as MediaFormat if known or null
     * */
    @Suppress("unused")
    var mediaFormat: MediaFormat?=null
    protected set

    /** Must be set in constructor of implementing class
     * Value = 1 for mono and 2 for stereo audio streams.
     * Values outside 1..2 range mean unfinished initialisation or non-standard audio stream
     * */
    var channelsCount:Int = 0
    protected set

    /**
     * Must be set in constructor of implementing class as CHANNEL_OUT_MONO or CHANNEL_OUT_STEREO.
     * Any other value means unfinished initialisation or non-standard audio stream.
     * @see channelConfig(channels: Int)
     * */
    var channelConfig:Int= CHANNEL_INVALID
    protected set

    /**
     * The sample rate of an audio format, in Hz
     * The associated value is an integer. Android hardware typically supports sample rates
     * from 8000 to 48000, with standard values 8000,11025,12000,16000,22050,24000,32000,
     * 44100,48000.
     */
    var sampleRate:Int =0
    protected set

    /**
     * A callback, if set, will be called on each write(...) and writeShorts(...)
     *
     */
    open var onWriteCallback: ((sentBytes:Long) -> Unit)? ={  }

    /** Always 2, as ENCODING_PCM_8BIT currently not supported
     * */
    var bytesPerSample: Int = 2
        protected set

    var frameSize: Int=bytesPerSample*channelsCount
        protected set

    /** Always ENCODING_PCM_16BIT as ENCODING_PCM_8BIT currently not supported
     * */
    var encoding:Int= ENCODING_PCM_16BIT
    protected set

    /** Time from stream start, in ms.
     * */
    @Volatile
    var timestamp=0L
    protected set


    private val bytesSentInternal: AtomicLong = AtomicLong(0L)
    protected fun moreBytesSent(moreBytesSent: Number) {
        val bytesSent = bytesSentInternal.addAndGet(moreBytesSent.toLong())
        timestamp = (frameTimeMs(sampleRate) * bytesSent).toLong()
    }
    /**
     * The number of bytes already sent to stream
     * */
    val bytesSent
        get() = bytesSentInternal.get()

    /**
     * If implemented, sets the specified output gain value on all channels of this track.
     * A value of 0.0 results in zero gains (silence), and
     * a value of 1.0 means unity gains (signal unchanged).
     * The default value is 1.0 meaning unity gain.
     * @param vol output gain for all channels.
     */
    open fun setVolume(vol:Float){

    }


    /**
     * Closes this input stream and releases any system resources associated.
     * write(...) calls are no longer valid after this call and can throw exception or be ignored
     * @exception  IOException  if an I/O error occurs.
     */
    abstract override fun close()


    /**
     *Writes the audio data to the output stream
     * @param b the byte array that holds the data to transfer.
     * @param off the offset expressed in bytes in b where the data to write starts.
     * @param len the number of bytes to write in audioData after the offset.
     * @throws IOException if an I/O error occurs.
     */
    abstract override fun write(b: ByteArray?, off: Int, len: Int)

    /**
     *Writes the audio data to the output stream by calling write(b, 0 ,b.size)
     * @throws IOException if an I/O error occurs.
     */
    @Synchronized
    override fun write(b: ByteArray) {
        write(b,0,b.size)
    }


    /**
     * True if writeShorts(b: ShortArray) and writeShorts(b: ShortArray, off: Int, len: Int)
     * methods supported by class.
     */
    open fun canWriteShorts():Boolean = false

    /**
     * If implemented, writes the audio data to the output stream.
     * @param b the short array that holds the data to transfer.
     * @param off the offset in b where the data to write starts.
     * @param len the number of samples to write in audioData after the offset.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    open fun writeShorts(b: ShortArray, off: Int, len: Int){
        throw NoSuchMethodException("Check canWriteShorts() value. Implementing class  must override " +
                "writeShorts(b: ShortArray, off: Int, len: Int) and canWriteShorts()")
    }

    /**
     * If implemented, writes the audio data to the output stream by calling
     * writeShorts(b,0,b.size)
     * @param b the short array that holds the data to transfer.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    open fun writeShorts(b: ShortArray) {
        throw NoSuchMethodException("Check canReturnShorts() value. Implementing class  must override " +
                "writeShorts(b: ShortArray) and canWriteShorts()")
    }



    fun channelConfig(channels: Int) = when (channels) {
        1-> CHANNEL_OUT_MONO
        2-> CHANNEL_OUT_STEREO
        else -> CHANNEL_INVALID
    }

    private fun frameTimeMs(rate:Int):Double{
        return 1000.0/(rate*frameSize.toDouble())
    }

}