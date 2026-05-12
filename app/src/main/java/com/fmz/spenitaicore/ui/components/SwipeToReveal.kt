package com.fmz.spenitaicore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SwipeToReveal(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (modifier: Modifier) -> Unit
) {
    // Simplified version — shows edit/delete buttons on a Card
    // For full swipe-to-reveal, would use AnchoredDraggable
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete action
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onError
            )
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            content(Modifier.fillMaxWidth())
        }

        // Edit action
        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.secondary)
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
