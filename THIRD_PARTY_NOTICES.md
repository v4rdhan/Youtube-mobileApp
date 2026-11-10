# Third-party notices

StreamShield's original source is provided under the Mozilla Public License 2.0; see LICENSE.

## Brave adblock-rust

- Source: https://github.com/brave/adblock-rust
- Version: 0.13.5 from the user-supplied ZIP, not a substitute crates.io engine.
- Copyright and license: the original notices are preserved in `vendor/adblock-rust-master/LICENSE` and source files. Licensed under MPL-2.0.
- Source is retained under `vendor/adblock-rust-master`; the JNI wrapper is under `native`.
- Cargo dependencies and exact versions are recorded in `native/Cargo.lock`. Their upstream licenses continue to apply.

## YTPro startup-ad protection

- Reference: user-supplied `YTPro-main.zip`, `scripts/script.js` (the fetch ad blocker).
- Project: https://github.com/prateek-chaubey/YTPro
- StreamShield adapts its dedicated ad-endpoint blocking and `adBreakHeartbeatParams` removal in `app/src/main/assets/youtube-player.js` and the starter rules. The rest of YTPro is not bundled or loaded remotely.
- The supplied archive's MIT license is retained below:

```text
MIT License

Copyright (c) 2022 Prateek Chaubey

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Optional EasyList and EasyPrivacy

The app only downloads these lists when the user requests an update:

- https://easylist.to/easylist/easylist.txt
- https://easylist.to/easylist/easyprivacy.txt
- Project: https://github.com/easylist/easylist

The full downloaded text, including its title, copyright, source and license headers, is retained in private app storage. The downloaded lists are not shipped in this source tree or APK. Consult their retained license headers before redistributing them.

## Android libraries and build tools

AndroidX, Material Components for Android, Kotlin, and Gradle are used under their respective upstream licenses. Gradle wrapper scripts include their Apache-2.0 license notice. Platform icons in this app are original vector geometry.

YouTube is a trademark of Google LLC. Brave is a trademark of Brave Software. These names describe interoperability or source provenance and do not imply endorsement.
