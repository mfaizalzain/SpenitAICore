package com.fmz.spenitaicore.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fmz.spenitaicore.R
import com.fmz.spenitaicore.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onSignedIn()
    }

    val canUseBiometrics = remember {
        try {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
    }

    val showBiometricPrompt = remember { mutableStateOf(false) }

    val biometricPrompt = remember(activity) {
        activity?.let { act ->
            BiometricPrompt(
                act,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.signInWithBiometrics()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) viewModel.clearError()
                    }
                    override fun onAuthenticationFailed() {}
                }
            )
        }
    }

    LaunchedEffect(showBiometricPrompt.value) {
        if (showBiometricPrompt.value && biometricPrompt != null) {
            showBiometricPrompt.value = false
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sign in to SpenIt")
                .setSubtitle("Use your fingerprint or face to sign in")
                .setNegativeButtonText("Cancel")
                .build()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val heroTextColor = if (isDarkTheme) Color(0xFFEAFBF5) else Color(0xFF0F2D2A)
    val heroSupportingColor = heroTextColor.copy(alpha = 0.74f)
    val heroPillColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.68f)
    }
    val heroBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val heroGradient = Brush.verticalGradient(
        if (isDarkTheme) {
            listOf(
                Color(0xFF04110E),
                Color(0xFF0D2A25),
                Color(0xFF10243A)
            )
        } else {
            listOf(
                Color(0xFFFDFEFE),
                Color(0xFFEAFBF5),
                Color(0xFFEAF6FF)
            )
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(heroGradient)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppIconMark()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SpenIt AICore",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = heroTextColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Track expenses with on-device AI",
                    style = MaterialTheme.typography.bodyLarge,
                    color = heroSupportingColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Feature pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeaturePill("Private", heroPillColor, heroBorderColor, heroTextColor)
                    FeaturePill("AI-Powered", heroPillColor, heroBorderColor, heroTextColor)
                    FeaturePill("On-Device", heroPillColor, heroBorderColor, heroTextColor)
                }
            }

            // Bottom auth card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.cachedName != null) "Welcome back, ${state.cachedName!!.split(" ").first()}" else "Sign in to continue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your financial data stays private on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Profile Photo (Cached)
                    if (state.cachedPhotoUrl != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        AsyncImage(
                            model = state.cachedPhotoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Error
                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.error!!,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Google Sign-In
                    SignInButton(
                        onClick = { viewModel.signInWithGoogle(context) },
                        enabled = !state.isLoading,
                        isLoading = state.isLoading,
                        label = "Sign in with Google",
                        leading = {
                            GoogleLogo(modifier = Modifier.size(20.dp))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Biometric
                    if (canUseBiometrics) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SignInButton(
                            onClick = { showBiometricPrompt.value = true },
                            enabled = !state.isLoading,
                            isLoading = state.isLoading,
                            label = "Continue with Biometrics",
                            leading = {
                                Icon(
                                    imageVector = Icons.Outlined.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconMark() {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.primary
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.36f),
                shape = RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(104.dp)
        )
    }
}

@Composable
private fun FeaturePill(
    label: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        scale(s, s, Offset.Zero) {
            val p = Path()
            // Blue
            p.moveTo(22.56f, 12.25f)
            p.cubicTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10f)
            p.lineTo(12f, 10f)
            p.lineTo(12f, 14.26f)
            p.lineTo(17.92f, 14.26f)
            p.cubicTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
            p.lineTo(15.71f, 20.34f)
            p.lineTo(19.28f, 20.34f)
            p.cubicTo(21.36f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f)
            drawPath(p, Color(0xFF4285F4))

            // Green
            p.reset()
            p.moveTo(12f, 23f)
            p.cubicTo(15.24f, 23f, 17.95f, 21.92f, 19.93f, 20.09f)
            p.lineTo(16.36f, 17.32f)
            p.cubicTo(15.37f, 17.98f, 14.11f, 18.38f, 12.01f, 18.38f)
            p.cubicTo(8.66f, 18.38f, 5.82f, 16.11f, 4.81f, 13.06f)
            p.lineTo(1.23f, 13.06f)
            p.lineTo(1.23f, 15.83f)
            p.cubicTo(3.21f, 19.68f, 7.31f, 23f, 12f, 23f)
            drawPath(p, Color(0xFF34A853))

            // Yellow
            p.reset()
            p.moveTo(4.8f, 13.06f)
            p.cubicTo(4.54f, 12.29f, 4.4f, 11.46f, 4.4f, 10.6f)
            p.cubicTo(4.4f, 9.74f, 4.54f, 8.91f, 4.8f, 8.14f)
            p.lineTo(4.8f, 5.37f)
            p.lineTo(1.23f, 5.37f)
            p.cubicTo(0.44f, 6.95f, 0f, 8.72f, 0f, 10.6f)
            p.cubicTo(0f, 12.48f, 0.44f, 14.25f, 1.23f, 15.83f)
            p.lineTo(4.8f, 13.06f)
            drawPath(p, Color(0xFFFBBC05))

            // Red
            p.reset()
            p.moveTo(12f, 4.61f)
            p.cubicTo(13.76f, 4.61f, 15.34f, 5.21f, 16.58f, 6.4f)
            p.lineTo(20.01f, 2.97f)
            p.cubicTo(17.95f, 1.08f, 15.24f, 0f, 12f, 0f)
            p.cubicTo(7.31f, 0f, 3.21f, 3.32f, 1.23f, 7.83f)
            p.lineTo(4.8f, 10.6f)
            p.cubicTo(5.81f, 7.55f, 8.65f, 5.28f, 12f, 5.28f)
            drawPath(p, Color(0xFFEA4335))
        }
    }
}

@Composable
private fun SignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    label: String,
    leading: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                leading()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
