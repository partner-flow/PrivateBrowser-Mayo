# Private Browser (Android)

A privacy-focused Android web browser: WebView-based browsing, a clearly indicated Private Mode,
local history/bookmarks, host-list ad/tracker blocking, and an optional PIN/biometric app lock.
Everything is stored on-device — the app has no backend and sends nothing about your browsing
anywhere. The UI uses one consistent premium palette (no Android 12+ wallpaper-based color) across
every screen, and the top address bar is the only place to type a search or URL — there's no
second search field on the home screen.

The toolbar is two rows on phone-width screens and one row on tablets/large windows, switching at
the standard 600dp breakpoint based on the bar's own measured width (`BoxWithConstraints`), not a
hardcoded screen size. Every control — Home, Back, Forward, Reload, the address field, 🔥 All
Clear, Tabs, Menu — stays visible and full-size on every supported width; nothing is hidden,
nothing scrolls, and no icon is shrunk below the standard 48dp touch target. The two-row layout
exists because the arithmetic genuinely doesn't work otherwise: seven full-size icon buttons alone
total over 330dp, which is most or all of a 320-412dp phone's width before the address field gets
anything, and a near-zero-width text field is what causes single-line text to collapse into a
one-character-per-line column. Splitting the controls across two short rows (address field + the
three buttons next to it on top, navigation buttons in a row underneath) keeps every control at
full size with comfortable room for the address field at every width from 320dp up.

This is a first working version, prioritized in this order: browsing works → private browsing
works correctly → tabs → history/bookmarks → clear browsing data → security → UI polish →
ad/tracker blocking → testing/store prep.

## Requirements

- Android Studio (Koala/2024.1 or newer recommended)
- JDK 17 (bundled with recent Android Studio)
- Android SDK Platform 34, Build-Tools matching AGP 8.5.x

## Opening the project

1. Open Android Studio → **File → Open** → select the `PrivateBrowser` folder (the one containing
   `settings.gradle.kts`).
2. Let Gradle sync. On first sync it will download the Gradle 8.7 wrapper distribution and the
   dependencies listed in `app/build.gradle.kts` — this needs an internet connection the first time.

> **Note on the Gradle wrapper:** this project includes `gradlew`, `gradlew.bat`, and
> `gradle/wrapper/gradle-wrapper.properties` (pointing at Gradle 8.7). It does **not** include
> `gradle/wrapper/gradle-wrapper.jar` — that file is a compiled binary that Gradle itself
> distributes, and it was generated in an offline, network-isolated environment with no Gradle
> installation to produce it from and no way to download the authentic one. Rather than ship a
> fabricated binary, this step is left for you to run once, and it's fast:
>
> - **Easiest:** open the project in Android Studio. Studio bundles its own Gradle and
>   auto-generates `gradle-wrapper.jar` the first time it syncs a project that's missing one — no
>   action needed beyond opening the project.
> - **Command line, if you have any Gradle installed already** (via SDKMAN, Homebrew, apt, etc.):
>   run this once from the project root:
>   ```bash
>   gradle wrapper --gradle-version 8.7 --distribution-type bin
>   ```
>   That regenerates `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` together and is safe to run
>   even though `gradlew`/`gradlew.bat` already exist — it just overwrites them with the same
>   content plus the jar.
>
> After either step, `./gradlew assembleDebug` from the project root works as expected.

## Running on a device or emulator

1. Connect a device with USB debugging enabled, or start an emulator (API 24+; API 34 recommended)
   from Android Studio's Device Manager.
2. Click **Run ▶** (or `Shift+F10`). Android Studio installs and launches the `debug` build variant.

## Generating a debug APK from the command line

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Generating a release AAB (for Google Play)

1. Create a signing key if you don't have one:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias private_browser \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Configure signing (recommended: put these in `~/.gradle/gradle.properties` or environment
   variables, **not** committed to source control), then add a `signingConfigs` block referencing
   them in `app/build.gradle.kts`'s `android {}` section and attach it to the `release` build type.
