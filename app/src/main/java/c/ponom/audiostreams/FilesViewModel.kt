package c.ponom.audiostreams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import c.ponom.audiostreams.audio_streams.AudioFileSoundSource
import c.ponom.audiostreams.audio_streams.AudioTrackOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.System.currentTimeMillis

@Suppress("BlockingMethodInNonBlockingContext")
class FilesViewModel : ViewModel() {

    internal var playing: Boolean=false
    var secondsPlayed: MutableLiveData<String> = MutableLiveData("")
    var mediaData: MutableLiveData<String> = MutableLiveData("")



    internal fun playUri(context: Context,uri: Uri){
        if (playing)return
        if (uri == Uri.EMPTY) return
        val audioInStream: AudioFileSoundSource.SoundInputStream
        val audioOutStream:AudioTrackOutputStream
        try {
            audioInStream = AudioFileSoundSource().getStream(context,uri)
            audioOutStream= AudioTrackOutputStream(audioInStream.sampleRate,audioInStream.channelsCount)
        }catch (e:Exception){
            mediaData.postValue("Error in media file - ${e.localizedMessage} ")
            return
        }
        playing=true
        // используется стандартный вывод
        CoroutineScope(Dispatchers.IO).launch{
            audioOutStream.play()
            val bufferArray = ShortArray(1024)
            var lastSecond = currentTimeMillis() /1000
            do {
                try {
                    if (!playing) break
                    val samples = audioInStream.readShorts(bufferArray)
                    if (samples > 0) audioOutStream.writeShorts(bufferArray)
                    else break
                    //вывод временной метки раз в секунду
                    val currentSecond = currentTimeMillis() /1000
                    if (currentSecond>lastSecond){
                        lastSecond=currentSecond
                        secondsPlayed.postValue(audioOutStream.timeString())
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

}

