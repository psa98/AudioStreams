

@file:Suppress("unused")


package c.ponom.recorder2.audio_streams
import android.media.AudioFormat
import android.media.AudioFormat.*
import android.media.AudioRecord
import android.media.AudioTrack
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


private  const val MAX_BUFFER_SIZE = 1024 * 1024
private const val RESERVE_BUFFER_SIZE = 16 * 1024


class AudioOutputSteam private constructor() :AbstractSoundOutputStream(){



    lateinit var audioFormat: AudioFormat
    var audioOut:AudioTrack?=null
    private var bufferReady: Boolean = false
    private var maxPos = MAX_BUFFER_SIZE - RESERVE_BUFFER_SIZE
    private var prepared = false
    private var mainBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var growBuffer: ByteArrayOutputStream = ByteArrayOutputStream(MAX_BUFFER_SIZE)


    @JvmOverloads
    @Throws(IllegalArgumentException::class, UnsupportedOperationException::class)
    constructor(channelConfig: Int, sampleRate: Int, encoding:Int, minBufferInMs:Int=0) : this() {
        this.sampleRate = sampleRate
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме
        //  8, 16,22, 32 и 44 - 48к
        //  и исключение для совсем левых
        val bufferForTime = 0 // todo посчитать
        channelsCount = if (channelConfig== CHANNEL_OUT_MONO)
            1 else if (channelConfig== CHANNEL_OUT_STEREO) 2 else 0
        if (channelsCount==0)
            throw IllegalArgumentException("Only CHANNEL_OUT_MONO and CHANNEL_OUT_STEREO supported")
        if (!(encoding== ENCODING_PCM_8BIT ||encoding== ENCODING_PCM_16BIT))
            throw IllegalArgumentException("Only 16 and 8 bit encodings supported")


        audioFormat= Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelsCount)
            .build()
        val bufferSize= AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(bufferForTime)

        audioOut=AudioTrack.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            //.setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)
            // - смотри доки как там устроен подбор буфера для этой штуки,
            // думаю без нее никаких гарантий дать нельзя
            .build()


        /*
                audioOut?.setPlaybackPositionUpdateListener()

        audioOut?.playbackHeadPosition

        *Returns the playback head position expressed in frames. Though the "int" type is signed 32-bits, the value should be reinterpreted as if it is unsigned 32-bits. That is, the next position after 0x7FFFFFFF is (int) 0x80000000. This is a continuously advancing counter. It will wrap (overflow) periodically,  for example approximately once every 27:03:11 hours:minutes:seconds at 44.1 kHz

         */

        /*
        startThresholdInFrames()
        Returns the streaming start threshold of the AudioTrack.
The streaming start threshold is the buffer level that the written audio data must reach for audio streaming to start after play() is called. When an AudioTrack is created, the streaming start threshold is the buffer capacity in frames. If the buffer size in frames is reduced by setBufferSizeInFrames(int) to a value smaller than the start threshold then that value will be used instead for the streaming start threshold.


         */

        /* play()
        If the mode is MODE_STREAM, you can optionally prime the data path prior to calling play(), by writing up to bufferSizeInBytes (from constructor). If you don't call write() first, or if you call write() but with an insufficient amount of data, then the track will be in underrun state at play(). In this case, playback will not actually start playing until the data path is filled to a device-specific minimum level. This requirement for the path to be filled to a minimum level is also true when resuming audio playback after calling stop(). Similarly the buffer will need to be filled up again after the track underruns due to failure to call write() in a timely manner with sufficient data. For portability, an application should prime the data path to the maximum allowed by writing data until the write() method returns a short transfer count. This allows play() to start immediately, and reduces the chance of underru
         */
    }




    override fun setVolume(leftOrMono: Float, right: Float) {

    }

    /**
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio data for later playback (static buffer mode). The format specified in the AudioTrack constructor should be AudioFormat.ENCODING_PCM_8BIT to correspond to the data in the array. The format can be AudioFormat.ENCODING_PCM_16BIT, but this is deprecated.
    In streaming mode, the write will normally block until all the data has been enqueued for playback, and will return a full transfer count. However, if the track is stopped or paused on entry, or another thread interrupts the write by calling stop or pause, or an I/O error occurs during the write, then the write may return a short transfer count.
    In static buffer mode, copies the data to the buffer starting at offset 0. Note that the actual playback of this data might occur after this function returns.
     */

    @Throws(IllegalArgumentException::class,NullPointerException::class)
    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int) {
        if (b == null) throw NullPointerException("Null byte array passed")
        audioOut?.write(b, off, len)
    }

    override fun close() {
        //TODO("Not yet implemented")
    }

    override fun write(b: Int) {
        //TODO("Not yet implemented")
    }


}