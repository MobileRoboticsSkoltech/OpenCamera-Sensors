package net.sourceforge.opencamera;

public abstract class CameraControllerManager {
	public abstract int getNumberOfCameras();
	abstract boolean isFrontFacing(int cameraId);
}
