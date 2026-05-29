package com.lightpod.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lightpod.R
import com.lightpod.ui.theme.LightOSBlack
import com.lightpod.ui.theme.LightOSWhite

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Only proceed if permission was actually granted —
        // if denied, user stays here and can try again
        if (granted) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightOSBlack)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text  = stringResource(R.string.onboarding_permission),
            style = MaterialTheme.typography.bodyLarge,
            color = LightOSWhite
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text  = stringResource(R.string.onboarding_sources),
            style = MaterialTheme.typography.bodyMedium,
            color = LightOSWhite
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text  = stringResource(R.string.onboarding_formats),
            style = MaterialTheme.typography.bodyMedium,
            color = LightOSWhite
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Grant Permission — centered underlined text button ────
        Text(
            text  = stringResource(R.string.onboarding_grant),
            style = MaterialTheme.typography.bodyLarge,
            color = LightOSWhite,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) { permissionLauncher.launch(permission) }
                .drawBehind {
                    drawLine(
                        color       = LightOSWhite,
                        start       = Offset(0f, size.height),
                        end         = Offset(size.width, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}
