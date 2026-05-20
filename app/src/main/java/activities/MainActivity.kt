package activities

import adapter.PropertyAdapter
import adapter.MyPropertiesAdapter
import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.seatify.R
import com.android.seatify.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import model.UserProfile
import model.SupabaseProperty
import utils.SupabaseClient
import utils.AuthManager
import android.net.Uri
import io.github.jan.supabase.auth.parseSessionFromUrl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val supabase = SupabaseClient.client
    private lateinit var propertyAdapter: PropertyAdapter
    private lateinit var searchAdapter: PropertyAdapter
    private lateinit var myListingsAdapter: MyPropertiesAdapter
    private var allProperties = listOf<SupabaseProperty>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!isInternetAvailable()) {
            Toast.makeText(this, "No internet access. Closing app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupNavigation()
        setupClickListeners()
        setupSwipeRefresh()
        setupDarkMode()
        setupSearch()
        
        val targetTab = intent.getIntExtra("TARGET_TAB", R.id.nav_home)
        if (targetTab == R.id.nav_profile) {
            binding.bottomNavigation.selectedItemId = R.id.nav_profile
            showView(binding.profileView)
        }
        
        loadUserProfile()
        fetchProperties()
        fetchMyListings()

        binding.fabAdd.show()

        // Handle Deep Link for Google Login Redirect
        intent?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        
        when (val route = AuthManager.validateAndRouteDeepLink(uri)) {
            is AuthManager.AuthRedirect.Main -> {
                lifecycleScope.launch {
                    try {
                        // Import session from the deep link
                        val session = supabase.auth.parseSessionFromUrl(uri.toString())
                        supabase.auth.importSession(session)
                        
                        val user = supabase.auth.currentUserOrNull() ?: return@launch
                        
                        // Check if user has a profile
                        val existingProfile = AuthManager.safeAuthCall {
                            supabase.postgrest.from("users")
                                .select { filter { eq("id", user.id) } }
                                .decodeList<UserProfile>()
                        }

                        if (existingProfile.getOrNull()?.isEmpty() == true) {
                            // New user: Redirect to register
                            val regIntent = Intent(this@MainActivity, RegisterActivity::class.java).apply {
                                data = uri
                            }
                            startActivity(regIntent)
                            finish()
                        } else {
                            // Existing user: Refresh UI
                            loadUserProfile()
                            Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        // Silent fail for non-auth links or simple logging
                    }
                }
            }
            else -> {
                // Ignore links meant for LoginActivity or ResetPasswordActivity
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
        fetchMyListings()
    }

    private fun loadUserProfile() {
        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            lifecycleScope.launch {
                try {
                    val profile = supabase.postgrest.from("users")
                        .select { filter { eq("id", user.id) } }
                        .decodeSingle<UserProfile>()
                    
                    binding.tvWelcomeName.text = "Hello, ${profile.name}!"
                    binding.tvProfileName.text = profile.name
                    
                    if (!profile.profile_picture_url.isNullOrEmpty()) {
                        Glide.with(this@MainActivity)
                            .load(profile.profile_picture_url)
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(binding.ivProfilePicMain)
                        
                        binding.ivProfilePicMain.imageTintList = null
                        binding.ivProfilePicMain.setPadding(0, 0, 0, 0)
                    } else {
                        binding.ivProfilePicMain.setImageResource(R.drawable.ic_person)
                    }

                    if (profile.role.lowercase() == "admin") {
                        binding.tvAdminPanelBtn.visibility = View.VISIBLE
                    } else {
                        binding.tvAdminPanelBtn.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    binding.tvWelcomeName.text = "Hello!"
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterProperties(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterProperties(query: String) {
        val filtered = if (query.isEmpty()) {
            allProperties
        } else {
            allProperties.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.address.contains(query, ignoreCase = true) 
            }
        }
        propertyAdapter.updateData(filtered)
        searchAdapter.updateData(filtered)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun setupRecyclerView() {
        propertyAdapter = PropertyAdapter(emptyList<SupabaseProperty>())
        binding.rvProperties.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = propertyAdapter
        }

        searchAdapter = PropertyAdapter(emptyList<SupabaseProperty>())
        binding.rvSearchResultsMain.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchAdapter
        }

        myListingsAdapter = MyPropertiesAdapter(emptyList()) { property, action ->
            when (action) {
                "edit" -> {
                    val intent = Intent(this, AddPropertyActivity::class.java)
                    intent.putExtra("EDIT_PROPERTY_JSON", Json.encodeToString(property))
                    startActivity(intent)
                }
                "delete" -> {
                    deleteProperty(property)
                }
            }
        }
        binding.rvMyListingsTab.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = myListingsAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            fetchProperties()
            loadUserProfile()
            fetchMyListings()
        }
    }

    private fun fetchProperties() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val properties = SupabaseClient.client.postgrest.from("properties")
                    .select()
                    .decodeList<SupabaseProperty>()
                
                allProperties = properties
                propertyAdapter.updateData(properties)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun fetchMyListings() {
        val user = supabase.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            try {
                val results = supabase.postgrest.from("properties")
                    .select { filter { eq("owner_id", user.id) } }
                    .decodeList<SupabaseProperty>()
                
                myListingsAdapter.updateData(results)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.fabAdd.show()
            
            when (item.itemId) {
                R.id.nav_home -> {
                    showView(binding.swipeRefresh)
                    true
                }
                R.id.nav_search -> {
                    showView(binding.searchView)
                    true
                }
                R.id.nav_listings -> {
                    showView(binding.listingsView)
                    true
                }
                R.id.nav_profile -> {
                    showView(binding.profileView)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddPropertyActivity::class.java))
        }

        binding.tvMyProfileBtn.setOnClickListener {
            startActivity(Intent(this, MyProfileActivity::class.java))
        }

        binding.tvMyBookingsBtn.setOnClickListener {
            val intent = Intent(this, ManageBookingsActivity::class.java)
            intent.putExtra("IS_OWNER_VIEW", false)
            startActivity(intent)
        }

        binding.tvMyListingsBtn.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_listings
        }

        binding.tvManageBookingsBtn.setOnClickListener {
            val intent = Intent(this, ManageBookingsActivity::class.java)
            intent.putExtra("IS_OWNER_VIEW", true)
            startActivity(intent)
        }

        binding.tvAboutUsBtn.setOnClickListener {
            startActivity(Intent(this, AboutUsActivity::class.java))
        }

        binding.tvSettingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tvLogoutBtn.setOnClickListener {
            logoutUser()
        }

        binding.tvAdminPanelBtn.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }

    private fun setupDarkMode() {
        val sharedPref = getSharedPreferences("SeatifyPrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun showView(viewToShow: View) {
        binding.swipeRefresh.visibility = View.GONE
        binding.searchView.visibility = View.GONE
        binding.listingsView.visibility = View.GONE
        binding.profileView.visibility = View.GONE
        
        viewToShow.visibility = View.VISIBLE
    }

    private fun deleteProperty(property: SupabaseProperty) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to delete this property?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        supabase.postgrest.from("properties").delete {
                            filter { eq("id", property.id!!) }
                        }
                        fetchMyListings()
                        fetchProperties()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun logoutUser() {
        lifecycleScope.launch {
            supabase.auth.signOut()
            val sharedPreferences = getSharedPreferences("SeatifyPrefs", MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("remember_me", false).apply()
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finishAffinity()
        }
    }
}
