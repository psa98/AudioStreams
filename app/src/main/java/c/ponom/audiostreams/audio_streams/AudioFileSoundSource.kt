@file:Suppress("DEPRECATION")

package c.ponom.recorder2.audio_streams

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.lang.Integer.max
import java.nio.ByteBuffer

/*
*максимальный размер буфера - соответствует примерно 1 минуте
*для  моно записи на 16000 отсчетов в сек
*/
private const val MAX_BUFFER_SIZE = 1024 * 1024

// резервная зона в конце буфера, что бы избежать его переполнениея
private const val RESERVE_BUFFER_SIZE = 16 * 1024
private const val TIMEOUT_US = 500_000L


private const val LOG_TAG: String = "Decoder"


/*
Класс возвращает монофонический поток байтов (little ended, 16bit, из левого канала)
 из заданного звукового файла. Для моей задачи требовался именно монофонический поток,
 поэтому перед отдачей для стерео данных проводится deinterleaving в моно данные.
 Убрать соответствующий код не составит труда
 */




@Suppress("unused")
open class AudioFileSoundSource {
    private var maxChunkSize = 0
    lateinit var codec: MediaCodec
    private var bufferReady: Boolean = false
    private var maxPos = MAX_BUFFER_SIZE - RESERVE_BUFFER_SIZE
    private val extractor: MediaExtractor = MediaExtractor()
    private lateinit var codecInputBuffers: Array<ByteBuffer>
    private lateinit var codecOutputBuffers: Array<ByteBuffer>
    private var inputEOS = false
    private var outputEOS = false
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var prepared = false
    private var mainBuffer: ByteBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var bytesTotalCount = 0
    private var bytesFinalCount = 0
    private var eofReached = false

    /**
     * значение продолжительности в мс!
     * */
    private var fileDuration: Long? = null
    private var channelsCount: Int? = null
    private var sampleRate: Int? = null
    private var released = false
    private lateinit var uri: Uri

