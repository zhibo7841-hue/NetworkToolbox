package com.networktoolbox.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Shared trailing affordance for a surface that opens a detail destination. */
@Composable
fun NetworkToolboxChevron(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Shared low-emphasis destructive icon used by local history actions. */
@Composable
fun NetworkToolboxDeleteIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.error,
) {
    Icon(
        imageVector = Icons.Outlined.DeleteOutline,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
