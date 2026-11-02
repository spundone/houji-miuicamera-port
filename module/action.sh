#!/system/bin/sh
# KernelSU / Magisk "Action" button: turns HEIC Ultra HDR on or off. HEIF photos then get the
# estimated gain map JPEG photos get. Off by default: Google Photos can't back up HEIF files with
# a gain map ('tmap' item), and macOS and ffmpeg can't open them; Android can. Photos with a
# watermark never get one, so MIUI Gallery's editor can still change or remove the watermark.
# Applies from the next photo.
MODDIR=${0%/*}
FLAG="$MODDIR/heic_ultrahdr"
if [ -f "$FLAG" ]; then
    rm -f "$FLAG"
    setprop debug.xcam17.heic_ultrahdr 0
    echo "HEIC Ultra HDR: off"
else
    touch "$FLAG"
    setprop debug.xcam17.heic_ultrahdr 1
    echo "HEIC Ultra HDR: on. Google Photos can't back these HEIF photos up, and macOS can't open them"
    echo "Photos with a watermark stay without Ultra HDR, so the Gallery editor can still change it"
fi
