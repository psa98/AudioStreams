package c.ponom.audiostreams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import c.ponom.audiostreams.databinding.FragmentFilesBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null

    /*
    * проигрывание произвольного файла
    * вывод медиа данных о нем
    *
    */
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}