package c.ponom.audiostreams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import c.ponom.audiostreams.databinding.FragmentAudioOutBinding

class AudioOutFragment : Fragment() {

    private var _binding: FragmentAudioOutBinding? = null

    private val binding get() = _binding!!

   /*
   * проигрыш звука из генератора (пока не выключат)
   * регулировка громкости.
   * пауза и  продолжение
   * выбор вывода
   *
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

        binding.testPlayGenerated.setOnClickListener {

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}