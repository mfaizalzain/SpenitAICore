package com.fmz.spenitaicore.data.auth

import android.content.Context
import android.content.Intent
import android.accounts.Account
import android.accounts.AccountManager
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject
import java.util.UUID

data class GoogleSignInResult(
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val idToken: String?
)

data class PasskeyResult(
    val credentialId: String,
    val userName: String,
    val userDisplayName: String
)

class GoogleAuthService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleAuthService"
    }

    // ── Google Sign-In ────────────────────────────────────────────

    fun getGoogleSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun parseGoogleSignInResult(data: Intent?): GoogleSignInResult {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Sign-in OK — id=${account.id}, email=${account.email}")
            GoogleSignInResult(
                googleId = account.id ?: "",
                name = account.displayName ?: "",
                email = account.email ?: "",
                photoUrl = account.photoUrl?.toString(),
                idToken = account.idToken
            )
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign-in failed: statusCode=${e.statusCode}")
            throw Exception("Google sign-in failed (code ${e.statusCode})", e)
        }
    }

    // ── Passkey Sign-In ───────────────────────────────────────────

    /**
     * Launch Credential Manager to sign in with an existing passkey.
     * Returns null if no passkey is available or the user cancelled.
     */
    suspend fun signInWithPasskey(): PasskeyResult? {
        val credentialManager = CredentialManager.create(context)

        val challenge = Base64.encodeToString(
            UUID.randomUUID().toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val requestJson = JSONObject().apply {
            put("challenge", challenge)
            put("rpId", "com.fmz.spenitaicore")
            put("userVerification", "required")
            put("timeout", 60000)
        }.toString()

        val passkeyOption = androidx.credentials.GetPublicKeyCredentialOption(
            requestJson = requestJson,
            clientDataHash = null
        )

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(passkeyOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            handlePasskeyResult(result)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Passkey sign-in cancelled or failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected passkey sign-in error", e)
            null
        }
    }

    private fun handlePasskeyResult(result: GetCredentialResponse): PasskeyResult? {
        val credential = result.credential

        if (credential is PublicKeyCredential) {
            // Parse the authentication response to extract user info
            val responseJson = credential.authenticationResponseJson
            return try {
                val json = JSONObject(responseJson)
                val id = json.optString("id", "unknown")
                val userName = json.optString("userName",
                    json.optJSONObject("response")?.optString("userHandle", "") ?: "")
                val displayName = json.optString("userDisplayName", userName)

                PasskeyResult(
                    credentialId = id,
                    userName = userName.ifEmpty { "Passkey User" },
                    userDisplayName = displayName.ifEmpty { userName.ifEmpty { "Passkey User" } }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse passkey response JSON", e)
                PasskeyResult(
                    credentialId = "unknown",
                    userName = "Passkey User",
                    userDisplayName = "Passkey User"
                )
            }
        }

        Log.w(TAG, "Unexpected credential type for passkey: ${credential.type}")
        return null
    }

    // ── Drive / Account helpers ───────────────────────────────────

    /**
     * Find the first Google account matching the given name, or the first
     * available Google account on the device. Returns null if none exist.
     */
    fun findGoogleAccount(accountName: String? = null): Account? {
        val accounts = AccountManager.get(context)
            .getAccountsByType("com.google")
        if (accounts.isEmpty()) return null
        return accounts.firstOrNull { it.name == accountName } ?: accounts.first()
    }

    /**
     * Request Drive file-scope authorization for the account.
     * Throws [com.google.android.gms.auth.UserRecoverableAuthException]
     * if the user needs to grant consent — caller must launch the intent.
     */
    fun authorizeDrive(account: Account): String {
        return com.google.android.gms.auth.GoogleAuthUtil.getToken(
            context,
            account,
            "oauth2:https://www.googleapis.com/auth/drive.file"
        )
    }
}
