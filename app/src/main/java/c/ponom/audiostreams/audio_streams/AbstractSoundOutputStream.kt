package c.ponom.recorder2.audio_streams

import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioFormat.ENCODING_PCM_8BIT
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractSoundOutputStream() :
    OutputStream() {

    var sampleRate: Int=0
    var channelsCount:Int=0 // 1 or 2 only
    var frameSize: Int=0 // посчитать размер в конструкторе
    var encoding:Int=0 //ENCODING_PCM_8BIT  или  ENCODING_PCM_16BIT)

    @Volatile
    var timestamp=0L // пересчет выведенных байтов в мс.
    @Volatile
    var bytesSent: Long = 0
    set(value) {
        field=value
        timestamp=(frameTimeMs(this.encoding,this.sampleRate)*value).toLong()
    }

    constructor(channelMask: Int, sampleRate: Int,encoding:Int) : this()

    //вызов обязателен, должно освобождать аппаратуру
    abstract override fun close()

    // класс будет стараться прочитать с буфер байт прежде чем отправить первые на устройства
    // и не будет блокировать write(...) до достижения заданного размера буфера
    // должно быть кратно размеру фрейма ? иначе будет подрезано вверх до ближайшего
    open fun setRecommendedBufferSize(bytes:Int){

    }


    fun frameTimeMs(encoding:Int,rate:Int):Double{
        val bytesInFrame:Int = when (encoding){
            ENCODING_PCM_8BIT -> channelsCount
            ENCODING_PCM_16BIT -> channelsCount*2
            else-> 0
        }
        return 1.0/(rate*bytesInFrame.toDouble())
    }

    open fun setRecommendedBufferSizeMs(ms:Int){

    }

    open fun setVolume(vol:Float){
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


    fun shortToByteArrayLittleEndian(shorts: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(shorts)
        return byteBuffer.array()
    }
}