package com.bazyak.payfacade.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip

/**
 * The building blocks of the settings page.
 *
 * Tiles, switches and sliders keep the library's own look, including its own faded look when a
 * setting is unavailable. A row that leads somewhere can be "highlighted" - accessibility granted,
 * a package named - and is then drawn with the same gradient a switch tile gets when it is on, so
 * done and not-done read the same way across the page.
 *
 * Numeric values are adjusted in place with an InlineSlider rather than by handing off to the
 * system input screen. Staying inside the app keeps the rotary bezel working and means a value is
 * applied the moment it changes instead of on return.
 */

private val MainTitleColor = Color(0xFFC4C4C8)
private val SubTitleColor = Color(0xFF9A9A9F)

/** Heading of one of the page's main blocks: Card, Vibration, Takeover, About. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, isFirst: Boolean = false) {
    Title(
        text = text,
        color = MainTitleColor,
        fontSize = 17,
        fontWeight = FontWeight.Medium,
        top = if (isFirst) 10.dp else 24.dp,
        bottom = 8.dp,
        modifier = modifier,
    )
}

/** Heading of a group inside a main block, e.g. "Apps" under Takeover. */
@Composable
fun SubsectionTitle(text: String, modifier: Modifier = Modifier) {
    Title(
        text = text,
        color = SubTitleColor,
        fontSize = 14,
        fontWeight = FontWeight.Normal,
        top = 12.dp,
        bottom = 6.dp,
        modifier = modifier,
    )
}

@Composable
private fun Title(
    text: String,
    color: Color,
    fontSize: Int,
    fontWeight: FontWeight,
    top: Dp,
    bottom: Dp,
    modifier: Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = top, bottom = bottom),
    )
}

/** A number the user nudges up and down, shown with its unit above the slider. */
@Composable
fun StepperSetting(
    title: String,
    value: Int,
    unit: String,
    range: IntProgression,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val labelColor = MaterialTheme.colors.onBackground
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$title: $value$unit",
            color = if (enabled) labelColor else labelColor.copy(alpha = ContentAlpha.disabled),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        InlineSlider(
            value = value,
            onValueChange = onValueChange,
            valueProgression = range,
            enabled = enabled,
            decreaseIcon = { Icon(InlineSliderDefaults.Decrease, null) },
            increaseIcon = { Icon(InlineSliderDefaults.Increase, null) },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** An on/off setting. */
@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    iconRes: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ToggleChip(
        modifier = modifier.fillMaxWidth(),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        label = { Text(text = title, maxLines = 2) },
        toggleControl = { Switch(checked = checked, enabled = enabled) },
        appIcon = { SettingIcon(iconRes) },
    )
}

/**
 * A row that leads somewhere, with the current state as its subtitle (if any).
 *
 * [highlighted] marks a row whose thing is done or set: it gets the gradient of a switched-on
 * tile. Otherwise, and while the row is unavailable, it is a plain tile.
 */
@Composable
fun ActionSetting(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    // Text colour is set explicitly: left to itself the chip derives it from the background, and
    // in this theme the surface grey doubles as secondaryVariant, whose pairing (onSecondary) is
    // black - so the text came out black on a dark tile.
    val theme = MaterialTheme.colors
    Chip(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        colors = if (highlighted && enabled) {
            // Same gradient as a switched-on ToggleChip: plain on the left, tinted on the right.
            ChipDefaults.gradientBackgroundChipColors(
                startBackgroundColor = theme.surface,
                endBackgroundColor = theme.primary.copy(alpha = 0.5f).compositeOver(theme.surface),
                contentColor = theme.onSurface,
            )
        } else {
            ChipDefaults.secondaryChipColors(contentColor = theme.onSurface)
        },
        label = { Text(text = title, maxLines = 1) },
        secondaryLabel = if (subtitle != null) {
            { Text(text = subtitle, maxLines = 1) }
        } else {
            null
        },
        icon = { SettingIcon(iconRes) },
    )
}

/**
 * A read-only line, used for the version.
 *
 * Deliberately not a disabled Chip: a disabled chip is drawn muted, which on this dark theme came
 * out as grey-on-black and could not be read.
 */
@Composable
fun InfoSetting(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

/**
 * No explicit tint: the icon takes the colour the tile gives its icon slot, so it fades with the
 * rest of the tile when the setting is unavailable.
 */
@Composable
private fun SettingIcon(iconRes: Int) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        modifier = Modifier.size(ChipDefaults.SmallIconSize),
    )
}
