package c.ponom.audiostreams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import c.ponom.audiostreams.AudioOutState.*
import c.ponom.audiostreams.databinding.FragmentAudioOutBinding

class AudioOutFragment : Fragment() {


    /**
     * Демонстрируется использование  потоков AudioTrackOutputStream, AudioFileSoundSource,
     * MicSoundInputStream,  класса StreamPump, обработки ошибок
     */

    private lateinit var  viewModel: AudioOutViewModel
    private var _binding: FragmentAudioOutBinding? = null
    private val binding get() = _binding!!
    var currentVolume=1f
    var sampleRate = 16000
    val sampleRateList = arrayListOf("Select sampling rate","16000 (Default)","22050","32000","44100","9999999 (Illegal value)")
    private val volume:Short =16000
    private val freq = 440.0
    private lateinit var secondsPlayed: LiveData<Float>
    private lateinit var errorMessage: LiveData<String>
    private lateinit var currentState: LiveData<AudioOutState>

    /**
     * Демонстрируется использование  потоков AudioTrackOutputStream, TestSoundInputStream,
     * свойства выходного потока  timestamp, низкоуровнего обращения к свойству audioOut
     * управление громкостью AudioTrackOutputStream
     */

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel=ViewModelProvider(this).get(AudioOutViewModel::class.java)
        secondsPlayed=viewModel.secondsPlayed
        errorMessage=viewModel.errorData
        currentState=viewModel.recorderState
        _binding = FragmentAudioOutBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

    override fun onPause() {
        super.onPause()
        viewModel.stopPlaying()
    }

    private fun setupObservers() {
        secondsPlayed.observe(this,{binding.secondsPlayed.text=it.toString()})
        errorMessage.observe(this,{ binding.errorMessageText.text=it.toString()})
        currentState.observe(this,{ setControlsState(it)})
    }

    private fun setControlsState(state: AudioOutState) {
        with (binding){
        // будет управлять видимостью контролей
            secondsPlayed.text="0.0"
            seekbar.progress=100
            currentVol.text= 1f.toString()
            when(state) {
                STOPPED -> {
                    seekbar.isEnabled=false
                    playButton.isEnabled = true
                    stopButton.isEnabled = false
                    forceError.isEnabled = false
                }
                PLAYING -> {
                    binding.seekbar.isEnabled=true
                    playButton.isEnabled = false
                    stopButton.isEnabled = true
                    forceError.isEnabled = true
                    errorMessageText.visibility = View.GONE
                    errorLabel.visibility = View.GONE
                }
                ERROR -> {
                    seekbar.isEnabled=false
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

        val rateAdapter = StandardChoiceAdapter(requireContext(), android.R.layout.simple_spinner_item, sampleRateList)
        binding.rateSelector.adapter = rateAdapter
        binding.rateSelector.onItemSelectedListener = SampleRateSelector()
        binding.rateSelector.prompt = "Select sampling rate"

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    inner class  SampleRateSelector : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Unit =
            run {
                if (position!=0)
                    sampleRate=sampleRateList[position].substringBefore(" (").toInt()
                }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }


    private fun setVolumeControl() {

        binding.seekbar.isEnabled=false
        binding.seekbar.max = 100
        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                currentVolume = seekBar.progress.toFloat()/100f
                viewModel.setVolume(currentVolume)
                binding.currentVol.text= currentVolume.toString()
            }
        })

    }


}