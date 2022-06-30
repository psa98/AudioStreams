package c.ponom.audiostreams

import android.media.AudioFormat
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreams.audio_streams.AudioTrackOutputStream
import c.ponom.audiostreams.audio_streams.StreamPump
import c.ponom.recorder2.audio_streams.TAG
import c.ponom.recorder2.audio_streams.TestSoundInputStream

private const val  SAMPLE_RATE = 16000
class AudioOutViewModel : ViewModel() {

    private lateinit var audioIn: TestSoundInputStream
    private var audioOutStream: AudioTrackOutputStream?=null
    var secondsPlayed: MutableLiveData<Float> = MutableLiveData(0.0f)
    var recorderState: MutableLiveData<AudioOutState> = MutableLiveData(AudioOutState.STOPPED)
    var errorData: MutableLiveData<String> = MutableLiveData("")
    private lateinit var audioPump: StreamPump

    fun play(freq:Double,volume:Short) {


        //try, для тестов сбоев конструктора при заведомо неправильных значениях
        try {
            audioIn = TestSoundInputStream(freq,volume, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,)
            audioOutStream= AudioTrackOutputStream(audioIn.sampleRate,1,audioIn.encoding,0)
         }catch (e:Exception){
            onError(e)
            return
        }

        audioPump=StreamPump(audioIn, audioOutStream!!, SAMPLE_RATE*2,
            onWrite =  { secondsPlayed.postValue(audioIn.timestamp/1000.0f)},
            onFinish = {recorderState.postValue(AudioOutState.STOPPED)},
            onFatalError= {onError(it)})
        audioOutStream?.play()
        audioPump.start(false)
        recorderState.postValue(AudioOutState.PLAYING)
    }

    private fun onError (e:Exception) {
        Log.e(TAG, "Error=${e.localizedMessage}")
        errorData.postValue(e.localizedMessage)
        recorderState.postValue(AudioOutState.ERROR)
        audioIn.close() //протестить отдельной кнопкой бросив ошибку микрофона
        audioOutStream?.close()
    }


    fun stopPlaying() {
        val audioOut=audioOutStream?.audioOut



        // полученный объект позволяет низкоуровнево  обращаться к внутренним свойствам AudioTrack,
        // к примеру можно сменить устройство вывода, получить данные о состоянии,подключить эффекты,
        // слушатели на позицию
        if (audioOut?.playState== PLAYSTATE_STOPPED) return
        if (audioPump.state==StreamPump.State.PUMPING){

            // остановит звук без щелчка в случае если это не сможет сделать штатный метод stopAndClear()
            audioOut?.setVolume(0.0f)
            audioOut?.stop()
            //audioOutStream?.close() - бросит ошибку
            audioOutStream?.stop()
            audioPump.stop()
            // тестируется ручное закрытие потоков
            audioIn.close()
            audioOutStream?.close()
            recorderState.postValue(AudioOutState.STOPPED)
        }
    }
    // todo имитация ошибки в выводном потоке для теста обработки ошибок StreamPump
    //audioOut?.release()
}


enum class AudioOutState{
    STOPPED,
    PLAYING,
    ERROR
}