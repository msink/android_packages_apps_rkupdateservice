package android.rockchip.update.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

public class Setting extends Activity {
    private Context mContext;
    private SharedPreferences mAutoCheckSet;
    private CheckBox mSwh_AutoCheck;
    private Button mBtn_CheckNow;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting);
        mContext = this;
        mSwh_AutoCheck = (CheckBox)findViewById(R.id.swh_auto_check);
        mBtn_CheckNow = (Button)findViewById(R.id.btn_check_now);
        mAutoCheckSet = getSharedPreferences("auto_check", 0);
        mSwh_AutoCheck.setChecked(mAutoCheckSet.getBoolean("auto_check", true));
        mSwh_AutoCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor e = mAutoCheckSet.edit();
                e.putBoolean("auto_check", isChecked);
                e.commit();
            }
        });
        mBtn_CheckNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent serviceIntent = new Intent(RKUpdateService.SERVICE_NAME);
                serviceIntent.putExtra("command", RKUpdateService.COMMAND_CHECK_NOW);
                mContext.startService(serviceIntent);
            }
        });
    }
}
