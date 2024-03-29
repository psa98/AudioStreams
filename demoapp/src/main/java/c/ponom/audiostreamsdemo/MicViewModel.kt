package c.ponom.audiostreamsdemo

import android.util.Log
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreamsdemo.MicRecordState.NO_FILE_RECORDED
import c.ponom.audiostreamsdemo.MicRecordState.PLAYING
import c.ponom.audiostreamsdemo.MicRecordState.RECORDING
import c.ponom.audiostreamsdemo.MicRecordState.STOPPED_READY
import c.ponom.audiostreamsdemo.RecordLevelControl.doSimpleProcessing
import c.ponom.audiuostreams.audiostreams.ArrayUtils.toShortArrayLittleEndian
import c.ponom.audiuostreams.audiostreams.AudioFileSoundStream
import c.ponom.audiuostreams.audiostreams.AudioTrackOutputStream
import c.ponom.audiuostreams.audiostreams.MicSoundInputStream
import c.ponom.audiuostreams.audiostreams.Mp3OutputAudioStream
import c.ponom.audiuostreams.audiostreams.SoundVolumeUtils.getRMSVolume
import c.ponom.audiuostreams.audiostreams.StreamPump
import com.naman14.androidlame.LameBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream


class MicTestViewModel : ViewModel() {


    var targetVolume: Float = 1f
    var recordLevel: MutableLiveData<Float> = MutableLiveData(0.0f)
    var bytesPassed: MutableLiveData<Int> = MutableLiveData(0)
    var recorderState: MutableLiveData<MicRecordState> = MutableLiveData(NO_FILE_RECORDED)
    private val outDirName = App.appContext.filesDir.toString()
    private val outDir = File("$outDirName/AudioStreams/").apply { mkdir() }
    private lateinit var audioPump: StreamPump
    private val testFileNum = "MicFile"
    private val testFileMp3 = File(outDir, "/TestMicStream_$testFileNum.mp3")
    private var recordingIsOn = false


    fun record(source: Int, sampleRate: Int) {
        val outputFileStream: FileOutputStream
        try {
            outputFileStream = testFileMp3.outputStream()
        } catch (e: Exception) {
            makeText(App.appContext, "Need all permissions to work!", LENGTH_LONG).show()
            return
        }
        recordingIsOn = true
        val testMicStream = MicSoundInputStream(sampleRate, source)
        // recommended mp3 bitrate should be no more than sampleRate/137, like in 44100/320,
        // or even sampleRate/160. See table of recommended bitrate|sample rate combinations
        // in Mp3OutputAudioStream() javadoc
        val encoderStream = Mp3OutputAudioStream(
            outputFileStream, sampleRate, sampleRate / 160, LameBuilder.Mode.MONO
        )
        recordingIsOn = true
        testMicStream.startRecordingSession()
        recorderState.postValue(RECORDING)
        CoroutineScope(IO).launch {
            recordMic(sampleRate, testMicStream, encoderStream)
        }
        Log.i(TAG, "Recording $source, $sampleRate")
    }

    private fun recordMic(
        sampleRate: Int, testMicStream: MicSoundInputStream, encoderStream: Mp3OutputAudioStream
    ) {
        val buffer = ShortArray(sampleRate / 4)
        while (recordingIsOn) {
            try {
                val shorts = testMicStream.readShorts(buffer)
                if (shorts == 0) continue
                if (shorts < 0) {
                    recordingIsOn = false
                    break
                }
                //Using readShorts and writeShorts with simple on the fly buffer preprocessing
                val newBuffer = doSimpleProcessing(buffer.copyOf(shorts), targetVolume)
                val level = getRMSVolume(newBuffer)
                if (recordingIsOn)
                    recordLevel.postValue(level.toFloat())
                else
                    recordLevel.postValue(0f)
                bytesPassed.postValue(testMicStream.bytesRead.toInt())
                encoderStream.writeShorts(newBuffer,0,shorts)
            } catch (e: java.lang.Exception) {
                recordingIsOn = false
                Log.e(TAG, "Error=${e.localizedMessage}")
                recorderState.postValue(NO_FILE_RECORDED)
                break
            }
        }
        try {
            testMicStream.close()
            encoderStream.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        recordingIsOn = false
        recorderState.postValue(STOPPED_READY)
    }

    fun play() {
        val audioIn = AudioFileSoundStream(testFileMp3.path)
        val audioOut = AudioTrackOutputStream(audioIn.sampleRate, audioIn.channelsCount, 0)
        audioPump = StreamPump(audioIn,
            audioOut,
            2048,
            onEachPump = { recordLevel.postValue(getRMSVolume(it.toShortArrayLittleEndian()).toFloat()) },
            onWrite = { bytesPassed.postValue(it.toInt()) },
            onFinish = { recorderState.postValue(STOPPED_READY) },
            onFatalError = {
                Log.e(TAG, "Error=${it.localizedMessage}")
                recorderState.postValue(NO_FILE_RECORDED)
            })
        audioOut.play()
        audioPump.start(true)
        recorderState.postValue(PLAYING)
    }

    fun stopPlaying() {
        if (audioPump.state == StreamPump.State.PUMPING) audioPump.stop()
        recorderState.postValue(STOPPED_READY)
    }
}

enum class MicRecordState {
    NO_FILE_RECORDED, STOPPED_READY, RECORDING, PLAYING
}