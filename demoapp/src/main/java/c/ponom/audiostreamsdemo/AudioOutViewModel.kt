package c.ponom.audiostreamsdemo

import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import c.ponom.audiostreamsdemo.AudioOutState.*
import c.ponom.audiuostreams.audiostreams.AudioTrackOutputStream
import c.ponom.audiuostreams.audiostreams.StreamPump
import c.ponom.audiuostreams.audiostreams.TestSoundInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("BlockingMethodInNonBlockingContext")
class AudioOutViewModel : ViewModel() {


    private lateinit var audioInStream: TestSoundInputStream
    private var audioOutStream: AudioTrackOutputStream?=null
    var secondsPlayed: MutableLiveData<Float> = MutableLiveData(0.0f)
    var recorderState: MutableLiveData<AudioOutState> = MutableLiveData(STOPPED)
    var errorData: MutableLiveData<String> = MutableLiveData("")
    private lateinit var audioPump: StreamPump



    fun play(freq: Double, volume: Short, sampleRate: Int) {
        try {
            audioInStream = TestSoundInputStream(freq,volume, sampleRate, CHANNEL_IN_MONO)
            audioOutStream= AudioTrackOutputStream(audioInStream.sampleRate,
            audioInStream.channelsCount,1000)
         }catch (e:Exception){
            onError(e)
            return
        }

        audioPump=StreamPump(audioInStream, audioOutStream!!,sampleRate,
            // testing audioInStream.timestamp field
            onWrite =  { secondsPlayed.postValue(audioOutStream!!.timestamp/100/10f)},
            onFinish = {recorderState.postValue(STOPPED)},
            onFatalError= {onError(it)})
        Log.i(TAG, "play: AudioTrackOutputStream buffer =" +
                "${audioOutStream?.audioOut?.bufferSizeInFrames} frames" )
        audioPump.start(false)
        recorderState.postValue(PLAYING)
        viewModelScope.launch {
            delay(500)
            audioOutStream?.play()
        }

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
        /* Can access audioOut object for low-level control
        */
        if (audioOut?.playState==PLAYSTATE_STOPPED) return
        if (audioPump.state==StreamPump.State.PUMPING){
            // code below prevent audible clicks on stopping playback,
            // see audioOutStream.stopAndClear() source code
            audioOut?.setVolume(0.0f)
            CoroutineScope(Default).launch {
                recorderState.postValue(STOPPED)
                delay(60)
                // Test for  StreamPump class auto close feature with stop(false)
                audioOut?.stop()
                audioPump.stop(false)
                audioOutStream?.stop()
                audioInStream.close()
                audioOutStream?.close()

            }
        }
    }

    fun forceError() {
        // Test for onError in StreamPump
        val audioOut=audioOutStream?.audioOut
        if (audioOut?.playState == PLAYSTATE_PLAYING) audioOut.release()
        // Output to channel after audioOut.release() will force an error.
    }

    fun setVolume(volume: Float) {
        if (recorderState.value == PLAYING) {
            val outStream = audioOutStream
            if (outStream != null && !outStream.closed)
                outStream.setVolume(volume)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaying()
    }
}

enum class AudioOutState{
    STOPPED,
    PLAYING,
    ERROR
}