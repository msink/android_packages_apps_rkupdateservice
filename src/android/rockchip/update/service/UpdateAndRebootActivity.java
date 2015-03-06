package android.rockchip.update.service;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Power;
import android.util.Log;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

public class UpdateAndRebootActivity extends AlertActivity {
    private static final String TAG = "UpdateAndRebootActivity"; 
    private static void LOG(String msg) { Log.d(TAG, msg); }

    public final static int COMMAND_START_UPDATING = 1;

    private String mImageFilePath;
    private Context mContext;
    private UiHandler mUiHandler;
    private WorkHandler mWorkHandler;
    private RKUpdateService.LocalBinder mBinder;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBinder = (RKUpdateService.LocalBinder) service;
            mWorkHandler.sendEmptyMessageDelayed(COMMAND_START_UPDATING, 3000);
        }
        public void onServiceDisconnected(ComponentName className)  {
            mBinder = null;
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        Intent startIntent = getIntent();
        mImageFilePath = startIntent.getExtras().getString(RKUpdateService.EXTRA_IMAGE_PATH);

        AlertController.AlertParams params = mAlertParams;
        params.mTitle = getString(R.string.updating_title);
        params.mIconId = R.drawable.ic_dialog_alert;
        String message = getText(R.string.updating_prompt).toString();
        if (mImageFilePath.contains("/sdcard")) {
            message += getText(R.string.updating_prompt_sdcard).toString();
        }
        params.mMessage = message;
        params.mPositiveButtonText = null;
        params.mPositiveButtonListener = null;
        params.mNegativeButtonText = null;
        params.mNegativeButtonListener = null;
        setupAlert();

        LOG("onCreate() : start 'work thread'.");
        HandlerThread thread = new HandlerThread("UpdateAndRebootActivity : work thread");
        thread.start();
        mWorkHandler = new WorkHandler(thread.getLooper());
        mUiHandler = new UiHandler();
        mContext.bindService(new Intent(mContext, RKUpdateService.class),
            mConnection, Context.BIND_AUTO_CREATE);
    }

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(R.string.update_error_summary);
        builder.setTitle(R.string.update_error);
        builder.setPositiveButton(R.string.NIA_btn_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Power.releaseWakeLock("ota-lock");
                finish();
            }
        });
        builder.create().show();
    }

    protected void onPause() {
        super.onPause();
        Power.releaseWakeLock("ota-lock");
        LOG("onPause() : Entered.");
    }

    protected void onResume() {
        super.onResume();
        Power.acquireWakeLock(1, "ota-lock");
    }

    private class UiHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case COMMAND_START_UPDATING:
                dialog();
                break;
            }
        }
    }

    private class WorkHandler extends Handler {
        public WorkHandler(Looper looper) {
            super(looper);
        }
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case COMMAND_START_UPDATING:
                LOG("WorkHandler::handleMessage() : To perform 'COMMAND_START_UPDATING'.");
                if (mBinder != null) {
                    if (mImageFilePath.endsWith("img")) {
                        mBinder.updateFirmware(mImageFilePath,
                            RKUpdateService.COMMAND_INSTALL_RKIMAGE);
                    } else if (!mBinder.doesOtaPackageMatchProduct(mImageFilePath)) {
                        mUiHandler.sendEmptyMessage(COMMAND_START_UPDATING);
                    } else {
                        mBinder.updateFirmware(mImageFilePath,
                            RKUpdateService.COMMAND_INSTALL_PACKAGE);
                    }
                } else {
                    Log.d(TAG, "service have not connected!");
                }
                break;
            }
        }
    }
}
