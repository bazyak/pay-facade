package com.bazyak.payfacade.main

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bazyak.payfacade.common.FileLog
import com.bazyak.payfacade.common.MIR_PAY_PACKAGE
import com.bazyak.payfacade.common.Preferences

private const val GOOGLE_PAY_PACKAGE = "com.google.android.apps.walletnfcrel"

/**
 * The Mir Pay screen shown during a payment. Opening Mir Pay from the launcher uses a different
 * activity, so reacting to this one never fires on an ordinary launch. If a Mir Pay update renames
 * it, this is the one line to change - the log records every window's class.
 */
private const val MIR_PAY_PAYMENT_SCREEN = "com.ekassir.mirpay.ui.activity.PaymentActivity"

/** The watch face / home screen on OnePlus firmware, as the accessibility events name it. */
private const val WATCH_FACE_PACKAGE = "com.oplus.wearable.sysui"
private const val WATCH_FACE_CLASS = "com.google.android.wearable.sysui.mainui.activity.SysUiActivity"

/**
 * How soon after Mir Pay's payment screen the watch face has to appear, with no other Mir Pay
 * window in between, to count as Mir Pay's failure. In a working payment the success sheet comes
 * within 0.2-2 s and the watch face only ~5 s later; in every logged failure the watch face came
 * within 0.5-1.4 s and the success sheet never did.
 */
private const val MIR_PAY_FAILURE_WINDOW_MS = 2000L

/** Wear OS Settings, where Mir Pay's app info page and its "Force stop" button live. */
private const val SETTINGS_PACKAGE = "com.google.android.apps.wearable.settings"

/** The confirmation dialog's title and its check-mark button - icon only, so found by id. */
private const val DIALOG_TITLE_ID = "$SETTINGS_PACKAGE:id/wear_alertdialog_title_text"
private const val DIALOG_OK_ID = "$SETTINGS_PACKAGE:id/wear_alertdialog_positive_button"

/**
 * The "Force stop" button has no id, only its label. The label is read from Settings itself; these
 * are the fallback if that fails.
 */
private val FORCE_STOP_FALLBACK_LABELS = listOf("Остановить", "Force stop")

/** How long the force-stop is attempted before giving up, and how often the screen is checked. */
private const val FORCE_STOP_TIMEOUT_MS = 6000L
private const val FORCE_STOP_POLL_MS = 150L

/** How long the swipe that dismisses Mir Pay after a warm-up takes. */
private const val SWIPE_DURATION_MS = 250L

/**
 * Mir Pay's "Devices" tab: a screen from which a swipe closes Mir Pay (its main screen keeps the
 * swipe for flipping cards). Found by its description - Mir Pay is in Russian only.
 */
private const val MIR_PAY_DEVICES_TAB = "Устройства"

/** How often to check whether Mir Pay is showing that tab yet. */
private const val TAB_SWITCH_POLL_MS = 100L

/**
 * Brings the app to the front at the two moments it is wanted, neither of which the app itself can
 * observe: when Google Pay opens, and when Mir Pay comes back after asking for a PIN or pattern.
 *
 * Unlike the activity, this service lives for as long as the permission is granted, so it can act
 * long after the countdown has closed the app - which matters, because entering a pattern takes
 * far longer than the countdown lasts.
 */
class PayFacadeAccessibilityService : AccessibilityService() {

    /**
     * When Mir Pay's payment screen was last seen with nothing else in front since, on the
     * elapsed-time clock. 0 means the chain is broken.
     */
    private var paymentScreenSeenAt = 0L

    /**
     * When Mir Pay's payment screen appeared with nothing from Mir Pay since, on the elapsed-time
     * clock - the start of the failure watch. 0 means not watching.
     */
    private var failureWatchSince = 0L

    private val prefs by lazy { Preferences.get(this) }

    private val handler = Handler(Looper.getMainLooper())

    private enum class ForceStopStep { IDLE, PRESS_STOP, CONFIRM }

    private var forceStopStep = ForceStopStep.IDLE
    private var forceStopDeadline = 0L

    /** Last force-stop progress logged, so each poll only logs when something changed. */
    private var forceStopStatus = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        FileLog.init(this)
        FileLog.write("accessibility service connected")
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @SuppressLint("WearRecents")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()

        FileLog.write("window: pkg=$pkg class=$cls")
        watchForMirPayFailure(pkg, cls)
        onMirPayLeft(pkg)

        if (pkg == packageName) {
            // Our own window: the card is up already, so there is nothing left to wait for.
            paymentScreenSeenAt = 0L
            return
        }

