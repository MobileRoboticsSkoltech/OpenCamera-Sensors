package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.Preview.ApplicationInterface;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.UI.DrawPreview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";
	
	private MainActivity main_activity = null;
	private LocationSupplier locationSupplier = null;
	private StorageUtils storageUtils = null;
	private DrawPreview drawPreview = null;
	private ImageSaver imageSaver = null;

	private Rect text_bounds = new Rect();

	private boolean last_image_saf = false;
	private Uri last_image_uri = null;
	private String last_image_name = null;
	
	// camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
	private int cameraId = 0;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		this.locationSupplier = new LocationSupplier(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
		this.storageUtils = new StorageUtils(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
		this.drawPreview = new DrawPreview(main_activity, this);
		
		this.imageSaver = new ImageSaver();

        if( savedInstanceState != null ) {
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
        }

		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}

	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
    	state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
    	state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
    	state.putFloat("focus_distance", focus_distance);
	}

	LocationSupplier getLocationSupplier() {
		return locationSupplier;
	}
	
	StorageUtils getStorageUtils() {
		return storageUtils;
	}

    @Override
	public Context getContext() {
    	return main_activity;
    }
    
    @Override
	public boolean useCamera2() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if( main_activity.supportsCamera2() ) {
    		return sharedPreferences.getBoolean(PreferenceKeys.getUseCamera2PreferenceKey(), false);
        }
        return false;
    }
    
	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}
	
	@Override
	public int createOutputVideoMethod() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	        		return VIDEOMETHOD_URI;
	        	}
	        }
        	// if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
			if( MyDebug.LOG )
				Log.d(TAG, "intent uri not specified");
			// note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
			return VIDEOMETHOD_FILE;
        }
        boolean using_saf = storageUtils.isUsingSAF();
		return using_saf ? VIDEOMETHOD_SAF : VIDEOMETHOD_FILE;
	}

	@Override
	public File createOutputVideoFile() throws IOException {
		return storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "mp4");
	}

	@Override
	public Uri createOutputVideoSAF() throws IOException {
		return storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_VIDEO, "mp4");
	}

	@Override
	public Uri createOutputVideoUri() throws IOException {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	    			return intent_uri;
	        	}
	        }
        }
        throw new RuntimeException(); // programming error if we arrived here
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}
	
    @Override
	public String getFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
    }

    @Override
	public String getFocusPref(boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), "");
    }

    @Override
	public boolean isVideoPref() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(action) || MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "launching from video intent");
    		return true;
		}
        else if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action) ) {
    		if( MyDebug.LOG )
    			Log.d(TAG, "launching from photo intent");
    		return false;
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getIsVideoPreferenceKey(), false);
    }

    @Override
	public String getSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getSceneModePreferenceKey(), "auto");
		return value;
    }
    
    @Override
    public String getColorEffectPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getColorEffectPreferenceKey(), "none");
    }

    @Override
    public String getWhiteBalancePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getWhiteBalancePreferenceKey(), "auto");
    }

    @Override
	public String getISOPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), "auto");
    }
    
    @Override
	public int getExposureCompensationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getExposurePreferenceKey(), "0");
		if( MyDebug.LOG )
			Log.d(TAG, "saved exposure value: " + value);
		int exposure = 0;
		try {
			exposure = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "exposure: " + exposure);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "exposure invalid format, can't parse to int");
		}
		return exposure;
    }

    @Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<Integer, Integer>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
    }
    
    @Override
    public int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String image_quality_s = sharedPreferences.getString(PreferenceKeys.getQualityPreferenceKey(), "90");
		int image_quality = 0;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
    }
    
	@Override
	public boolean getFaceDetectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getFaceDetectionPreferenceKey(), false);
    }
    
	@Override
	public String getVideoQualityPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), "");
	}
	
    @Override
	public boolean getVideoStabilizationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getVideoStabilizationPreferenceKey(), false);
    }
    
    @Override
	public boolean getForce4KPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( cameraId == 0 && sharedPreferences.getBoolean(PreferenceKeys.getForceVideo4KPreferenceKey(), false) && main_activity.supportsForceVideo4K() ) {
			return true;
		}
		return false;
    }
    
    @Override
    public String getVideoBitratePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoBitratePreferenceKey(), "default");
    }

    @Override
    public String getVideoFPSPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoFPSPreferenceKey(), "default");
    }
    
    @Override
    public long getVideoMaxDurationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_duration_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "0");
		long video_max_duration = 0;
		try {
			video_max_duration = (long)Integer.parseInt(video_max_duration_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_duration value: " + video_max_duration_value);
    		e.printStackTrace();
    		video_max_duration = 0;
        }
		return video_max_duration;
    }

    @Override
    public int getVideoRestartTimesPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String restart_value = sharedPreferences.getString(PreferenceKeys.getVideoRestartPreferenceKey(), "0");
		int remaining_restart_video = 0;
		try {
			remaining_restart_video = Integer.parseInt(restart_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_restart value: " + restart_value);
    		e.printStackTrace();
    		remaining_restart_video = 0;
        }
		return remaining_restart_video;
    }
    
    @Override
	public long getVideoMaxFileSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_filesize_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "0");
		long video_max_filesize = 0;
		try {
			video_max_filesize = Integer.parseInt(video_max_filesize_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_filesize value: " + video_max_filesize_value);
    		e.printStackTrace();
    		video_max_filesize = 0;
        }
		return video_max_filesize;
	}

    @Override
	public boolean getVideoRestartMaxFileSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoRestartMaxFileSizePreferenceKey(), true);
	}

    @Override
    public boolean getVideoFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoFlashPreferenceKey(), false);
    }
    
    @Override
	public String getPreviewSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg");
    }
    
    @Override
    public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
    }
    
    @Override
    public String getLockOrientationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
    }

    @Override
    public boolean getTouchCapturePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	String value = sharedPreferences.getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none");
    	return value.equals("single");
    }
    
    @Override
	public boolean getDoubleTapCapturePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	String value = sharedPreferences.getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none");
    	return value.equals("double");
    }

    @Override
    public boolean getPausePreviewPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getPausePreviewPreferenceKey(), false);
    }

    @Override
	public boolean getShowToastsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getShowToastsPreferenceKey(), true);
    }

    public boolean getThumbnailAnimationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getThumbnailAnimationPreferenceKey(), true);
    }
    
    @Override
    public boolean getShutterSoundPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), true);
    }

    @Override
	public boolean getStartupFocusPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getStartupFocusPreferenceKey(), true);
    }

    @Override
    public long getTimerPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_timer value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
    public String getRepeatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
    }
    
    @Override
    public long getRepeatIntervalPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getBurstIntervalPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
    public boolean getGeotaggingPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
    }
    
    @Override
    public boolean getRequireLocationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getRequireLocationPreferenceKey(), false);
    }
    
    private boolean getGeodirectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getGPSDirectionPreferenceKey(), false);
    }
    
    @Override
	public boolean getRecordAudioPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getRecordAudioPreferenceKey(), true);
    }
    
    @Override
    public String getRecordAudioChannelsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioChannelsPreferenceKey(), "audio_default");
    }
    
    @Override
    public String getRecordAudioSourcePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioSourcePreferenceKey(), "audio_src_camcorder");
    }

    private boolean getAutoStabilisePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
		if( auto_stabilise && main_activity.supportsAutoStabilise() )
			return true;
		return false;
    }
    
    private String getStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getStampPreferenceKey(), "preference_stamp_no");
    }
    
    private String getStampDateFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getStampDateFormatPreferenceKey(), "preference_stamp_dateformat_default");
    }
    
    private String getStampTimeFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getStampTimeFormatPreferenceKey(), "preference_stamp_timeformat_default");
    }
    
    private String getStampGPSFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getStampGPSFormatPreferenceKey(), "preference_stamp_gpsformat_default");
    }
    
    private String getTextStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getTextStampPreferenceKey(), "");
    }
    
    private int getTextStampFontSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	int font_size = 12;
		String value = sharedPreferences.getString(PreferenceKeys.getStampFontSizePreferenceKey(), "12");
		if( MyDebug.LOG )
			Log.d(TAG, "saved font size: " + value);
		try {
			font_size = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "font_size: " + font_size);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "font size invalid format, can't parse to int");
		}
		return font_size;
    }
    
    @Override
    public int getZoomPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getZoomPref: " + zoom_factor);
    	return zoom_factor;
    }

    @Override
    public long getExposureTimePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getLong(PreferenceKeys.getExposureTimePreferenceKey(), CameraController.EXPOSURE_TIME_DEFAULT);
    }
    
    @Override
	public float getFocusDistancePref() {
    	return focus_distance;
    }

    @Override
    public boolean isTestAlwaysFocus() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test);
		}
    	return main_activity.is_test;
    }

	@Override
	public void stoppedVideo(final int video_method, final Uri uri, final String filename) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stoppedVideo");
			Log.d(TAG, "video_method " + video_method);
			Log.d(TAG, "uri " + uri);
			Log.d(TAG, "filename " + filename);
		}
		boolean done = false;
		if( video_method == VIDEOMETHOD_FILE ) {
			if( filename != null ) {
				File file = new File(filename);
				storageUtils.broadcastFile(file, false, true, true);
				done = true;
			}
		}
		else {
			if( uri != null ) {
				// see note in onPictureTaken() for where we call broadcastFile for SAF photos
	    	    File real_file = storageUtils.getFileFromDocumentUriSAF(uri);
				if( MyDebug.LOG )
					Log.d(TAG, "real_file: " + real_file);
                if( real_file != null ) {
	            	storageUtils.broadcastFile(real_file, false, true, true);
	            	main_activity.test_last_saved_image = real_file.getAbsolutePath();
                }
                else {
                	// announce the SAF Uri
	    		    storageUtils.announceUri(uri, false, true);
                }
			    done = true;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "done? " + done);

		String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
    		if( done && video_method == VIDEOMETHOD_FILE ) {
    			// do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
    		}
    		else {
    			if( MyDebug.LOG )
    				Log.d(TAG, "from video capture intent");
    			Intent output = null;
    			if( done ) {
    				// may need to pass back the Uri we saved to, if the calling application didn't specify a Uri
    				// set note above for VIDEOMETHOD_FILE
    				// n.b., currently this code is not used, as we always switch to VIDEOMETHOD_FILE if the calling application didn't specify a Uri, but I've left this here for possible future behaviour
    				if( video_method == VIDEOMETHOD_SAF ) {
    					output = new Intent();
    					output.setData(uri);
    					if( MyDebug.LOG )
    						Log.d(TAG, "pass back output uri [saf]: " + output.getData());
    				}
    			}
            	main_activity.setResult(done ? Activity.RESULT_OK : Activity.RESULT_CANCELED, output);
            	main_activity.finish();
    		}
        }
        else if( done ) {
			// create thumbnail
	    	long time_s = System.currentTimeMillis();
			Bitmap thumbnail = null;
		    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				if( video_method == VIDEOMETHOD_FILE ) {
					File file = new File(filename);
					retriever.setDataSource(file.getPath());
				}
				else {
					ParcelFileDescriptor pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "r");
					retriever.setDataSource(pfd_saf.getFileDescriptor());
				}
				thumbnail = retriever.getFrameAtTime(-1);
			}
		    catch(FileNotFoundException e) {
		    	// video file wasn't saved?
				Log.d(TAG, "failed to find thumbnail");
		    	e.printStackTrace();
		    }
		    catch(IllegalArgumentException e) {
		    	// corrupt video file?
				Log.d(TAG, "failed to find thumbnail");
		    	e.printStackTrace();
		    }
		    catch(RuntimeException e) {
		    	// corrupt video file?
				Log.d(TAG, "failed to find thumbnail");
		    	e.printStackTrace();
		    }
		    finally {
		    	try {
		    		retriever.release();
		    	}
		    	catch(RuntimeException ex) {
		    		// ignore
		    	}
		    }
		    if( thumbnail != null ) {
		    	ImageButton galleryButton = (ImageButton) main_activity.findViewById(R.id.gallery);
		    	int width = thumbnail.getWidth();
		    	int height = thumbnail.getHeight();
				if( MyDebug.LOG )
					Log.d(TAG, "    video thumbnail size " + width + " x " + height);
		    	if( width > galleryButton.getWidth() ) {
		    		float scale = (float) galleryButton.getWidth() / width;
		    		int new_width = Math.round(scale * width);
		    		int new_height = Math.round(scale * height);
					if( MyDebug.LOG )
						Log.d(TAG, "    scale video thumbnail to " + new_width + " x " + new_height);
		    		Bitmap scaled_thumbnail = Bitmap.createScaledBitmap(thumbnail, new_width, new_height, true);
	    		    // careful, as scaled_thumbnail is sometimes not a copy!
	    		    if( scaled_thumbnail != thumbnail ) {
	    		    	thumbnail.recycle();
	    		    	thumbnail = scaled_thumbnail;
	    		    }
		    	}
		    	final Bitmap thumbnail_f = thumbnail;
		    	main_activity.runOnUiThread(new Runnable() {
					public void run() {
		    	    	updateThumbnail(thumbnail_f);
					}
				});
		    }
			if( MyDebug.LOG )
				Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - time_s));
		}
	}

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
		drawPreview.clearContinuousFocusMove();
	}

	@Override
	public void onContinuousFocusMove(boolean start) {
		if( MyDebug.LOG )
			Log.d(TAG, "onContinuousFocusMove: " + start);
		drawPreview.onContinuousFocusMove(start);
	}

	@Override
	public void touchEvent(MotionEvent event) {
		main_activity.getMainUI().clearSeekBar();
		main_activity.getMainUI().closePopup();
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}
	
	@Override
	public void startingVideo() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getLockVideoPreferenceKey(), false) ) {
			main_activity.lockScreen();
		}
		main_activity.stopAudioListeners(); // important otherwise MediaRecorder will fail to start() if we have an audiolistener! Also don't want to have the speech recognizer going off
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_recording);
		view.setContentDescription( getContext().getResources().getString(R.string.stop_video) );
		view.setTag(R.drawable.take_video_recording); // for testing
	}

	@Override
	public void stoppingVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "stoppingVideo()");
		main_activity.unlockScreen();
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void onVideoInfo(int what, int extra) {
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			int message_id = 0;
			if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "max duration reached");
				message_id = R.string.video_max_duration;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "max filesize reached");
				message_id = R.string.video_max_filesize;
			}
			if( message_id != 0 )
				main_activity.getPreview().showToast(null, message_id);
			// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
			// fixed in 1.25; also was correct for 1.23 and earlier
			String debug_value = "info_" + what + "_" + extra;
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("last_video_error", debug_value);
			editor.apply();
		}
	}

	@Override
	public void onFailedStartPreview() {
		main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
	}

	@Override
	public void onPhotoError() {
	    main_activity.getPreview().showToast(null, R.string.failed_to_take_picture);
	}

	@Override
	public void onVideoError(int what, int extra) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		}
		int message_id = R.string.video_error_unknown;
		if( what == MediaRecorder.MEDIA_ERROR_SERVER_DIED  ) {
			if( MyDebug.LOG )
				Log.d(TAG, "error: server died");
			message_id = R.string.video_error_server_died;
		}
		main_activity.getPreview().showToast(null, message_id);
		// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
		// fixed in 1.25; also was correct for 1.23 and earlier
		String debug_value = "error_" + what + "_" + extra;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("last_video_error", debug_value);
		editor.apply();
	}
	
	@Override
	public void onVideoRecordStartError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStartError");
		String error_message = "";
		String features = main_activity.getPreview().getErrorFeatures(profile);
		if( features.length() > 0 ) {
			error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		else {
			error_message = getContext().getResources().getString(R.string.failed_to_record_video);
		}
		main_activity.getPreview().showToast(null, error_message);
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void onVideoRecordStopError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStopError");
		//main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
		String features = main_activity.getPreview().getErrorFeatures(profile);
		String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
		if( features.length() > 0 ) {
			error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		main_activity.getPreview().showToast(null, error_message);
	}
	
	@Override
	public void onFailedReconnectError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_reconnect_camera);
	}
	
	@Override
	public void onFailedCreateVideoFileError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

    @Override
	public void hasPausedPreview(boolean paused) {
	    View shareButton = (View) main_activity.findViewById(R.id.share);
	    View trashButton = (View) main_activity.findViewById(R.id.trash);
	    if( paused ) {
		    shareButton.setVisibility(View.VISIBLE);
		    trashButton.setVisibility(View.VISIBLE);
	    }
	    else {
			shareButton.setVisibility(View.GONE);
		    trashButton.setVisibility(View.GONE);
	    }
		
	}
	
    @Override
    public void cameraInOperation(boolean in_operation) {
    	drawPreview.cameraInOperation(in_operation);
    	main_activity.getMainUI().showGUI(!in_operation);
    }

	@Override
	public void cameraClosed() {
		main_activity.getMainUI().clearSeekBar();
		main_activity.getMainUI().destroyPopup(); // need to close popup - and when camera reopened, it may have different settings
		drawPreview.clearContinuousFocusMove();
	}
	
	void updateThumbnail(Bitmap thumbnail) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateThumbnail");
		main_activity.updateGalleryIcon(thumbnail);
		drawPreview.updateThumbnail(thumbnail);
	}
	
	@Override
	public void timerBeep(long remaining_time) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "timerBeep()");
			Log.d(TAG, "remaining_time: " + remaining_time);
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "play beep!");
			boolean is_last = remaining_time <= 1000;
			main_activity.playSound(is_last ? R.raw.beep_hi : R.raw.beep);
		}
		if( sharedPreferences.getBoolean(PreferenceKeys.getTimerSpeakPreferenceKey(), false) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "speak countdown!");
			int remaining_time_s = (int)(remaining_time/1000);
			if( remaining_time_s <= 60 )
				main_activity.speak("" + remaining_time_s);
		}
	}

	@Override
	public void layoutUI() {
		main_activity.getMainUI().layoutUI();
	}
	
	@Override
	public void multitouchZoom(int new_zoom) {
		main_activity.getMainUI().setSeekbarZoom();
	}

	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

    @Override
    public void setFlashPref(String flash_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
		editor.apply();
    }

    @Override
    public void setFocusPref(String focus_value, boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), focus_value);
		editor.apply();
		// focus may be updated by preview (e.g., when switching to/from video mode)
    	final int visibility = main_activity.getPreview().getCurrentFocusValue() != null && main_activity.getPreview().getCurrentFocusValue().equals("focus_mode_manual2") ? View.VISIBLE : View.INVISIBLE;
	    View focusSeekBar = (SeekBar) main_activity.findViewById(R.id.focus_seekbar);
	    focusSeekBar.setVisibility(visibility);
    }

    @Override
	public void setVideoPref(boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PreferenceKeys.getIsVideoPreferenceKey(), is_video);
		editor.apply();
    }

    @Override
    public void setSceneModePref(String scene_mode) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getSceneModePreferenceKey(), scene_mode);
		editor.apply();
    }
    
    @Override
	public void clearSceneModePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getSceneModePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setColorEffectPref(String color_effect) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getColorEffectPreferenceKey(), color_effect);
		editor.apply();
    }
	
    @Override
	public void clearColorEffectPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getColorEffectPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setWhiteBalancePref(String white_balance) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getWhiteBalancePreferenceKey(), white_balance);
		editor.apply();
    }

    @Override
	public void clearWhiteBalancePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getWhiteBalancePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setISOPref(String iso) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getISOPreferenceKey(), iso);
		editor.apply();
    }

    @Override
	public void clearISOPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getISOPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setExposureCompensationPref(int exposure) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getExposurePreferenceKey(), "" + exposure);
		editor.apply();
    }

    @Override
	public void clearExposureCompensationPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposurePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
		editor.apply();
    }
    
    @Override
    public void setVideoQualityPref(String video_quality) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), video_quality);
		editor.apply();
    }
    
    @Override
	public void setZoomPref(int zoom) {
		Log.d(TAG, "setZoomPref: " + zoom);
    	this.zoom_factor = zoom;
    }
    
    @Override
	public void setExposureTimePref(long exposure_time) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), exposure_time);
		editor.apply();
	}

    @Override
	public void clearExposureTimePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposureTimePreferenceKey());
		editor.apply();
    }

    @Override
	public void setFocusDistancePref(float focus_distance) {
		this.focus_distance = focus_distance;
	}

    private int getStampFontColor() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String color = sharedPreferences.getString(PreferenceKeys.getStampFontColorPreferenceKey(), "#ffffff");
		return Color.parseColor(color);
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
    	drawPreview.onDrawPreview(canvas);
    }

    public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, false);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, boolean align_top) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, align_top, null, true);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, boolean align_top, String ybounds_text, boolean shadow) {
		final float scale = getContext().getResources().getDisplayMetrics().density;
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(64);
		int alt_height = 0;
		if( ybounds_text != null ) {
			paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
			alt_height = text_bounds.bottom - text_bounds.top;
		}
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		if( ybounds_text != null ) {
			text_bounds.bottom = text_bounds.top + alt_height;
		}
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
			float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
			if( paint.getTextAlign() == Paint.Align.CENTER )
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
		text_bounds.left += location_x - padding;
		text_bounds.right += location_x + padding;
		if( align_top ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			// unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
			int y_diff = - text_bounds.top + padding - 1;
			text_bounds.top = location_y - 1;
			text_bounds.bottom = text_bounds.top + height;
			location_y += y_diff;
		}
		else {
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;
		}
		if( shadow ) {
			canvas.drawRect(text_bounds, paint);
		}
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}

    @Override
	public boolean onPictureTaken(byte [] data) {
        System.gc();
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken");

		boolean image_capture_intent = false;
	        Uri image_capture_intent_uri = null;
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			image_capture_intent = true;
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	image_capture_intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
    			if( MyDebug.LOG )
    				Log.d(TAG, "save to: " + image_capture_intent_uri);
	        }
        }

        boolean using_camera2 = main_activity.getPreview().usingCamera2API();
		int image_quality = getImageQualityPref();
        boolean do_auto_stabilise = getAutoStabilisePref() && main_activity.getPreview().hasLevelAngle();
		double level_angle = do_auto_stabilise ? main_activity.getPreview().getLevelAngle() : 0.0;
		if( do_auto_stabilise && main_activity.test_have_angle )
			level_angle = main_activity.test_angle;
		if( do_auto_stabilise && main_activity.test_low_memory )
	    	level_angle = 45.0;
		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
		boolean is_front_facing = main_activity.getPreview().getCameraController() != null && main_activity.getPreview().getCameraController().isFrontFacing();
		String preference_stamp = this.getStampPref();
		String preference_textstamp = this.getTextStampPref();
		int font_size = getTextStampFontSizePref();
        int color = getStampFontColor();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String pref_style = sharedPreferences.getString(PreferenceKeys.getStampStyleKey(), "preference_stamp_style_shadowed");
		String preference_stamp_dateformat = this.getStampDateFormatPref();
		String preference_stamp_timeformat = this.getStampTimeFormatPref();
		String preference_stamp_gpsformat = this.getStampGPSFormatPref();
		boolean store_location = getGeotaggingPref() && getLocation() != null;
		Location location = store_location ? getLocation() : null;
		boolean store_geo_direction = main_activity.getPreview().hasGeoDirection() && getGeodirectionPref();
		double geo_direction = store_geo_direction ? main_activity.getPreview().getGeoDirection() : 0.0;
		boolean has_thumbnail_animation = getThumbnailAnimationPref();
        
		boolean success = imageSaver.saveImage(data, main_activity,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				do_auto_stabilise, level_angle,
				is_front_facing,
				preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
				store_location, location, store_geo_direction, geo_direction,
				has_thumbnail_animation);
		
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken complete");
		
		return success;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
	public boolean onRawPictureTaken(DngCreator dngCreator, Image image) {
        System.gc();
		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken");
		boolean success = false;
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
			if( MyDebug.LOG )
				Log.e(TAG, "RAW requires LOLLIPOP or higher");
			return success;
		}

        FileOutputStream output = null;
        try {
    		File picFile = null;
    		Uri saveUri = null; // if non-null, then picFile is a temporary file, which afterwards we should redirect to saveUri

			if( storageUtils.isUsingSAF() ) {
				saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, "dng");
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "saveUri: " + saveUri);
				picFile = File.createTempFile("picFile", "dng", main_activity.getCacheDir());
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "temp picFile: " + picFile.getAbsolutePath());
			}
			else {
        		picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, "dng");
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "save to: " + picFile.getAbsolutePath());
			}

    		if( MyDebug.LOG )
    			Log.d(TAG, "save to: " + picFile.getAbsolutePath());
            output = new FileOutputStream(picFile);
            dngCreator.writeImage(output, image);
    		if( saveUri == null ) { // if saveUri is non-null, then we haven't succeeded until we've copied to the saveUri
    			success = true;
            	// broadcast for SAF is done later, when we've actually written out the file
        		storageUtils.broadcastFile(picFile, true, false, false);
    		}

            if( saveUri != null ) {
            	imageSaver.copyUriToFile(main_activity, saveUri, picFile);
    		    success = true;
    		    /* We still need to broadcastFile for SAF - see notes above for onPictureTaken().
    		    */
	    	    File real_file = storageUtils.getFileFromDocumentUriSAF(saveUri);
				if( MyDebug.LOG )
					Log.d(TAG, "real_file: " + real_file);
                if( real_file != null ) {
					if( MyDebug.LOG )
						Log.d(TAG, "broadcast file");
	            	storageUtils.broadcastFile(real_file, true, false, false);
                }
                else {
					if( MyDebug.LOG )
						Log.d(TAG, "announce SAF uri");
                	// announce the SAF Uri
	    		    storageUtils.announceUri(saveUri, true, false);
                }
            }
        }
        catch(FileNotFoundException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "File not found: " + e.getMessage());
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "ioexception writing raw image file");
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        finally {
        	if( output != null ) {
				try {
					output.close();
				}
				catch(IOException e) {
					if( MyDebug.LOG )
						Log.e(TAG, "ioexception closing raw output");
					e.printStackTrace();
				}
        	}
        }

        System.gc();
		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken complete");
		return success;
	}
	
	void setLastImage(File file) {
		if( MyDebug.LOG )
			Log.d(TAG, "setLastImage");
    	last_image_saf = false;
    	last_image_name = file.getAbsolutePath();
    	last_image_uri = Uri.parse("file://" + last_image_name);
	}

	void setLastImageSAF(Uri uri) {
		if( MyDebug.LOG )
			Log.d(TAG, "setLastImageSAF");
    	last_image_saf = true;
    	last_image_name = null;
    	last_image_uri = uri;
	}
	
	void clearLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImage");
		last_image_saf = false;
		last_image_name = null;
		last_image_uri = null;
	}

	void shareLastImage() {
		Preview preview  = main_activity.getPreview();
		if( preview.isPreviewPaused() ) {
			if( last_image_uri != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Share: " + last_image_uri);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, last_image_uri);
				main_activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			clearLastImage();
			preview.startCameraPreview();
		}
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	void trashLastImage() {
		Preview preview  = main_activity.getPreview();
		if( preview.isPreviewPaused() ) {
			if( last_image_saf && last_image_uri != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Delete: " + last_image_uri);
	    	    File file = storageUtils.getFileFromDocumentUriSAF(last_image_uri); // need to get file before deleting it, as fileFromDocumentUriSAF may depend on the file still existing
				if( !DocumentsContract.deleteDocument(main_activity.getContentResolver(), last_image_uri) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to delete " + last_image_uri);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "successfully deleted " + last_image_uri);
    	    	    preview.showToast(null, R.string.photo_deleted);
                    if( file != null ) {
                    	// SAF doesn't broadcast when deleting them
    	            	storageUtils.broadcastFile(file, false, false, true);
                    }
				}
			}
			else if( last_image_name != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Delete: " + last_image_name);
				File file = new File(last_image_name);
				if( !file.delete() ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to delete " + last_image_name);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "successfully deleted " + last_image_name);
    	    	    preview.showToast(null, R.string.photo_deleted);
	            	storageUtils.broadcastFile(file, false, false, true);
				}
			}
			clearLastImage();
			preview.startCameraPreview();
		}
    	// Calling updateGalleryIcon() immediately has problem that it still returns the latest image that we've just deleted!
    	// But works okay if we call after a delay. 100ms works fine on Nexus 7 and Galaxy Nexus, but set to 500 just to be safe.
    	final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				main_activity.updateGalleryIcon();
			}
		}, 500);
	}

	// for testing

	public boolean hasThumbnailAnimation() {
		return this.drawPreview.hasThumbnailAnimation();
	}
}
