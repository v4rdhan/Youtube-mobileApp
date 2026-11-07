# StreamShield

A Kotlin Android app for browsing and playing YouTube's live mobile site, with the **adblock-rust 0.13.5 source supplied in your ZIP** compiled into the APK through JNI.

## Features

- Live YouTube browsing, search, Shorts, and playback in Android WebView. No API key or separate backend.
- Native dark Material 3 interface, fullscreen video, Android Back handling, and shared YouTube links.
- Rust request filtering and site-specific cosmetic CSS rules. Video URLs and streams are left to WebView; there is no stream extraction service.
- Protection switch and an actual blocked-request count for the current app session.
- Built-in starter rules; optional EasyList and EasyPrivacy downloads from Protection → Update filter lists.
- Custom Adblock Plus rules and local saved video links, with removal.
- Error recovery, bounded HTTPS filter downloads, atomic filter-cache writes, and background rule compilation.

## Playback and blocking limits

This is a working browser-based Android client, not a guarantee that every YouTube video plays or every advertisement disappears. Private, paid, age-restricted, region-restricted, DRM-protected, removed, or otherwise unavailable content remains subject to YouTube's controls. Google may reject sign-in through embedded browsers. Opening a link in the external browser uses that browser's own session.

The supplied engine performs filter matching; it is not the complete Brave browser. This integration implements network blocking and site-specific CSS hiding. It does **not** implement scriptlet resources, procedural cosmetics, generic class/id cosmetics, redirect resources, or URL rewriting. WebView does not expose every kind of request (including WebSockets and service-worker traffic) to this interception hook. Ads delivered from the same stream or through player data can remain. Updating EasyList does not add the missing browser capabilities.

Fullscreen follows YouTube's own video controls. There is no background playback, download feature, account/password collection, or bypass of video access restrictions. The native shell is designed for phones. YouTube's site and DOM can change independently of this app.

## Build

Requirements: JDK 17, Android SDK platform 36, Android build tools 35 or newer, Android NDK 27 or newer, Rust 1.98, and internet access on the first build. Set `ANDROID_HOME` to the SDK. Set `ANDROID_NDK_HOME` if you need a particular NDK version; otherwise the build selects the newest installed NDK.

Install Rust's Android standard libraries:

```powershell
rustup toolchain install 1.98 --profile minimal
rustup target add --toolchain 1.98 aarch64-linux-android x86_64-linux-android
.\gradlew.bat assembleDebug
```

On Windows, Rust also needs a host linker to compile procedural macros and build scripts. Use Visual C++ Build Tools, or install the GNU toolchain with its bundled linker:

```powershell
rustup toolchain install 1.98-x86_64-pc-windows-gnu --profile minimal
rustup target add --toolchain 1.98-x86_64-pc-windows-gnu aarch64-linux-android x86_64-linux-android
.\gradlew.bat assembleDebug
```

The Windows build script selects the installed GNU toolchain when `link.exe` is unavailable. Set `RUSTUP_TOOLCHAIN` to override the selection. Linux/macOS can use `./gradlew assembleDebug` with the same SDK, NDK, and Rust targets.

Gradle compiles the native library automatically before packaging. The APK includes **arm64-v8a** and **x86_64**, targets Android 16/API 36, and runs on Android 8.0/API 26 or newer. 32-bit-only devices are not included. JNI libraries use 16 KB segment alignment.

APK: `app/build/outputs/apk/debug/app-debug.apk`

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open this folder in Android Studio. For a distributable release, configure your own signing key or use Android Studio's Generate Signed Bundle / APK action. No private signing key is included.

## CI and releases

GitHub Actions runs unit tests, Android lint, and Rust tests for non-`main` pushes and pull requests targeting `main`. When a pull request is merged into `main`, the release workflow increments `versionCode` and the patch portion of `versionName`, builds the release APK, commits the version bump, creates a matching `v<versionName>` tag, and publishes the unsigned APK with a SHA-256 checksum in a GitHub Release. The workflow needs `contents: write` permission and permission to push version commits to `main`; branch protection rules may need to allow GitHub Actions to do so. The APK is unsigned and must be signed before distribution through an app store.

## Verify

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
cargo test --locked --manifest-path native/Cargo.toml
.\gradlew.bat connectedDebugAndroidTest
```

For Rust host tests on Windows without Visual C++ use `cargo +1.98-x86_64-pc-windows-gnu test --locked --manifest-path native/Cargo.toml`. Device tests exercise the real packaged JNI library, concurrent rule replacement, and the protection switch. Live playback additionally needs a device/emulator with a current Android System WebView and network access.

## Source layout

| Location | Purpose |
| --- | --- |
| `app/src/main/java/app/streamshield/MainActivity.kt` | Native UI, WebView, fullscreen, navigation |
| `app/src/main/java/app/streamshield/blocking/` | Rule updates, request classification, JNI declarations |
| `native/src/lib.rs` | Thread-safe Rust/JNI bridge |
| `vendor/adblock-rust-master/` | Unmodified source extracted from the supplied ZIP |
| `scripts/build-native.ps1` and `.sh` | NDK cross-compilation |
| `app/src/main/assets/filters/starter.txt` | Small, shipped network and cosmetic rule set |

Source archive SHA-256: `B94FD587287A402C544235D2FA3CFD50371D65C857AAF7F7AA979405FF0FC9BA`.

## Privacy and licensing

YouTube receives browser traffic and cookies as it does in a browser. Optional list updates contact easylist.to. Saved links, preferences, and filter caches stay in private app storage. There is no app analytics backend; app backups and device transfers are excluded. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSE](LICENSE).

Independent project; not affiliated with YouTube, Google, or Brave. If you redistribute an APK, make its corresponding MPL-covered source available to recipients and retain the notices. Review YouTube's current terms before distribution; this project does not claim to be an approved YouTube API client.
