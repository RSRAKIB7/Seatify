package activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityRegisterBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import model.UserProfile
import utils.AuthManager
import utils.SupabaseClient

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind CCP to EditText
        binding.ccp.registerCarrierNumberEditText(binding.etPhone)

        // Check for Deep Link (Google Redirect)
        intent?.let { handleDeepLink(it) }

        binding.btnCreateAccount.setOnClickListener {
            registerUser()
        }

        binding.tvSignIn.setOnClickListener {
            finish()
        }

        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.auth.signInWith(Google, redirectUrl = "seatify://activity_main")
            }
            result.onFailure { e ->
                Toast.makeText(this@RegisterActivity, "Google Sign In Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        
        when (val route = AuthManager.validateAndRouteDeepLink(uri)) {
            is AuthManager.AuthRedirect.Register -> {
                lifecycleScope.launch {
                    try {
                        // 1. Import session from the redirect URL
                        val session = supabase.auth.parseSessionFromUrl(uri.toString())
                        supabase.auth.importSession(session)
                        
                        // 2. Check if user already has a profile (existing user logging in via Google)
                        val user = supabase.auth.currentUserOrNull() ?: return@launch
                        
                        val existingProfile = AuthManager.safeAuthCall {
                            supabase.postgrest.from("users")
                                .select { filter { eq("id", user.id) } }
                                .decodeList<UserProfile>()
                        }

                        if (existingProfile.getOrNull()?.isNotEmpty() == true) {
                            // Already registered! Go to Main
                            startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                            finishAffinity()
                            return@launch
                        }

                        // 3. New User: Pre-fill the form from Google metadata
                        val name = user.userMetadata?.get("full_name")?.toString()?.replace("\"", "") ?: ""
                        val email = user.email ?: ""

                        binding.etName.setText(name)
                        binding.etEmail.setText(email)

                        // Disable email editing to maintain auth integrity
                        binding.etEmail.isEnabled = false
                        binding.etPassword.hint = "Google Login (Password not required)"
                        binding.etPassword.isEnabled = false
                        binding.etConfirmPassword.isEnabled = false

                        Toast.makeText(this@RegisterActivity, "Welcome $name! Please complete your profile.", Toast.LENGTH_LONG).show()

                    } catch (e: Exception) {
                        Log.e("RegisterActivity", "Deep link handling failed", e)
                    }
                }
            }
            is AuthManager.AuthRedirect.Invalid -> {
                Toast.makeText(this, route.error, Toast.LENGTH_LONG).show()
            }
            else -> { }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun registerUser() {
        val name = binding.etName.text.toString().trim()
        val emailValue = binding.etEmail.text.toString().trim()
        val passwordValue = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            return
        }
        
        if (!binding.ccp.isValidFullNumber) {
            binding.etPhone.error = "Invalid phone number"
            return
        }
        
        if (!binding.cbTerms.isChecked) {
            Toast.makeText(this, "Please agree to terms", Toast.LENGTH_SHORT).show()
            return
        }

        val fullPhoneNumber = binding.ccp.fullNumberWithPlus

        showLoading(true)
        lifecycleScope.launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                
                // If currentUser is null, we need to sign up via Email
                if (currentUser == null) {
                    if (passwordValue != confirmPassword) {
                        showLoading(false)
                        binding.etConfirmPassword.error = "Passwords do not match"
                        return@launch
                    }
                    
                    val signupResult = AuthManager.safeAuthCall {
                        supabase.auth.signUpWith(Email) {
                            email = emailValue
                            password = passwordValue
                        }
                    }
                    
                    if (signupResult.isFailure) {
                        showLoading(false)
                        Toast.makeText(this@RegisterActivity, signupResult.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }

                // Now we definitely have a user (either from Google redirect or just signed up)
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    val userProfile = UserProfile(
                        id = user.id,
                        name = name,
                        email = user.email!!,
                        phone = fullPhoneNumber,
                        role = "student"
                    )
                    
                    val insertResult = AuthManager.safeAuthCall {
                        supabase.postgrest.from("users").upsert(userProfile)
                    }

                    insertResult.onSuccess {
                        val sharedPreferences = getSharedPreferences("SeatifyPrefs", MODE_PRIVATE)
                        sharedPreferences.edit().putBoolean("remember_me", true).apply()

                        showLoading(false)
                        Toast.makeText(this@RegisterActivity, "Registration Successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()
                    }.onFailure { e ->
                        showLoading(false)
                        Toast.makeText(this@RegisterActivity, "Profile Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    showLoading(false)
                    Toast.makeText(this@RegisterActivity, "Please confirm your email to continue", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@RegisterActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCreateAccount.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
    }
}
