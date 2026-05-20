package activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityLoginBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import model.UserProfile
import utils.AuthManager
import utils.SupabaseClient
import android.net.Uri
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Deep Link from Google Login
        intent?.let { handleDeepLink(it) }

        // Persistent Login Check
        val sharedPreferences = getSharedPreferences("SeatifyPrefs", MODE_PRIVATE)
        val isRemembered = sharedPreferences.getBoolean("remember_me", false)

        lifecycleScope.launch {
            val status = supabase.auth.sessionStatus.first()
            if (status is SessionStatus.Authenticated && isRemembered) {
                // Verify session is still valid
                val refreshed = AuthManager.refreshSession(supabase)
                if (refreshed) {
                    checkUserRoleAndRedirect(supabase.auth.currentUserOrNull()?.id ?: "")
                } else {
                    supabase.auth.signOut()
                }
            } else if (status is SessionStatus.Authenticated && !isRemembered) {
                supabase.auth.signOut()
            }
        }

        binding.btnSignIn.setOnClickListener {
            loginUser()
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

        binding.tvForgotPassword.setOnClickListener {
            forgotPassword()
        }
        
        // Hide or disable Facebook button
        binding.btnFacebook.visibility = android.view.View.GONE
    }

    private fun forgotPassword() {
        val emailValue = binding.etEmail.text.toString().trim()
        if (emailValue.isEmpty()) {
            binding.etEmail.error = "Enter email to reset password"
            return
        }

        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.auth.resetPasswordForEmail(
                    email = emailValue,
                    redirectUrl = "seatify://activity_reset_password"
                )
            }
            
            result.onSuccess {
                Toast.makeText(this@LoginActivity, "Password reset link sent to your email", Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signInWithGoogle() {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                // Point to activity_main for a direct login experience
                supabase.auth.signInWith(Google, redirectUrl = "seatify://activity_main")
            }

            result.onFailure { e ->
                Toast.makeText(this@LoginActivity, "Google Sign In Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        
        when (val route = AuthManager.validateAndRouteDeepLink(uri)) {
            is AuthManager.AuthRedirect.Main -> {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(mainIntent)
                finish()
            }
            is AuthManager.AuthRedirect.Register -> {
                // If it's the register link, we go to RegisterActivity which has the smart pre-fill logic
                val regIntent = Intent(this, RegisterActivity::class.java).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(regIntent)
            }
            is AuthManager.AuthRedirect.ResetPassword -> {
                val resetIntent = Intent(this, ResetPasswordActivity::class.java).apply {
                    data = uri
                }
                startActivity(resetIntent)
            }
            is AuthManager.AuthRedirect.Invalid -> {
                Toast.makeText(this, route.error, Toast.LENGTH_LONG).show()
            }
            else -> { }
        }
    }

    private fun processLoginRedirect(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Self-fix: Instead of relying purely on URL params,
                // we import the session and then re-fetch the user to ensure it's valid.
                val session = supabase.auth.parseSessionFromUrl(uri.toString())
                supabase.auth.importSession(session)

                val refreshed = AuthManager.refreshSession(supabase)
                if (refreshed) {
                    val user = supabase.auth.currentUserOrNull()
                    if (user != null) {
                        Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                        checkUserRoleAndRedirect(user.id)
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Session verification failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Login Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun loginUser() {
        val emailValue = binding.etEmail.text.toString().trim()
        val passwordValue = binding.etPassword.text.toString().trim()
        val rememberMe = binding.cbRemember.isChecked

        if (emailValue.isEmpty()) {
            binding.etEmail.error = "Email is required"
            Toast.makeText(this, "Please fill in the email", Toast.LENGTH_SHORT).show()
            return
        }

        if (passwordValue.isEmpty()) {
            binding.etPassword.error = "Password is required"
            Toast.makeText(this, "Please fill in the password", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.auth.signInWith(Email) {
                    email = emailValue
                    password = passwordValue
                }
            }

            result.onSuccess {
                // Save Remember Me preference
                val sharedPreferences = getSharedPreferences("SeatifyPrefs", MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("remember_me", rememberMe).apply()

                val user = supabase.auth.currentUserOrNull()
                user?.let { checkUserRoleAndRedirect(it.id) }
            }.onFailure { e ->
                Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkUserRoleAndRedirect(userId: String) {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}
