package android.rockchip.update.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import java.util.Formatter;
import java.util.Locale;

public class FirmwareUpdatingActivity extends AlertActivity implements DialogInterface.OnClickListener {
    private static final String TAG = "FirmwareUpdatingActivity";
    private static void LOG(String msg) { Log.d(TAG, msg); }

    private String FLASH_ROOT = "/flash";
    private String SDCARD_ROOT = "/sdcard";
    private String mCurrentVersion;
    private String mImageFilePath;
    private String mImageVersion;
    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());

    protected void onCreate(Bundle savedInstanceState) {
        LOG("onCreate() : Entered.");
        super.onCreate(savedInstanceState);
        Bundle extr = getIntent().getExtras();
        mImageFilePath = extr.getString(RKUpdateService.EXTRA_IMAGE_PATH);
        mImageVersion = extr.getString(RKUpdateService.EXTRA_IMAGE_VERSION);
        mCurrentVersion = extr.getString(RKUpdateService.EXTRA_CURRENT_VERSION);
        AlertController.AlertParams p = mAlertParams;
        p.mTitle = getString(R.string.updating_title);
        String messageFormat = getString(R.string.updating_message_formate);
        sFormatBuilder.setLength(0);
        sFormatter.format(messageFormat, mImageFilePath);
        p.mMessage = sFormatBuilder.toString();
        p.mPositiveButtonText = getString(R.string.updating_button_install);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.updating_button_cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        registerReceiver(mReceiver, filter);
    }

    protected void onDestroy() {
        super.onDestroy();
        LOG("onDestroy() : Entered.");
        mImageFilePath = null;
        mImageVersion = null;
        mCurrentVersion = null;
        unregisterReceiver(mReceiver);
    }

    protected void onPause() {
        super.onPause();
        LOG("onPause() : Entered.");
    }

    protected void onResume() {
        super.onResume();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            LOG("mReceiver.onReceive() : 'action' =" + intent.getAction());
            if (intent.getAction() == Intent.ACTION_MEDIA_UNMOUNTED) {
                String path = intent.getData().getPath();
                LOG("mReceiver.onReceive() : original mount point : " + path + "; image file path : " + mImageFilePath);
                if (mImageFilePath != null && mImageFilePath.contains(path)) {
                    LOG("mReceiver.onReceive() : Media that img file live in is unmounted, to finish this activity.");
                    finish();
                }
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Intent intent = new Intent();
            intent.setClass(this, UpdateAndRebootActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RKUpdateService.EXTRA_IMAGE_PATH, mImageFilePath);
            startActivity(intent);
        }
        finish();
    }
}
