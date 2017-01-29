package de.ursa.android.gomxpf.installer.receivers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.ursa.android.gomxpf.installer.util.DownloadsUtil;

public class DownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            DownloadsUtil.triggerDownloadFinishedCallback(context, downloadId);
        }
    }
}