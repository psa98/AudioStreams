

package c.ponom.audiostreams.audio_streams

import android.annotation.SuppressLint
import android.media.AudioFormat.*
import android.media.AudioRecord
import android.media.AudioRecord.*
import android.media.MediaRecorder
import android.util.Log
import c.ponom.recorder2.audio_streams.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.IOException


@Suppress("MemberVisibilityCanBePrivate", "unused")
class MicSoundInputStream : AudioInputStream {


    /**
     * After initialisation, internal AudioRecord object can be accessed for low level control of
     * microphone recording.
     * See registerAudioRecordingCallback(), setPreferredDevice(), setRecordPositionUpdateListener()
     *
     *
     */
    var audioRecord: AudioRecord?=null
    private set

    private constructor()

    /**
     * True if stream was already closed, including after error
     */
    private var closed:Boolean=false


    /**
     * Class constructor.
       * @param source the recording source.
     *   See {@link MediaRecorder.AudioSource} for the recording source definitions.
     * @param sampleRateInHz the sample rate expressed in Hertz. 44100Hz is currently the only
     *   rate that is guaranteed to work on all devices, but other rates such as 22050,
     *   16000, and 11025 may work on some devices.
     * @param channels describes the number of the audio channels. Must be equal 1 or 2.
     *   Mono recording  is guaranteed to work on all devices.
     * @param encoding the format in which the audio data is to be returned.
     *   See AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT.
     *   Only AudioFormat#ENCODING_PCM_16BIT currently supported.
     * @param bufferSizeMs the minimal size (in ms) of the buffer where audio data is written
     *   to during the recording. New audio data can be read from this buffer in smaller chunks
     *   than this size.
     * @throws java.lang.IllegalArgumentException
     */
    @JvmOverloads
    @SuppressLint("MissingPermission")
    @Throws(IllegalArgumentException::class,IOException::class)
    constructor(sampleRateInHz: Int, source: Int = MediaRecorder.AudioSource.DEFAULT,
        channels: Int = 1,
        encoding: Int = ENCODING_PCM_16BIT,
        bufferSizeMs: Int = 0): this() {
        channelConfig=channelConfig(channels)
        channelsCount=channels
        if (!(channelConfig== CHANNEL_IN_MONO ||channelConfig== CHANNEL_IN_STEREO))
            throw IllegalArgumentException("Only 1 and 2 channels (CHANNEL_IN_MONO " +
                    "and CHANNEL_IN_STEREO) supported")
        require(encoding== ENCODING_PCM_16BIT)
            { "Only PCM 16 bit encoding currently supported"}
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        sampleRate=sampleRateInHz
        frameSize=bytesPerSample*channels
        val bufferSize=frameSize*(sampleRate/1000)*(bufferSizeMs/1000.0).toInt()
        val minBuffer= getMinBufferSize(sampleRate,channelConfig,encoding)
        audioRecord= AudioRecord(source,sampleRate ,channelConfig,encoding,
            minBuffer.coerceAtLeast(bufferSize))
         if (audioRecord==null||audioRecord?.state!= STATE_INITIALIZED)
             throw IllegalArgumentException("Audio record init error - wrong params? ")
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        frameSize=bytesPerSample*channels

        // todo - возможно нужны коллбэки на готовность и асинхронный вариант конструктора,
        //  инициализация микрофона может быть не быстрой
    }


    /** Not implemented for MicSoundInputStream
     *
     */
   override fun read(): Int {
       throw NotImplementedError("Not  implemented, use read(....)")
   }


   /**
    * Return -1 since there is no estimated stream length
    */
   override fun totalBytesEstimate(): Long {
       return -1
   }

    /**
     * Return -1 since there is no estimated stream length
     */
   override fun bytesRemainingEstimate(): Long {
        return if (audioRecord==null) 0 else -1L
   }

