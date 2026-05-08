package com.fmz.spenitaicore.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SharedImportsBadgeIcon(
    count: Int,
    contentDescription: String = "Shared Imports"
) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge {
                    Text(if (count > 99) "99+" else count.toString())
                }
            }
        }
    ) {
        Icon(Icons.Outlined.Inbox, contentDescription = contentDescription)
    }
}
