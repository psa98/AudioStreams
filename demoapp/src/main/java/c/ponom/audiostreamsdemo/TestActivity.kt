package c.ponom.audiostreamsdemo

import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import c.ponom.audiostreamsdemo.databinding.ActivityTestBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar

private const val PERMISSION_REQUEST_CODE = 1

class TestActivity : AppCompatActivity() {


    private lateinit var binding: ActivityTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_files, R.id.navigation_mic, R.id.navigation_out
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        checkPermissions()
    }

    private fun isPermissionsGranted(): Boolean {
        return checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_GRANTED
    }


    private fun checkPermissions() {
        if (!isPermissionsGranted()) requestPermissions(
            this, arrayOf(RECORD_AUDIO), PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PERMISSION_GRANTED ) Snackbar.make(
            binding.root, "Need permission to work!", Snackbar.LENGTH_LONG
        ).show()
    }
}