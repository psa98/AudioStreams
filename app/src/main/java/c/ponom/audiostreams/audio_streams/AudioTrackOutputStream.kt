


package c.ponom.audiostreams.audio_streams
import android.media.AudioFormat
import android.media.AudioFormat.*
import android.media.AudioFormat.Builder
import android.media.AudioTrack
import android.media.AudioTrack.*
import android.util.Log
import c.ponom.recorder2.audio_streams.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException


class AudioTrackOutputStream private constructor() : AudioOutputStream(){


    private var currentVolume: Float=1f
    lateinit var audioFormat: AudioFormat
    // поле не приватное что позволяет менять доступные свойства проигрывателя - частоты, роутинг,
    // ставить слушатели и тп
    var audioOut:AudioTrack?=null
    private var prepared = false

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
        sampleFreq: Int,
        channelsCount: Int,
        encoding: Int = ENCODING_PCM_16BIT,
        minBufferInMs: Int = 0
    ) : this() {
        sampleRate = sampleFreq
        channelConfig=channelConfig(channelsCount)
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме
        //  8, 16,22, 32 и 44 - 48к
        // доделать буфер!
        //  и исключение для совсем левых
        if (!(channelConfig== CHANNEL_OUT_MONO ||channelConfig== CHANNEL_OUT_STEREO))
            throw IllegalArgumentException("Only 1 or 2 channels(CHANNEL_OUT_MONO " +
                    "and CHANNEL_OUT_STEREO) supported")
        if (encoding!=ENCODING_PCM_16BIT)
            throw IllegalArgumentException("Only PCM 16 bit encoding currently supported")
        super.encoding=encoding
        super.channelsCount=channelsCount
        when (encoding){
            ENCODING_PCM_8BIT -> frameSize = channelsCount
            ENCODING_PCM_16BIT-> frameSize= channelsCount*2
        }
        val minBufferInBytes=frameSize*(sampleRate/1000)*(minBufferInMs/1000.0).toInt()

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
            .setTransferMode(MODE_STREAM)
            .build()
        prepared=true
    }

    /**
     *стартовать следует: либо заранее, будучи готовым напихать туда секунду-другую
     *звука в буфер и далее подавать с достаточным темпом, либо уже уже подав звук в буфер
     * до заполнения, тогда по play() начнется его проигрывание
     */
    @Throws(IOException::class)
    fun play(){
        if (closed||audioOut == null||audioOut?.state != STATE_INITIALIZED)
            throw IOException("Stream closed or in error state")
        audioOut?.play()
    }

    /**
     * Использовать для немедленной  остановки с очисткой остатков в буфере. Убирает "щелчок"
     * возникающий при внезапной остановке громкого звука при обычном stop()
     */

    fun stopAndClear(){
        if (closed||audioOut == null||audioOut?.playState!= PLAYSTATE_STOPPED) return
        try {
        audioOut?.setVolume(0.02f)         //это позволяет  убрать клик в конце
            CoroutineScope(IO).launch {
                delay(90)
            //время подобрано на слух, меньше 30 дает клик на частоте 440 гц 16000 сэмплов
            // этого может быть мало для звуков с мощными НЧ, но стоит потестить
                audioOut?.pause()
                audioOut?.flush()
                audioOut?.stop()
                audioOut?.setVolume(currentVolume)
            }
        } catch (e:IllegalStateException){
            e.printStackTrace()
        }

    }

    fun stop(){
        if (closed||audioOut == null||audioOut?.playState!= PLAYSTATE_STOPPED) return
        try {
            audioOut?.stop()
        } catch (e:IllegalStateException){
            e.printStackTrace()
        }
    }

    fun pause (){
        if (closed||audioOut == null||audioOut?.playState!= PLAYSTATE_PLAYING) return
        try {
            audioOut?.setVolume(0.02f)         //это позволяет  убрать клик в конце
            //время подобрано на слух, меньше 30 дает клик на частоте 440 гц 16000 сэмплов
            // этого может быть мало для звуков с мощными НЧ, но стоит потестить
            CoroutineScope(IO).launch {
                audioOut?.pause()
                audioOut?.setVolume(currentVolume)
            }
        } catch (e:IllegalStateException){
            e.printStackTrace()
        }
    }

    fun resume() {
        if (closed||audioOut == null||audioOut?.playState!= PLAYSTATE_PAUSED) return
        try {
             audioOut?.play()
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
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio
     * data for later playback.
    *
     * In streaming mode, the write will normally block until all the data has been enqueued for playback,
     * and will return a full transfer count. However, if the track is stopped or paused on entry, or another
     * thread interrupts the write by calling stop or pause, or an I/O error occurs during the write, then
     * the write may return a short transfer count.
     *
     *
     * @param b the array that holds the data to play.
     * @param off the offset expressed in bytes in audioData where the data to write
     *    starts.
     *    Must not be negative, or cause the data access to go out of bounds of the array.
     * @param len the number of bytes to write in audioData after the offset.
     *    Must not be negative, or cause the data access to go out of bounds of the array.
     * @return zero or the positive number of bytes that were written, or one of the following
     *    error codes. The number of bytes will be a multiple of the frame size in bytes
     *    not to exceed sizeInBytes.
     * <ul>
     * <li>{@link #ERROR_INVALID_OPERATION} if the track isn't properly initialized</li>
     * <li>{@link #ERROR_BAD_VALUE} if the parameters don't resolve to valid data and indexes</li>
     * <li>{@link #ERROR_DEAD_OBJECT} if the AudioTrack is not valid anymore and
     *    needs to be recreated. The dead object error code is not returned if some data was
     *    successfully transferred. In this case, the error is returned at the next write()</li>
     * <li>{@link #ERROR} in case of other error</li>
     * </ul>
     * @throws IOException if the track isn't properly initialized, or he AudioTrack is not valid
     * anymore and needs to be recreated
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     * @throws NullPointerException if null array passed
     */

    /*todo - переписать через вызов пишущего шорты и протестить

    The format specified in the AudioTrack constructor should be
     * {@link AudioFormat#ENCODING_PCM_8BIT} to correspond to the data in the array.
     * todo The format can be {@link AudioFormat#ENCODING_PCM_16BIT}, but this is deprecated.

     */

    @Throws(IOException::class,NullPointerException::class,IllegalArgumentException::class)
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (audioOut == null||closed) throw IOException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong write(....) parameters")
        val result:Int = audioOut!!.write(b, off, len)
        bytesSent += result.coerceAtLeast(0)
        if (result<0){
            audioOut?.release()
            audioOut=null
            closed=true
            throw IOException ("Error code $result - see codes for AudioTrack write(...)")
        }
    }



    /**
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio
     * data for later playback calling writeShorts(b,0,b.size)
     * @param b the array that holds the data to play.
     * @return zero or the positive number of bytes that were written, or one of the following
     *    error codes. The number of bytes will be a multiple of the frame size in bytes
     *    not to exceed sizeInBytes.
     * @throws IOException if the track isn't properly initialized, or he AudioTrack is not valid
     * anymore and needs to be recreated
     * */
    @Throws(IOException::class,NullPointerException::class,IllegalArgumentException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }


    override fun canWriteShorts(): Boolean = true

    /**
     *Writes the audio data to the audio sink for playback (streaming mode), or copies audio
     * data for later playback.
     *
     * In streaming mode, the write will normally block until all the data has been enqueued for playback,
     * and will return a full transfer count. However, if the track is stopped or paused on entry, or another
     * thread interrupts the write by calling stop or pause, or an I/O error occurs during the write, then
     * the write may return a short transfer count.
     *
     *
     * @param b the array that holds the data to play.
     * @param off the offset expressed in bytes in audioData where the data to write
     *    starts.
     *    Must not be negative, or cause the data access to go out of bounds of the array.
     * @param len the number of bytes to write in audioData after the offset.
     *    Must not be negative, or cause the data access to go out of bounds of the array.
     * @return zero or the positive number of bytes that were written, or one of the following
     *    error codes. The number of bytes will be a multiple of the frame size in bytes
     *    not to exceed sizeInBytes.
     * @throws IOException if the track isn't properly initialized, or he AudioTrack is not valid
     * anymore and needs to be recreated
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     * @throws NullPointerException if null array passed
     */
    @Throws(IllegalArgumentException::class,IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        if (audioOut == null||closed) throw IOException("Stream closed or in error state")
        val size=b.size
        if (off > len ||len>size||off>size||off<0||len<0)
            throw IllegalArgumentException("Wrong write(....) parameters")
        val result = audioOut!!.write(b, off, len, WRITE_BLOCKING)
        bytesSent += result.coerceAtLeast(0)*2
        if (result<0){
            audioOut?.release()
            audioOut=null
            closed=true
            throw IOException ("Error code $result - see codes for AudioTrack write(...)")
        }
   }
        override fun close() {
        stopAndClear()
        audioOut?.release()
        audioOut=null
        closed=true
    }

    override fun write(b: Int) {
        throw NotImplementedError (" Not implemented, use write(byteArray[] ...)")
    }

}