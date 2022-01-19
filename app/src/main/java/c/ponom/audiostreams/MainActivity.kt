package c.ponom.audiostreams

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.navigation.ui.AppBarConfiguration
import c.ponom.audiostreams.audio_streams.MicSoundInputStream
import c.ponom.audiostreams.audio_streams.SoundProcessingUtils.getRMS
import c.ponom.audiostreams.databinding.ActivityMainBinding
import c.ponom.recorder2.audio_streams.AudioOutputSteam
import c.ponom.recorder2.audio_streams.TestSoundInputStream
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.System.currentTimeMillis

private const val PERMISSION_REQUEST_CODE: Int =1
class MainActivity : AppCompatActivity() {

    private var recordingIsOn: Boolean=false
    private var permissionGranted: Boolean=false
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    var microphoneStream: MicSoundInputStream = MicSoundInputStream(16000)
    private var lastVolumeTimestamp = 0L

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermission()
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
        val testSoundInputStream=TestSoundInputStream(440.0,480.0,
            8000,7000,16000, CHANNEL_IN_STEREO, ENCODING_PCM_16BIT)
        val data=ShortArray(64000)
        testSoundInputStream.readShorts(data)

        CoroutineScope(IO).launch{
            val out  =AudioOutputSteam(16000, CHANNEL_OUT_STEREO,ENCODING_PCM_16BIT,500)
            out.play()
            //testMic()
            out.writeShorts(data)
            out.close()
        }
    }




    fun playSoundMono(view: View) {
        val testSoundInputStream=TestSoundInputStream(440.0,32000,
            16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        val data=ShortArray(128000)
        testSoundInputStream.readShorts(data)

        CoroutineScope(IO).launch{
            val out  =AudioOutputSteam(16000, CHANNEL_OUT_STEREO,ENCODING_PCM_16BIT,500)
            out.play()
            //testMic()
            out.writeShorts(data)
            out.close()
        }
    }


    fun playSound(view: View) {
        playSoundStereo(view)
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