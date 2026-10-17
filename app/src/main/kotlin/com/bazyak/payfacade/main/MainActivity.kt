package com.bazyak.payfacade.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Display
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.ambient.AmbientLifecycleObserver
import com.bazyak.payfacade.R
import com.bazyak.payfacade.common.BONUS_SECONDS
import com.bazyak.payfacade.common.FileLog
import com.bazyak.payfacade.common.MIR_PAY_PACKAGE
import com.bazyak.payfacade.common.OwnActivityLaunchTracker
import com.bazyak.payfacade.common.Preferences
import com.bazyak.payfacade.common.SCREEN_BRIGHTNESS

/**
 * The card screen: a white wallet pictogram on black with a countdown, and nothing else.
 *
 * Built from plain views on purpose. This is the screen that opens at a terminal, so start-up cost
 * matters more than anything here, and a Compose hierarchy has to initialise before it can draw.
 * Settings live in their own activity and pay that cost only when opened.
 */
class MainActivity : ComponentActivity() {

    private companion object {
        /** How dense the pictogram sits on the dial, as a fraction of screen width. */
        const val ICON_WIDTH_FRACTION = 0.58f

        /** Gap between the countdown and the bottom edge, as a fraction of screen height. */
        const val TIMER_BOTTOM_FRACTION = 0.08f

        const val TICK_MS = 200L

        // The display does not commit the ambient transition immediately, so a single reading
        // right after onEnterAmbient always says ON. Probing repeatedly makes a stray DOZE visible.
        const val PROBE_STEP_MS = 200L
        const val PROBE_COUNT = 8
    }

