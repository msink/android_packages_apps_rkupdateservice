package android.rockchip.update.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class RKUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "RKUpdateReceiver";  

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action = " + action);
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "RKUpdateReceiver recv ACTION_BOOT_COMPLETED.");
            Intent serviceIntent = new Intent(RKUpdateService.SERVICE_NAME);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 1000);
            context.startService(serviceIntent);
        } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            String[] path = new String[1];
            path[0] = intent.getData().getPath();
            Intent serviceIntent = new Intent(RKUpdateService.SERVICE_NAME);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_LOCAL_UPDATING);
            serviceIntent.putExtra("delay", 1000);
            context.startService(serviceIntent);
            Log.d(TAG, "media is mounted to '" + path[0] + "'. To check local update.");
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cmanger = (ConnectivityManager)
                context.getSystemService("connectivity");
            NetworkInfo netInfo = cmanger.getActiveNetworkInfo();
            if (netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI
                    || !netInfo.isConnected()) {
                return;
            }
            Intent serviceIntent = new Intent(RKUpdateService.SERVICE_NAME);
            serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_WIFI_UPDATING);
            serviceIntent.putExtra("delay", 3000);
            context.startService(serviceIntent);
        }
    }
}
