package adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.seatify.R
import com.android.seatify.databinding.ItemImagePreviewBinding
import com.bumptech.glide.Glide

class ImagePreviewAdapter(
    private val images: MutableList<Any>, // Can be Uri (local) or String (remote URL)
    private val onImageClick: (Int) -> Unit,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<ImagePreviewAdapter.ViewHolder>() {

    var mainImageIndex = 0

    inner class ViewHolder(private val binding: ItemImagePreviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(image: Any, position: Int) {
            
            if (image is Uri) {
                binding.ivPreview.setImageURI(image)
            } else if (image is String) {
                Glide.with(binding.ivPreview.context)
                    .load(image)
                    .placeholder(R.drawable.bg_gradient)
                    .into(binding.ivPreview)
            }
            
            if (position == mainImageIndex) {
                binding.tvMainLabel.visibility = View.VISIBLE
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_start)
            } else {
                binding.tvMainLabel.visibility = View.GONE
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, android.R.color.transparent)
            }

            binding.root.setOnClickListener {
                mainImageIndex = position
                notifyDataSetChanged()
                onImageClick(position)
            }

            binding.btnRemoveImage.setOnClickListener {
                onRemoveClick(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImagePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position], position)
    }

    override fun getItemCount(): Int = images.size
}
