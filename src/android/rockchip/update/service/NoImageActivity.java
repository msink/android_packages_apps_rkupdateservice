package android.rockchip.update.service;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import android.content.DialogInterface;
import android.os.Bundle;

public class NoImageActivity extends AlertActivity implements DialogInterface.OnClickListener {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertController.AlertParams p = mAlertParams;
        p.mTitle = getString(R.string.NIA_title);
        p.mIconId = R.drawable.ic_dialog_alert;
        p.mMessage = String.format(getString(R.string.NIA_msg_format), "/flash", "/sdcard");
        p.mPositiveButtonText = getString(R.string.NIA_btn_ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = null;
        p.mNegativeButtonListener = null;
        setupAlert();
    }

    public void onClick(DialogInterface dialog, int which) {
        finish();
    }
}
