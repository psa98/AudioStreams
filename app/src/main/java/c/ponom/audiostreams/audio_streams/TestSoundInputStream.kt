

@file:Suppress("unused")

package c.ponom.recorder2.audio_streams

import android.media.AudioFormat.*
import android.util.Log
import androidx.annotation.IntRange
import c.ponom.audiostreams.audio_streams.ShortArrayUtils
import c.ponom.recorder2.audio_streams.TestSoundInputStream.TestSignalType.MONO
import c.ponom.recorder2.audio_streams.TestSoundInputStream.TestSignalType.STEREO
import java.io.IOException
import java.lang.Integer.min
import kotlin.math.PI
import kotlin.math.sin

const val TAG = "Test Sound Stream"

class TestSoundInputStream private constructor() : AbstractSoundInputStream()  {


    private lateinit var testMode: TestSignalType
    private var prepared: Boolean=false
    private var closed: Boolean=false
    private lateinit var monoParams: MonoSoundParameters
    private lateinit var stereoParams: StereoSoundParameters


    // надо как то элегантнее - стерео параметры в одну структуру из 4 параметров, моно из двух





    inner class MonoSoundParameters {
        var samplesInPeriodMono: Double
        var periodDurationMono: Double
        val volumeMono: Short
        val testFrequencyMono: Double
        constructor(volumeMono: Short, testFrequencyMono: Double) {
            this.volumeMono = volumeMono
            this.testFrequencyMono = testFrequencyMono
            periodDurationMono=1.0/testFrequencyMono
            samplesInPeriodMono=sampleRate.toDouble()/testFrequencyMono


         }
    }


    inner  class  StereoSoundParameters {
        var periodDurationRight: Double
        var samplesInPeriodRight: Double
        var samplesInPeriodLeft: Double
        var periodDurationLeft: Double
        var testFrequencyLeft: Double
        var testFrequencyRight: Double
        var volumeLeft: Short
        var volumeRight: Short

        constructor(
            testFrequencyLeft: Double,
            testFrequencyRight: Double,
            volumeLeft: Short,
            volumeRight: Short
        ) {
            this.testFrequencyLeft = testFrequencyLeft
            this.testFrequencyRight = testFrequencyRight
            this.volumeLeft = volumeLeft
            this.volumeRight = volumeRight
            periodDurationLeft=1.0/testFrequencyLeft
            samplesInPeriodLeft=sampleRate.toDouble()/testFrequencyLeft*2
            periodDurationRight = 1.0 / testFrequencyRight
            samplesInPeriodRight=sampleRate.toDouble()/testFrequencyRight*2

        }
    }



    /**
     *This constructor usable only for CHANNEL_IN_MONO and encoding ENCODING_PCM_16BIT. Only 16 bit
     * encoding currently supported <BR>
     *Test frequency below 32 or above 16000 Hz can be inaudible. Non standard sampling rate below
     * 16000 or over 48000 can by problematic for testing of media encoders or players"
     */

    @Throws(IllegalArgumentException::class,IOException::class)
    constructor (
        testFrequencyMono: Double, volume: Short,
        @IntRange(from = 8000, to= 48000 )sampleRate: Int,
        @IntRange(from = 1, to= 16)channelConfig: Int,
        @IntRange(from = 1, to= 2) encoding: Int
    ) : this() {
        if (channelConfig!= CHANNEL_IN_MONO)
            throw IllegalArgumentException("This constructor usable only for CHANNEL_IN_MONO")
        if (encoding!=ENCODING_PCM_16BIT)
            throw IllegalArgumentException("Only 16 encoding currently supported")
        // кинуть предупреждение если частоты в неслышимом диапазоне
        if (testFrequencyMono<32||testFrequencyMono>16000||testFrequencyMono>sampleRate/2)
            Log.v(TAG, "Test frequency = $testFrequencyMono Hz, probably inaudible")
        if (sampleRate<16000||sampleRate>48000)
            Log.v(TAG, "Non standard sampling rate of $sampleRate can by problematic for testing" +
                    "of sound encoders or players")
        this.sampleRate = sampleRate
        monoParams=MonoSoundParameters(volume,testFrequencyMono)
        this.testMode= MONO
        channelsCount = 1
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2 else  1
        frameSize=bytesPerSample*channelsCount
        prepared=true
    }


