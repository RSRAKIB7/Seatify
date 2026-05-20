package utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

object AuthManager {
    private const val TAG = "AuthManager"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 2000L

    /**
     * Executes an auth block with retry logic for network failures.
     */
    suspend fun <T> safeAuthCall(block: suspend () -> T): Result<T> {
        var currentAttempt = 0
        while (true) {
            try {
                return Result.success(block())
            } catch (e: IOException) {
                currentAttempt++
                if (currentAttempt >= MAX_RETRIES) {
                    return Result.failure(AuthException.NetworkError("Network issue. Please check your connection.", e))
                }
                delay(RETRY_DELAY)
            } catch (e: Exception) {
                Log.e(TAG, "Auth error: ${e.message}", e)
                return Result.failure(handleSupabaseError(e))
            }
        }
    }

    /**
     * Validates and handles deep links for auth redirects.
     * Prevents localhost redirects and ensures valid host routing.
     */
    fun validateAndRouteDeepLink(uri: Uri?): AuthRedirect {
        if (uri == null) return AuthRedirect.None

        val scheme = uri.scheme
        val host = uri.host

        // Check for localhost or invalid schemes
        if (host == "localhost" || host == "127.0.0.1") {
            Log.w(TAG, "Blocked localhost redirect: $uri")
            return AuthRedirect.Invalid("Localhost redirects are not allowed for security.")
        }

        if (scheme != "seatify") {
            Log.w(TAG, "Invalid scheme: $scheme")
            return AuthRedirect.Invalid("Invalid deep link scheme.")
        }

        return when (host) {
            "activity_main" -> AuthRedirect.Main
            "activity_register" -> AuthRedirect.Register
            "activity_reset_password" -> AuthRedirect.ResetPassword
            else -> {
                Log.w(TAG, "Unknown host in deep link: $host")
                AuthRedirect.UnknownHost(host ?: "missing")
            }
        }
    }

    /**
     * Ensures the session is fresh by re-fetching if necessary.
     */
    suspend fun refreshSession(supabase: SupabaseClient): Boolean {
        return try {
            val status = supabase.auth.sessionStatus.first()
            if (status is SessionStatus.Authenticated) {
                // Trigger a refresh or just verify current session
                supabase.auth.retrieveUserForCurrentSession(updateSession = true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh session", e)
            false
        }
    }

    private fun handleSupabaseError(e: Exception): AuthException {
        val message = e.localizedMessage ?: "An unknown error occurred"
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) -> 
                AuthException.InvalidCredentials("Invalid email or password.")
            message.contains("Email not confirmed", ignoreCase = true) -> 
                AuthException.EmailNotVerified("Please verify your email address before logging in.")
            message.contains("User already registered", ignoreCase = true) -> 
                AuthException.UserAlreadyExists("An account with this email already exists.")
            message.contains("Token has expired", ignoreCase = true) -> 
                AuthException.TokenExpired("Your session has expired. Please log in again.")
            else -> AuthException.Unknown(message, e)
        }
    }

    sealed class AuthRedirect {
        object Main : AuthRedirect()
        object LoginCallback : AuthRedirect()
        object Register : AuthRedirect()
        object ResetPassword : AuthRedirect()
        object None : AuthRedirect()
        data class Invalid(val error: String) : AuthRedirect()
        data class UnknownHost(val host: String) : AuthRedirect()
    }

    sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class NetworkError(message: String, cause: Throwable) : AuthException(message, cause)
        class InvalidCredentials(message: String) : AuthException(message)
        class EmailNotVerified(message: String) : AuthException(message)
        class UserAlreadyExists(message: String) : AuthException(message)
        class TokenExpired(message: String) : AuthException(message)
        class Unknown(message: String, cause: Throwable) : AuthException(message, cause)
    }
}
