

@file:Suppress("unused")
package c.ponom.audiostreams.audio_streams
import android.media.AudioFormat
import android.media.AudioFormat.*
import android.media.AudioTrack
import android.media.AudioTrack.WRITE_BLOCKING
import android.media.AudioTrack.getMinBufferSize
import android.util.Log
import c.ponom.recorder2.audio_streams.AudioOutputStream
import c.ponom.recorder2.audio_streams.TAG
import java.io.IOException
import java.lang.System.currentTimeMillis


private  const val MAX_BUFFER_SIZE = 512 * 1024
private const val RESERVE_BUFFER_SIZE = 24 * 1024

class AudioTrackOutputStream private constructor() : AudioOutputStream(){


    private var currentVolume: Float=1f
    lateinit var audioFormat: AudioFormat
    // поле не приватное что позволяет менять доступные свойства проигрывателя - частоты, роутинг,
    // ставить слушатели и тп
    var audioOut:AudioTrack?=null
    var prepared = false
    private set
    /* todo - сделать State? а может глобальный enum со стейтом универсальный для всех потоков
    *   включая PAUSE для тех что его поддерживают?
    *   в v. 2 - удобное управление audioOut!!.setPreferredDevice(AudioDeviceInfo), с выбором
    *   "Первый динамик", "первый динамик телефона"
    *
    * */
    var closed = false
    private set

    @JvmOverloads
    @Throws(IllegalArgumentException::class, UnsupportedOperationException::class)
    constructor(
        sampleRate: Int,
        channelCount: Int,
        encoding: Int = ENCODING_PCM_16BIT,
        minBufferInMs: Int = 0
    ) : this() {
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
        val minBufferInBytes=frameSize*(sampleRate/1000)*(minBufferInMs/1000.0).toInt()

        //val bufferForTime = (frameTimeMs(encoding,sampleRate)*minBufferInMs/frameSize).toInt()
        audioFormat= Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()
        val minBuffer =getMinBufferSize(sampleRate, channelConfig, encoding)
        Log.d(TAG, "AUDIO TRACK: MIN.BUFFER.SIZE=$minBuffer bytes")
        audioOut=AudioTrack.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(minBufferInBytes))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        prepared=true

    }

    /**
     *стартовать следует: либо заранее, будучи готовым напихать туда секунду-другую
     *звука в буфер и далее подавать с достаточным темпом, либо уже уже подав звук в буфер
     * до заполнения, тогда по play() начнется его проигрывание
     */
    @Synchronized
    @Throws(IOException::class)
    fun play(){
        if (audioOut == null) throw IOException("Stream closed or in error")
        audioOut?.play()
    }

    /**
     * Использовать для немедленной  остановки с очисткой остатков в буфере. Убирает "щелчок"
     * возникающий при внезапной остановке громкого звука при обычном stop()
     */

    @Synchronized
    fun stopAndClear(){
        if (closed)return
        if (audioOut == null) return
        try {
        audioOut?.setVolume(0.02f)         //это позволяет  убрать клик в конце
        Thread.sleep(60)
        //время подобрано на слух, меньше 30 дает клик на частоте 440 гц 16000 сэмплов
            // этого может быть мало для звуков с мощными НЧ, но стоит потестить
        audioOut?.pause()
        audioOut?.flush()
        audioOut?.stop()
        audioOut?.setVolume(currentVolume)
        } catch (e:IllegalStateException){
            e.printStackTrace()
        }

    }

    //todo v2- добавить pause-resume?

    @Synchronized
    fun stop(){
        if (closed)return
        if (audioOut == null) return
        try {
            audioOut?.stop()
        } catch (e:IllegalStateException){
            e.printStackTrace()
        }
    }

    // документировать
    override fun setVolume(vol: Float) {
       currentVolume =vol
       audioOut?.setVolume(vol.coerceAtLeast(0f).coerceAtMost(1f))
    }

    /**
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio data for later playback
     * (static buffer mode). The format specified in the AudioTrack constructor should be AudioFormat.ENCODING_PCM_8BIT
     * to correspond to the data in the array.
     * In streaming mode, the write will normally block until all the data has been enqueued for playback,
     * and will return a full transfer count. However, if the track is stopped or paused on entry, or another
     * thread interrupts the write by calling stop or pause, or an I/O error occurs during the write, then
     * the write may return a short transfer count.
     * In static buffer mode, copies the data to the buffer starting at offset 0. Note that the actual
     * playback of this data might occur after this function returns.
     */


    @Throws(IOException::class,NullPointerException::class,IllegalArgumentException::class)
    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (audioOut == null) throw IOException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong write(....) parameters")
        val result:Int = audioOut!!.write(b, off, len)
        bytesSent += result.coerceAtLeast(0)
        if (result<0){
            close()
            throw IOException ("Error code $result - see codes for AudioTrack write(byte []..)")
        }
    }


    @Synchronized

    @Throws(IOException::class,NullPointerException::class,IllegalArgumentException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }

    override fun canWriteShorts(): Boolean = true
    private var lastWrite= currentTimeMillis()

    @Synchronized
    @Throws(IllegalArgumentException::class,IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        if (audioOut == null) throw IOException("Stream closed or in error state")
        val size=b.size
        val time= currentTimeMillis()
        lastWrite=time
        if (off > len ||len>size||off>size||off<0||len<0)
            throw IllegalArgumentException("Wrong write(....) parameters")
        val result = audioOut!!.write(b, off, len, WRITE_BLOCKING)
        bytesSent += result.coerceAtLeast(0)*2
        if (result<0){
            close()
            throw IOException ("Error code $result - see codes for AudioTrack write(byte []..)")
        }
   }
    @Synchronized
        override fun close() {
        stopAndClear()
        audioOut?.release()
        audioOut=null
        closed=true
        prepared=false
    }

    @Synchronized
    override fun write(b: Int) {
        throw NotImplementedError (" Not implemented, use write(byteArray[] ...)")
    }

}