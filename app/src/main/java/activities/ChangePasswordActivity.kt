package activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.databinding.ActivityChangePasswordBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import utils.AuthManager
import utils.SupabaseClient

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarChangePassword.setNavigationOnClickListener { finish() }

        binding.btnSavePassword.setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        val oldPassword = binding.etOldPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmNewPassword.text.toString().trim()

        if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmNewPassword.error = "Passwords do not match"
            return
        }

        if (newPassword.length < 6) {
            binding.etNewPassword.error = "Password must be at least 6 characters"
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                // 1. Verify old password by attempting to sign in again
                val email = supabase.auth.currentUserOrNull()?.email ?: throw Exception("User not found")
                
                val loginResult = AuthManager.safeAuthCall {
                    supabase.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                        this.email = email
                        this.password = oldPassword
                    }
                }

                loginResult.onSuccess {
                    // 2. If old password is correct, update to new password
                    val updateResult = AuthManager.safeAuthCall {
                        supabase.auth.updateUser {
                            password = newPassword
                        }
                    }

                    updateResult.onSuccess {
                        Toast.makeText(this@ChangePasswordActivity, "Password changed successfully!", Toast.LENGTH_LONG).show()
                        finish() // Go back to Settings
                    }.onFailure { throw it }

                }.onFailure {
                    Toast.makeText(this@ChangePasswordActivity, "Incorrect current password", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@ChangePasswordActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.pbChangePassword.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSavePassword.isEnabled = !isLoading
    }
}