    /*
   Returns current size of MicSoundInputStream internal buffer.
   */
   override fun available(): Int {
       return audioRecord?.bufferSizeInFrames?.times(frameSize)?:0
   }

    /**
     * Reads up to <code>b.len</code> bytes of data from the MicSoundInputStream into
     * an array of bytes calling read(b,0, b.size)
     */
    @Throws(NullPointerException::class)
    override fun read(b: ByteArray?): Int {
        if (b==null) throw NullPointerException ("Null array passed")
        if (audioRecord==null) return -1
        return read(b,0,b.size)
    }


    /**
     * This method blocks until some input data is available
     * Params:
     * @param b – the array to which the recorded audio data is written.
     * @param off – offset in b to which the data is written. Must not be negative.
     * @param len – the number of requested bytes.
     * Returns:
     * @return  zero or the positive number of bytes that were read,
     * -1  if the MicSoundInputStream not valid anymore due to error, or one of the following error
     * codes:
     * AudioRecord.ERROR_INVALID_OPERATION if the stream isn't properly initialized OR recording is
     * not started or was stopped,
     * AudioRecord.ERROR_BAD_VALUE if the parameters don't resolve to valid data and indexes.
     * @throws IOException if MicSoundInputStream was closed on previous error or by calling close()
     * @throws IllegalArgumentException for illegal combinations of b.size, off and len parameters
     * Method will write zero volume samples values to b if application don't hold
     * Manifest.permission.RECORD_AUDIO and during phone calls
     */
    @Throws(NullPointerException::class,IOException::class,IllegalArgumentException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException ("Null array passed")
        if (len == 0|| b.isEmpty()) return 0
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong read(...) params")
        if (len == 0) return 0
        if (closed) throw IOException("Stream already closed")
        if (!isRecording()) {
            logMicError(ERROR_INVALID_OPERATION)
            return ERROR_INVALID_OPERATION
        }
        val bytes = audioRecord!!.read(b, off, len)
        if (bytes < 0) logMicError(bytes)
        if (bytes == ERROR_DEAD_OBJECT||bytes == ERROR) {
            close()
            return  -1
        }
        if (bytes>0) bytesRead += bytes
        onReadCallback?.invoke(bytesRead)
        return bytes
    }


