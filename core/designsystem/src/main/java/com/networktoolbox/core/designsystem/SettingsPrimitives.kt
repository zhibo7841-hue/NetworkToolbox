package com.networktoolbox.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A compact settings group: a section label followed by one outlined surface
 * containing related rows.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedNetworkCard(content = content)
    }
}

/** A non-interactive row for a setting or an explanatory piece of information. */
@Composable
fun SettingsInfoRow(
    title: String,
    supportingText: String? = null,
    icon: ImageVector? = null,
    trailingValue: String? = null,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        title = title,
        supportingText = supportingText,
        icon = icon,
        trailingValue = trailingValue,
        modifier = modifier,
    )
}

/**
 * A full-width, accessible settings row. Interactive rows keep a comfortable
 * touch target while remaining visually lighter than a standalone button.
 */
@Composable
fun SettingsRow(
    title: String,
    supportingText: String? = null,
    icon: ImageVector? = null,
    trailingValue: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )

    Row(
        modifier = rowModifier.padding(vertical = NetworkToolboxSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supportingText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailingValue?.let { value ->
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
