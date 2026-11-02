package dev.spundone.xcam17.aosp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Leica Essential's look picker with the Leica Moment build's eight looks, in place of the
 * camera's own list (FragmentLegendarySelected), as that build does. The engine keeps the choice
 * (preference lmoment_v101) and renders the photo with it; the camera's own look follows as M3
 * for the black and white look and M9 for the others, so the preview matches.
 *
 * names: the fragment's look component field, its item-selected handler (static, fragment and
 * item) and its hide method, "#"-separated.
 */
public final class LeicaLooks {
    /** MomentBridge's gallery order. */
    static final String[][] LOOKS = {
        {"M9", "Classic colour with the M9's warm tonality"},
        {"M3", "Black and white with silver-gelatin grain"},
        {"M9+", "Richer colour and stronger contrast"},
        {"M10-R", "Restrained colour, fine grain and a vignette"},
        {"ARRI 2049", "Cinema colour: teal shadows, warm highlights"},
        {"CCD", "Cool, clear film colour that protects skin tones"},
        {"Adaptive", "Tone and colour adjusted to the scene"},
        {"Classic", "The Leica port's earlier M9 rendering"},
    };
    private static final int ACCENT = 0xFFE60012;

    private static Dialog dialog;

    private LeicaLooks() {}

    /** After FragmentLegendarySelected.initView. */
    public static void open(Object fragment, View view, String names) {
        Class<?> bridge = Leica.moment();
        if (bridge == null || fragment == null || view == null) return;
        try {
            String[] n = names.split("#");
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            if (activity == null || activity.isFinishing()) return;
            Object component = fragment.getClass().getField(n[0]).get(fragment);
            Method hide = fragment.getClass().getMethod(n[2]);
            Method found = null;
            for (Method m : fragment.getClass().getDeclaredMethods()) {
                if (m.getName().equals(n[1]) && Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 2) found = m;
            }
            Method select = found;
            if (component == null || select == null) throw new IllegalStateException("look list members not found");
            String current = (String) component.getClass().getMethod("getComponentValue", int.class)
                    .invoke(component, Leica.MODULE);
            int selected = (int) bridge.getMethod("gallerySelection", Context.class, int.class)
                    .invoke(null, activity, "M3".equals(current) ? 1 : 0);
            close();
            view.setVisibility(View.INVISIBLE);
            dialog = picker(activity, bridge, selected, pos -> {
                try {
                    bridge.getMethod("galleryApply", Activity.class, Object.class, int.class).invoke(null, activity, null, pos);
                    int model = (int) bridge.getMethod("galleryModelIndex", int.class).invoke(null, pos);
                    String look = model == 1 ? "M3" : "M9";
                    if (!look.equals(current)) {
                        Object item = item(component, look);
                        if (item != null) select.invoke(null, fragment, item);
                    }
                    Toast.makeText(activity, "Leica look: " + LOOKS[pos][0], Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Log.once("leica: look not applied", t);
                }
            }, () -> {
                try {
                    hide.invoke(fragment);
                } catch (Throwable t) {
                    Log.once("leica: look list not hidden", t);
                }
            });
            dialog.show();
        } catch (Throwable t) {
            view.setVisibility(View.VISIBLE);
            Log.once("leica: Leica Moment looks picker", t);
        }
    }

    /** Before FragmentLegendarySelected.onPause. */
    public static void close() {
        Dialog d = dialog;
        dialog = null;
        if (d == null) return;
        d.setOnDismissListener(null);
        d.dismiss();
    }

    /** The look component's item for a value; the item class is obfuscated, its toString names the value. */
    static Object item(Object component, String value) throws Exception {
        Object items = component.getClass().getMethod("getItems").invoke(component);
        if (!(items instanceof List)) return null;
        for (Object item : (List<?>) items) {
            if (String.valueOf(item).contains("mValue='" + value + "'")) return item;
        }
        return null;
    }

    interface Choice {
        void chosen(int pos);
    }

    static Dialog picker(Activity activity, Class<?> bridge, int selected, Choice choice, Runnable closed) throws Exception {
        Context app = Leica.app();
        Method sample = bridge.getMethod("gallerySample", Context.class, int.class);
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2141414);
        float r = dp(activity, 24);
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);

        TextView title = text(activity, "Leica looks", 20, Color.WHITE, true);
        title.setPadding(dp(activity, 4), 0, 0, dp(activity, 12));
        panel.addView(title);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < LOOKS.length; i++) {
            final int pos = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(activity, 14));
            rowBg.setColor(i == selected ? 0x26FFFFFF : Color.TRANSPARENT);
            if (i == selected) rowBg.setStroke(dp(activity, 2), ACCENT);
            row.setBackground(rowBg);

            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageBitmap((Bitmap) sample.invoke(null, app, pos));
            } catch (Throwable t) {
                Log.once("leica: look samples", t.getCause() != null ? t.getCause() : t);
            }
            GradientDrawable clip = new GradientDrawable();
            clip.setCornerRadius(dp(activity, 10));
            clip.setColor(0xFF2A2A2A);
            image.setBackground(clip);
            image.setClipToOutline(true);
            row.addView(image, new LinearLayout.LayoutParams(dp(activity, 84), dp(activity, 63)));

            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(activity, 14), 0, 0, 0);
            words.addView(text(activity, LOOKS[i][0], 16, Color.WHITE, true));
            words.addView(text(activity, LOOKS[i][1], 13, 0xFFB0B0B0, false));
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            row.setOnClickListener(v -> {
                choice.chosen(pos);
                d.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(activity, 6);
            list.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        int maxHeight = activity.getResources().getDisplayMetrics().heightPixels * 3 / 5;
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(maxHeight, dp(activity, 79) * LOOKS.length)));

        d.setContentView(panel);
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(x -> {
            if (dialog == d) dialog = null;
            closed.run();
        });
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.4f);
        }
        return d;
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
