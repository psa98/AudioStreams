package c.ponom.audiostreams

import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreams.MicRecordState.*
import c.ponom.audiostreams.audio_streams.*
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.audiostreams.audio_streams.SoundVolumeUtils.getRMSVolume
import c.ponom.recorder2.audio_streams.TAG
import com.naman14.androidlame.LameBuilder
import java.io.File
import java.io.FileOutputStream


class MicTestViewModel : ViewModel() {



    var recordLevel: MutableLiveData<Float> = MutableLiveData(0.0f)
    var  bytesPassed: MutableLiveData<Int> = MutableLiveData(0)
    var  recorderState: MutableLiveData<MicRecordState> = MutableLiveData(NO_FILE_RECORDED)
    private val outDirName= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString()
    private val outDir = File("$outDirName/AudioStreams/").apply { mkdir() }
    private lateinit var audioPump:StreamPump
    //todo - сделать имена файлов переменными от времени
    // иначе при переустановке приложения прежний
    // файл будет реадонли и его надо удалить
    private val testFileMp3 = File(outDir, "/TestMicStream.mp3")
    var recordingIsOn=false


    fun record(source: Int, sampleRate: Int) {
        //подготовим входящий микрофонный поток
        recordingIsOn=true
        val outputFileStream:FileOutputStream
        //подготовим входящий микрофонный поток и исходящий файловый
        try {
             outputFileStream = testFileMp3.outputStream()
        } catch (e:Exception){
            Toast.makeText(App.appContext,"Need permissions to work!", Toast.LENGTH_LONG).show()
            return
        }
        val testMicStream=MicSoundInputStream(sampleRate, source)
        // рекомендуемый битрейт для частоты не более (частота/137), аналогично соотношению 44100/320
        val encoderStream=Mp3OutputAudioStream(outputFileStream,
            sampleRate,sampleRate/160, LameBuilder.Mode.MONO)
        audioPump=StreamPump(testMicStream, encoderStream,bufferSize=1000,
            onEachPump = {recordLevel.postValue(getRMSVolume(byteToShortArrayLittleEndian(it)).toFloat())},
            onWrite =  { bytesPassed.postValue(it.toInt())},
            onFatalError={
                Log.e(TAG, "Error=${it.localizedMessage}")
                recorderState.postValue(NO_FILE_RECORDED)
                // можно искуственно создать ошибку, к примеру, заменив выше sampleRate/150
                // на недопустимый для частоты 16000 параметр =320
             })

        testMicStream.startRecordingSession()
        audioPump.start(true)
        recorderState.postValue(RECORDING)
            Log.e(TAG, "Recording $source, $sampleRate")
    }

    fun stopRecording() {
        recordingIsOn=false
        audioPump.stop(true)
        recorderState.postValue(STOPPED_READY)
    }

    fun play() {
        val audioIn = AudioFileSoundStream(testFileMp3.path)
        val audioOut= AudioTrackOutputStream(audioIn.sampleRate,audioIn.channelsCount,
            audioIn.encoding,0)
        audioPump=StreamPump(audioIn, audioOut, 4096,
            onEachPump = {recordLevel.postValue(getRMSVolume(byteToShortArrayLittleEndian(it)).toFloat())},
            onWrite =  { bytesPassed.postValue(it.toInt())},
            onFinish = {recorderState.postValue(STOPPED_READY)},
            onFatalError={ Log.e(TAG, "Error=${it.localizedMessage}")})
        audioOut.play()
        audioPump.start(true)
        recorderState.postValue(PLAYING)
    }

    fun stopPlaying() {
        if (audioPump.state==StreamPump.State.PUMPING) audioPump.stop()
        recorderState.postValue(STOPPED_READY)
    }

}

enum class MicRecordState{
    NO_FILE_RECORDED,
    STOPPED_READY,
    RECORDING,
    PLAYING
}