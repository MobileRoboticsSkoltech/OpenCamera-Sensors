package net.sourceforge.opencamera.CameraController;

import net.sourceforge.opencamera.MyDebug;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;

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
	public boolean isFrontFacing(int cameraId) {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			String cameraIdS = manager.getCameraIdList()[cameraId];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
			return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
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

	/* Returns true if the device supports the required hardware level, or better.
	 * From http://msdx.github.io/androiddoc/docs//reference/android/hardware/camera2/CameraCharacteristics.html#INFO_SUPPORTED_HARDWARE_LEVEL
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
		if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
			return requiredLevel == deviceLevel;
		}
		// deviceLevel is not LEGACY, can use numerical sort
		return requiredLevel <= deviceLevel;
	}

	/* Rather than allowing Camera2 API on all Android 5+ devices, we restrict it to cases where all cameras have at least LIMITED support.
	 * (E.g., Nexus 6 has FULL support on back camera, LIMITED support on front camera.)
	 * For now, devices with only LEGACY support should still use the old API.
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
