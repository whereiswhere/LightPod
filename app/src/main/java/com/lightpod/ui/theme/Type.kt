package com.lightpod.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lightpod.R

val NotoSansSC = FontFamily(
    Font(R.font.noto_sans_sc_regular, FontWeight.Normal)
)

val LightOSTypography = Typography(
    displaySmall = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 35.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    bodyLarge = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 18.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    bodyMedium = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)
