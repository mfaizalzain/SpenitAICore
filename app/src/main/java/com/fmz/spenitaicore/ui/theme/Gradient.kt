package com.fmz.spenitaicore.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun Modifier.spenItGradientBackground(): Modifier =
    background(
        Brush.verticalGradient(
            colors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                listOf(
                    Color(0xFF071713),
                    Color(0xFF0D2A25),
                    Color(0xFF10243A),
                    Color(0xFF0F172A)
                )
            } else {
                listOf(
                    Color(0xFFFDFEFE),
                    Color(0xFFEAFBF5),
                    Color(0xFFEAF6FF),
                    Color(0xFFFFFFFF)
                )
            }
        )
    )
