package c.ponom.audiostreamsdemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import c.ponom.audiostreamsdemo.AudioOutState.*
import c.ponom.audiostreamsdemo.databinding.FragmentAudioOutBinding

class AudioOutFragment : Fragment() {


    /**
     * Using of AudioTrackOutputStream, AudioFileSoundSource,
     * MicSoundInputStream, StreamPump classes demonstrated
     */

    private val  viewModel: AudioOutViewModel by lazy {
        ViewModelProvider(this)[AudioOutViewModel::class.java]
    }
    private var _binding: FragmentAudioOutBinding? = null
    private val binding get() = _binding!!
    var currentVolume=1f
    var sampleRate = 16000
    val sampleRateList = arrayOf("Select sampling rate","16000 (Default)","22050","32000","44100","9999999 (Illegal value)")
    private val volume:Short =16000
    private val freq = 440.0
    private lateinit var secondsPlayed: LiveData<Float>
    private lateinit var errorMessage: LiveData<String>
    private lateinit var currentState: LiveData<AudioOutState>

    /**
     * Демонстрируется использование потоков AudioTrackOutputStream, TestSoundInputStream,
     * свойства выходного потока timestamp, низкоуровневого обращения к свойству audioOut
     * управление громкостью AudioTrackOutputStream
     */

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioOutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        secondsPlayed=viewModel.secondsPlayed
        errorMessage=viewModel.errorData
        currentState=viewModel.recorderState
        setupButtons()
        setupObservers()
        setupSpinner()
        setVolumeControl()
    }

    private fun setupButtons() {
        with(binding) {
            playButton.setOnClickListener {viewModel.play(freq, volume, sampleRate)}
            stopButton.setOnClickListener {viewModel.stopPlaying()}
            forceError.setOnClickListener {viewModel.forceError()
            playButton.isEnabled=true}
        }
    }


    private fun setupObservers() {
        secondsPlayed.observe(viewLifecycleOwner) { binding.secondsPlayed.text = it.toString() }
        errorMessage.observe(viewLifecycleOwner) { binding.errorMessageText.text = it.toString() }
        currentState.observe(viewLifecycleOwner) { setControlsState(it) }
    }

    private fun setControlsState(state: AudioOutState) {
        with (binding){
            secondsPlayed.text="0.0"
            volumeSlider.progress=100
            currentVol.text= 1f.toString()
            when(state) {
                STOPPED -> {
                    volumeSlider.isEnabled=false
                    playButton.isEnabled = true
                    stopButton.isEnabled = false
                    forceError.isEnabled = false
                }
                PLAYING -> {
                    volumeSlider.isEnabled=true
                    playButton.isEnabled = false
                    stopButton.isEnabled = true
                    forceError.isEnabled = true
                    errorMessageText.visibility = View.GONE
                    errorLabel.visibility = View.GONE
                }
                ERROR -> {
                    volumeSlider.isEnabled=false
                    playButton.isEnabled = true
                    stopButton.isEnabled = false
                    forceError.isEnabled = false
                    errorMessageText.visibility = View.VISIBLE
                    errorLabel.visibility = View.VISIBLE
                    errorMessageText.text = errorMessage.value

                }
            }
        }
    }

    private fun setupSpinner() {

        val rateAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, sampleRateList)
        binding.rateSelector.adapter = rateAdapter
        binding.rateSelector.onItemSelectedListener = SampleRateSelector()
        binding.rateSelector.prompt = "Select sampling rate"

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    inner class  SampleRateSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?,
                                    position: Int, id: Long): Unit =
            run {
                if (position!=0)
                    sampleRate=sampleRateList[position].substringBefore(" (").toInt()
                }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }


    private fun setVolumeControl() {
        binding.volumeSlider.isEnabled=false
        binding.volumeSlider.max = 100
        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                currentVolume = seekBar.progress.toFloat()/100f
                viewModel.setVolume(currentVolume)
                binding.currentVol.text= currentVolume.toString()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPlaying()

    }

}