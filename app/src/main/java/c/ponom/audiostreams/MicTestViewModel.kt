package c.ponom.navgrafapp.ui.notifications

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.navgrafapp.ui.notifications.MicRecordState.NO_FILE_RECORDED
import c.ponom.recorder2.audio_streams.TAG

class MicTestViewModel : ViewModel() {
    fun record(input: Int, sampleRate: Int) {
        Log.e(TAG, "Recording $input, $sampleRate")
    }

    fun stopRecording() {
        Log.e(TAG, "Stop!")
   }

    fun play() {
        Log.e(TAG, "Play!")
    }

    fun stopPlaying() {
        Log.e(TAG, "Stop!")
    }


    var recordLevel: MutableLiveData<Float> = MutableLiveData(0.0f)
    var  bytesPassed: MutableLiveData<Int> = MutableLiveData(0)
    var  recorderState: MutableLiveData<MicRecordState> = MutableLiveData(NO_FILE_RECORDED)

    private val _text = MutableLiveData<String>().apply {
        value = "This is mic Fragment"
    }
    val text: LiveData<String> = _text
}

enum class MicRecordState{
    NO_FILE_RECORDED,
    STOPPED_READY,
    RECORDING,
    PLAYING
}