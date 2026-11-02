# HyperOS (preliminary)

Camera 6.8.002090.2 on the Xiaomi 14 (houji) running HyperOS, with the LSPosed module. **Untested**: written from how the camera behaves on AOSP ROMs and how HyperOS differs. Reports welcome, with the log lines described below.

## How it differs from AOSP ROMs

- **Two ways to get 6.8 in place.** Xiaomi signs its camera builds with one key (`c9009d01…`, the same for the Xiaomi 17's 6.7 and the 18 Pro's 6.8), so on stock HyperOS the newer APK should install as a normal update. xiaomi.eu modifies and re-signs many of Xiaomi's apps, so there the update can be refused; the module zip replaces the camera at boot instead, as on AOSP ROMs. The standalone zips are for AOSP ROMs only and refuse to install on HyperOS.
- **The LSPosed module is still needed.** 6.8 has no Xiaomi 14 profile and closes three seconds after opening without the module, which loads Xiaomi's flagship profile with the Xiaomi 14's own values. It also adds 50MP on every lens, 50MP RAW, lens switching while recording, Open Gate, Fastshot and the rest of the feature list.
- **Fixes for what AOSP lacks are skipped.** On HyperOS the module leaves the camera's client name, video recorder output, haptics and Movie mode's audio to HyperOS. It logs `HyperOS` at start when it detects it (from `ro.mi.os.version.name`).
- **Same camera hardware.** Everything in the README's limitations that comes from houji's camera HAL applies here too.
- **Leica Essential** only with the module zip and the Leica port APK in `Download` (as on AOSP ROMs), and only where the module manager mounts the module's `system/` folder (Magisk does; KernelSU needs a mount module).

## Install with the module zip (xiaomi.eu, or any HyperOS)

Needs root (KernelSU or Magisk) and an LSPosed framework.

1. If the camera has updates installed, remove them: Settings > Apps > Camera > Uninstall updates.
2. Put camera 6.8.002090.2 in `Download` (where to get it: [houji-camera.md](houji-camera.md#4-getting-the-camera-apks)), or use a zip built with the APK inside (`scripts/build.sh --apk`, kept to yourself).
3. Flash `houji-miuicamera-port-<version>.zip` in KernelSU or Magisk. On HyperOS it finds the camera wherever the ROM keeps it (`/system` or `/product`), skips the AOSP extras (icon overlay, MIUI Gallery), and installs the LSPosed module.
4. Enable **MiuiCamera port for houji** in LSPosed for **Camera** (with Vector it's enabled for you), then reboot.
5. If the camera closes with "invalid module index", clear its data once: `adb shell pm clear com.android.camera`.

To go back, remove the module and reboot, then uninstall the LSPosed module.

## Install as an update (stock HyperOS, no module)

1. Note your current camera version (Settings > Apps > Camera) in case you want to go back.
2. `adb install -r MiuiCamera-6.8.002090.2.apk`. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means your camera is signed with another key: use the module zip instead.
3. Install `houji-miuicamera-port-lsposed-<version>.apk` from [Releases](https://github.com/spundone/houji-miuicamera-port/releases), enable it in LSPosed for **Camera**, then force stop the camera.

To go back: Settings > Apps > Camera > Uninstall updates (or `adb shell pm uninstall-system-updates com.android.camera`), and disable the LSPosed module.

## Reporting

The module's log is in LSPosed's log viewer (or `/data/adb/lspd/log/`), lines starting with `xcam17:`. The first ones say whether HyperOS was detected and which camera build was recognised; each hook then logs whether it applied. A crash log helps too: `adb logcat -b crash -d`.
