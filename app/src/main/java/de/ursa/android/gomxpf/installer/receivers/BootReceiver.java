package de.ursa.android.gomxpf.installer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.ursa.android.gomxpf.installer.util.UpdateService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                context.startService(new Intent().setClass(context, UpdateService.class));
            }
        }, 10 * 1000); //wait 10 sec before starting service
    }

}