    /**
     * This method blocks until some input data is available
     * Params:
     * @param b – the array to which the recorded audio data is written.
     * @param off – offset in b to which the data is written. Must not be negative.
     * @param len – the number of requested shorts samples.
     * Returns:
     * @return  zero or the positive number of bytes that were read,
     * -1  if the MicSoundInputStream not valid anymore due to error, or one of the following
     * error codes:
     * AudioRecord.ERROR_INVALID_OPERATION if the stream isn't properly initialized OR recording is
     * not started or was stopped,
     * AudioRecord.ERROR_BAD_VALUE if the parameters don't resolve to valid data and indexes.
     * @throws IOException if MicSoundInputStream was closed on previous error or by calling close()
     * @throws IllegalArgumentException for illegal combinations of b.size, off and len parameters
     * Method will write zero volume samples values to b if application don't hold
     * Manifest.permission.RECORD_AUDIO and during phone calls
     */
    @Throws(IOException::class,IllegalArgumentException::class)
    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        if (len == 0|| b.isEmpty()) return 0
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong read(...) params")
        if (closed) throw IOException("Stream already closed")
        if (!isRecording()) {
            logMicError(ERROR_INVALID_OPERATION)
            return ERROR_INVALID_OPERATION
        }
        val samples = audioRecord!!.read(b, off, len)
        if (samples < 0) logMicError(samples)
        if (samples == ERROR_DEAD_OBJECT||samples == ERROR) {
            close()
            return  -1
        }
         if (samples>0)bytesRead+=samples*2
        return samples
    }

    /**
     * Reads up to <code>b.len</code> samples of audio data from the MicSoundInputStream into
     * an array of shorts calling read(b,0, b.size)
     * @see readShorts(b: ShortArray, off: Int, len: Int)
     *
     */
    override fun readShorts(b: ShortArray): Int {
         return readShorts(b,0,b.size)
    }

    /**
     * Closes MicSoundInputStream, releasing resources
     *
     */
    override fun close() {
        if (audioRecord != null) {
            audioRecord?.release()
            closed=true
            audioRecord=null
        }
    }


    fun isRecording(): Boolean {
        return if (audioRecord != null)
            (audioRecord?.state==STATE_INITIALIZED &&
                    audioRecord?.recordingState==RECORDSTATE_RECORDING)
        else false
    }

    /**
     * Start recording. No-op if already in recording state
     * @throws IOException if stream was already closed
     */
    fun startRecordingSession() {
        if (closed) throw IOException ("Stream already closed")
        if (audioRecord?.state==STATE_INITIALIZED &&
            audioRecord?.recordingState==RECORDSTATE_STOPPED)
                audioRecord?.startRecording()
    }

    /**
     * Stops (pause) recording.
     */
    fun stopRecordingSession() {
        if(isRecording()) audioRecord?.stop()
   }


    private fun logMicError(response: Int) {
        val message:String =
            when (response){
                ERROR_INVALID_OPERATION -> "MicSoundInputStream AudioRecord not initiated " +
                        "or started properly"
                ERROR_DEAD_OBJECT -> "MicSoundInputStream AudioRecord not valid anymore " +
                        "and stream to be recreated "
                ERROR_BAD_VALUE -> "MicSoundInputStream#read(..) parameters don't resolve " +
                        "to valid data and indexes"
                ERROR -> "MicSoundInputStream#read(..) error"
                else -> return
            }
        Log.d(TAG, "Audio Record read result=$response, $message" )
    }

    /** @return current mic buffer size in bytes.
     * Always check for value before setting own buffers size. Zero buffer size means than
     * device didn't initialised properly or stream is already closed
     */
    fun currentBufferSize(): Int {
        if (audioRecord==null) return 0
        return try {
            audioRecord?.bufferSizeInFrames!!.times(frameSize)
        } catch (e:IllegalStateException){
            0
        }
    }

    /**
     * True if readShorts(b: ShortArray) and readShorts(b: ShortArray, off: Int, len: Int)
     * methods supported by class.
     * @return true for MicSoundInputStream
     */
    override fun canReadShorts():Boolean = true


    /* Static and async API for class */
    companion object {

        /**
         * Async creation of microphone input audio stream.
         *
         * @param sampleRateInHz the sample rate expressed in Hertz. 44100Hz is currently the only
         *   rate that is guaranteed to work on all devices, but other rates such as 22050,
         *   16000, and 11025 may work on some devices.
         * @param channels describes the number of the audio channels. Must be equal 1 or 2.
         *   Mono recording  is guaranteed to work on all devices.
         * @param encoding the format in which the audio data is to be returned.
         *   See AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT.
         *   Only AudioFormat.ENCODING_PCM_16BIT currently supported.
         * @param bufferSizeMs the minimal size (in ms) of the buffer where audio data is written
         *   to during the recording. New audio data can be read from this buffer in smaller chunks
         *   than this size.
         * @return  the  Result&lt;MicSoundInputStream&gt; object containing created stream or
         * Throwable
         *
         *   */
        @JvmStatic
        fun createChannelAsync(sampleRateInHz: Int,
                               source: Int = MediaRecorder.AudioSource.DEFAULT,
                               channels: Int = 1,
                               encoding: Int = ENCODING_PCM_16BIT,
                               bufferSizeMs: Int = 0): Deferred<Result<MicSoundInputStream>> =
            CoroutineScope(Dispatchers.IO).async{
                runCatching{
                    MicSoundInputStream(sampleRateInHz,source,channels,encoding, bufferSizeMs)}
            }
    }

}





