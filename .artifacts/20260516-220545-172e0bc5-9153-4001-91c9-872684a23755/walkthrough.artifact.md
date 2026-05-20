# Walkthrough: Google Login & Password Reset Fix

I have fixed the issues causing Google Login to hang and the Password Reset to fail with "localhost" errors.

## What I Fixed

1.  **Direct Login to Main Page**: Google Login now redirects to `seatify://activity_main`. Existing users are logged in immediately on the Main screen, and only new users are sent to the Registration page.
2.  **Password Reset Flow**: Standardized the reset link to `seatify://activity_reset_password`.
3.  **Localhost Removal**: Removed any dependencies on `localhost` in the code and provided the correct dashboard settings.
4.  **Deep Link Handling**: Added the necessary logic in `MainActivity` and `ResetPasswordActivity` to capture and verify Supabase sessions automatically.

## Files Updated

- [AndroidManifest.xml](file:///E:/Android/Seatify/app/src/main/AndroidManifest.xml): Added intent filters for `activity_main` and fixed `activity_reset_password`.
- [AuthManager.kt](file:///E:/Android/Seatify/app/src/main/java/utils/AuthManager.kt): Updated routing logic to support the new hosts.
- [SupabaseClient.kt](file:///E:/Android/Seatify/app/src/main/java/utils/SupabaseClient.kt): Updated default auth host.
- [LoginActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/LoginActivity.kt): Updated Google and Forgot Password redirect URLs.
- [MainActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/MainActivity.kt): Added session capture for Google redirects.
- [ResetPasswordActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/ResetPasswordActivity.kt): Fixed token handling and host check.

---

## Action Required: Supabase Dashboard Settings

To make these changes work on your phone, you **MUST** update your Supabase settings:

1.  Go to **Supabase Dashboard -> Authentication -> URL Configuration**.
2.  **Site URL**: Change it to `seatify://activity_main` (Remove `https://localhost`).
3.  **Redirect URLs**: Add these two (copy exactly):
    - `seatify://activity_main`
    - `seatify://activity_reset_password`

### Fixed Email Template
Copy this into **Supabase Dashboard -> Auth -> Email Templates -> Password Recovery**:

```html
<h2>Reset your password</h2>
<p>Hello,</p>
<p>We received a request to reset the password for your Seatify account.</p>
<p>Click the button below to set a new password. This link will expire in 1 hour.</p>

<p style="margin: 2em 0;">
  <a href="{{ .ConfirmationURL }}" style="background-color: #1a73e8; color: #ffffff; padding: 10px 20px; text-decoration: none; border-radius: 4px; font-weight: bold;">Reset your password</a>
</p>

<p>If the button doesn't work, copy and paste this link into your browser:</p>
<p><a href="{{ .ConfirmationURL }}">{{ .ConfirmationURL }}</a></p>

<p>If you did not request a password reset, please ignore this email.</p>
<hr>
<p><small>Seatify Support Team<br>Contact us at <a href="mailto:rsrakibulhasan62@gmail.com">support@seatify.com</a></small></p>
```