    /**
     * файл должен иметь строго один трек!
     */
    @Throws(IOException::class,IllegalArgumentException::class,MediaCodec.CodecException::class)
    fun getStream(fd: AssetFileDescriptor): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        extractor.setDataSource(fd)
        return createStream()
    }

    @Throws(IOException::class,IllegalArgumentException::class,MediaCodec.CodecException::class)
    fun getStream(path: String): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        extractor.setDataSource(path)
        return createStream()
    }

    @Throws(IOException::class,IllegalArgumentException::class,MediaCodec.CodecException::class)
    fun getStream(context: Context, uri: Uri): SoundInputStream {
        if (prepared || released)
            throw IllegalStateException("The extractor was already started or released, create new instance")
        this.uri = uri
        extractor.setDataSource(context, uri, null)
        return createStream()
    }

    @Throws(IOException::class,IllegalArgumentException ::class,MediaCodec.CodecException::class)
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
            // ничего не делаем, очень битый файл. todo - надо бы вернуть сразу -1
        }
        // todo документация андроида так же обещает что это три параметра декодер добудет
        // из потока с почти любым audio. Но я бы ограничил список разумным количеством
        // и документировал расширения, из raw ничего не добыть к примеру
        codec = MediaCodec.createDecoderByType(mimeString)
        // todo если тут могут таки бросить исклюение - надо их все обработать. И IO в процессе работы,
        //типа "внезапно стерли файл" - лучше просто выдать конец потока
        codec.configure(mediaFormat, null, null, 0)
        codec.start()
        codecInputBuffers = codec.inputBuffers
        codecOutputBuffers = codec.outputBuffers
        bufferInfo = MediaCodec.BufferInfo()
        prepared = true
        return SoundInputStream(mediaFormat)
    }



    //todo - протестить, погоняв на мини приложении с самыми разными форматами
    // в идеале добиться выброски всех эксепшнов в тестах, и убедиться что исходный поток
    // декодируется до байта.


    // todo - разумеется в проде надо сделать два буфера хотя бы, один из которых заполняется
    //  в то время пока из второго идет чтение. Пока что в данном случае заполнение буфера
    //  - блокирующая операция и может занимать сотни мс.

    @Throws(IllegalStateException::class,MediaCodec.CodecException::class)
    private fun fillBuffer() {
        if (!prepared || bufferReady || eofReached) throw
        IllegalStateException("Extractor not isReady or already released")
        maxChunkSize=0
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

    @Throws(IllegalStateException::class,MediaCodec.CodecException::class )
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


    @Throws(IllegalStateException::class,MediaCodec.CodecException::class)
    private fun output() {
        val res =
            codec.dequeueOutputBuffer(bufferInfo!!, TIMEOUT_US)
        if (res >= 0) {
            val buf = codecOutputBuffers[res]
            val chunk = ByteArray(bufferInfo!!.size)
            buf[chunk]
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
            // ничего не делаем, не ожидается такое посреди трека
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
     *
     * Ожидается что аналогичный поток будет реализован для микрофона и других устойств ввода вывода
     */



    open inner class SoundInputStream(format: MediaFormat) : AbstractSoundInputStream(format) {


        @Throws(IllegalArgumentException::class,NullPointerException::class,MediaCodec.CodecException::class)
        @Synchronized
        override fun read(): Int {
            if (bytesSent >= bytesFinalCount)
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



        // todo - сделать варианты такого же чтения - с коллбэком внутри,
        //  отрабатывающим по итогу (отдает результат, где ексепшн или байты)

        @Throws(IllegalArgumentException::class,NullPointerException::class,MediaCodec.CodecException::class)
        @Synchronized
        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            if (b == null) throw NullPointerException("Null byte array passed")
            if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
            // todo - реализовать
            if (bytesSent >= bytesFinalCount && bytesFinalCount != 0)
                return -1
            val bytes =  getBytesFromBuffer(b, off, len)
            bytesSent += bytes
            onReadCallback?.invoke(bytesSent)
            return  bytes
        }


        // todo -сделать read  отдающий 16 битные samples +  метод разбивки двойного набора на
        //  левый и правый. точнее для этого будет фильтр



        @Throws(IllegalArgumentException::class,NullPointerException::class,MediaCodec.CodecException::class)
        @Synchronized
        override fun read(b: ByteArray?): Int {
            if (b == null) throw NullPointerException("Null byte array passed") else
            return read(b,0, b.size)
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
            //отдадим мегабайт памяти обратно системе сразу, а то может дурень программист на этот
            //объект ссылку будет долго держать
            codecInputBuffers=Array(1){ ByteBuffer.allocate(1)}
            codecOutputBuffers=Array(1){ ByteBuffer.allocate(1)}
        }

        /*
        Returns an estimate of the number of bytes that can be read (or skipped over) from this input
        stream without blocking by the next invocation of a method for this input stream.
        The next invocation might be the same thread or another thread. A single read or skip of this
        many bytes will not block, but may read or skip fewer bytes.
        Чтение большего количества инициализирует блокирующий запрос на заполненеие буфера.
         Возможно оптимальный подход будет таким - запросить размер, потом запросить ровно столько
         и одновременно запросить в фоне следующий буфер (будет получен блокирующим чтением) и пока
         работать с первым полученным
        */
        override fun available(): Int {
            return max(mainBuffer.remaining(),bytesRemainingEstimate().toInt())
        }

        @Synchronized
        @Throws(IllegalArgumentException::class,NullPointerException::class,MediaCodec.CodecException::class)
        override fun skip(n: Long): Long {
            return read(ByteArray(n.toInt())).toLong()
        }



        private fun getBytesFromBuffer(b: ByteArray, off: Int, len: Int): Int {
            if (!bufferReady && !eofReached) fillBuffer()
            var length = len
            if (len >= mainBuffer.remaining()) {
                length = mainBuffer.remaining()
                bufferReady = false
            }
            mainBuffer.get(b, 0, length)
            return length
        }
    }

}

