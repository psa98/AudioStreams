package c.ponom.audiostreams

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat.*
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.navigation.ui.AppBarConfiguration
import c.ponom.audiostreams.audio_streams.MicSoundInputStream
import c.ponom.audiostreams.audio_streams.Mp3OutputAudioStream
import c.ponom.audiostreams.audio_streams.ShortArrayUtils.shortToByteArrayLittleEndian
import c.ponom.audiostreams.audio_streams.SoundProcessingUtils.getRMS
import c.ponom.audiostreams.databinding.ActivityMainBinding
import c.ponom.recorder2.audio_streams.AudioFileSoundSource
import c.ponom.recorder2.audio_streams.AudioOutputSteam
import c.ponom.recorder2.audio_streams.TAG
import c.ponom.recorder2.audio_streams.TestSoundInputStream
import com.google.android.material.snackbar.Snackbar
import com.naman14.androidlame.LameBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.System.currentTimeMillis

private const val ASKING_FOR_FILE=2
private const val PERMISSION_REQUEST_CODE: Int =1
class MainActivity : AppCompatActivity() {


    private var recordingIsOn: Boolean=false
    private var permissionGranted: Boolean=false
    private lateinit var stopButton: Button
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    var microphoneStream: MicSoundInputStream = MicSoundInputStream(16000)
    private var lastVolumeTimestamp = 0L

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermission()
        stopButton=binding.stopPlayer
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
            val mediaFileUri: Uri = uri //это начинается с content
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
        val assetStream =this.contentResolver.openInputStream(uri)
        val canonical =this.contentResolver.canonicalize(uri)
        Log.e(TAG, "play uri: canonical: $canonical, fd len ${fd?.length}")

        //val assetStream =this.contentResolver.openTypedAssetFileDescriptor()
        // Это вроде дает вариант открыть файл с допданными из контент провайлдера для медиа
        // - надо разбраться

        val audioIn =AudioFileSoundSource().getStream(this,uri)
        audioIn.onReadCallback = {pos-> Log.e(TAG, "playUri: pos=$pos")}
        val audioOut=AudioOutputSteam(audioIn.sampleRate,audioIn.channelsCount,
            audioIn.encoding,0)
        Log.e(TAG, "playUri: ="+audioIn.mediaFormat.toString())
        playing=true
        stopButton.isEnabled=true
        CoroutineScope(IO).launch{
            audioOut.play()
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
            finally {

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
                    withContext(Dispatchers.Main) {
                        run {
                            val timeNow = currentTimeMillis()
                            val meteringFreq = 300
                            if (timeNow - lastVolumeTimestamp > meteringFreq) {
                                lastVolumeTimestamp = timeNow
                                val vol =
                                    (getRMS(soundRawData) + prevVol) / 2
                                micData.text = "$vol"
                                val dataArr = ByteArray(100)
                                //monitoredStream.read(dataArr)
                                Log.e("Vol=", "$vol, time=${microphoneStream.timestamp}")
                                //val mp3=mp3Encoder.encodeMonoStream(soundRawData)
                                prevVol = vol.toShort()
                            }
                        }
                    }
                }
            } while (bytes>=0&&recordingIsOn)
            withContext(Dispatchers.Main){micData.text="0"}
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
        val data=ShortArray(sampleRate*4*2) //4 секунды, 2 байта, 2 канала
        // тут тестируется передача и отправка данных в байтах, не в shorts
        testSoundInputStream.readShorts(data)
        CoroutineScope(IO).launch{
            val outChannel  =AudioOutputSteam(sampleRate,
                2,ENCODING_PCM_16BIT,500)
            outChannel.play()
            outChannel.writeShorts(data)
            outChannel.close()
        }
    }

    var playing =false


    fun playSoundMono(view: View) {

        val sampleRate = 48000
        val testSoundInputStream=TestSoundInputStream(440.0,10000,
            sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        val data=ShortArray(sampleRate*4)//4 секунды, 1 16-битовый отчет , 1 канал
        testSoundInputStream.readShorts(data)
        // тут тестируется передача и отправка данных в shorts
        CoroutineScope(IO).launch{
            val outChannel  =AudioOutputSteam(sampleRate,
                1,ENCODING_PCM_16BIT,500)
            outChannel.play()
            outChannel.writeShorts(data)
            outChannel.close()
        }
    }
    @Suppress("LocalVariableName")
    val MP3outBitrate = 128
    val sampleRate = 24000

        fun makeMp3(view: View) {
        Log.e(TAG, "makeMp3: =start")

        val stereoSamplesStream = TestSoundInputStream(400.0,440.0,
            7000, 12000, sampleRate, CHANNEL_IN_STEREO,
            ENCODING_PCM_16BIT)
        val monoSamplesStream = TestSoundInputStream(430.0, 8000,
            sampleRate, CHANNEL_IN_MONO,ENCODING_PCM_16BIT)

        val samplesCount = 48000//15 seconds
        val monoSamples =ShortArray(samplesCount *10)
        val stereoSamples =ShortArray(samplesCount *10*2)
        monoSamplesStream.readShorts(monoSamples)
        stereoSamplesStream.readShorts(stereoSamples)
        Log.e(TAG, "makeMp3: =generated")
        val outDirName= getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val outDir = File("$outDirName/AudioStreams/")
        outDir.mkdir()

        TestMp3ShortsWrite(outDir,monoSamples,stereoSamples)
        testBytesMP3Write(outDir,monoSamples, stereoSamples)
        stereoSamplesStream.close()
        monoSamplesStream.close()
        Log.e(TAG, "makeMp3: = end")
        }

    private fun TestMp3ShortsWrite(outDir: File, monoSamples: ShortArray,stereoSamples: ShortArray) {
        val outputFileMono = File(outDir, "/TestMono.mp3")
        val outputFileMonoStream = outputFileMono.outputStream()

        val outputFileStereo = File(outDir, "/TestStereo.mp3")
        val outputFileStereoStream = outputFileStereo.outputStream()

        val mp3MonoWriter = Mp3OutputAudioStream(
            outputFileMonoStream,
            sampleRate, MP3outBitrate, LameBuilder.Mode.MONO
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
        //runMediaScanner(arrayOf(outputFileMono.toString(), outputFileStereo.toString()))
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
            sampleRate, MP3outBitrate, LameBuilder.Mode.MONO
        )
        Log.e(TAG, "makeMp3: =start mono")
        mp3MonoWriterBytes.write(shortToByteArrayLittleEndian(monoSamples))
        mp3MonoWriterBytes.close()
        outputMonoBytesTest.close()
        Log.e(TAG, "makeMp3: =monoSaved")
        Log.e(TAG, "makeMp3: =stereo start")
        val mp3StereoWriterBytes = Mp3OutputAudioStream(
            outputStereoSoundBytesTest,
            sampleRate, MP3outBitrate, LameBuilder.Mode.STEREO
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


/*todo
   разобраться с размером буфера для  вывода звука, подсократить буфер у файла
   (вероятно надо секунд -надцать сделать)
   наладить и протестировать пересчет таймстемпов всех классов
   писать классы  на базе фильтрстрима - мониторный, приеобразование в байты и обратно,
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