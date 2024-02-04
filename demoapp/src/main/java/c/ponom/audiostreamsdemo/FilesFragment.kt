package c.ponom.audiostreamsdemo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import c.ponom.audiostreamsdemo.databinding.FragmentFilesBinding
import c.ponom.audiuostreams.audiostreams.AudioDataInfo



class FilesFragment : Fragment() {

    /**
     * Demo for AudioTrackOutputStream, AudioFileSoundSource classes, timestamp property,
     * AudioDataInfo class
     */

    private var _binding: FragmentFilesBinding? = null
    private var activityResultLaunch = registerForActivityResult(
        StartActivityForResult()
    ) { result -> callback(result) }
    private val viewModel: FilesViewModel by viewModels()
    private val secondsPlayed: LiveData<String> by lazy { viewModel.secondsPlayed }
    private val mediaData: LiveData<String> by lazy { viewModel.mediaData }
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        setupObservers()
    }

    private fun setupObservers() {
        secondsPlayed.observe(this.viewLifecycleOwner) { time -> binding.secondsPlayed.text = time }
        mediaData.observe(this.viewLifecycleOwner) { binding.textMediaData.text = it.toString() }
    }

    private fun setupButtons() {
        binding.playButton.setOnClickListener {
            playExternalFile()
            it.isEnabled = false
            binding.stopButton.isEnabled = true
        }
        binding.stopButton.setOnClickListener {
            viewModel.playing = false
            it.isEnabled = false
            binding.playButton.isEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        binding.playButton.isEnabled = true
        binding.stopButton.isEnabled = false
        viewModel.playing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun playExternalFile() {
        val intent = Intent()
        intent.apply {
            action = Intent.ACTION_OPEN_DOCUMENT
            type = "audio/*"
        }
        activityResultLaunch.launch(intent)
    }


    @SuppressLint("SetTextI18n")
    private fun callback(result: ActivityResult) {
        val uri = result.data?.data ?: return
        Log.i(TAG, "callback Uri = $uri")
        val tracks = AudioDataInfo.getTrackData(requireContext(), uri)
        val mediaData = AudioDataInfo(requireContext(), uri)
        binding.textMediaData.text = "Media Data: $mediaData \n Media Data for tracks:  $tracks"
        binding.stopButton.isEnabled = true
        viewModel.playUri(requireContext(), uri)
    }

}