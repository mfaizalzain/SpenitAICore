package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.SpenItApp
import com.fmz.spenitaicore.data.auth.PasskeyResult
import com.fmz.spenitaicore.data.db.entity.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val authMethod: String? = null, // "google", "passkey", or "biometric"
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val error: String? = null
)

class AuthViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val authService = container.googleAuthService
    private val preferences = container.preferences

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            val loggedIn = preferences.getIsLoggedIn()
            if (loggedIn) {
                _state.value = AuthState(
                    isLoggedIn = true,
                    userName = preferences.getUserName(),
                    userEmail = preferences.getUserEmail(),
                    userPhotoUrl = preferences.getUserPhotoUrl(),
                    authMethod = preferences.getAuthMethod()
                )
            }
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────

    fun signInWithGoogle() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val result = authService.signInWithGoogle()

            if (result == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Sign in was cancelled"
                )
                return@launch
            }

            persistLogin(
                authMethod = "google",
                name = result.name,
                email = result.email,
                photoUrl = result.photoUrl,
                googleId = result.googleId,
                idToken = result.idToken
            )
        }
    }

    // ── Passkey Sign-In ───────────────────────────────────────────

    fun signInWithPasskey() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val result = authService.signInWithPasskey()

            if (result == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "No passkey found or sign in was cancelled"
                )
                return@launch
            }

            persistLogin(
                authMethod = "passkey",
                name = result.userDisplayName,
                email = result.userName,
                photoUrl = null,
                googleId = result.credentialId,
                idToken = null
            )
        }
    }

    // ── Biometric Sign-In ─────────────────────────────────────────

    /**
     * Called by the LoginScreen after a successful BiometricPrompt.
     * Since biometric auth is verified at the OS level, we trust the result.
     */
    fun signInWithBiometrics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Try to restore a previously-saved profile, fall back to a local profile
            val existingProfile = try {
                container.database.userProfileDao().getUserProfile()
            } catch (_: Exception) { null }

            val name = existingProfile?.name?.ifEmpty { null }
                ?: preferences.getUserName().ifEmpty { null }
                ?: "SpenIt User"

            val email = existingProfile?.email?.ifEmpty { null }
                ?: preferences.getUserEmail().ifEmpty { null }
                ?: ""

            val photoUrl = existingProfile?.photoUrl
                ?: preferences.getUserPhotoUrl()

            persistLogin(
                authMethod = "biometric",
                name = name,
                email = email,
                photoUrl = photoUrl,
                googleId = existingProfile?.googleId ?: "",
                idToken = null
            )
        }
    }

    // ── Shared persistence ────────────────────────────────────────

    private suspend fun persistLogin(
        authMethod: String,
        name: String,
        email: String,
        photoUrl: String?,
        googleId: String,
        idToken: String?
    ) {
        // Persist to DataStore (fast, for app gate)
        preferences.setLoggedIn(
            googleId = googleId,
            name = name,
            email = email,
            photoUrl = photoUrl,
            authMethod = authMethod
        )

        // Persist to Room (durable, for future sync features)
        container.database.userProfileDao().insert(
            UserProfile(
                googleId = googleId,
                name = name,
                email = email,
                photoUrl = photoUrl,
                idToken = idToken
            )
        )

        _state.value = AuthState(
            isLoggedIn = true,
            isLoading = false,
            authMethod = authMethod,
            userName = name,
            userEmail = email,
            userPhotoUrl = photoUrl
        )
    }

    // ── Sign-Out ──────────────────────────────────────────────────

    fun signOut() {
        viewModelScope.launch {
            preferences.clearAuth()
            container.database.userProfileDao().deleteAll()
            _state.value = AuthState()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ── Legacy alias ──────────────────────────────────────────────

    @Deprecated("Renamed to signInWithGoogle", ReplaceWith("signInWithGoogle()"))
    fun signIn() = signInWithGoogle()
}
