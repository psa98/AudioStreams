@file:Suppress("DEPRECATION")

package c.ponom.audiostreams.audio_streams

import android.content.Context
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.max

/*
*максимальный размер буфера - соответствует примерно 5 сек на 44100
*/
private const val MAX_BUFFER_SIZE = 512 * 1024

// резервная зона в конце буфера, что бы избежать его переполнения,
// к ней плюсуется максимальный размер буфера используемого кодеком (типично 4-16Кб)
private const val RESERVE_BUFFER_SIZE = 32 * 1024
private const val TIMEOUT_US = 0L
private const val QUEUE_SIZE  = 8

@Suppress("unused")
class AudioFileSoundStream: AudioInputStream, AutoCloseable{
    private  var path: String=""
    private lateinit var currentBuffer: ByteBuffer
    private var maxChunkSize = 0
    private lateinit var codec: MediaCodec
    private var bufferReady: Boolean = false
    private val extractor: MediaExtractor = MediaExtractor()
    private lateinit var codecInputBuffers: Array<ByteBuffer>
    private lateinit var codecOutputBuffers: Array<ByteBuffer>
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
    private var uri: Uri= Uri.EMPTY

    @Throws(IOException::class,IllegalArgumentException::class)
    @JvmOverloads
    constructor (fd: FileDescriptor,track: Int=0){
        extractor.setDataSource(fd)
        return createStream(track)
    }

    @Throws(IOException::class,IllegalArgumentException::class)
    @JvmOverloads
    constructor(path: String,track: Int=0){
        this.path = path
        extractor.setDataSource(path)
        return createStream(track)
    }

    @JvmOverloads
    @Throws(IOException::class,IllegalArgumentException::class)
    constructor(context: Context, uri: Uri, track: Int=0,
                headers: MutableMap<String, String>? =null) {
        this.uri = uri
        extractor.setDataSource(context, uri, headers)
        return createStream(track)
    }

    @Throws(IOException::class,IllegalArgumentException ::class)
    private fun createStream(track:Int) {
        extractor.selectTrack(track)
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
            encoding = if (format.containsKey("pcm-encoding")) {
                format.getInteger("pcm-encoding")
            } else ENCODING_PCM_16BIT //если ключа нет то 16 битный звук
            if (encoding != ENCODING_PCM_16BIT)
                throw IllegalArgumentException("audio track is not ENCODING_PCM_16BIT")
            mediaFormat = format
            channelConfig=channelConfig(channelConfig)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "Wrong file format or track #$track is not audio track")
        }
        catch (e:NullPointerException){
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
            throw IllegalArgumentException("Wrong format or track #$track is DRM protected")
        }
        fillBufferQueue()
    }

    //todo - протестить, погоняв на мини приложении с самыми разными форматами
    // в идеале добиться выброски всех эксепшнов в тестах, и убедиться что исходный поток
    // декодируется до байта.

    @Throws(IllegalStateException::class, CodecException::class)
    private fun takeNewBuffer() {
        //берем первый буфер объект из очереди, read будет брать из него пока все не кончится,
        // после чего либо бросит исключение, либо отдаст -1, либо запросит следующий
        if (!prepared || bufferReady || eofReached) throw
            IllegalStateException("Extractor not ready or already released")
        currentBufferChunk = bufferQueue.take()
        mainBuffer = currentBufferChunk.byteBuffer
        mainBuffer.position(0)
        fatalErrorInBuffer=currentBufferChunk.inFatalError
        lastBuffer=currentBufferChunk.isLastBuffer
        bufferReady = true
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun fillBufferQueue(){
        var maxPos:Int
        if (!prepared) throw IllegalStateException("Extractor not ready or already released")
        CoroutineScope(Dispatchers.IO).launch {
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
                        newByteBufferChunk.byteBuffer.limit()
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

    @Throws(IllegalStateException::class, CodecException::class )
    fun inputForBuff(): Boolean {
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
    fun outputToBuff(currentBuffer: ByteBuffer): Boolean {
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




    @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
    override fun read(): Int {
        throw NoSuchMethodException ("Not implemented, use read(b[].... )")
    }

    @Throws(IOException::class,IllegalArgumentException::class,
        NullPointerException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException("Null byte array passed")
        if (closed) throw IOException("Stream already closed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IllegalArgumentException("Wrong read(...) params")
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
        if (len==0) return 0
        if (((bytesRead >= bytesFinalCount && bytesFinalCount != 0)&&!fatalErrorInBuffer)) {
            //close()
            //todo - а вообще формально доигранный до конца поток или поток с ошибкой и нормально
            // закрытый это одно и то же? как я понимаю вроде для доигранного если он mark
            // поддерживает можно отмотать на начало. Если сделаю поддержку перемотки то
            // close() тут не нужен
            return -1
        }
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


    override fun close() {
        closed=true
        released=true
        bufferInfo=null
        channelsCount=0
        sampleRate=0
        duration=0
        if (!released)  extractor.release()
        mainBuffer= ByteBuffer.allocate(1)
        bufferQueue=ArrayBlockingQueue(1)
        codecInputBuffers=Array(1){ ByteBuffer.allocate(1)}
        codecOutputBuffers=Array(1){ ByteBuffer.allocate(1)}
    }

    @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
    override fun skip(n: Long): Long {
        //todo - разбить на запрос кусков разумного размера, скажем 16K
        return read(ByteArray(n.toInt())) .toLong()
    }


    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        val byteArray =ByteArray(b.size*2)
        val bytes = read(byteArray, off, len*2)
        if (bytes==-1) return -1
        val resultShorts= byteToShortArrayLittleEndian(byteArray)
        resultShorts.copyInto(b,0,0,bytes/2)
        return bytes/2
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

        /*
        Returns an estimate of the number of bytes that can be read (or skipped over) from this input
        stream without blocking by the next invocation of a method for this input stream.
        The next invocation might be the same thread or another thread. A single read or skip of this
        many bytes will not block, but may read or skip fewer bytes.
        */

    //Чтение большего количества инициализирует блокирующий запрос на заполнение буфера
    //или неблокирующий запрос готового
    override fun available(): Int = max(mainBuffer.remaining(),bytesRemainingEstimate().toInt())

    override fun readShorts(b: ShortArray): Int = readShorts(b,0,b.size)

    override fun canReadShorts(): Boolean = true

    override fun toString(): String = "AudioFileSoundStream path='$path'," +
            " uri=$uri, media format=$mediaFormat)"

    inner class BufferedChunk{
        val byteBuffer:ByteBuffer= ByteBuffer.allocate(MAX_BUFFER_SIZE)
        var isLastBuffer:Boolean=true
        var inFatalError:Boolean=false
        var exception:Exception?=null
    }
}






