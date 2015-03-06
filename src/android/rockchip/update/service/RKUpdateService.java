package android.rockchip.update.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Properties;

public class RKUpdateService extends Service {
    private static final String TAG = "RKUpdateService";
    private static void LOG(String msg) { Log.d(TAG, msg); }

    static final String SERVICE_NAME = "android.rockchip.update.service";

    public final static int COMMAND_CHECK_LOCAL_UPDATING = 1;
    public final static int COMMAND_CHECK_WIFI_UPDATING = 2;
    public final static int COMMAND_CHECK_NOW = 3;

    public final static int COMMAND_INSTALL_RKIMAGE = 1;
    public final static int COMMAND_INSTALL_PACKAGE = 2;

    public final static int RESULT_SUCCESS = 1;
    public final static int RESULT_FAILED = 2;

    public final static String EXTRA_IMAGE_PATH =
            "android.rockchip.update.extra.IMAGE_PATH";
    public final static String EXTRA_IMAGE_VERSION =
            "android.rockchip.update.extra.IMAGE_VERSION";
    public final static String EXTRA_CURRENT_VERSION =
            "android.rockchip.update.extra.CURRENT_VERSION";

    static {
        System.loadLibrary("rockchip_update_jni");
    }

    private static volatile boolean mWorkHandleLocked = false;
    final public static String CACHE_ROOT =
        Environment.getDownloadCacheDirectory().getAbsolutePath();
    final private static String[] IMAGE_FILE_DIRS =
        { "/flash/", "/sdcard/" };

    public static URI mRemoteURI = null;
    private boolean mIsFirstStartUp = true;
    private String mTargetURI = null;
    private String mOtaPackageVersion = null;
    private String mSystemVersion = null;
    private String mOtaPackageName = null;
    private String mOtaPackageLength = null;

