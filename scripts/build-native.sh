#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../native"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ndk="${ANDROID_NDK_HOME:-}"
if [ -z "$ndk" ] && [ -n "$sdk" ]; then ndk="$(find "$sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"; fi
if [ -z "$ndk" ]; then echo 'Set ANDROID_NDK_HOME to Android NDK 27 or newer.' >&2; exit 1; fi
case "$(uname -s)" in Darwin) host=darwin-x86_64;; *) host=linux-x86_64;; esac
bin="$ndk/toolchains/llvm/prebuilt/$host/bin"
export PATH="$bin:$PATH"
for abi in arm64-v8a x86_64; do
  if [ "$abi" = arm64-v8a ]; then target=aarch64-linux-android; else target=x86_64-linux-android; fi
  key="$(echo "$target" | tr '[:lower:]-' '[:upper:]_')"
  export "CARGO_TARGET_${key}_LINKER=$bin/${target}26-clang"
  export "CARGO_TARGET_${key}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384"
  cargo build --locked --release --target "$target"
  mkdir -p "../app/src/main/jniLibs/$abi"
  cp "target/$target/release/libstreamshield_adblock.so" "../app/src/main/jniLibs/$abi/"
done
