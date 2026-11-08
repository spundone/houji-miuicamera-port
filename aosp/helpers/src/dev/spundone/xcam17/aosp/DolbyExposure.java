package dev.spundone.xcam17.aosp;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Range;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Dolby Vision in Video mode came out about a stop darker than in Pro video. Video mode records
 * through houji's logical multi-camera (the lens switching pipeline, camera 5), Pro video through
 * a single camera (camera 2). With the same scene, Video mode's sensor gain was higher (ISO 1600
 * against 1380 at 1/50 s, exposure compensation 0), yet its HLG whites reached about 105 nits
 * against Pro video's 213 (HLG reference white is 203): the HAL's Dolby tone mapping on the
 * logical camera is darker. SDR clips from the two modes matched. This adds exposure compensation
 * while Dolby Vision is on and the open camera is a logical multi-camera.
 */
public final class DolbyExposure {
    /** Steps of the camera's exposure compensation (1/6 EV on houji): 4 is +0.67 EV. */
    static final int BIAS_STEPS = 4;
    static final int LOGICAL_MULTI_CAMERA = 11;

    private static Method repository, component, dolbyOn;
    private static Class<?> trueColour;
    private static Field characteristics;

    private DolbyExposure() {}

    /** After l9.n0.m1 (exposure compensation index for a request; caps is the camera's l9.g). */
    public static int afterCompensation(int index, Object caps) {
        try {
            if (caps == null || !dolbyVisionOn()) return index;
            CameraCharacteristics cc = characteristics(caps);
            if (cc == null || !logicalMultiCamera(cc)) return index;
            Range<Integer> range = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int out = index + BIAS_STEPS;
            if (range != null) out = Math.max(range.getLower(), Math.min(range.getUpper(), out));
            Log.once("dolby: exposure compensation +" + BIAS_STEPS + " steps on the logical camera");
            return out;
        } catch (Throwable t) {
            Log.once("dolby: exposure compensation left as is", t);
            return index;
        }
    }

    /** ((s2.c) g2.a.a().x(s2.c.class)).p(): the True Colour (Dolby Vision) component is on. */
    static boolean dolbyVisionOn() throws Exception {
        if (dolbyOn == null) {
            ClassLoader cl = DolbyExposure.class.getClassLoader();
            repository = Class.forName("g2.a", true, cl).getMethod("a");
            trueColour = Class.forName("s2.c", true, cl);
            dolbyOn = trueColour.getMethod("p");
        }
        Object repo = repository.invoke(null);
        if (repo == null) return false;
        if (component == null) component = repo.getClass().getMethod("x", Class.class);
        Object c = component.invoke(repo, trueColour);
        return c != null && Boolean.TRUE.equals(dolbyOn.invoke(c));
    }

    static CameraCharacteristics characteristics(Object caps) throws Exception {
        if (characteristics == null) {
            for (Field f : caps.getClass().getDeclaredFields()) {
                if (f.getType() == CameraCharacteristics.class) {
                    f.setAccessible(true);
                    characteristics = f;
                    break;
                }
            }
            if (characteristics == null) throw new NoSuchFieldException("CameraCharacteristics in " + caps.getClass());
        }
        return (CameraCharacteristics) characteristics.get(caps);
    }

    static boolean logicalMultiCamera(CameraCharacteristics cc) {
        int[] caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (caps == null) return false;
        for (int c : caps) if (c == LOGICAL_MULTI_CAMERA) return true;
        return false;
    }
}
