# Verification

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
