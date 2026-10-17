package com.bazyak.payfacade.common

/**
 * Tells a user-initiated exit apart from the app opening a system screen itself.
 *
 * MainActivity closes on onUserLeaveHint so the crown quits the app instead of backgrounding it.
 * That callback also fires when we launch a settings picker, and without this flag the app would
 * close mid-selection with nowhere to return the chosen value to.
 */
object OwnActivityLaunchTracker {
    @Volatile
    var isLaunchingOwnActivity: Boolean = false
}
