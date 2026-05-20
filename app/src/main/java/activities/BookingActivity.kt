package activities

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityBookingBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseBooking
import model.SupabaseProperty
import model.UserProfile
import utils.AuthManager
import utils.SupabaseClient
import java.util.Calendar
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Locale

class BookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingBinding
    private val supabase = SupabaseClient.client
    private var property: SupabaseProperty? = null
    private var propertyId: String? = null
    private var idCardUri: Uri? = null
    private var seatCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        propertyId = intent.getStringExtra("PROPERTY_ID")

        if (propertyId != null) {
            fetchPropertyDetails(propertyId!!)
        } else {
            Toast.makeText(this, "Error: Property ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupSpinners()
        loadUserInfo()
        setupSeatSelection()

        binding.etMoveInDate.setOnClickListener {
            showDatePicker()
        }

        binding.cvIdCardPhoto.setOnClickListener {
            idPickerLauncher.launch("image/*")
        }

        binding.btnConfirmBooking.setOnClickListener {
            confirmBooking()
        }
    }

    private fun setupSeatSelection() {
        binding.btnPlusSeat.setOnClickListener {
            val maxSeats = property?.availableSeats ?: 1
            if (seatCount < maxSeats) {
                seatCount++
                binding.tvBookingSeatCount.text = seatCount.toString()
            } else {
                Toast.makeText(this, "Only $maxSeats seats available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMinusSeat.setOnClickListener {
            if (seatCount > 1) {
                seatCount--
                binding.tvBookingSeatCount.text = seatCount.toString()
            }
        }
    }

    private fun setupSpinners() {
        val genders = resources.getStringArray(R.array.gender_options)
        binding.spinnerBookingGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        val smoking = resources.getStringArray(R.array.smoking_options)
        binding.spinnerSmoking.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, smoking)
    }

    private val idPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            idCardUri = it
            binding.ivIdCardPreview.setImageURI(it)
            binding.ivIdCardPreview.imageTintList = null
            binding.ivIdCardPreview.setPadding(0, 0, 0, 0)
        }
    }

    private fun fetchPropertyDetails(id: String) {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                SupabaseClient.client.postgrest.from("properties")
                    .select { filter { eq("id", id) } }
                    .decodeSingle<SupabaseProperty>()
            }
            
            result.onSuccess { propertyData ->
                property = propertyData
                withContext(Dispatchers.Main) {
                    binding.tvBookingPropertyName.text = propertyData.name
                    binding.tvBookingPropertyPrice.text = "৳ ${propertyData.price ?: "0"} / month"
                    
                    Glide.with(this@BookingActivity)
                        .load(propertyData.image_url)
                        .placeholder(R.drawable.bg_gradient)
                        .into(binding.ivBookingPropertyImage)
                    
                    binding.tvBookingSeatCount.text = "1"
                }
            }.onFailure { e ->
                Toast.makeText(this@BookingActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserInfo() {
        val user = supabase.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            try {
                val profile = supabase.postgrest.from("users")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingle<UserProfile>()
                
                binding.etBookingUserName.setText(profile.name)
                binding.etBookingNid.setText(profile.nid_birth_cert)
                binding.etBookingPermanentAddress.setText(profile.permanent_address)
                binding.etBookingEmergencyName.setText(profile.emergency_contact_name)
                binding.etBookingEmergencyPhone.setText(profile.emergency_contact_phone)
                binding.etBookingWorkplace.setText(profile.occupation_institution)
                binding.etBookingDuration.setText(profile.stay_duration)
                binding.etBookingDietary.setText(profile.dietary_preference)

                val genderArray = resources.getStringArray(R.array.gender_options)
                val genderIndex = genderArray.indexOf(profile.gender).coerceAtLeast(0)
                binding.spinnerBookingGender.setSelection(genderIndex)
                
            } catch (e: Exception) {
                binding.etBookingUserName.setText(user.userMetadata?.get("full_name")?.toString()?.replace("\"", ""))
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(this, { _, year, month, day ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, day)
            
            // Final validation check for 6 months
            val maxDate = Calendar.getInstance()
            maxDate.add(Calendar.MONTH, 6)
            
            if (selectedDate.after(maxDate)) {
                Toast.makeText(this, "Booking must be within 6 months from today", Toast.LENGTH_LONG).show()
                binding.etMoveInDate.setText("")
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                binding.etMoveInDate.setText(sdf.format(selectedDate.time))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        
        // Prevent selecting past dates
        datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
        
        // Set maximum selectable date to 6 months from now
        val maxCalendar = Calendar.getInstance()
        maxCalendar.add(Calendar.MONTH, 6)
        datePicker.datePicker.maxDate = maxCalendar.timeInMillis
        
        datePicker.show()
    }

    private fun confirmBooking() {
        val name = binding.etBookingUserName.text.toString().trim()
        val nid = binding.etBookingNid.text.toString().trim()
        val moveIn = binding.etMoveInDate.text.toString()

        if (name.isEmpty() || nid.isEmpty() || moveIn.isEmpty()) {
            Toast.makeText(this, "Please fill Name, NID, and Move-in Date", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnConfirmBooking.isEnabled = false
        binding.pbIdCard.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                
                var idUrl: String? = null
                idCardUri?.let { uri ->
                    val bytes = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    if (bytes != null) {
                        val fileName = "id_${userId}_${UUID.randomUUID()}.jpg"
                        val bucket = supabase.storage.from("nid_birth_image")
                        bucket.upload(fileName, bytes)
                        idUrl = bucket.publicUrl(fileName)
                    }
                }

                val booking = SupabaseBooking(
                    user_id = userId,
                    user_name = name,
                    user_phone = binding.etBookingEmergencyPhone.text.toString(),
                    user_email = supabase.auth.currentUserOrNull()?.email,
                    property_id = propertyId!!,
                    property_name = property?.name ?: "Property",
                    seat_count = seatCount,
                    move_in_date = moveIn,
                    notes = binding.etBookingNotes.text.toString(),
                    property_owner_id = property?.owner_id,
                    
                    nid_birth_cert = nid,
                    id_card_url = idUrl,
                    guardian_name = binding.etBookingGuardian.text.toString(),
                    permanent_address = binding.etBookingPermanentAddress.text.toString(),
                    emergency_contact_name = binding.etBookingEmergencyName.text.toString(),
                    emergency_contact_relation = binding.etBookingEmergencyRelation.text.toString(),
                    emergency_contact_phone = binding.etBookingEmergencyPhone.text.toString(),
                    stay_duration = binding.etBookingDuration.text.toString(),
                    preferred_room_type = property?.room_type ?: "Any",
                    institution_workplace = binding.etBookingWorkplace.text.toString(),
                    gender = binding.spinnerBookingGender.selectedItem.toString(),
                    dietary_preference = binding.etBookingDietary.text.toString(),
                    smoking_habit = binding.spinnerSmoking.selectedItem.toString()
                )

                val result = AuthManager.safeAuthCall {
                    supabase.postgrest.from("bookings").insert(booking)
                }

                result.onSuccess {
                    Toast.makeText(this@BookingActivity, "Booking Request Sent!", Toast.LENGTH_LONG).show()
                    finish()
                }.onFailure { throw it }

            } catch (e: Exception) {
                Toast.makeText(this@BookingActivity, "Booking Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnConfirmBooking.isEnabled = true
                binding.pbIdCard.visibility = View.GONE
            }
        }
    }
}
