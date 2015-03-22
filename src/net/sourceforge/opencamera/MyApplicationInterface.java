package net.sourceforge.opencamera;

import java.io.File;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;

public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";
	
	MainActivity main_activity = null;
	LocationSupplier locationSupplier = null;
	StorageUtils storageUtils = null;
	
	MyApplicationInterface(MainActivity main_activity) {
		this.main_activity = main_activity;
		this.locationSupplier = new LocationSupplier(main_activity);
		this.storageUtils = new StorageUtils(main_activity);
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
    		return sharedPreferences.getBoolean(MainActivity.getUseCamera2PreferenceKey(), false);
        }
        return false;
    }
    
	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}

	@Override
	public File getOutputMediaFile(int type) {
		return storageUtils.getOutputMediaFile(type);
	}

    @Override
	public String getFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getFlashPreferenceKey(main_activity.getPreview().getCameraId()), "");
    }

    @Override
	public String getFocusPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getFocusPreferenceKey(main_activity.getPreview().getCameraId()), "");
    }

    @Override
	public boolean isVideoPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(MainActivity.getIsVideoPreferenceKey(), false);
    }

    @Override
	public String getSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(MainActivity.getSceneModePreferenceKey(), "auto");
		return value;
    }
    
    @Override
    public String getColorEffectPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getColorEffectPreferenceKey(), "none");
    }

    @Override
    public String getWhiteBalancePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getWhiteBalancePreferenceKey(), "auto");
    }

    @Override
	public String getISOPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getISOPreferenceKey(), "auto");
    }
    
    @Override
	public int getExposureCompensationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(MainActivity.getExposurePreferenceKey(), "0");
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
		String resolution_value = sharedPreferences.getString(MainActivity.getResolutionPreferenceKey(main_activity.getPreview().getCameraId()), "");
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
		String image_quality_s = sharedPreferences.getString(MainActivity.getQualityPreferenceKey(), "90");
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
		return sharedPreferences.getBoolean(MainActivity.getFaceDetectionPreferenceKey(), false);
    }
    
	@Override
	public String getVideoQualityPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getVideoQualityPreferenceKey(main_activity.getPreview().getCameraId()), "");
	}
	
    @Override
	public boolean getVideoStabilizationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(MainActivity.getVideoStabilizationPreferenceKey(), false);
    }
    
    @Override
	public boolean getForce4KPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( main_activity.getPreview().getCameraId() == 0 && sharedPreferences.getBoolean(MainActivity.getForceVideo4KPreferenceKey(), false) && main_activity.supportsForceVideo4K() ) {
			return true;
		}
		return false;
    }
    
    @Override
    public String getVideoBitratePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getVideoBitratePreferenceKey(), "default");
    }

    @Override
    public String getVideoFPSPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getVideoFPSPreferenceKey(), "default");
    }
    
    @Override
    public long getVideoMaxDurationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_duration_value = sharedPreferences.getString(MainActivity.getVideoMaxDurationPreferenceKey(), "0");
		long video_max_duration = 0;
		try {
			video_max_duration = Integer.parseInt(video_max_duration_value) * 1000;
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
		String restart_value = sharedPreferences.getString(MainActivity.getVideoRestartPreferenceKey(), "0");
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
    public boolean getVideoFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getVideoFlashPreferenceKey(), false);
    }
    
    @Override
	public String getPreviewSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(MainActivity.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg");
    }
    
    @Override
    public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getRotatePreviewPreferenceKey(), "0");
    }
    
    @Override
    public String getLockOrientationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getLockOrientationPreferenceKey(), "none");
    }

    @Override
    public boolean getPausePreviewPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getPausePreviewPreferenceKey(), false);
    }
    
    @Override
    public boolean getThumbnailAnimationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getThumbnailAnimationPreferenceKey(), true);
    }
    
    @Override
    public boolean getShutterSoundPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getShutterSoundPreferenceKey(), true);
    }

    @Override
    public long getTimerPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(MainActivity.getTimerPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = Integer.parseInt(timer_value) * 1000;
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
    	return sharedPreferences.getString(MainActivity.getBurstModePreferenceKey(), "1");
    }
    
    @Override
    public long getRepeatIntervalPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(MainActivity.getBurstIntervalPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = Integer.parseInt(timer_value) * 1000;
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
    	return sharedPreferences.getBoolean(MainActivity.getLocationPreferenceKey(), false);
    }
    
    @Override
    public boolean getRequireLocationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getRequireLocationPreferenceKey(), false);
    }
    
    @Override
    public boolean getGeodirectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getGPSDirectionPreferenceKey(), false);
    }
    
    @Override
	public boolean getRecordAudioPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(MainActivity.getRecordAudioPreferenceKey(), true);
    }
    
    @Override
    public String getRecordAudioSourcePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getRecordAudioSourcePreferenceKey(), "audio_src_camcorder");
    }

    @Override
    public boolean getAutoStabilisePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean auto_stabilise = sharedPreferences.getBoolean(MainActivity.getAutoStabilisePreferenceKey(), false);
		if( auto_stabilise && main_activity.supportsAutoStabilise() )
			return true;
		return false;
    }
    
    @Override
    public String getStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getStampPreferenceKey(), "preference_stamp_no");
    }
    
    @Override
    public String getTextStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(MainActivity.getTextStampPreferenceKey(), "");
    }
    
    @Override
    public int getTextStampFontSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	int font_size = 12;
		String value = sharedPreferences.getString(MainActivity.getStampFontSizePreferenceKey(), "12");
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
    public long getExposureTimePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getLong(MainActivity.getExposureTimePreferenceKey(), 1000000000l/30);
    }

    @Override
    public boolean isTestAlwaysFocus() {
    	return main_activity.is_test;
    }


	@Override
	public void broadcastFile(File file, boolean is_new_picture, boolean is_new_video) {
		storageUtils.broadcastFile(file, is_new_picture, is_new_video);
	}

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
	}

	@Override
	public void touchEvent(MotionEvent event) {
		main_activity.clearSeekBar();
		main_activity.closePopup();
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}

	@Override
	public void startingVideo() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(MainActivity.getLockVideoPreferenceKey(), false) ) {
			main_activity.lockScreen();
		}
	}

	@Override
	public void stoppingVideo() {
		main_activity.unlockScreen();
	}

	@Override
	public void cameraClosed() {
		main_activity.clearSeekBar();
	}

	@Override
	public void updateThumbnail(Bitmap thumbnail) {
		main_activity.updateThumbnail(thumbnail);
	}

	@Override
	public void layoutUI() {
		main_activity.layoutUI();
	}

    @Override
    public void setFlashPref(String flash_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getFlashPreferenceKey(main_activity.getPreview().getCameraId()), flash_value);
		editor.apply();
    }

    @Override
    public void setFocusPref(String focus_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getFocusPreferenceKey(main_activity.getPreview().getCameraId()), focus_value);
		editor.apply();
    }

    @Override
	public void setVideoPref(boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(MainActivity.getIsVideoPreferenceKey(), is_video);
		editor.apply();
    }

    @Override
    public void setSceneModePref(String scene_mode) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getSceneModePreferenceKey(), scene_mode);
		editor.apply();
    }
    
    @Override
	public void clearSceneModePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getSceneModePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setColorEffectPref(String color_effect) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getColorEffectPreferenceKey(), color_effect);
		editor.apply();
    }
	
    @Override
	public void clearColorEffectPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getColorEffectPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setWhiteBalancePref(String white_balance) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getWhiteBalancePreferenceKey(), white_balance);
		editor.apply();
    }

    @Override
	public void clearWhiteBalancePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getWhiteBalancePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setISOPref(String iso) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getISOPreferenceKey(), iso);
		editor.apply();
    }

    @Override
	public void clearISOPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getISOPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setExposureCompensationPref(int exposure) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getExposurePreferenceKey(), "" + exposure);
		editor.apply();
    }

    @Override
	public void clearExposureCompensationPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getExposurePreferenceKey());
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
		editor.putString(MainActivity.getResolutionPreferenceKey(main_activity.getPreview().getCameraId()), resolution_value);
		editor.apply();
    }
    
    @Override
    public void setVideoQualityPref(String video_quality) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(MainActivity.getVideoQualityPreferenceKey(main_activity.getPreview().getCameraId()), video_quality);
		editor.apply();
    }
    
    @Override
	public void setExposureTimePref(long exposure_time) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(MainActivity.getExposureTimePreferenceKey(), exposure_time);
		editor.apply();
	}

    @Override
	public void clearExposureTimePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(MainActivity.getExposureTimePreferenceKey());
		editor.apply();
    }

}
