# HyperOS (preliminary)

Camera 6.8.002090.2 on the Xiaomi 14 (houji) running HyperOS, with the LSPosed module. **Untested**: written from how the camera behaves on AOSP ROMs and how HyperOS differs. Reports welcome, with the log lines described below.

## How it differs from AOSP ROMs

- **No KernelSU module.** Xiaomi signs its camera builds with one key (`c9009d01…`, the same for the Xiaomi 17's 6.7 and the 18 Pro's 6.8), so on HyperOS the newer APK should install as a normal update to the stock camera. Don't flash `houji-miuicamera-port-<version>.zip` or the standalone zips on HyperOS.
- **The LSPosed module is still needed.** 6.8 has no Xiaomi 14 profile and closes three seconds after opening without the module, which loads Xiaomi's flagship profile with the Xiaomi 14's own values. It also adds 50MP on every lens, 50MP RAW, lens switching while recording, Open Gate, Fastshot and the rest of the feature list.
- **Fixes for what AOSP lacks are skipped.** On HyperOS the module leaves the camera's client name, video recorder output, haptics and Movie mode's audio to HyperOS. It logs `HyperOS` at start when it detects it (from `ro.mi.os.version.name`).
- **Same camera hardware.** Everything in the README's limitations that comes from houji's camera HAL applies here too.
- **No Leica Essential.** Its files come with the KernelSU module.

## Install

1. Root (KernelSU or Magisk) with an LSPosed framework (Vector or LSPosed).
2. Note your current camera version (Settings > Apps > Camera) in case you want to go back.
3. Install camera 6.8.002090.2 as an update (where to get it: [houji-camera.md](houji-camera.md#4-getting-the-camera-apks)):

   ```bash
   adb install -r MiuiCamera-6.8.002090.2.apk
   ```

   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means your camera is signed with a different key; stop there and report it.
4. Install `houji-miuicamera-port-lsposed-<version>.apk` from [Releases](https://github.com/spundone/houji-miuicamera-port/releases), enable it in LSPosed for **Camera**, then force stop the camera.
5. Open the camera. If it closes with "invalid module index", clear its data once: `adb shell pm clear com.android.camera`.

## Going back

Settings > Apps > Camera > Uninstall updates (or `adb shell pm uninstall-system-updates com.android.camera`), and disable the LSPosed module.

## Reporting

The module's log is in LSPosed's log viewer (or `/data/adb/lspd/log/`), lines starting with `xcam17:`. The first ones say whether HyperOS was detected and which camera build was recognised; each hook then logs whether it applied. A crash log helps too: `adb logcat -b crash -d`.
