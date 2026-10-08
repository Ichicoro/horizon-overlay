# HTML Feed

A small personal hub on your launcher's -1 screen, the panel you get by swiping right from
the first home screen.

The panel is a stack of modules — clock, contacts, weather, agenda, and a web page — that you
turn on and put in whatever order you like. The app implements the GSA overlay protocol that
Pixel Launcher and the Google app use, so any launcher that can take a third-party feed
provider (Lawnchair, Neo Launcher) can show it. All Kotlin, Compose throughout.

## Build and install

```
./gradlew :app:installDebug
```

Needs JDK 21. Gradle, AGP and the Android SDK come from the wrapper and
`gradle/libs.versions.toml`; compileSdk is 37 and minSdk is 26.

## Modules

Open HTML Feed from the app drawer. Every module is a row: the switch turns it on, the arrows
move it, and tapping the row opens its options.

**Clock** — time, date, and a greeting. Sits at the top as a header rather than a card.

**Contacts** — the contacts you've starred, as a row of faces. Tap someone to open their
contact, hold to go straight to the dialer. Needs `READ_CONTACTS`.

**Quick navigation** — every address saved on a contact, one tap from directions. A contact with
both a home and a work address gets a row each, since telling them apart is the point. Settings
has a checklist to narrow it down; until something is unticked the stored selection is null,
meaning "all of them", so a newly saved address shows up without a trip through settings.
`google.navigation:` starts directions where a maps app handles it and `geo:` is the fallback -
both are declared in `<queries>`, without which the check comes back null on Android 11+ and
everything would take the fallback. Needs `READ_CONTACTS`.

**Weather** — conditions now, the day's range, and the next six hours. Search for a town in
settings and pick it; there's no location permission involved. Forecasts come from
[Open-Meteo](https://open-meteo.com), which needs no account and no API key, and are cached for
15 minutes so swiping the panel doesn't mean a request each time.

**Agenda** — the next few calendar events, each one a tap from the calendar app. Reads
`Instances`, so a repeating event shows up as the occurrence that's actually next. Needs
`READ_CALENDAR`.

**Web page** — the overlay this app started as: any URL in a WebView. Type one in settings;
leaving off the scheme is fine, `example.com` becomes `https://example.com`. A local page works
too — put it in `app/src/main/assets/` and use `file:///android_asset/index.html`.

With every other module switched off, the page gets the whole panel with no card around it,
which is exactly what the app used to do.

Both permissions are asked for from the settings screen, not from the overlay: the overlay has
no Activity to show a dialog from. Until they're granted the modules show a placeholder with a
shortcut back to settings.

The overlay re-reads settings and refetches on resume, so a change takes effect the next time
you swipe. Pulling the stack down refetches on demand, and drops the weather cache first so the
pull isn't just handing back the same reading.

The refresh indicator is driven by the weather fetch, which is the only module that waits on the
network - `HubScreen` owns that load for exactly this reason, rather than letting `WeatherCard`
fetch for itself like the local-query modules do. The page module keeps the gesture for itself:
it scrolls on its own.

## Opening apps from the panel

Tapping a contact or an event is a background activity start, and the usual exemption - "the
calling app has a visible window" - is out of reach here. The panel's window hangs off the
*launcher's* activity token, so WindowManager attributes it to the launcher; the system sees
this app with nothing on screen and drops the start with `BAL_BLOCK`. The panel shuts and
nothing opens, which looks exactly like a tap that went to the home screen.

So the app asks for `SYSTEM_ALERT_WINDOW`, which is the only exemption left to it. It draws
nothing over anything - the permission is there for the exemption alone. The settings screen
leads with a card explaining that and a button to the system toggle, and the card goes away
once it's granted.

Two things follow from this, worth remembering before changing `hubActions`:

Start the activity *before* closing the panel. Closing it first sets `isVisible = false`, which
zeroes the window alpha and puts `FLAG_NOT_TOUCHABLE` back on - and with it any chance of a
visible-window exemption, should a future Android grant one.

`BAL_ALLOW_GRACE_PERIOD` will make taps work for about ten seconds after the settings activity
has been on screen. That makes for a convincing false positive while testing. Check `adb logcat`
for the exemption actually used:

```
adb logcat | grep "uid .* (sh.zelda.htmlfeed)"
```

`BAL_ALLOW_SAW_PERMISSION result code=0` is the one that means it's really working.

## Picking it in Lawnchair

Lawnchair only lists feed providers whose signature is on a hardcoded whitelist
(`FeedBridge.kt`), and this app isn't on it. To get around that:

1. Open the app drawer and type `/lawnchairdebug` in the search box. This toggles the
   debug menu and drops you back to the home screen.
2. Open Lawnchair settings. There's a wrench icon in the top bar now; tap it.
3. Under "Debug flags", turn on `pref_ignoreFeedWhitelist`.
4. Home settings, Home screen, Feed provider, pick HTML Feed.

The flag has to stay on. It gates the binding, not just the picker.

## Layout

`app` is the launcher icon (the settings screen) and the overlay; `google-gsa` is the overlay
protocol — the AIDL-equivalent binder interfaces, the sliding panel, and the controller the
launcher drives.

Inside `app`:

- `Settings.kt` — the module order and every module's options, read in one pass as a
  `HubSettings` so nothing below the overlay touches a `Context`.
- `HubModule.kt` — what modules exist. The `id` is what gets persisted.
- `SettingsScreen.kt` — the organizer.
- `Type.kt` — Google Sans Flex, looked up as the device's named family `google-sans-flex`
  rather than bundled. It's a variable font, so one file covers every weight; where the family
  isn't installed the lookup resolves to the system default on its own.
- `HubOverlay.kt` — the overlay window.
- `hub/` — one file per module, each with its card and, where it needs one, its data source.

Modules open apps through `LocalHubActions` rather than a context of their own, because the
overlay has to close the panel before it hands off.

Two things to know about `HubOverlay` before changing it:

The overlay window belongs to a Service, so there's no Activity to supply the owners
Compose needs. `HubOverlay` is its own `LifecycleOwner` and `SavedStateRegistryOwner`,
and both get set on `slidingPanelLayout` rather than on the `ComposeView`. Compose
resolves the recomposer from the window's content view, and that's the content view.
Putting them on the `ComposeView` alone crashes on attach.

`onCreate` moves the lifecycle to STARTED itself instead of waiting for `onStart`.
Recomposition is paused below STARTED and the launcher doesn't promise to send an
activity state, so otherwise the panel can come up blank.

## Fade

The scrim and the content fade in with the swipe. `onScroll` gets 0..1 for every frame of
the drag and the settle animation, and that value drives the scrim alpha and the content's
alpha. The sliding panel's own alpha handling is compiled out
(`ENABLE_ALPHA = false`), so this has to come from the overlay.
