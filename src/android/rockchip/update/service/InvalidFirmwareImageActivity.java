package android.rockchip.update.service;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import java.io.File;
import java.util.Formatter;
import java.util.Locale;

public class InvalidFirmwareImageActivity extends AlertActivity implements DialogInterface.OnClickListener {
    private static final String TAG = "InvalidFirmwareImageActivity"; 
    private static void LOG(String msg) { Log.d(TAG, msg); }

    private String SDCARD_ROOT = "/sdcard";;
    private static StringBuilder sFormatBuilder = new StringBuilder();;
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private String mImageFilePath;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            LOG("onReceive() : 'action' = " + intent.getAction());
            if (intent.getAction() == Intent.ACTION_MEDIA_UNMOUNTED) {
                String path = intent.getData().getPath();
                LOG("mReceiver.onReceive() : original mount point : " + path + "; image file path : " + mImageFilePath);
                if (mImageFilePath != null && mImageFilePath.contains(path)) {
                    LOG("mReceiver.onReceive() : Media that image file lives in is unmounted, to finish this activity.");
                    finish();
                }
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOG("onCreate() : Entered.");
        Bundle extr = getIntent().getExtras();
        mImageFilePath = extr.getString(RKUpdateService.EXTRA_IMAGE_PATH);
        AlertController.AlertParams p = mAlertParams;
        p.mIconId = R.drawable.ic_dialog_alert;
        p.mTitle = getString(R.string.IFIA_title);
        String messageFormat = getString(R.string.IFIA_msg);
        sFormatBuilder.setLength(0);
        sFormatter.format(messageFormat, mImageFilePath);
        p.mMessage = sFormatBuilder.toString();
        p.mPositiveButtonText = getString(R.string.IFIA_btn_yes);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.IFIA_btn_no);
        p.mNegativeButtonListener = this;
        setupAlert();
        IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        registerReceiver(mReceiver, filter);
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onDestroy() {
        super.onDestroy();
        LOG("onDestroy() : Entered.");
        mImageFilePath = null;
        unregisterReceiver(mReceiver);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            LOG("onClick() : User desided to delete the invalid image file.");
            if (new File(mImageFilePath).delete() == false) {
                Log.w(TAG, "onClick() : Failed to delete invalid image file : " + mImageFilePath);
            }
        }
        finish();
    }
}
