# KernelSU / Magisk module installer, sourced with MODPATH, ZIPFILE, ui_print and abort set.
# shellcheck shell=sh disable=SC2034,SC2154
SKIPUNZIP=0

. "$MODPATH/common.sh"

ui_print "- MiuiCamera port for the Xiaomi 14 (houji)"

# ROMs name the device differently (xiaomi.eu's HyperOS 3 vendor reports "mivendor"), so any
# of these naming houji will do; the boot properties come from the bootloader.
is_houji=""
for p in ro.product.vendor.device ro.product.device ro.product.system.device ro.build.product \
    ro.product.mod_device ro.boot.hardware.revision ro.boot.hardware.sku ro.bootimage.build.fingerprint; do
    case "$(getprop "$p")" in
        houji*|*/houji/*) is_houji=1 ;;
    esac
done
if [ -z "$is_houji" ]; then
    abort "! This module is for the Xiaomi 14 (houji); this phone reports '$(getprop ro.product.device)' / '$(getprop ro.product.vendor.device)' / '$(getprop ro.boot.hardware.revision)'"
fi
[ "$KSU" = "true" ] || [ -n "$MAGISK_VER_CODE" ] || abort "! KernelSU or Magisk is required"
[ "$(getprop ro.build.version.sdk)" -ge 35 ] || abort "! Android 15 or newer is required"

# HyperOS (Xiaomi's firmware, or xiaomi.eu) has its own camera stack: only the camera APK is
# swapped, and the LSPosed module skips its AOSP-only fixes there.
hyperos=$(getprop ro.mi.os.version.name)
if [ -n "$hyperos" ]; then
    ui_print "- HyperOS $hyperos (preliminary support)"
    [ -f "$MODPATH/kit" ] && abort "! This zip is for AOSP ROMs; on HyperOS use the module zip with the LSPosed module"
fi

# The ROM's camera: AOSP houji ROMs keep it in /system, HyperOS may keep it in /product.
target=""
for t in /system/priv-app/MiuiCamera/MiuiCamera.apk /product/priv-app/MiuiCamera/MiuiCamera.apk \
    /system_ext/priv-app/MiuiCamera/MiuiCamera.apk /product/app/MiuiCamera/MiuiCamera.apk; do
    [ -f "$t" ] && target="$t" && break
done
[ -n "$target" ] || abort "! This ROM has no MiuiCamera to replace"
echo "$target" > "$MODPATH/target"
ui_print "- Replacing $target"
# A camera update installed on top (from the app store, or adb install) hides the system APK.
case "$(pm path com.android.camera 2>/dev/null)" in
    *package:/data/*)
        abort "! Uninstall the camera's updates first (Settings > Apps > Camera > Uninstall updates)" ;;
esac

# The camera APK is Xiaomi's and is not redistributed. A zip built with --apk carries it;
# otherwise take it from the phone's Download folder.
apk=""
if [ -f "$MODPATH/MiuiCamera.apk" ]; then
    apk="$MODPATH/MiuiCamera.apk"
else
    for f in /sdcard/Download/*.apk; do
        [ -f "$f" ] || continue
        if known_apk "$f"; then
            apk="$f"
            break
        fi
    done
    [ -n "$apk" ] || abort "! Put a supported camera APK (6.8.002090.2 or 6.7.000280.0) in /sdcard/Download and install again"
    ui_print "- Using $(basename "$apk")"
    cp "$apk" "$MODPATH/MiuiCamera.apk" || abort "! Could not copy the camera APK"
fi

known_apk "$MODPATH/MiuiCamera.apk" || abort "! Camera APK does not match a tested build (see known-apks.sha256)"

set_perm "$MODPATH/MiuiCamera.apk" 0 0 0644 u:object_r:system_file:s0
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
# Keep the HEIC Ultra HDR choice across updates.
[ -f /data/adb/modules/xiaomi17_camera/heic_ultrahdr ] && touch "$MODPATH/heic_ultrahdr"

# KernelSU-only zips (scripts/build-kit-zip.sh) carry a camera APK patched by the AOSP kit, so
# the LSPosed module must not hook it a second time.
if [ -f "$MODPATH/kit" ]; then
    ui_print "- KernelSU only: the camera APK is patched, no LSPosed module needed"
    vector=/data/adb/modules/zygisk_vector/cli
    if pm path dev.spundone.xcam17 >/dev/null 2>&1; then
        if [ -x "$vector" ] && "$vector" modules disable dev.spundone.xcam17 >/dev/null 2>&1; then
            ui_print "- Disabled the LSPosed module in Vector"
        else
            ui_print "! Disable or uninstall the 'MiuiCamera port for houji' LSPosed module"
        fi
    fi
fi

# The MIUI Gallery Global module, when a zip carries it: staged like a KernelSU module update.
gallery_zip="$MODPATH/gallery/MIUIGallery.zip"
[ -n "$hyperos" ] && rm -rf "$MODPATH/gallery"
if [ -f "$gallery_zip" ]; then
    staged=/data/adb/modules_update/MIUIGallery
    rm -rf "$staged" && mkdir -p "$staged"
    if unzip -o -q "$gallery_zip" -d "$staged"; then
        set_perm_recursive "$staged" 0 0 0755 0644
        for d in system system_ext; do
            [ -d "$staged/$d" ] && set_perm_recursive "$staged/$d" 0 0 0755 0644 u:object_r:system_file:s0
        done
        # KernelSU keeps an installed module's disabled state through an update.
        if [ -d /data/adb/modules/MIUIGallery ]; then
            touch /data/adb/modules/MIUIGallery/update
            rm -f /data/adb/modules/MIUIGallery/disable /data/adb/modules/MIUIGallery/remove
        fi
        ui_print "- MIUI Gallery Global module installed"
    else
        rm -rf "$staged"
        ui_print "! Could not install the MIUI Gallery module"
    fi
    rm -rf "$MODPATH/gallery"
fi

# Leica Essential looks (optional). A community port of camera 6.8 (com.kobbin.leica) renders
# them in the app; its code, libraries and models are copied from the user's copy of it to
# /system/etc/xcam17/leica. Nothing of it is redistributed. Kept across updates.
kit="$MODPATH/system/etc/xcam17/leica"
old_kit=/data/adb/modules/xiaomi17_camera/system/etc/xcam17/leica
leica_apk=""
# Standalone zips (scripts/build-kit-zip.sh --leica) carry those files in leica/. They go next
# to the APK in app/, which post-fs-data.sh mounts as the camera's folder, so no module manager
# mount is needed.
if [ -d "$MODPATH/leica" ]; then
    kit="$MODPATH/app/leica"
    mkdir -p "$MODPATH/app"
    mv "$MODPATH/leica" "$kit"
    ln "$MODPATH/MiuiCamera.apk" "$MODPATH/app/MiuiCamera.apk" 2>/dev/null \
        || cp "$MODPATH/MiuiCamera.apk" "$MODPATH/app/MiuiCamera.apk"
    set_perm_recursive "$MODPATH/app" 0 0 0755 0644 u:object_r:system_file:s0
    ui_print "- Leica Essential looks from the zip"
    old_kit=/nonexistent
else
    # The classic port before the Leica Moment build (com.kobbin.leica.compat17u), whose own
    # looks need the kit's picker.
    for f in /sdcard/Download/*.apk; do
        [ -f "$f" ] || continue
        unzip -l "$f" assets/legend18/provenance.json 2>/dev/null | grep -q provenance.json || continue
        unzip -p "$f" classes11.dex 2>/dev/null | grep -q LegendaryPostProcessor || continue
        if unzip -l "$f" classes15.dex 2>/dev/null | grep -q classes15.dex; then
            [ -n "$leica_apk" ] || leica_apk="$f"
        else
            leica_apk="$f"
            break
        fi
    done
fi
if [ -n "$leica_apk" ]; then
    ui_print "- Leica Essential looks from $(basename "$leica_apk")"
    mkdir -p "$kit/lib"
    files='^classes1[1-9]\.dex$|^assets/(legend18|leica-v4|leica-v43-ui|lm-ui)/|^assets/(leica_filter_param_|lm_)|^assets/(scene-transfer\.bin|leica-color-v3\.rgb)$'
    # shellcheck disable=SC2046
    unzip -o -q "$leica_apk" $(unzip -l "$leica_apk" | awk '{print $4}' | grep -E "$files") -d "$kit"
    # The port's own native libraries: those the camera APK doesn't have, and the C++ runtime
    # its helper programs load from the same folder.
    have=$(unzip -l "$MODPATH/MiuiCamera.apk" 'lib/arm64-v8a/*' | awk '{print $4}')
    for lib in $(unzip -l "$leica_apk" 'lib/arm64-v8a/*' | awk '{print $4}' | grep '^lib/arm64-v8a/.*\.so$'); do
        [ "$lib" != lib/arm64-v8a/libc++_shared.so ] && echo "$have" | grep -qx "$lib" && continue
        unzip -o -q -p "$leica_apk" "$lib" > "$kit/lib/${lib##*/}"
    done
    [ -f "$kit/classes11.dex" ] && [ -f "$kit/lib/libmonopan_runner.so" ] \
        || { rm -rf "$MODPATH/system/etc/xcam17"; ui_print "! Could not copy the Leica port's files; Leica Essential stays off"; }
