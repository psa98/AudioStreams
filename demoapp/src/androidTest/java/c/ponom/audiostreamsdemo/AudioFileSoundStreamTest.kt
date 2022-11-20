package c.ponom.audiostreamsdemo

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import c.ponom.audiuostreams.audiostreams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.audiuostreams.audiostreams.AudioFileSoundStream
import c.ponom.audiuostreams.audiostreams.SoundVolumeUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Instrumented test, which will execute on an Android device.
 *
 */

const val DURATION_SECONDS = 60
const val TEST_FILE_SAMPLE_FREQ = 44100

@RunWith(AndroidJUnit4::class)
class AudioFileSoundStreamTest {



    private val appContext: Context = getInstrumentation().targetContext
    private val fileList = listOf("test_60sec_440sinewave_m4a",
        "test_60sec_440sinewave_mp3",
        "test_60sec_440sinewave_flac")

    @Test
    fun checkAudioStreams(){
        for (file in fileList) {
            openAudioFileSoundStream(file)
        }
    }


    private fun openAudioFileSoundStream(name:String) {
        val soundId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        val uri=Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                appContext.packageName + "/$soundId")
        assertNotEquals(soundId,0)

        val audioStream = AudioFileSoundStream(appContext,uri)
        val duration = audioStream.duration
        val frameSize = audioStream.frameSize
        val sampleRate = audioStream.sampleRate

        assertEquals((duration / 1000.0).roundToInt(), DURATION_SECONDS)
        assertEquals(audioStream.channelsCount,1)
        assertEquals(sampleRate, TEST_FILE_SAMPLE_FREQ)
        assertEquals(audioStream.bytesPerSample,2)
        // todo - add stereo files to tests
        assertEquals(audioStream.channelConfig,AudioFormat.CHANNEL_IN_MONO)
        assertEquals(frameSize,2*audioStream.channelsCount)
        val expectedBytes:Int = (duration / 1000.0 * sampleRate * frameSize).toInt()
        //estimated length no more than +/- 0,2 sec different from real
        assertTrue(abs(audioStream.bytesRemainingEstimate() - expectedBytes) < sampleRate*0.2)
        assertTrue(abs(audioStream.totalBytesEstimate() - expectedBytes) < sampleRate*0.2)
        assertEquals(audioStream.timestamp,0)

        val byteArrayStream =ByteArrayOutputStream(1)
        val bufferArray = ByteArray (8192)

        do {
            val count=audioStream.read(bufferArray)
            if (count>0) byteArrayStream.write(bufferArray,0,count)
        }while (count>0)

        val bytesArray=byteArrayStream.toByteArray()
        assertTrue(abs(bytesArray.size - expectedBytes)
                <sampleRate/0.2)
        val shortsArray= byteToShortArrayLittleEndian(bytesArray)
        val volume = SoundVolumeUtils.getRMSVolume(shortsArray)
        assertTrue(volume>4300)
        assertTrue(volume<4800)
        val maxVol = SoundVolumeUtils.getMaxVolume(shortsArray)
        assertTrue(maxVol>6200)
        assertTrue(maxVol<6800)
        assertEquals((audioStream.timestamp / 1000.0).roundToInt(), DURATION_SECONDS)
        assertTrue(audioStream.read(bufferArray)==-1)
        assertTrue(abs(audioStream.bytesRemainingEstimate() ) <sampleRate/0.1)
        audioStream.close()
        try{
            audioStream.read(bufferArray)
            throw RuntimeException("Must throw IOException  after close()")
        }
        catch (e:Exception){
            e.printStackTrace()
            assertEquals(e.javaClass,IOException::class.java)
        }

        val streamTestShorts = AudioFileSoundStream(appContext,uri)
        byteArrayStream.reset()
        //todo - make same readShorts test

        do {
            val count=streamTestShorts.read(bufferArray)
            if (count>0) byteArrayStream.write(bufferArray,0,count)
        }while (count>0)

        val readShortsArray= byteToShortArrayLittleEndian(byteArrayStream.toByteArray())
        assertArrayEquals(readShortsArray,shortsArray)

    }


}