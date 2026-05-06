package com.fmz.spenitaicore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.fmz.spenitaicore.ui.navigation.SpenItNavHost
import com.fmz.spenitaicore.ui.theme.SpenItTheme
import com.fmz.spenitaicore.util.FileUtils
import com.fmz.spenitaicore.util.PendingSharedFiles

class MainActivity : AppCompatActivity() {

    private val sharedImportSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpenItApp

        // Handle incoming shared files
        handleSharedIntent(intent)

        setContent {
            val shareSignal by sharedImportSignal
            SpenItTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpenItNavHost(
                        container = app.container,
                        sharedImportSignal = shareSignal
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        val uris = extractFileUris(intent)
        if (uris.isNotEmpty()) {
            val importedCount = uris.count { processSharedUri(it) }
            if (importedCount > 0) sharedImportSignal.intValue += 1
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

    private fun processSharedUri(uri: Uri): Boolean {
        // Copy file to app cache for persistent access
        val localFile = FileUtils.copySharedFileToCache(this, uri)
        return if (localFile != null) {
            val displayName = FileUtils.getDisplayName(this, uri)
                ?: localFile.name
            PendingSharedFiles.add(localFile.absolutePath, displayName)
            true
        } else {
            false
        }
    }
}
