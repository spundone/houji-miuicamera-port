#!/system/bin/sh
# Runs after boot. With MIUI Gallery installed, sets the haptics version its editor can use.
# Once after an install, the launcher's icon cache is dropped so it draws the
# overlay's icon instead of the one it cached before the overlay was mounted. App data stays
# encrypted until the first unlock, so this waits for the launcher's data to appear.
MODDIR=${0%/*}

# MIUI Gallery's editor carries HyperOS's haptic code, which it uses when houji reports
# sys.haptic.version 2.0, and which crashes on AOSP (missing HyperOS framework resources) when
# a list bounces at its end. At 1.0 Xiaomi's apps use Android's haptics; the camera feels the same.
if [ -d /data/adb/modules/MIUIGallery ] && [ ! -f /data/adb/modules/MIUIGallery/disable ]; then
    /data/adb/ksu/bin/resetprop sys.haptic.version 1.0
    (
        i=0
        until [ "$(getprop sys.boot_completed)" = 1 ] || [ $i -ge 300 ]; do sleep 2; i=$((i + 1)); done
        /data/adb/ksu/bin/resetprop sys.haptic.version 1.0
    ) &
fi

# Videos had a silent audio track on AOSP ROMs. houji's vendor sets ro.vendor.audio.zoom.type=2,
# which makes camera 6.8 record Video, Pro video and Movie audio as channels 2 and 3 of a
# 4-channel capture (AudioFormat channel index mask 0xC). HyperOS's audio stack opens that
# 4-channel stream; AOSP's opens 2 channels, so channels 2 and 3 are empty and AudioFlinger
# fills them with zeros. At 0 the camera records plain stereo. The camera reads the property
# each time it starts recording, so setting it after boot is enough. HyperOS keeps its value.
if [ -z "$(getprop ro.mi.os.version.name)" ] && [ "$(getprop ro.vendor.audio.zoom.type)" = 2 ]; then
    for rp in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop resetprop; do
        command -v "$rp" >/dev/null 2>&1 && { "$rp" ro.vendor.audio.zoom.type 0; break; }
    done
fi

[ -f "$MODDIR/reset_icon_cache" ] && [ -f "$MODDIR/launcher" ] || exit 0
launcher=$(cat "$MODDIR/launcher")
data="/data/data/$launcher/databases"
i=0
until [ -d "$data" ] || [ $i -ge 600 ]; do
    sleep 2
    i=$((i + 1))
done
[ -d "$data" ] || exit 0
rm -f "$data"/app_icons.db*
am force-stop "$launcher"
rm -f "$MODDIR/reset_icon_cache"
