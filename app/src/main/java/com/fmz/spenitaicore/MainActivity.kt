package com.fmz.spenitaicore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fmz.spenitaicore.ui.navigation.SpenItNavHost
import com.fmz.spenitaicore.ui.theme.SpenItTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SpenItApp

        setContent {
            SpenItTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpenItNavHost(app.container)
                }
            }
        }
    }
}
