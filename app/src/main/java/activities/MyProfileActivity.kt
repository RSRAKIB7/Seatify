package activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityMyProfileBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import model.UserProfile
import utils.AuthManager
import utils.SupabaseClient

class MyProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyProfileBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarProfileDetail.setNavigationOnClickListener { finish() }
        
        loadProfileData()
    }

    private fun loadProfileData() {
        val user = supabase.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.postgrest.from("users")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingle<UserProfile>()
            }

            result.onSuccess { profile ->
                binding.tvDetailName.text = profile.name
                binding.tvDetailEmail.text = profile.email
                binding.tvDetailRole.text = profile.role.replaceFirstChar { it.uppercase() }
                
                binding.tvValNid.text = profile.nid_birth_cert ?: "Not provided"
                binding.tvValOcc.text = profile.occupation_institution ?: "Not provided"
                binding.tvValPhone.text = profile.phone ?: "Not provided"
                binding.tvValAddr.text = profile.permanent_address ?: "Not provided"
                binding.tvValEName.text = profile.emergency_contact_name ?: "Not provided"
                binding.tvValEPhone.text = profile.emergency_contact_phone ?: "Not provided"

                if (!profile.profile_picture_url.isNullOrEmpty()) {
                    Glide.with(this@MyProfileActivity)
                        .load(profile.profile_picture_url)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivDetailProfilePic)
                }
            }.onFailure { e ->
                Toast.makeText(this@MyProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
