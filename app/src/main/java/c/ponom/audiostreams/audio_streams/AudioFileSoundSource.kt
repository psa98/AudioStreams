@file:Suppress("DEPRECATION")

package c.ponom.audiostreams.audio_streams

import android.content.Context
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.recorder2.audio_streams.AudioInputStream
import c.ponom.recorder2.audio_streams.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.min

/*
*максимальный размер буфера - соответствует примерно ~~~~ сек на 44100
*/
private const val MAX_BUFFER_SIZE = 512 * 1024

// резервная зона в конце буфера, что бы избежать его переполнения,
// к ней плюсуется максимальный размер буфера используемого кодеком (типично 4-16Кб)
private const val RESERVE_BUFFER_SIZE = 32 * 1024
private const val TIMEOUT_US = 0L
private const val LOG_TAG: String = "Decoder"
private const val QUEUE_SIZE  =8


/**
 * Отдаваемый класс  SoundInputStream содержит:
 * 1. все необходимые данные о медиафайле, включая продолжительность
 * 2. довольно точнную оценку общей длины содержащегося байтового потока
 * 3. Возможность переопределить коллбэк под чтение каждой порции. К примеру, байты
 * из него может читать сторонняя библиотека, и полученные в коллбэке данные позволят
 * определить прогресс
 */




@Suppress("unused")
open class AudioFileSoundSource {

    private lateinit var currentBuffer: ByteBuffer
    private var maxChunkSize = 0
    private lateinit var codec: MediaCodec
    private var bufferReady: Boolean = false

    private val extractor: MediaExtractor = MediaExtractor()
    private lateinit var codecInputBuffers: Array<ByteBuffer>
    private lateinit var codecOutputBuffers: Array<ByteBuffer>
    private var inputEOS = false
    private var outputEOS = false
    private var eofReached = false
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var prepared = false
    private var currentBufferChunk = BufferedChunk()
    private var mainBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var lastBuffer =false
    private var fatalErrorInBuffer =false
    private var lock = Any()
    private var bufferQueue:ArrayBlockingQueue<BufferedChunk> = ArrayBlockingQueue(QUEUE_SIZE,true)
    private var bytesTotalCount = 0
    private var bytesFinalCount = 0
    /**
     * значение продолжительности в мс!
     * */
    private var fileDuration: Long? = null
    private var channelsCount: Int? = null
    private var sampleRate: Int? = null
    private var released = false
    private lateinit var uri: Uri
    // todo - осталось проверить что доигрывает

