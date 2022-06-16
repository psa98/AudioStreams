package c.ponom.audiostreams

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat.*
import android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import c.ponom.audiostreams.audio_streams.*
import c.ponom.audiostreams.audio_streams.ArrayUtils.shortToByteArrayLittleEndian
import c.ponom.audiostreams.audio_streams.Mp3OutputAudioStream.EncodingQuality
import c.ponom.audiostreams.audio_streams.SoundProcessingUtils.getRMSVolume
import c.ponom.audiostreams.audio_streams.StreamPump.State.PUMPING
import c.ponom.audiostreams.databinding.ActivityMainBinding
import c.ponom.recorder2.audio_streams.AudioFileSoundSource
import c.ponom.recorder2.audio_streams.TAG
import c.ponom.recorder2.audio_streams.TestSoundInputStream
import com.google.android.material.snackbar.Snackbar
import com.naman14.androidlame.LameBuilder
import com.naman14.androidlame.LameBuilder.Mode.MONO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.System.currentTimeMillis

private const val ASKING_FOR_FILE=2
private const val PERMISSION_REQUEST_CODE: Int =1

@Suppress("LocalVariableName", "PropertyName","Deprecation")
class MainActivity : AppCompatActivity() {


    private var mainPump: StreamPump?=null
    private var monitorPump: StreamPump?=null
    private var recordingIsOn: Boolean=false
    private var permissionGranted: Boolean=false
    private lateinit var stopButton: Button
    private lateinit var volume:TextView
    private lateinit var binding: ActivityMainBinding
    private var microphoneStream: MicSoundInputStream = MicSoundInputStream(16000)
    private var lastVolumeTimestamp = 0L
    val meteringFreq = 300

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermission()
        stopButton=binding.stopPlayer
        volume=binding.volume
        stopButton.isEnabled=false
        Log.e("TAG", microphoneStream.isBluetoothSCOMicAvailable(this).toString())
        microphoneStream.getInputDeviceList(this).forEach {
            Log.e("Type=", it.type.toString()) }
        val preferred = microphoneStream.getPreferredDevice().toString()
        Log.e("TAG",  preferred)
        binding.micData.text=preferred
        binding.micData.text=  "BT mic present="+
        microphoneStream.isBluetoothSCOMicAvailable(this).toString()

    }

    private fun checkPermission() {
        if (!isPermissionGranted()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSION_REQUEST_CODE
            )
        }
        updateUi()
    }


    private fun isPermissionGranted(): Boolean {
        val recordPermission =
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO
            ) == PermissionChecker.PERMISSION_GRANTED
        val writeExternalStoragePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PermissionChecker.PERMISSION_GRANTED
        permissionGranted= recordPermission && writeExternalStoragePermission
        return recordPermission && writeExternalStoragePermission
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,
                                            grantResults: IntArray) { //isPermissionGranted()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PermissionChecker.PERMISSION_GRANTED ||
            grantResults[1] != PermissionChecker.PERMISSION_GRANTED
        ) { Snackbar.make(binding.root,"Permissions needed",Snackbar.LENGTH_LONG).show()
        } else permissionGranted =true //это должно блочить ui, проверить ниже
        updateUi()
    }

    private fun updateUi() {
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == ASKING_FOR_FILE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            val path = uri?.encodedPath
            if (path.isNullOrBlank()) return // добавить тосты
            val name = uri.lastPathSegment.toString().substringAfterLast("/")
            // грубоватый способ, надо разобраться со структурой контент имен, там last segment мрак
            Log.e(TAG, "onActivityResult: path $path, name $name, uri $uri")

            playUri(uri)

        }
        super.onActivityResult(requestCode, resultCode, data)
    }




    private fun playUri(uri: Uri){
        playing=!playing
        if (!playing)return //повторный тык выключает

        if (uri == Uri.EMPTY) return
        val fd =this.contentResolver.openAssetFileDescriptor(uri,"r")
        Log.e(TAG, "play uri:  fd len ${fd?.length}")

        //val assetStream =this.contentResolver.openTypedAssetFileDescriptor()
        // Это вроде дает вариант открыть файл с допданными из контент провайлдера для медиа
        // - надо разбраться

        val audioIn =AudioFileSoundSource().getStream(this,uri)
        audioIn.onReadCallback = {pos-> Log.e(TAG, "playUri: pos=$pos")}
        val audioOut= AudioTrackOutputSteam(audioIn.sampleRate,audioIn.channelsCount,
            audioIn.encoding,0)
        Log.e(TAG, "playUri: ="+audioIn.mediaFormat.toString())
        playing=true
        stopButton.isEnabled=true
        CoroutineScope(IO).launch{
            audioOut.play()
            //стартовать следует: либо заранее, будутчи готовым напихать туда секунду-другую
            // звука в буфер и далее подавать с достаточным темпом,
            //либо уже уже подав звук в буфер до заполнения, тогда по play()
            // начнется его проигрывание
            val samplesArray = ShortArray(10000)
            //val byteArray = ByteArray(4096*16)

            do {
            try {
                if (!playing) break
                val samples = audioIn.readShorts(samplesArray)

                //val bytes = audioIn.read(byteArray)
                //if (bytes > 0) audioOut.writeShorts(byteToShortArrayLittleEndian(byteArray),0, bytes/2)

                if (samples > 0) audioOut.writeShorts(samplesArray)
                else break
            } catch (e:Exception){
                e.printStackTrace()
             break
            }

            }
            while(true)
            playing=false
            audioOut.close()
            audioIn.close()
            launch(CoroutineScope(Main).coroutineContext){
                stopButton.isEnabled=false
            }
        }
    }


    fun stopPlayer(view: View){
        playing=false
    }

    fun playExternalFile(view: View) {
        val intent = Intent()
        intent.apply {
            action = Intent.ACTION_OPEN_DOCUMENT
            type = "audio/*"
        }
        startActivityForResult(intent, ASKING_FOR_FILE)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun testMic() {
        if (!microphoneStream.isReady) microphoneStream= MicSoundInputStream(16000)
        val askingThread = Thread.currentThread()
        askingThread.interrupt()
        //val monitoredStream=MonitoredAudioInputStream(microphoneStream)
        recordingIsOn=true
        var prevVol:Short =0
        //val mp3Encoder=Mp3Encoder(16000,32,LameBuilder.Mode.MONO,1)
        val micData = binding.micData
        microphoneStream.startRecordingSession()
        val bufferSize =microphoneStream.currentBufferSize()
        val soundRawData = ShortArray(bufferSize)
            do {
                val bytes = microphoneStream.readShorts(soundRawData)
                if (bytes >= 0) {
                    withContext(Main) {
                        run {
                            val timeNow = currentTimeMillis()
                            if (timeNow - lastVolumeTimestamp > meteringFreq) {
                                lastVolumeTimestamp = timeNow
                                val vol =
                                    (getRMSVolume(soundRawData) + prevVol) / 2
                                micData.text = "$vol"
                                Log.e("Vol=", "$vol, time=${microphoneStream.timestamp/1000}")
                                prevVol = vol.toShort()
                            }
                        }
                    }
                }
            } while (bytes>=0&&recordingIsOn)
            withContext(Main){micData.text="0"}
            microphoneStream.stopRecordingSession()
            microphoneStream.close()
    }


    fun stopMic(view: View) {
        recordingIsOn=false
    }
    fun startMic(view: View) {
        CoroutineScope(IO).launch{
            testMic()
        }

    }


    fun playSoundStereo(view: View) {
        val sampleRate = 48000
        val testSoundInputStream=TestSoundInputStream(440.0,480.0,
            8000,7001,sampleRate, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT)
        val data=ShortArray(sampleRate*10*2) //10 секунд, 2 байта, 2 канала
        // тут тестируется передача и отправка данных в байтах, не в shorts
        testSoundInputStream.readShorts(data)

        CoroutineScope(IO).launch{
            val outChannel  = AudioTrackOutputSteam(sampleRate,
                2, ENCODING_PCM_16BIT,500)
            outChannel.play()
            outChannel.writeShorts(data)
            outChannel.close()
        }
    }

    private var playing =false


    fun playSoundMono(view: View) {
        val sampleRate = 44100
        val testSoundInputStream=TestSoundInputStream(440.0,10000,
            sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        val data=ShortArray(sampleRate*10)//10 секунд, 1 16-битовый отчет , 1 канал
        testSoundInputStream.readShorts(data)
        // тут тестируется передача и отправка данных в shorts
        CoroutineScope(IO).launch{
            val outChannel  = AudioTrackOutputSteam(sampleRate,
                1,ENCODING_PCM_16BIT,500)
            outChannel.play()
            outChannel.writeShorts(data)
            outChannel.close()
        }
    }
    @Suppress("LocalVariableName", "PropertyName", "PrivatePropertyName")
    private val  MP3outBitrate = 128

    private val sampleRate = 24000


        fun makeMp3(view: View) {
        Log.e(TAG, "makeMp3: =start")
        val stereoSamplesStream = TestSoundInputStream(400.0,440.0,
            7000, 12000 , sampleRate, CHANNEL_IN_STEREO,
            ENCODING_PCM_16BIT)
        val monoSamplesStream = TestSoundInputStream(430.0, 8000,
            sampleRate, CHANNEL_IN_MONO,ENCODING_PCM_16BIT)
        val samplesCount = 48000//15 seconds
        val monoSamples =ShortArray(samplesCount *10)
        val stereoSamples =ShortArray(samplesCount *10*2)
        monoSamplesStream.readShorts(monoSamples)
        stereoSamplesStream.readShorts(stereoSamples)
        Log.e(TAG, "makeMp3: =generated")
        val outDirName= getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString()
        val outDir = File("$outDirName/AudioStreams/")
        outDir.mkdir()
            CoroutineScope(IO).launch {
                testMp3ShortsWrite(outDir, monoSamples, stereoSamples)
                testBytesMP3Write(outDir, monoSamples, stereoSamples)
            }
        stereoSamplesStream.close()
        monoSamplesStream.close()

        Log.e(TAG, "makeMp3: = end")
        }


    private fun testMp3ShortsWrite(outDir: File, monoSamples: ShortArray, stereoSamples: ShortArray) {
        val outputFileMono = File(outDir, "/TestMono.mp3")
        val outputFileMonoStream = outputFileMono.outputStream()

        val outputFileStereo = File(outDir, "/TestStereo.mp3")
        val outputFileStereoStream = outputFileStereo.outputStream()

        val mp3MonoWriter = Mp3OutputAudioStream(
            outputFileMonoStream,
            sampleRate, MP3outBitrate, MONO
        )

        Log.e(TAG, "makeMp3: =start mono")
        mp3MonoWriter.writeShorts(monoSamples)
        mp3MonoWriter.close()
        outputFileMonoStream.close()
        Log.e(TAG, "makeMp3: =monoSaved")


        Log.e(TAG, "makeMp3: =stereo start")
        val mp3StereoWriter = Mp3OutputAudioStream(
            outputFileStereoStream,
            sampleRate, MP3outBitrate, LameBuilder.Mode.STEREO
        )
        mp3StereoWriter.writeShorts(stereoSamples)
        mp3StereoWriter.close()
        outputFileStereoStream.close()
        Log.e(TAG, "makeMp3: =stereoSaved")
        runMediaScanner(arrayOf(outputFileMono.toString(), outputFileStereo.toString()))
    }

    private fun testBytesMP3Write(
        outDir: File,
        monoSamples: ShortArray,
        stereoSamples: ShortArray
    ) {
        val outputMonoBytesTest = File(outDir, "/TestMonoByteWrite.mp3").outputStream()
        val outputStereoSoundBytesTest = File(outDir, "/TestStereoBytesWrite.mp3").outputStream()

        val mp3MonoWriterBytes = Mp3OutputAudioStream(
            outputMonoBytesTest,
            sampleRate, MP3outBitrate, MONO,EncodingQuality.HIGH_AND_SLOW
        )
        Log.e(TAG, "makeMp3: =start mono")
        mp3MonoWriterBytes.write(shortToByteArrayLittleEndian(monoSamples))
        mp3MonoWriterBytes.close()
        outputMonoBytesTest.close()
        Log.e(TAG, "makeMp3: =monoSaved")
        Log.e(TAG, "makeMp3: =stereo start")
        val mp3StereoWriterBytes = Mp3OutputAudioStream(
            outputStereoSoundBytesTest,
            sampleRate, MP3outBitrate, LameBuilder.Mode.STEREO, EncodingQuality.FAST_ENCODING
        )
        mp3StereoWriterBytes.write(shortToByteArrayLittleEndian(stereoSamples))
        mp3StereoWriterBytes.close()
        Log.e(TAG, "makeMp3: =stereoSaved")
        outputStereoSoundBytesTest.close()
    }


    private fun runMediaScanner(filePaths: Array<String>) {
        MediaScannerConnection.scanFile(this,filePaths, null
        ) { path, uri ->
            Log.e("ExternalStorage", "Scanned $path:")
            Log.e("ExternalStorage", "-> uri=$uri")
        }
    }

    var testMicStream:MicSoundInputStream?=null
    var recordingMic=false

    fun micToFile(view: View) {
        recordingMic=true
        val shortBuffer=ShortArray(4096)
        val outDirName= getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val outDir = File("$outDirName/AudioStreams/")
        val outputFileMp3 = File(outDir, "/TestMicStream.mp3")

        val outputFileStream = outputFileMp3.outputStream()
        val testMicStream=MicSoundInputStream(32000, VOICE_RECOGNITION)
        testMicStream.startRecordingSession()
        val encoderStream=Mp3OutputAudioStream(outputFileStream,
            32000,64, MONO)

          CoroutineScope(IO).launch {
            do {
                if (!recordingMic){
                    testMicStream.close()
                    encoderStream.close()
                    break
                }
                var read = 0
                try {
                    read = testMicStream.readShorts(shortBuffer)
                        //.. тут есть смелое допущение что поток успеет записать до
                    // следующего потока данных с микрофона. в идеале все же нужен буфер
                    encoderStream.writeShorts(shortBuffer)
                val timeNow = currentTimeMillis()
                if (timeNow - lastVolumeTimestamp > meteringFreq) {
                    lastVolumeTimestamp = timeNow
                    withContext(Main) {
                        val vol = (getRMSVolume(shortBuffer))
                        volume.text = "$vol"
                        Log.e("Vol=", "$vol, time=${testMicStream.timestamp / 1000.0}")
                        }
                    }
                } catch (e: Exception) {
                    recordingMic=false
                    // todo - добавать трайкэтч на закрытие обоих потоков, его отдельно надо
                    e.printStackTrace()
                    break
                }
            } while (read >= 0&&recordingMic)
            testMicStream.close()
            encoderStream.close()
            recordingMic =false
        }
    }

    fun stopRecording(view: View) {
        recordingMic=false
        if (mainPump?.state== PUMPING) mainPump?.stop()
        if (monitorPump?.state== PUMPING) monitorPump?.stop()

    }



    fun monitoredRecord(view: View) {
        recordingMic=true
        val outDirName= getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val outDir = File("$outDirName/AudioStreams/")
        val outputFileMp3 = File(outDir, "/TestMicStreamM.mp3")
        val outputFileStream = outputFileMp3.outputStream()
        val testMicStream=MicSoundInputStream(32000, VOICE_RECOGNITION)
        testMicStream.startRecordingSession()
        val monitoredStream=MonitoredAudioInputStream(testMicStream)
        val monitor: MonitoredAudioInputStream =monitoredStream.monitoringStream

        val encoderStream=Mp3OutputAudioStream(outputFileStream,
            32000,32, MONO)
        mainPump =StreamPump(
            encoderStream,
            monitoredStream,
            {
                Log.e(TAG, "monitoredRecord: end")
            },
            {
                Log.e(TAG, "monitoredRecord: fatal error")
            }
        )
//        mainPump?.onWrite={ bytesWritten ->  Log.e(TAG, "monitoredRecord: = "+bytesWritten)}
        mainPump?.start()
        val audioTrackMonitor=AudioTrackOutputSteam(32000,1)
        Thread.sleep(20)
        audioTrackMonitor.play()

        //TODO - сделать в рекордере  (1) монитор при условии подключения любых наушников
        // (2) - я уверен что рано или поздно при реализации монитора расползется звук,
        // поэтому по уму когда будут делать в рекордере  надо сравнивать байты выданные
        // туда и туда и при расхождении больше заданного немного начинать выкусывать отсчеты.
        // Но это уже для платных версий
        // о (3) обязательно надо отрубать монитор при отключении наушников, иначе будет
        // ужас ужас


        monitorPump = StreamPump(audioTrackMonitor,monitor,
            { Log.e(TAG, "monitoredRecord: in monitor  =end")},
            {e->Log.e(TAG,"monitoredRecord: in monitor ="+e.localizedMessage)})
            //monitorPump?.onWrite={bytes-> Log.e(TAG, "monitoredRecord: in monitor  $bytes")}
        monitorPump?.start()
    }


/*todo
   разобраться с размером буфера для  вывода звука, подсократить буфер у файла
   (вероятно надо секунд -надцать сделать)
   наладить и протестировать пересчет таймстемпов всех классов
   писать классы  на базе  - мониторный, приеобразование в байты и обратно,
   автоперегоняющий.
   писать энкодер.
   для всех выводных потоков - метод отдающий ему входной поток и команду играть до его завершения
   или команды стоп.
   для НЕКОТОРЫХ классов которые попроще - написать поддержку 8 битного звука и пометить их отдельно.
   для всех остальных добавть возможность отдавать  readShorts.

   ! временно это на холде кроме всего что не требуется для выкладки оснвоного приложения.

   Для него требуется потенциально:
   Микрофоннный вводной поток ПЛЮС мониторный поток с буферизацией
   либо приделка выводного потока с буферизацией в основную записывалку, со кольцевым буфером.



 */
}