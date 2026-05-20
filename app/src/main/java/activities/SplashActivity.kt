package activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.seatify.databinding.ActivitySplashBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import utils.AuthManager
import utils.SupabaseClient

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fade in animation
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 1500
        binding.tvSplashName.startAnimation(fadeIn)

        checkFirstRunAndDeepLinks()
    }

    private fun checkFirstRunAndDeepLinks() {
        val sharedPref = getSharedPreferences("SeatifyPrefs", Context.MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("is_first_run_v2", true)

        if (isFirstRun && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires manual user approval for deep links
            showDeepLinkAlert(sharedPref)
        } else {
            proceedToNextScreen()
        }
    }

    private fun showDeepLinkAlert(sharedPref: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle("Enable Smart Links")
            .setMessage("To ensure 'Forgot Password' and 'Google Login' work correctly, please enable 'Open supported links' in the next screen.\n\nLook for 'Open by default' -> 'Open supported links'.")
            .setPositiveButton("Go to Settings") { _, _ ->
                sharedPref.edit().putBoolean("is_first_run_v2", false).apply()
                openAppSettings()
            }
            .setNegativeButton("Maybe Later") { _, _ ->
                sharedPref.edit().putBoolean("is_first_run_v2", false).apply()
                proceedToNextScreen()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback for some OS versions
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            fallbackIntent.data = Uri.parse("package:$packageName")
            startActivity(fallbackIntent)
        }
        // Note: We don't finish() here because user needs to return to app
        // They will return and we should check again or just proceed next time
        finish() 
    }

    private fun proceedToNextScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthAndNavigate()
        }, 2500)
    }

    private fun checkAuthAndNavigate() {
        lifecycleScope.launch {
            val refreshed = AuthManager.refreshSession(SupabaseClient.client)
            val intent = if (refreshed) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }
}
