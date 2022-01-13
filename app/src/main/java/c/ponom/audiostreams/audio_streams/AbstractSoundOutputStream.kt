package c.ponom.recorder2.audio_streams

import android.media.MediaFormat
import java.io.OutputStream

abstract class AbstractSoundOutputStream() :
    OutputStream() {

    var mediaFormat: MediaFormat?=null
    var sampleRate: Int=0
    var channelsCount:Int=0
    var frameSize: Int=0 // посчитать размер в конструкторе


    constructor(channelMask: Int, sampleRate: Int,encoding:Int) : this()

    //вызов обязателен, должно освобождать аппаратуру
    abstract override fun close()

    // класс будет стараться прочитать с буфер байт прежде чем отправить первые на устройства
    // и не будет блокировать write(...) до достижения заданного размера буфера
    // должно быть кратно размеру фрейма ? иначе будет подрезано вверх до ближайшего
    open fun setRecommendedBufferSize(bytes:Int){

    }


    open fun setRecommendedBufferSizeMs(ms:Int){


    }

    open fun setVolume(leftOrMono:Float, right:Float){


    }
}