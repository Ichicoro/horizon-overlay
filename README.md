# HTML Feed

Puts a web page on your launcher's -1 screen, the panel you get by swiping right from
the first home screen.

The app implements the GSA overlay protocol that Pixel Launcher and the Google app use,
so any launcher that can take a third-party feed provider (Lawnchair, Neo Launcher) can
show it. The panel itself is one WebView.

## Build and install

```
./gradlew :app:installDebug
```

Needs JDK 21. Gradle, AGP and the Android SDK come from the wrapper and
`gradle/libs.versions.toml`; compileSdk is 37 and minSdk is 26.

## Setting the page

Open HTML Feed from the app drawer and type a URL. Leaving off the scheme is fine,
`example.com` becomes `https://example.com`. Reset puts back the default.

A local page works too: put it in `app/src/main/assets/` and use
`file:///android_asset/index.html`. The bundled `index.html` is a clock on a transparent
background, which is what the overlay is set up for.

The overlay reloads on resume, so a change takes effect the next time you swipe.

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

`app` is the launcher icon (a settings screen) and the overlay. `google-gsa` is the
overlay protocol: the AIDL-equivalent binder interfaces, the sliding panel, and the
controller the launcher drives. All Kotlin.

Two things to know about `HtmlOverlay` before changing it:

The overlay window belongs to a Service, so there's no Activity to supply the owners
Compose needs. `HtmlOverlay` is its own `LifecycleOwner` and `SavedStateRegistryOwner`,
and both get set on `slidingPanelLayout` rather than on the `ComposeView`. Compose
resolves the recomposer from the window's content view, and that's the content view.
Putting them on the `ComposeView` alone crashes on attach.

`onCreate` moves the lifecycle to STARTED itself instead of waiting for `onStart`.
Recomposition is paused below STARTED and the launcher doesn't promise to send an
activity state, so otherwise the panel can come up blank.

## Fade

The scrim and the page fade in with the swipe. `onScroll` gets 0..1 for every frame of
the drag and the settle animation, and that value drives the scrim alpha and the
WebView's alpha. The sliding panel's own alpha handling is compiled out
(`ENABLE_ALPHA = false`), so this has to come from the overlay.
