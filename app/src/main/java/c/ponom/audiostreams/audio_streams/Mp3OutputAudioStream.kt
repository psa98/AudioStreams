package c.ponom.audiostreams.audio_streams

import android.media.AudioFormat.ENCODING_PCM_16BIT
import androidx.annotation.IntRange
import c.ponom.recorder2.audio_streams.AudioOutputStream
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.IOException
import java.io.OutputStream

class Mp3OutputAudioStream private constructor() : AudioOutputStream(){



    lateinit var androidLame: AndroidLame
    private var finished = false
    private lateinit var  outputStream:OutputStream
    private lateinit var stereoMode:LameBuilder.Mode



    /**Важно! OutBitrate - это уровень сжатого потока и он задается  в килобитах/сек! то есть
     * стандартные значения надо указывать в килобитах от 32  до 320, а не от 32 000  до 320 000
     * Mp3 формат поддерживает только 16 битную кодировку, левый канал - нечетные сэмплы (signed Short)
     * outChannelsNum - 1 или 2.
     * допустимые входные частоты дискретизации - от 8000 до 48000 <BR>
     * qualityMode = режим высокого качества оптимален для офлайн-сжатия потоков при сохранении
     * итоговых звуковых файлов. Быстрый режим -  для онлайн сжатия с минимальными задержаками и
     * минимальным задействованием процессора
     * Для всех последующих операций используется последнее по времени создания энкодера установленное
     * значение качества, при создании нескольких потоков установление отличающегося от базового
     * качества сжатия  меняет его значение и для созданных ранее но еще не закрытых потоков
     */
    @JvmOverloads
    constructor(
        outStream: OutputStream,
        @IntRange(from = 8000, to= 48000)inSampleRate: Int,
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
        //Only 16 bit  encoding supported
        // when (encoding){


        /**
         *
        internal algorithm selection.  True quality is determined by the bitrate
        but this variable will effect quality by selecting expensive or cheap algorithms.
        quality=0..9.  0=best (very slow).  9=worst.
        recommended:  2     near-best quality, not too slow
        5     good quality, fast
        7     ok quality, really fast

         */

        val quality:Int = when (qualityMode){
            EncodingQuality.HIGH_AND_SLOW -> 1
            EncodingQuality.BALANCED -> 5
            EncodingQuality.FAST_ENCODING -> 7
        }
        outputStream=outStream
        androidLame= LameBuilder()
            .setQuality(quality)
            .setInSampleRate(inSampleRate)
            .setMode(outStereoMode )
            .setOutChannels(channelsCount)
            .setOutBitrate(outBitrate.coerceAtMost(320).coerceAtLeast(32))
            .build()
    }


    @Throws(IndexOutOfBoundsException::class,NullPointerException::class,IllegalStateException::class,
        IOException::class)
    @Synchronized

    // синхронизацию всех пишущих методов делать надо на один объект.
    // Плюс все ж переделать на атомики
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (finished) throw IllegalStateException("Stream closed or in error state")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong write(...) params")
        //!!! todo доделать оффсеты

        val samplesShorts = byteToShortArrayLittleEndian(b)
        val samples=samplesShorts.copyOf(len/2)
        val result = if (channelsCount==2)
            encodeInterleavedStream(samples)
        else encodeMonoStream(samples)
        outputStream.write(result)
        bytesSent += result.size
        onWriteCallback?.invoke(bytesSent)
    }


    override fun canReturnShorts(): Boolean {
        return true
    }

    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class, IOException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }


    @Synchronized
    @Throws(IllegalArgumentException::class,IllegalStateException::class, IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        val samples=b.copyOf(len)
        val result:ByteArray
        if (channelsCount==1) result =  encodeMonoStream(samples)
        else result =  encodeInterleavedStream(samples)
        bytesSent += result.size
        onWriteCallback?.invoke(bytesSent)
        outputStream.write(result)
    }

    /**
     * не закрывает подлежащий поток автоматически! Мало ли что вы в него еще писать будете
     * другим образом
     */
    @Synchronized
    override fun close() {
        val result=encodeEofFrame()
        outputStream.write(result)
        finished=true
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        throw NoSuchMethodException("Not implemented - use write (byte[]....)")
    }


    private fun encodeMonoStream(inArray: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        val outBuff = ByteArray(inArray.size)
        val resultBytes = androidLame.encode(inArray, inArray, inArray.size, outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }

    private fun encodeStereoStream(leftArray: ShortArray, rightArray: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        if (leftArray.size != rightArray.size) throw IllegalStateException("Both samples have  same length")
        val outBuff = ByteArray(leftArray.size)
        val resultBytes = androidLame.encode(leftArray, rightArray, leftArray.size, outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }

    // убедиться что оно берет little ended  shorts
    private fun encodeInterleavedStream(samples: ShortArray): ByteArray {
        if (finished) throw IllegalStateException("Already finished, create new encoder")
        val size = samples.size
        val outBuff = ByteArray(size)
        val resultBytes = androidLame.encodeBufferInterLeaved(samples, size/2, outBuff)
            // sample here =  L+R pair
        return outBuff.sliceArray(0 until resultBytes)
    }




    private fun encodeEofFrame(): ByteArray {
        if (finished) throw IllegalStateException("Already finished")
        finished = true
        val outBuff = ByteArray(16 * 1024)
        // вообще оно заведомо до 2048 + ограниченный размер тегов, но пусть
        val resultBytes = androidLame.flush(outBuff)
        return outBuff.sliceArray(0 until resultBytes)
    }


    enum class EncodingQuality {
        HIGH_AND_SLOW, BALANCED, FAST_ENCODING
    }
}

