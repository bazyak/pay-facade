package com.bazyak.payfacade.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * The handful of values this app persists.
 *
 * Each value is backed by Compose state as well as SharedPreferences: the state is what the UI
 * reads, so a change made on the settings page redraws immediately, and the write-through keeps
 * it on disk for the next launch. Plain fields reading straight from SharedPreferences would
 * persist fine but would not trigger recomposition.
 */
class Preferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pay_facade", Context.MODE_PRIVATE)

    private val countdownState = mutableIntStateOf(
        prefs.getInt(KEY_COUNTDOWN, DEFAULT_COUNTDOWN_SECONDS)
    )
    private val buzzStartState = mutableStateOf(prefs.getBoolean(KEY_BUZZ_START, false))
    private val buzzFinishState = mutableStateOf(prefs.getBoolean(KEY_BUZZ_FINISH, false))
    private val buzzLengthState = mutableIntStateOf(
        prefs.getInt(KEY_BUZZ_LENGTH, DEFAULT_BUZZ_LENGTH_MS)
    )
    private val googlePayTakeoverState =
        mutableStateOf(prefs.getBoolean(KEY_GOOGLE_PAY_TAKEOVER, false))
    private val customTakeoverState =
        mutableStateOf(prefs.getString(KEY_CUSTOM_TAKEOVER, "").orEmpty())
    private val unlockWindowState = mutableIntStateOf(
        prefs.getInt(KEY_UNLOCK_WINDOW, DEFAULT_UNLOCK_WINDOW_SECONDS)
    )
    private val warmUpState = mutableStateOf(prefs.getBoolean(KEY_WARMUP, false))

    /** How long the card stays up before the app closes itself. */
    var countdownSeconds: Int
        get() = countdownState.intValue
        set(value) {
            countdownState.intValue = value
            prefs.edit().putInt(KEY_COUNTDOWN, value).apply()
        }

    /** Buzz once when the card appears. */
    var buzzOnStart: Boolean
        get() = buzzStartState.value
        set(value) = buzzStartState.write(KEY_BUZZ_START, value)

    /** Buzz once when the countdown runs out. */
    var buzzOnFinish: Boolean
        get() = buzzFinishState.value
        set(value) = buzzFinishState.write(KEY_BUZZ_FINISH, value)

    /** Length of a single buzz, in milliseconds. */
    var buzzLengthMs: Int
        get() = buzzLengthState.intValue
        set(value) {
            buzzLengthState.intValue = value
            prefs.edit().putInt(KEY_BUZZ_LENGTH, value).apply()
        }

    /**
     * Whether opening Google Pay should be turned into opening this app instead. Off by default:
     * it makes Google Pay itself impossible to open, which is rarely what someone wants.
     */
    var googlePayTakeover: Boolean
        get() = googlePayTakeoverState.value
        set(value) = googlePayTakeoverState.write(KEY_GOOGLE_PAY_TAKEOVER, value)

    /**
     * Package of any app whose launch should be turned into opening this app instead, set by the
     * user. Empty means off. Works the same way as the Google Pay takeover, just for whatever the
     * user names - for wallets this app does not know about.
     */
    var customTakeoverPackage: String
        get() = customTakeoverState.value
        set(value) {
            customTakeoverState.value = value
            prefs.edit().putString(KEY_CUSTOM_TAKEOVER, value).apply()
        }

    /**
     * Longest gap between Mir Pay's payment screen and its return that still counts as coming
     * back after a PIN or pattern. Read by the accessibility service, same process.
     */
    var unlockWindowSeconds: Int
        get() = unlockWindowState.intValue
        set(value) {
            unlockWindowState.intValue = value
            prefs.edit().putInt(KEY_UNLOCK_WINDOW, value).apply()
        }

    /**
     * Whether to open Mir Pay before the card after it may have been force-stopped, so its first
     * payment after the stop does not hit a cold start. Mir Pay is closed again as soon as it has
     * loaded - see PayFacadeAccessibilityService.startWarmUpWatch.
     */
    var warmUpMirPay: Boolean
        get() = warmUpState.value
        set(value) = warmUpState.write(KEY_WARMUP, value)

    /**
     * Set when the user goes to Mir Pay's app info page from the card - the only sign this app can
     * get that Mir Pay may have been force-stopped. The system's own "stopped" flag is no use: as
     * the default payment app Mir Pay is started again by the system right after a force-stop.
     * Not shown in the UI, so plain SharedPreferences without Compose state.
     */
    var warmUpPending: Boolean
        get() = prefs.getBoolean(KEY_WARMUP_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_WARMUP_PENDING, value).apply()

    private fun MutableState<Boolean>.write(key: String, value: Boolean) {
        this.value = value
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        /**
         * One instance for the whole app.
         *
         * The values are cached in Compose state so the settings page redraws as they change, and
         * that cache is per instance - so the card screen and the settings screen have to be
         * looking at the same one. With an instance each, a value changed in settings would be
         * written to disk but the card screen would keep using what it read at launch.
         */
        @Volatile
        private var instance: Preferences? = null

        fun get(context: Context): Preferences =
            instance ?: synchronized(this) {
                instance ?: Preferences(context.applicationContext).also { instance = it }
            }

        const val KEY_COUNTDOWN = "countdown_seconds"
        const val KEY_BUZZ_START = "buzz_on_start"
        const val KEY_BUZZ_FINISH = "buzz_on_finish"
        const val KEY_BUZZ_LENGTH = "buzz_length_ms"
        const val KEY_UNLOCK_WINDOW = "unlock_window_seconds"
        const val KEY_GOOGLE_PAY_TAKEOVER = "google_pay_takeover"
        const val KEY_CUSTOM_TAKEOVER = "custom_takeover_package"
        const val KEY_WARMUP = "warm_up_mir_pay"
        const val KEY_WARMUP_PENDING = "warm_up_pending"
    }
}
