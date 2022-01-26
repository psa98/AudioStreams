package c.ponom.recorder2.audio_streams

import android.media.AudioFormat
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
    // одно из законных значений - CHANNEL_IN_MONO,CHANNEL_IN_STEREO.
    //должно быть выставлено при создании канала
    var channelConfig:Int= AudioFormat.CHANNEL_INVALID

    // типичный поддерживаемый аппаратурой диапазон - 16000 - 48000, стандартные значения:
    // 8000,11025,12000,16000,22050,24000,32000,44100,48000, гарантированно
    // поддерживается 44100
    var sampleRate:Int =0

    var frameSize: Int=0 // всегда считать размер в конструкторе


    // переопределите коллбэк для возможности учета вызовов методов чтения из стороннего API,
    // к примеру для реализации индиктора прогресса
    open var onWriteCallback: ((sentBytes:Long) -> Unit)? ={  }



    var encoding:Int= ENCODING_PCM_16BIT

    @Volatile
    var timestamp=0L // пересчет выведенных байтов в мс.


    // todo переделать на атомики?
    @Volatile
    var bytesSent: Long = 0
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
            ENCODING_PCM_8BIT -> channelsCount
            ENCODING_PCM_16BIT -> channelsCount*2
            else-> 0
        }
        return 1.0/(rate*bytesInFrame.toDouble())
    }
}