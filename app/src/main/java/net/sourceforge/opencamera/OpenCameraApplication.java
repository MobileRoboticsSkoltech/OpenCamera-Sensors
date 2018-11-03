package net.sourceforge.opencamera;

import android.app.Application;
import android.os.Process;
import android.util.Log;

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
    }

    private void checkAppReplacingState() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkAppReplacingState");
        if( getResources() == null ) {
            Log.e(TAG, "app is replacing, kill");
            Process.killProcess(Process.myPid());
        }
    }
}
