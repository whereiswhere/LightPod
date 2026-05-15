package com.lightmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lightmusic.R

val NotoSansSC = FontFamily(
    Font(R.font.noto_sans_sc_regular, FontWeight.Normal)
)

val LightOSTypography = Typography(
    titleMedium = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        letterSpacing = 0.02.em
    ),
    bodyLarge = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 20.sp,
        letterSpacing = 0.0.em
    ),
    bodyMedium = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        letterSpacing = 0.01.em
    ),
    bodySmall = TextStyle(
        fontFamily    = NotoSansSC,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        letterSpacing = 0.01.em
    ),
    displaySmall = TextStyle(
        fontFamily = NotoSansSC,
        fontWeight = FontWeight.Normal,
        fontSize   = 35.sp
    )
)
