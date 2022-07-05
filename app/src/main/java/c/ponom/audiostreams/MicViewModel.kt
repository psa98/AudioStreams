package c.ponom.audiostreams

import android.os.Environment
import android.util.Log
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
    private lateinit var microphoneStream: MicSoundInputStream
    private val outDirName= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString()
    private val outDir = File("$outDirName/AudioStreams/").apply { mkdir() }

    var outputMicTest:FileOutputStream? =null
    private lateinit var mp3TestStream: Mp3OutputAudioStream
    private lateinit var audioPump:StreamPump
    val testFileMp3 = File(App.appContext.filesDir, "/TestMicStream.mp3")
    var recordingIsOn=false



    fun record(source: Int, sampleRate: Int) {
        //подготовим входящий микрофонный поток
        recordingIsOn=true

        val outputFileStream = testFileMp3.outputStream()
        val testMicStream=MicSoundInputStream(sampleRate,source,bufferMult = 16)
        testMicStream.startRecordingSession()
        // рекомендуемый битрейт для частоты не более (частота/137), аналогично соотношению 44100/320
        val encoderStream=Mp3OutputAudioStream(outputFileStream,
            sampleRate,sampleRate/137, LameBuilder.Mode.MONO
        )

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
        Log.e(TAG, "Play!")
        val audioIn = AudioFileSoundSource().getStream(testFileMp3.path)
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
        Log.e(TAG, "Stop!")
        recorderState.postValue(STOPPED_READY)
    }

}

enum class MicRecordState{
    NO_FILE_RECORDED,
    STOPPED_READY,
    RECORDING,
    PLAYING
}