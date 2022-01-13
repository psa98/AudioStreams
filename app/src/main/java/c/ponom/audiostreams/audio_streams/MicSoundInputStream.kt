

@file:Suppress("unused")

package c.ponom.recorder2.audio_streams

import android.annotation.SuppressLint
import android.media.AudioFormat.*
import android.media.AudioRecord
import android.media.AudioRecord.*
import android.media.MediaRecorder
import java.io.IOException
import java.lang.Integer.max



private const val BUFFER_SIZE__MULT: Int=4 //переделать под мс
class MicSoundInputStream : AbstractSoundInputStream()  {


    private var recordingIsOn: Boolean=false
    var realBufferSize =0
    private var  audioRecord:AudioRecord?=null
    var prepared=false
    var bufferSizeMs = 0


    // если разрешения нет - данные от микрофона будут пустыми. Описать в доках,
    // это проблема програмиста, а не повод бросать исключение
    @JvmOverloads
    @SuppressLint("MissingPermission")
    @Throws(IllegalArgumentException::class,IOException::class)
    fun getMicSoundStream(freq:Int, bufferSize:Int, channelConfig:Int= CHANNEL_IN_MONO,
                          encoding:Int= ENCODING_PCM_16BIT,
                          mic:Int= MediaRecorder.AudioSource.VOICE_COMMUNICATION){
        if (!(channelConfig== CHANNEL_IN_MONO ||channelConfig== CHANNEL_IN_STEREO))
            throw IllegalArgumentException("Only CHANNEL_IN_MONO and CHANNEL_IN_STEREO supported")
        if (!(encoding== ENCODING_PCM_8BIT ||encoding== ENCODING_PCM_16BIT))
            throw IllegalArgumentException("Only 16 and 8 bit encodings supported")
        val minBuffer= getMinBufferSize(freq,channelConfig,encoding)
        audioRecord= AudioRecord(mic,freq ,channelConfig,encoding,max(minBuffer*BUFFER_SIZE__MULT,minBuffer))
         if (audioRecord==null) throw IllegalArgumentException("Audio record init error - wrong params? ")
        channelsCount = audioRecord!!.channelCount
        sampleRate = audioRecord!!.sampleRate
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме 16,22 и 44к
        //  и исключение для совсем левых
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1

        frameSize=bytesPerSample*channelsCount
        realBufferSize=audioRecord!!.bufferSizeInFrames*frameSize
        //val dev = audioRecord!!.preferredDevice
        //val audioManager = App.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        //val adi = audioManager!!.getDevices(AudioManager.GET_DEVICES_INPUTS)
        bufferSizeMs=(audioRecord!!.bufferSizeInFrames.toFloat()/sampleRate*1000).toInt()

         //audioManager.mode
       //audioRecord.preferredDevice=AudioDeviceInfo()
        /**
         * для будущего развития можно будет выставить наружу методы сообщающие удобым образом:
         * - имеется ли в системе доступный блютуз микрофон
         * - надо ли подключаться к нему при попытке создать поток.
         * что делать при его физ.отключении - сбросить поток через -1, через исключение, или молча
         * переключиться на другой микрофон
         *
         * в принципе следует исходить из того что есть всегда основной (фронт) и почти всегда шумодавный (бэк)
         * микрофоны, SCO блютуз может быть как подключен так и отключен в любой момент, как и проводная
         * гарнтитура, замещение основного микрофона гарнитурным проводным может производиться и
         * авто (тестировать надо)
         *
         */
        //val list = audioRecord.activeMicrophones
        /// это на  посмотреть не будет ли в списке микрофонов от гарнитур  и прочего и что вообще есть

        /**
         * д
         */
        if (audioRecord?.state== STATE_UNINITIALIZED)
             throw IOException ("Cannot init recording")
        prepared=true

         // todo - возможно нужны коллбэки на готовность, инициализация микрофона может быть не быстрой
    }

    //доработать вызов после закрытия потока -


   override fun read(): Int {
       throw IllegalArgumentException("Not  implemented, use read(....)")
   }

   @Throws(IllegalArgumentException::class,NullPointerException::class)
   override fun read(b: ByteArray?): Int {
       if (b==null) throw NullPointerException ("Null array passed")
       return read(b,0,b.size)
   }

   @Volatile
   override var bytesSent: Long = 0

