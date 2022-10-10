package c.ponom.audiostreamsdemo

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import c.ponom.audiostreamsdemo.MicRecordState.*
import c.ponom.audiostreamsdemo.databinding.FragmentMicBinding


class MicFragment : Fragment() {

    private var _binding: FragmentMicBinding? = null
    var source = MediaRecorder.AudioSource.DEFAULT
    var sampleRate = 16000
    val sampleRateList = arrayOf("16000","22050","32000","44100")
    val inputList = arrayOf("0=DEFAULT","6=VOICE_RECOGNITION","9=UNPROCESSED")
    private val binding get() = _binding!!
    private val viewModel:MicTestViewModel by viewModels()
    private lateinit var recordLevel: LiveData<Float>
    private lateinit var bytesPassed: LiveData<Int>
    private lateinit var currentState: LiveData<MicRecordState>
    private val recordButton by lazy { binding.recordButton }
    private val stopRecordingButton by lazy { binding.stopRecording}
    private val playRecordButton by lazy { binding.playRecord}
    private val stopPlayingButton by lazy { binding.stopPlaying}

    /**
     * Демонстрируется использование  потоков AudioTrackOutputStream, AudioFileSoundSource,
     * MicSoundInputStream,  класса StreamPump, обработка ошибок, запись публичный медиа
     * каталог
     */


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMicBinding.inflate(inflater, container, false)
        recordLevel=viewModel.recordLevel
        bytesPassed=viewModel.bytesPassed
        currentState=viewModel.recorderState
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupButtons()
        setupObservers()
    }

    private fun setupButtons() {
        recordButton.setOnClickListener { viewModel.record(source, sampleRate) }
        stopRecordingButton.setOnClickListener{ viewModel.stopRecording() }
        playRecordButton.setOnClickListener{ viewModel.play() }
        stopPlayingButton.setOnClickListener{
            viewModel.stopPlaying()
            binding.meterLevel.level=0f
            binding.textMicCurrentLevel.text=""}

    }

    private fun setupObservers() {
        recordLevel.observe(viewLifecycleOwner,{
            binding.meterLevel.level=it
            binding.textMicCurrentLevel.text=it.toString()})
        bytesPassed.observe(viewLifecycleOwner,{ binding.textMicBytesWritten.text=it.toString()})
        currentState.observe(viewLifecycleOwner,{ setControlsState(it)})
    }

    private fun setControlsState(state: MicRecordState) {

        if (!isPermissionsGranted()) {
            binding.textMicTest.text = getString(R.string.need_permissions)
            binding.textMicTest.setTextColor(Color.RED)

        }
        binding.textMicBytesWritten.text="0"
        binding.meterLevel.level=0.0f
        binding.textMicCurrentLevel.text="0.0"

        when(state) {
            NO_FILE_RECORDED ->{
                recordButton.isEnabled=true
                stopRecordingButton.isEnabled=false
                playRecordButton.isEnabled=false
                stopPlayingButton.isEnabled=false
            }
            STOPPED_READY -> {
                recordButton.isEnabled=true
                stopRecordingButton.isEnabled=false
                playRecordButton.isEnabled=true
                stopPlayingButton.isEnabled=false
            }
            RECORDING -> {
                recordButton.isEnabled=false
                stopRecordingButton.isEnabled=true
                playRecordButton.isEnabled=false
                stopPlayingButton.isEnabled=false
            }
            PLAYING -> {
                recordButton.isEnabled=false
                stopRecordingButton.isEnabled=false
                playRecordButton.isEnabled=false
                stopPlayingButton.isEnabled=true
            }
        }
    }


    override fun onPause() {
        super.onPause()
        when (viewModel.recorderState.value) {
            RECORDING -> viewModel.stopRecording()
            PLAYING -> viewModel.stopPlaying()
            else -> {}
        }
    }

    private fun setupSpinners() {

        val rateAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, sampleRateList)
        binding.rateSelector.adapter = rateAdapter
        binding.rateSelector.onItemSelectedListener = SampleRateSelector()
        val inputAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, inputList)
        binding.inputSelector.adapter = inputAdapter
        binding.inputSelector.onItemSelectedListener = InputSelector()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class  SampleRateSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run {
                sampleRate=sampleRateList[position].toInt()
                Log.e(TAG, "onItemSelected: =$sampleRate")
            }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private inner class  InputSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run{
                source = inputList[position].split("=")[0]. toInt()
                Log.e(TAG, "onItemSelected: =$source")
            }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun isPermissionsGranted(): Boolean {
        val recordPermission = checkSelfPermission(requireContext(), RECORD_AUDIO) ==
                PERMISSION_GRANTED
        val writePermission = checkSelfPermission(requireContext(),WRITE_EXTERNAL_STORAGE) ==
                PERMISSION_GRANTED
        return recordPermission && writePermission
    }

}