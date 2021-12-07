package net.sourceforge.opencamera;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import net.gotev.uploadservice.UploadServiceConfig;

/**
 * The class that is initialized in Application in the onCreate method.
 */
public class Core {
    private final String uploadNotificationChannelID = "UploadChannel";

    public Core(Application context) {
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            createUploadNotificationChannel(context);
        }
        initializeNotificationConfig(context);
    }

    /**
     * Create channel that notifies the user of file upload in progress.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createUploadNotificationChannel(Application context) {
        CharSequence name = "Open Camera File Upload";
        String description = "Notification channel for uploading images in the background";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(uploadNotificationChannelID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Initializes Upload Service with upload notification channel.
     */
    void initializeNotificationConfig(Application context) {
        UploadServiceConfig.initialize(context, uploadNotificationChannelID, BuildConfig.DEBUG);
    }

}
