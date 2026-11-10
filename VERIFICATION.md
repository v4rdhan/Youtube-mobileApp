# Verification

Inline startup-ad filtering extended on 9 November 2026 after comparing the supplied v0.1.4 source ZIP with this newer checkout:

- Standalone `playerResponse`, serialized `ytplayer.config.args.player_response`, and initial objects filled in place are now cleaned when their global is read. Existing response identity, stream data, and access status are retained.
- All 39 Node player regression tests and 5 Kotlin unit tests passed. The three new inline-data regression cases failed before the fix; an additional recovery test confirms these sources wake the existing single-retry path without seeking content.
- Debug APK built at `app/build/outputs/apk/debug/app-debug.apk`; Android lint completed with 0 errors and 13 warnings. The extended Protection on/off WebView fixture compiled successfully.
- Device tests and live YouTube playback were not run for this change. No device was connected, and starting the configured read-only API 35 emulator failed because its Android system image is missing. These checks do not establish universal startup-ad removal.

The additional startup-loading fix was reapplied on 9 November 2026 after it was found missing from the v0.1.6 checkout:

- All 35 Node player regression tests passed. Coverage includes `get_watch` JSON/text/XHR responses, delayed responses waking recovery, startup without an ad marker, pause and normal-buffering guards, access restrictions, and a single retry budget across query-only URL changes.
- All 5 Kotlin unit tests passed, Android test fixtures compiled, and Android lint completed with 0 errors and 13 warnings. These checks did not package or install an APK.
- Startup without an ad marker qualifies for one retry after eight seconds only if nonempty ad data was removed for that video, playback was requested, the page is visible, the response is playable and non-live, and no content media data arrived. This path does not seek the video.
- Restored the Android WebView regression fixture for Protection on/off. The device tests and live playback were not rerun during this reapplication; the phone's reported failure still needs a retest with the updated code.

Startup-ad recovery checked on 9 November 2026 on the Android 15/API 35 emulator with WebView 124.0.6367.219:

- All 25 Node player regression tests, 5 Kotlin unit tests, and 8 Android instrumentation tests passed. Android lint: 0 errors, 9 warnings.
- Debug and optimized unsigned release APKs built successfully; release signing remains handled by GitHub Actions.
- Regression cases cover prefetched navigation responses, visible/disabled Skip controls, consecutive finite ads, blocked ads without metadata, a single bounded player retry, retained content position, and normal buffering that must not trigger recovery.
- WebView fixtures confirm that an existing ad state exits through Skip or a bounded player retry with Protection enabled; both remain untouched with Protection disabled.
- Live Big Buck Bunny (`aqz-KE-bpKQ`) played with Protection enabled, readyState 4, no visible ad state, and time advancing from 2.138 to 32.615 seconds. Clicking a related video (`vE57fPUAQh0`) navigated to the correct video and playback advanced to 0.888 seconds with readyState 4 and no visible ad state.

The reported v0.1.5 failure on Android 16 has not been reproduced on that device. These checks validate the recovery paths and sampled playback, not universal ad removal or zero network buffering. Android 16 phone verification is still required.

Screen-off playback verified on 8 November 2026 on the Android 15/API 35 emulator:

- Debug and optimized unsigned release APKs built; all 5 Kotlin unit tests and all 7 device tests passed. Android lint: 0 errors, 14 existing warnings.
- Separate offline audio and VP8/Opus video fixtures kept advancing while `PowerManager.isInteractive` was false. The video fixture exercises WebView's window-visibility handling as well as the page visibility handler.
- Notification Pause and media-session Pause both paused playback and removed the service notification. Both focused playback tests passed again after adding the media-session command check.
- Live Big Buck Bunny (`aqz-KE-bpKQ`) stayed unmuted and playing with the screen off: time advanced from 6.705 to 88.571 seconds, readyState 4, device wakefulness `Asleep`, and the media foreground service and partial wake lock active.
- An initial audio-only test passed while live video stopped; `PlaybackWebView` now preserves window visibility during active playback, and the added real-video fixture covers that regression.
- An installable debug build is also available as `app/build/outputs/apk/debug/StreamShield-v0.1.3-debug.apk`; release signing and versioned release downloads remain handled by GitHub Actions.

Playback ends on pause, native panel navigation, renderer loss, task removal, or activity destruction. These checks do not establish behavior on every physical device or under manufacturer battery restrictions.

Player ad-data filtering verified on 8 November 2026 using the Android 15/API 35 emulator:

