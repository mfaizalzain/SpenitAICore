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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpenItApp

        handleSharedIntent(intent)
        if (intent.getBooleanExtra(ImportNotificationHelper.EXTRA_NAVIGATE_TO_IMPORTS, false)) {
            navigateToSharedImports.trySend(Unit)
        }

        setContent {
            val shareSignal by sharedImportSignal
            SpenItTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpenItNavHost(
                        container = app.container,
                        sharedImportSignal = shareSignal,
                        navigateToImportsFlow = navigateToSharedImports.receiveAsFlow()
                    )
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
