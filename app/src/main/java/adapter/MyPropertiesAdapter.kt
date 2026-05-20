package adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.seatify.databinding.ItemMyPropertyBinding
import com.bumptech.glide.Glide
import model.SupabaseProperty
import com.android.seatify.R

class MyPropertiesAdapter(
    var properties: List<SupabaseProperty>,
    private val onActionClick: (SupabaseProperty, String) -> Unit
) : RecyclerView.Adapter<MyPropertiesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMyPropertyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMyPropertyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val property = properties[position]
        holder.binding.apply {
            tvMyPropertyName.text = property.name
            tvMyPropertyInfo.text = "৳ ${property.price} | ${property.availableSeats} Seats Left"
            
            Glide.with(ivMyPropertyImage.context)
                .load(property.image_url)
                .placeholder(R.drawable.bg_gradient)
                .into(ivMyPropertyImage)

            btnEditProperty.setOnClickListener {
                onActionClick(property, "edit")
            }

            btnDeleteProperty.setOnClickListener {
                onActionClick(property, "delete")
            }
        }
    }

    override fun getItemCount(): Int = properties.size

    fun updateData(newProperties: List<SupabaseProperty>) {
        properties = newProperties
        notifyDataSetChanged()
    }
}
