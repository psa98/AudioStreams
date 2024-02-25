package c.ponom.audiostreamsdemo

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
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
import c.ponom.audiostreamsdemo.MicRecordState.NO_FILE_RECORDED
import c.ponom.audiostreamsdemo.MicRecordState.PLAYING
import c.ponom.audiostreamsdemo.MicRecordState.RECORDING
import c.ponom.audiostreamsdemo.MicRecordState.STOPPED_READY
import c.ponom.audiostreamsdemo.databinding.FragmentMicBinding
import com.google.android.material.slider.Slider


class MicFragment : Fragment() {

    /**
     * Demo for AudioTrackOutputStream, AudioFileSoundSource, MicSoundInputStream, StreamPump,
     * classes, error handling.
     */

    private var _binding: FragmentMicBinding? = null
    var source = MediaRecorder.AudioSource.DEFAULT
    var sampleRate = 16000
    val sampleRateList = arrayOf("16000","22050","32000","44100")
    val inputList = arrayOf("0=DEFAULT","6=VOICE_RECOGNITION","9=UNPROCESSED")
    private val binding get() = _binding!!
    private val viewModel:MicTestViewModel by viewModels()
    private val recordLevel: LiveData<Float> by lazy { viewModel.recordLevel }
    private val bytesPassed: LiveData<Int> by lazy { viewModel.bytesPassed }
    private val currentState: LiveData<MicRecordState> by lazy { viewModel.recorderState }
    private val recordButton by lazy { binding.recordButton }
    private val stopRecordingButton by lazy { binding.stopRecording}
    private val playRecordButton by lazy { binding.playRecord}
    private val stopPlayingButton by lazy { binding.stopPlaying}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupButtons()
        setupObservers()
        initVolumeControls()
    }

    private fun setupButtons() {
        recordButton.setOnClickListener { viewModel.record(source, sampleRate) }
        stopRecordingButton.setOnClickListener{
            viewModel.stopRecording()
        }
        playRecordButton.setOnClickListener{ viewModel.play() }
        stopPlayingButton.setOnClickListener{
            viewModel.stopPlaying()
            binding.meterLevel.level=0f
            binding.textMicCurrentLevel.text=""}
    }

    private fun setupObservers() {
        recordLevel.observe(viewLifecycleOwner) { level->
            binding.meterLevel.level = level
            binding.textMicCurrentLevel.text = level.toString()
        }
        bytesPassed.observe(viewLifecycleOwner) { binding.textMicBytesWritten.text = it.toString() }
        currentState.observe(viewLifecycleOwner) { setControlsState(it) }
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
                binding.volumeControlSlider.isEnabled=false
            }
            STOPPED_READY -> {
                recordButton.isEnabled=true
                stopRecordingButton.isEnabled=false
                playRecordButton.isEnabled=true
                stopPlayingButton.isEnabled=false
                binding.volumeControlSlider.isEnabled=false
            }
            RECORDING -> {
                recordButton.isEnabled=false
                stopRecordingButton.isEnabled=true
                playRecordButton.isEnabled=false
                stopPlayingButton.isEnabled=false
                binding.volumeControlSlider.isEnabled=true
            }
            PLAYING -> {
                recordButton.isEnabled=false
                stopRecordingButton.isEnabled=false
                playRecordButton.isEnabled=false
                stopPlayingButton.isEnabled=true
                binding.volumeControlSlider.isEnabled=false
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
            }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private inner class  InputSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run{
                source = inputList[position].split("=")[0].toInt()
            }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun isPermissionsGranted(): Boolean {
        return checkSelfPermission(requireContext(), RECORD_AUDIO) == PERMISSION_GRANTED
    }


    @SuppressLint("SetTextI18n")
    private fun initVolumeControls() {
        with(binding) {
            volumeControlSlider.value = 100f
            volumeControlValue.text = "100%"
            volumeControlSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
                val vol = (value/100f)
                viewModel.targetVolume=vol
                binding.volumeControlValue.text= "${(vol * 100).toInt()}%"
             })
        }
    }

}