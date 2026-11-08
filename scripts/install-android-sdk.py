#!/usr/bin/env python3
"""Install CI's Android SDK packages, retrying failed downloads separately."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


PACKAGES = (
    "platform-tools",
    "platforms;android-36",
    "build-tools;35.0.0",
    "ndk;27.2.12479018",
)
MAX_ATTEMPTS = 3


def clear_temporary_downloads(sdk):
    # This runs between sequential installs on an isolated CI runner. Keep all
    # installed packages and licenses; only discard SDK Manager's staging area.
    staging = sdk / ".temp"
    if staging.is_symlink():
        raise ValueError(f"Refusing to clear a symlinked SDK staging directory: {staging}")
    if staging.exists():
        shutil.rmtree(staging)


def install_packages(sdk, sdkmanager):
    for package in PACKAGES:
        for attempt in range(1, MAX_ATTEMPTS + 1):
            print(f"Installing {package} (attempt {attempt}/{MAX_ATTEMPTS})", flush=True)
            result = subprocess.run(
                [sdkmanager, f"--sdk_root={sdk}", "--install", package],
                check=False,
            )
            if result.returncode == 0:
                break
            if attempt == MAX_ATTEMPTS:
                print(f"::error::Failed to install {package} after {MAX_ATTEMPTS} attempts.", flush=True)
                return result.returncode
            print("SDK installation failed; clearing temporary downloads before retrying.", flush=True)
            clear_temporary_downloads(sdk)
            time.sleep(10 * attempt)
    return 0


def main():
    sdk_path = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_path:
        raise SystemExit("Set ANDROID_HOME to the Android SDK directory.")
    sdk = Path(sdk_path).resolve()
    if not sdk.is_dir() or sdk == Path(sdk.anchor):
        raise SystemExit(f"Invalid Android SDK directory: {sdk}")
    sdkmanager = shutil.which("sdkmanager")
    if not sdkmanager:
        raise SystemExit("sdkmanager is missing from PATH; set up Android command-line tools first.")
    return install_packages(sdk, sdkmanager)


if __name__ == "__main__":
    sys.exit(main())
