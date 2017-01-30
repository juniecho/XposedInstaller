package de.ursa.android.gomxpf.installer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ParseException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.ursa.android.gomxpf.installer.util.AssetUtil;
import de.ursa.android.gomxpf.installer.util.DownloadsUtil;
import de.ursa.android.gomxpf.installer.util.JSONUtils;
import de.ursa.android.gomxpf.installer.util.NavUtil;
import de.ursa.android.gomxpf.installer.util.RootUtil;
import de.ursa.android.gomxpf.installer.util.ThemeUtil;
import de.ursa.android.gomxpf.installer.util.XposedZip;

import static android.content.Context.MODE_PRIVATE;
import static de.ursa.android.gomxpf.installer.XposedApp.WRITE_EXTERNAL_PERMISSION;
import static de.ursa.android.gomxpf.installer.util.XposedZip.Installer;
import static de.ursa.android.gomxpf.installer.util.XposedZip.Uninstaller;

public class InstallerFragment extends Fragment implements DownloadsUtil.DownloadFinishedCallback {

    public static final String JAR_PATH = XposedApp.BASE_DIR + "bin/UrsaXpBridge.jar";
    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");
    private static final int INSTALL_MODE_NORMAL = 0;
    private static final int INSTALL_MODE_RECOVERY_AUTO = 1;
    private static final int INSTALL_MODE_RECOVERY_MANUAL = 2;
    private static final String BINARIES_FOLDER = AssetUtil.getBinariesFolder();
    private static List<String> messages = new LinkedList<>();
    private static ArrayList<Installer> installers;
    private static ArrayList<Uninstaller> uninstallers;
    private final LinkedList<String> mCompatibilityErrors = new LinkedList<>();
    private String APP_PROCESS_NAME = null;
    private RootUtil mRootUtil = new RootUtil();
    private boolean mHadSegmentationFault = false;
    private TextView txtInstallError, txtKnownIssue;
    private Button btnInstall, btnUninstall;
    private ProgressBar mLoading;
    private Spinner mInstallersChooser;
    private Spinner mUninstallersChooser;
    private ImageView mInfoInstaller, mInfoUninstaller;
    private String newApkVersion = XposedApp.THIS_APK_VERSION;
    private String newApkLink;
    private String newApkChangelog;
    private Button mUpdateButton;
    private ImageView mInfoUpdate;
    private Button mClickedButton;
    private ImageView mErrorIcon;
    private TextView mErrorTv;
    private CardView mUpdateView;
    private boolean isCompatible;

    private static int extractIntPart(String str) {
        int result = 0, length = str.length();
        for (int offset = 0; offset < length; offset++) {
            char c = str.charAt(offset);
            if ('0' <= c && c <= '9')
                result = result * 10 + (c - '0');
            else
                break;
        }
        return result;
    }

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static ArrayList<Installer> getInstallersBySdk(int sdk) {
        ArrayList<Installer> list = new ArrayList<>();
        for (Installer i : installers) {
            if (i.sdk == sdk)
                list.add(i);
        }
        return list;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.tab_installer, container, false);

        txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);

        btnInstall = (Button) v.findViewById(R.id.btnInstall);
        btnUninstall = (Button) v.findViewById(R.id.btnUninstall);

        mLoading = (ProgressBar) v.findViewById(R.id.loading);
        mInstallersChooser = (Spinner) v.findViewById(R.id.chooserInstallers);
        mUninstallersChooser = (Spinner) v.findViewById(R.id.chooserUninstallers);
        mUpdateButton = (Button) v.findViewById(R.id.updateButton);

        mInfoInstaller = (ImageView) v.findViewById(R.id.infoInstaller);
        mInfoUninstaller = (ImageView) v.findViewById(R.id.infoUninstaller);

        mInfoUpdate = (ImageView) v.findViewById(R.id.infoUpdate);

        mErrorIcon = (ImageView) v.findViewById(R.id.errorIcon);
        mErrorTv = (TextView) v.findViewById(R.id.errorTv);

        mUpdateView = (CardView) v.findViewById(R.id.updateView);

        String installedXposedVersion = XposedApp.getXposedProp().get("version");
        View disableView = v.findViewById(R.id.disableView);
        Switch xposedDisable = (Switch) v.findViewById(R.id.disableSwitch);

        if (Build.VERSION.SDK_INT >= 21) {
            if (installedXposedVersion == null) {
                txtInstallError.setText(R.string.not_installed_no_lollipop);
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            } else {
                int installedXposedVersionInt = extractIntPart(installedXposedVersion);
                if (installedXposedVersionInt == XposedApp.getXposedVersion()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop,
                            installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
                } else {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                }
            }
        } else {
            int installedXposedVersionInt = XposedApp.getXposedVersion();
            if (installedXposedVersionInt != 0) {
                txtInstallError.setText(getString(R.string.installed_lollipop, installedXposedVersionInt));
                txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));

                if (DISABLE_FILE.exists()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersionInt));
                    txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                }
            } else {
                txtInstallError.setText(getString(R.string.not_installed_no_lollipop));
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        }

        isCompatible = true;
        if (Build.VERSION.SDK_INT == 15) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk15";
            isCompatible = checkCompatibility();
        } else if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 18) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
            isCompatible = checkCompatibility();
        } else if (Build.VERSION.SDK_INT == 19) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk19";
            isCompatible = checkCompatibility();
        }

        txtInstallError.setVisibility(View.VISIBLE);

        if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
            final View dontShowAgainView = inflater.inflate(R.layout.dialog_install_warning, null);

            new MaterialDialog.Builder(getActivity())
                    .title(R.string.install_warning_title)
                    .customView(dontShowAgainView, false)
                    .positiveText(android.R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            CheckBox checkBox = (CheckBox) dontShowAgainView.findViewById(android.R.id.checkbox);
                            if (checkBox.isChecked())
                                XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).apply();
                        }
                    }).cancelable(false).show();
        }

        new JSONParser().execute();

        mInfoInstaller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Installer selectedInstaller = (Installer) mInstallersChooser.getSelectedItem();
                String s = getString(R.string.infoInstaller,
                        selectedInstaller.name, selectedInstaller.sdk,
                        selectedInstaller.architecture,
                        selectedInstaller.version);

                new MaterialDialog.Builder(getContext()).title(R.string.info)
                        .content(s).positiveText(android.R.string.ok).show();
            }
        });
        mInfoUninstaller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uninstaller selectedUninstaller = (Uninstaller) mUninstallersChooser.getSelectedItem();
                String s = getString(R.string.infoUninstaller,
                        selectedUninstaller.name,
                        selectedUninstaller.architecture,
                        selectedUninstaller.date);

                new MaterialDialog.Builder(getContext()).title(R.string.info)
                        .content(s).positiveText(android.R.string.ok).show();
            }
        });

        btnInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClickedButton = btnInstall;
                if (checkPermissions())
                    return;

                areYouSure(R.string.warningArchitecture,
                        new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                Installer selectedInstaller = (Installer) mInstallersChooser.getSelectedItem();

                                DownloadsUtil.add(getContext(), selectedInstaller.name, selectedInstaller.link, InstallerFragment.this,
                                        DownloadsUtil.MIME_TYPES.ZIP, true);
                            }
                        });
            }
        });

        btnUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClickedButton = btnUninstall;
                if (checkPermissions())
                    return;

                areYouSure(R.string.warningArchitecture, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);

                        Uninstaller selectedUninstaller = (Uninstaller) mUninstallersChooser.getSelectedItem();

                        DownloadsUtil.add(getContext(),
                                selectedUninstaller.name,
                                selectedUninstaller.link,
                                InstallerFragment.this,
                                DownloadsUtil.MIME_TYPES.ZIP, true);
                    }
                });
            }
        });

        mUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClickedButton = mUpdateButton;
                if (checkPermissions())
                    return;

                new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/XposedInstaller/XposedInstaller_by_Gomdolius.apk").delete();

                DownloadsUtil.add(getContext(), "XposedInstaller_by_Gomdolius", newApkLink, new DownloadsUtil.DownloadFinishedCallback() {
                    @Override
                    public void onDownloadFinished(Context context, DownloadsUtil.DownloadInfo info) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(
                                Uri.fromFile(new File(Environment
                                        .getExternalStorageDirectory()
                                        .getAbsolutePath()
                                        + "/XposedInstaller/XposedInstaller_by_Gomdolius.apk")),
                                DownloadsUtil.MIME_TYPE_APK);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                }, DownloadsUtil.MIME_TYPES.APK, true);
            }
        });

        xposedDisable.setChecked(!DISABLE_FILE.exists());

        xposedDisable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Toast.makeText(getContext(), getString(R.string.xposed_on_next_reboot), Toast.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Toast.makeText(getContext(), getString(R.string.xposed_off_next_reboot), Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "InstallerFragment -> " + e.getMessage());
                    }
                }
            }
        });
        // xposedDisable.setVisibility(View.GONE);
        // disableView.setVisibility(View.GONE);
        return v;
    }

    private void hideAllFrameworkItems() {
        btnInstall.setVisibility(View.GONE);
        btnUninstall.setVisibility(View.GONE);
        mInfoInstaller.setVisibility(View.GONE);
        mInfoUninstaller.setVisibility(View.GONE);
        mInstallersChooser.setVisibility(View.GONE);
        mUninstallersChooser.setVisibility(View.GONE);
    }

    private void showAllFrameworkItems() {
        btnInstall.setVisibility(View.VISIBLE);
        btnUninstall.setVisibility(View.VISIBLE);
        mInfoInstaller.setVisibility(View.VISIBLE);
        mInfoUninstaller.setVisibility(View.VISIBLE);
        mInstallersChooser.setVisibility(View.VISIBLE);
        mUninstallersChooser.setVisibility(View.VISIBLE);

        btnInstall.setEnabled(true);
        btnUninstall.setEnabled(true);
        mInfoInstaller.setEnabled(true);
        mInfoUninstaller.setEnabled(true);
        mInstallersChooser.setEnabled(true);
        mUninstallersChooser.setEnabled(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_installer, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:
                String arch = getArch();
                String info = getString(R.string.helpChoose) + "\n\n\n" + getString(R.string.detected_as, Build.VERSION.SDK_INT, arch + "\n");
                info += getUIFramework();
                new MaterialDialog.Builder(getContext()).title(R.string.help)
                        .content(info)
                        .positiveText(android.R.string.ok).show();
                break;
            case R.id.reboot:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            reboot(null);
                        }
                    });
                } else {
                    reboot(null);
                }
                break;
            case R.id.soft_reboot:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.soft_reboot, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            softReboot();
                        }
                    });
                } else {
                    softReboot();
                }
                break;
            case R.id.reboot_recovery:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_recovery, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            reboot("recovery");
                        }
                    });
                } else {
                    reboot("recovery");
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_PERMISSION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mClickedButton != null) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mClickedButton.performClick();
                        }
                    }, 500);
                }
            } else {
                Toast.makeText(getActivity(), R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getArch() {
        String info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info = Build.SUPPORTED_ABIS[0];
        } else {
            info = System.getenv("os.arch");
        }
        if ("x86".equals(info)) {
            return "x86"; // Intel 32 bit
        } else if ("x86_64".equals(info)) {
            return "x86_64"; // Intel 64 bit
        } else if (info.startsWith("arm64")) {
            return "arm64"; // Arm 64 bit
        } else {
            return "arm"; // Arm 32 bit
        }
    }
    
    private String getUIFramework() {
        String ui = "";
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung") && new File("/system/framework/twframework.jar").exists()) {
            return "Samsung TouchWiz";
        }
        if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi") && new File("/system/framework/framework-miui-res.apk").exists()) {
            return "Xiaomi MIUI";
        }
        return ui;
    }

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_PERMISSION);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        mHadSegmentationFault = false;
        refreshKnownIssue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRootUtil.dispose();
    }

    @SuppressLint("StringFormatInvalid")
    private void refreshKnownIssue() {
        String issueName = null;
        String issueLink = null;

        if (new File("/system/framework/core.jar.jex").exists()) {
            issueName = "Aliyun OS";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";

        } else if (new File("/data/miui/DexspyInstaller.jar").exists() || checkClassExists("miui.dexspy.DexspyInstaller")) {
            issueName = "MIUI/Dexspy";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";

        } else if (mHadSegmentationFault) {
            issueName = "Segmentation fault";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52292102&postcount=7";

        } else if (checkClassExists("com.huawei.android.content.res.ResourcesEx")
                || checkClassExists("android.content.res.NubiaResources")) {
            issueName = "Resources subclass";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52801382&postcount=8";
        }

        if (issueName != null) {
            final String issueLinkFinal = issueLink;
            txtKnownIssue.setText(getString(R.string.install_known_issue, issueName));
            txtKnownIssue.setVisibility(View.VISIBLE);
            txtKnownIssue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), issueLinkFinal);
                }
            });

            txtInstallError.setTextColor(ThemeUtil.getThemeColor(getActivity(), android.R.attr.textColorTertiary));
        } else {
            txtKnownIssue.setVisibility(View.GONE);
        }
    }

    private void showAlert(final String result) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showAlert(result);
                }
            });
            return;
        }

        MaterialDialog dialog = new MaterialDialog.Builder(getActivity()).content(result).positiveText(android.R.string.ok).build();
        dialog.show();

        TextView txtMessage = (TextView) dialog
                .findViewById(android.R.id.message);
        try {
            txtMessage.setTextSize(14);
        } catch (NullPointerException ignored) {
        }

        mHadSegmentationFault = result.toLowerCase(Locale.US).contains("segmentation fault");
        refreshKnownIssue();
    }

    private void areYouSure(int contentTextId, MaterialDialog.ButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
                .content(contentTextId)
                .iconAttr(android.R.attr.alertDialogIcon)
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(yesHandler).show();
    }

    private void showConfirmDialog(final String message, final MaterialDialog.ButtonCallback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showConfirmDialog(message, callback);
                }
            });
            return;
        }

        new MaterialDialog.Builder(getActivity())
                .content(message).positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(callback).show();

        mHadSegmentationFault = message.toLowerCase(Locale.US).contains("segmentation fault");
        refreshKnownIssue();
    }

    private boolean checkCompatibility() {
        mCompatibilityErrors.clear();
        return checkAppProcessCompatibility();
    }

    private boolean checkAppProcessCompatibility() {
        try {
            if (APP_PROCESS_NAME == null)
                return false;

            File testFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
            if (testFile == null) {
                mCompatibilityErrors.add("could not write app_process to cache");
                return false;
            }

            Process p = Runtime.getRuntime().exec(new String[]{testFile.getAbsolutePath(), "--xposedversion"});

            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = stdout.readLine();
            stdout.close();

            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errorLine;
            while ((errorLine = stderr.readLine()) != null) {
                mCompatibilityErrors.add(errorLine);
            }
            stderr.close();

            p.destroy();

            testFile.delete();
            return result != null && result.startsWith("Xposed version: ");
        } catch (IOException e) {
            mCompatibilityErrors.add(e.getMessage());
            return false;
        }
    }

    private boolean startShell() {
        if (mRootUtil.startShell())
            return true;

        showAlert(getString(R.string.root_failed));
        return false;
    }

    private int getInstallMode() {
        int mode = XposedApp.getPreferences().getInt("install_mode", INSTALL_MODE_NORMAL);
        if (mode < INSTALL_MODE_NORMAL || mode > INSTALL_MODE_RECOVERY_MANUAL)
            mode = INSTALL_MODE_NORMAL;
        return mode;
    }

    private String getInstallModeText() {
        final int installMode = getInstallMode();
        switch (installMode) {
            case INSTALL_MODE_NORMAL:
                return getString(R.string.install_mode_normal);
            case INSTALL_MODE_RECOVERY_AUTO:
                return getString(R.string.install_mode_recovery_auto);
            case INSTALL_MODE_RECOVERY_MANUAL:
                return getString(R.string.install_mode_recovery_manual);
        }
        throw new IllegalStateException("unknown install mode " + installMode);
    }

    private boolean prepareAutoFlash(List<String> messages, File file) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (!isCompatible) {
                messages.addAll(mCompatibilityErrors);
                messages.add(String.format(getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI));
                return false;
            }

            File appProcessFile = AssetUtil.writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00700);
            if (appProcessFile == null) {
                showAlert(getString(R.string.file_extract_failed, "app_process"));
                return false;
            }

            messages.add(getString(R.string.file_copying, "UrsaXpBridge.jar"));
            File jarFile = AssetUtil.writeAssetToFile("UrsaXpBridge.jar", new File(JAR_PATH), 00644);
            if (jarFile == null) {
                messages.add("");
                messages.add(getString(R.string.file_extract_failed, "UrsaXpBridge.jar"));
                return false;
            }

            mRootUtil.executeWithBusybox("sync", messages);
        }

        if (mRootUtil.execute("ls /cache/recovery", null) != 0) {
            messages.add(getString(R.string.file_creating_directory, "/cache/recovery"));
            if (mRootUtil.executeWithBusybox("mkdir /cache/recovery",
                    messages) != 0) {
                messages.add("");
                messages.add(getString(R.string.file_create_directory_failed, "/cache/recovery"));
                return false;
            }
        }

        messages.add(getString(R.string.file_copying, file));

        if (mRootUtil.executeWithBusybox("cp -a " + file.getAbsolutePath() + " /cache/recovery/", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_copy_failed, file, "/cache"));
            return false;
        }

        messages.add(getString(R.string.file_writing_recovery_command));
        if (mRootUtil.execute("echo --update_package=/cache/recovery/" + file.getName() + " > /cache/recovery/command", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_writing_recovery_command_failed));
            return false;
        }

        return true;
    }

    private void offerRebootToRecovery(List<String> messages, final String file,
                                       final int installMode) {
        if (installMode == INSTALL_MODE_RECOVERY_AUTO)
            messages.add(getString(R.string.auto_flash_note, file));
        else
            messages.add(getString(R.string.manual_flash_note, file));

        messages.add("");
        messages.add(getString(R.string.reboot_recovery_confirmation));
        showConfirmDialog(TextUtils.join("\n", messages).trim(),
                new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        reboot("recovery");
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        super.onNegative(dialog);
                        if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
                            // clean up to avoid unwanted flashing
                            mRootUtil.executeWithBusybox("rm /cache/recovery/command", null);
                            mRootUtil.executeWithBusybox("rm /cache/recovery/" + file, null);
                            AssetUtil.removeBusybox();
                        }
                    }
                }

        );
    }

    private void softReboot() {
        if (!startShell())
            return;

        List<String> messages = new LinkedList<>();
        if (mRootUtil.execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
    }

    private void reboot(String mode) {
        if (!startShell())
            return;

        List<String> messages = new LinkedList<>();

        String command = "reboot";
        if (mode != null) {
            command += " " + mode;
            if (mode.equals("recovery"))
                // create a flag used by some kernels to boot into recovery
                mRootUtil.executeWithBusybox("touch /cache/recovery/boot", messages);
        }

        if (mRootUtil.executeWithBusybox(command, messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
        AssetUtil.removeBusybox();
    }

    @Override
    public void onDownloadFinished(final Context context, final DownloadsUtil.DownloadInfo info) {
        messages.clear();
        Toast.makeText(context, getString(R.string.downloadZipOk, info.localFilename), Toast.LENGTH_LONG).show();

        if (getInstallMode() == INSTALL_MODE_RECOVERY_MANUAL)
            return;

        areYouSure(R.string.install_warning, new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                super.onPositive(dialog);

                if (!startShell()) return;

                prepareAutoFlash(messages, new File(info.localFilename));
                offerRebootToRecovery(messages, info.title, INSTALL_MODE_RECOVERY_AUTO);
            }
        });
    }

    private class JSONParser extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mLoading.setVisibility(View.VISIBLE);
            mInfoInstaller.setVisibility(View.GONE);
            mInfoUninstaller.setVisibility(View.GONE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            /* try {
                String originalJson = JSONUtils.getFileContent(JSONUtils.JSON_LINK);
                String newJson = JSONUtils.listZip();

                String jsonString = originalJson.replace("%XPOSED_ZIP%", newJson);

                JSONObject json = new JSONObject(jsonString);
                JSONArray installerArray = json.getJSONArray("installer");
                JSONArray uninstallerArray = json.getJSONArray("uninstaller");

                installers = new ArrayList<>();
                uninstallers = new ArrayList<>();

                for (int i = 0; i < installerArray.length(); i++) {
                    JSONObject jsonObject = installerArray.getJSONObject(i);

                    String link = jsonObject.getString("link");
                    String name = jsonObject.getString("name");
                    String architecture = jsonObject.getString("architecture");
                    String version = jsonObject.getString("version");
                    int sdk = jsonObject.getInt("sdk");

                    installers.add(new XposedZip.Installer(link, name, architecture, sdk, version));
                }

                for (int i = 0; i < uninstallerArray.length(); i++) {
                    JSONObject jsonObject = uninstallerArray.getJSONObject(i);

                    String link = jsonObject.getString("link");
                    String name = jsonObject.getString("name");
                    String architecture = jsonObject.getString("architecture");

                    @SuppressLint("SimpleDateFormat")
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    Date date = null;
                    try {
                        date = sdf.parse(jsonObject.getString("date"));
                    } catch (ParseException ignored) {
                    }
                    java.text.DateFormat dateFormat = DateFormat.getDateFormat(getContext());

                    uninstallers.add(new Uninstaller(link, name, architecture, dateFormat.format(date)));
                }

                newApkVersion = json.getJSONObject("apk").getString("version");
                newApkLink = json.getJSONObject("apk").getString("link");
                newApkChangelog = json.getJSONObject("apk").getString("changelog");
                return true;
            } catch (Exception e) {
                Log.e(XposedApp.TAG, "InstallerFragment -> " + e.getMessage());
                return false;
            } */
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mLoading.setVisibility(View.GONE);

            try {

                if (!result) {
                    mErrorIcon.setVisibility(View.VISIBLE);
                    mErrorTv.setVisibility(View.VISIBLE);
                    return;
                }

                showAllFrameworkItems();

                String arch = System.getProperty("os.arch");
                int archPos = 0;
                if (arch.contains("64")) {
                    archPos = 1;
                } else if (arch.contains("86")) {
                    archPos = Build.VERSION.SDK_INT > 19 ? 2 : 0;
                }

                List<Installer> listInstallers = getInstallersBySdk(Build.VERSION.SDK_INT);
                if (listInstallers.size() == 0) {
                    hideAllFrameworkItems();
                    mErrorTv.setVisibility(View.VISIBLE);
                    mErrorTv.setText(getString(R.string.phone_not_compatible, Build.VERSION.SDK_INT, Build.CPU_ABI));
                } else {
                    if (uninstallers.size() != 0) {
                        if (Build.VERSION.SDK_INT <= 19) {
                            List<XposedZip> temp = new ArrayList<>();
                            temp.add(listInstallers.get(0));

                            mInstallersChooser.setAdapter(new XposedZip.MyAdapter<>(getContext(), temp));

                            temp = new ArrayList<>();
                            temp.add(uninstallers.get(uninstallers.size() - 1));

                            mUninstallersChooser.setAdapter(new XposedZip.MyAdapter<>(getContext(), temp));
                        } else {
                            mInstallersChooser.setAdapter(new XposedZip.MyAdapter<>(getContext(), listInstallers));
                            mInstallersChooser.setSelection(archPos);

                            mUninstallersChooser.setAdapter(new XposedZip.MyAdapter<>(getContext(), uninstallers));
                            mUninstallersChooser.setSelection(archPos);
                        }
                    } else {
                        hideAllFrameworkItems();
                        mErrorTv.setVisibility(View.VISIBLE);
                        mErrorTv.setText(getString(R.string.phone_not_compatible, Build.VERSION.SDK_INT, Build.CPU_ABI));
                    }
                }

                if (newApkChangelog != null) {
                    mInfoUpdate.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialDialog.Builder(getContext())
                                    .title(R.string.changes)
                                    .content(Html.fromHtml(newApkChangelog))
                                    .positiveText(android.R.string.ok).show();
                        }
                    });
                } else {
                    mInfoUpdate.setVisibility(View.GONE);
                }

                if (newApkVersion == null)
                    return;

                SharedPreferences prefs;
                try {
                    prefs = getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", MODE_PRIVATE);

                    prefs.edit().putString("changelog_" + newApkVersion, newApkChangelog).apply();
                } catch (NullPointerException ignored) {
                }

                BigInteger a = new BigInteger(XposedApp.THIS_APK_VERSION);
                BigInteger b = new BigInteger(newApkVersion);

                if (a.compareTo(b) == -1) {
                    mUpdateView.setVisibility(View.VISIBLE);
                }

            } catch (NullPointerException ignored) {
            }
        }
    }
}
