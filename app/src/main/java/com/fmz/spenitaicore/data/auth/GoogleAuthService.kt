package com.fmz.spenitaicore.data.auth

import android.content.Context
import android.accounts.Account
import android.accounts.AccountManager
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.fmz.spenitaicore.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject
import java.security.SecureRandom
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
        private const val NONCE_BYTES = 32
    }

    // ── Google Sign-In ────────────────────────────────────────────

    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        val credentialManager = CredentialManager.create(context)
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId())
            .setNonce(generateNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleGoogleCredential(response)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${e.type} ${e.message}")
            throw Exception("Google sign-in was cancelled or unavailable.", e)
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Google ID token parsing failed", e)
            throw Exception("Google sign-in failed. Please try again.", e)
        }
    }

    suspend fun clearCredentialState() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w(TAG, "Failed to clear credential state: ${e.type}", e)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error clearing credential state", e)
        }
    }

    private fun handleGoogleCredential(response: GetCredentialResponse): GoogleSignInResult {
        val credential = response.credential
        if (
            credential is CustomCredential &&
            (
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                )
        ) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val tokenClaims = parseJwtClaims(googleCredential.idToken)
            val email = tokenClaims?.optString("email")?.takeIf { it.isNotBlank() }
                ?: googleCredential.id
            val googleId = tokenClaims?.optString("sub")?.takeIf { it.isNotBlank() }
                ?: googleCredential.id

            Log.d(TAG, "Credential Manager Google sign-in OK — id=$googleId, email=$email")
            return GoogleSignInResult(
                googleId = googleId,
                name = googleCredential.displayName ?: "",
                email = email,
                photoUrl = googleCredential.profilePictureUri?.toString(),
                idToken = googleCredential.idToken
            )
        }

        Log.w(TAG, "Unexpected credential type for Google sign-in: ${credential.type}")
        throw Exception("Google sign-in returned an unsupported credential.")
    }

    private fun serverClientId(): String =
        context.getString(R.string.google_client_id).trim()

    private fun generateNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun parseJwtClaims(idToken: String): JSONObject? {
        val payload = idToken.split(".").getOrNull(1) ?: return null
        return try {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse Google ID token claims", e)
            null
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
