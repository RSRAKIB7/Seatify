package activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.databinding.ActivityResetPasswordBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import kotlinx.coroutines.launch
import utils.AuthManager
import utils.SupabaseClient

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Capture session from the reset password deep link
        intent?.data?.let { uri ->
            when (val route = AuthManager.validateAndRouteDeepLink(uri)) {
                is AuthManager.AuthRedirect.ResetPassword -> {
                    lifecycleScope.launch {
                        try {
                            // Automatically parse and import session from the deep link
                            val session = supabase.auth.parseSessionFromUrl(uri.toString())
                            supabase.auth.importSession(session)
                            
                            Toast.makeText(this@ResetPasswordActivity, "Session verified. Enter new password.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@ResetPasswordActivity, "Invalid reset link or session expired", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is AuthManager.AuthRedirect.Invalid -> {
                    Toast.makeText(this, route.error, Toast.LENGTH_LONG).show()
                    finish()
                }
                else -> { /* Ignore */ }
            }
        }

        binding.btnResetPassword.setOnClickListener {
            updatePassword()
        }
    }

    private fun updatePassword() {
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmResetPassword.text.toString().trim()

        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "Required"
            return
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmResetPassword.error = "Passwords do not match"
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.auth.updateUser {
                    password = newPassword
                }
            }

            result.onSuccess {
                showLoading(false)
                Toast.makeText(this@ResetPasswordActivity, "Password updated successfully!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ResetPasswordActivity, MainActivity::class.java))
                finishAffinity()
            }.onFailure { e ->
                showLoading(false)
                Toast.makeText(this@ResetPasswordActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarReset.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnResetPassword.isEnabled = !isLoading
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}