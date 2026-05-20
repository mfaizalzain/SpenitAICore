package com.fmz.spenitaicore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fmz.spenitaicore.data.preferences.THEME_MODE_NIGHT
import com.fmz.spenitaicore.data.preferences.THEME_MODE_SYSTEM
import com.fmz.spenitaicore.data.db.entity.SharedImportItem
import com.fmz.spenitaicore.data.db.entity.SharedImportKind
import com.fmz.spenitaicore.data.db.entity.SharedImportStatus
import com.fmz.spenitaicore.ui.navigation.SpenItNavHost
import com.fmz.spenitaicore.ui.theme.SpenItTheme
import com.fmz.spenitaicore.data.notification.ImportNotificationHelper
import com.fmz.spenitaicore.util.FileUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    val navigateToSharedImports = Channel<Unit>(Channel.CONFLATED)

    private val sharedImportSignal = mutableIntStateOf(0)

    // App Lock state. The app locks only on a fresh process start (cold
    // launch) - it is NOT re-locked when minimized, which avoids the
    // stop/recompose/resume race that left a black window.
    private val locked = mutableStateOf(true)
    private val unlockFailed = mutableStateOf(false)
    private var biometricInProgress = false
    private var prefsLoaded = false
    private var isLoggedIn = false
    private var appLockEnabled = false

    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpenItApp

        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricInProgress = false
                    unlockFailed.value = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricInProgress = false
                    unlockFailed.value = true
                }
            }
        )

        handleSharedIntent(intent)
        if (intent.getBooleanExtra(ImportNotificationHelper.EXTRA_NAVIGATE_TO_IMPORTS, false)) {
            navigateToSharedImports.trySend(Unit)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    app.container.preferences.isLoggedIn,
                    app.container.preferences.isAppLockEnabled
                ) { loggedIn, lockEnabled -> loggedIn to lockEnabled }
                    .collect { (loggedIn, lockEnabled) ->
                        isLoggedIn = loggedIn
                        appLockEnabled = lockEnabled
                        if (!prefsLoaded) {
                            prefsLoaded = true
                            locked.value = loggedIn && lockEnabled
                            maybePromptUnlock()
                        }
                    }
            }
        }

        setContent {
            val shareSignal by sharedImportSignal

            val themeMode by app.container.preferences.themeMode.collectAsState(initial = THEME_MODE_SYSTEM)
            val isLoggedInState by app.container.preferences.isLoggedIn.collectAsState(initial = null)

            val useDarkTheme = when (themeMode) {
                THEME_MODE_NIGHT -> true
                THEME_MODE_SYSTEM -> isSystemInDarkTheme()
                else -> false
            }

            SpenItTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val loggedIn = isLoggedInState
                    if (loggedIn == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main app content - always in composition to preserve state
                            SpenItNavHost(
                                container = app.container,
                                initialLoggedIn = loggedIn,
                                sharedImportSignal = shareSignal,
                                navigateToImportsFlow = navigateToSharedImports.receiveAsFlow()
                            )

                            // Lock overlay - covers app content while locked. Default
                            // is a blank surface so the biometric prompt appears
                            // straight over it; the retry UI only shows if the user
                            // cancels or biometric auth fails.
                            if (locked.value) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    if (unlockFailed.value) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("SpenIt is locked", fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(onClick = { showBiometricPrompt() }) {
                                                Text("Unlock")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybePromptUnlock()
    }

    private fun maybePromptUnlock() {
        if (prefsLoaded && locked.value && isLoggedIn && appLockEnabled && !biometricInProgress) {
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        if (biometricInProgress) return

        val canAuthenticate = try {
            BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
        if (!canAuthenticate) {
            // No biometrics / device credential available - don't lock the user out.
            locked.value = false
            return
        }

        unlockFailed.value = false
        biometricInProgress = true
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SpenIt")
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
        if (intent.getBooleanExtra(ImportNotificationHelper.EXTRA_NAVIGATE_TO_IMPORTS, false)) {
            navigateToSharedImports.trySend(Unit)
        }
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        val uris = extractFileUris(intent)
        if (uris.isNotEmpty()) {
            val app = application as SpenItApp
            val importedCount = uris.count { processSharedUri(app, it) }
            if (importedCount > 0) {
                sharedImportSignal.intValue += 1
                navigateToSharedImports.trySend(Unit)
            }
        }
    }

    private fun extractFileUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
                intent.data?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }

        intent.clipData?.let { clipData ->
            repeat(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri?.let { uris.add(it) }
            }
        }

        return uris.distinct()
    }

    private fun processSharedUri(app: SpenItApp, uri: Uri): Boolean {
        val localFile = FileUtils.copySharedFileToImports(this, uri)
        return if (localFile != null) {
            val displayName = FileUtils.getDisplayName(this, uri)
                ?: localFile.name
            app.container.sharedImportStore.add(
                SharedImportItem(
                    id = UUID.randomUUID().toString(),
                    filePath = localFile.absolutePath,
                    displayName = displayName,
                    kind = SharedImportKind.Unknown,
                    status = SharedImportStatus.NeedsReview
                )
            )
            true
        } else {
            false
        }
    }
}
