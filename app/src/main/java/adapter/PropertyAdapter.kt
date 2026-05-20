package adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.seatify.R
import com.android.seatify.databinding.ItemSeatBinding
import com.bumptech.glide.Glide
import model.SupabaseProperty
import activities.PropertyDetailActivity

class PropertyAdapter(private var properties: List<SupabaseProperty>) :
    RecyclerView.Adapter<PropertyAdapter.PropertyViewHolder>() {

    class PropertyViewHolder(val binding: ItemSeatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val binding = ItemSeatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PropertyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        val property = properties[position]
        holder.binding.apply {
            tvSeatTitle.text = property.name
            tvSeatLocation.text = property.address
            tvSeatPrice.text = "৳ ${property.price ?: "0"}"
            tvSeatsLeft.text = "${property.availableSeats} Left"
            
            if (!property.image_url.isNullOrEmpty()) {
                Glide.with(ivSeatImage.context)
                    .load(property.image_url)
                    .placeholder(R.drawable.bg_gradient)
                    .error(R.drawable.bg_gradient)
                    .centerCrop()
                    .into(ivSeatImage)
            } else {
                ivSeatImage.setImageResource(R.drawable.bg_gradient)
            }

            root.setOnClickListener {
                val intent = Intent(root.context, PropertyDetailActivity::class.java)
                intent.putExtra("PROPERTY_ID", property.id)
                intent.putExtra("IS_OWNER", false)
                root.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = properties.size

    fun updateData(newProperties: List<SupabaseProperty>) {
        properties = newProperties
        notifyDataSetChanged()
    }
}
