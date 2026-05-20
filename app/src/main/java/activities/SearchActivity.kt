package activities

import android.text.Editable
import android.text.TextWatcher
import adapter.PropertyAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.seatify.databinding.ActivitySearchBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import model.SupabaseProperty
import utils.AuthManager
import utils.SupabaseClient

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val supabase = SupabaseClient.client
    private lateinit var adapter: PropertyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnBackSearch.setOnClickListener { finish() }

        // Remove the Apply button as we are automating search
        binding.btnApplyFilters.visibility = View.GONE

        val autoSearchWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(binding.etSearchFull.text.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etSearchFull.addTextChangedListener(autoSearchWatcher)
        binding.etMinPrice.addTextChangedListener(autoSearchWatcher)
        binding.etMaxPrice.addTextChangedListener(autoSearchWatcher)

        binding.etSearchFull.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch(binding.etSearchFull.text.toString().trim())
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = PropertyAdapter(emptyList())
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = adapter
    }

    private fun performSearch(query: String) {
        val minPrice = binding.etMinPrice.text.toString().toLongOrNull()
        val maxPrice = binding.etMaxPrice.text.toString().toLongOrNull()

        setLoading(true)
        lifecycleScope.launch {
            val result = AuthManager.safeAuthCall {
                // Perform a case-insensitive search on 'name' OR 'address'
                supabase.postgrest.from("properties")
                    .select {
                        filter {
                            if (query.isNotEmpty()) {
                                or {
                                    ilike("name", "%$query%")
                                    ilike("address", "%$query%")
                                }
                            }
                            
                            // price is text in DB, so we need to handle it carefully
                            // Usually, you should store price as integer/numeric for range queries
                            // But here we'll filter locally for now if needed, 
                            // or try GTE/LTE if the string format allows it
                        }
                    }
                    .decodeList<SupabaseProperty>()
            }
            
            result.onSuccess { properties ->
                setLoading(false)
                
                // Client-side price filtering (since price is text in DB currently)
                val filteredProperties = properties.filter { property ->
                    val priceVal = property.price?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0L
                    val matchesMin = minPrice == null || priceVal >= minPrice
                    val matchesMax = maxPrice == null || priceVal <= maxPrice
                    matchesMin && matchesMax
                }

                adapter.updateData(filteredProperties)
                binding.tvNoResults.visibility = if (filteredProperties.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                setLoading(false)
                Toast.makeText(this@SearchActivity, "Search failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.pbSearch.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.rvSearchResults.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.tvNoResults.visibility = View.GONE
    }
}
