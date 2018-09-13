package net.sourceforge.opencamera.Preview;

import java.util.Date;
import java.util.List;

import android.graphics.Canvas;
import android.location.Location;
import android.net.Uri;
import android.util.Pair;
import android.view.MotionEvent;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.CameraController.RawImage;

/** A partial implementation of ApplicationInterface that provides "default" implementations. So
 *  sub-classing this is easier than implementing ApplicationInterface directly - you only have to
 *  provide the unimplemented methods to get started, and can later override
 *  BasicApplicationInterface's methods as required.
 *  Note there is no need for your subclass of BasicApplicationInterface to call "super" methods -
 *  these are just default implementations that should be overridden as required.
 */
public abstract class BasicApplicationInterface implements ApplicationInterface {
	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public int getCameraIdPref() {
		return 0;
	}

	@Override
	public String getFlashPref() {
		return "flash_off";
	}

	@Override
	public String getFocusPref(boolean is_video) {
		return "focus_mode_continuous_picture";
	}

	@Override
	public boolean isVideoPref() {
		return false;
	}

	@Override
	public String getSceneModePref() {
		return CameraController.SCENE_MODE_DEFAULT;
	}

	@Override
	public String getColorEffectPref() {
		return CameraController.COLOR_EFFECT_DEFAULT;
	}

	@Override
	public String getWhiteBalancePref() {
		return CameraController.WHITE_BALANCE_DEFAULT;
	}

	@Override
	public int getWhiteBalanceTemperaturePref() {
		return 0;
	}

	@Override
	public String getAntiBandingPref() {
		return CameraController.ANTIBANDING_DEFAULT;
	}

	@Override
	public String getEdgeModePref() {
		return CameraController.EDGE_MODE_DEFAULT;
	}

	@Override
	public String getNoiseReductionModePref() {
		return CameraController.NOISE_REDUCTION_MODE_DEFAULT;
	}

	@Override
	public String getISOPref() {
		return CameraController.ISO_DEFAULT;
	}

	@Override
	public int getExposureCompensationPref() {
		return 0;
	}

