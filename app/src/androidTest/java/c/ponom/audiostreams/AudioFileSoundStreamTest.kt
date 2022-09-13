package c.ponom.audiostreams

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import c.ponom.audiostreams.audio_streams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.audiostreams.audio_streams.AudioFileSoundStream
import c.ponom.audiostreams.audio_streams.SoundVolumeUtils
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
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AudioFileSoundStreamTest {



    private val appContext: Context = getInstrumentation().targetContext
    private val fileList = listOf("test_60sec_440sinewave_m4a", "test_60sec_440sinewave_mp3","test_60sec_440sinewave_flac")

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

        val stream = AudioFileSoundStream(appContext,uri)
        val duration = stream.duration
        val frameSize = stream.frameSize
        val sampleRate = stream.sampleRate
        assertEquals((duration / 1000.0).roundToInt(),60)
        assertEquals(stream.channelsCount,1)

        assertEquals(sampleRate,44100)
        assertEquals(stream.bytesPerSample,2)
        // add stereo to tests
        assertEquals(stream.channelConfig,AudioFormat.CHANNEL_IN_MONO)
        assertEquals(frameSize,2*stream.channelsCount)
        val expectedBytes:Int = (duration / 1000.0 * sampleRate * frameSize).toInt()
        //estimated length no more than +/- 0,2 sec
        assertTrue(abs(stream.bytesRemainingEstimate() - expectedBytes) < sampleRate/0.2)
        assertTrue(abs(stream.totalBytesEstimate() - expectedBytes) < sampleRate/0.2)
        assertEquals(stream.timestamp,0)

        val byteArrayStream =ByteArrayOutputStream(1)
        val bufferArray = ByteArray (8192)

        do {
            val count=stream.read(bufferArray)
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
        assertTrue(stream.timestamp/1000.0>59.9)
        assertTrue(stream.timestamp/1000.0<60.1)
        assertTrue(stream.read(bufferArray)==-1)
        assertTrue(abs(stream.bytesRemainingEstimate() ) <sampleRate/0.1)
        stream.close()
        try{
            stream.read(bufferArray)
            throw RuntimeException("Must throw IOException  after close()")
        }
        catch (e:Exception){
            e.printStackTrace()
            assertEquals(e.javaClass,IOException::class.java)
        }

        val streamTestShorts = AudioFileSoundStream(appContext,uri)
        byteArrayStream.reset()

        do {
            val count=streamTestShorts.read(bufferArray)
            if (count>0) byteArrayStream.write(bufferArray,0,count)
        }while (count>0)

        val readShortsArray= byteToShortArrayLittleEndian(byteArrayStream.toByteArray())
        assertArrayEquals(readShortsArray,shortsArray)
    }


}