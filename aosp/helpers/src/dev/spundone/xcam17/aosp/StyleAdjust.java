package dev.spundone.xcam17.aosp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


/**
 * Filter adjustments (Saturation, Tone, Soft light, Cold/Warm, Cyan/Magenta, Noise, Vignette, and
 * the mist effects) in the saved photo, not only in the viewfinder.
 *
 * The camera sends them to the camera service as "@StyleAdjustEffect;Algos=..." in the photo's
 * filter script. houji's /odm/lib64/libMiPhotoFilter.so, which renders photos there, knows only
 * CubeLutEffect, CvEffect, SharpenEffect and ChangeLuminance, so photos kept the filter's colour
 * table and lost the adjustments (houji's camera doesn't even send them: it uses the filter id
 * path, not the MIVI 3 render tag). The viewfinder draws them with the app's own CandySDK, which
 * has the effect. This keeps the viewfinder's current style script, and after the JPEG is saved
 * runs it through CandySDK on the full photo, the same code as the viewfinder, keeping the
 * photo's metadata segments as they are.
 */
public final class StyleAdjust {
    static final String[] EFFECTS = {"StyleAdjustEffect", "WhiteMistEffect", "BlackMistEffect"};
    static final String CANDY = "com.xiaomi.milab.filtersdk.CandySDK";
    /** CandySDK's input type for a Bitmap, as the camera's own bitmap helpers use it. */
    static final int CANDY_BITMAP = 5;
    static final int QUALITY = 95;
    /** Larger photos (50MP is 205 MB as ARGB) are left as they are rather than risk running out of memory. */
    static final long MAX_PIXELS = 52_000_000L;

    private static volatile String lastScript;
    private static volatile long lastScriptAt;
    /** A script older than this when the photo is saved is from a viewfinder no longer drawing it. */
    static final long FRESH_MS = 3000;

    private StyleAdjust() {}

    /**
     * After the viewfinder's style script builder (Tu.r$a.a, run for every preview frame while a
     * filter has adjustments): keeps the script and when it was built. houji's camera doesn't
     * use the MIVI 3 render tag that carries the script to the camera service
     * (supportMIVI3OutputJpeg is false), so the viewfinder's is the only copy.
     */
    public static String afterViewfinderScript(String script) {
        lastScript = script == null ? null : styleOnly(script);
        lastScriptAt = android.os.SystemClock.uptimeMillis();
        return script;
    }

    /** The style effects at the end of a filter script, or null when it has none. */
    static String styleOnly(String script) {
        int at = -1;
        for (String e : EFFECTS) {
            int i = script.indexOf(e);
            if (i >= 0 && (at < 0 || i < at)) at = i;
        }
        if (at < 0) return null;
        if (at > 0 && script.charAt(at - 1) == '@') at--;
        return script.substring(at);
    }

    /** The JPEG with the last photo's style effects applied, or the same array when there are none. */
    static byte[] apply(byte[] jpeg) {
        String script = lastScript;
        if (script == null || script.isEmpty()) return jpeg;
        if (android.os.SystemClock.uptimeMillis() - lastScriptAt > FRESH_MS) return jpeg;
        Bitmap b = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
            if ((long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) {
                Log.once("style: " + bounds.outWidth + "x" + bounds.outHeight + " photo too large, adjustments not applied");
                return jpeg;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inMutable = true;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (b == null) return jpeg;

            Class<?> sdk = Class.forName(CANDY);
            Object candy = sdk.getConstructor(int.class).newInstance(CANDY_BITMAP);
            Method release = sdk.getMethod("e");
            try {
                sdk.getMethod("a", String.class).invoke(candy, script);
                sdk.getMethod("i", String.class).invoke(candy, script);
                sdk.getMethod("c", Object.class, float[].class).invoke(candy, b, null);
            } finally {
                release.invoke(candy);
            }

            ByteArrayOutputStream enc = new ByteArrayOutputStream(jpeg.length);
            if (!b.compress(Bitmap.CompressFormat.JPEG, QUALITY, enc)) return jpeg;
            byte[] out = withHeaderOf(jpeg, enc.toByteArray());
            Log.once("style: adjustments applied to saved photos (" + script + ")");
            return out;
        } catch (Throwable t) {
            Log.once("style: adjustments not applied", t);
            return jpeg;
        } finally {
            if (b != null) b.recycle();
        }
    }

    /** The original's SOI and APP/COM segments (EXIF, XMP, ICC, Xiaomi's) followed by the new image data. */
    static byte[] withHeaderOf(byte[] original, byte[] encoded) {
        int head = headerEnd(original), body = headerEnd(encoded);
        byte[] out = new byte[head + encoded.length - body];
        System.arraycopy(original, 0, out, 0, head);
        System.arraycopy(encoded, body, out, head, encoded.length - body);
        return out;
    }

    /** Offset just after SOI and the APPn and COM segments that follow it. */
    static int headerEnd(byte[] d) {
        int i = 2;
        while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
            int marker = d[i + 1] & 0xFF;
            if (!(marker >= 0xE0 && marker <= 0xEF) && marker != 0xFE) break;
            i += 2 + (((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF));
        }
        return i;
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