    private SharedPreferences mAutoCheckSet;
    private Context mContext;

    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    public void onCreate() {
        super.onCreate();
        mContext = this;
        Log.d(TAG, "starting RKUpdateService, version is 1.3.1");
        try {
            mRemoteURI = new URI(getRemoteUri());
            LOG("remote uri is " + mRemoteURI.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        mAutoCheckSet = getSharedPreferences("auto_check", 0);
        HandlerThread thread = new HandlerThread("UpdateService : work thread");
        thread.start();
        mWorkHandler = new WorkHandler(thread.getLooper());
        if (mIsFirstStartUp) {
            Log.d(TAG, "first startup!!!");
            mIsFirstStartUp = false;
            String command = RecoverySystem.readFlagCommand();
            if (command != null) {
                LOG("command = " + command);
                if (command.contains("$path")) {
                    String path = command.substring(command.indexOf(61) + 1);
                    LOG("last_flag: path = " + path);
                    if (command.startsWith("success")) {
                        Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("flag", RESULT_SUCCESS);
                        intent.putExtra("path", path);
                        startActivity(intent);
                        mWorkHandleLocked = true;
                    } else if (command.startsWith("updating")) {
                        Intent intent = new Intent(mContext, NotifyDeleteActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("flag", RESULT_FAILED);
                        intent.putExtra("path", path);
                        startActivity(intent);
                        mWorkHandleLocked = true;
                    }
                }
            }
        }
    }

    public void onDestroy() {
        LOG("onDestroy.......");
        super.onDestroy();
    }

    public void onStart(Intent intent, int startId) {
        LOG("onStart.......");
        super.onStart(intent, startId);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG("onStartCommand.......");
        if (intent == null) {
            return START_NOT_STICKY;
        }
        int command = intent.getIntExtra("command", 0);
        int delayTime = intent.getIntExtra("delay", 1000);
        LOG("command = " + command + " delaytime = " + delayTime);
        if (command == 0) {
            return START_NOT_STICKY;
        }
        if (command == COMMAND_CHECK_WIFI_UPDATING &&
                !mAutoCheckSet.getBoolean("auto_check", true)) {
            LOG("user set not auto check!");
            return START_NOT_STICKY;
        }
        if (command == COMMAND_CHECK_NOW) {
            command = COMMAND_CHECK_WIFI_UPDATING;
        }
        Message msg = new Message();
        msg.what = command;
        msg.arg1 = 0;
        mWorkHandler.sendMessageDelayed(msg, (long)delayTime);
        return START_REDELIVER_INTENT;
    }

    private final LocalBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        public void updateFirmware(String imagePath, int mode) {
            LOG("updateFirmware(): imagePath = " + imagePath);
            try {
                mWorkHandleLocked = true;
                if (mode == COMMAND_INSTALL_PACKAGE) {
                    RecoverySystem.installPackage(mContext, new File(imagePath));
                } else if (mode == COMMAND_INSTALL_RKIMAGE) {
                    RecoverySystem.installRKimage(mContext, imagePath);
                }
            } catch (IOException e) {
                Log.e(TAG, "updateFirmware() : Reboot for updateFirmware() failed", e);
            }
        }
        public boolean doesOtaPackageMatchProduct(String imagePath) {
            LOG("doesImageMatchProduct(): start verify package , imagePath = " + imagePath);
            try {
                RecoverySystem.verifyPackage(new File(imagePath), null, null);
            } catch (GeneralSecurityException e) {
                LOG("doesImageMatchProduct(): verifaPackage faild!");
                return false;
            } catch (IOException exc) {
                LOG("doesImageMatchProduct(): verifaPackage faild!");
                return false;
            }
            return true;
        }
        public void deletePackage(String path) {
            LOG("try to deletePackage...");
            File f = new File(path);
            if (f.exists()) {
                f.delete();
                LOG("delete complete! path=" + path);
            } else {
                LOG("path=" + path + " ,file not exists!");
            }
        }
        public void unLockWorkHandler() {
            LOG("unLockWorkHandler...");
            mWorkHandleLocked = false;
        }
        public void LockWorkHandler() {
            mWorkHandleLocked = true;
            LOG("LockWorkHandler...!");
        }
    }

    private WorkHandler mWorkHandler;
    private class WorkHandler extends Handler {

        public WorkHandler(android.os.Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            String currentFirmwareVersion = null;

            switch (msg.what) {

            case COMMAND_CHECK_LOCAL_UPDATING:
                LOG("WorkHandler::handleMessage() : To perform 'COMMAND_CHECK_LOCAL_UPDATING'.");
                if (mWorkHandleLocked) {
                    LOG("WorkHandler::handleMessage() : locked !!!");
                    break;
                } 
                String[] validFirmwareImageFile = getValidFirmwareImageFile(IMAGE_FILE_DIRS);
                if (validFirmwareImageFile != null) {
                    if (validFirmwareImageFile.length == 1) {
                        String imageFile = validFirmwareImageFile[0];
                        String imageFileVersion = null;
                        if (imageFile.endsWith("img")) {
                            if (checkRKimage(imageFile) == false) {
                                LOG("WorkHandler::handleMessage() : not a valid rkimage !!");
                                break;
                            } 
                            imageFileVersion = getImageVersion(imageFile);
                            LOG("WorkHandler::handleMessage() : Find a VALID image file : '"
                               + imageFile + "'. imageFileVersion is '" + imageFileVersion);
                            currentFirmwareVersion = getCurrentFirmwareVersion();
                            LOG("WorkHandler::handleMessage() : Current system firmware version : '"
                               + currentFirmwareVersion + "'");
                            String tmp = currentFirmwareVersion;
                            currentFirmwareVersion = imageFileVersion;
                            imageFileVersion = tmp;
                        }
                        startProposingActivity(imageFile, currentFirmwareVersion, imageFileVersion);
                    } else {
                        LOG("WorkHandler::handleMessage() : Find a INVALID image file : '"
                            + validFirmwareImageFile[0] + "'. To notify user.");
                        notifyInvalidImage(validFirmwareImageFile[0]);
                    }
                }
                break;

            case COMMAND_CHECK_WIFI_UPDATING:
                if (mWorkHandleLocked) {
                    LOG("WorkHandler::handleMessage() : locked !!!");
                    break;
                }
                for (int retry = 0; retry < 1; retry++) {
                    try {
                        if (requestRemoteServerForUpdate() == true) {
                            LOG("find a remote update package, now start PackageDownloadActivity...");
                            Intent intent = new Intent(mContext, OtaUpdateNotifyActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("uri", mTargetURI);
                            intent.putExtra("OtaPackageLength", mOtaPackageLength);
                            intent.putExtra("OtaPackageName", mOtaPackageName);
                            intent.putExtra("OtaPackageVersion", mOtaPackageVersion);
                            intent.putExtra("SystemVersion", mSystemVersion);
                            mContext.startActivity(intent);
                        } else {
                            LOG("no find remote update package...");
                        }
                    } catch (IOException e) {
                        LOG("request remote server error...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                break;
            }
        }
    }

    private String[] getValidFirmwareImageFile(String[] searchPaths) {
        for (String dir_path : searchPaths) {
            String filePath = dir_path + "update.zip";
            LOG("getValidFirmwareImageFile() : Target image file path : " + filePath);
            if (new File(filePath).exists()) {
                return new String[]{filePath};
            }
        }
        for (String dir_path : searchPaths) {
            String filePath = dir_path + "update.img";
            if (new File(filePath).exists()) {
                return new String[]{filePath};
            }
        }
        return null;
    }

    private void startProposingActivity(String path, String imageVersion, String currentVersion) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(SERVICE_NAME,
            "android.rockchip.update.service.FirmwareUpdatingActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_IMAGE_PATH, path);
        intent.putExtra(EXTRA_IMAGE_VERSION, imageVersion);
        intent.putExtra(EXTRA_CURRENT_VERSION, currentVersion);
        mContext.startActivity(intent);
    }

    static native private String getImageProductName(String path);
    static native private String getImageVersion(String path);

    private boolean checkRKimage(String path) {
        String imageProductName = getImageProductName(path);
        LOG("checkRKimage() : imageProductName = " + imageProductName);
        if (imageProductName.equals(getProductName())) return true;
        return false;
    }

    private String getCurrentFirmwareVersion()  {
        return SystemProperties.get("ro.firmware.version");
    }

    private static String getProductName() {
        return SystemProperties.get("ro.product.model");
    }

    private void notifyInvalidImage(String path) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(SERVICE_NAME,
            "android.rockchip.update.service.InvalidFirmwareImageActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_IMAGE_PATH, path);
        mContext.startActivity(intent);
    }

    public static String getRemoteUri() {
        return "http://" + getRemoteHost() +
               "/OtaUpdater/android?product=" + getOtaProductName() +
               "&version=" + getSystemVersion();
    }

    public static String getRemoteHost() {
        String remoteHost = SystemProperties.get("ro.product.ota.host");
        if (remoteHost == null || remoteHost.length() == 0) {
            remoteHost = getRemoteHostFromFile();
            if (remoteHost == null) {
                remoteHost = "172.16.7.55:2300";
            }
        }
        return remoteHost;
    }

    public static String getRemoteHostFromFile() {
        String remoteHostPath = null;
        Properties props = new Properties();
        try {
            File f1 = new File(Environment.getExternalStorageDirectory() +
                               "/OTA_UPDATE_SERVICE_PATH");
            File f2 = new File("/system/OTA_UPDATE_SERVICE_PATH");
            if (f1.exists()) {
                InputStream in = new FileInputStream(f1);
                props.load(in);
                remoteHostPath = props.get("remoteHost").toString();
            }
            if (remoteHostPath == null) {
                InputStream in = new FileInputStream(f2);
                props.load(in);
                remoteHostPath = props.get("remoteHost").toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return remoteHostPath;
    }

    public static String getOtaProductName() {
        String productName = SystemProperties.get("ro.product.model");
        if (productName.contains(" ")) {
            productName = productName.replaceAll(" ", "");
        }
        return productName;
    }

    private boolean requestRemoteServerForUpdate()
            throws IOException, ClientProtocolException {
        HttpClient httpClient = CustomerHttpClient.getHttpClient();
        HttpHead httpHead = new HttpHead(mRemoteURI);
        HttpResponse response = httpClient.execute(httpHead);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            return false;
        }
        for (Header header : response.getAllHeaders()) {
            LOG(header.getName() + ":" + header.getValue());
        }
        Header[] headLength = response.getHeaders("OtaPackageLength");
        if (headLength.length > 0) {
            mOtaPackageLength = headLength[0].getValue();
        }
        Header[] headName = response.getHeaders("OtaPackageName");
        if (headName.length > 0) {
            mOtaPackageName = headName[0].getValue();
        }
        Header[] headVersion = response.getHeaders("OtaPackageVersion");
        if (headVersion.length > 0) {
            mOtaPackageVersion = headVersion[0].getValue();
        }
        Header[] headTargetURI = response.getHeaders("OtaPackageUri");
        if (headTargetURI.length > 0) {
            mTargetURI = headTargetURI[0].getValue();
        }
        mTargetURI = "http://" + getRemoteHost() +
            (mTargetURI.startsWith("/") ? mTargetURI : "/" + mTargetURI);
        mSystemVersion = getSystemVersion();
        LOG("OtaPackageName = " + mOtaPackageName +
           " OtaPackageVersion = " + mOtaPackageVersion +
           " OtaPackageLength = " + mOtaPackageLength +
           " SystemVersion = " + mSystemVersion + 
           "OtaPackageUri = " + mTargetURI);
        return true;
    }

    public static String getSystemVersion() {
        String version = SystemProperties.get("ro.product.version");
        if (version == null || version.length() == 0) {
            version = "1.0.0";
        }
        return version;
    }
}
