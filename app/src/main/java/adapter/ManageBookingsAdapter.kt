package adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.seatify.databinding.ItemManageBookingBinding
import model.SupabaseBooking
import activities.BookingDetailActivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ManageBookingsAdapter(
    private val bookings: List<SupabaseBooking>,
    private val isOwnerView: Boolean,
    private val onActionClick: (SupabaseBooking, String) -> Unit
) : RecyclerView.Adapter<ManageBookingsAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemManageBookingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(booking: SupabaseBooking) {
            binding.tvBookingUserName.text = if (isOwnerView) booking.user_name else "To: ${booking.property_name}"
            binding.tvBookingPropertyName.text = if (isOwnerView) booking.property_name else "Booking Request"
            binding.tvBookingInfo.text = "Seats: ${booking.seat_count} | Date: ${booking.move_in_date}"
            binding.tvBookingPhone.text = "Status: ${booking.status.replaceFirstChar { it.uppercase() }}"
            
            if (isOwnerView) {
                // Owner View: Show Accept/Reject
                binding.layoutBookingActions.visibility = View.VISIBLE
                binding.tvBookingStatus.visibility = View.VISIBLE
                binding.tvBookingStatus.text = "Current Status: ${booking.status.replaceFirstChar { it.uppercase() }}"
                
                binding.btnAcceptBooking.text = "Accept"
                binding.btnAcceptBooking.setOnClickListener { onActionClick(booking, "accepted") }
                
                binding.btnDenyBooking.text = "Reject"
                binding.btnDenyBooking.setOnClickListener { onActionClick(booking, "rejected") }
            } else {
                // Tenant View: Show Cancel option
                binding.layoutBookingActions.visibility = View.VISIBLE
                binding.tvBookingStatus.visibility = View.VISIBLE
                binding.tvBookingStatus.text = "My Status: ${booking.status.replaceFirstChar { it.uppercase() }}"
                
                binding.btnAcceptBooking.visibility = View.GONE // Hide Accept
                binding.btnDenyBooking.text = "Cancel Request"
                binding.btnDenyBooking.setOnClickListener { onActionClick(booking, "cancel") }
            }

            binding.root.setOnClickListener {
                val intent = Intent(binding.root.context, BookingDetailActivity::class.java)
                intent.putExtra("BOOKING_JSON", Json.encodeToString(booking))
                binding.root.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(bookings[position])
    }

    override fun getItemCount(): Int = bookings.size
}
