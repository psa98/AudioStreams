package c.ponom.audiostreams

import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreams.AudioOutState.*
import c.ponom.audiostreams.audio_streams.AudioTrackOutputStream
import c.ponom.audiostreams.audio_streams.StreamPump
import c.ponom.recorder2.audio_streams.TAG
import c.ponom.recorder2.audio_streams.TestSoundInputStream

class AudioOutViewModel : ViewModel() {



    private lateinit var audioInStream: TestSoundInputStream
    private var audioOutStream: AudioTrackOutputStream?=null
    var secondsPlayed: MutableLiveData<Float> = MutableLiveData(0.0f)
    var recorderState: MutableLiveData<AudioOutState> = MutableLiveData(STOPPED)
    var errorData: MutableLiveData<String> = MutableLiveData("")
    private lateinit var audioPump: StreamPump

    fun play(freq: Double, volume: Short, sampleRate: Int) {


        //тестируется поведение конструктора AudioTrackOutputStream при заведомо неправильных значениях параметров
        try {
            audioInStream = TestSoundInputStream(freq,volume, sampleRate, CHANNEL_IN_MONO,)
            audioOutStream= AudioTrackOutputStream(audioInStream.sampleRate,1,
                audioInStream.encoding,500)
         }catch (e:Exception){
            onError(e)
            return
        }
        /*выбор меньшего буфера чем sampleRate приведет в щелчкам из-за его underruning-a -
        StreamPump будет подавать данные меньшим темпом чем их будет забирать выход звука
        В общем случае размер буферов при использовании библиотечных классов следует подбирать
        в процессе отладки.
        */
        audioPump=StreamPump(audioInStream, audioOutStream!!, sampleRate*2,
            // тестируется правильность расчета поля audioInStream.timestamp
            onWrite =  { secondsPlayed.postValue(audioOutStream!!.timestamp/1000.0f)},
            onFinish = {recorderState.postValue(STOPPED)},
            onFatalError= {onError(it)})
        audioOutStream?.play()
        audioPump.start(false)
        recorderState.postValue(PLAYING)
    }

    private fun onError (e:Exception) {
        Log.e(TAG, "Error=${e.localizedMessage}")
        errorData.postValue(e.localizedMessage)
        recorderState.postValue(ERROR)
        audioInStream.close()
        audioOutStream?.close()
    }


    fun stopPlaying() {
        if (recorderState.value!=PLAYING) return
        val audioOut=audioOutStream?.audioOut
        /*полученный объект позволяет низкоуровнево  обращаться к внутренним свойствам AudioTrack,
        к примеру можно сменить устройство вывода, получить данные о состоянии,подключить эффекты,
        слушатели на позицию
        */
        if (audioOut?.playState==PLAYSTATE_STOPPED) return
        if (audioPump.state==StreamPump.State.PUMPING){
            // пример использования - остановит звук без щелчка
            audioOut?.setVolume(0.0f)
            audioOut?.stop()
            audioOutStream?.stop()
            audioPump.stop(false)
            // тестируется режим неавтоматического закрытие потоков StreamPump
            audioInStream.close()
            audioOutStream?.close()
            recorderState.postValue(STOPPED)
        }
    }

    fun forceError() {
        // имитация ошибки в выводном потоке для теста обработки ошибок StreamPump
        val audioOut=audioOutStream?.audioOut
        if (audioOut?.playState == PLAYSTATE_PLAYING) audioOut.release()
        // последующая запись в поток вызовет ошибку. Проверяется что ошибка  проброшена в onError
    }

    fun setVolume(volume: Float) {
        if (recorderState.value!=PLAYING) return
        val outStream = audioOutStream
        if (outStream==null||outStream.closed ) return
        else outStream.setVolume(volume)

    }
}

enum class AudioOutState{
    STOPPED,
    PLAYING,
    ERROR
}