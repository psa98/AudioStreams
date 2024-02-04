package c.ponom.audiuostreams.audiostreams

import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.util.Log
import androidx.annotation.IntRange
import c.ponom.audiuostreams.audiostreams.ArrayUtils.byteToShortArrayLittleEndian
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder

import java.io.IOException
import java.io.OutputStream

@Suppress("MemberVisibilityCanBePrivate", "JoinDeclarationAndAssignment")
class Mp3OutputAudioStream : AudioOutputStream {


    private constructor(outStream: OutputStream, stereoMode: LameBuilder.Mode) : super() {
        this.outputStream = outStream
        this.stereoMode = stereoMode
    }

    private lateinit var androidLame: AndroidLame
    private var closed = false
    val  outputStream:OutputStream
    private val stereoMode:LameBuilder.Mode


    /** Class constructor
     *
     * @param outStream OutputStream object for forwarding resulting compressed audio. Calling
     * close() on Mp3OutputAudioStream will also call close() on outStream.
     * @param inSampleRate sample rate for incoming audio stream, see below for recommended values.
     *
     * @param outBitrate output MP3 bitrate in kbps, from 16 to 320kbps. Recommended mp3 bitrate
     * to freq ratio should be no more than sampleRate/137, like in 44100/320.
     *
     * Recommended standard Quality settings list:
     *
     * MP3     24kbps 11.025kHz Stereo
     * MP3     24kbps 22.05kHz Mono
     * MP3     32kbps 11.025kHz Stereo
     * MP3     32kbps 22.05kHz Mono
     * MP3     64kbps 22.05kHz Stereo
     * MP3     64kbps 44.1kHz Mono
     * MP3     80kbps 22.05kHz Stereo
     * MP3     80kbps 44.1kHz Mono
     * MP3     96kbps 44.1kHz Mono
     * MP3     96kbps 44.1kHz Stereo
     * MP3     112kbps 44.1kHz Mono
     * MP3     112kbps 44.1kHz Stereo
     * MP3     128kbps 44.1kHz Mono
     * MP3     128kbps 44.1kHz Stereo
     * MP3     160kbps 44.1kHz Mono
     * MP3     160kbps 44.1kHz Stereo
     * MP3     192kbps 44.1kHz Mono
     * MP3     192kbps 44.1kHz Stereo
     * MP3     224kbps 44.1kHz Mono
     * MP3     224kbps 44.1kHz Stereo
     * MP3     256kbps 44.1kHz Mono
     * MP3     256kbps 44.1kHz Stereo
     * MP3     320kbps 44.1kHz Mono
     * MP3     320kbps 44.1kHz Stereo
     *
     * @param outStereoMode one of LameBuilder.Mode - STEREO, MONO, DEFAULT (stereo).
     * @param qualityMode set internal algorithm. True quality is determined by the bitrate,
     * but this variable will affect quality by selecting expensive or cheap algorithms.
     */



    @JvmOverloads
    constructor(
        outStream: OutputStream,
        @IntRange(from = 11025, to = 48000)inSampleRate: Int,
        @IntRange(from = 24, to= 320) outBitrate: Int,
        outStereoMode: LameBuilder.Mode,
        qualityMode: EncodingQuality=EncodingQuality.BALANCED) : this(outStream, outStereoMode) {
        sampleRate = inSampleRate
        channelsCount = when (outStereoMode) {
            LameBuilder.Mode.MONO -> 1
            else -> 2
        }
        channelConfig=channelConfig(channelsCount)
        encoding = ENCODING_PCM_16BIT
        frameSize= channelsCount*2
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
            .setOutBitrate(outBitrate.coerceAtMost(320).coerceAtLeast(24))
            .build()
    }


    /**
     *Compress and write the audio data to the output stream.
     * @param b the byte array that holds the audio data, short 16-bit PCM samples must be
     * converted to little-ended byte stream
     * For stereo streams b must contain interleaved data for the left and right samples,
     * LL-RR-LL-RR...
     * @param off the offset in b where the data to write starts.
     * @param len the number of samples to write in b after the offset.
     * @throws IOException if an I/O error or codec error occurs and if stream was already closed.
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     */
    @Synchronized
    @Throws(NullPointerException::class,IllegalArgumentException::class,IOException::class)
    override fun write(b: ByteArray?, off: Int, len: Int){
        if (closed) throw IOException("Stream was already closed")
        if (b == null) throw NullPointerException ("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong write(...) params")
        val samplesShorts = byteToShortArrayLittleEndian(b)
        val samples=samplesShorts.copyOf(len/2)
        val result = if (channelsCount==2)
            encodeInterleavedStream(samples)
        else encodeMonoStream(samples)
        outputStream.write(result)
        moreBytesSent(samples.size*2)
        onWriteCallback?.invoke(bytesSent)
    }


