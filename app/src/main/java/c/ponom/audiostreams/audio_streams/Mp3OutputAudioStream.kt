package c.ponom.audiostreams.audio_streams

import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.util.Log
import androidx.annotation.IntRange
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.recorder2.audio_streams.AudioOutputStream
import c.ponom.recorder2.audio_streams.TAG
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.IOException
import java.io.OutputStream

class Mp3OutputAudioStream private constructor() : AudioOutputStream(){



    private lateinit var androidLame: AndroidLame
    private var finished = false
    lateinit var  outputStream:OutputStream
    lateinit var stereoMode:LameBuilder.Mode



    /**Важно! OutBitrate - это уровень сжатого потока и он задается  в килобитах/сек! то есть
     * стандартные значения надо указывать в килобитах от 32  до 320, а не от 32 000  до 320 000. <BR>
     * Mp3 формат поддерживает только 16 битную кодировку, левый канал - нечетные сэмплы (signed Short)
     * допустимые входные частоты дискретизации - от 8000 до 48000 <BR>
     * qualityMode = режим высокого качества оптимален для офлайн-сжатия потоков при сохранении
     * итоговых звуковых файлов. Быстрый режим -  для онлайн сжатия с минимальными задержками и
     * минимальным задействованием процессора
     *  По итогам тестов разница по скорости сжатия между FAST|HIGH примерно в 2 раза.
     *  Как я понимаю NDK инстанс существует один, так что вызов этого конструктора после close()
     *  не создает новый.
     *  Для всех последующих операций используется последнее по времени создания энкодера установленное
     * значение качества, при создании нескольких потоков установление отличающегося от базового
     * качества сжатия  меняет его значение и для созданных ранее но еще не закрытых потоков
     */

    //todo -
    // в документацию в переводе:
    // - outBitrate не должно превышать  inSampleRate/137,  (но делить на ~150 лучше), к примеру, при 22100
    // максимум ~160, рекомендуемое 128, для 44100 - 320 .
    // минимальный размер буфера отправляемого на write должен быть достаточен для помещения туда данных
    // фрейма, зависит от параметров, рекомендуемый не менее inSampleRate сэмплов

    @JvmOverloads
    constructor(
        outStream: OutputStream,
        @IntRange(from = 16000, to= 48000)inSampleRate: Int,
        @IntRange(from = 32, to= 320) outBitrate: Int,
        outStereoMode: LameBuilder.Mode,
        qualityMode: EncodingQuality=EncodingQuality.BALANCED) : this() {
        sampleRate = inSampleRate
        stereoMode=outStereoMode
        outputStream=outStream
        channelsCount = when (outStereoMode) {
            LameBuilder.Mode.MONO -> 1
            else -> 2
        }
        channelConfig=channelConfig(channelsCount)
        encoding = ENCODING_PCM_16BIT
        frameSize= channelsCount*2

        //документириовать что оnly 16 bit  encoding supported

        /* From Lame doc
        * internal algorithm selection.  True quality is determined by the bitrate
        * but this variable will effect quality by selecting expensive or cheap algorithms.
        * quality=0..9.  0=best (very slow).  9=worst.
        * recommended:  2     near-best quality, not too slow
        *               5     good quality, fast
        *               7     ok quality, really fast
        */

        val quality:Int = when (qualityMode){
            EncodingQuality.HIGH_AND_SLOW -> 1
            EncodingQuality.BALANCED -> 5
            EncodingQuality.FAST_ENCODING -> 7
        }
        androidLame= LameBuilder()
            .setQuality(quality)
            .setInSampleRate(inSampleRate)
            .setMode(outStereoMode )
            .setOutChannels(channelsCount)
            .setOutBitrate(outBitrate.coerceAtMost(320).coerceAtLeast(32))
            .build()
    }

    /**
     * todo для версии 2- автоувеличить размер буфера передаваемого Lame c сохр. исх. len:Int
     *  или протестить работу с отдачей туда вдвое увеличенного outBuff
     *  документировать что минимальный размер буфера для чтения должен быть достаточен для помещения туда данных
     *  фрейма, зависит от параметров LAME, безопасный размер =  не менее inSampleRate сэмплов
     */


    @Throws(IndexOutOfBoundsException::class,NullPointerException::class,
        IllegalStateException::class,IOException::class)
    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (finished) throw IllegalStateException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong write(...) params")
        // todo,  доделать в тех классах что писал я - что оффсет не поддерживается,
        //  во всех write.
        val samplesShorts = byteToShortArrayLittleEndian(b)
        val samples=samplesShorts.copyOf(len/2)
        val result = if (channelsCount==2)
            encodeInterleavedStream(samples)
        else encodeMonoStream(samples)
        outputStream.write(result)
        bytesSent += samples.size*2
        onWriteCallback?.invoke(bytesSent)
    }


    override fun canWriteShorts(): Boolean = true

    @Synchronized
    @Throws(IllegalStateException::class, IOException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }



    // документировать что sample here =  L+R pair
    @Synchronized
    @Throws(IndexOutOfBoundsException::class,IllegalStateException::class, IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        if (finished) throw IllegalStateException("Stream was already closed or in error state")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong write(...) params")
        val samples=b.copyOf(len)
        val result:ByteArray
        if (channelsCount==1) result =  encodeMonoStream(samples)
        else result =  encodeInterleavedStream(samples)
        bytesSent += b.size.coerceAtMost(len) *2
        onWriteCallback?.invoke(bytesSent)
        outputStream.write(result)
    }

    /**
     * закрывает выходной поток автоматически! Нет смысла писать туда что-то после финального
     * блока  данных
     */
    @Synchronized
    override fun close() {
        if (finished) return
        val result=encodeEofFrame()
        outputStream.write(result)
        outputStream.close()
        finished=true
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        throw NoSuchMethodException("Not implemented - use write (byte[]..../ short[]...)")
    }


    private fun encodeMonoStream(inArray: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Stream closed, create new encoder")
        val outBuff = ByteArray(inArray.size*2)
        val resultBytes = androidLame.encode(inArray, inArray, inArray.size, outBuff)
        if (resultBytes<0){
            finished=true
            outputStream.close()
            throw IOException("Lame codec error $resultBytes, wrong init params or too small buffer size")
        }
        return outBuff.sliceArray(0 until resultBytes)
    }

    private fun encodeInterleavedStream(samples: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Stream closed, create new encoder")
        val size = samples.size
        val outBuff = ByteArray(size*2)
        val resultBytes = androidLame.encodeBufferInterLeaved(samples, size/2, outBuff)
        if (resultBytes<0) {
            finished=true
            outputStream.close()
            throw IOException("Lame codec error $resultBytes, wrong init params or too small buffer size")
            }
        return outBuff.sliceArray(0 until resultBytes)
    }

    private fun encodeEofFrame(): ByteArray {
        if (finished) throw IllegalStateException("Stream was already closed")
        finished = true
        val outBuff = ByteArray(16 * 1024)
        // вообще оно заведомо до 2048 + ограниченный размер тегов, но пусть
        val resultBytes = androidLame.flush(outBuff)
        Log.e(TAG, "encodeMonoStream: MP3 bytes closed=")
        androidLame.close()
        return outBuff.sliceArray(0 until resultBytes)
    }

    enum class EncodingQuality {
        HIGH_AND_SLOW, BALANCED, FAST_ENCODING
    }
}

