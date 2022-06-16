package c.ponom.recorder2.audio_streams

import android.media.AudioFormat.*
import android.media.MediaFormat
import androidx.annotation.IntRange
import java.io.IOException
import java.io.OutputStream

abstract class AudioOutputStream() :
    OutputStream(),  AutoCloseable{


    constructor(@IntRange(from = 2400, to = 96000) samplingRate:Int,
                @IntRange(from = 1, to = 2)channelsNumber: Int) : this() {
        channelsCount=channelsNumber
        sampleRate=samplingRate
        channelConfig=channelConfig(channelsNumber)
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2  else 1

    }


    // будет содержать валидное значение если это возможно
    var mediaFormat: MediaFormat?=null
    protected set

    // значение 0 не валидно, его наличие указывает на незавершенность инциализации
    var channelsCount:Int = 0
    protected set

    // не путать с числом каналов! см. channelConfig(channels: Int)
    // законные значения - CHANNEL_OUT_MONO,CHANNEL_OUT_STEREO.
    //должно быть выставлено при создании канала
    var channelConfig:Int= CHANNEL_INVALID
    protected set

    // типичный поддерживаемый аппаратурой диапазон - 16000 - 48000, стандартные значения:
    // 8000,11025,12000,16000,22050,24000,32000,44100,48000, гарантированно
    // поддерживается 44100
    var sampleRate:Int =0
    protected set

    // переопределите коллбэк для возможности учета вызовов методов чтения из стороннего API,
    // к примеру для реализации индикатора прогресса
    open var onWriteCallback: ((sentBytes:Long) -> Unit)? ={  }

    var bytesPerSample: Int = 2 // для 16 бит всегда, 8 битный звук в настоящее время не поддерживается
        protected set

    var frameSize: Int=bytesPerSample*channelsCount // всегда считать размер в конструкторе
        protected set

    var encoding:Int= ENCODING_PCM_16BIT
    protected set

    @Volatile
    var timestamp=0L // время от начала проигрывания

    @Volatile
    var bytesSent: Long = 0
    // todo -посмотреть, у меня есть умножение на 2 в mp3 рекордере,
    //  не лишнее ли и вообще включить во все тесты - это именно байты должны быть, не сэмплы
    // вывести в составе колбэка данные о байтах и времени выведенного

    @Synchronized //TODO ДОБАВИТЬ В ЛИБУ ВЕЗДЕ ГДЕ НАДО
    set(value) {
        field=value
        timestamp=(frameTimeMs(this.sampleRate)*value).toLong()
    }


    open fun setVolume(vol:Float){
    }



    //вызов обязателен, должно освобождать аппаратуру
    abstract override fun close()



    open fun setRecommendedBufferSize(bytes:Int){

    }



    abstract override fun write(b: ByteArray?, off: Int, len: Int)

    override fun write(b: ByteArray) {
        write(b,0,b.size)
    }

    open fun setRecommendedBufferSizeMs(ms:Int){

    }


    fun channelConfig(channels: Int) = when (channels) {
        1-> CHANNEL_OUT_MONO
        2-> CHANNEL_OUT_STEREO
        else ->{
            CHANNEL_INVALID
        }
    }


    open fun canWriteShorts():Boolean =false


    @Throws(IOException::class)
    open fun writeShorts(b: ShortArray, off: Int, len: Int){
        throw NoSuchMethodException("Check canWriteShorts() value. Implementing class  must override " +
                "writeShorts(b: ShortArray, off: Int, len: Int) and canWriteShorts()")
    }

    @Throws(IOException::class)
    open fun writeShorts(b: ShortArray) {
        throw NoSuchMethodException("Check canReturnShorts() value. Implementing class  must override " +
                "writeShorts(b: ShortArray) and canWriteShorts()")
    }




    fun frameTimeMs( rate:Int):Double{
        return 1000.0/(rate*frameSize.toDouble())
    }

}