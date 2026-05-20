package activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.seatify.databinding.ActivityAdminBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import utils.SupabaseClient

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val supabase = SupabaseClient.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnManageHouse.setOnClickListener {
            Toast.makeText(this, "House Management coming soon", Toast.LENGTH_SHORT).show()
            // Intent to HouseManagementActivity
        }

        binding.btnManageMess.setOnClickListener {
            Toast.makeText(this, "Mess Management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnManageHostel.setOnClickListener {
            Toast.makeText(this, "Hostel Management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnManageRent.setOnClickListener {
            Toast.makeText(this, "Rent Management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnViewAnalytics.setOnClickListener {
            Toast.makeText(this, "Detailed Analytics coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.tvLogout.setOnClickListener {
            lifecycleScope.launch {
                supabase.auth.signOut()
                
                // Clear Remember Me preference on manual logout
                val sharedPreferences = getSharedPreferences("SeatifyPrefs", MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("remember_me", false).apply()

                startActivity(Intent(this@AdminActivity, LoginActivity::class.java))
                finishAffinity()
            }
        }
    }
}
