@file:Suppress("DEPRECATION")

package c.ponom.recorder2.audio_streams

import android.content.Context
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/*
*максимальный размер буфера - соответствует примерно 4 минут
*для  моно записи на 16000 отсчетов в сек, ~1 мин на стерео 44100
*/
private const val MAX_BUFFER_SIZE = 4096 * 1024

// резервная зона в конце буфера, что бы избежать его переполнениея
private const val RESERVE_BUFFER_SIZE = 96 * 1024
private const val TIMEOUT_US = 500_000L


private const val LOG_TAG: String = "Decoder"


/*
Класс возвращает монофонический поток байтов (little ended, 16bit, из левого канала)
 из заданного звукового файла. Для моей задачи требовался именно монофонический поток,
 поэтому перед отдачей для стерео данных проводится deinterleaving в моно данные.
 Убрать соответствующий код не составит труда
 */




@Suppress("unused")
open class AudioFileSoundSourceOld { //todo - переделать под


    private lateinit var currentBuffer: ByteBuffer
    private var maxChunkSize = 0
    private lateinit var codec: MediaCodec
    private var bufferReady: Boolean = false
    private var maxPos = MAX_BUFFER_SIZE - RESERVE_BUFFER_SIZE
    private val extractor: MediaExtractor = MediaExtractor()
    private lateinit var codecInputBuffers: Array<ByteBuffer>
    private lateinit var codecOutputBuffers: Array<ByteBuffer>
    private var inputEOS = false
    private var outputEOS = false
    private var eofReached = false
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var prepared = false

    private var newBufferReady: Boolean = false
    private var newBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var mainBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var lock = Any()
    private val bufferQueue:ArrayBlockingQueue<BufferedChunck> = ArrayBlockingQueue(1000,true)

    private  var bufferFutureList: LinkedBlockingDeque<Future<ByteBuffer>> = LinkedBlockingDeque(3)

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
    private val bufferNum:AtomicInteger= AtomicInteger(0)


    private val executor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor

    /**
     * файл должен иметь строго один трек!
     */
    @Throws(IOException::class,IllegalArgumentException::class, CodecException::class)
    fun getStream(fd: FileDescriptor): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")

