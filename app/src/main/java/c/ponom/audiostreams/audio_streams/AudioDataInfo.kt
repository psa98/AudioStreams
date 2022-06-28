@file:Suppress("unused")

package c.ponom.audiostreams.audio_streams

/*todo - по классу
 *  - переделать всю обработку исключений на их возврат
 *  - временно убрать код работающий по uri, но сделать работающий
 * по fd (c него можно длину файла получить)
 * убрать дату-время
 *  - не забыть все файлы и ресурсы позакрывать
  *
  *
  *
  * todo по всем - @JvmOverloads, по статикам - @JvmStatic
 */


import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

@Suppress("FoldInitializerAndIfToElvis")
class AudioDataInfo{
    var uri: Uri=Uri.EMPTY
    private set
    var mimeString: String?=""
        private set
    var hasInfo=false
        private set
    var duration:Long?=0L
        private set
    var samplingRate:Int? =0
        private set
    var channelsCount:Int? =0
        private set
    var durationString:String=""
        private set
    var fileSize:Long?=null
        private set
    var fileDate:Long?=null
        private set
    var fileSizeText:String=""
        private set
    var mediaFormat:MediaFormat?=null
        private set
    var error:Exception? = null
    var errorMessage:String=""
    private var extractor:MediaExtractor= MediaExtractor()

    @JvmOverloads
    @Throws (IllegalArgumentException::class)
    constructor (context: Context, uri: Uri, track:Int=0,headers: Map<String, String>? =null ){
        if (uri == Uri.EMPTY) throw IllegalArgumentException("Path is empty")
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context,uri,headers)
            this.uri=uri
            extractor.selectTrack(track)
            val trackFormat = extractor.getTrackFormat(track)
            //документация андроида утверждает что любая
            // дорожка медиафайла имеет этот параметр
            val mime = trackFormat.getString("mime").toString()
            require(mime.contains("audio",true)&&!mime
                .contains("raw",true)){"Track isn't valid audio track"}
            initFields(trackFormat)
        } catch (e:IOException) {
                // перейти к "всегда эксепшн" или "всегда ошибка и null"
                error=e
                errorMessage=e.message.toString()
                e.printStackTrace()
                hasInfo=false
                return
        }
        // это работает для иерархических путей внутри файловой системы
        val path=uri.encodedPath
        // todo - а оно вообще у меня с content: и file: файлами работает нормально?
        if (path==null) return
        if (uri.isRelative) {
            /* todo  вопрос сюда и для библиотеки -
            *   я сделал функцию перегоняющую контент пути в файловые
            *   1. можно брать от того файла если дадут размер и дату файла
            *   2. на старших апи проще брать от контент провайдера файл, и брать оттуда
            *   3. можно добавить поле "реальный путь" или дать функцию в состав либы (отлаженную)
            *   4. все что работет через контент провайдер надо сделать suspend, оно медленное  */
            try {
                val file = File(path)
                fileSize = file.length()
                fileSizeText = fileSizeToText(fileSize)
                fileDate = file.lastModified()
            } catch (e: IOException) {
                error=e
                errorMessage = e.message.toString()
                e.printStackTrace()}
        }
        else {
            try {
                val s = context.contentResolver.openInputStream(uri) as FileInputStream?
                fileSize=s?.channel?.size()?:0
                fileSizeText = fileSizeToText(fileSize)
                //todo - либа передеать это под медиастор, но он дико медленный
                // на старших версиях можно вместо потока открывать сам файл, это даст доступ к данным
                }
                catch (e: FileNotFoundException){
                e.printStackTrace()
                }
            }
        }

    @JvmOverloads
    @Throws (IllegalArgumentException::class)
    constructor (path:String, track: Int =0){
        if (path.isBlank()) throw IllegalArgumentException("Path is null or empty")
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(path)
            extractor.selectTrack(track)
            val trackFormat = extractor.getTrackFormat(track)
            val mime = trackFormat.getString("mime").toString()
            require(mime.contains("audio",true)&&!mime
                .contains("raw",true)){"Track isn't valid audio track"}
        initFields(trackFormat)
        } catch (e:IOException) {
            e.printStackTrace()
            error=e
            errorMessage=e.message.toString()
            hasInfo=false
        }

        try {
                //todo - протестить. Теоретически это должно быть для честных путей, не file:
                // - для тех выше метод с uri
            val file = File(path)
            uri=file.toUri()
            fileSize=file.length()
            fileDate = file.lastModified()
            fileSizeText=fileSizeToText(fileSize)
        }catch (e:IOException){
            errorMessage=e.message.toString()
            e.printStackTrace()
        }
    }

    private constructor()

    private fun initFields (trackFormat:MediaFormat){
        mediaFormat=trackFormat
        /* According to MediaFormat.getTrackFormat(...) documentation any audio tracks have this 4
         * params, so we shouldn't get an exception
         */
        duration = trackFormat.getLong("durationUs").div(1000)
        samplingRate = trackFormat.getInteger("sample-rate")
        channelsCount = trackFormat.getInteger("channel-count")
        mimeString=trackFormat.getString("mime")
        durationString=timeConversion(duration)
        hasInfo=true
        extractor.release()
    }




    private fun timeConversion(value: Long?): String {
        if (value == null) return ""
        val audioTime: String
        val dur = value.toInt()
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

    /* Static and async API for class */
    companion object{

        // todo - специально для понимания как работают исключения из корутин - бросить оттуда
        fun  getMediaDataAsync(context: Context, uri: Uri, track:Int=0,
                               headers: Map<String, String>? =null ) =
                        CoroutineScope(Dispatchers.IO)
                            .async{ AudioDataInfo(context,uri,track,headers)}

        fun  getMediaDataAsync(path: String, track:Int=0) = CoroutineScope(Dispatchers.IO)
            .async{ AudioDataInfo(path,track)}

        fun getMediaData(context: Context, uri: Uri, track:Int=0,
                         headers: Map<String, String>? =null ): AudioDataInfo {
            return AudioDataInfo(context,uri,track,headers)
        }

        fun getMediaData(path:String, track:Int=0): AudioDataInfo {
            return AudioDataInfo(path,track)
        }

    }

    override fun toString(): String {
        if (hasInfo) return "Media format for file $uri = ${mediaFormat.toString()}\n" +
                "status for other file data $errorMessage"
        return "Media format for file $uri not available, possible error =$errorMessage"
    }


    private fun fileSizeToText(fileSize: Long?): String {
        if (fileSize==null||fileSize==0L)
            return ""
        if (fileSize<1024)
            return "${fileSize}b"
        if (fileSize<1024*1024)
            return String.format(Locale.US,"%1.1f",fileSize/ 1024f) + "Kb"
        if (fileSize<1024*1024*1024)
            return String.format(Locale.US,"%1.1f",fileSize/ (1024 * 1024f)) + "Mb"
        return String.format(Locale.US,"%1.1f",fileSize / (1024 * 1024 * 1024f)) + "Gb"
    }
}