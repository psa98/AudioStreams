package c.ponom.audiostreams.audio_streams

import c.ponom.audiostreams.audio_streams.ArrayUtils.shortToByteArrayLittleEndian
import c.ponom.audiostreams.audio_streams.StreamPump.State.*
import c.ponom.recorder2.audio_streams.AudioInputStream
import c.ponom.recorder2.audio_streams.AudioOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException


/*
вызов коллбэков происходит в IO потоке!
 */
class StreamPump @JvmOverloads constructor(
    val outputStream: AudioOutputStream,
    val inputStream: AudioInputStream,
    private val bufferSize:Int = 8192,
    private val onEachPump: ((b: ByteArray) -> Unit) = {},
    val onWrite:(bytesWritten:Long) -> Unit ={},
    private val onFinish: () -> Unit = {},
    private val onFatalError: (e: Exception) -> Unit = {}
    ) {



    /*
        поток читает (жадным образом), блокирующим чтением, все из входного потока до получения там -1,
        либо команды stop. команда стоп передается потом потока как close(). Пока не определился будет ли он
        входным, выходным или просто классом.
        доступные методы:
        stop, start, pause, resume, setmaxspeed, setbuffersize, getIn|Out, get state
        идея простестить на нем откачку данных в поток распознавания  - на входе write от микрофона
        на выходе запись в файл, и посмотреть нет ли дыр или ошибок в полученном файле

        */
    var state: State=NOT_READY
        private set
    private var canPumpShorts=false
    private val byteBuffer:ByteArray
    private val shortBuffer:ShortArray



    @Volatile
    var bytesSent=0L
    private set


    @Throws(IllegalStateException::class)
    fun start(){
        when(state){
            NOT_READY -> return
            PREPARED, PUMPING -> {
                state=PUMPING
                pump()
            }
            PAUSED -> {
                state=PUMPING
                pump()
            }
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("already finished on error")
        }
   }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun pump() {
        CoroutineScope(Dispatchers.IO).launch{
            do {
                if (state==FINISHED||state==FATAL_ERROR) break
                var read: Int
                try {
                    if (canPumpShorts) {
                        read = inputStream.readShorts(shortBuffer)
                        if (read>=0) {
                            bytesSent+=read*2
                            onWrite(bytesSent)
                        //у меня микрофонный поток может вернуть не -1 при ошибке,
                        // а другое значение, -3 или -6. Возможно я переделаю это под бросание
                        // исключения
                        } else read=-1
                        outputStream.writeShorts(shortBuffer)
                        onEachPump(shortToByteArrayLittleEndian(shortBuffer))
                    }else{
                        read = inputStream.read(byteBuffer)
                        if (read>=0) {
                            bytesSent+=read
                            onWrite(bytesSent)
                        } else read=-1
                        outputStream.write(byteBuffer)
                        onEachPump(byteBuffer)
                    }
                    if (read < 0) {
                        inputStream.close()
                        outputStream.close()
                        state = FINISHED
                        onFinish
                    }
                } catch (e: Exception) {
                    state = FATAL_ERROR
                    onFatalError (e)
                    onFinish
                  break
                }
            } while (read >= 0&& state!=PAUSED)
        }
    }


    @Throws(IllegalStateException::class)
    fun stop(){
        when(state){
            NOT_READY, PREPARED -> return
            PAUSED, PUMPING -> {
                state=FINISHED
                try {
                inputStream.close()
                outputStream.close()
                } catch (e:IOException){
                    e.printStackTrace()
                    onFatalError(e)
                    state=FATAL_ERROR
                }
                onFinish
            }
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("already finished on error")
        }

    }

    @Throws(IllegalStateException::class)
    fun pause(){
        when(state){
            NOT_READY, PREPARED,PAUSED ->return
            PUMPING -> state=PAUSED
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("Already finished on error")
        }
    }

    @Throws(IllegalStateException::class)
    fun resume(){
        when(state){
            NOT_READY, PREPARED, PUMPING ->return
            PAUSED-> {
                state=PUMPING
                pump()
            }
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("Already finished on error")
        }
    }


    enum class State{
        NOT_READY,
        PREPARED,
        PUMPING,
        PAUSED,
        FINISHED,
        FATAL_ERROR
    }

    init {
        canPumpShorts = inputStream.canReadShorts() && outputStream.canWriteShorts()
          if (bufferSize < 2) {
            throw IllegalArgumentException ("Buffer size should be at least 2 bytes")
        } else {
            byteBuffer = ByteArray(bufferSize)
            shortBuffer = ShortArray(bufferSize/2)
        }
        state = PREPARED
    }
}