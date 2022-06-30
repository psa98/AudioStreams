package c.ponom.audiostreams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import c.ponom.audiostreams.AudioOutState.*
import c.ponom.audiostreams.databinding.FragmentAudioOutBinding

class AudioOutFragment : Fragment() {

    private lateinit var  viewModel: AudioOutViewModel
    private var _binding: FragmentAudioOutBinding? = null
    private val binding get() = _binding!!
    private val volume:Short =16000
    private val freq = 440.0
    private lateinit var secondsPlayed: LiveData<Float>
    private lateinit var errorMessage: LiveData<String>
    private lateinit var currentState: LiveData<AudioOutState>

    /*
    * проигрыш звука из генератора (пока не выключат)
    * регулировка громкости (ползунок)
    * пауза и  продолжение
    *
    *
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
    }

    private fun setupButtons() {
        binding.playButton.setOnClickListener{
            viewModel.play(freq,volume) }
        binding.stopButton.setOnClickListener{
            viewModel.stopPlaying() }

    }


    private fun setupObservers() {
        secondsPlayed.observe(this,{
            binding.secondsPlayed.text=it.toString()})
        errorMessage.observe(this,{ binding.errorMessage.text=it.toString()})
        currentState.observe(this,{ setControlsState(it)})
    }

    private fun setControlsState(state: AudioOutState) {
        // будет управлять видимостью контролей
        binding.secondsPlayed.text="0.0"
        when(state) {
            STOPPED ->{
                binding.playButton.isEnabled=true
                binding.stopButton.isEnabled=false
            }
            PLAYING -> {
                binding.playButton.isEnabled=false
                binding.stopButton.isEnabled=true
                binding.errorMessage.visibility= View.GONE
                binding.errorLabel.visibility= View.GONE
            }
            ERROR -> {
                binding.playButton.isEnabled=true
                binding.stopButton.isEnabled=false
                binding.errorMessage.visibility= View.VISIBLE
                binding.errorLabel.visibility= View.VISIBLE
                binding.errorMessage.text=errorMessage.value

            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}