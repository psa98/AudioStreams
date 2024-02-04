package c.ponom.audiostreamsdemo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiuostreams.audiostreams.AudioFileSoundStream
import c.ponom.audiuostreams.audiostreams.AudioTrackOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch


class FilesViewModel : ViewModel() {

    var playing: Boolean = false
    var secondsPlayed: MutableLiveData<String> = MutableLiveData("")
    var mediaData: MutableLiveData<String> = MutableLiveData("")


    fun playUri(context: Context, uri: Uri) {
        if (playing || uri == Uri.EMPTY) return
        val audioInStream: AudioFileSoundStream
        val audioOutStream: AudioTrackOutputStream
        try {
            audioInStream = AudioFileSoundStream(context, uri)
            audioOutStream = AudioTrackOutputStream(
                audioInStream.sampleRate, audioInStream.channelsCount, 1000
            )
        } catch (e: java.lang.Exception) {
            mediaData.postValue("Error in media file - ${e.localizedMessage} ")
            return
        }
        CoroutineScope(IO).launch {
            audioOutStream.play()
            val bufferArray = ShortArray(1024)
            var lastTime = ""
            playing = true
            do {
                try {
                    if (!playing) break
                    val samples = audioInStream.readShorts(bufferArray)
                    if (samples > 0) audioOutStream.writeShorts(bufferArray, 0, samples)
                    else {
                        Log.i(TAG, "playUri - eof or error, samples=$samples")
                        break
                    }
                    val time = timeString(audioOutStream.timestamp)
                    if (lastTime != time) {
                        lastTime = time
                        secondsPlayed.postValue(timeString(audioOutStream.timestamp))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            } while (true)
            playing = false
            audioOutStream.close()
            audioInStream.close()
        }
    }


    private fun timeString(msTime: Number): String {
        val audioTime: String
        val dur = msTime.toInt()
        val hrs = dur / 3600000
        val mns = (dur / 60000 % 60000) - hrs * 60
        val scs = dur % 60000 / 1000
        audioTime = if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mns, scs)
        } else {
            String.format("%02d:%02d", mns, scs)
        }
        return audioTime
    }
}

