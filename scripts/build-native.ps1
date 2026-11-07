param([string[]]$Abis = @('arm64-v8a', 'x86_64'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$ndkPath = if ($env:ANDROID_NDK_HOME) { $env:ANDROID_NDK_HOME } else { (Get-ChildItem (Join-Path $sdkPath 'ndk') -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName }
if (-not $ndkPath) { throw 'Install the Android NDK (27 or newer) in Android Studio, or set ANDROID_NDK_HOME.' }
$toolchain = Join-Path $ndkPath 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$cargoPath = if (Get-Command cargo -ErrorAction SilentlyContinue) { (Get-Command cargo).Source } else { Join-Path $env:USERPROFILE '.cargo\bin\cargo.exe' }
if (-not (Test-Path -LiteralPath $cargoPath)) { throw 'Install Rust 1.98 with rustup, then add aarch64-linux-android and x86_64-linux-android targets.' }
# Rust build scripts run on the host. The GNU distribution includes a host linker,
# making it useful on Windows machines without Visual C++ Build Tools.
if (-not $env:RUSTUP_TOOLCHAIN -and -not (Get-Command link.exe -ErrorAction SilentlyContinue)) {
    $gnuToolchain = Join-Path $env:USERPROFILE '.rustup\toolchains\1.98-x86_64-pc-windows-gnu'
    if (Test-Path -LiteralPath $gnuToolchain) { $env:RUSTUP_TOOLCHAIN = '1.98-x86_64-pc-windows-gnu' }
}
$targets = @{ 'arm64-v8a' = @('aarch64-linux-android', 'aarch64-linux-android26-clang.cmd'); 'x86_64' = @('x86_64-linux-android', 'x86_64-linux-android26-clang.cmd') }
$env:PATH = "$toolchain;$env:PATH"
foreach ($abi in $Abis) {
    if (-not $targets.ContainsKey($abi)) { throw "Unsupported ABI: $abi" }
    $target = $targets[$abi][0]
    $key = $target.Replace('-', '_').ToUpperInvariant()
    Set-Item -Path "Env:CARGO_TARGET_${key}_LINKER" -Value (Join-Path $toolchain $targets[$abi][1])
    Set-Item -Path "Env:AR_$($target.Replace('-', '_'))" -Value (Join-Path $toolchain 'llvm-ar.exe')
    Set-Item -Path "Env:CARGO_TARGET_${key}_RUSTFLAGS" -Value '-C link-arg=-Wl,-z,max-page-size=16384'
    Push-Location (Join-Path $projectRoot 'native')
    try {
        & $cargoPath build --locked --release --target $target
        if ($LASTEXITCODE -ne 0) { throw "Rust build failed for $abi. Check the compiler output above; install Android targets for the active Rust toolchain and a working host linker." }
    } finally { Pop-Location }
    $destination = Join-Path $projectRoot "app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot "native\target\$target\release\libstreamshield_adblock.so") -Destination $destination
}
