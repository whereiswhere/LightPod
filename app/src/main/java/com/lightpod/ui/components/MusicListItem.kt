package com.lightpod.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightpod.ui.theme.LightOSWhite

@Composable
fun MusicListItem(
    title: String,
    subtitle: String? = null,
    prefix: String? = null,
    isCurrentlyPlaying: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) { onClick() }
            .padding(
                start  = 5.dp,
                end    = 24.dp,
                top    = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (prefix != null) {
            Text(
                text      = prefix,
                style     = MaterialTheme.typography.bodyLarge,
                color     = LightOSWhite,
                maxLines  = 1,
                textAlign = TextAlign.End,
                modifier  = Modifier
                    .width(32.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.bodyLarge,
                color    = LightOSWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (isCurrentlyPlaying) {
                            drawLine(
                                color       = LightOSWhite,
                                start       = Offset(0f, size.height),
                                end         = Offset(size.width, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
            )
            if (subtitle != null) {
                Text(
                    text     = subtitle,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = LightOSWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
