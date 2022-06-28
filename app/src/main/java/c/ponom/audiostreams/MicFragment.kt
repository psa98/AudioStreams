package c.ponom.audiostreams

import android.R
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import c.ponom.audiostreams.databinding.FragmentMicBinding
import c.ponom.navgrafapp.ui.notifications.MicRecordState
import c.ponom.navgrafapp.ui.notifications.MicRecordState.*
import c.ponom.navgrafapp.ui.notifications.MicTestViewModel
import c.ponom.recorder2.audio_streams.TAG

class MicFragment : Fragment() {

    private var _binding: FragmentMicBinding? = null
    var source = MediaRecorder.AudioSource.DEFAULT
    var sampleRate = 8000
    val sampleRateList = arrayListOf("16000","22050","32000","44100")
    val inputList = arrayListOf("0=DEFAULT","6=VOICE_RECOGNITION","9=UNPROCESSED")
    private val binding get() = _binding!!
    private lateinit var viewmodel:MicTestViewModel
    private lateinit var recordLevel: LiveData<Float>
    private lateinit var bytesPassed: LiveData<Int>
    private lateinit var currentState: LiveData<MicRecordState>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMicBinding.inflate(inflater, container, false)
        viewmodel=ViewModelProvider(this).get(MicTestViewModel::class.java)
        recordLevel=viewmodel.recordLevel
        bytesPassed=viewmodel.bytesPassed
        currentState=viewmodel.recorderState
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupButtons()
        setupObservers()
    }

    private fun setupButtons() {
        binding.recordButton.setOnClickListener { viewmodel.record(source, sampleRate) }
        binding.stopRecording.setOnClickListener{ viewmodel.stopRecording() }
        binding.playRecord.setOnClickListener{ viewmodel.play() }
        binding.stopPlaying.setOnClickListener{ viewmodel.stopPlaying() }

    }

    private fun setupObservers() {
        recordLevel.observe(this,{
            binding.meterLevel.level=it
            binding.textMicCurrentLevel.text=it.toString()})
        bytesPassed.observe(this,{ binding.textMicBytesWritten.text=it.toString()})
        currentState.observe(this,{ setControlsState(it)})
    }

    private fun setControlsState(state: MicRecordState) {
        // будет управлять видимостью контролей
        binding.textMicBytesWritten.text="0"
        binding.meterLevel.level=0.0f
        binding.textMicCurrentLevel.text="0.0"
        when(state) {
            NO_FILE_RECORDED ->{
                binding.recordButton.isEnabled=true
                binding.stopRecording.isEnabled=false
                binding.playRecord.isEnabled=false
                binding.stopPlaying.isEnabled=false
            }
            STOPPED_READY -> {
                binding.recordButton.isEnabled=true
                binding.stopRecording.isEnabled=false
                binding.playRecord.isEnabled=true
                binding.stopPlaying.isEnabled=false

            }
            RECORDING -> {
                binding.recordButton.isEnabled=false
                binding.stopRecording.isEnabled=true
                binding.playRecord.isEnabled=false
                binding.stopPlaying.isEnabled=false
            }
            PLAYING -> {
                binding.recordButton.isEnabled=false
                binding.stopRecording.isEnabled=false
                binding.playRecord.isEnabled=false
                binding.stopPlaying.isEnabled=true
            }
        }
    }


    private fun setupSpinners() {

        val rateAdapter = StandardChoiceAdapter(requireContext(), R.layout.simple_spinner_item, sampleRateList)
        binding.rateSelector.adapter = rateAdapter
        binding.rateSelector.onItemSelectedListener = SampleRateSelector()
        val inputAdapter = StandardChoiceAdapter(requireContext(), R.layout.simple_spinner_item, inputList)
        binding.inputSelector.adapter = inputAdapter
        binding.inputSelector.onItemSelectedListener = InputSelector()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class  SampleRateSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run {
                sampleRate=sampleRateList[position].toInt()
                Log.e(TAG, "onItemSelected: =$sampleRate")
            }



        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    inner class  InputSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run{
                source = inputList[position].split("=")[0]. toInt()
                Log.e(TAG, "onItemSelected: =$source")
            }



        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }



}