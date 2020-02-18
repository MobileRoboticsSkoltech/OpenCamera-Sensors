package net.sourceforge.opencamera.cameracontroller;

import net.sourceforge.opencamera.R;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

/** Provides support using Android's original camera API
 *  android.hardware.Camera.
 */
public class CameraControllerManager1 extends CameraControllerManager {
    private static final String TAG = "CControllerManager1";
    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    @Override
    public CameraController.Facing getFacing(int cameraId) {
        try {
            Camera.CameraInfo camera_info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, camera_info);
            switch( camera_info.facing ) {
                case Camera.CameraInfo.CAMERA_FACING_FRONT:
                    return CameraController.Facing.FACING_FRONT;
                case Camera.CameraInfo.CAMERA_FACING_BACK:
                    return CameraController.Facing.FACING_BACK;
            }
            Log.e(TAG, "unknown camera_facing: " + camera_info.facing);
        }
        catch(RuntimeException e) {
            // Had a report of this crashing on Galaxy Nexus - may be device specific issue, see http://stackoverflow.com/questions/22383708/java-lang-runtimeexception-fail-to-get-camera-info
            // but good to catch it anyway
            Log.e(TAG, "failed to get facing");
            e.printStackTrace();
        }
        return CameraController.Facing.FACING_UNKNOWN;
    }

    @Override
    public String getDescription(Context context, int cameraId) {
        switch( getFacing(cameraId) ) {
            case FACING_FRONT:
                return context.getResources().getString(R.string.front_camera);
            case FACING_BACK:
                return context.getResources().getString(R.string.back_camera);
        }
        return null;
    }
}