3. Build:
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`.

This first version ships without a signing config wired in on purpose — add your own keystore
before shipping, and never hard-code its passwords in `build.gradle.kts`.

## Where things live

| Area | Path |
|---|---|
| Gradle / module config | `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` |
| Manifest & permissions | `app/src/main/AndroidManifest.xml` |
| Navigation / app-lock gate | `app/src/main/java/.../MainActivity.kt` |
| Browser UI, address bar, WebView, tabs, error screen | `app/src/main/java/.../ui/BrowserScreen.kt` |
| History / Bookmarks / Settings / Privacy info / Lock UI | `app/src/main/java/.../ui/*.kt` |
| Tab state, private-mode logic, clear-data logic | `app/src/main/java/.../browser/BrowserViewModel.kt` |
| Ad/tracker blocker | `app/src/main/java/.../browser/AdBlocker.kt` + `app/src/main/assets/blocklist.txt` |
| Downloads | `app/src/main/java/.../browser/DownloadsManager.kt`, `ui/DownloadsScreen.kt` |
| PIN hashing / secure storage | `app/src/main/java/.../security/AppLockManager.kt` |
| Local history/bookmarks storage | `app/src/main/java/.../data/HistoryRepository.kt`, `BookmarkRepository.kt` |
| Settings storage (DataStore) | `app/src/main/java/.../data/SettingsRepository.kt` |
| Tests | `app/src/test/...` (unit), `app/src/androidTest/...` (instrumented) |

## The 🔥 All Clear button

The rightmost button in the top bar is a one-tap reset, separate from Settings → "Clear browsing
data" (which lets you pick what to clear). All Clear always asks for confirmation first, then:

- Closes every tab, normal and private
- Clears all history
- Clears cookies and WebView site data (this also signs you out of sites in normal tabs, the same
  trade-off described below for closing private tabs)
- Clears the cache
- Opens one fresh normal tab so the browser lands in a clean, default state

Bookmarks and your Settings (search engine, theme, app lock, etc.) are **not** touched by All
Clear — those are things you chose to keep, not transient browsing data.

## Updating the tracker/ad blocklist

Edit `app/src/main/assets/blocklist.txt` — one host per line, `#` for comments. `AdBlocker`
(`app/src/main/java/.../browser/AdBlocker.kt`) loads it at startup and blocks any sub-resource
request whose host matches an entry or is a subdomain of one. This is intentionally a simple,
maintainable static list rather than a full filter-list engine (no cosmetic rules, no
regex/URL-pattern matching) — see "Known limitations" below.

There is no remote/auto-update mechanism by design (the app doesn't phone home). To ship an
updated list, edit the file and release a new app version. If you later want live updates, the
clean extension point is to fetch a signed list over HTTPS on a background schedule and fall back
to the bundled file — `AdBlocker.load()` is the single place that would need to change.

## Known limitations (documented honestly, not hidden)

- **Per-tab private isolation.** Android's stock `WebView` shares one cookie/site-data store
  process-wide; it doesn't give each tab its own storage container. This app clears that shared
  store when the last private tab closes, which achieves "private data doesn't outlive the private
  session" but does **not** give you simultaneous, mutually-isolated private and normal sessions
  the way a desktop browser's separate profiles would. A future version could add per-tab
  isolation via multiple `WebView` processes or a wrapper like a Custom Tabs-based architecture —
  that's a substantially larger change intentionally left out of this first version. The 🔥 All
  Clear button and "Clear browsing data" → Cookies both hit this same shared store, so either one
  also signs you out of sites in your normal tabs.
- **Ad blocking is a static host list**, not a full content-blocking engine — see above.
- **History/bookmarks storage** uses simple JSON files in app-private storage rather than a
  database (Room/SQLite). This keeps the first version's code small and dependency-light; it's a
  clean drop-in upgrade later if the data volume grows (swap the repository internals, keep the
  same public API).
- **No HTTP→HTTPS warning banner yet** — the WebView blocks invalid/expired certificates outright
  (see `onReceivedSslError` in `BrowserWebView`), but there's no separate "this page is not secure"
  interstitial for plain HTTP pages in this first version.
- **Downloads.** Handled via Android's system `DownloadManager` (`browser/DownloadsManager.kt`),
  wired to `WebView.setDownloadListener`. Files land in the public Downloads collection; a
  Downloads screen (`ui/DownloadsScreen.kt`) shows progress/complete/failed state and lets you
  open a finished file or remove an entry. Opening a file resolves its URI via
  `DownloadManager.getUriForDownloadedFile()` rather than the raw local-file column, which avoids
  a `FileUriExposedException` crash on API 24–28. On API 28 and below this requests the legacy
  `WRITE_EXTERNAL_STORAGE` permission at first use; API 29+ needs no runtime permission (scoped
  storage). Not yet included: pause/resume controls and a persistent in-app record of *private*
  downloads separate from the system list (system DownloadManager entries aren't added to this
  app's own browsing history either way, so the "don't add private downloads to history"
  requirement is satisfied by construction).
- **Instrumented tests** included are a minimal smoke test, not full coverage of every flow;
  `app/src/test` unit tests currently cover the URL-vs-search heuristic in isolation. Expanding
  both is called out as follow-up work, not attempted here to keep the first version's scope
  manageable.

## Privacy honesty notes

Per the design brief, this app does **not** claim to make you anonymous, invisible, or untraceable
online, and Private Mode is explicitly not presented as equivalent to a VPN or Tor. See the in-app
Settings → Privacy information screen (`PrivacyInfoScreen.kt`) for the exact language shown to
users.


## Build status (read this first)

This source tree has **not been compiled or run**. It was prepared in a sandbox with no Android SDK,
no JDK compiler, no Gradle and no network access, so `./gradlew assembleDebug`, the unit tests and
the instrumented tests have never been executed here. Every change was applied by reading the code,
and checked as far as that allows — brace/paren balance, every call site matching its function
signature, every resource the manifest references actually defined, no stray/duplicate
implementations, and the icon names used all existing in the Material icon set. The first real
compiler this touches is Android Studio or the GitHub Actions workflow below.

`gradle/wrapper/gradle-wrapper.jar` is also missing (it is a binary that could not be downloaded
there). Generate it once with `gradle wrapper --gradle-version 8.7 --distribution-type bin`, or just
open the project in Android Studio — both are described above.

## Building the APK

```bash
./gradlew clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (application ID `com.privacybrowser.app.debug`;
the release ID is `com.privacybrowser.app`). Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Versions: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, Compose compiler 1.5.14, compileSdk/targetSdk 34, minSdk 24, JDK 17.

## Troubleshooting

- *"Unsupported class file major version"*: run Gradle with JDK 17, not a newer JDK.
- *"Could not find gradle-wrapper.jar"*: see the wrapper note above.
- *SDK not found*: create `local.properties` with `sdk.dir=/path/to/Android/Sdk`, or set `ANDROID_HOME`.
- *Sites signed out unexpectedly*: closing the last private tab clears the shared WebView cookie store.


## Building the APK with GitHub Actions (no local setup needed)

The workflow at `.github/workflows/android-build.yml` builds the app on a GitHub-hosted
`ubuntu-24.04` runner with JDK 17, Android SDK 34 and Gradle 8.7 (compatible with AGP 8.5.2). If
`gradle/wrapper/gradle-wrapper.jar` is missing, the workflow generates the real one with Gradle
itself. It then runs `./gradlew clean`, `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`,
and fails if compilation or any test fails.

1. Create a new repository on GitHub and push this project's contents to the `main` branch
   (the `.github` folder must be at the repository root).
2. Open the repository's **Actions** tab. If prompted, enable workflows.
3. The build starts automatically on every push. To start it manually, choose
   **Android CI** in the left list, then **Run workflow**.
4. Wait for the run to finish (usually 5-15 minutes the first time). A green check means it built.
5. Open the finished run and scroll to **Artifacts** at the bottom of the summary page:
   - `private-browser-debug-apk` contains `app-debug.apk` (built at `app/build/outputs/apk/debug/app-debug.apk`).
   - `build-and-test-reports` contains the test and build reports (also uploaded when a run fails).
6. Download the artifact zip, extract `app-debug.apk`, and install it on your phone
   (allow installs from your file manager or browser when Android asks).

The APK exists only after a run succeeds. If a run fails, open the failed step's log to see the
exact error and download the reports artifact for details.