    private val prefs by lazy { Preferences.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private lateinit var secondsLeftView: TextView

    /** When the countdown runs out, on the elapsed-time clock. */
    private var deadlineElapsedMs = 0L

    /** Whether this activity is the one actually in front - see the ambient callback. */
    private var isInForeground = false

    private val tick = object : Runnable {
        override fun run() {
            val left = secondsRemaining()
            secondsLeftView.text = left.toString()
            if (left <= 0) {
                if (prefs.buzzOnFinish) buzz()
                finishAndRemoveTask()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun secondsRemaining(): Int {
        val ms = deadlineElapsedMs - SystemClock.elapsedRealtime()
        return if (ms <= 0) 0 else ((ms + 999) / 1000).toInt()
    }

    private fun startCountdown() {
        deadlineElapsedMs = SystemClock.elapsedRealtime() + prefs.countdownSeconds * 1000L
        if (prefs.buzzOnStart) buzz()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                prefs.buzzLengthMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    private fun displayState(): Int? = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.state

    /** Logs display.state after a wake so a stray DOZE shows up in the log file. */
    private fun probeDisplayState() {
        for (i in 1..PROBE_COUNT) {
            val delay = PROBE_STEP_MS * i
            handler.postDelayed({
                val state = displayState()
                val mark = if (state == Display.STATE_ON) "" else "  <-- NOT ON"
                FileLog.write("probe +${delay}ms: display.state=$state$mark")
            }, delay)
        }
    }

    /**
     * Turning the wrist makes the firmware call goToSleep() directly, which neither
     * FLAG_KEEP_SCREEN_ON nor a wake lock can veto. Instead of recovering from ambient we get in
     * before it commits: setTurnScreenOn plus a singleTop self-relaunch makes the system walk the
     * "bring activity to front" path again, and the display never reaches DOZE. Because it never
     * dims, NFC keeps working - NfcService looks at display state, not at brightness.
     */
    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            FileLog.write("onEnterAmbient: display.state=${displayState()}")
            if (!isInForeground) {
                // Something else is on top - Mir Pay asks for a PIN over this screen after a few
                // payments. Pulling ourselves forward would dismiss it.
                FileLog.write("skip wake: another screen is in front")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(true)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            startActivity(
                Intent(this@MainActivity, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            )
            probeDisplayState()
        }

        override fun onExitAmbient() = FileLog.write("onExitAmbient")
        override fun onUpdateAmbient() = Unit
    }

    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        FileLog.init(this)

        setContentView(R.layout.activity_card)
        secondsLeftView = findViewById(R.id.secondsLeft)
        sizeToScreen()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Window-level brightness overrides adaptive brightness while the app is open and is
        // restored by the system on exit - no WRITE_SETTINGS permission, nothing to clean up.
        window.attributes = window.attributes.apply { screenBrightness = SCREEN_BRIGHTNESS }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(true)
        lifecycle.addObserver(ambientObserver)

        setUpGestures()
        if (savedInstanceState == null) {
            FileLog.write("card created, mir pay stopped flag=${isMirPayStopped()}")
        }
    }

    /**
     * Whether the system currently has Mir Pay in its force-stopped state. Logged only: as the
     * default payment app Mir Pay seems to be started again by the system right after a stop, and
     * the log will show whether this flag is ever still set when the card opens.
     */
    private fun isMirPayStopped(): Boolean? = try {
        (packageManager.getApplicationInfo(MIR_PAY_PACKAGE, 0).flags and
            ApplicationInfo.FLAG_STOPPED) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * After a force-stop the first payment failed the same way Mir Pay's stuck state does. Opening
     * Mir Pay for a few seconds first gives it time to start up; then the card comes back with a
     * fresh countdown (the countdown follows onStart/onStop). Runs once per visit to Mir Pay's app
     * info page - see Preferences.warmUpPending.
     */
    private fun warmUpMirPayIfPending() {
        if (!prefs.warmUpPending) return
        prefs.warmUpPending = false
        if (!prefs.warmUpMirPay) return
        val launch = packageManager.getLaunchIntentForPackage(MIR_PAY_PACKAGE)
        if (launch == null) {
            FileLog.write("warm-up: mir pay has no launch intent - skipped")
            return
        }
        // Closing Mir Pay again and bringing the card back is the service's job; without it Mir
        // Pay would be left open, so there is no warm-up at all.
        val service = PayFacadeAccessibilityService.instance
        if (service == null) {
            FileLog.write("warm-up: no accessibility service - skipped")
            return
        }
        FileLog.write("warm-up: opening mir pay")
        service.startWarmUpWatch()
        // Leaving for Mir Pay must not count as the user leaving: that would close the card.
        OwnActivityLaunchTracker.isLaunchingOwnActivity = true
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Keeps the proportions of the old Compose layout: sizes follow the dial, not fixed dp. */
    private fun sizeToScreen() {
        val metrics = resources.displayMetrics
        findViewById<ImageView>(R.id.walletIcon).layoutParams.apply {
            width = (metrics.widthPixels * ICON_WIDTH_FRACTION).toInt()
            height = width
        }
        (secondsLeftView.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin =
            (metrics.heightPixels * TIMER_BOTTOM_FRACTION).toInt()
    }

    private fun setUpGestures() {
        val root = findViewById<android.view.View>(R.id.cardRoot)
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            /** Undocumented on purpose: buys more time when a terminal is slow to read. */
            override fun onDoubleTap(e: MotionEvent): Boolean {
                deadlineElapsedMs += BONUS_SECONDS * 1000L
                secondsLeftView.text = secondsRemaining().toString()
                return true
            }

            /**
             * Undocumented too: force-stops Mir Pay, for when it gets stuck. An app cannot
             * force-stop another one itself, so this opens Mir Pay's app info page and the
             * accessibility service presses "Force stop" and confirms - or, without the service,
             * leaves the user on that page to press it.
             */
            override fun onLongPress(e: MotionEvent) {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                openMirPayAppInfo()
            }

            override fun onFling(
                down: MotionEvent?, up: MotionEvent, velocityX: Float, velocityY: Float,
            ): Boolean {
                // A fling to the left opens settings. Flings to the right are left alone: that is
                // the system dismiss gesture, and it should close the app.
                if (down != null && down.x - up.x > 80 && kotlin.math.abs(velocityX) > 200) {
                    openSettings()
                    return true
                }
                return false
            }
        })
        root.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
        }
    }

    /**
     * Opens in a task of its own: leaving for it closes this activity (onUserLeaveHint), and
     * finishAndRemoveTask would take the info page down with it if it shared our task.
     */
    private fun openMirPayAppInfo() {
        FileLog.write("long press -> opening mir pay app info")
        // Whether Mir Pay really gets stopped cannot be seen from here; assume it does.
        prefs.warmUpPending = true
        val service = PayFacadeAccessibilityService.instance
        if (service != null) {
            service.forceStopMirPay()
        } else {
            FileLog.write("no accessibility service - force stop left to the user")
        }
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", MIR_PAY_PACKAGE, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            FileLog.write("app info page not available: $e")
        }
    }

    private fun openSettings() {
        OwnActivityLaunchTracker.isLaunchingOwnActivity = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // The countdown is tied to onStart/onStop rather than onResume/onPause on purpose: going
    // ambient keeps the activity resumed, so the countdown carries on across a wrist turn, while
    // opening settings stops it and coming back starts it again from a full interval - with the
    // value just changed there already applied.
    override fun onStart() {
        super.onStart()
        // Logged on every appearance: onCreate (and so "session started") only runs when the
        // activity is created anew, not when an existing one is brought back to the front.
        FileLog.write("card shown")
        startCountdown()
        // In onStart rather than onCreate: the card may come back from memory without onCreate.
        handler.post { warmUpMirPayIfPending() }
    }

    override fun onStop() {
        super.onStop()
        FileLog.write("card hidden")
        handler.removeCallbacks(tick)
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        OwnActivityLaunchTracker.isLaunchingOwnActivity = false
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    /**
     * The crown sends HOME, so it cannot be intercepted as a key. onUserLeaveHint fires only when
     * the user deliberately leaves - unlike onPause it does not fire on the wrist turn - so
     * closing here is safe.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (OwnActivityLaunchTracker.isLaunchingOwnActivity) return
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        lifecycle.removeObserver(ambientObserver)
        super.onDestroy()
    }
}