    /**
     *Compress and write the audio data to the output stream by calling write(b,0,b.size)
     * @param b the array of bytes that holds the data.
     * For stereo streams, b must contain interleaved little-ended data for left and right samples
     * @throws IOException if an I/O error or codec error occurs and if stream was already closed.
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     */

    @Throws(IllegalArgumentException::class, IOException::class)
    override fun write(b: ByteArray) {
        write(b,0,b.size)
    }


    /**
     *Compress and write the audio data to the output stream by calling writeShorts(b,0,b.size)
     * @param b the array of shorts that holds the data.
     * For stereo streams, b must contain interleaved data for left and right samples
     * @throws IOException if an I/O error or codec error occurs and if stream was already closed.
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     */

    @Throws(java.lang.IllegalArgumentException::class, IOException::class)
    override fun writeShorts(b: ShortArray) {
        writeShorts(b,0,b.size)
    }



    /**
     *Compress and write the audio data to the output stream.
     * @param b the array of shorts that holds the data.
     * For stereo streams, b must contain interleaved data for left and right samples
     * @param off the offset in b where the data to write starts.
     * @param len the number of samples to write in b after the offset.
     * @throws IOException if an I/O error or codec error occurs and if stream was already closed.
     * @throws IllegalArgumentException if the parameters don't resolve to valid data and indexes
     */
    @Synchronized
    @Throws(IllegalArgumentException::class, IOException::class)
    override fun writeShorts(b: ShortArray, off: Int, len: Int) {
        if (closed) throw IOException("Stream was already closed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong write(...) params")
        val samples=b.copyOf(len)
        val result:ByteArray = if (channelsCount==1) encodeMonoStream(samples)
            else encodeInterleavedStream(samples)
        moreBytesSent(b.size.coerceAtMost(len) *2)
        onWriteCallback?.invoke(bytesSent)
        outputStream.write(result)
    }


    /**
     * True if writeShorts(b: ShortArray) and writeShorts(b: ShortArray, off: Int, len: Int)
     * methods supported by class.
     * @return true
     */

    override fun canWriteShorts(): Boolean = true

    /**
     * Encode the final MP3 frame, closes this stream and call close() on the underlying stream
     * @throws IOException if an I/O error or codec error occurs, do nothing if stream already closed.
     */
    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (closed) return
        val result=encodeEofFrame()
        outputStream.write(result)
        outputStream.close()
        closed=true
    }


    @Throws(IOException::class)
    override fun write(b: Int) {
        throw NoSuchMethodException("Not implemented - use write (byte[]..../ short[]...)")
    }


    private fun encodeMonoStream(inArray: ShortArray): ByteArray {
        if (closed) throw IOException("Stream was already closed")
        val outBuff = ByteArray(inArray.size*4)
        val resultBytes = androidLame.encode(inArray, inArray, inArray.size, outBuff)
        if (resultBytes<0){
            closed=true
            outputStream.close()
            throw IOException("Lame codec error $resultBytes, wrong init params or buffer size")
        }
        return outBuff.sliceArray(0 until resultBytes)
    }


    private fun encodeInterleavedStream(samples: ShortArray): ByteArray {
        if (closed) throw IOException("Stream was already closed")
        val size = samples.size
        val outBuff = ByteArray(size*4)
        val resultBytes = androidLame.encodeBufferInterLeaved(samples, size/2, outBuff)
        if (resultBytes<0) {
            closed=true
            outputStream.close()
            throw IOException("Lame codec error $resultBytes, wrong init params or buffer size")
            }
        return outBuff.sliceArray(0 until resultBytes)
    }

    @Throws(IOException::class)
    private fun encodeEofFrame(): ByteArray {
        if (closed) throw IOException("Stream was already closed")
        closed = true
        val outBuff = ByteArray(32 * 1024)
        // real size = ~2048 bytes + tags, should be enough
        val resultBytes = androidLame.flush(outBuff)
        Log.v(TAG, "Mp3OutputAudioStream, EOF chunk $resultBytes bytes, stream closed")
        if (resultBytes<0)
            throw IOException("Lame codec error on final #$resultBytes")
        androidLame.close()
        return outBuff.sliceArray(0 until resultBytes)
    }

    enum class EncodingQuality {
        HIGH_AND_SLOW, BALANCED, FAST_ENCODING
    }
}

