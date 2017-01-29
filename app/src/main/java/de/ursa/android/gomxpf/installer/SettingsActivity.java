package de.ursa.android.gomxpf.installer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.ColorInt;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.color.ColorChooserDialog;

import java.io.File;
import java.io.IOException;

import de.ursa.android.gomxpf.installer.util.RepoLoader;
import de.ursa.android.gomxpf.installer.util.ThemeUtil;
import de.ursa.android.gomxpf.installer.util.UpdateService;

import static de.ursa.android.gomxpf.installer.XposedApp.darkenColor;

public class SettingsActivity extends XposedBaseActivity implements ColorChooserDialog.ColorCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setTheme(this);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.nav_item_settings);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating(toolbar, 0);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new SettingsFragment()).commit();
        }

    }

    @Override
    public void onColorSelection(ColorChooserDialog dialog,
                                 @ColorInt int color) {
        if (!dialog.isAccentMode()) {
            XposedApp.getPreferences().edit().putInt("colors", color).apply();
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
        public final static int[] PRIMARY_COLORS = new int[]{
                Color.parseColor("#F44336"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#9C27B0"),
                Color.parseColor("#673AB7"),
                Color.parseColor("#3F51B5"),
                Color.parseColor("#2196F3"),
                Color.parseColor("#03A9F4"),
                Color.parseColor("#00BCD4"),
                Color.parseColor("#009688"),
                Color.parseColor("#4CAF50"),
                Color.parseColor("#8BC34A"),
                Color.parseColor("#CDDC39"),
                Color.parseColor("#FFEB3B"),
                Color.parseColor("#FFC107"),
                Color.parseColor("#FF9800"),
                Color.parseColor("#FF5722"),
                Color.parseColor("#795548"),
                Color.parseColor("#9E9E9E"),
                Color.parseColor("#607D8B")
        };
        private static final File mDisableResourcesFlag = new File(XposedApp.BASE_DIR + "conf/disable_resources");
        private Preference nav_bar;
        private Preference colors;
        private PackageManager pm;
        private String packName;
        private Context mContext;
        private Preference.OnPreferenceChangeListener iconChange = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {

                String act = ".WelcomeActivity-";
                String[] iconsValues = new String[]{"dvdandroid", "hjmodi", "rovo", "rovo-old", "staol"};

                for (String s : iconsValues) {
                    pm.setComponentEnabledSetting(new ComponentName(mContext, packName + act + s), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }

                act += iconsValues[Integer.parseInt((String) newValue)];

                int drawable = XposedApp.iconsValues[Integer
                        .parseInt((String) newValue)];

                if (Build.VERSION.SDK_INT >= 21) {

                    ActivityManager.TaskDescription tDesc = new ActivityManager.TaskDescription(getString(R.string.app_name),
                            XposedApp.drawableToBitmap(mContext.getDrawable(drawable)),
                            XposedApp.getColor(mContext));
                    getActivity().setTaskDescription(tDesc);
                }

                pm.setComponentEnabledSetting(new ComponentName(mContext, packName + act), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                return true;
            }
        };

        public SettingsFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);

            nav_bar = findPreference("nav_bar");
            colors = findPreference("colors");
            if (Build.VERSION.SDK_INT < 21) {
                Preference heads_up = findPreference("heads_up");

                heads_up.setEnabled(false);
                nav_bar.setEnabled(false);
                heads_up.setSummary(heads_up.getSummary() + " LOLLIPOP+");
                nav_bar.setSummary("LOLLIPOP+");
            }

            mContext = getActivity();

            findPreference("release_type_global").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    RepoLoader.getInstance().setReleaseTypeGlobal((String) newValue);
                    return true;
                }
            });

            CheckBoxPreference prefDisableResources = (CheckBoxPreference) findPreference("disable_resources");
            prefDisableResources.setChecked(mDisableResourcesFlag.exists());
            prefDisableResources.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enabled = (Boolean) newValue;
                    if (enabled) {
                        try {
                            mDisableResourcesFlag.createNewFile();
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        mDisableResourcesFlag.delete();
                    }
                    return (enabled == mDisableResourcesFlag.exists());
                }
            });

            colors.setOnPreferenceClickListener(this);

            ListPreference customIcon = (ListPreference) findPreference("custom_icon");

            pm = mContext.getPackageManager();
            packName = mContext.getPackageName();

            customIcon.setOnPreferenceChangeListener(iconChange);

        }

        @Override
        public void onResume() {
            super.onResume();

            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            if (Build.VERSION.SDK_INT >= 21)
                getActivity().getWindow().setStatusBarColor(darkenColor(XposedApp.getColor(getActivity()), 0.85f));
        }

        @Override
        public void onPause() {
            super.onPause();

            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(colors.getKey()) || key.equals("theme") || key.equals(nav_bar.getKey()))
                getActivity().recreate();

            if (key.equals("update_service_interval")) {
                final Intent intent = new Intent(getActivity(), UpdateService.class);
                getActivity().stopService(intent);
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().startService(intent);
                    }
                }, 1000);

            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            SettingsActivity act = (SettingsActivity) getActivity();
            if (act == null)
                return false;

            if (preference.getKey().equals(colors.getKey()))
                new ColorChooserDialog.Builder(act, preference.getTitleRes())
                        .backButton(R.string.back)
                        .allowUserColorInput(false)
                        .customColors(PRIMARY_COLORS, null)
                        .doneButton(android.R.string.ok)
                        .preselect(XposedApp.getColor(act)).show();

            return true;
        }
    }
}