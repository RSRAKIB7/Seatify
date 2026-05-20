package activities

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityMyInformationBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.UserProfile
import utils.AuthManager
import utils.SupabaseClient
import java.util.UUID

class MyInformationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyInformationBinding
    private val supabase = SupabaseClient.client
    private var profilePhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserInfo()

        binding.cvProfilePhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        binding.btnSaveInfo.setOnClickListener {
            saveUserInfo()
        }
    }

    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            profilePhotoUri = it
            binding.ivProfileInfo.setImageURI(it)
            binding.ivProfileInfo.imageTintList = null
            binding.ivProfileInfo.setPadding(0, 0, 0, 0)
        }
    }

    private fun loadUserInfo() {
        val user = supabase.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            try {
                val result = AuthManager.safeAuthCall {
                    supabase.postgrest.from("users")
                        .select { filter { eq("id", user.id) } }
                        .decodeList<UserProfile>()
                }
                
                result.onSuccess { profileList ->
                    if (profileList.isNotEmpty()) {
                        val profile = profileList[0]
                        binding.etInfoName.setText(profile.name)
                        binding.etInfoEmail.setText(profile.email)
                        binding.etInfoPhone.setText(profile.phone)
                        binding.etInfoOccupation.setText(profile.occupation_institution)
                        binding.etInfoPresentAddress.setText(profile.present_address)
                        binding.etInfoPermanentAddress.setText(profile.permanent_address)
                        binding.etInfoNid.setText(profile.nid_birth_cert)
                        binding.etInfoEmergencyName.setText(profile.emergency_contact_name)
                        binding.etInfoEmergencyPhone.setText(profile.emergency_contact_phone)

                        val genderArray = resources.getStringArray(R.array.gender_options)
                        val genderIndex = genderArray.indexOf(profile.gender).coerceAtLeast(0)
                        binding.spinnerGender.setSelection(genderIndex)
                        
                        if (!profile.profile_picture_url.isNullOrEmpty()) {
                            Glide.with(this@MyInformationActivity)
                                .load(profile.profile_picture_url)
                                .circleCrop()
                                .into(binding.ivProfileInfo)
                            binding.ivProfileInfo.imageTintList = null
                            binding.ivProfileInfo.setPadding(0, 0, 0, 0)
                        }
                    } else {
                        binding.etInfoName.setText(user.userMetadata?.get("full_name")?.toString()?.replace("\"", ""))
                        binding.etInfoEmail.setText(user.email)
                    }
                }.onFailure { e ->
                    Toast.makeText(this@MyInformationActivity, "Error loading info: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyInformationActivity, "System error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserInfo() {
        val user = supabase.auth.currentUserOrNull() ?: return
        val name = binding.etInfoName.text.toString().trim()
        val phone = binding.etInfoPhone.text.toString().trim()
        val gender = if (binding.spinnerGender.selectedItemPosition > 0) binding.spinnerGender.selectedItem.toString() else null
        val occupation = binding.etInfoOccupation.text.toString().trim()
        val presentAddress = binding.etInfoPresentAddress.text.toString().trim()
        val permanentAddress = binding.etInfoPermanentAddress.text.toString().trim()
        val nid = binding.etInfoNid.text.toString().trim()
        val emergencyName = binding.etInfoEmergencyName.text.toString().trim()
        val emergencyPhone = binding.etInfoEmergencyPhone.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.etInfoName.error = "Name is required"
            return
        }

        binding.btnSaveInfo.isEnabled = false
        binding.pbProfilePhoto.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                var photoUrl: String? = null
                
                profilePhotoUri?.let { uri ->
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        val fileName = "${user.id}_${UUID.randomUUID()}.jpg"
                        // Profile photos go to person_image bucket
                        val bucket = supabase.storage.from("person_image")
                        bucket.upload(fileName, bytes)
                        photoUrl = bucket.publicUrl(fileName)
                    }
                }

                val updateData = mutableMapOf<String, String?>()
                updateData["id"] = user.id 
                updateData["name"] = name
                updateData["email"] = user.email
                updateData["phone"] = phone
                updateData["gender"] = gender
                updateData["occupation_institution"] = occupation
                updateData["present_address"] = presentAddress
                updateData["permanent_address"] = permanentAddress
                updateData["nid_birth_cert"] = nid
                updateData["emergency_contact_name"] = emergencyName
                updateData["emergency_contact_phone"] = emergencyPhone
                
                photoUrl?.let { updateData["profile_picture_url"] = it }

                val result = AuthManager.safeAuthCall {
                    supabase.postgrest.from("users").upsert(updateData)
                }

                result.onSuccess {
                    Toast.makeText(this@MyInformationActivity, "Information updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure { throw it }

            } catch (e: Exception) {
                Toast.makeText(this@MyInformationActivity, "Update failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSaveInfo.isEnabled = true
                binding.pbProfilePhoto.visibility = View.GONE
            }
        }
    }
}
