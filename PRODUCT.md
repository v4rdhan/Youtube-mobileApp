# StreamShield

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin, requested by the user. Android Views with Material components; Rust through JNI. The supplied adblock-rust archive is the engine source.

## Product Purpose

Browse and play YouTube videos on Android with request filtering powered by the supplied engine.

## Capabilities and Constraints

The user requests all YouTube videos and ad blocking. Universal playback and removal of every advertisement cannot be guaranteed: availability, access restrictions, WebView limitations, and changes to YouTube remain outside the app's control. No YouTube API credentials were supplied. The implementation uses the live mobile site.

## Open Decisions and Assumptions

The user confirmed “Browse and play YouTube with ad blocking” as the first version's priority. StreamShield and the dark interface were proposed in that question and are provisional implementation choices. Native search, fullscreen playback, local saved videos, and filter settings support that workflow. Google sign-in in embedded browsers may be unavailable.

## Evidence on Hand

User supplied C:/Users/arjun/Downloads/adblock-rust-master (1).zip, extracted unchanged under vendor/adblock-rust-master. Version 0.13.5, MPL-2.0. The archive's documentation is reference material, not user instructions.