- 12 Node regression tests passed: initial data, subsequent assignments, parsed JSON, serialized player configs, fetch JSON/text, XHR, unrelated responses, invalid JSON, repeated injection, and preserved video/access data.
- All 5 Kotlin unit tests and all 5 Android instrumentation tests passed. The new WebView fixture confirms ad fields are removed before the first inline read on a document-start-capable WebView, and that Protection off/on disables and restores the filter across reloads.
- The first device-suite attempt timed out in the existing navigation startup test; the subsequent full run passed all five tests.
- Android lint: 0 errors, 14 warnings. Debug APK rebuilt at `app/build/outputs/apk/debug/app-debug.apk`.
- Live Big Buck Bunny (`aqz-KE-bpKQ`) playback with protection enabled: player status `OK`, no known player ad fields, no visible ad state, video time advanced from 11.948 to 41.546 seconds with readyState 4 and decoded width 640.

These checks cover known player-data formats and one live public video. They do not establish that every startup ad is removed; older WebViews and ads embedded in streams remain limitations.

Navigation update verified on 8 November 2026 on the Android 15/API 35 phone emulator:

- Debug APK built; 5 Kotlin unit tests and all 4 device instrumentation tests passed. Android lint: 0 errors, 14 warnings.
- Browsing has no native logo toolbar or search field. YouTube's search submitted `android` and loaded live results.
- One native bottom bar remains. The site's `ytm-pivot-bar-renderer` computed display is `none`, and `ytm-app` bottom padding is `0px`, with protection enabled and disabled.
- Protection and More preserve the selected destination; Back from Protection returns to Saved when opened there. The More menu remains accessible.
- Phone screenshots inspected at normal and 1.3 font scale; font scale restored to 1.0 and protection re-enabled after the check.

Updated captures: `.impeccable/review/phone-navigation.png`, `phone-youtube-search.png`, and `phone-navigation-large-text.png`.

The earlier verification below describes the original interface and playback checks.

Verified on 7 November 2026 using JDK 17, Android SDK 36, NDK 27.1.12297006, Gradle 8.14.3, and Rust 1.98. Android device checks used an isolated Android 15/API 35 x86_64 emulator, 1080×2400, density 420, with system WebView 124.0.6367.219.

| Check | Result |
| --- | --- |
| Debug APK, both arm64-v8a and x86_64 JNI libraries | Built and installed |
| Optimized release APK | Built; unsigned, requires the distributor's key |
| Rust tests | 4 passed: ad endpoints, stream/CDN allowance, exceptions, cosmetics, invalid requests |
| Kotlin unit tests | 5 passed: URL validation/normalization and request classification |
| Device instrumentation | 4 passed: JNI behavior, concurrent engine replacement, protection bypass, settings/saved navigation |
| Android lint | 0 errors, 14 warnings: dependency updates, suggested Kotlin extension syntax, always-visible toolbar actions, and a legacy backup-configuration suggestion |
| Live YouTube browsing | Loaded the mobile site |
| Native search | Submitted a search through the Kotlin search field and loaded YouTube search results |
| Live YouTube playback | Official Blender Big Buck Bunny video aqz-KE-bpKQ played; observed currentTime progressing from 4.746 to 30.018 seconds, readyState 4, decoded video width 640 |
| Fullscreen | Entered landscape fullscreen with native chrome hidden; Back returned to the portrait app |
| Saved videos | Saved the current video from the native menu; the local Saved screen displayed its title |
| Filter update | Downloaded EasyList and EasyPrivacy in-app; 3,591,477 bytes cached and last-updated status displayed |
| Playback after full filter update | Reopened the saved video with downloaded lists active; playback running at 3.136 seconds, readyState 4 |
| Font scaling | Protection screen inspected at font scale 1.3; restored to 1.0 |
| Native segment alignment | arm64 LOAD segments aligned to 0x4000 (16 KB) |

Screenshots: `.impeccable/review/phone.png`, `phone-settings.png`, `phone-fullscreen.png`, `phone-large-text.png`, and `phone-saved.png`. These are real emulator captures; playback imagery belongs to the video shown.

The initial device pass found a settings TextInputLayout layout-parameter crash; it was corrected and the device navigation regression test passes on the final build. No app fatal exception appeared in the final device run.

This confirms one live public video and the listed app workflows. It does not establish universal video availability, universal ad removal, physical-device performance, Google sign-in, or ARM hardware playback. See README.md for the integration's blocking and playback limits.
