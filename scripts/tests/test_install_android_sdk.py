import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import call, patch


spec = importlib.util.spec_from_file_location(
    "install_android_sdk", Path(__file__).resolve().parents[1] / "install-android-sdk.py"
)
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class InstallAndroidSdkTests(unittest.TestCase):
    def setUp(self):
        temporary_sdk = tempfile.TemporaryDirectory(prefix="sdk-installer-test-")
        self.addCleanup(temporary_sdk.cleanup)
        self.sdk = Path(temporary_sdk.name).resolve()
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.run = self.enterContext(patch.object(installer.subprocess, "run"))
        self.sleep = self.enterContext(patch.object(installer.time, "sleep"))

    def test_success_installs_each_package_once_without_cleanup_or_delay(self):
        self.run.return_value = subprocess.CompletedProcess([], 0)
        with patch.object(installer, "clear_temporary_downloads") as clear:
            self.assertEqual(installer.install_packages(self.sdk, "sdkmanager"), 0)
        self.assertEqual(self.run.call_args_list, [
            call(["sdkmanager", f"--sdk_root={self.sdk}", "--install", package], check=False)
            for package in installer.PACKAGES
        ])
        clear.assert_not_called()
        self.sleep.assert_not_called()

    def test_corrupt_ndk_downloads_are_discarded_before_each_retry(self):
        installed = self.sdk / "platform-tools" / "adb"
        installed.parent.mkdir()
        installed.write_text("installed tool")
        licenses = self.sdk / "licenses"
        licenses.mkdir()
        (licenses / "android-sdk-license").write_text("accepted")
        staging = self.sdk / ".temp"
        ndk_attempts = 0

        def sdkmanager(command, check):
            nonlocal ndk_attempts
            if command[-1] != "ndk;27.2.12479018":
                return subprocess.CompletedProcess(command, 0)
            self.assertFalse(staging.exists(), "Retry reused a corrupt download")
            ndk_attempts += 1
            if ndk_attempts < 3:
                staging.mkdir()
                (staging / "android-ndk-r27c-linux.zip").write_bytes(b"corrupt zip")
                return subprocess.CompletedProcess(command, 1)
            return subprocess.CompletedProcess(command, 0)

        self.run.side_effect = sdkmanager
        self.assertEqual(installer.install_packages(self.sdk, "sdkmanager"), 0)
        self.assertEqual(ndk_attempts, 3)
        self.assertEqual(self.run.call_count, len(installer.PACKAGES) + 2)
        self.assertEqual(self.sleep.call_args_list, [call(10), call(20)])
        self.assertEqual(installed.read_text(), "installed tool")
        self.assertEqual((licenses / "android-sdk-license").read_text(), "accepted")

    def test_persistent_failure_returns_exit_code_and_stops_installing(self):
        self.run.return_value = subprocess.CompletedProcess([], 17)
        self.assertEqual(installer.install_packages(self.sdk, "sdkmanager"), 17)
        self.assertEqual(self.run.call_count, 3)
        self.assertTrue(all(args.args[0][-1] == "platform-tools" for args in self.run.call_args_list))
        self.assertEqual(self.sleep.call_args_list, [call(10), call(20)])

    def test_cleanup_refuses_symlinked_staging_directory(self):
        with patch.object(Path, "is_symlink", return_value=True):
            with patch.object(installer.shutil, "rmtree") as remove:
                with self.assertRaisesRegex(ValueError, "symlinked"):
                    installer.clear_temporary_downloads(self.sdk)
        remove.assert_not_called()

    def test_missing_or_root_sdk_path_is_rejected_before_install(self):
        for environment in ({}, {"ANDROID_HOME": self.sdk.anchor}):
            with self.subTest(environment=environment):
                with patch.dict(installer.os.environ, environment, clear=True):
                    with self.assertRaises(SystemExit):
                        installer.main()
        self.run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
