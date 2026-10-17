# Pay Facade

**English** | [Русский](README.ru.md)

A minimal Wear OS companion for Mir Pay that keeps the watch screen genuinely awake so an NFC
payment goes through when you turn your wrist towards the terminal

Open it, hold the watch to the reader, done. A countdown closes the app on its own so a payment
screen is never left up on your wrist

## Why it exists

On some watches — OnePlus / OPPO firmware in particular — turning the wrist away puts the display
into ambient mode. The screen looks lit but is really in `DOZE`, and `NfcService` reads the display
state rather than the brightness, so the card is not read and the payment fails

Neither `FLAG_KEEP_SCREEN_ON` nor a wake lock helps: the firmware calls `goToSleep()` directly, and
that call cannot be vetoed by an app. What does work is getting in before the transition commits —
on `onEnterAmbient` the app calls `setTurnScreenOn(true)` and relaunches itself `singleTop`, which
makes the system walk the "bring activity to front" path again. The display never reaches `DOZE`,
so it never dims and NFC keeps working

Screen brightness is pinned at 60% at the window level while the app is open. That overrides
adaptive brightness — which otherwise dims the screen visibly on wrist turns — and the system
restores it automatically on exit, so no `WRITE_SETTINGS` permission is needed

## Features

- White wallet pictogram on black, with a countdown underneath (10 s by default, 5–60 s);
- **Double-tap the card to add 5 seconds** — useful when a terminal is slow to read;
- **Long-press the card to force-stop Mir Pay** — see [When Mir Pay gets stuck](#when-mir-pay-gets-stuck);
- Swipe left for settings;
- The crown (home) closes the app rather than backgrounding it

Settings are grouped into sections:

- **Card** — countdown length;
- **Vibration** — buzz on start, buzz on finish, buzz length;
- **Takeover** (needs accessibility access):
  - **Return after unlock** — when a payment asks for a PIN or pattern, Mir Pay shows it over the
    card and the card is gone by the time you are done. Pay Facade notices Mir Pay coming back
    after the unlock and brings the card up again, so the second tap goes through. *Takeover
    window* — how long to wait for Mir Pay to come back after the first tap (90 s by default);
  - **Replace Google Pay** — opening Google Pay brings up Pay Facade instead. Off by default;
  - **Replace any app** — the same for any app you name by its package. For advanced use;
- **Warm-up** — after a force-stop, open Mir Pay once, wait for it to load and close it again
  before the card comes back. Off by default; not needed on a OnePlus Watch 4, kept for watches
  where Mir Pay may need it

## When Mir Pay gets stuck

Now and then Mir Pay gets into a state where the PIN or pattern prompt flashes up and is
immediately covered by Mir Pay's own screen, so the code cannot be entered. Only a force-stop of
Mir Pay clears it

Keep in mind what the terminal is doing at that moment: once it has asked for the code, it is
waiting for a *second* tap after the code is entered. A tap with a freshly restarted Mir Pay is a
*first* tap again, and the terminal rejects it. So:

1. Cancel the payment on the terminal;
2. Long-press the card — Pay Facade opens Mir Pay's app info page, presses *Force stop* and
   confirms for you, then brings the card back;
3. Start the payment on the terminal again and tap;
4. Enter the code when asked; the card comes back on its own, and the second tap pays

## Permissions

**Accessibility** — needed for the takeover features, for force-stopping Mir Pay and for the
warm-up. Without it the card and its countdown work as usual, and the long-press just opens Mir
Pay's app info page for you to press *Force stop* yourself. Grant it from the settings page inside
the app; the tile there shows the real state

The service is declared with `android:isAccessibilityTool="true"`. Pay Facade is not an
accessibility tool, but without that flag the system hides the Settings window from the service and
it cannot press *Force stop*. The service sees which app is in front (package and screen names go
into the log), but acts only on Mir Pay, Google Pay, the app you name yourself and Mir Pay's app
info page, and the force-stop runs only when you long-press the card

## Logs

Pay Facade writes what it sees and does to a log file on the watch — handy when something goes
wrong:

```
adb pull /sdcard/Android/data/com.bazyak.payfacade/files/pay-facade.log
```

## Build

Nothing special is required — open in Android Studio and run

Note that debug builds are noticeably slow on watch hardware: Compose instrumentation is enabled
and baseline profiles are ignored for debuggable apps. Judge performance on a release or
profileable build