elif [ -f "$old_kit/classes11.dex" ]; then
    mkdir -p "${kit%/*}" && cp -a "$old_kit" "${kit%/*}/" && ui_print "- Leica Essential looks kept from the installed version"
fi

# HyperOS's launcher draws the camera's icon itself: no icon overlay there.
[ -n "$hyperos" ] && rm -rf "$MODPATH/system/product/overlay" "$MODPATH/overlay"

# The camera APK is bind-mounted by post-fs-data.sh; system/ holds the icon overlay
# (product/overlay) and the Leica files, mounted by the module manager. Older versions set skip_mount.
rm -f "$MODPATH/skip_mount"
[ -d "$MODPATH/system" ] && set_perm_recursive "$MODPATH/system" 0 0 0755 0644 u:object_r:system_file:s0
# The Leica processor runs its helpers as programs.
for f in "$kit"/lib/*_runner.so; do
    [ -f "$f" ] && set_perm "$f" 0 0 0755 u:object_r:system_file:s0
done

# The launcher reads the camera's label and icon from the camera's files, so it must see the
# module's files too. Remember the current home app for post-fs-data.sh and uninstall.sh.
if [ -z "$hyperos" ]; then
    home=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null \
        | tail -1 | cut -d/ -f1)
    case "$home" in
        *.*) echo "$home" > "$MODPATH/launcher" && ui_print "- Launcher: $home" ;;
    esac
    # The launcher caches icons by package and keeps the one it drew before the overlay was
    # mounted; service.sh drops that cache once, after the next boot's first unlock.
    touch "$MODPATH/reset_icon_cache"
fi

# All in one: install the LSPosed module carried in the zip, and enable it if Vector is the
# LSPosed framework. pm reads the APK as system, so it goes through /data/local/tmp.
if [ -f "$MODPATH/xcam17-lsposed.apk" ]; then
    tmp=/data/local/tmp/xcam17-lsposed.apk
    cp "$MODPATH/xcam17-lsposed.apk" "$tmp" && chmod 0644 "$tmp"
    if pm install -r "$tmp" >/dev/null 2>&1; then
        ui_print "- LSPosed module installed"
        vector=/data/adb/modules/zygisk_vector/cli
        if [ -x "$vector" ] && "$vector" modules enable dev.spundone.xcam17 >/dev/null 2>&1 \
            && "$vector" scope set dev.spundone.xcam17 com.android.camera/0 >/dev/null 2>&1; then
            lsposed_enabled=1
            ui_print "- LSPosed module enabled in Vector for the camera"
        fi
    else
        ui_print "! Could not install the LSPosed module; install the houji-miuicamera-port-lsposed APK by hand"
    fi
    rm -f "$tmp" "$MODPATH/xcam17-lsposed.apk"
fi

# Android caches parsed packages by path; drop the cached 6.4 so the new APK is read.
clear_camera_package_cache

# KernelSU hides module files from apps unless their profile says otherwise; the camera would
# then run its old APK against the new one's manifest and close (ClassNotFoundException).
# KernelSU Next sets that from here; official KernelSU's ksud can't, so it's done by hand.
if [ "$KSU" = "true" ] && ! set_profile com.android.camera false; then
    ui_print "! Set the Camera's KernelSU profile by hand, or it will close on start:"
    ui_print "  KernelSU > Superuser > (show system apps) > Camera > turn off 'Umount modules'"
    ui_print "  (or KernelSU > Settings > turn off 'Umount modules by default')"
fi

# Ultra HDR: what photos get, and the opt-in for HEIF.
ui_print "- Ultra HDR: JPEG photos get an HDR gain map, except photos with a watermark"
ui_print "  (so MIUI Gallery's editor can still change or remove it)"
if [ -f "$MODPATH/heic_ultrahdr" ]; then
    ui_print "- HEIC Ultra HDR is on (Action button turns it off)"
else
    ui_print "- HEIC Ultra HDR is off; the Action button turns it on"
fi

if [ -n "$lsposed_enabled" ] || [ -f "$MODPATH/kit" ]; then
    ui_print "- Done. Reboot"
else
    ui_print "- Done. Enable 'MiuiCamera port for houji' in LSPosed (scope: Camera), then reboot"
fi