    /**
     * файл должен иметь строго один трек!
     */
    @Throws(IOException::class,IllegalArgumentException::class, CodecException::class)
    fun getStream(fd: FileDescriptor): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        extractor.setDataSource(fd)
        return createStream()
    }

    @Throws(IOException::class,IllegalArgumentException::class, CodecException::class)
    fun getStream(path: String): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        extractor.setDataSource(path)
        return createStream()
    }

    @JvmOverloads
    @Throws(IOException::class,IllegalArgumentException::class, CodecException::class)
    fun getStream(context: Context, uri: Uri, headers: MutableMap<String, String>? =null): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        this.uri = uri
        //uri тут может быть вообще чем попало, к примеру, ури от контентпровайдера.
        extractor.setDataSource(context, uri, headers)
        return createStream()
    }

    /* Берется первая звуковая дорожка файла!
    * // todo - свернуть исключения кодека в IOException
    *     //TODO -(1)  переписать вот это под работу с указанной дорожкой  (2) проверки ниже убрать
    *        (3)
    */
    @Throws(IOException::class,IllegalArgumentException ::class, CodecException::class)
    private fun createStream(): SoundInputStream {
        extractor.selectTrack(0)
        var mediaFormat:MediaFormat = extractor.getTrackFormat(0)
        var mimeString =""
        try {
            for (i:Int in 0 until extractor.trackCount) {
                mediaFormat = extractor.getTrackFormat(i)
                mimeString = mediaFormat.getString("mime").toString()
                if (mimeString.contains("audio")) break
            }
            if (!mimeString.contains("audio"))
                // ни одной аудио дорожки, вот незадача!
                throw IllegalArgumentException ("Wrong file - no audio tracks found")
            fileDuration = mediaFormat.getLong("durationUs").div(1000)
            sampleRate = mediaFormat.getInteger("sample-rate")
            channelsCount = mediaFormat.getInteger("channel-count")


        } catch (e:ClassCastException){
            // ничего не делаем, очень битый файл.
            throw IllegalArgumentException("Wrong file format")
            // todo документация андроида так же обещает что это три параметра декодер добудет
            // из потока с почти любым audio. Но я бы ограничил список разумным количеством
            // и документировал расширения, из raw ничего не добыть к примеру
        }
        codec = MediaCodec.createDecoderByType(mimeString)
        // todo если тут могут таки бросить исключение - надо их все обработать.
        codec.configure(mediaFormat, null, null, 0)
        codec.start()
        codecInputBuffers = codec.inputBuffers
        codecOutputBuffers = codec.outputBuffers
        bufferInfo = MediaCodec.BufferInfo()
        prepared = true
        fillBufferQueue()
        return SoundInputStream(mediaFormat)
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



    private fun fillBufferQueue(){
        var maxPos=0
        if (!prepared) throw IllegalStateException("Extractor not ready or already released")
        CoroutineScope(Dispatchers.Default).launch {
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
            Log.e(TAG, "fillBufferQueue last chunk TOTAL size ${bufferQueue.size}")
            // при чтении из буфера надо будет в конце выкинуть -1 если буфер последний или бросить
            // ошибку если она поймана, но не ранее отдачи всех байтов
        }
    }
    @Throws(IllegalStateException::class, CodecException::class )
    fun inputForBuff(): Boolean {
        var inputEOS=false
        val inputBufIndex =
            codec.dequeueInputBuffer(TIMEOUT_US)
        Log.i(LOG_TAG, "inputBufIndex : $inputBufIndex")
        if (inputBufIndex >= 0) {
            val dstBuf = codecInputBuffers[inputBufIndex]
            var sampleSize = extractor.readSampleData(dstBuf, 0)
            var presentationTimeUs: Long = 0
            if (sampleSize < 0) {
                inputEOS = true
                sampleSize = 0
            } else {
                presentationTimeUs = extractor.sampleTime
            }
            codec.queueInputBuffer(
                inputBufIndex,
                0,
                sampleSize,
                presentationTimeUs,
                if (inputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            )
            if (!inputEOS) {
                extractor.advance()
            }
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
            if (chunk.maxOrNull()!! <=0)
                Log.e(TAG, "CHUNK: = ZEROLEVEL")
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
                released=true
                extractor.release()
            }
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            codecOutputBuffers = codec.outputBuffers
        return eofReached
    }

    inner class SoundInputStream(format: MediaFormat) : AudioInputStream(format), AutoCloseable {
        init {
            bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2
                    else throw IllegalArgumentException("Only 16bit Encoding media files accepted ")
            frameSize=bytesPerSample*channelsCount

            //todo конструктор не устанавливает обязательные поля!
            // непойми зачем ставятся приватные поля выше
        }


        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        @Synchronized
        override fun read(): Int {
            throw NoSuchMethodException ("Not implemented, use read(b [].... )")
            /*todo тестить, возможны варианты реализации
               - оставить метод неподдерживаемым, все нормальные запросы от сторонних API
               требуют не побайтное чтение
               - вернуть данные запрошенные через b = ByteArray(1);read([b]); return b[1].toInt + 128

             */
        }

        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        @Synchronized
        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            if (b == null) throw NullPointerException("Null byte array passed")
            if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
            if (len==0) return 0
            if (((bytesSent >= bytesFinalCount && bytesFinalCount != 0)&&!fatalErrorInBuffer)) {
                //todo - а вообще формально доигранный до конца поток или поток с ошибкой и нормально
                // закрытый это одно и то же? как я понимаю вроде для доигранного если он mark
                // поддерживает можно отмотать на начало. Это на будущее если будет
                // реализовываться mark
                return -1
            }
            if ((bytesSent >= bytesFinalCount && bytesFinalCount != 0)&&fatalErrorInBuffer) {
                close()
                currentBufferChunk.exception?.printStackTrace()
                throw IOException("IO exception in audio codec =${currentBufferChunk.exception}")
            }
            val bytes =  getBytesFromBuffer(b, len)
            bytesSent += bytes
            onReadCallback?.invoke(bytesSent)
            return  bytes
        }
        @Synchronized
        override fun close() {

            if (released) return
            released=true
            bufferInfo=null
            channelsCount=0
            sampleRate=0
            duration=0
            extractor.release()
            //отдадим мегабайты памяти обратно системе сразу
            mainBuffer= ByteBuffer.allocate(1)
            bufferQueue=ArrayBlockingQueue(1)
            codecInputBuffers=Array(1){ ByteBuffer.allocate(1)}
            codecOutputBuffers=Array(1){ ByteBuffer.allocate(1)}
        }


        @Synchronized
        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        override fun skip(n: Long): Long {
            return read(ByteArray(n.toInt())) .toLong()
        }

        override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
            val byteArray =ByteArray(b.size*2)
            //todo - могут вылезать интересные вещи при нечетном числе байт в невалидных файлах
            val bytes = read(byteArray, off, len*2)
            if (bytes==-1)return -1
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
                    if (lastBuffer || fatalErrorInBuffer) eofReached = true
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
        override fun available(): Int {
            //Чтение большего количества инициализирует блокирующий запрос на заполнение буфера
            //или неблокирующий запрос готового
            return min(mainBuffer.remaining(),bytesRemainingEstimate().toInt()).coerceAtLeast(0)
        }

        override fun readShorts(b: ShortArray): Int {
            return readShorts(b,0,b.size)
        }

        override fun canReadShorts(): Boolean = true
    }

    inner class BufferedChunk{
        val byteBuffer:ByteBuffer= ByteBuffer.allocate(MAX_BUFFER_SIZE)
        var isLastBuffer:Boolean=true
        var inFatalError:Boolean=false
        var exception:Exception?=null
    }

}