   /**
    * Return -1 when there is no estimated stream length (for example,for endless streams)
    * or estimated rest of bytes in stream
    */
   override fun totalBytesEstimate(): Long {
       return -1
   }

    /**
     * Return -1 when there is no estimated stream length (for example,for endless streams)
     * or estimated rest of bytes in stream
     */
   override fun bytesRemainingEstimate(): Long {
       return -1L
   }

    /**
     * смотри описание исходного. Теоретически тут надо сообщить сколько можно отдать без
     * блокирования из буферов, подумать реализуемо ли это
     */
   override fun available(): Int {
       return 0
   }


    /**
     * Params:
     * audioData – the array to which the recorded audio data is written.
     * offset – index in audioData to which the data is written.
     * Must not be negative, or cause the data access to go out of bounds of the array.
     * size – the number of requested bytes. Must not be negative, or cause the data
     * access to go out of bounds of the array.
     * Returns:
     * zero or the positive number of bytes that were read, or one of the following error codes.
     * Minus 1 if stream is closed (ended)
     * The number of shorts will be a multiple of the channel count not to exceed sizeInShorts.
     *AudioRecord.ERROR_INVALID_OPERATION if the object isn't properly initialized
     * AudioRecord.ERROR_BAD_VALUE if the parameters don't resolve to valid data and indexes
     * AudioRecord.ERROR_DEAD_OBJECT if the object is not valid anymore and needs to be recreated.
     * The dead object error code is not returned if some data was successfully transferred.
     * In this case, the error is returned at the next read() ERROR in case of other error
     */

    @Synchronized
    @Throws(NullPointerException::class,IllegalStateException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b==null) throw NullPointerException ("Null array passed")
        if (audioRecord==null) return -1
        val bytes = audioRecord!!.read(b, off, len)
        if (bytes== ERROR_DEAD_OBJECT) close() //Конец потока
        bytesSent+=bytes.coerceAtLeast(0)
        onReadCallback?.invoke(bytesSent)
        return bytes
   }

    /**
     * Params:
     * audioData – the array to which the recorded audio data is written.
     * offsetInShorts – index in audioData to which the data is written expressed in shorts.
     * Must not be negative, or cause the data access to go out of bounds of the array.
     * sizeInShorts – the number of requested shorts. Must not be negative, or cause the data
     * access to go out of bounds of the array.
     * Returns:
     * zero or the positive number of shorts that were read, or one of the following error codes.
     * Minus 1 if stream is closed (ended)
     * The number of shorts will be a multiple of the channel count not to exceed sizeInShorts.
     *  AudioRecord.ERROR_INVALID_OPERATION if the object isn't properly initialized
     * AudioRecord.ERROR_BAD_VALUE if the parameters don't resolve to valid data and indexes
     * AudioRecord.ERROR_DEAD_OBJECT if the object is not valid anymore and needs to be recreated.
     * The dead object error code is not returned if some data was successfully transferred.
     * In this case, the error is returned at the next read() ERROR in case of other error
     */

    @Synchronized
    fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        if (audioRecord==null) return -1
        val samples = audioRecord!!.read(b, off, len)
        if (samples== ERROR_DEAD_OBJECT) close() //Конец потока
        onReadCallback?.invoke(bytesSent)
        bytesSent+=samples.coerceAtLeast(0)*2
        return samples
    }

    @Synchronized
    @Throws(NullPointerException::class,IllegalStateException::class)
    fun readShorts(buffer: ShortArray): Int {
         return readShorts(buffer,0,buffer.size)
    }


    //todo - как идея - можно унифицировать поведение скип и клоуз с filestream - то есть для
    // закрытых потоков кидать исключение а не игнорить
    @Synchronized
    override fun close() {
       if (audioRecord==null) return
       audioRecord?.release()
       recordingIsOn=false
       prepared=false
       audioRecord=null
    }

    fun isRecording(): Boolean {
        return (audioRecord?.state==STATE_INITIALIZED &&
                audioRecord?.recordingState==RECORDSTATE_RECORDING)
    }

    @Synchronized
    fun startRecordingSession() {
        if (audioRecord?.state==STATE_INITIALIZED &&
                audioRecord?.recordingState==RECORDSTATE_STOPPED) audioRecord?.startRecording()
            else return
        bytesSent=0
        recordingIsOn=true
    }

    @Synchronized
    fun stopRecordingSession() {
        if(isRecording()) audioRecord?.stop()
            else return
        recordingIsOn=false
   }
}


