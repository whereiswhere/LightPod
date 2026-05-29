package com.lightpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightpod.ui.theme.LightOSWhite
import com.lightpod.ui.theme.NotoSansSC

@Composable
fun LightTopBar(
    title: String,
    onBack: () -> Unit,
    searchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    autoFocusSearch: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current
    var textLayout     by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused      by remember { mutableStateOf(false) }

    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            focusRequester.requestFocus()
            onAutoFocusConsumed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) {
                    focusManager.clearFocus()
                    onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint               = LightOSWhite,
                modifier           = Modifier.size(30.dp)
            )
        }

        if (searchActive) {
            BasicTextField(
                value           = searchQuery,
                onValueChange   = onSearchQueryChange,
                singleLine      = true,
                cursorBrush     = SolidColor(Color.Transparent),
                textStyle       = TextStyle(
                    fontFamily    = NotoSansSC,
                    fontSize      = 22.sp,
                    color         = LightOSWhite,
                    letterSpacing = 0.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                onTextLayout    = { textLayout = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox   = { inner ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier         = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (isFocused) {
                                    val cx = textLayout
                                        ?.getCursorRect(searchQuery.length.coerceAtLeast(0))
                                        ?.left ?: return@drawBehind
                                    drawLine(
                                        color       = Color.White,
                                        start       = Offset(cx, 0f),
                                        end         = Offset(cx, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                    ) { inner() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.Center)
                    .padding(start = 48.dp, end = 16.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .align(Alignment.BottomCenter)
                    .padding(start = 48.dp, end = 16.dp)
                    .background(LightOSWhite)
            )
        } else {
            Text(
                text     = title,
                style    = MaterialTheme.typography.titleMedium,
                color    = LightOSWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp)
            )
        }
    }
}
