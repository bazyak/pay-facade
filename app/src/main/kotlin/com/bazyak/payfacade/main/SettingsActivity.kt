package com.bazyak.payfacade.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bazyak.payfacade.common.OwnActivityLaunchTracker
import com.bazyak.payfacade.common.Preferences
import com.bazyak.payfacade.ui.SettingsScreen
import com.bazyak.payfacade.ui.theme.PayFacadeTheme

/**
 * Settings, kept apart from the card screen so that Compose is only initialised when someone
 * actually opens them - never on the path to paying.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayFacadeTheme {
                SettingsScreen(prefs = Preferences.get(this))
            }
        }
    }

    /**
     * The crown sends HOME, so it cannot be intercepted as a key. Leaving from here has to close
     * the whole app, not just this screen: otherwise the task stays in the background on the
     * settings page, and the next launch reopens settings instead of the card.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (OwnActivityLaunchTracker.isLaunchingOwnActivity) return
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        // Set when this screen sends the user to the system accessibility settings; coming back
        // from there must not be mistaken for leaving the app.
        OwnActivityLaunchTracker.isLaunchingOwnActivity = false
    }
}
