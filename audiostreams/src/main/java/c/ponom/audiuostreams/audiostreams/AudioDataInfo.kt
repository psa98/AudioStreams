@file:Suppress("unused")

package c.ponom.audiuostreams.audiostreams

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import java.io.IOException

/**
 * @author Sergey Ponomarev,2022, 461300@mail.ru
 * MIT licence
 */


class AudioDataInfo{
    private var uri: Uri=Uri.EMPTY
    private var path: String=""
    var mimeString: String=""
        private set
    var duration:Long=0L
        private set
    var sampleRate:Int =0
        private set
    var channelsCount:Int =0
        private set

    var mediaFormat:MediaFormat= MediaFormat()
        private set
    private var extractor:MediaExtractor= MediaExtractor()

    /**
     * Returns the AudioDataInfo object containing basic info for media properties of media
     * located at uri.
     *
     * @return  the AudioDataInfo object containing basic info for media properties of media
     * located at uri.
     * @throws IllegalArgumentException if the data at uri is not a valid audio source,
     * @throws IOException if file or url is not available
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to extract from.
     *
     * <p>When <code>uri</code> refers to a network file the
     * {@link android.Manifest.permission#INTERNET} permission is required.
     * @param track  the number of audio track in the source.
     * For most audio sources, track #0 contains audio data, for most video
     *  sources, audio tracks start from #1
     * @param headers the headers to be sent together with the request for the data.
     *        This can be {@code null} if no specific headers are to be sent with the
     *        request.
     */
    @JvmOverloads
    @Throws (IllegalArgumentException::class,IOException::class)
    constructor (context: Context, uri: Uri, track:Int=0,headers: Map<String, String>? =null ){
        if (uri == Uri.EMPTY) throw IllegalArgumentException("Uri is empty")
        this.uri=uri
        extractor.setDataSource(context,uri,headers)
        if (track>extractor.trackCount-1||track<0)
            throw IllegalArgumentException("No such track in file")
        extractor.selectTrack(track)
        val trackFormat = extractor.getTrackFormat(track)
        extractor.release()
        //документация андроида утверждает что любая
        // дорожка медиафайла имеет этот параметр, но мало ли что нам подсунут
        val mime = trackFormat.getString("mime").toString()
        require(mime.contains("audio",true)){"Track $track isn't valid audio track"}
        initFields(trackFormat)
        }



    /**
     * Returns the AudioDataInfo object containing basic info for media properties of media
     * located at file-path or http URL.
     *
     * @return  he AudioDataInfo object containing basic info for media properties of media
     * located at file-path or http URL.
     * @throws IllegalArgumentException if the data at path is not a valid audio source,
     * @throws IOException if file or url is not available
     *
     * @param path the path to audio file
     * <p>When <code>uri</code> refers to a network file the
     * {@link android.Manifest.permission#INTERNET} permission is required.
     * @param track  the number of audio track in the source. For most audio sources, track #0
     * contains audio data, for most video sources, audio tracks start from #1
     */
    @JvmOverloads
    @Throws (IllegalArgumentException::class,IOException::class)
    constructor (path:String, track: Int =0){
        if (path.isBlank()) throw IllegalArgumentException("Path is or empty")
        this.path=path
        extractor.setDataSource(path)
        if (track>extractor.trackCount-1||track<0)
            throw IllegalArgumentException("No such track in file")
        extractor.selectTrack(track)
        val trackFormat = extractor.getTrackFormat(track)
        extractor.release()
        val mime = trackFormat.getString("mime").toString()
        require(mime.contains("audio",true)){"Track $track  isn't valid audio track"}
        initFields(trackFormat)
    }

    private constructor()


    @Throws (IllegalArgumentException::class)
    private fun initFields (trackFormat:MediaFormat){
        mediaFormat=trackFormat
        /* According to MediaFormat.getTrackFormat(...) documentation any audio tracks have this 4
         * params, so we shouldn't get an exception
         */
        try {
            duration = trackFormat.getLong("durationUs").div(1000)
            sampleRate = trackFormat.getInteger("sample-rate")
            channelsCount = trackFormat.getInteger("channel-count")
            mimeString=trackFormat.getString("mime")?:""
        } catch (e:Exception){
            throw IllegalArgumentException ("Audio file $path $uri don't have valid media data")
        }
    }








