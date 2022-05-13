package c.ponom.recorder2.audio_streams

import android.media.AudioFormat.*
import android.media.MediaFormat
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AudioOutputStream() :
    OutputStream(),  AutoCloseable{



    // будет содержать валидное значение если это возможно
    var mediaFormat: MediaFormat?=null


    // значение 0 не валидно, его наличие указывает на незавершенность инциализации
    var channelsCount:Int = 0


    // не путать с числом каналов! см. channelConfig(channels: Int)
    // законные значения - CHANNEL_OUT_MONO,CHANNEL_OUT_STEREO.
    //должно быть выставлено при создании канала
    var channelConfig:Int= CHANNEL_INVALID

    // типичный поддерживаемый аппаратурой диапазон - 16000 - 48000, стандартные значения:
    // 8000,11025,12000,16000,22050,24000,32000,44100,48000, гарантированно
    // поддерживается 44100
    var sampleRate:Int =0

    var frameSize: Int=0 // всегда считать размер в конструкторе


    // переопределите коллбэк для возможности учета вызовов методов чтения из стороннего API,
    // к примеру для реализации индикатора прогресса
    open var onWriteCallback: ((sentBytes:Long) -> Unit)? ={  }



    var encoding:Int= ENCODING_PCM_16BIT

    @Volatile
    var timestamp=0L // пересчет выведенных байтов в мс.


    // todo переделать на атомики?
    @Volatile
    var bytesSent: Long = 0
    // todo -посмотреть, у меня есть умножение на 2 в mp3 рекордере,
    // не лишнее ли и вообще включить во все тесты - это именно байты должны быть, не сэмплы

    @Synchronized //TODO ДОБАВИТЬ В ЛИБУ ВЕЗДЕ ГДЕ НАДО
    set(value) {
        field=value
        timestamp=(frameTimeMs(this.encoding,this.sampleRate)*value).toLong()
    }

    constructor(channelMask: Int, sampleRate: Int,encoding:Int) : this()



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

    open fun setVolume(vol:Float){
    }



    fun channelConfig(channels: Int) = when (channels) {
        1-> CHANNEL_OUT_MONO
        2-> CHANNEL_OUT_STEREO
        else ->{
            CHANNEL_INVALID
        }
    }

    fun shortToByteArray(arr: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(arr.size * 2)
        byteBuffer.asShortBuffer().put(arr)
        return byteBuffer.array()
    }


    fun byteToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).asShortBuffer()[shorts]
        return shorts
    }

    fun byteToShortArrayLittleEndian(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
        return shorts
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


    fun shortToByteArrayLittleEndian(shorts: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(shorts)
        return byteBuffer.array()
    }


    private fun frameTimeMs(encoding:Int, rate:Int):Double{
        val bytesInFrame:Int = when (encoding){
            //TODO теоретически у меня байты во фрейме должны гарантированно быть заданы в
            // конструкторах, и 8 битного нет, можно будет это убрать при условии покрытия тестами
            ENCODING_PCM_8BIT -> channelsCount
            ENCODING_PCM_16BIT -> channelsCount*2
            else-> 2
        }
        return 1000.0/(rate*bytesInFrame.toDouble())
    // эта вещь тоже должна в конструкторе
    // считаться один раз и не быть равной 0, лучше не перевычислять ее каждый раз
            // когда у нас вызывется приватный конструктор абстрактного класса? туда можно
    }


}