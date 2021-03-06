package c.ponom.audiostreams

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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import c.ponom.audiostreams.audio_streams.AudioDataInfo
import c.ponom.audiostreams.databinding.FragmentFilesBinding
import c.ponom.recorder2.audio_streams.TAG




@Suppress("FoldInitializerAndIfToElvis")
class FilesFragment : Fragment() {

    /**
     * Демонстрируется использование  потоков AudioTrackOutputStream, AudioFileSoundSource,
     * свойства выходного потока  timestamp, получение медиаданных через AudioDataInfo
     */



    private var _binding: FragmentFilesBinding? = null
    private lateinit var secondsPlayed: LiveData<String>
    private lateinit var mediaData: LiveData<String>
    private var activityResultLaunch = registerForActivityResult(StartActivityForResult()
    ) { result -> callback(result) }
    private lateinit var  viewModel: FilesViewModel


    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        viewModel= ViewModelProvider(this)[FilesViewModel::class.java]
        secondsPlayed=viewModel.secondsPlayed
        mediaData=viewModel.mediaData
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        setupObservers()
    }

    private fun setupObservers() {
        secondsPlayed.observe(this,{binding.secondsPlayed.text=it.toString()})
        mediaData.observe(this,{ binding.textMediaData.text=it.toString()})
    }

    private fun setupButtons() {
        binding.playButton.setOnClickListener {
            playExternalFile()
            it.isEnabled=false
            binding.stopButton.isEnabled=true

        }
        binding.stopButton.setOnClickListener {
            viewModel.playing=false
            it.isEnabled=false
            binding.playButton.isEnabled=true
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.playing=false
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




    @SuppressLint()
    private fun callback(result:ActivityResult) {
        val uri =result.data?.data
        if (uri==null) return
        Log.e(TAG, "callback Uri = $uri")
        val mediaData=AudioDataInfo(requireContext(),uri)
        binding.textMediaData.text="$mediaData"
        viewModel.playUri(requireContext(),uri)

    }








}