    /* Static and async API for class */
    companion object {

        /**
         * Returns the Result&lt;AudioDataInfo&gt; object containing basic info for media
         * @return the Result&lt;AudioDataInfo&gt; object containing basic info for media
         * properties of media located at uri or Throwable.
         * @throws IllegalArgumentException if the data at uri is not a valid audio source,
         * @throws IOException if file or url is not available
         * @param context the Context to use when resolving the Uri
         * @param uri the Content URI of the data you want to extract from.
         * <p>When <code>uri</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         * @param track the number of audio track in the source. For most audio sources, track #0
         * contains audio data, for most video sources, audio tracks start from #1
         * @param headers the headers to be sent together with the request for the data.
         *        This can be {@code null} if no specific headers are to be sent with the
         *        request.
         */
        @JvmOverloads
        @JvmStatic
        @Suppress("BlockingMethodInNonBlockingContext")
        fun getMediaDataAsync(
            context: Context, uri: Uri, track: Int = 0,
            headers: Map<String, String>? = null): Deferred<Result<AudioDataInfo>> =
            CoroutineScope(IO).async {runCatching { AudioDataInfo(context, uri, track, headers) }}

        /**
         * Returns the Result&lt;AudioDataInfo&gt; object containing basic info for media
         *properties of media located at file-path or http URL.
         *
         * @return  the Result&lt;AudioDataInfo&gt; object containing basic info for media properties
         * of media located at uri file-path or http URL or Throwable
         *
         * @throws IllegalArgumentException if the data at path is not a valid audio source,
         * @throws IOException if file or url is not available
         * @param path the path to audio file. When <code>path</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         * @param track  the number of audio track in the source. For most audio sources, track #0
         * contains audio data, for most video sources, audio tracks start from #1
         */
        @JvmStatic
        fun getMediaDataAsync(path: String, track: Int = 0):  Deferred<Result<AudioDataInfo>> =
            CoroutineScope(IO).async {runCatching{AudioDataInfo(path,track) }}


        /**
         *
         * Returns AudioDataInfo containing basic info for media properties of media
         * located at uri.
         *
         * @return  the AudioDataInfo object containing basic info for media properties of media
         * located at uri.
         * @throws IllegalArgumentException if the data at uri is not a valid audio source,
         * @throws IOException if file or url is not available
         *
         * @param context the Context to use when resolving the Uri
         * @param uri the path to audio file. When <code>uri</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         * @param track  the number of audio track in the source. For most audio sources, track #0
         * contains audio data, for most video sources, audio tracks start from #1
         * @param headers the headers to be sent together with the request for the data.
         *        This can be {@code null} if no specific headers are to be sent with the
         *        request.
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class, IOException::class)
        fun getMediaData(
            context: Context, uri: Uri, track: Int = 0,
            headers: Map<String, String>? = null
        ): AudioDataInfo {
            return AudioDataInfo(context, uri, track, headers)
        }

        /**
         * Returns the AudioDataInfo object containing basic info for media properties
         * of media located at file-path or http URL.
         *
         * @return  the AudioDataInfo object containing basic info for media properties
         * of media located at file-path or http URL.
         * @throws IllegalArgumentException if the data at path is not a valid audio source,
         * @throws IOException if file or url is not available
         *
         * @param path the path to audio file. When <code>path</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         * @param track  the number of audio track in the source. For most audio sources, track 0
         * contains audio data; for most video sources, audio tracks start from #1
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class, IOException::class)
        fun getMediaData(path: String, track: Int = 0): AudioDataInfo {
            return AudioDataInfo(path, track)
        }

        /**
         * Returns the HashMap object containing info for media tracks properties of media
         * @return  the HashMap<Int, AudioDataInfo.AudioTrackData>object containing
         * info for media tracks properties of media located at the path.
         * @throws IllegalArgumentException if the data at path is not a valid audio source,
         * @throws IOException if the file or url is not available.
         * @param path the path to audio file. When <code>path</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class, IOException::class)
        fun getTrackData(path: String): HashMap<Int, AudioTrackData> {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)
            return trackData(extractor)
        }

        /**
         * Returns the HashMap object containing  info for media tracks properties of media
         * @return  the HashMap<Int, AudioDataInfo.AudioTrackData>object containing
         * info for media tracks properties of media located at the path
         * @throws IllegalArgumentException if the data at uri is not a valid audio source,
         * @throws IOException if file or url is not available
         * @param context the Context to use when resolving the Uri
         * @param uri the path to audio file. When <code>uri</code> refers to a network file the
         * {@link android.Manifest.permission#INTERNET} permission is required.
         * @param headers the headers to be sent together with the request for the data.
         *        This can be {@code null} if no specific headers are to be sent with the
         *        request.
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IllegalArgumentException::class, IOException::class)
        fun getTrackData(context: Context, uri: Uri,headers: Map<String,
                String>? = null): HashMap<Int, AudioTrackData> {
            val extractor = MediaExtractor()
            extractor.setDataSource(context,uri,headers)
            return trackData(extractor)
        }

        private fun trackData(extractor: MediaExtractor):HashMap<Int, AudioTrackData>  {
            val tracks = ArrayList<MediaFormat>()
            val audioTracks = HashMap<Int,AudioTrackData>()
            for (i in 0 until extractor.trackCount)
                tracks.add(extractor.getTrackFormat(i))
            for (trackNumber in 0 until tracks.size){
                val trackMediaFormat = tracks[trackNumber]
                val mimeString = trackMediaFormat.getString("mime").toString()
                if(!mimeString.contains("audio",true)) continue
                var trackDuration:Long
                var samplingRate:Int
                var channelsCount:Int
                var lang= ""
                try {
                    trackDuration = trackMediaFormat.getLong("durationUs").div(1000)
                    samplingRate = trackMediaFormat.getInteger("sample-rate")
                    channelsCount = trackMediaFormat.getInteger("channel-count")
                } catch (e:Exception){
                    continue
                }
                try {
                    val langValue = trackMediaFormat.getString("language")
                    if (langValue!=null) lang = langValue
                } catch (e:Exception){
                    e.printStackTrace()
                }
                audioTracks[trackNumber] = AudioTrackData(mimeString,trackDuration,
                    samplingRate,channelsCount,lang)
            }
            extractor.release()
            return audioTracks
        }
    }



    override fun toString(): String {
        return "Media format for source $path $uri = ${mediaFormat}\n"
    }
    data class AudioTrackData (val mimeString: String="",
                               val duration:Long=0L,
                               val samplingRate:Int =0,
                               val channelsCount:Int =0,
                               val language:String="")
}