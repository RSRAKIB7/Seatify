package activities

import adapter.ManageBookingsAdapter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.seatify.databinding.ActivityManageBookingsBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseBooking
import utils.AuthManager
import utils.SupabaseClient
import com.google.android.material.tabs.TabLayout

class ManageBookingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBookingsBinding
    private val supabase = SupabaseClient.client
    private val bookingList = mutableListOf<SupabaseBooking>()
    private lateinit var adapter: ManageBookingsAdapter
    private var isOwnerView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isOwnerView = intent.getBooleanExtra("IS_OWNER_VIEW", false)
        if (isOwnerView) {
            binding.tabLayoutBookings.getTabAt(1)?.select()
        }

        setupRecyclerView()
        setupTabs()
        fetchBookings()

        binding.toolbarManage.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabLayoutBookings.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isOwnerView = tab?.position == 1
                setupRecyclerView() // Re-setup to update isOwnerView in adapter
                fetchBookings()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        fetchBookings()
    }

    private fun setupRecyclerView() {
        adapter = ManageBookingsAdapter(bookingList, isOwnerView) { booking, action ->
            when (action) {
                "cancel" -> showCancelConfirm(booking)
                else -> updateBookingStatus(booking, action)
            }
        }
        binding.rvManageBookings.layoutManager = LinearLayoutManager(this)
        binding.rvManageBookings.adapter = adapter
    }

    private fun fetchBookings() {
        val user = supabase.auth.currentUserOrNull() ?: return
        binding.pbManageBookings.visibility = View.VISIBLE
        binding.tvEmptyBookings.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val column = if (isOwnerView) "property_owner_id" else "user_id"
                
                val results = supabase.postgrest.from("bookings")
                    .select {
                        filter {
                            eq(column, user.id)
                        }
                    }
                    .decodeList<SupabaseBooking>()
                
                withContext(Dispatchers.Main) {
                    bookingList.clear()
                    bookingList.addAll(results)
                    adapter.notifyDataSetChanged()
                    binding.pbManageBookings.visibility = View.GONE
                    if (bookingList.isEmpty()) {
                        binding.tvEmptyBookings.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbManageBookings.visibility = View.GONE
                    Toast.makeText(this@ManageBookingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCancelConfirm(booking: SupabaseBooking) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Booking")
            .setMessage("Are you sure you want to cancel this booking request? It will be removed for both you and the owner.")
            .setPositiveButton("Yes, Cancel") { _, _ -> deleteBooking(booking) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteBooking(booking: SupabaseBooking) {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.postgrest.from("bookings").delete {
                    filter { eq("id", booking.id!!) }
                }
            }
            result.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageBookingsActivity, "Booking Cancelled", Toast.LENGTH_SHORT).show()
                    fetchBookings()
                }
            }.onFailure { e ->
                Toast.makeText(this@ManageBookingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBookingStatus(booking: SupabaseBooking, status: String) {
        lifecycleScope.launch {
            try {
                val result = AuthManager.safeAuthCall {
                    supabase.postgrest.from("bookings").update(
                        mapOf("status" to status)
                    ) {
                        filter { eq("id", booking.id!!) }
                    }
                }
                
                result.onSuccess {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ManageBookingsActivity, "Status updated to $status", Toast.LENGTH_SHORT).show()
                        fetchBookings()
                    }
                }.onFailure { throw it }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageBookingsActivity, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
