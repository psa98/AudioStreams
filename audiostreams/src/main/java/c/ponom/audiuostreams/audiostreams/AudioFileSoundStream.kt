@file:Suppress("DEPRECATION")

package c.ponom.audiuostreams.audiostreams

import android.content.Context
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import c.ponom.audiuostreams.audiostreams.ArrayUtils.byteToShortArrayLittleEndian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.max

private const val MAX_BUFFER_SIZE = 512 * 1024
private const val RESERVE_BUFFER_SIZE = 32 * 1024
const val MAX_READ_SIZE = MAX_BUFFER_SIZE - 128 *1024
private const val TIMEOUT_US = 0L
private const val QUEUE_SIZE  = 4

/**
 * @author Sergey Ponomarev,2022, 461300@mail.ru
 * MIT licence
 */

class AudioFileSoundStream: AudioInputStream, AutoCloseable{

    private var path: String=""
    private var uri: Uri= Uri.EMPTY
    private var currentBuffer = ByteBuffer.allocate(0)
    private var maxChunkSize = 0
    private lateinit var codec: MediaCodec
    private var bufferReady: Boolean = false
    private val extractor: MediaExtractor = MediaExtractor()
    private var codecInputBuffers: Array<ByteBuffer> = emptyArray()
    private var codecOutputBuffers: Array<ByteBuffer> = emptyArray()
    private var eofReached = false
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var prepared = false
    private var currentBufferChunk = BufferedChunk()
    private var mainBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var lastBuffer =false
    private var closed=false
    private var fatalErrorInBuffer =false
    private var bufferQueue:ArrayBlockingQueue<BufferedChunk> = ArrayBlockingQueue(QUEUE_SIZE,true)
    private var bytesTotalCount = 0
    private var bytesFinalCount = 0
    private var released = false

    /**
     * Class constructor.
     * @param fd file descriptor of media file. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     * @throws IllegalArgumentException if file is not valid audio file or track is a not valid
     * audio track
     * @throws IOException if file is not accessible
     */
    @Suppress("unused")
    @Throws(IOException::class,IllegalArgumentException::class)
    @JvmOverloads
    constructor (fd: FileDescriptor,track: Int=0){
        extractor.setDataSource(fd)
        return createStream(track)
    }

    /**
     * Class constructor.
     * @param path path to media file (file-path or http URL)
     * @throws IllegalArgumentException if source is not valid audio file or track is a not
     * valid audio track
     * @throws IOException if source not accessible
     */
    @Throws(IOException::class,IllegalArgumentException::class)
    @JvmOverloads
    constructor(path: String,track: Int=0){
        this.path = path
        extractor.setDataSource(path)
        return createStream(track)
    }

    /**
     * Class constructor.
     * When <code>uri</code> refers to a network file the
     * android.Manifest.permission.INTERNET permission is required.
     * @param context the Context to use when resolving the Uri.
     * @param uri the Content URI of the data you want to extract from.
     *
     * @throws IllegalArgumentException if source is not valid audio file or track is a not valid
     * audio track
     * @throws IOException if source not accessible
     */
    @JvmOverloads
    @Throws(IOException::class,IllegalArgumentException::class)
    constructor(context: Context, uri: Uri, track: Int=0,
                headers: MutableMap<String, String>? =null) {
        this.uri = uri
        extractor.setDataSource(context, uri, headers)
        return createStream(track)
    }

    /*
    TODO: .wav files support. Their content marked as audio/raw, sample rate
     must be read from header or set externally
     */

