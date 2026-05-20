package activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.seatify.databinding.ActivityAboutUsBinding

class AboutUsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutUsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAbout.setNavigationOnClickListener { finish() }
    }
}
