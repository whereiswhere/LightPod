package com.lightpod.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightpod.ui.MusicViewModel
import com.lightpod.ui.theme.LightOSWhite
import com.lightpod.ui.theme.NotoSansSC
import kotlinx.coroutines.delay

private val npMeta = TextStyle(
    fontFamily = NotoSansSC,
    fontWeight = FontWeight.Normal,
    fontSize   = 17.sp
)

private val npTitle = TextStyle(
    fontFamily = NotoSansSC,
    fontWeight = FontWeight.Normal,
    fontSize   = 20.sp
)

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onShowLyrics: () -> Unit) {
    val song = vm.currentSong ?: return

    var currentPosition by remember { mutableStateOf(vm.player?.currentPosition?.coerceAtLeast(0L) ?: 0L) }
    var duration        by remember { mutableStateOf(vm.player?.duration?.coerceAtLeast(0L) ?: 0L) }

    LaunchedEffect(vm.isPlaying) {
        val p = vm.player ?: return@LaunchedEffect
        if (vm.isPlaying) {
            while (true) {
                currentPosition = p.currentPosition.coerceAtLeast(0L)
                duration        = p.duration.coerceAtLeast(0L)
                delay(500)
            }
        } else {
            currentPosition = p.currentPosition.coerceAtLeast(0L)
            duration        = p.duration.coerceAtLeast(0L)
        }
    }

    val progress = if (duration > 0L)
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    else 0f

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(38.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text      = song.artist,
                style     = npTitle,
                color     = LightOSWhite,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            if (vm.currentLyrics != null) {
                Icon(
                    imageVector        = Icons.Outlined.Lyrics,
                    contentDescription = "Lyrics",
                    tint               = LightOSWhite,
                    modifier           = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-24).dp)
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) { onShowLyrics() }
                )
            }
        }

        Text(
            text      = song.title,
            style     = npTitle,
            color     = LightOSWhite,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .basicMarquee()
                .offset(y = (-6).dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text      = formatMs(duration),
            style     = npMeta,
            color     = LightOSWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        LightProgressBar(
            progress = progress,
            onSeek   = { vm.seekTo((it * duration).toLong()) },
            modifier = Modifier.padding(horizontal = 30.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 38.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            TapTarget(size = 44.dp, onClick = { vm.skipPrev() }) {
                Icon(
                    imageVector        = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    tint               = LightOSWhite,
                    modifier           = Modifier.size(42.dp)
                )
            }
            TapTarget(size = 44.dp, onClick = { vm.togglePlayPause() }) {
                Icon(
                    imageVector        = if (vm.isPlaying) Icons.Outlined.Pause
                                        else               Icons.Outlined.PlayArrow,
                    contentDescription = if (vm.isPlaying) "Pause" else "Play",
                    tint               = LightOSWhite,
                    modifier           = Modifier.size(42.dp)
                )
            }
            TapTarget(size = 44.dp, onClick = { vm.skipNext() }) {
                Icon(
                    imageVector        = Icons.Outlined.SkipNext,
                    contentDescription = "Next",
                    tint               = LightOSWhite,
                    modifier           = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Keeps layout stable while playing; visible only when paused
        Text(
            text      = formatMs(currentPosition),
            style     = npMeta,
            color     = LightOSWhite,
            textAlign = TextAlign.Center,
            modifier  = Modifier.alpha(if (vm.isPlaying) 0f else 1f)
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TapTarget(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier         = Modifier
            .size(size)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
        content          = content
    )
}

@Composable
private fun LightProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(onSeek) {
                detectDragGestures { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val cy        = size.height / 2f
        val thickPx   = 6.dp.toPx()
        val thinPx    = 1.5.dp.toPx()
        val progressX = size.width * progress.coerceIn(0f, 1f)

        drawRect(
            color   = Color.White,
            topLeft = Offset(progressX, cy - thinPx / 2f),
            size    = Size((size.width - progressX).coerceAtLeast(0f), thinPx)
        )
        if (progressX > 0f) {
            drawRect(
                color   = Color.White,
                topLeft = Offset(0f, cy - thickPx / 2f),
                size    = Size(progressX, thickPx)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val minutes = ms / 1000L / 60L
    val seconds = ms / 1000L % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
