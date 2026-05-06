package com.fmz.spenitaicore.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fmz.spenitaicore.MainActivity
import com.fmz.spenitaicore.R
import java.util.concurrent.atomic.AtomicInteger

object ImportNotificationHelper {

    const val EXTRA_NAVIGATE_TO_IMPORTS = "navigate_to_imports"

    private const val CHANNEL_ID = "spenit_imports"
    private const val CHANNEL_NAME = "File Imports"
    private val idCounter = AtomicInteger(2000)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Import status notifications" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun showSuccess(context: Context, displayName: String, message: String) {
        show(
            context = context,
            title = "Import Complete",
            text = message.ifBlank { "$displayName imported successfully" },
            pendingIntent = mainIntent(context)
        )
    }

    fun showFailed(context: Context, displayName: String, reason: String) {
        show(
            context = context,
            title = "Import Failed — $displayName",
            text = "${reason.ifBlank { "Could not import file" }}. Tap to review.",
            pendingIntent = importsIntent(context)
        )
    }

    fun showDuplicate(context: Context, displayName: String) {
        show(
            context = context,
            title = "Duplicate File — $displayName",
            text = "This file already exists. Tap to review.",
            pendingIntent = importsIntent(context)
        )
    }

    private fun show(
        context: Context,
        title: String,
        text: String,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(idCounter.getAndIncrement(), notification)
    }

    private fun importsIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO_IMPORTS, true)
        }
        return PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