    @Throws(IOException::class,IllegalArgumentException ::class)
    private fun createStream(track:Int) {
        extractor.selectTrack(track)
        if (track>extractor.trackCount-1||track<0){
            extractor.release()
            throw IllegalArgumentException("No such track in file")
        }
        val format: MediaFormat
        val mimeString:String
        try {
            format = extractor.getTrackFormat(track)
            mimeString = format.getString("mime").toString()
            if (!mimeString.contains("audio"))
                throw IllegalArgumentException("Wrong file - no audio tracks found")
            duration = format.getLong("durationUs").div(1000)
            sampleRate = format.getInteger("sample-rate")
            channelsCount = format.getInteger("channel-count")
            // todo спорно = по итогам тестов класс успешно отдает 2 канальный звук
            //  при открытии 6 канального (5.1) файла на обычном девайсе, кодек справляется с этим
            //  под капотом сам отдавая 2 канала но это не документировано
            if (channelsCount>2) channelsCount = 2
            encoding = if (format.containsKey("pcm-encoding")) {
                format.getInteger("pcm-encoding")
            } else ENCODING_PCM_16BIT //если ключа нет то 16 битный звук
            if (encoding != ENCODING_PCM_16BIT) {
                extractor.release()
                throw IllegalArgumentException("audio track is not ENCODING_PCM_16BIT")
            }
            mediaFormat = format
            channelConfig=channelConfig(channelsCount)
        } catch (e: ClassCastException) {
            extractor.release()
            throw IllegalArgumentException(
                "Wrong file format or track #$track is not audio track")
        }
        catch (e:NullPointerException){
            extractor.release()
            throw IllegalArgumentException(
                "Wrong file format or track #$track is not audio track")
        }
        bytesPerSample = if (encoding == ENCODING_PCM_16BIT) 2 else 1
        frameSize = bytesPerSample * channelsCount
        codec = MediaCodec.createDecoderByType(mimeString)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            codecInputBuffers = codec.inputBuffers
            codecOutputBuffers = codec.outputBuffers
            bufferInfo = MediaCodec.BufferInfo()
            prepared = true
        }catch (exception:Exception){
            extractor.release()
            throw IllegalArgumentException("Wrong format or track #$track is DRM protected")
        }
        fillBufferQueue()
    }

    @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
    override fun read(): Int {
        throw NoSuchMethodException ("Not implemented, use read(b[].... )")
    }

    /**
     * Params:
     * @param b  the byte array to which the recorded audio data is written.
     * @param off offset in b to which the data is written. Non-zero offset values currently
     * are not supported
     * @param len the number of requested bytes.
     * Returns:
     * @return  zero or the positive number of bytes that were read, -1 if the end of file reached
     * @throws IOException if the stream was closed on previous error or by calling close(),
     * or codec error happens
     * @throws IllegalArgumentException for illegal combinations of b.size, off and len parameters
     */
    @Throws(IOException::class,IllegalArgumentException::class,NullPointerException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException("Null byte array passed")
        if (len==0) return 0
        if (closed) throw IOException("Stream already closed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong read(...) params")
        if (len > MAX_READ_SIZE)
            throw IllegalArgumentException("Wrong read(...) params, " +
                    "cannot read more than $MAX_READ_SIZE bytes")
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not supported")
        //close()
        if (((bytesRead >= bytesFinalCount && bytesFinalCount != 0)&&!fatalErrorInBuffer))
            return -1
        if ((bytesRead >= bytesFinalCount && bytesFinalCount != 0)&&fatalErrorInBuffer) {
            close()
            currentBufferChunk.exception?.printStackTrace()
            throw IOException("IO exception in audio codec =${currentBufferChunk.exception}")
        }
        val bytes =  getBytesFromBuffer(b, len)
        bytesRead += bytes
        onReadCallback?.invoke(bytesRead)
        return  bytes
    }

    /**
     * Closes this input stream and releases any system resources associated.
     * Read(...) calls are no longer valid after this call and will throw exception
     */
    override fun close() {
        closed=true
        if (!released)  extractor.release()
        released=true
        bufferInfo=null
        channelsCount=0
        sampleRate=0
        duration=0
        //deallocate memory immediately
        mainBuffer= ByteBuffer.allocate(1)
        bufferQueue=ArrayBlockingQueue(1)
        codecInputBuffers= emptyArray()
        codecOutputBuffers= emptyArray()
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from this audio
     * stream. The <code>skip</code> method may, for a variety of reasons, end
     * up skipping over some smaller number of bytes, possibly <code>0</code>.
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @throws IOException if the stream was closed on previous error or by calling close()
      */
    @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
    override fun skip(n: Long): Long {
        //todo - should be split into a number of small reads
        return read(ByteArray(n.toInt())).toLong().coerceAtLeast(0)
    }

    /**
     * Params:
     * @param b the short array to which the recorded audio data is written.
     * @param off  offset in b to which the data is written. Non-zero offset values currently
     * not supported
     * @param len  the number of requested bytes.
     * Returns:
     * @return  zero or the positive number of samples that were read, -1,  if the end of file reached
     * @throws IOException if the stream was closed on previous error or by calling close()
     * @throws IllegalArgumentException for illegal combinations of b.size, off and len parameters
      */
    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        val byteArray =ByteArray(b.size*2)
        val bytes = read(byteArray, off*2, len*2)
        if (bytes==-1) return -1
        val resultShorts= byteToShortArrayLittleEndian(byteArray)
        resultShorts.copyInto(b,0,0,bytes/2)
        return bytes/2
    }


    /**
    *@return an estimate of the number of bytes that can be read (or skipped over) from this input
    *stream without blocking by the next invocation of a method for this input stream.
    *The next invocation might be the same thread or another thread. A single read or skip of this
    *many bytes will not block, but may read or skip fewer bytes.
    */
    override fun available(): Int = max(mainBuffer.remaining(),bytesRemainingEstimate().toInt())

    /**
     * Reads up to <code>b.len</code> samples of audio data from the file into
     * an array of short calling read(b,0, b.size)
     * @see readShorts(b: ShortArray, off: Int, len: Int)
     *
     */
    override fun readShorts(b: ShortArray): Int = readShorts(b,0,b.size)

    /**
     * True if readShorts(b: ShortArray) and readShorts(b: ShortArray, off: Int, len: Int)
     * methods supported by class
     * @return true
     */
    override fun canReadShorts(): Boolean = true

    override fun toString(): String = "AudioFileSoundStream path='$path'," +
            " uri=$uri, media format=$mediaFormat)"

    @Throws(IllegalStateException::class, CodecException::class)
    private fun takeNewBuffer() {
        //берем первый буфер объект из очереди, read будет брать из него пока все не кончится,
        // после чего либо бросит исключение, либо отдаст -1, либо запросит следующий
        if (!prepared || bufferReady || eofReached)
            throw IllegalStateException("Extractor not ready or already released")
        currentBufferChunk = bufferQueue.take()
        mainBuffer = currentBufferChunk.byteBuffer
        mainBuffer.position(0)
        fatalErrorInBuffer=currentBufferChunk.inFatalError
        lastBuffer=currentBufferChunk.isLastBuffer
        bufferReady = true
    }

    private fun getBytesFromBuffer(b: ByteArray, len: Int): Int {
        if (!bufferReady && !eofReached) {
            takeNewBuffer()
            mainBuffer.get(b, 0, len)
            return len
        }
        var length = len
        if (len >= mainBuffer.remaining()) {
            length = mainBuffer.remaining()
            mainBuffer.get(b, 0, length)
            if (!lastBuffer && !fatalErrorInBuffer) {
                bufferReady = false
                takeNewBuffer()
            }
            if (lastBuffer || fatalErrorInBuffer)
                eofReached = true
            return length
        }
        mainBuffer.get(b, 0, length)
        return length
    }



    @Suppress("BlockingMethodInNonBlockingContext")
    private fun fillBufferQueue(){
        var maxPos:Int
        if (!prepared) throw IllegalStateException("Extractor not ready or already released")
        CoroutineScope(Default.limitedParallelism(4)).launch {
            maxChunkSize=0
            do {
                val newByteBufferChunk=BufferedChunk()
                currentBuffer = newByteBufferChunk.byteBuffer
                do {
                    try {
                        inputForBuff()
                        val eof = outputToBuff(currentBuffer)
                        maxPos = MAX_BUFFER_SIZE - (RESERVE_BUFFER_SIZE +maxChunkSize)
                        newByteBufferChunk.isLastBuffer = eof
                        if (currentBuffer.position() >= maxPos || eof){
                            currentBuffer.limit(currentBuffer.position())
                            break
                        }
                    } catch (e:Exception){
                        newByteBufferChunk.isLastBuffer=true
                        newByteBufferChunk.inFatalError=true
                        newByteBufferChunk.exception=e
                        e.printStackTrace()
                        break
                    }
                }while (true)
                bufferQueue.put(newByteBufferChunk)
                if (newByteBufferChunk.isLastBuffer||newByteBufferChunk.inFatalError) break
            } while (true)
        }
    }

    override fun totalBytesEstimate(): Long {
        return if (closed) 0 else
            super.totalBytesEstimate()
    }

    @Throws(IllegalStateException::class, CodecException::class )
    private fun inputForBuff(): Boolean {
        var inputEOS=false
        val inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufIndex >= 0) {
            val dstBuf = codecInputBuffers[inputBufIndex]
            var sampleSize = extractor.readSampleData(dstBuf, 0)
            var presentationTimeUs: Long = 0
            if (sampleSize < 0) {
                inputEOS = true
                sampleSize = 0
            } else presentationTimeUs = extractor.sampleTime
            codec.queueInputBuffer(inputBufIndex,0,sampleSize,
                presentationTimeUs,if (inputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
            if (!inputEOS) extractor.advance()
        }
        return inputEOS
    }

    @Throws(IllegalStateException::class, CodecException::class)
    private fun outputToBuff(currentBuffer: ByteBuffer): Boolean {
        var eofReached=false
        val res = codec.dequeueOutputBuffer(bufferInfo!!, TIMEOUT_US)
        if (res >= 0) {
            val buf:ByteBuffer = codecOutputBuffers[res]
            val chunk = ByteArray(bufferInfo!!.size)
            buf.get(chunk)
            buf.clear()
            val bytesRead=chunk.size
            if (bytesRead > 0) {
                currentBuffer.put(chunk, 0, bytesRead)
                bytesTotalCount += bytesRead
            }
            if (bytesRead > maxChunkSize) maxChunkSize = bytesRead
            codec.releaseOutputBuffer(res, false /* render */)
            if (bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                bytesFinalCount = bytesTotalCount
                eofReached = true
            }
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            codecOutputBuffers = codec.outputBuffers
        return eofReached
    }

    private inner class BufferedChunk{
        val byteBuffer:ByteBuffer= ByteBuffer.allocate(MAX_BUFFER_SIZE)
        var isLastBuffer:Boolean=true
        var inFatalError:Boolean=false
        var exception:Exception?=null
    }
}






