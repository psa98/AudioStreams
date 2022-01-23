

@file:Suppress("unused")


package c.ponom.recorder2.audio_streams
import android.media.AudioFormat
import android.media.AudioFormat.*
import android.media.AudioTrack
import android.media.AudioTrack.WRITE_BLOCKING
import android.media.AudioTrack.getMinBufferSize
import java.io.IOException


private  const val MAX_BUFFER_SIZE = 512 * 1024
private const val RESERVE_BUFFER_SIZE = 24 * 1024


class AudioOutputSteam private constructor() :AudioOutputStream(){


    private var currentVolume: Float=1f
    lateinit var audioFormat: AudioFormat
    private var audioOut:AudioTrack?=null
    private var prepared = false


    @JvmOverloads
    @Throws(IllegalArgumentException::class, UnsupportedOperationException::class)
    constructor( sampleRate: Int, channelCount: Int,encoding:Int, minBufferInMs:Int=0) : this() {
        this.sampleRate = sampleRate
        //todo -- переделать под число каналов на входе, нечего тут
        channelConfig=channelConfig(channelCount)

        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме
        //  8, 16,22, 32 и 44 - 48к
        // доделать буфер!
        //  и исключение для совсем левых
        if (!(channelConfig== CHANNEL_OUT_MONO ||channelConfig== CHANNEL_OUT_STEREO))
            throw IllegalArgumentException("Only 1 or 2 channels(CHANNEL_OUT_MONO " +
                    "and CHANNEL_OUT_STEREO) supported")
        if (!(encoding== ENCODING_PCM_8BIT ||encoding== ENCODING_PCM_16BIT))
            throw IllegalArgumentException("Only 16 and 8 bit encodings supported")
        this.encoding=encoding
        when (encoding){
            ENCODING_PCM_8BIT -> frameSize = channelsCount
            ENCODING_PCM_16BIT-> frameSize= channelsCount*2
        }

        //val bufferForTime = (frameTimeMs(encoding,sampleRate)*minBufferInMs/frameSize).toInt()
        audioFormat= Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()
        audioOut=AudioTrack.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(getMinBufferSize(sampleRate, channelConfig, encoding))
            .setTransferMode(AudioTrack.MODE_STREAM)
            //.setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)
            // - смотри доки как там устроен подбор буфера для этой штуки,
            // думаю без нее никаких гарантий дать нельзя
            .build()
    }


    @Synchronized
    @Throws(IllegalStateException::class)
    fun play(){
        if (audioOut == null) throw IllegalStateException("Stream closed or in error")
        audioOut?.play()
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    fun stopAndClear(){
        if (audioOut == null) throw IllegalStateException("Stream closed or in error")
        audioOut?.setVolume(0.02f)         //это позволяет  убрать клик в конце
        Thread.sleep(50)
        //время подобрано на слух, меньше 30 дает клик на частоте 440 гц 16000 сэмплов
        audioOut?.pause()
        audioOut?.flush()
        audioOut?.stop()
        audioOut?.setVolume(currentVolume)
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    fun stop(){
        if (audioOut == null) throw IllegalStateException("Stream closed or in error")
        audioOut?.setVolume(0.02f)         //это позволяет  убрать клик в конце
        audioOut?.stop()
        Thread.sleep(30)
        // пересчитать время так что бы за него заведомо произошло исчерпание текущего буфера + неск.мс
        audioOut?.setVolume(currentVolume)

    }


    override fun setVolume(vol: Float) {
        audioOut?.setVolume(vol.coerceAtLeast(0f).coerceAtMost(1f))
        currentVolume =vol
    }

    /**
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio data for later playback (static buffer mode). The format specified in the AudioTrack constructor should be AudioFormat.ENCODING_PCM_8BIT to correspond to the data in the array. The format can be AudioFormat.ENCODING_PCM_16BIT, but this is deprecated.
    In streaming mode, the write will normally block until all the data has been enqueued for playback, and will return a full transfer count. However, if the track is stopped or paused on entry, or another thread interrupts the write by calling stop or pause, or an I/O error occurs during the write, then the write may return a short transfer count.
    In static buffer mode, copies the data to the buffer starting at offset 0. Note that the actual playback of this data might occur after this function returns.
     */


    @Throws(IllegalArgumentException::class,NullPointerException::class,
        IllegalStateException::class,IOException::class)
    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (audioOut == null) throw IllegalStateException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong write(...) params")
        val result:Int = audioOut!!.write(b, off, len)
        bytesSent += result.coerceAtLeast(0)
        if (result<0)
            throw IllegalStateException ("Error code $result - see codes for AudioTrack write(byte []..)")
    }


    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class,IOException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }

    override fun canReturnShorts(): Boolean {
        return true
    }

    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class,IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        if (audioOut == null) throw IllegalStateException("Stream closed or in error state")
        val size=b.size
        if (off > len ||len>size||off>size||off<0||len<0)
            throw IllegalArgumentException("Wrong write(....) parameters")
        val result = audioOut!!.write(b, off, len, WRITE_BLOCKING)
        bytesSent += result.coerceAtLeast(0)*2
        if (result<0)
            throw IllegalStateException ("Error code $result - see codes for AudioTrack write(byte []..)")
   }
    @Synchronized
    override fun close() {
        stopAndClear()
        audioOut?.release()
        audioOut=null

    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        val byteArray=ByteArray(1){b.toByte()}
        write(byteArray,0,1)
    }

}