package net.sourceforge.opencamera;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.RequiresApi;
import net.gotev.uploadservice.UploadServiceConfig;

/** We override the Application class to implement the workaround at
 *  https://issuetracker.google.com/issues/36972466#comment14 for Google bug crash. It seems ugly,
 *  but Google consider this a low priority despite calling these "bad behaviours" in applications!
 */
public class OpenCameraApplication extends Application {
    private static final String TAG = "OpenCameraApplication";

    @Override
    public void onCreate() {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate();
        checkAppReplacingState();

        Core core = new Core();
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            core.createUploadNotificationChannel();
        }
        core.initializeNotificationConfig();
    }

    private void checkAppReplacingState() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkAppReplacingState");
        if( getResources() == null ) {
            Log.e(TAG, "app is replacing, kill");
            Process.killProcess(Process.myPid());
        }
    }

    private class Core {
        private final String uploadNotificationChannelID = "UploadChannel";

        /**
         * Create channel that notifies the user of file upload in progress.
         */
        @RequiresApi(api = Build.VERSION_CODES.O)
        void createUploadNotificationChannel() {
            CharSequence name = "Open Camera File Upload";
            String description = "Notification channel for uploading images in the background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(uploadNotificationChannelID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        /**
         * Initializes Upload Service with upload notification channel.
         */
        void initializeNotificationConfig() {
            UploadServiceConfig.initialize(OpenCameraApplication.this, uploadNotificationChannelID, BuildConfig.DEBUG);
        }

    }
}
