@file:Suppress("unused")

package c.ponom.audiostreams.audio_streams

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File
import java.io.IOException
import java.util.*

@Suppress("FoldInitializerAndIfToElvis")
open class AudioDataInfo
{
    var uri: Uri=Uri.EMPTY
    var mimeString: String?=""
    var hasInfo=false
    var duration:Long?=0L
    var samplingRate:Int? =0
    var channelsCount:Int? =0
    var durationString:String=""
    var fileSize:Long?=null
    var fileDate:Long?=null
    var fileSizeText:String=""
    var mediaFormat:MediaFormat?=null
    var errorMessage:String="Ok"
    private var extractor:MediaExtractor= MediaExtractor()

    private fun initFields (trackFormat:MediaFormat){

            mediaFormat=trackFormat
            duration = trackFormat.getLong("durationUs").div(1000)
            samplingRate = trackFormat.getInteger("sample-rate")
            channelsCount = trackFormat.getInteger("channel-count")
            mimeString=trackFormat.getString("mime")
            durationString=timeConversion(duration)
            hasInfo=true
            extractor.release()
    }
    //todo обратить внимание на синхронизацию методов в классах либы.
    // Нельзя упустить ни один один тэг
    @Throws (IllegalArgumentException::class)
    constructor (context: Context, uri: Uri?){
        if (uri==null|| uri == Uri.EMPTY) throw IllegalArgumentException("Path is null or empty")
        try {
      /* todo  вопрос сюда и для библиотеки -
      *   я сделал функцию перегоняющую контент пути в файловые
      *   1. можно брать от того файла если дадут размер и дату файла
      *   2. на старших апи проще брать от контент провайдера файл, и брать оттуда
      *   3. можно добавить поле "реальный путь" или дать функцию в состав либы (отлаженную)
      *   4. все что работет через контент провайдер надо сделать suspend, оно медленное
      *   */

            val extractor = MediaExtractor()
            extractor.setDataSource(context,uri,null)
            this.uri=uri
            extractor.selectTrack(0)
            val trackFormat = extractor.getTrackFormat(0)
            //документация андроида утверждает что любая
            // дорожка медиафайла имеет этот параметр
            val mime = trackFormat
                .getString("mime").toString()
            require(mime.contains("audio",true)&&!mime
                .contains("raw",true)){"Track 0 isn't valid audio track"}
            initFields(trackFormat)
        } catch (e:IOException) {
                    errorMessage=e.message.toString()
                    e.printStackTrace()
                    hasInfo=false
                    return
                }
        val path=uri.encodedPath //тodo - посмотреть разницу м-ду encodedPath и path
        // todo - а оно вообще у меня с content: и file: файлами работает нормально?
        if (path==null) return
        try {
            val file = File(path)
            /* todo - переделать - у content: это не отдает данные, там другие методы
            длину из контент файла можно добыть через contentResolver.openAssetFile().length вероятно,
            но это медленно и лучше не надо
            contentResolver.openAssetFile().
             */
            fileSize= file.length()
            fileSizeText=fileSizeToText(fileSize)
            fileDate = file.lastModified()
        }
        catch (e: IOException) {
            errorMessage=e.message.toString()
            e.printStackTrace()
            }
        }

        @Throws (IllegalArgumentException::class)
        constructor (path:String?){
            if (path.isNullOrBlank()) throw IllegalArgumentException("Path is null or empty")
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(path)
                extractor.selectTrack(0)
                val trackFormat = extractor.getTrackFormat(0)
                if (!trackFormat
                        .getString("mime")!!
                        .contains("audio",true)){
                            errorMessage="Track 0 isn't audio track"
                            throw IllegalArgumentException("Track 0 isn't audio track")
                }
            initFields(trackFormat)
        } catch (e:IOException) {
            e.printStackTrace()
            errorMessage=e.message.toString()
            hasInfo=false
        }

        try {
                //аналогично, см. выше. ну или вообще не запрашивать эти данные у content:
                // это в любом случае делать не надо, оно может блочить процесс на секунды, а
                // у file: проверить как это работает
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

    // сделать open метод для возврата языковых значений
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


    private constructor()


    //todo open метод для возврата кастомных разделителей, и
    // использующий стандартные разделители локали по умолчанию. И сунуть его же в утилиты проекта
    open fun timeConversion(value: Long?): String {
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

    companion object{

        fun  getMediaFormatDataAsync(context:Context, uri:Uri) = CoroutineScope(Dispatchers.IO)
            .async{ AudioDataInfo(context,uri)}

        fun  getMediaFormatDataAsync(path: String) = CoroutineScope(Dispatchers.IO)
            .async{ AudioDataInfo(path)}

        fun getMediaFormatData(context:Context, uri:Uri): AudioDataInfo {
            return AudioDataInfo(context,uri)
        }

        fun getMediaFormatData(path:String): AudioDataInfo {
            return AudioDataInfo(path)
        }

    }

    override fun toString(): String {
        if (hasInfo) return "Audio media format for file $uri = ${mediaFormat.toString()}\n" +
                "status for other file data $errorMessage"
        return "Audio media format for file $uri not available, possible error =$errorMessage"
    }
}