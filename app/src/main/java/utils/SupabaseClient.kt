package utils

import android.content.Context
import com.android.seatify.R
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

object SupabaseClient {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: io.github.jan.supabase.SupabaseClient by lazy {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("SupabaseClient not initialized. Call init(context) in Application class.")
        }
        
        val url = appContext.getString(R.string.supabase_url)
        val key = appContext.getString(R.string.supabase_key)
        
        createSupabaseClient(url, key) {
            httpEngine = Android.create()
            install(Postgrest)
            install(Storage)
            install(Auth) {
                scheme = "seatify"
                host = "activity_main"
            }
        }
    }
}
