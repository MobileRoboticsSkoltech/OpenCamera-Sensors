package net.sourceforge.opencamera.CameraController;

import net.sourceforge.opencamera.MyDebug;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
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
	private static final String TAG = "CameraControllerManager2";

	private Context context = null;

	public CameraControllerManager2(Context context) {
		this.context = context;
	}

	@Override
	public int getNumberOfCameras() {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			return manager.getCameraIdList().length;
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
		catch(AssertionError e) {
			// had reported java.lang.AssertionError on Google Play, "Expected to get non-empty characteristics" from CameraManager.getOrCreateDeviceIdListLocked(CameraManager.java:465)
			// yes, in theory we shouldn't catch AssertionError as it represents a programming error, however it's a programming error by Google (a condition they thought couldn't happen)
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
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
		return false;
	}

	/* Rather than allowing Camera2 API on all Android 5+ devices, we restrict it to cases where all cameras have at least LIMITED support.
	 * (E.g., Nexus 6 has FULL support on back camera, LIMITED support on front camera.)
	 * For now, devices with only LEGACY support should still with the old API.
	 */
	public boolean allowCamera2Support(int cameraId) {
		CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
		try {
			String cameraIdS = manager.getCameraIdList()[cameraId];
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
			int support = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
			if( MyDebug.LOG ) {
				if( support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
					Log.d(TAG, "Camera " + cameraId + " has LEGACY Camera2 support");
				else if( support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
					Log.d(TAG, "Camera " + cameraId + " has LIMITED Camera2 support");
				else if( support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
					Log.d(TAG, "Camera " + cameraId + " has FULL Camera2 support");
				else
					Log.d(TAG, "Camera " + cameraId + " has unknown Camera2 support?!");
			}
			return support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED || support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
		return false;
	}
}
