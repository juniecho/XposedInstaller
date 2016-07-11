package de.robv.android.xposed.installer.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class ThemeUtil {
	private static int[] THEMES = new int[] {
			R.style.Theme_XposedInstaller_Light,
			R.style.Theme_XposedInstaller_Dark,
			R.style.Theme_XposedInstaller_Dark_Black, };

	private ThemeUtil() {
	}

	public static int getSelectTheme() {
		int theme = XposedApp.getPreferences().getInt("theme", 0);
		return (theme >= 0 && theme < THEMES.length) ? theme : 0;
	}

	public static void setTheme(XposedBaseActivity activity) {
		activity.mTheme = getSelectTheme();
		activity.setTheme(THEMES[activity.mTheme]);
	}

	public static void reloadTheme(XposedBaseActivity activity) {
		int theme = getSelectTheme();
		if (theme != activity.mTheme)
			activity.recreate();
	}

	public static int getThemeColor(Context context, int id) {
		Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes(new int[] { id });
		int result = a.getColor(0, 0);
		a.recycle();
		return result;
	}

    public static void tintStatusBar(Activity activity, View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        int color = getColor(activity);

        activity.getWindow().setStatusBarColor(darkenColor(color, 0.85f));

        applyStatusBarLight(color, view);
    }

    public static void applyStatusBarLight(int color, View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (!isDark(color)) {
            setLightStatusBar(view);
        } else {
            clearLightStatusBar(view);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void setLightStatusBar(@NonNull View view) {
        int flags = view.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        view.setSystemUiVisibility(flags);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void clearLightStatusBar(@NonNull View view) {
        int flags = view.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        view.setSystemUiVisibility(flags);
    }

    /**
     * Check that the lightness value (0–1)
     */
    private static boolean isDark(float[] hsl) { // @Size(3)
        return hsl[2] < 0.65f;
    }

    /**
     * Convert to HSL & check that the lightness value
     */
    private static boolean isDark(@ColorInt int color) {
        float[] hsl = new float[3];
        android.support.v4.graphics.ColorUtils.colorToHSL(color, hsl);
        return isDark(hsl);
    }

    public static int getColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        int defaultColor = context.getResources().getColor(R.color.colorPrimary);

        return prefs.getInt("colors", defaultColor);
    }

    public static void setColors(ActionBar actionBar, Activity activity) {
        int color = getColor(activity);
        SharedPreferences prefs = activity.getSharedPreferences(activity.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        int drawable = XposedApp.iconsValues[Integer.parseInt(prefs.getString("custom_icon", "0"))];

        if (actionBar != null)
            actionBar.setBackgroundDrawable(new ColorDrawable(color));

        if (Build.VERSION.SDK_INT >= 21) {

            ActivityManager.TaskDescription tDesc = new ActivityManager.TaskDescription(activity.getString(R.string.app_name),
                    drawableToBitmap(activity.getDrawable(drawable)), color);
            activity.setTaskDescription(tDesc);

            if (XposedApp.getPreferences().getBoolean("nav_bar", false)) {
                activity.getWindow().setNavigationBarColor(darkenColor(color, 0.85f));
            } else {
                int black = activity.getResources().getColor(android.R.color.black);
                activity.getWindow().setNavigationBarColor(black);
            }
        }
    }

    /**
     * Use this method to colorize toolbar icons to the desired target color
     *
     * @param toolbarView toolbar view being colored
     * @param activity    reference to activity needed to register observers
     */
    public static void colorizeToolbar(Activity activity, Toolbar toolbarView) {
        int toolbarIconsColor = getColor(activity);
        toolbarIconsColor = !isDark(toolbarIconsColor) ? Color.BLACK : Color.WHITE;

        final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(toolbarIconsColor, PorterDuff.Mode.MULTIPLY);

        for (int i = 0; i < toolbarView.getChildCount(); i++) {
            final View v = toolbarView.getChildAt(i);

            //Step 1 : Changing the color of back button (or open drawer button).
            if (v instanceof ImageButton) {
                //Action Bar back button
                ((ImageButton) v).getDrawable().setColorFilter(colorFilter);
                v.invalidate();
            }

            if (v instanceof ActionMenuView) {
                for (int j = 0; j < ((ActionMenuView) v).getChildCount(); j++) {

                    //Step 2: Changing the color of any ActionMenuViews - icons that
                    //are not back button, nor text, nor overflow menu icon.
                    final View innerView = ((ActionMenuView) v).getChildAt(j);

                    if (innerView instanceof ActionMenuItemView) {
                        int drawablesCount = ((ActionMenuItemView) innerView).getCompoundDrawables().length;
                        for (int k = 0; k < drawablesCount; k++) {
                            if (((ActionMenuItemView) innerView).getCompoundDrawables()[k] != null) {
                                final int finalK = k;

                                //Important to set the color filter in seperate thread,
                                //by adding it to the message queue
                                //Won't work otherwise.
                                innerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((ActionMenuItemView) innerView).getCompoundDrawables()[finalK].setColorFilter(colorFilter);
                                    }
                                });
                            }
                        }
                    }

                    if (innerView instanceof TextView) {
                        ((TextView) innerView).setTextColor(toolbarIconsColor);
                        v.invalidate();
                    }
                }
            }

            //Step 3: Changing the color of title and subtitle.
            toolbarView.setTitleTextColor(toolbarIconsColor);
            toolbarView.setSubtitleTextColor(toolbarIconsColor);
        }

        Drawable overflowIcon = toolbarView.getOverflowIcon();
        if (overflowIcon != null) {
            overflowIcon.setColorFilter(colorFilter);
            toolbarView.setOverflowIcon(overflowIcon);
        }
    }

    /**
     * @author PeterCxy https://github.com/PeterCxy/Lolistat/blob/aide/app/src/
     * main/java/info/papdt/lolistat/support/Utility.java
     */
    public static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