    /**
     *This constructor usable only for CHANNEL_IN_STEREO and encoding ENCODING_PCM_16BIT. Only 16 bit
     * encoding currently supported <BR>
     *Test frequency below 32 or above 16000 Hz can be inaudible. Non standard sampling rate below
     * 16000 or over 48000 can by problematic for testing of media encoders or players"
     */
    @Throws(IllegalArgumentException::class,IOException::class)
    constructor (
        testFrequencyLeft: Double,testFrequencyRight: Double,
        volumeLeft: Short,volumeRight: Short,
        @IntRange(from = 8000, to= 48000 )sampleRate: Int,
        @IntRange(from = 12, to= 12)channelConfig: Int,
        @IntRange(from = 1, to= 2) encoding: Int
    ) : this() {
        if (channelConfig!= CHANNEL_IN_STEREO)
            throw IllegalArgumentException("This constructor usable only for CHANNEL_IN_STEREO")
        if (encoding!=ENCODING_PCM_16BIT)
            throw IllegalArgumentException("Only 16 encoding currently supported")
        // кинуть предупреждение если частоты в неслышимом диапазоне
        if (testFrequencyLeft<32||testFrequencyLeft>16000||testFrequencyLeft>sampleRate/2)
            Log.v(TAG, "Test frequency L = $testFrequencyLeft Hz, probably inaudible")
        if (testFrequencyRight<32||testFrequencyRight>16000||testFrequencyRight>sampleRate/2)
            Log.v(TAG, "Test frequency R = $testFrequencyRight Hz, probably inaudible")
        if (sampleRate<16000||sampleRate>48000)
            Log.v(TAG, "Non standard sampling rate of $sampleRate can by problematic for testing" +
                    "of sound encoders or players")
        this.sampleRate = sampleRate
        stereoParams=StereoSoundParameters(testFrequencyLeft,testFrequencyRight,volumeLeft,volumeRight)
        testMode=STEREO
        channelsCount = 2
        bytesPerSample = if (encoding== ENCODING_PCM_16BIT) 2 else  1
        frameSize=bytesPerSample*channelsCount
        prepared=true
    }








   /**
    * Return -1 when there is no estimated stream length (for example,for endless streams)
    * or estimated rest of bytes in stream
    */
   override fun totalBytesEstimate(): Long {
       return -1
   }

    /**
     * Return -1 when there is no estimated stream length (for example,for endless streams)
     * or estimated rest of bytes in stream
     */
   override fun bytesRemainingEstimate(): Long {
       return -1L
   }

    /**
     * смотри описание исходного. Теоретически тут надо сообщитьсколько можно отдать без
     * блокирования из буферов - класс может отдать сколько угодно, но думаю что слишком большим
     * буфером (от 32-64к) некоторые устройства могут подавиться, поэтому отдается разумное число
     */
   override fun available(): Int {
       return 1024*16
   }


    // не тестровано пока
    override fun read(): Int {
        val b=ByteArray(1)
        if (closed) return -1
        return read(b)+128
    }


    @Throws(NullPointerException::class)
    override fun read(b: ByteArray?): Int {
        if (b==null) throw NullPointerException ("Null array passed")
        if (closed) return -1
        return read(b,0,b.size)
    }


    /**
     *
     */

    @Synchronized
    @Throws(NullPointerException::class,IllegalArgumentException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) throw NullPointerException("Null array passed")
        if (off < 0 || len < 0 || len > b.size - off)
            throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
        if (closed) return -1
        val shortArray=ShortArray(min(len/2,b.size/2))
        val bytes= readShorts(shortArray,0,len/2)*2
        ShortArrayUtils.shortToByteArrayLittleEndian(shortArray).copyInto(b)
        bytesSent+=bytes
        onReadCallback?.invoke(bytesSent)
        return bytes
   }




    @Synchronized
    @Throws(NullPointerException::class)
    override fun readShorts(b: ShortArray, off: Int, len: Int): Int {
        if (closed) return -1
        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException("Wrong read(...) params")
        if (len == 0) return 0
        if (off != 0) throw IllegalArgumentException("Non zero offset currently not implemented")
        val length = min(b.size,len)
        val dataArray=ShortArray(length)
        if (testMode== MONO) dataArray.forEachIndexed { index, _ ->
            dataArray[index] = calculateSampleValueMono(index.toLong())
        }
        if (testMode== STEREO) {
            //todo если убрать -2 в конце - будет превышение? или это перестраховка
            for (index in 0..dataArray.size-2 step 2 ){
            val samplesPair= calculateSampleValueStereo(index.toLong())
            dataArray[index] =samplesPair.first
            dataArray[index+1] =samplesPair.second
            }
        }
        dataArray.copyInto(b)
        bytesSent+=len*2
        return len
    }

    @Synchronized
    override fun readShorts(b: ShortArray): Int {
        if (closed) return -1
        return if (b.isEmpty()) 0 else readShorts(b, 0, b.size)
    }

    override fun canReturnShorts():Boolean =true


    @Synchronized
    override fun close() {
       prepared=false
       closed=true
    }



    enum class TestSignalType{
        MONO,
        STEREO;
    }

    private fun calculateSampleValueMono(sampleNum:Long):Short{
        val x =(sampleNum/monoParams.samplesInPeriodMono*2*PI)
        return (sin(x)*monoParams.volumeMono).toInt().toShort()
    }

    private fun calculateSampleValueStereo(sampleNum:Long):Pair<Short,Short>{
        val xLeft =(sampleNum/stereoParams.samplesInPeriodLeft*2*PI)
        val xRight =((sampleNum+1)/stereoParams.samplesInPeriodRight*2*PI)
        val left =(sin(xLeft)*stereoParams.volumeLeft).toInt().toShort()
        val right =(sin(xRight)*stereoParams.volumeRight).toInt().toShort()
        return Pair(left,right)
    }

}


