package com.fmz.spenitaicore.data.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import com.fmz.spenitaicore.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class GoogleSignInResult(
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val idToken: String
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

    /**
     * Launch the Credential Manager bottom sheet for Google Sign-In.
     * Returns null if the user cancelled or an error occurred.
     */
    suspend fun signInWithGoogle(): GoogleSignInResult? {
        val credentialManager = CredentialManager.create(context)

        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = rawNonce.toByteArray().let { bytes ->
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.google_client_id).trim())
            .setNonce(hashedNonce)
            .setAutoSelectEnabled(true)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            handleGoogleResult(result)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in cancelled or failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google sign-in error", e)
            null
        }
    }

    private fun handleGoogleResult(result: GetCredentialResponse): GoogleSignInResult? {
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.w(TAG, "Unexpected credential type: ${credential.type}")
            return null
        }

        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)

        return GoogleSignInResult(
            googleId = googleIdToken.id,
            name = googleIdToken.displayName ?: "",
            email = googleIdToken.id,
            photoUrl = googleIdToken.profilePictureUri?.toString(),
            idToken = googleIdToken.idToken
        )
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
            clientDataHash = null,
            preferImmediatelyAvailableCredentials = false
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
                val id = json.optString("id", credential.credentialId)
                val userName = json.optString("userName",
                    json.optJSONObject("response")?.optString("userHandle", "") ?: "")
                val displayName = credential.displayName ?: userName

                PasskeyResult(
                    credentialId = id,
                    userName = userName.ifEmpty { "Passkey User" },
                    userDisplayName = displayName.ifEmpty { userName.ifEmpty { "Passkey User" } }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse passkey response JSON", e)
                PasskeyResult(
                    credentialId = credential.credentialId,
                    userName = credential.displayName ?: "Passkey User",
                    userDisplayName = credential.displayName ?: "Passkey User"
                )
            }
        }

        Log.w(TAG, "Unexpected credential type for passkey: ${credential.type}")
        return null
    }

    // ── Legacy alias for backward compat ──────────────────────────

    @Deprecated("Renamed to signInWithGoogle", ReplaceWith("signInWithGoogle()"))
    suspend fun signIn(): GoogleSignInResult? = signInWithGoogle()
}
