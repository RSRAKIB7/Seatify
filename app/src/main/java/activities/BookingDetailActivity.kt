package activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityBookingDetailBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseBooking
import model.SupabaseProperty
import utils.AuthManager
import utils.SupabaseClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class BookingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingDetailBinding
    private var booking: SupabaseBooking? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookingJson = intent.getStringExtra("BOOKING_JSON")
        if (bookingJson != null) {
            booking = Json.decodeFromString<SupabaseBooking>(bookingJson)
            displayBookingDetails()
        } else {
            Toast.makeText(this, "Error: Booking data missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnAcceptDetail.setOnClickListener { updateStatus("accepted") }
        binding.btnRejectDetail.setOnClickListener { updateStatus("rejected") }
        binding.btnCompleteDetail.setOnClickListener { showCompleteConfirmation() }
    }

    private fun displayBookingDetails() {
        booking?.let { b ->
            binding.tvDetailUserName.text = "Name: ${b.user_name}"
            binding.tvDetailUserPhone.text = "Phone: ${b.user_phone}"
            binding.tvDetailUserNid.text = "NID: ${b.nid_birth_cert ?: "N/A"}"
            binding.tvDetailUserAddress.text = "Address: ${b.permanent_address ?: "N/A"}"
            
            binding.tvDetailMoveIn.text = "Move-in: ${b.move_in_date}"
            binding.tvDetailDuration.text = "Duration: ${b.stay_duration ?: "N/A"}"
            binding.tvDetailRoomType.text = "Room: ${b.preferred_room_type ?: "Default"}"
            binding.tvDetailSeats.text = "Seats requested: ${b.seat_count}"
            
            binding.tvDetailGender.text = "Gender: ${b.gender ?: "N/A"}"
            binding.tvDetailSmoking.text = "Smoking: ${b.smoking_habit ?: "N/A"}"
            binding.tvDetailDietary.text = "Dietary: ${b.dietary_preference ?: "N/A"}"
            binding.tvDetailNotes.text = "Notes: ${b.notes ?: "None"}"

            if (!b.id_card_url.isNullOrEmpty()) {
                Glide.with(this)
                    .load(b.id_card_url)
                    .placeholder(R.drawable.bg_gradient)
                    .into(binding.ivDetailIdCard)
            }

            binding.tvDetailStatusFinal.text = "Current Status: ${b.status.replaceFirstChar { it.uppercase() }}"
        }
    }

    private fun showCompleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Complete Booking")
            .setMessage("The booking notification will be deleted and property seats will be reduced. If seats reach zero, the listing will be removed. Are you sure?")
            .setPositiveButton("Yes") { _, _ -> completeBooking() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun completeBooking() {
        val b = booking ?: return
        lifecycleScope.launch {
            try {
                // 1. Fetch current property seat count
                val propResult = AuthManager.safeAuthCall {
                    SupabaseClient.client.postgrest.from("properties")
                        .select { filter { eq("id", b.property_id) } }
                        .decodeSingle<SupabaseProperty>()
                }

                propResult.onSuccess { property ->
                    val newSeats = (property.availableSeats - b.seat_count).coerceAtLeast(0)
                    
                    if (newSeats == 0) {
                        // 2a. Delete property listing if zero seats remaining
                        val deletePropResult = AuthManager.safeAuthCall {
                            SupabaseClient.client.postgrest.from("properties").delete {
                                filter { eq("id", property.id!!) }
                            }
                        }
                        
                        deletePropResult.onSuccess {
                            finalizeBookingCompletion(b.id!!)
                        }.onFailure { throw it }
                        
                    } else {
                        // 2b. Update property seats if still available
                        val updatePropResult = AuthManager.safeAuthCall {
                            SupabaseClient.client.postgrest.from("properties")
                                .update(mapOf("availableSeats" to newSeats)) {
                                    filter { eq("id", property.id!!) }
                                }
                        }

                        updatePropResult.onSuccess {
                            finalizeBookingCompletion(b.id!!)
                        }.onFailure { throw it }
                    }
                }.onFailure { throw it }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BookingDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun finalizeBookingCompletion(bookingId: String) {
        // Delete booking notification
        val deleteBookingResult = AuthManager.safeAuthCall {
            SupabaseClient.client.postgrest.from("bookings").delete {
                filter { eq("id", bookingId) }
            }
        }

        deleteBookingResult.onSuccess {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BookingDetailActivity, "Booking Completed successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
        }.onFailure { throw it }
    }

    private fun updateStatus(status: String) {
        val bookingId = booking?.id ?: return
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                SupabaseClient.client.postgrest.from("bookings").update(
                    mapOf("status" to status)
                ) {
                    filter { eq("id", bookingId) }
                }
            }

            result.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BookingDetailActivity, "Booking $status", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BookingDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
