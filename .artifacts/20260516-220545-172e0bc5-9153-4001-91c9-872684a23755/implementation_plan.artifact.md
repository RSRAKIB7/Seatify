# Comprehensive Fix: Google Login & Password Reset

This plan fixes the "forever processing" Google Login, the "localhost" error, and the broken password reset flow.

## Supabase Dashboard Configuration (CRITICAL)

To make these changes work, you **MUST** update your Supabase settings in the dashboard (**Authentication -> URL Configuration**):

1.  **Site URL**: `seatify://activity_main` (Change this from `https://localhost`)
2.  **Redirect URLs**:
    - `seatify://activity_main`
    - `seatify://activity_reset_password`

## Proposed Changes

### Android Configuration

#### [AndroidManifest.xml](file:///E:/Android/Seatify/app/src/main/AndroidManifest.xml)

- Add a deep link handler for `MainActivity` to receive Google Login redirects.
- Fix/Update `ResetPasswordActivity` intent filter to use `activity_reset_password`.

```xml
        <!-- MainActivity Deep Link -->
        <activity
            android:name="activities.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="seatify" android:host="activity_main" />
            </intent-filter>
        </activity>

        <!-- ResetPasswordActivity Deep Link -->
        <activity
            android:name="activities.ResetPasswordActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="seatify" android:host="activity_reset_password" />
            </intent-filter>
        </activity>
```

---

### Core Logic & Utils

#### [AuthManager.kt](file:///E:/Android/Seatify/app/src/main/java/utils/AuthManager.kt)

- **Fix Google Login Hang**: Ensure the `AuthRedirect` logic recognizes `activity_main`.
- Update host validation to allow the new schemes.

#### [SupabaseClient.kt](file:///E:/Android/Seatify/app/src/main/java/utils/SupabaseClient.kt)

- Update the default auth scheme and host to match the new configuration.

```kotlin
            install(Auth) {
                scheme = "seatify"
                host = "activity_main" // Changed from activity_register
            }
```

---

### UI & Activity Logic

#### [LoginActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/LoginActivity.kt)

- **Fix Google Login**: Change `signInWithGoogle` redirect URL to `seatify://activity_main`.
- **Fix Forgot Password**: Use `seatify://activity_reset_password` as the redirect.
- **Improved Deep Link Handling**: Properly route the incoming links.

#### [MainActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/MainActivity.kt)

- **Session Handling**: Automatically capture the session from the Google redirect.
- **Smart Routing**: If it's a new user (no profile), send them to `RegisterActivity`. Otherwise, stay on `MainActivity`.

#### [ResetPasswordActivity.kt](file:///E:/Android/Seatify/app/src/main/java/activities/ResetPasswordActivity.kt)

- Extract tokens from the URL correctly.
- Implement `supabase.auth.setSession` before calling `updateUser`.

---

### Supabase Email Template (Full Fixed Code)

Replace your **Password Recovery** template in Supabase Dashboard with this:

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
