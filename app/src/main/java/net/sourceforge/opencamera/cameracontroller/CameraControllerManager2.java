package net.sourceforge.opencamera.cameracontroller;

import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;
import android.util.SizeF;

/** Provides support using Android 5's Camera 2 API
 *  android.hardware.camera2.*.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraControllerManager2 extends CameraControllerManager {
    private static final String TAG = "CControllerManager2";

    private final Context context;

    public CameraControllerManager2(Context context) {
        this.context = context;
    }

    @Override
    public int getNumberOfCameras() {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            return manager.getCameraIdList().length;
        }
        catch(Throwable e) {
            // in theory we should only get CameraAccessException, but Google Play shows we can get a variety of exceptions
            // from some devices, e.g., AssertionError, IllegalArgumentException, RuntimeException, so just catch everything!
            // We don't want users to experience a crash just because of buggy camera2 drivers - instead the user can switch
            // back to old camera API.
            if( MyDebug.LOG )
                Log.e(TAG, "exception trying to get camera ids");
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public CameraController.Facing getFacing(int cameraId) {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraIdS = manager.getCameraIdList()[cameraId];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
            switch( characteristics.get(CameraCharacteristics.LENS_FACING) ) {
                case CameraMetadata.LENS_FACING_FRONT:
                    return CameraController.Facing.FACING_FRONT;
                case CameraMetadata.LENS_FACING_BACK:
                    return CameraController.Facing.FACING_BACK;
                case CameraMetadata.LENS_FACING_EXTERNAL:
                    return CameraController.Facing.FACING_EXTERNAL;
            }
            Log.e(TAG, "unknown camera_facing: " + characteristics.get(CameraCharacteristics.LENS_FACING));
        }
        catch(Throwable e) {
            // in theory we should only get CameraAccessException, but Google Play shows we can get a variety of exceptions
            // from some devices, e.g., AssertionError, IllegalArgumentException, RuntimeException, so just catch everything!
            // We don't want users to experience a crash just because of buggy camera2 drivers - instead the user can switch
            // back to old camera API.
            if( MyDebug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            e.printStackTrace();
        }
        return CameraController.Facing.FACING_UNKNOWN;
    }

    @Override
    public String getDescription(Context context, int cameraId) {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        String description = null;
        try {
            String cameraIdS = manager.getCameraIdList()[cameraId];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);

            switch( characteristics.get(CameraCharacteristics.LENS_FACING) ) {
                case CameraMetadata.LENS_FACING_FRONT:
                    description = context.getResources().getString(R.string.front_camera);
                    break;
                case CameraMetadata.LENS_FACING_BACK:
                    description = context.getResources().getString(R.string.back_camera);
                    break;
                case CameraMetadata.LENS_FACING_EXTERNAL:
                    description = context.getResources().getString(R.string.external_camera);
                    break;
                default:
                    Log.e(TAG, "unknown camera type");
                    return null;
            }

            SizeF view_angle = CameraControllerManager2.computeViewAngles(characteristics);
            if( view_angle.getWidth() > 90.5f ) {
                // count as ultra-wide
                description += ", " + context.getResources().getString(R.string.ultrawide);
            }
        }
        catch(Throwable e) {
            // see note under isFrontFacing() why we catch anything, not just CameraAccessException
            if( MyDebug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            e.printStackTrace();
        }
        return description;
    }

    /** Helper class to compute view angles from the CameraCharacteristics.
     * @return The width and height of the returned size represent the x and y view angles in
     *         degrees.
     */
    static SizeF computeViewAngles(CameraCharacteristics characteristics) {
        // Note this is an approximation (see http://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie ).
        // This does not take into account the aspect ratio of the preview or camera, it's up to the caller to do this (e.g., see Preview.getViewAngleX(), getViewAngleY()).
        Rect active_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        SizeF physical_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        android.util.Size pixel_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        float [] focal_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if( active_size == null || physical_size == null || pixel_size == null || focal_lengths == null || focal_lengths.length == 0 ) {
            // in theory this should never happen according to the documentation, but I've had a report of physical_size (SENSOR_INFO_PHYSICAL_SIZE)
            // being null on an EXTERNAL Camera2 device, see https://sourceforge.net/p/opencamera/tickets/754/
            if( MyDebug.LOG ) {
                Log.e(TAG, "can't get camera view angles");
            }
            // fall back to a default
            return new SizeF(55.0f, 43.0f);
        }
        //camera_features.view_angle_x = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getWidth(), (2.0 * focal_lengths[0])));
        //camera_features.view_angle_y = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getHeight(), (2.0 * focal_lengths[0])));
        float frac_x = ((float)active_size.width())/(float)pixel_size.getWidth();
        float frac_y = ((float)active_size.height())/(float)pixel_size.getHeight();
        float view_angle_x = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getWidth() * frac_x, (2.0 * focal_lengths[0])));
        float view_angle_y = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getHeight() * frac_y, (2.0 * focal_lengths[0])));
        if( MyDebug.LOG ) {
            Log.d(TAG, "frac_x: " + frac_x);
            Log.d(TAG, "frac_y: " + frac_y);
            Log.d(TAG, "view_angle_x: " + view_angle_x);
            Log.d(TAG, "view_angle_y: " + view_angle_y);
        }
        return new SizeF(view_angle_x, view_angle_y);
    }

    /* Returns true if the device supports the required hardware level, or better.
     * See https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL .
     * From Android N, higher levels than "FULL" are possible, that will have higher integer values.
     * Also see https://sourceforge.net/p/opencamera/tickets/141/ .
     */
    static boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if( MyDebug.LOG ) {
            switch (deviceLevel) {
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    Log.d(TAG, "Camera has LEGACY Camera2 support");
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                    Log.d(TAG, "Camera has EXTERNAL Camera2 support");
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    Log.d(TAG, "Camera has LIMITED Camera2 support");
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    Log.d(TAG, "Camera has FULL Camera2 support");
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                    Log.d(TAG, "Camera has Level 3 Camera2 support");
                    break;
                default:
                    Log.d(TAG, "Camera has unknown Camera2 support: " + deviceLevel);
                    break;
            }
        }

        // need to treat legacy and external as special cases; otherwise can then use numerical comparison

        if( deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ) {
            return requiredLevel == deviceLevel;
        }

        if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL ) {
            deviceLevel = CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED;
        }
        if( requiredLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL ) {
            requiredLevel = CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED;
        }

        return requiredLevel <= deviceLevel;
    }

    /* Rather than allowing Camera2 API on all Android 5+ devices, we restrict it to certain cases.
     * This returns whether the specified camera has at least LIMITED support.
     */
    public boolean allowCamera2Support(int cameraId) {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraIdS = manager.getCameraIdList()[cameraId];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
            //return isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY);
            return isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED);
        }
        catch(Throwable e) {
            // in theory we should only get CameraAccessException, but Google Play shows we can get a variety of exceptions
            // from some devices, e.g., AssertionError, IllegalArgumentException, RuntimeException, so just catch everything!
            // We don't want users to experience a crash just because of buggy camera2 drivers - instead the user can switch
            // back to old camera API.
            if( MyDebug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            e.printStackTrace();
        }
        return false;
    }
}
