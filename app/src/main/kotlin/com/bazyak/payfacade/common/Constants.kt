package com.bazyak.payfacade.common

/** Countdown length offered by default, in seconds. */
const val DEFAULT_COUNTDOWN_SECONDS = 10

/** Range the countdown can be set to, in seconds. */
val COUNTDOWN_RANGE = 5..60
const val COUNTDOWN_STEP = 5

/** Seconds granted by a double tap on the card. */
const val BONUS_SECONDS = 5

/** Buzz length offered by default, and the range it can be set to, in milliseconds. */
const val DEFAULT_BUZZ_LENGTH_MS = 75
val BUZZ_LENGTH_RANGE = 25..200
const val BUZZ_LENGTH_STEP = 25

/**
 * Longest gap between Mir Pay's payment screen and its return that still counts as "came back
 * after a PIN or pattern", in seconds, plus the range it can be set to. Long enough to draw a
 * pattern unhurried, short enough that two unrelated payments are not mistaken for one.
 */
const val DEFAULT_UNLOCK_WINDOW_SECONDS = 90
val UNLOCK_WINDOW_RANGE = 30..300
const val UNLOCK_WINDOW_STEP = 30

/** Mir Pay's package - the wallet this app stands in front of. */
const val MIR_PAY_PACKAGE = "ru.nspk.mirpay"

/** Screen brightness held while the app is open (0f..1f). */
const val SCREEN_BRIGHTNESS = 0.6f
