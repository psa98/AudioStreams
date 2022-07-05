package c.ponom.audiostreams.audio_streams

import c.ponom.audiostreams.audio_streams.ArrayUtils.shortToByteArrayLittleEndian
import c.ponom.audiostreams.audio_streams.StreamPump.State.*
import c.ponom.recorder2.audio_streams.AudioInputStream
import c.ponom.recorder2.audio_streams.AudioOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException





// todo - перетестить
class StreamPump @JvmOverloads constructor(
    val inputStream: AudioInputStream,
    val outputStream: AudioOutputStream,
    val bufferSize:Int = 8192,
    var onEachPump: ((b: ByteArray) -> Unit) = {},
    var onWrite:(bytesWritten:Long) -> Unit ={},
    var onFinish: () -> Unit = {},
    var onFatalError: (e: Exception) -> Unit = {}

    ) {



        /*
        * поток читает (жадным образом, блокирующим чтением), все из входного потока до получения там -1,
        * либо команды stop. команда стоп или ошибка возможно передается потом в оба потока как close().
        * доступные методы:
        * stop, start, pause, resume, setmaxspeed, setbuffersize, getIn|OutStream, get state
        */

    var state: State=NOT_READY
        private set
    private var canPumpShorts=false
    private val byteBuffer:ByteArray
    private val shortBuffer:ShortArray
    @Volatile
    var bytesSent=0L
    private set

    @JvmOverloads
    @Throws(IllegalStateException::class)
    fun start(autoClose:Boolean=false){
        when(state){
            NOT_READY -> return
            PREPARED, PUMPING -> {
                state=PUMPING
                pump(autoClose)
            }
            PAUSED -> {
                state=PUMPING
                pump(autoClose)
            }
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("already finished on error")
        }
   }
    // задокументировать что onFinish вызывается всегда, даже при ошибке, после onError

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun pump(autoClose: Boolean) {
        CoroutineScope(Dispatchers.Default).launch{
            do {
                if (state==FINISHED||state==FATAL_ERROR) break
                var read: Int=0
                try {
                    if (canPumpShorts) {
                        read = inputStream.readShorts(shortBuffer)
                        if (read>=0) {
                            bytesSent+=read*2
                            onWrite(bytesSent)
                        //у меня микрофонный поток может вернуть не -1 при ошибке, поэтому не на -1 проверка
                            outputStream.writeShorts(shortBuffer)
                            onEachPump(shortToByteArrayLittleEndian(shortBuffer))
                            continue
                        } else read=-1
                     }else{
                        read = inputStream.read(byteBuffer)
                        if (read>=0) {
                            bytesSent+=read
                            onWrite(bytesSent)
                            outputStream.write(byteBuffer)
                            onEachPump(byteBuffer)
                            continue
                        } else read=-1
                    }
                    if (read < 0) {
                        if (autoClose) try {
                            //нормальное закрытие без ошибки
                            inputStream.close()
                            outputStream.close()
                            state=FINISHED
                            onFinish.invoke()
                            break
                        } catch (e:IOException){ // секция ловит ошибку в закрытии потоков
                            e.printStackTrace()
                            state=FATAL_ERROR
                            onFatalError(e)
                            onFinish.invoke()
                            break
                        } else {
                            state=FINISHED
                            onFinish.invoke()
                            break
                        }
                    }
                } catch (e: Exception) { // секция ловит ошибку в чтении записи потоков
                    state = FATAL_ERROR
                    onFatalError(e)
                    e.printStackTrace()
                    if(autoClose) try {
                        inputStream.close()
                        outputStream.close()
                        break
                    } catch (e:IOException){
                        // секция ловит ошибку в *закрытии* потоков.
                        // эта ошибка не передается выше, поскольку она затрет реальную
                        e.printStackTrace()
                        break
                     }finally {
                        onFinish.invoke()
                     }
                 }
            } while (read >= 0&& state!=PAUSED)

        }
    }

    // документировать параметр
    @JvmOverloads
    @Throws(IllegalStateException::class,IOException::class)
    fun stop(closeAllStreams:Boolean=false){
        when(state){
            NOT_READY, PREPARED -> return
            PAUSED, PUMPING -> {
                state=FINISHED
                if (closeAllStreams) try {
                    inputStream.close()
                    outputStream.close()
                } catch (e:IOException){
                    e.printStackTrace()
                    onFatalError(e)
                    state=FATAL_ERROR
                }
                onFinish.invoke()
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

    @JvmOverloads
    @Throws(IllegalStateException::class)
    fun resume(autoClose:Boolean=false){
        when(state){
            NOT_READY, PREPARED, PUMPING ->return
            PAUSED-> {
                state=PUMPING
                pump(autoClose)
            }
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("Already finished on error")
        }
    }


    enum class State{
        NOT_READY, //todo убрать
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