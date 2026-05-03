package com.fmz.spenitaicore.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                .background(Color(0xFFB00020))
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color.White
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
                .background(Color(0xFF1565C0))
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = Color.White
            )
        }
    }
}
