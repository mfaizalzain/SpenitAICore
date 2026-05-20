package com.fmz.spenitaicore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

class MainActivity : AppCompatActivity() {

    val navigateToSharedImports = Channel<Unit>(Channel.CONFLATED)

    private val sharedImportSignal = mutableIntStateOf(0)
    private val appLockSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpenItApp

        handleSharedIntent(intent)
        if (intent.getBooleanExtra(ImportNotificationHelper.EXTRA_NAVIGATE_TO_IMPORTS, false)) {
            navigateToSharedImports.trySend(Unit)
        }

        setContent {
            val shareSignal by sharedImportSignal
            val lockSignal by appLockSignal

            val themeMode by app.container.preferences.themeMode.collectAsState(initial = THEME_MODE_SYSTEM)
            val isLoggedInState by app.container.preferences.isLoggedIn.collectAsState(initial = null)
            val isAppLockEnabledState by app.container.preferences.isAppLockEnabled.collectAsState(initial = null)

            val useDarkTheme = when (themeMode) {
                THEME_MODE_NIGHT -> true
                THEME_MODE_SYSTEM -> isSystemInDarkTheme()
                else -> false
            }

            SpenItTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLoggedInState == null || isAppLockEnabledState == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val isLoggedIn = isLoggedInState!!
                        val isAppLockEnabled = isAppLockEnabledState!!

                        var isAppUnlocked by remember(lockSignal) {
                            mutableStateOf(!isLoggedIn || !isAppLockEnabled)
                        }
                        var promptAppUnlock by remember { mutableStateOf(false) }
                        val biometricPrompt = remember {
                            BiometricPrompt(
                                this@MainActivity,
                                ContextCompat.getMainExecutor(this@MainActivity),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        isAppUnlocked = true
                                        promptAppUnlock = false
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                        ) {
                                            promptAppUnlock = false
                                        }
                                    }
                                }
                            )
                        }

                        LaunchedEffect(isLoggedIn, isAppLockEnabled) {
                            if (isLoggedIn && isAppLockEnabled && !isAppUnlocked) {
                                val canAuthenticate = try {
                                    BiometricManager.from(this@MainActivity)
                                        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                                        BiometricManager.BIOMETRIC_SUCCESS
                                } catch (_: Exception) { false }
                                if (canAuthenticate) {
                                    promptAppUnlock = true
                                } else {
                                    isAppUnlocked = true
                                }
                            }
                        }

                        LaunchedEffect(promptAppUnlock) {
                            if (promptAppUnlock) {
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Unlock SpenIt")
                                    .setSubtitle("Use biometrics to continue")
                                    .setNegativeButtonText("Cancel")
                                    .build()
                                biometricPrompt.authenticate(promptInfo)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main App content - always in composition to preserve state
                            SpenItNavHost(
                                container = app.container,
                                initialLoggedIn = isLoggedIn,
                                sharedImportSignal = shareSignal,
                                navigateToImportsFlow = navigateToSharedImports.receiveAsFlow()
                            )

                            // Lock overlay - covers the app content when locked
                            if (!isAppUnlocked) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                    ) {
                                        if (promptAppUnlock) {
                                            CircularProgressIndicator()
                                        } else {
                                            Text("SpenIt is locked", fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(onClick = { promptAppUnlock = true }) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
        if (intent.getBooleanExtra(ImportNotificationHelper.EXTRA_NAVIGATE_TO_IMPORTS, false)) {
            navigateToSharedImports.trySend(Unit)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            appLockSignal.intValue += 1
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
