

package c.ponom.audiostreams.audio_streams

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import android.media.AudioFormat.*
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecord.*
import android.media.MediaRecorder
import android.util.Log
import c.ponom.recorder2.audio_streams.AudioInputStream
import c.ponom.recorder2.audio_streams.TAG
import java.io.IOException


private const val BUFFER_SIZE__MULT: Int=4 //todo переделать под мс
class MicSoundInputStream private constructor(var audioRecord: AudioRecord? = null): AudioInputStream()  {

    private var recordingIsOn: Boolean=false

    var isReady=false


    // если разрешения нет - данные от микрофона будут пустыми. Описать в доках,
    // это проблема програмиста, а не повод бросать исключение
    @JvmOverloads
    @SuppressLint("MissingPermission")
    @Throws(IllegalArgumentException::class,IOException::class)
    constructor(
        sampleRate: Int, source: Int = MediaRecorder.AudioSource.DEFAULT,
        channels: Int = 1,
        encoding: Int = ENCODING_PCM_16BIT,
        bufferMult: Int = BUFFER_SIZE__MULT
    )
                : this() {
        channelConfig=channelConfig(channels)
        channelsCount=channels
        if (!(channelConfig== CHANNEL_IN_MONO ||channelConfig== CHANNEL_IN_STEREO))
            throw IllegalArgumentException("Only 1 and 2 channels (CHANNEL_IN_MONO " +
                    "and CHANNEL_IN_STEREO) supported")
            require(encoding== ENCODING_PCM_16BIT) { "Only PCM 16 bit encoding currently supported"}
        val buffer= getMinBufferSize(sampleRate,channelConfig,encoding)
        audioRecord= AudioRecord(source,sampleRate ,channelConfig,encoding,buffer*bufferMult)
         if (audioRecord==null) throw IllegalArgumentException("Audio record init error - wrong params? ")
        this.sampleRate = audioRecord!!.sampleRate
        // todo тут будет проверка на законные значения из списка, варнинг для всех законных кроме 16,22 и 44к
        //  и исключение для совсем левых
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1
        frameSize=bytesPerSample*channels
        isReady=true
        // todo - возможно нужны коллбэки на готовность и асинхронный вариант конструктора,
        //  инициализация микрофона может быть не быстрой
    }



   override fun read(): Int {
       throw NotImplementedError("Not  implemented, use read(....)")
   }


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
    @Throws(NullPointerException::class)
    override fun read(b: ByteArray?): Int {
        if (b==null) throw NullPointerException ("Null array passed")
        if (audioRecord==null) return -1
        return read(b,0,b.size)
    }




    //todo или эксепшн? надо определиться все же однозначно, что мы ВСЕМИ Input отдаем если
    // - поток уже закрыт через close(). тут хотелось бы -1 все же
    // - поток не прошел удачно инициализацию
    // - поток уже бросал исключение по ошибке
    // Одинаково для Bytes и Shorts
    // - еще - надо задокументировать что bufferSize =


    @Synchronized
    @Throws(NullPointerException::class,IOException::class,IndexOutOfBoundsException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (audioRecord==null) return -1
        if (!isRecording()) {
            logMicError(ERROR_INVALID_OPERATION)
            return ERROR_INVALID_OPERATION
        }
        val bytes = audioRecord!!.read(b, off, len)
        if (bytes < 0) logMicError(bytes)
        if (bytes == ERROR_DEAD_OBJECT) close()
        //конец потока
        if (bytes>0) bytesSent += bytes
        onReadCallback?.invoke(bytesSent)
        return bytes
    }

    private fun logMicError(response: Int) {
            val message:String =
            when (response){
                ERROR_INVALID_OPERATION -> "MicSoundInputStream AudioRecord not initiated or started properly"
                ERROR_DEAD_OBJECT -> "MicSoundInputStream AudioRecord not valid anymore and stream to be recreated "
                ERROR_BAD_VALUE -> "MicSoundInputStream#read(..) parameters don't resolve to valid data and indexes"
                else -> return
            }
        Log.d(TAG, "Audio Record read result=$response, $message", )
    }


    // у меня же onRead коллбэки переделаны, убрать это, сделать единое
    @Synchronized
    fun readShorts(b: ShortArray, off: Int, len: Int,
                   onReady:((samples:Int,dataSamples: ShortArray) -> Unit)?): Int {
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (audioRecord==null) return -1
        if (!isRecording()) {
            logMicError(ERROR_INVALID_OPERATION)
            return ERROR_INVALID_OPERATION
        }
        val samples = audioRecord!!.read(b, off, len)
        if (samples < 0) logMicError(samples)
        if (samples== ERROR_DEAD_OBJECT) {
            close()
            return  -1
        }
        if (samples>0){
            val data=b.copyOf(samples)
                if(onReady!=null)
                onReady(samples,data)
            bytesSent+=samples*2
            onReadCallback?.invoke(bytesSent)
        }
        return samples
    }


    @Synchronized
    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (audioRecord==null) return -1
        if (!isRecording()) {
            logMicError(ERROR_INVALID_OPERATION)
            return ERROR_INVALID_OPERATION
        }
        val samples = audioRecord!!.read(b, off, len)
        if (samples < 0) logMicError(samples)
        if (samples== ERROR_DEAD_OBJECT) {
            close()
            return  -1
        }
            //Конец потока
        if (samples>0)bytesSent+=samples*2
        return samples
    }

    @Synchronized
    override fun readShorts(b: ShortArray): Int {
         return readShorts(b,0,b.size)
    }


    @Synchronized
    override fun close() {
       if (audioRecord==null) return
       audioRecord?.release()
       recordingIsOn=false
       isReady=false
       audioRecord=null
    }

    fun isRecording(): Boolean {
        if (audioRecord==null){
            recordingIsOn=false
            return false
        }
        recordingIsOn= (audioRecord?.state==STATE_INITIALIZED &&
                audioRecord?.recordingState==RECORDSTATE_RECORDING)
        return recordingIsOn
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


    fun getInputDeviceList(context: Context): List<AudioDeviceInfo> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        if (audioManager==null) return ArrayList()
        val audioDeviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return audioDeviceInfo!!.asList()
    }


    fun chooseWiredMicIfAvailable(context: Context){
        val micList=getInputDeviceList(context)
        val device = micList.firstOrNull { it.type == TYPE_WIRED_HEADSET
        }
        if (device!=null) audioRecord?.preferredDevice = device
    }


    fun isBluetoothSCOMicAvailable(context: Context): Boolean {
        val micList=getInputDeviceList(context)
        val device = micList.firstOrNull { it.type == TYPE_BLUETOOTH_SCO}
        return device != null
    }


    fun getPreferredDevice(): AudioDeviceInfo? {
        if (audioRecord==null) return null
        else return audioRecord!!.preferredDevice
    }

    //  Check for 0!
    fun currentBufferSize(): Int {
        if (audioRecord==null) return 0
        return try {
            audioRecord?.bufferSizeInFrames!!.div(frameSize)
        } catch (e:IllegalStateException){
            0
        }
    }

    override fun canReadShorts():Boolean = true



}





