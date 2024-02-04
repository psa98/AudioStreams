package c.ponom.audiostreamsdemo

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import c.ponom.audiuostreams.audiostreams.ArrayUtils.byteToShortArrayLittleEndian
import c.ponom.audiuostreams.audiostreams.ArrayUtils.toByteArrayLittleEndian
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
    private val fileList = listOf(
        "test_60sec_440sinewave_m4a", "test_60sec_440sinewave_mp3", "test_60sec_440sinewave_flac"
    )

    @Test
    fun checkAudioStreams() {
        for (file in fileList) {
            openAudioFileSoundStream(file)
        }
    }


    private fun openAudioFileSoundStream(name: String) {
        val soundId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        val uri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + appContext.packageName + "/$soundId"
        )
        assertNotEquals(soundId, 0)

        val audioStream = AudioFileSoundStream(appContext, uri)
        val duration = audioStream.duration
        val frameSize = audioStream.frameSize
        val sampleRate = audioStream.sampleRate

        // todo - add stereo files to tests
        // Stream must have a valid duration (60s), and mono 16 bit sound
        assertEquals((duration / 1000.0).roundToInt(), DURATION_SECONDS)
        assertEquals(audioStream.channelsCount, 1)
        assertEquals(sampleRate, TEST_FILE_SAMPLE_FREQ)
        assertEquals(audioStream.bytesPerSample, 2)
        assertEquals(audioStream.channelConfig, AudioFormat.CHANNEL_IN_MONO)
        assertEquals(frameSize, 2 * audioStream.channelsCount)
        val expectedBytes: Int = (duration / 1000.0 * sampleRate * frameSize).toInt()
        //estimated stream length calculated from number of samples no more than +/- 0,2 sec
        // different from real
        assertTrue(abs(audioStream.bytesRemainingEstimate() - expectedBytes) < sampleRate * 0.2)
        assertTrue(abs(audioStream.totalBytesEstimate() - expectedBytes) < sampleRate * 0.2)


        //stream currently is in start position, 0.000 s playback
        assertEquals(audioStream.timestamp, 0)

        // output byte stream and buffer
        val byteArrayStream = ByteArrayOutputStream(1024)
        val bufferArray = ByteArray(2048)
        // Testing for right bytes count reading from two threads
        val runnable: () -> Unit = {
            repeat(100) {
                val count = audioStream.read(bufferArray)
                if (count > 0) byteArrayStream.write(bufferArray, 0, count)
            }
        }
        Thread(runnable).start()
        do {
            val count = audioStream.read(bufferArray)
            if (count > 0) byteArrayStream.write(bufferArray, 0, count)
        } while (count > 0)

        //output byte array with samples (little-endian 16 bit mono)
        val byteArray = byteArrayStream.toByteArray()

        assertTrue(abs(byteArray.size - expectedBytes) < sampleRate / 0.2)

        // test that samples data sine waveforms were successfully loaded,
        // converted to Short samples and measured
        val shortsArray = byteToShortArrayLittleEndian(byteArray)
        val volume = SoundVolumeUtils.getRMSVolume(shortsArray)
        assertTrue(volume > 4300)
        assertTrue(volume < 4800)
        val maxVol = SoundVolumeUtils.getMaxVolume(shortsArray)
        assertTrue(maxVol > 6200)
        assertTrue(maxVol < 6800)

        //stream currently is in end, ~ duration second playback position
        assertEquals((audioStream.timestamp / 1000.0).roundToInt(), DURATION_SECONDS)
        //repeating reading after EOS must return -1
        assertTrue(audioStream.read(bufferArray) == -1)
        //stream currently is in end position, estimated rest of unplayed
        // bytes must be zero or close to zero
        assertTrue(abs(audioStream.bytesRemainingEstimate()) < sampleRate / 0.1)
        audioStream.close()
        try {
            //repeating reading after close() must throw exception, not return -1
            audioStream.read(bufferArray)
            throw RuntimeException("Must throw IOException  after close()")
        } catch (e: Exception) {
            e.printStackTrace()
            assertEquals(e.javaClass, IOException::class.java)
        }

        //reopening stream and reread it to byteArrayStream with read and readShorts -
        // should get same samples
        var streamTestShorts = AudioFileSoundStream(appContext, uri)
        byteArrayStream.reset()
        bufferArray.fill(0)
        do {
            val count = streamTestShorts.read(bufferArray)
            if (count > 0) byteArrayStream.write(bufferArray, 0, count)
        } while (count >= 0)
        val testByteArray1 = byteArrayStream.toByteArray()
        byteArrayStream.reset()
        streamTestShorts = AudioFileSoundStream(appContext, uri)
        val testShortsBuffer = ShortArray(1024)
        do {
            val count = streamTestShorts.readShorts(testShortsBuffer)
            if (count > 0) byteArrayStream.write(
                testShortsBuffer.toByteArrayLittleEndian(), 0, count * 2
            )
        } while (count >= 0)
        val testByteArray2 = byteArrayStream.toByteArray()
        assertArrayEquals(testByteArray1, testByteArray2)
    }


}