package com.bazyak.payfacade.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import com.bazyak.payfacade.BuildConfig
import com.bazyak.payfacade.R
import com.bazyak.payfacade.common.BUZZ_LENGTH_RANGE
import com.bazyak.payfacade.common.BUZZ_LENGTH_STEP
import com.bazyak.payfacade.common.COUNTDOWN_RANGE
import com.bazyak.payfacade.common.COUNTDOWN_STEP
import com.bazyak.payfacade.common.UNLOCK_WINDOW_RANGE
import com.bazyak.payfacade.common.UNLOCK_WINDOW_STEP
import com.bazyak.payfacade.common.Preferences
import com.bazyak.payfacade.settings.ActionSetting
import com.bazyak.payfacade.settings.InfoSetting
import com.bazyak.payfacade.settings.PackageInputSetting
import com.bazyak.payfacade.settings.SectionTitle
import com.bazyak.payfacade.settings.StepperSetting
import com.bazyak.payfacade.settings.SubsectionTitle
import com.bazyak.payfacade.settings.SwitchSetting
import com.bazyak.payfacade.settings.openAccessibilitySettings
import com.bazyak.payfacade.settings.rememberAccessibilityEnabled
import com.bazyak.payfacade.ui.theme.PayFacadeTheme

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun SettingsScreen(prefs: Preferences) {
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()
    val context = LocalContext.current
    val accessibilityEnabled by rememberAccessibilityEnabled(context)

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester,
                ),
            state = listState,
        ) {
            item { SectionTitle(stringResource(R.string.section_card), isFirst = true) }
            item {
                StepperSetting(
                    title = stringResource(R.string.setting_countdown),
                    value = prefs.countdownSeconds,
                    unit = stringResource(R.string.unit_seconds),
                    range = IntProgression.fromClosedRange(
                        COUNTDOWN_RANGE.first, COUNTDOWN_RANGE.last, COUNTDOWN_STEP
                    ),
                    onValueChange = { prefs.countdownSeconds = it },
                )
            }

            item { SectionTitle(stringResource(R.string.section_buzz)) }
            item {
                SwitchSetting(
                    title = stringResource(R.string.setting_buzz_start),
                    checked = prefs.buzzOnStart,
                    iconRes = R.drawable.ic_vibration_24,
                    onCheckedChange = { prefs.buzzOnStart = it },
                )
            }
            item {
                SwitchSetting(
                    title = stringResource(R.string.setting_buzz_finish),
                    checked = prefs.buzzOnFinish,
                    iconRes = R.drawable.ic_vibration_24,
                    onCheckedChange = { prefs.buzzOnFinish = it },
                )
            }
            item {
                StepperSetting(
                    title = stringResource(R.string.setting_buzz_length),
                    value = prefs.buzzLengthMs,
                    unit = stringResource(R.string.unit_milliseconds),
                    range = IntProgression.fromClosedRange(
                        BUZZ_LENGTH_RANGE.first, BUZZ_LENGTH_RANGE.last, BUZZ_LENGTH_STEP
                    ),
                    onValueChange = { prefs.buzzLengthMs = it },
                )
            }

            // Everything in this block runs through the accessibility service, so until it is
            // granted the grant row is the one thing to do here and the rest is unavailable.
            item { SectionTitle(stringResource(R.string.section_takeover)) }
            item {
                ActionSetting(
                    title = stringResource(R.string.setting_accessibility),
                    subtitle = stringResource(
                        if (accessibilityEnabled) R.string.state_granted
                        else R.string.state_tap_to_grant
                    ),
                    iconRes = R.drawable.ic_accessibility_24,
                    highlighted = accessibilityEnabled,
                    onClick = { context.openAccessibilitySettings() },
                )
            }

            item { SubsectionTitle(stringResource(R.string.section_takeover_apps)) }
            item {
                SwitchSetting(
                    title = stringResource(R.string.setting_google_pay_takeover),
                    checked = prefs.googlePayTakeover,
                    iconRes = R.drawable.ic_accessibility_24,
                    enabled = accessibilityEnabled,
                    onCheckedChange = { prefs.googlePayTakeover = it },
                )
            }
            item {
                PackageInputSetting(
                    title = stringResource(R.string.setting_custom_takeover),
                    value = prefs.customTakeoverPackage,
                    iconRes = R.drawable.ic_accessibility_24,
                    enabled = accessibilityEnabled,
                    onValueChange = { prefs.customTakeoverPackage = it },
                )
            }
            if (prefs.customTakeoverPackage.isNotEmpty()) {
                item {
                    ActionSetting(
                        title = stringResource(R.string.setting_clear_custom_takeover),
                        subtitle = null,
                        iconRes = R.drawable.ic_accessibility_24,
                        enabled = accessibilityEnabled,
                        onClick = { prefs.customTakeoverPackage = "" },
                    )
                }
            }

            item { SubsectionTitle(stringResource(R.string.section_takeover_lock)) }
            item {
                StepperSetting(
                    title = stringResource(R.string.setting_unlock_window),
                    value = prefs.unlockWindowSeconds,
                    unit = stringResource(R.string.unit_seconds),
                    range = IntProgression.fromClosedRange(
                        UNLOCK_WINDOW_RANGE.first, UNLOCK_WINDOW_RANGE.last, UNLOCK_WINDOW_STEP
                    ),
                    enabled = accessibilityEnabled,
                    onValueChange = { prefs.unlockWindowSeconds = it },
                )
            }

            // Coming back from Mir Pay to the card relies on the accessibility service the same
            // way the return after a PIN does, so this block needs it too.
            item { SectionTitle(stringResource(R.string.section_warmup)) }
            item {
                SwitchSetting(
                    title = stringResource(R.string.setting_warmup),
                    checked = prefs.warmUpMirPay,
                    iconRes = R.drawable.ic_watch_wake_24,
                    enabled = accessibilityEnabled,
                    onCheckedChange = { prefs.warmUpMirPay = it },
                )
            }

            item { SectionTitle(stringResource(R.string.section_about)) }
            item {
                InfoSetting(
                    title = stringResource(R.string.setting_version),
                    value = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    PayFacadeTheme {
        SettingsScreen(prefs = Preferences.get(LocalContext.current))
    }
}
