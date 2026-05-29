package com.lightpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightpod.ui.theme.LightOSWhite

@Composable
fun LightBottomBar(
    shuffleOn: Boolean,
    onShuffleToggle: () -> Unit,
    onSearchOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Shuffle with active underline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) { onShuffleToggle() }
        ) {
            Icon(
                imageVector        = Icons.Outlined.Shuffle,
                contentDescription = "Shuffle",
                tint               = LightOSWhite,
                modifier           = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(1.5.dp)
                    .background(if (shuffleOn) LightOSWhite else Color.Transparent)
            )
        }

        // Search
        Icon(
            imageVector        = Icons.Outlined.Search,
            contentDescription = "Search",
            tint               = LightOSWhite,
            modifier           = Modifier
                .size(30.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) { onSearchOpen() }
        )
    }
}
