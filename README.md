# Private Browser (Android)

A privacy-focused Android web browser: WebView-based browsing, a clearly indicated Private Mode,
local history/bookmarks, host-list ad/tracker blocking, and an optional PIN/biometric app lock.
Everything is stored on-device — the app has no backend and sends nothing about your browsing
anywhere.

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

> **Note on the Gradle wrapper:** this project includes `gradle/wrapper/gradle-wrapper.properties`
> (which points at Gradle 8.7) but not the wrapper `.jar`/`gradlew` scripts themselves, since those
> are binary/generated files. Android Studio regenerates them automatically on first open. If you
> ever need them from the command line first, run `gradle wrapper` once (using any locally
> installed Gradle) inside the project root to generate `gradlew`, `gradlew.bat`, and
> `gradle/wrapper/gradle-wrapper.jar`.

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
| PIN hashing / secure storage | `app/src/main/java/.../security/AppLockManager.kt` |
| Local history/bookmarks storage | `app/src/main/java/.../data/HistoryRepository.kt`, `BookmarkRepository.kt` |
| Settings storage (DataStore) | `app/src/main/java/.../data/SettingsRepository.kt` |
| Tests | `app/src/test/...` (unit), `app/src/androidTest/...` (instrumented) |

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
  that's a substantially larger change intentionally left out of this first version.
- **Ad blocking is a static host list**, not a full content-blocking engine — see above.
- **History/bookmarks storage** uses simple JSON files in app-private storage rather than a
  database (Room/SQLite). This keeps the first version's code small and dependency-light; it's a
  clean drop-in upgrade later if the data volume grows (swap the repository internals, keep the
  same public API).
- **No HTTP→HTTPS warning banner yet** — the WebView blocks invalid/expired certificates outright
  (see `onReceivedSslError` in `BrowserWebView`), but there's no separate "this page is not secure"
  interstitial for plain HTTP pages in this first version.
- **Downloads** use the system `DownloadManager`-style flow implicitly triggered by WebView's
  default download handling on modern Android; a dedicated in-app downloads list/screen was left
  out of this first version to keep scope focused per the priority order above, and is a natural
  next feature.
- **Instrumented tests** included are a minimal smoke test, not full coverage of every flow;
  `app/src/test` unit tests currently cover the URL-vs-search heuristic in isolation. Expanding
  both is called out as follow-up work, not attempted here to keep the first version's scope
  manageable.

## Privacy honesty notes

Per the design brief, this app does **not** claim to make you anonymous, invisible, or untraceable
online, and Private Mode is explicitly not presented as equivalent to a VPN or Tor. See the in-app
Settings → Privacy information screen (`PrivacyInfoScreen.kt`) for the exact language shown to
users.
