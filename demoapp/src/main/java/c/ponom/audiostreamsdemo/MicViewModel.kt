package c.ponom.audiostreamsdemo

import android.os.Environment.DIRECTORY_MUSIC
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreamsdemo.MicRecordState.*
import c.ponom.audiostreamsdemo.RecordLevelControl.doSimpleProcessing
import c.ponom.audiuostreams.audiostreams.*
import c.ponom.audiuostreams.audiostreams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.audiuostreams.audiostreams.SoundVolumeUtils.getRMSVolume
import com.naman14.androidlame.LameBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random


class MicTestViewModel : ViewModel() {


    var targetVolume: Float=1f

    // using different filenames for different instances
    private val testFileNum = Random(137).nextInt(100000).toString(16)
    var recordLevel: MutableLiveData<Float> = MutableLiveData(0.0f)
    var bytesPassed: MutableLiveData<Int> = MutableLiveData(0)
    var recorderState: MutableLiveData<MicRecordState> = MutableLiveData(NO_FILE_RECORDED)
    private val outDirName = getExternalStoragePublicDirectory(DIRECTORY_MUSIC).toString()
    private val outDir = File("$outDirName/AudioStreams/").apply { mkdir() }
    private lateinit var audioPump:StreamPump
    private val testFileMp3 = File(outDir, "/TestMicStream_$testFileNum.mp3")
    private var recordingIsOn=false


    fun record(source: Int, sampleRate: Int) {
        val outputFileStream:FileOutputStream
        try {
             outputFileStream = testFileMp3.outputStream()
        } catch (e:Exception){
            makeText(App.appContext,"Need all permissions to work!", LENGTH_LONG).show()
            return
        }
        recordingIsOn=true
        val testMicStream=MicSoundInputStream(sampleRate, source)
        // recommended mp3 bitrate should be no more than sampleRate/137, like in 44100/320,
        // or even sampleRate/160. See table of recommended bitrate|sample rate combinations
        // in Mp3OutputAudioStream() javadoc
        val encoderStream=Mp3OutputAudioStream(outputFileStream,
            sampleRate,sampleRate/160, LameBuilder.Mode.MONO)
        /*
        audioPump=StreamPump(testMicStream, encoderStream,bufferSize=1000,
            onEachPump = {recordLevel.postValue(getRMSVolume(byteToShortArrayLittleEndian(it)).toFloat())},
            onWrite =  { bytesPassed.postValue(it.toInt())},
            onFatalError={
                Log.e(TAG, "Error=${it.localizedMessage}")
                recorderState.postValue(NO_FILE_RECORDED)
             })
        audioPump.start(true)
        */

        recordingIsOn=true
        testMicStream.startRecordingSession()
        val buffer = ShortArray(sampleRate/4)
        recorderState.postValue(RECORDING)

        //Using readShorts and writeShorts with simple on the fly buffer preprocessing
        CoroutineScope(IO).launch {
            do {
                try{
                    val bytes = testMicStream.readShorts(buffer)
                    if (bytes==0) continue
                    val newBuffer= doSimpleProcessing(buffer,targetVolume)
                    val level = getRMSVolume(newBuffer)
                    recordLevel.postValue(level.toFloat())
                    bytesPassed.postValue(testMicStream.bytesRead.toInt())
                    if (bytes<0) {
                        recordingIsOn=false
                        testMicStream.close()
                        encoderStream.close()
                        break
                    } else {
                        encoderStream.writeShorts(newBuffer,0,bytes)
                    }
                } catch (e:java.lang.Exception){
                    recordingIsOn=false
                    Log.e(TAG, "Error=${e.localizedMessage}")
                    recorderState.postValue(NO_FILE_RECORDED)
                    try {
                        testMicStream.close()
                        encoderStream.close()
                    }catch (e:java.lang.Exception){
                        e.printStackTrace()
                    }
                    break
                }
            }while (recordingIsOn)
        }
        Log.i(TAG, "Recording $source, $sampleRate")
    }

    fun stopRecording() {
        recordingIsOn=false
        //audioPump.stop(true)
        // Test for  StreamPump class auto close feature with stop(true)
        recorderState.postValue(STOPPED_READY)
    }

    fun play() {
        val audioIn = AudioFileSoundStream(testFileMp3.path)
        val audioOut= AudioTrackOutputStream(audioIn.sampleRate,audioIn.channelsCount,0)
        audioPump=StreamPump(audioIn, audioOut, 2048,
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