        val customPackage = prefs.customTakeoverPackage
        if (customPackage.isNotEmpty() && pkg == customPackage) {
            FileLog.write("$pkg in front (user-set takeover) -> taking over")
            paymentScreenSeenAt = 0L
            performGlobalAction(GLOBAL_ACTION_BACK)
            bringAppToFront()
            return
        }

        if (pkg == GOOGLE_PAY_PACKAGE) {
            if (!prefs.googlePayTakeover) {
                FileLog.write("google pay in front - takeover is off, leaving it alone")
                paymentScreenSeenAt = 0L
                return
            }
            FileLog.write("google pay in front -> taking over")
            performGlobalAction(GLOBAL_ACTION_BACK)
            bringAppToFront()
            return
        }

        val isPaymentScreen = pkg == MIR_PAY_PACKAGE && cls == MIR_PAY_PAYMENT_SCREEN
        if (!isPaymentScreen) {
            // Anything else in front means the user went elsewhere - the chain is broken.
            paymentScreenSeenAt = 0L
            return
        }

        onPaymentScreen()
    }

    /**
     * Mir Pay's payment screen appearing twice in a row, with nothing seen in between, is the trace
     * of a PIN or pattern prompt: the system credential screen is a secure window that this
     * service never receives, so from here it looks like Mir Pay handing over to something
     * invisible and then coming back. The first appearance must be left alone - taking over there
     * would cover the prompt before it could be answered. The second is Mir Pay back after the
     * unlock, and since Mir Pay cannot keep the screen awake on this firmware, the payment would
     * fail on the next wrist turn - so that is where the card is brought up.
     */
    private fun onPaymentScreen() {
        val now = SystemClock.elapsedRealtime()
        val windowMs = prefs.unlockWindowSeconds * 1000L

        if (paymentScreenSeenAt != 0L && now - paymentScreenSeenAt < windowMs) {
            FileLog.write("mir pay back after an unseen screen -> bringing the card up")
            paymentScreenSeenAt = 0L
            bringAppToFront()
        } else {
            FileLog.write("mir pay payment screen - waiting to see if it returns")
            paymentScreenSeenAt = now
        }
    }

    /**
     * Records - only records, nothing is done about it yet - the trace Mir Pay leaves when it gets
     * stuck: the PIN or pattern screen flashes and is gone, and the watch goes back to the watch
     * face, all within a second or so of the payment screen and without a success screen. Kept to
     * the log until enough real cases show the pattern never fires on a working payment.
     */
    private fun watchForMirPayFailure(pkg: String, cls: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == MIR_PAY_PACKAGE) {
            // The payment screen starts the watch; any other Mir Pay window (the success sheet)
            // means Mir Pay moved on normally.
            failureWatchSince = if (cls == MIR_PAY_PAYMENT_SCREEN) now else 0L
            return
        }
        if (failureWatchSince == 0L) return

        val elapsed = now - failureWatchSince
        if (elapsed > MIR_PAY_FAILURE_WINDOW_MS) {
            failureWatchSince = 0L
            return
        }
        if (pkg == WATCH_FACE_PACKAGE && cls == WATCH_FACE_CLASS) {
            FileLog.write(
                "MIR PAY FAILURE suspected: watch face ${elapsed}ms after the payment screen, " +
                    "no success screen"
            )
            failureWatchSince = 0L
        }
    }

    /**
     * Force-stops Mir Pay on the user's behalf. Called right before the card opens Mir Pay's app
     * info page: from then on the screen is checked every few hundred ms, "Force stop" is pressed
     * once the page is up, then the check mark in the confirmation dialog, and the card is brought
     * back. Nothing is pressed unless the page is Mir Pay's own; if any step does not show up in
     * time, it stops trying and leaves the user on the page to finish by hand.
     */
    fun forceStopMirPay() {
        FileLog.write("force stop: started")
        forceStopStep = ForceStopStep.PRESS_STOP
        forceStopDeadline = SystemClock.elapsedRealtime() + FORCE_STOP_TIMEOUT_MS
        forceStopStatus = ""
        handler.removeCallbacks(forceStopTick)
        handler.postDelayed(forceStopTick, FORCE_STOP_POLL_MS)
    }

    private val forceStopTick = object : Runnable {
        override fun run() {
            if (forceStopStep == ForceStopStep.IDLE) return
            if (SystemClock.elapsedRealtime() > forceStopDeadline) {
                FileLog.write("force stop: gave up at $forceStopStep - left to the user")
                forceStopStep = ForceStopStep.IDLE
                return
            }
            val root = findSettingsRoot()
            if (root != null) {
                when (forceStopStep) {
                    ForceStopStep.PRESS_STOP -> if (pressForceStop(root)) {
                        forceStopStep = ForceStopStep.CONFIRM
                    }
                    ForceStopStep.CONFIRM -> if (pressConfirm(root)) {
                        FileLog.write("force stop: done -> bringing the card up")
                        forceStopStep = ForceStopStep.IDLE
                        // A moment for the dialog to close before the card replaces it.
                        handler.postDelayed({ bringAppToFront() }, 500L)
                        return
                    }
                    ForceStopStep.IDLE -> return
                }
            }
            handler.postDelayed(this, FORCE_STOP_POLL_MS)
        }
    }

    /**
     * The Settings window's content, looked for among all windows on screen rather than only the
     * "active" one: on the watch the active window is not necessarily the one in front, and the
     * first attempt that relied on it never saw Settings at all.
     */
    private fun findSettingsRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.takeIf { it.packageName?.toString() == SETTINGS_PACKAGE }
            ?.let { return it }
        val roots = windows.mapNotNull { it.root }
        val settings = roots.firstOrNull { it.packageName?.toString() == SETTINGS_PACKAGE }
        if (settings == null) {
            logForceStopStatus(
                "settings not found; active=${rootInActiveWindow?.packageName}, " +
                    "windows=${roots.map { it.packageName }}"
            )
        }
        return settings
    }

    private fun logForceStopStatus(status: String) {
        if (status == forceStopStatus) return
        forceStopStatus = status
        FileLog.write("force stop: $status")
    }

    /** Presses "Force stop" if this is Mir Pay's app info page and the button is there. */
    private fun pressForceStop(root: AccessibilityNodeInfo): Boolean {
        val mirPayLabel = appLabel(MIR_PAY_PACKAGE)
        if (mirPayLabel == null) {
            logForceStopStatus("cannot read mir pay's name")
            return false
        }
        val isMirPayPage = root.findAccessibilityNodeInfosByText(mirPayLabel)
            .any { it.text?.toString() == mirPayLabel }
        if (!isMirPayPage) {
            logForceStopStatus("settings open, but no \"$mirPayLabel\" title (yet)")
            return false
        }

        val labels = forceStopLabels()
        val seen = mutableListOf<String>()
        for (label in labels) {
            // findAccessibilityNodeInfosByText matches substrings; only the exact label counts,
            // not e.g. the dialog title that starts with the same word. The text sits on a label
            // inside the button, not on the button itself, so the button is the nearest
            // clickable ancestor of that label.
            val matches = root.findAccessibilityNodeInfosByText(label)
                .filter { it.text?.toString() == label }
            val buttons = matches.map { clickableSelfOrAncestor(it) }
            seen += "\"$label\": ${matches.size} label(s), " +
                "${buttons.count { it != null }} with a clickable button"
            val button = buttons.firstOrNull { it != null } ?: continue
            if (!button.isEnabled) {
                FileLog.write("force stop: button is disabled - mir pay already stopped")
                forceStopStep = ForceStopStep.IDLE
                return false
            }
            FileLog.write("force stop: pressing \"$label\"")
            return button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        logForceStopStatus("mir pay page open, but no force-stop button: $seen")
        return false
    }

    /** The node itself if clickable, else its nearest clickable ancestor (a few levels up). */
    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val n = current ?: return null
            if (n.isClickable) return n
            current = n.parent
        }
        return null
    }

    /** Presses the check mark once the confirmation dialog is up. */
    private fun pressConfirm(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByViewId(DIALOG_TITLE_ID).isEmpty()) {
            logForceStopStatus("waiting for the confirmation dialog")
            return false
        }
        val ok = root.findAccessibilityNodeInfosByViewId(DIALOG_OK_ID).firstOrNull()
        if (ok == null) {
            logForceStopStatus("dialog open, but no check-mark button")
            return false
        }
        FileLog.write("force stop: confirming")
        return ok.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    /** Settings' own "Force stop" label in the current language first, then the fallbacks. */
    private fun forceStopLabels(): List<String> {
        val own = try {
            val res = packageManager.getResourcesForApplication(SETTINGS_PACKAGE)
            val id = res.getIdentifier("force_stop", "string", SETTINGS_PACKAGE)
            if (id != 0) res.getString(id) else null
        } catch (e: Exception) {
            null
        }
        return listOfNotNull(own) + FORCE_STOP_FALLBACK_LABELS
    }

    /**
     * Takes over a warm-up as it launches Mir Pay: waits for Mir Pay to load, closes it again and
     * brings the card back. There is no fixed duration - Mir Pay takes anywhere from 2 to over
     * 5 s to load after a force-stop, and a timer either cut it short or left it open.
     *
     * Closing it: Back and the crown only send Mir Pay to the background; a swipe-to-dismiss
     * closes it, but not on its main screen, where a sideways swipe flips between cards. So once
     * the tab bar is there, the "Devices" tab is pressed, and once that tab is showing - whoever
     * selected it - the swipe is made. See warmUpTick.
     *
     * The card is brought back by the window events (onMirPayLeft) as soon as Mir Pay, having
     * been seen, is no longer in front - whether the swipe closed it or the user pressed the crown
     * or swiped it away themselves, at any point.
     */
    fun startWarmUpWatch() {
        closingMirPay = true
        mirPaySeen = false
        devicesTabPressed = false
        handler.removeCallbacks(warmUpTick)
        handler.postDelayed(warmUpTick, TAB_SWITCH_POLL_MS)
    }

    /** Whether a warm-up is in progress: Mir Pay is to be closed and the card brought back. */
    private var closingMirPay = false

    /**
     * Whether Mir Pay has appeared since the warm-up started - before that, "not in front" means
     * Mir Pay is still starting, not that it is gone.
     */
    private var mirPaySeen = false

    private var devicesTabPressed = false

    /**
     * Polls Mir Pay's screen: presses "Devices" once the tab bar is there, swipes once that tab is
     * showing. A tab bar appearing or a tab switch inside Mir Pay is not a window change, so there
     * is no event for either. Stops on its own once Mir Pay leaves the screen.
     */
    private val warmUpTick = object : Runnable {
        override fun run() {
            if (!closingMirPay) return
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == MIR_PAY_PACKAGE) {
                if (findDevicesTab(root, clickable = false)?.isSelected == true) {
                    FileLog.write("warm-up: tab is showing")
                    dispatchDismissSwipe()
                    return
                }
                if (!devicesTabPressed) {
                    val tab = findDevicesTab(root)
                    if (tab != null && tab.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        FileLog.write("warm-up: mir pay loaded, opened its \"$MIR_PAY_DEVICES_TAB\" tab")
                        devicesTabPressed = true
                    }
                }
            }
            handler.postDelayed(this, TAB_SWITCH_POLL_MS)
        }
    }

    /**
     * The tab bar items carry no usable id (obfuscated), only their description. Mir Pay makes the
     * tab that is showing non-clickable, so [clickable] is required only when the tab is about to
     * be pressed, not when checking whether it is showing.
     */
    private fun findDevicesTab(
        root: AccessibilityNodeInfo,
        clickable: Boolean = true,
    ): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByText(MIR_PAY_DEVICES_TAB).firstOrNull {
            it.contentDescription?.toString() == MIR_PAY_DEVICES_TAB && (!clickable || it.isClickable)
        }

    /** A left-to-right swipe across the middle of the screen, as a person dismisses a screen. */
    private fun dispatchDismissSwipe() {
        val metrics = resources.displayMetrics
        val y = metrics.heightPixels / 2f
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.05f, y)
            lineTo(metrics.widthPixels * 0.85f, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS))
            .build()
        FileLog.write("warm-up: swiping mir pay away")
        val dispatched = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                FileLog.write("warm-up: swipe was cancelled - waiting for mir pay to be closed by hand")
            }
        }, handler)
        if (!dispatched) {
            FileLog.write("warm-up: swipe could not be dispatched - waiting for mir pay to be closed by hand")
        }
    }

    /**
     * Called for every window event while Mir Pay is being closed. A window from another app only
     * counts once the active window is no longer Mir Pay - overlays such as a notification or the
     * system bar also send events without taking Mir Pay off the screen.
     */
    private fun onMirPayLeft(pkg: String) {
        if (!closingMirPay) return
        if (pkg == MIR_PAY_PACKAGE) {
            mirPaySeen = true
            return
        }
        if (!mirPaySeen) return
        val active = rootInActiveWindow?.packageName?.toString()
        if (active == MIR_PAY_PACKAGE) return
        closingMirPay = false
        handler.removeCallbacks(warmUpTick)
        if (pkg == packageName) {
            FileLog.write("warm-up: done - mir pay closed, the card is already back")
        } else {
            FileLog.write("warm-up: done - mir pay left the screen ($pkg) -> bringing the card back")
            bringAppToFront()
        }
    }

    private fun bringAppToFront() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        /**
         * The running service, for the card screen to call into - same process. Null while the
         * accessibility permission is not granted.
         */
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var instance: PayFacadeAccessibilityService? = null
            private set
    }
}
