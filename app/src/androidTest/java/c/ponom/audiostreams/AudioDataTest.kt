package c.ponom.audiostreams

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import c.ponom.audiostreams.audio_streams.AudioDataInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Instrumented test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4::class)
class AudioDataInfoTest {



    private val appContext: Context = getInstrumentation().targetContext
    private val fileList = listOf("test_60sec_440sinewave_m4a",
        "test_60sec_440sinewave_mp3",
        "test_60sec_440sinewave_flac")
    //.flac files can have "audio/flac" or "audio/raw" mime string depending on version.
    // For emulator with sdk<31 = raw, for Samsung sdk 32 = flac
    private val mimeList = listOf("audio/mp4a-latm", "audio/mpeg","audio/flac")
    private val illegalUri =Uri.parse("illegal uri")


    @Test
    fun checkAudioInfo() {
        fileList.forEachIndexed {
                index, _ ->testAudioInfoSoundStream(fileList[index],mimeList[index])
        }
        testForIllegalUri()
        testAsyncApi(Uri.EMPTY)
        testAsyncApi(Uri.parse("illegal uri"))
}

    private fun testForIllegalUri() {
        try {
            AudioDataInfo(appContext, Uri.parse("invalid uri string"))
            throw RuntimeException("Must throw IOException ")
        } catch (e: Exception) {
            e.printStackTrace()
            assertEquals(e.javaClass, IOException::class.java)
        }


        try {
            AudioDataInfo(appContext, Uri.EMPTY)
            throw RuntimeException("Must throw IllegalArgumentException ")
        } catch (e: Exception) {
            e.printStackTrace()
            assertEquals(e.javaClass, IllegalArgumentException::class.java)
        }
    }


    private fun testAudioInfoSoundStream(name: String, targetMime: String) {
        val soundId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        val uri=Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                appContext.packageName + "/$soundId")
        assertNotEquals(soundId,0)
        val audioData = AudioDataInfo(appContext,uri)
        println("AUDIO DATA FOR FILE:$audioData")
        val duration = audioData.duration
        val sampleRate = audioData.sampleRate
        val channels= audioData.channelsCount
        val mime = audioData.mimeString

        assertEquals((duration / 1000.0).roundToInt(),60)
        assertEquals(channels,1)
        assertEquals(sampleRate,44100)
        assertEquals(mime,targetMime)

        testAsyncApi(uri)
        testForIllegalTrack(uri)


    }

    private fun testForIllegalTrack(uri: Uri) {
        try {
            AudioDataInfo(appContext, uri, 999)
            throw RuntimeException("Must throw IllegalArgumentException !")
        } catch (e: Exception) {
            assertEquals(e.javaClass, IllegalArgumentException::class.java)
            e.printStackTrace()
        }
    }

    private fun testAsyncApi(uri: Uri) {
        val deferredResult = AudioDataInfo.getMediaDataAsync(appContext, uri)
        runBlocking {
            val result = deferredResult.await()
            val resultOrNull = result.getOrNull()
            if (uri == Uri.EMPTY || uri == illegalUri) {
                assertNull(resultOrNull)
                val exception = result.exceptionOrNull()
                exception?.run {println("TEST ERROR for Uri=$uri - error =$exception")}
            } else {
                assertNotNull(resultOrNull)
                println("TEST Result for Uri=$uri, ${resultOrNull!!}")
                val duration = resultOrNull.duration
                val sampleRate = resultOrNull.sampleRate
                val channels= resultOrNull.channelsCount
                val mime = resultOrNull.mimeString
                assertEquals((duration / 1000.0).roundToInt(),60)
                assertEquals(channels,1)
                assertEquals(sampleRate,44100)
                assertTrue(mime.contains("audio",true))
            }
        }
    }


}