package de.ursa.android.gomxpf.installer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import de.ursa.android.gomxpf.installer.util.NavUtil;
import de.ursa.android.gomxpf.installer.util.ThemeUtil;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static de.ursa.android.gomxpf.installer.XposedApp.darkenColor;

public class AboutActivity extends XposedBaseActivity {

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
            ab.setTitle(R.string.nav_item_about);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating(toolbar, R.string.details);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.container, new AboutFragment()).commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent sharingIntent = new Intent(ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(EXTRA_TEXT, getString(R.string.share_app_text, getString(R.string.support_material_xda)));
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share)));

        return super.onOptionsItemSelected(item);
    }

    public static class AboutFragment extends Fragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (Build.VERSION.SDK_INT >= 21)
                getActivity().getWindow().setStatusBarColor(darkenColor(XposedApp.getColor(getActivity()), 0.85f));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.tab_about, container, false);

            View changelogView = v.findViewById(R.id.changelogView);
            View developersView = v.findViewById(R.id.developersView);
            View licensesView = v.findViewById(R.id.licensesView);
            View translatorsView = v.findViewById(R.id.translatorsView);
            View sourceCodeView = v.findViewById(R.id.sourceCodeView);

            String packageName = getActivity().getPackageName();
            String translator = getResources().getString(R.string.translator);

            SharedPreferences prefs = getContext().getSharedPreferences(packageName + "_preferences", MODE_PRIVATE);

            final String changes = prefs.getString("changelog_" + XposedApp.THIS_APK_VERSION, null);
            changelogView.setVisibility(View.GONE);

            try {
                String version = getActivity().getPackageManager().getPackageInfo(packageName, 0).versionName;
                ((TextView) v.findViewById(R.id.app_version)).setText(version);
            } catch (NameNotFoundException ignored) {
            }

            createListener(licensesView, R.string.about_libraries_label, R.string.about_libraries);
            createListener(developersView, R.string.about_developers_label, R.string.about_developers);

            sourceCodeView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), getString(R.string.about_source));
                }
            });

            if (translator.isEmpty()) {
                translatorsView.setVisibility(View.GONE);
            }

            return v;
        }

        public void createListener(View v, final int title, final int content) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    return;
                    /* MaterialDialog dialog = new MaterialDialog.Builder(getContext()).title(title).content(content).positiveText(android.R.string.ok).show();
                    TextView dialogTextView = (TextView) dialog.findViewById(android.R.id.content);
                    if (dialogTextView != null) {
                        dialogTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    } */
                }
            });
        }
    }
}