        //todo! размер файла в этом случае добывается  FileInputStream(fd).channel.size()
        // todo - можно попробовать как-то добыть размер файла и добавит его в необязательные свойства

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
        // todo - uri тут может быть вообще чем попало, к примеру, ури от контентпровайдера.
        //размер файла тут можно попробовать получить как выше - если за ури стоит контент провайдер и
        // от него добываем поток -  размер файла в этом случае добывается через
        // открыть,  FileInputStream(fd).channel.size() закрыть
        extractor.setDataSource(context, uri, headers)
        return createStream()
    }

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
            if (!mimeString.contains("audio"))  // ни одной аудио дорожки, вот незадача!
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
        // todo если тут могут таки бросить исключение - надо их все обработать. И IO в процессе работы,
        //типа "внезапно стерли файл" -надо возможно бросить
        codec.configure(mediaFormat, null, null, 0)
        codec.start()
        codecInputBuffers = codec.inputBuffers
        codecOutputBuffers = codec.outputBuffers
        bufferInfo = MediaCodec.BufferInfo()
        prepared = true
        //fillBufferQueue()
        return SoundInputStream(mediaFormat)
    }



    //todo - протестить, погоняв на мини приложении с самыми разными форматами
    // в идеале добиться выброски всех эксепшнов в тестах, и убедиться что исходный поток
    // декодируется до байта.





    @Throws(IllegalStateException::class, CodecException::class)
    private fun fillBuffer() {
        if (!prepared || bufferReady || eofReached) throw
        IllegalStateException("Extractor not ready or already released")
        maxChunkSize=0 //todo пусть его возвращает input? не надо глобальных переменных
        mainBuffer.clear()
        do {
            input()
            output()
            //maxChunkSize - максимальный размер полученного буфера, типичный
            // показатель - единицы килобайт. Переполнение основного  буфера недопустимо, поэтому
            // при приближении к "резервной зоне" в его конце fillBuffer() прекращает дальнйшее
            // чтение
            maxPos = MAX_BUFFER_SIZE - max(RESERVE_BUFFER_SIZE, maxChunkSize)
            if (mainBuffer.position() > maxPos||eofReached) {
                break
            }
        } while (true)
        mainBuffer.position(0)
        bufferReady = true
    }

    @Throws(IllegalStateException::class, CodecException::class )
    private  fun input() {
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
    }


    @Throws(IllegalStateException::class, CodecException::class)
    private fun output() {
        val res =
            codec.dequeueOutputBuffer(bufferInfo!!, TIMEOUT_US)
        if (res >= 0) {
            val buf:ByteBuffer = codecOutputBuffers[res]
            val chunk = ByteArray(bufferInfo!!.size)
            buf.get(chunk)
            buf.clear()
            val bytesRead=chunk.size
            if (bytesRead > 0) {
                mainBuffer.put(chunk, 0, bytesRead)
                bytesTotalCount += bytesRead
            }

            if (bytesRead > maxChunkSize) maxChunkSize = bytesRead
            codec.releaseOutputBuffer(res, false /* render */)
            if (bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                bytesFinalCount = bytesTotalCount
                eofReached = true
                outputEOS = true
                released=true
                extractor.release()
            }
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            codecOutputBuffers = codec.outputBuffers
        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val format = codec.outputFormat
            // todo? ничего не делаем, не ожидается такое посреди трека
            // mAudioTrack.setPlaybackRate(oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        }
    }








    /**
     * Стоит обратить внимание на то что отдаваемый класс  SoundInputStream содержит:
     * 1. все необходимые данные о медиафайле, включая продолжительность
     * 2. довольно точнную оценку общей длины содержащегося байтового потока
     * 3. Возможность переопределить коллбэк под чтение каждой порции. К примеру, байты
     * из него может читать сторонняя библиотека, и полученные в коллбэке данные позволят
     * определить прогресс
       */



    inner class SoundInputStream(format: MediaFormat) : AudioInputStream(format), AutoCloseable {

        init {
            bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2
                    else throw IllegalArgumentException("Only 16bit Encoding media files accepted ")
            frameSize=bytesPerSample*channelsCount
        }

        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        @Synchronized         //todo тестить
        override fun read(): Int {
            if ((bytesSent >= bytesFinalCount && bytesFinalCount != 0))
                return -1
            //это ситуация =  отдан последний байт через return mainBuffer.get() ниже
            if (mainBuffer.remaining() == 0) fillBuffer()
            //это если флаг был выставлен а буфер забран   прошлым read(....)
            if (!bufferReady) fillBuffer()
            //а это для чтения по одному байту выставить флаг для следующего прохода
            if (mainBuffer.remaining() == 1) bufferReady = false
            bytesSent++
            onReadCallback?.invoke(bytesSent)
            return mainBuffer.get() + 128
         }

        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        @Synchronized
        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            if (b == null) throw NullPointerException("Null byte array passed")
            if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
            if ((bytesSent >= bytesFinalCount && bytesFinalCount != 0)
            ) return -1
            if (len==0) return 0
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
            mainBuffer= ByteBuffer.allocate(1)
            newBuffer= ByteBuffer.allocate(1)
            //bufferFutureList.clear()
            //отдадим мегабайты памяти обратно системе сразу
            codecInputBuffers=Array(1){ ByteBuffer.allocate(1)}
            codecOutputBuffers=Array(1){ ByteBuffer.allocate(1)}
        }
        /*
        Returns an estimate of the number of bytes that can be read (or skipped over) from this input
        stream without blocking by the next invocation of a method for this input stream.
        The next invocation might be the same thread or another thread. A single read or skip of this
        many bytes will not block, but may read or skip fewer bytes.
        Чтение большего количества инициализирует блокирующий запрос на заполненеие буфера.
        */


        @Synchronized
        @Throws(IllegalArgumentException::class,NullPointerException::class, CodecException::class)
        override fun skip(n: Long): Long {
            return read(ByteArray(n.toInt())).toLong()
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
            if (!bufferReady && !eofReached) fillBuffer()
            var length = len
            if (len >= mainBuffer.remaining()) {
                length = mainBuffer.remaining()
                bufferReady = false
                //getNextDataInBuffer()
            } // интересно, не может ли это случайно дать запрос на получение 0 если мы
            // считали последний байт после EOF? и потом цикл?
            mainBuffer.get(b, 0, length)
            return length
        }


        override fun available(): Int {
            return max(mainBuffer.remaining(),bytesRemainingEstimate().toInt())
        }
        override fun readShorts(b: ShortArray): Int {
            return readShorts(b,0,b.size)
        }
        override fun canReadShorts(): Boolean = true

        /*
        *
         private fun fillInBackground(): Future<ByteBuffer> {
            val fill: () -> ByteBuffer = { nextBuffer() }
            return executor.submit(fill)
        }


        @Throws(IllegalStateException::class, CodecException::class,IllegalArgumentException::class)
        private fun nextBuffer(): ByteBuffer {
            if (!prepared || bufferReady || eofReached)
                return ByteBuffer.allocate(0) // хз как сработает, это на случай если че то в очереди останется
            val buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
            synchronized(lock) {
                var lastPos: Int
                do {
                    input()
                    output()
                    lastPos = MAX_BUFFER_SIZE - max(RESERVE_BUFFER_SIZE, maxChunkSize)
                    if (buffer.position() > lastPos||eofReached) {
                        break
                    }
                } while (true)
                buffer.position(0)
            }
            return buffer
        }
         */






    }

    inner class BufferedChunck{
        val buffer:ByteBuffer= ByteBuffer.allocate(MAX_BUFFER_SIZE)
        var lastBuffer:Boolean=true
        var fatalError:Boolean=false
        var exception:Exception?=null
    }

    fun fillBufferQueue(){
        var _maxPos=0
        if (!prepared  ) throw IllegalStateException("Extractor not ready or already released")
        CoroutineScope(Dispatchers.IO).launch {
            maxChunkSize=0 //todo пусть его возвращает input? не надо глобальных переменных
            do {
                val newByteBuffer=BufferedChunck()
                currentBuffer = newByteBuffer.buffer
                    do {
                    try {


                        val eos = inputForBuff()
                        val eof = outputToBuff(currentBuffer)
                        _maxPos = MAX_BUFFER_SIZE - max(RESERVE_BUFFER_SIZE, maxChunkSize)
                        newByteBuffer.lastBuffer = eof
                        if (currentBuffer.position() > _maxPos || eof) break
                        //todo - обработку исключений как то
                    } catch (e:Exception){
                      newByteBuffer.lastBuffer=true
                      newByteBuffer.fatalError=true
                      newByteBuffer.exception=e
                      e.printStackTrace()
                      break
                    }

                    }while (true)
                    bufferQueue.put(newByteBuffer)
                if (newByteBuffer.lastBuffer||newByteBuffer.fatalError) break
                Log.e(TAG, "fillBufferQueue next ${bufferQueue.size}")
                } while (true)
                Log.e(TAG, "fillBufferQueue TOTAL size ${bufferQueue.size}")

            // при чтении из буфера надо будет в конце выкинуть -1 если буфер последний и бросить
            // ошибку если она поймана

        }









    }

    @Throws(IllegalStateException::class, CodecException::class )
    fun inputForBuff(): Boolean {
        var _inputEOS=false
        var _eofReached=false
        val inputBufIndex =
            codec.dequeueInputBuffer(TIMEOUT_US)
        Log.i(LOG_TAG, "inputBufIndex : $inputBufIndex")
        if (inputBufIndex >= 0) {
            val dstBuf = codecInputBuffers[inputBufIndex]
            var sampleSize = extractor.readSampleData(dstBuf, 0)
            var presentationTimeUs: Long = 0
            if (sampleSize < 0) {
                _inputEOS = true
                sampleSize = 0
            } else {
                presentationTimeUs = extractor.sampleTime
            }
            codec.queueInputBuffer(
                inputBufIndex,
                0,
                sampleSize,
                presentationTimeUs,
                if (_inputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            )
            if (!_inputEOS) {
                extractor.advance()
            }
        }
        return _inputEOS
    }

    @Throws(IllegalStateException::class, CodecException::class)
    fun outputToBuff(currentBuffer: ByteBuffer): Boolean {

        var _eofReached=false

        val res =
            codec.dequeueOutputBuffer(bufferInfo!!, TIMEOUT_US)
        if (res >= 0) {
            val buf:ByteBuffer = codecOutputBuffers[res]
            val chunk = ByteArray(bufferInfo!!.size)
            buf[chunk]
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
                _eofReached = true

                //released=true
                //extractor.release()
            }
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            codecOutputBuffers = codec.outputBuffers
        return _eofReached
    }


}

