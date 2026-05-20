package activities

import adapter.MyPropertiesAdapter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.seatify.databinding.ActivityMyPropertiesBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SupabaseProperty
import utils.AuthManager
import utils.SupabaseClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MyPropertiesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyPropertiesBinding
    private val supabase = SupabaseClient.client
    private val propertiesList = mutableListOf<SupabaseProperty>()
    private lateinit var adapter: MyPropertiesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyPropertiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchMyProperties()

        binding.toolbarMyProperties.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        fetchMyProperties()
    }

    private fun setupRecyclerView() {
        adapter = MyPropertiesAdapter(propertiesList) { property, action ->
            when (action) {
                "edit" -> {
                    val intent = android.content.Intent(this, AddPropertyActivity::class.java)
                    intent.putExtra("EDIT_PROPERTY_JSON", Json.encodeToString(property))
                    startActivity(intent)
                }
                "delete" -> {
                    showDeleteConfirm(property)
                }
            }
        }
        binding.rvMyProperties.layoutManager = LinearLayoutManager(this)
        binding.rvMyProperties.adapter = adapter
    }

    private fun fetchMyProperties() {
        val user = supabase.auth.currentUserOrNull() ?: return
        binding.pbMyProperties.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.postgrest.from("properties")
                    .select { filter { eq("owner_id", user.id) } }
                    .decodeList<SupabaseProperty>()
            }
            
            result.onSuccess { list ->
                propertiesList.clear()
                propertiesList.addAll(list)
                withContext(Dispatchers.Main) {
                    adapter.notifyDataSetChanged()
                    binding.pbMyProperties.visibility = View.GONE
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    binding.pbMyProperties.visibility = View.GONE
                    Toast.makeText(this@MyPropertiesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirm(property: SupabaseProperty) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to permanently delete this property?")
            .setPositiveButton("Delete") { _, _ -> deleteProperty(property) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProperty(property: SupabaseProperty) {
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                supabase.postgrest.from("properties").delete {
                    filter { eq("id", property.id!!) }
                }
            }
            result.onSuccess {
                fetchMyProperties()
            }.onFailure { e ->
                Toast.makeText(this@MyPropertiesActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