	@Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		return null;
	}

	@Override
	public int getImageQualityPref() {
		return 90;
	}

	@Override
	public boolean getFaceDetectionPref() {
		return false;
	}

	@Override
	public String getVideoQualityPref() {
		return "";
	}

	@Override
	public boolean getVideoStabilizationPref() {
		return false;
	}

	@Override
	public boolean getForce4KPref() {
		return false;
	}

	@Override
	public String getVideoBitratePref() {
		return "default";
	}

	@Override
	public String getVideoFPSPref() {
		return "default";
	}

	@Override
	public float getVideoCaptureRateFactor() {
		return 1.0f;
	}

	@Override
	public boolean useVideoLogProfile() {
		return false;
	}

	@Override
	public float getVideoLogProfileStrength() {
		return 0;
	}

	@Override
	public long getVideoMaxDurationPref() {
		return 0;
	}

	@Override
	public int getVideoRestartTimesPref() {
		return 0;
	}

	@Override
	public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
		VideoMaxFileSize video_max_filesize = new VideoMaxFileSize();
		video_max_filesize.max_filesize = 0;
		video_max_filesize.auto_restart = true;
		return video_max_filesize;
	}

	@Override
	public boolean getVideoFlashPref() {
		return false;
	}

	@Override
	public boolean getVideoLowPowerCheckPref() {
		return true;
	}

	@Override
	public String getPreviewSizePref() {
		return "preference_preview_size_wysiwyg";
	}

	@Override
	public String getPreviewRotationPref() {
		return "0";
	}

	@Override
	public String getLockOrientationPref() {
		return "none";
	}

	@Override
	public boolean getTouchCapturePref() {
		return false;
	}

	@Override
	public boolean getDoubleTapCapturePref() {
		return false;
	}

	@Override
	public boolean getPausePreviewPref() {
		return false;
	}

	@Override
	public boolean getShowToastsPref() {
		return true;
	}

	@Override
	public boolean getShutterSoundPref() {
		return true;
	}

	@Override
	public boolean getStartupFocusPref() {
		return true;
	}

	@Override
	public long getTimerPref() {
		return 0;
	}

	@Override
	public String getRepeatPref() {
		return "1";
	}

	@Override
	public long getRepeatIntervalPref() {
		return 0;
	}

	@Override
	public boolean getGeotaggingPref() {
		return false;
	}

	@Override
	public boolean getRequireLocationPref() {
		return false;
	}

	@Override
	public boolean getRecordAudioPref() {
		return true;
	}

	@Override
	public String getRecordAudioChannelsPref() {
		return "audio_default";
	}

	@Override
	public String getRecordAudioSourcePref() {
		return "audio_src_camcorder";
	}

	@Override
	public int getZoomPref() {
		return 0;
	}

	@Override
	public double getCalibratedLevelAngle() {
		return 0;
	}

	@Override
	public boolean canTakeNewPhoto() {
		return true;
	}

	@Override
	public long getExposureTimePref() {
		return CameraController.EXPOSURE_TIME_DEFAULT;
	}

	@Override
	public float getFocusDistancePref(boolean is_target_distance) {
		return 0;
	}

	@Override
	public boolean isExpoBracketingPref() {
		return false;
	}

	@Override
	public int getExpoBracketingNImagesPref() {
		return 3;
	}

	@Override
	public double getExpoBracketingStopsPref() {
		return 2.0;
	}

	@Override
	public int getFocusBracketingNImagesPref() {
		return 3;
	}

	@Override
	public boolean getFocusBracketingAddInfinityPref() {
		return false;
	}

	@Override
	public boolean isFocusBracketingPref() {
		return false;
	}

	@Override
	public boolean isCameraBurstPref() {
		return false;
	}

	@Override
	public int getBurstNImages() {
		return 5;
	}

	@Override
	public boolean getBurstForNoiseReduction() {
		return false;
	}

	@Override
	public boolean getOptimiseAEForDROPref() {
		return false;
	}

	@Override
	public RawPref getRawPref() {
		return RawPref.RAWPREF_JPEG_ONLY;
	}

	@Override
	public int getMaxRawImages() {
		return 2;
	}

	@Override
	public boolean useCamera2FakeFlash() {
		return false;
	}

	@Override
	public boolean useCamera2FastBurst() {
		return true;
	}

	@Override
	public boolean usePhotoVideoRecording() {
		return true;
	}

	@Override
	public boolean isTestAlwaysFocus() {
		return false;
	}

	@Override
	public void cameraSetup() {

	}

	@Override
	public void touchEvent(MotionEvent event) {

	}

	@Override
	public void startingVideo() {

	}

	@Override
	public void startedVideo() {

	}

	@Override
	public void stoppingVideo() {

	}

	@Override
	public void stoppedVideo(int video_method, Uri uri, String filename) {

	}

	@Override
	public void onFailedStartPreview() {

	}

	@Override
	public void onCameraError() {

	}

	@Override
	public void onPhotoError() {

	}

	@Override
	public void onVideoInfo(int what, int extra) {

	}

	@Override
	public void onVideoError(int what, int extra) {

	}

	@Override
	public void onVideoRecordStartError(VideoProfile profile) {

	}

	@Override
	public void onVideoRecordStopError(VideoProfile profile) {

	}

	@Override
	public void onFailedReconnectError() {

	}

	@Override
	public void onFailedCreateVideoFileError() {

	}

	@Override
	public void hasPausedPreview(boolean paused) {

	}

	@Override
	public void cameraInOperation(boolean in_operation, boolean is_video) {

	}

	@Override
	public void turnFrontScreenFlashOn() {

	}

	@Override
	public void cameraClosed() {

	}

	@Override
	public void timerBeep(long remaining_time) {

	}

	@Override
	public void layoutUI() {

	}

	@Override
	public void multitouchZoom(int new_zoom) {

	}

	@Override
	public void setCameraIdPref(int cameraId) {

	}

	@Override
	public void setFlashPref(String flash_value) {

	}

	@Override
	public void setFocusPref(String focus_value, boolean is_video) {

	}

	@Override
	public void setVideoPref(boolean is_video) {

	}

	@Override
	public void setSceneModePref(String scene_mode) {

	}

	@Override
	public void clearSceneModePref() {

	}

	@Override
	public void setColorEffectPref(String color_effect) {

	}

	@Override
	public void clearColorEffectPref() {

	}

	@Override
	public void setWhiteBalancePref(String white_balance) {

	}

	@Override
	public void clearWhiteBalancePref() {

	}

	@Override
	public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {

	}

	@Override
	public void setISOPref(String iso) {

	}

	@Override
	public void clearISOPref() {

	}

	@Override
	public void setExposureCompensationPref(int exposure) {

	}

	@Override
	public void clearExposureCompensationPref() {

	}

	@Override
	public void setCameraResolutionPref(int width, int height) {

	}

	@Override
	public void setVideoQualityPref(String video_quality) {

	}

	@Override
	public void setZoomPref(int zoom) {

	}

	@Override
	public void setExposureTimePref(long exposure_time) {

	}

	@Override
	public void clearExposureTimePref() {

	}

	@Override
	public void setFocusDistancePref(float focus_distance, boolean is_target_distance) {

	}

	@Override
	public void onDrawPreview(Canvas canvas) {

	}

	@Override
	public boolean onBurstPictureTaken(List<byte[]> images, Date current_date) {
		return false;
	}

	@Override
	public boolean onRawPictureTaken(RawImage raw_image, Date current_date) {
		return false;
	}

	@Override
	public void onCaptureStarted() {

	}

	@Override
	public void onPictureCompleted() {

	}

	@Override
	public void onContinuousFocusMove(boolean start) {

	}
}
