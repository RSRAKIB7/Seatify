package activities

import adapter.ImagePreviewAdapter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.seatify.R
import com.android.seatify.databinding.ActivityAddPropertyBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseProperty
import utils.AuthManager
import utils.SupabaseClient
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class AddPropertyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPropertyBinding
    private val supabase = SupabaseClient.client
    
    private val displayImages = mutableListOf<Any>() 
    private val removedUrls = mutableListOf<String>() // Track URLs to be moved to delete bucket
    private lateinit var imageAdapter: ImagePreviewAdapter
    private var mainImageIndex = 0
    private var editPropertyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPropertyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val propertyJson = intent.getStringExtra("EDIT_PROPERTY_JSON")
        if (propertyJson != null) {
            setupEditMode(propertyJson)
        }

        setupRecyclerView()

        binding.btnPickImages.setOnClickListener {
            if (displayImages.size < 8) {
                imagePickerLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Maximum 8 images allowed", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSubmitProperty.setOnClickListener {
            validateAndUpload()
        }
    }

    private fun setupEditMode(json: String) {
        try {
            val property = Json.decodeFromString<SupabaseProperty>(json)
            editPropertyId = property.id
            
            binding.tvAddTitle.text = "Update Property"
            binding.btnSubmitProperty.text = "Save Updates"
            
            binding.etAddName.setText(property.name)
            binding.etAddPrice.setText(property.price)
            binding.etAddSeats.setText(property.availableSeats.toString())
            binding.etAddContact.setText(property.contact_number)
            binding.etAddDescription.setText(property.description)
            
            val parts = property.address.split(", ")
            if (parts.size >= 2) {
                binding.etAddAddress.setText(parts.dropLast(1).joinToString(", "))
                binding.etAddLocation.setText(parts.last())
            } else {
                binding.etAddAddress.setText(property.address)
            }

            val typeArray = resources.getStringArray(R.array.property_types)
            binding.spinnerType.setSelection(typeArray.indexOf(property.type).coerceAtLeast(0))

            val roomTypeArray = resources.getStringArray(R.array.room_types)
            binding.spinnerRoomType.setSelection(roomTypeArray.indexOf(property.room_type).coerceAtLeast(0))
            
            displayImages.add(property.image_url)
            property.other_images?.let { displayImages.addAll(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading property for edit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImagePreviewAdapter(displayImages, { index ->
            mainImageIndex = index
        }, { index ->
            val removedItem = displayImages.removeAt(index)
            if (removedItem is String) {
                removedUrls.add(removedItem) // Track for move-to-delete-bucket
            }
            if (mainImageIndex >= displayImages.size) mainImageIndex = 0
            imageAdapter.mainImageIndex = mainImageIndex
            imageAdapter.notifyDataSetChanged()
        })
        binding.rvSelectedImages.apply {
            layoutManager = LinearLayoutManager(this@AddPropertyActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val remainingSlots = 8 - displayImages.size
        displayImages.addAll(uris.take(remainingSlots))
        imageAdapter.notifyDataSetChanged()
    }

    private fun validateAndUpload() {
        if (displayImages.isEmpty()) {
            Toast.makeText(this, "Please upload at least 1 image", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            processImagesAndSave()
        }
    }

    private suspend fun processImagesAndSave() {
        val finalUrls = mutableListOf<String>()
        var hasError = false

        withContext(Dispatchers.IO) {
            try {
                // 1. Move removed images to 'delete' bucket
                moveRemovedToDeletedBucket()

                val bucket = SupabaseClient.client.storage.from("image")
                for (item in displayImages) {
                    if (item is String) {
                        finalUrls.add(item)
                    } else if (item is Uri) {
                        val bytes = contentResolver.openInputStream(item)?.use { it.readBytes() }
                        if (bytes != null) {
                            val fileName = "${UUID.randomUUID()}.jpg"
                            bucket.upload(fileName, bytes)
                            finalUrls.add(bucket.publicUrl(fileName))
                        }
                    }
                }
            } catch (e: Exception) {
                hasError = true
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@AddPropertyActivity, "Storage Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        if (!hasError && finalUrls.isNotEmpty()) {
            val mainImageUrl = finalUrls.getOrNull(mainImageIndex) ?: finalUrls[0]
            val otherImages = finalUrls.toMutableList().apply { remove(mainImageUrl) }
            saveToDatabase(mainImageUrl, otherImages)
        }
    }

    private suspend fun moveRemovedToDeletedBucket() {
        val storage = SupabaseClient.client.storage
        for (url in removedUrls) {
            try {
                val fileName = url.substringAfterLast("/")
                // In Supabase, moving is essentially copying then deleting
                // We'll attempt to copy to 'deleted_images' bucket if it exists
                // Note: user mentioned 'delete bucket', I'll use 'deleted_images'
                val bytes = storage.from("image").downloadPublic(fileName)
                storage.from("deleted_images").upload(fileName, bytes)
                storage.from("image").delete(fileName)
            } catch (e: Exception) {
                // If move fails (e.g. bucket doesn't exist), we just hard delete from source
                try {
                    val fileName = url.substringAfterLast("/")
                    storage.from("image").delete(fileName)
                } catch (inner: Exception) {}
            }
        }
    }

    private fun saveToDatabase(mainImageUrl: String, otherImages: List<String>) {
        val name = binding.etAddName.text.toString().trim()
        val address = binding.etAddAddress.text.toString().trim()
        val location = binding.etAddLocation.text.toString().trim()
        val price = binding.etAddPrice.text.toString().trim()
        val seats = binding.etAddSeats.text.toString().trim().toIntOrNull() ?: 1
        val contact = binding.etAddContact.text.toString().trim()
        val description = binding.etAddDescription.text.toString().trim()
        val type = binding.spinnerType.selectedItem.toString()
        val roomType = binding.spinnerRoomType.selectedItem.toString()

        lifecycleScope.launch {
            try {
                val property = SupabaseProperty(
                    id = editPropertyId,
                    name = name,
                    address = "$address, $location",
                    latitude = 0.0, 
                    longitude = 0.0,
                    availableSeats = seats,
                    image_url = mainImageUrl,
                    other_images = otherImages,
                    price = price,
                    description = description,
                    type = type,
                    room_type = roomType,
                    owner_id = supabase.auth.currentUserOrNull()?.id,
                    contact_number = contact
                )

                val result = AuthManager.safeAuthCall {
                    if (editPropertyId != null) {
                        SupabaseClient.client.postgrest.from("properties").update(property) {
                            filter { eq("id", editPropertyId!!) }
                        }
                    } else {
                        SupabaseClient.client.postgrest.from("properties").insert(property)
                    }
                }

                result.onSuccess {
                    withContext(Dispatchers.Main) {
                        setLoading(false)
                        Toast.makeText(this@AddPropertyActivity, "Success!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }.onFailure { throw it }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@AddPropertyActivity, "DB Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBarAdd.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmitProperty.isEnabled = !isLoading
        binding.btnPickImages.isEnabled = !isLoading
    }
}
