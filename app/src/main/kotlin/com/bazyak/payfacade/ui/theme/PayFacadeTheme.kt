package com.bazyak.payfacade.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * A deliberately monochrome palette: the card page is a white pictogram on black, and the settings
 * page should not fight with it. Accents are plain greys rather than a colour, so nothing on
 * screen competes with the card for attention during a payment.
 */
private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Slate = Color(0xFF2A2A2C)
private val Mist = Color(0xFFB8BABF)
private val Alert = Color(0xFFE06C63)

private val Palette = Colors(
    primary = Paper,
    primaryVariant = Mist,
    secondary = Mist,
    secondaryVariant = Slate,
    background = Ink,
    surface = Slate,
    error = Alert,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Paper,
    onSurface = Paper,
    onSurfaceVariant = Mist,
    onError = Ink,
)

@Composable
fun PayFacadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = Palette, content = content)
}
