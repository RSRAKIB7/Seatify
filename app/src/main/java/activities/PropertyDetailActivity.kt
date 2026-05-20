package activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.R
import com.android.seatify.databinding.ActivityPropertyDetailBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseProperty
import utils.AuthManager
import utils.SupabaseClient

class PropertyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPropertyDetailBinding
    private var propertyId: String? = null
    private var isOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPropertyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        propertyId = intent.getStringExtra("PROPERTY_ID")
        isOwner = intent.getBooleanExtra("IS_OWNER", false)

        if (propertyId != null) {
            fetchPropertyDetails(propertyId!!)
        } else {
            Toast.makeText(this, "Property not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnDeletePropertyDetail.visibility = if (isOwner) View.VISIBLE else View.GONE
        binding.btnDeletePropertyDetail.setOnClickListener {
            showDeleteConfirmation()
        }
        
        binding.btnBookNow.visibility = if (isOwner) View.GONE else View.VISIBLE
        binding.btnBookNow.setOnClickListener {
            val intent = Intent(this, BookingActivity::class.java)
            intent.putExtra("PROPERTY_ID", propertyId)
            startActivity(intent)
        }
    }

    private fun fetchPropertyDetails(id: String) {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                SupabaseClient.client.postgrest.from("properties")
                    .select { filter { eq("id", id) } }
                    .decodeSingle<SupabaseProperty>()
            }
            
            result.onSuccess { property ->
                withContext(Dispatchers.Main) {
                    populateDetails(property)
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PropertyDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateDetails(property: SupabaseProperty) {
        binding.tvDetailName.text = property.name
        binding.tvDetailLocation.text = property.address
        binding.tvDetailSeats.text = "${property.availableSeats} Seats Available"
        binding.tvDetailPrice.text = "৳ ${property.price ?: "0"}/month"
        binding.tvDetailDescription.text = property.description ?: "No description"
        binding.tvDetailFullAddress.text = property.address
        binding.tvDetailContact.text = property.contact_number ?: "Not provided"

        binding.tvDetailContact.setOnClickListener {
            property.contact_number?.let { num ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$num")
                startActivity(intent)
            }
        }

        // Show multiple images: For now, we load main image.
        // For a full implementation, adding a ViewPager2/Carousel would be better.
        // Let's implement a simple click to toggle through images if other_images exist.
        val allImages = mutableListOf<String>()
        allImages.add(property.image_url)
        property.other_images?.let { allImages.addAll(it) }

        var currentImgIndex = 0
        
        fun updateImage() {
            Glide.with(this)
                .load(allImages[currentImgIndex])
                .placeholder(R.drawable.bg_gradient)
                .into(binding.ivPropertyDetail)
            
            if (allImages.size > 1) {
                Toast.makeText(this, "Showing image ${currentImgIndex + 1} of ${allImages.size}. Tap image to see next.", Toast.LENGTH_SHORT).show()
            }
        }

        updateImage()

        binding.ivPropertyDetail.setOnClickListener {
            if (allImages.size > 1) {
                currentImgIndex = (currentImgIndex + 1) % allImages.size
                updateImage()
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to delete this listing?")
            .setPositiveButton("Delete") { _, _ -> deleteProperty() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProperty() {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                SupabaseClient.client.postgrest.from("properties").delete {
                    filter { eq("id", propertyId!!) }
                }
            }
            result.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PropertyDetailActivity, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PropertyDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
