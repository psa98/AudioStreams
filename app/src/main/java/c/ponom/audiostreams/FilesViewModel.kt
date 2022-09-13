package c.ponom.audiostreams

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreams.audio_streams.AudioFileSoundStream
import c.ponom.audiostreams.audio_streams.AudioTrackOutputStream
import c.ponom.recorder2.audio_streams.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

@Suppress("BlockingMethodInNonBlockingContext")
class FilesViewModel : ViewModel() {






    internal var playing: Boolean=false
    var secondsPlayed: MutableLiveData<String> = MutableLiveData("")
    var mediaData: MutableLiveData<String> = MutableLiveData("")


    internal fun playUri(context: Context,uri: Uri){
        if (playing)return
        if (uri == Uri.EMPTY) return
        val audioInStream: AudioFileSoundStream
        val audioOutStream:AudioTrackOutputStream
        try {
            audioInStream = AudioFileSoundStream(context,uri)
            audioOutStream= AudioTrackOutputStream(audioInStream.sampleRate,
                audioInStream.channelsCount,1000)
        }catch (e:java.lang.Exception){
            mediaData.postValue("Error in media file - ${e.localizedMessage} ")
            return
        }
        playing=true
        CoroutineScope(IO).launch{
            audioOutStream.play()
            val bufferArray = ShortArray(1024) // при выводе в динамик желателен малый буфер
            var lastTime =""
            do {
                try {
                    if (!playing) break
                    val samples = audioInStream.readShorts(bufferArray)
                    if (samples > 0) audioOutStream.writeShorts(bufferArray)
                    else{
                        Log.e(TAG, "playUri - eof or error, samples=$samples")
                        break
                    }
                    val time =timeString(audioOutStream.timestamp)
                    if (lastTime!=time){
                        lastTime=time
                        secondsPlayed.postValue(timeString(audioOutStream.timestamp))
                    }
                } catch (e:Exception){
                    e.printStackTrace()
                    break
                }
            }while(true)
            playing=false
            audioOutStream.close()
            audioInStream.close()
        }
    }


    private fun timeString(msTime:Long): String {
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

