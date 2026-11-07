# Third-party notices

StreamShield's original source is provided under the Mozilla Public License 2.0; see LICENSE.

## Brave adblock-rust

- Source: https://github.com/brave/adblock-rust
- Version: 0.13.5 from the user-supplied ZIP, not a substitute crates.io engine.
- Copyright and license: the original notices are preserved in `vendor/adblock-rust-master/LICENSE` and source files. Licensed under MPL-2.0.
- Source is retained under `vendor/adblock-rust-master`; the JNI wrapper is under `native`.
- Cargo dependencies and exact versions are recorded in `native/Cargo.lock`. Their upstream licenses continue to apply.

## Optional EasyList and EasyPrivacy

The app only downloads these lists when the user requests an update:

- https://easylist.to/easylist/easylist.txt
- https://easylist.to/easylist/easyprivacy.txt
- Project: https://github.com/easylist/easylist

The full downloaded text, including its title, copyright, source and license headers, is retained in private app storage. The downloaded lists are not shipped in this source tree or APK. Consult their retained license headers before redistributing them.

## Android libraries and build tools

AndroidX, Material Components for Android, Kotlin, and Gradle are used under their respective upstream licenses. Gradle wrapper scripts include their Apache-2.0 license notice. Platform icons in this app are original vector geometry.

YouTube is a trademark of Google LLC. Brave is a trademark of Brave Software. These names describe interoperability or source provenance and do not imply endorsement.
