package c.ponom.audiostreams.audio_streams

import c.ponom.audiostreams.audio_streams.AudioPumpStream.State.*
import c.ponom.recorder2.audio_streams.AudioInputStream
import c.ponom.recorder2.audio_streams.AudioOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException




/*
вызов коллбэков происходит в IO потоке!
 */
class AudioPumpStream @JvmOverloads constructor(
    private var outputStream: AudioOutputStream,
    private var inputStream: AudioInputStream,
    var onFinish: () -> Unit = {},
    var onFatalError: (e: Exception) -> Unit = {},
    private var bufferSize: Int = 0
) {
     val defaultBufferSizeShorts =4096
      val defaultBufferSizeBytes =8192
    /*
        поток читает (жадным образом), блокирующим чтением, все из входного потока до получения там -1,
        либо команды stop. команда стоп передается потом в оба потока как close(). Пока не определился будет ли он
        входным, выходным или просто классом.
        доступные методы:
        stop, start, pause, resume, setmaxspeed, setbuffersize, getIn|Out, get state
        идея простестить на нем откачку данных в поток распознавания  - на входе write от микрофона
        на выходе запись в файл, и посмотреть нет ли дыр или ошибок в полученном файле

        */
    var state: State
        private set

    private var canSendShort=false
    private val byteBuffer:ByteArray
    private val shortBuffer:ShortArray

    @Volatile
    var bytesSent=0L
    private set

    var onWrite:(bytesWritten:Long) -> Unit ={}

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
                if (state==FINISHED) break
                var read: Int
                try {
                    if (canSendShort) {
                        read = inputStream.readShorts(shortBuffer)
                        if (read>0) {
                            bytesSent+=read*2
                            onWrite(bytesSent)
                        //это под то, что у меня микрофонный поток может вернуть не -1 при ошибке,
                        // а другое значение, -3 или -6. Возможно я переделаю это под бросание
                        // исключения там, тогда убрать
                        } else read=-1
                        outputStream.writeShorts(shortBuffer)
                    }else{
                        read = inputStream.read(byteBuffer)
                        if (read>0) {
                            bytesSent+=read
                            onWrite(bytesSent)
                        } else read=-1
                    outputStream.write(byteBuffer)
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

    //todo сделать stopAndClose() отдельно, обычный стоп пусть не закрывает потоки,
    // с ошибками подумать че, пусть закрывают

    @Throws(IOException::class,IllegalStateException::class)
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
            PAUSED-> state=PUMPING
            FINISHED -> throw IllegalStateException ("Already finished")
            FATAL_ERROR -> throw IllegalStateException ("Already finished on error")
        }
    }
    @JvmOverloads
    fun setVolume(leftOrMono:Float=1f,right:Float=1f){
        //todo
    }

    enum class State{
        NOT_READY, //убрать? что будет при бросании исключения в init()?
        PREPARED,
        PUMPING,
        PAUSED,
        FINISHED,
        FATAL_ERROR
    }

    init {
        this.state = NOT_READY
        canSendShort = inputStream.canReadShorts() && outputStream.canWriteShorts()
        state = PREPARED
        if (bufferSize != 0) {
            byteBuffer = ByteArray(bufferSize)
            shortBuffer = ShortArray(bufferSize / 2)
        } else {
            byteBuffer = ByteArray(defaultBufferSizeBytes)
            shortBuffer = ShortArray(defaultBufferSizeShorts)
        }
    }
}