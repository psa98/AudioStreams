@file:Suppress("unused")

package c.ponom.audiostreams.audio_streams

/*  по классу
 * в исходном  моем варианте класс так же запрашивал исходный размер и дату создания объекта, но для
 *  uri или файлов контент-провайдера это слишком ненадежно работало, и требовало трудноотлаживаемых
 *  примерно +200 строк,  сами добывайте как хотите, убрал
 */


import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.IOException


class AudioDataInfo{
    private var uri: Uri=Uri.EMPTY
    private var path: String=""
    var mimeString: String?=""
        private set
    var duration:Long?=0L
        private set
    var samplingRate:Int? =0
        private set
    var channelsCount:Int? =0
        private set
    var mediaFormat:MediaFormat?=null
        private set
    private var extractor:MediaExtractor= MediaExtractor()

    @JvmOverloads
    @Throws (IllegalArgumentException::class,IOException::class)
    constructor (context: Context, uri: Uri, track:Int=0,headers: Map<String, String>? =null ){
        if (uri == Uri.EMPTY) throw IllegalArgumentException("Uri is empty")
        this.uri=uri
        extractor.setDataSource(context,uri,headers)
        extractor.selectTrack(track)
        val trackFormat = extractor.getTrackFormat(track)
        extractor.release()
        //документация андроида утверждает что любая
        // дорожка медиафайла имеет этот параметр, но мало ли что нам подсунут
        val mime = trackFormat.getString("mime").toString()
        require(mime.contains("audio",true)&&!mime
            .contains("raw",true)){"Track $track isn't valid audio track"}
        initFields(trackFormat)
        }

    @JvmOverloads
    @Throws (IllegalArgumentException::class,IOException::class)
    constructor (path:String, track: Int =0){
        if (path.isBlank()) throw IllegalArgumentException("Path is null or empty")
        this.path=path
        extractor.setDataSource(path)
        extractor.selectTrack(track)
        val trackFormat = extractor.getTrackFormat(track)
        extractor.release()
        val mime = trackFormat.getString("mime").toString()
        require(mime.contains("audio",true)&&!mime
            .contains("raw",true)){"Track $track  isn't valid audio track"}
        initFields(trackFormat)
    }

    private constructor()

    @Throws (IllegalArgumentException::class)
    private fun initFields (trackFormat:MediaFormat){
        mediaFormat=trackFormat
        /* According to MediaFormat.getTrackFormat(...) documentation any audio tracks have this 4
         * params, so we shouldn't get an exception
         *
         */
        try {
            duration = trackFormat.getLong("durationUs").div(1000)
            samplingRate = trackFormat.getInteger("sample-rate")
            channelsCount = trackFormat.getInteger("channel-count")
            mimeString=trackFormat.getString("mime")
        } catch (e:Exception){
            throw IllegalArgumentException ("Audio file $path $uri don't have valid media data")
        }

    }





    /* Static and async API for class */
    companion object{

        @JvmStatic
        fun  getMediaDataAsync(context: Context, uri: Uri, track:Int=0,
                               headers: Map<String, String>? =null ) =
                        CoroutineScope(Dispatchers.IO)
                            .async{ AudioDataInfo(context,uri,track,headers)}

        @JvmStatic
        fun  getMediaDataAsync(path: String, track:Int=0) = CoroutineScope(Dispatchers.IO)
            .async{ AudioDataInfo(path,track)}

        @JvmStatic
        fun getMediaData(context: Context, uri: Uri, track:Int=0,
                         headers: Map<String, String>? =null ): AudioDataInfo {
            return AudioDataInfo(context,uri,track,headers)
        }

        @JvmStatic
        fun getMediaData(path:String, track:Int=0): AudioDataInfo {
            return AudioDataInfo(path,track)
        }

    }

    override fun toString(): String {
        return "Media format for file $uri = ${mediaFormat.toString()}\n"
    }

}