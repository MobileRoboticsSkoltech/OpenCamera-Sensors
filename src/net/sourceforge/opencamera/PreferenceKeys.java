package net.sourceforge.opencamera;

/** Stores all of the string keys used for SharedPreferences.
 */
public class PreferenceKeys {
    // must be static, to safely call from other Activities
	
	// arguably the static methods here that don't receive an argument could just be static final strings? Though we may want to change some of them to be cameraId-specific in future

    public static String getFirstTimePreferenceKey() {
        return "done_first_time";
    }

    public static String getUseCamera2PreferenceKey() {
    	return "preference_use_camera2";
    }

    public static String getFlashPreferenceKey(int cameraId) {
    	return "flash_value_" + cameraId;
    }

    public static String getFocusPreferenceKey(int cameraId, boolean is_video) {
    	return "focus_value_" + cameraId + "_" + is_video;
    }

    public static String getResolutionPreferenceKey(int cameraId) {
    	return "camera_resolution_" + cameraId;
    }
    
    public static String getVideoQualityPreferenceKey(int cameraId) {
    	return "video_quality_" + cameraId;
    }
    
    public static String getIsVideoPreferenceKey() {
    	return "is_video";
    }
    
    public static String getExposurePreferenceKey() {
    	return "preference_exposure";
    }

    public static String getColorEffectPreferenceKey() {
    	return "preference_color_effect";
    }

    public static String getSceneModePreferenceKey() {
    	return "preference_scene_mode";
    }

    public static String getWhiteBalancePreferenceKey() {
    	return "preference_white_balance";
    }

    public static String getISOPreferenceKey() {
    	return "preference_iso";
    }
    
    public static String getExposureTimePreferenceKey() {
    	return "preference_exposure_time";
    }
    
    public static String getVolumeKeysPreferenceKey() {
    	return "preference_volume_keys";
    }
    
    public static String getAudioControlPreferenceKey() {
    	return "preference_audio_control";
    }
    
    public static String getAudioNoiseControlSensitivityPreferenceKey() {
    	return "preference_audio_noise_control_sensitivity";
    }
    
    public static String getQualityPreferenceKey() {
    	return "preference_quality";
    }
    
    public static String getAutoStabilisePreferenceKey() {
    	return "preference_auto_stabilise";
    }
    
    public static String getLocationPreferenceKey() {
    	return "preference_location";
    }
    
    public static String getGPSDirectionPreferenceKey() {
    	return "preference_gps_direction";
    }
    
    public static String getRequireLocationPreferenceKey() {
    	return "preference_require_location";
    }
    
    public static String getStampPreferenceKey() {
    	return "preference_stamp";
    }

    public static String getStampDateFormatPreferenceKey() {
    	return "preference_stamp_dateformat";
    }

    public static String getStampTimeFormatPreferenceKey() {
    	return "preference_stamp_timeformat";
    }

    public static String getStampGPSFormatPreferenceKey() {
    	return "preference_stamp_gpsformat";
    }

    public static String getTextStampPreferenceKey() {
    	return "preference_textstamp";
    }

    public static String getStampFontSizePreferenceKey() {
    	return "preference_stamp_fontsize";
    }

    public static String getStampFontColorPreferenceKey() {
    	return "preference_stamp_font_color";
    }

    public static String getStampStyleKey() {
    	return "preference_stamp_style";
    }

    public static String getUIPlacementPreferenceKey() {
    	return "preference_ui_placement";
    }
    
    public static String getTouchCapturePreferenceKey() {
    	return "preference_touch_capture";
    }

    public static String getPausePreviewPreferenceKey() {
    	return "preference_pause_preview";
    }

    public static String getShowToastsPreferenceKey() {
    	return "preference_show_toasts";
    }

    public static String getThumbnailAnimationPreferenceKey() {
    	return "preference_thumbnail_animation";
    }

    public static String getShowWhenLockedPreferenceKey() {
    	return "preference_show_when_locked";
    }

    public static String getStartupFocusPreferenceKey() {
    	return "preference_startup_focus";
    }

    public static String getKeepDisplayOnPreferenceKey() {
    	return "preference_keep_display_on";
    }

    public static String getMaxBrightnessPreferenceKey() {
    	return "preference_max_brightness";
    }
    
    public static String getUsingSAFPreferenceKey() {
    	return "preference_using_saf";
    }

    public static String getSaveLocationPreferenceKey() {
    	return "preference_save_location";
    }

    public static String getSaveLocationSAFPreferenceKey() {
    	return "preference_save_location_saf";
    }

    public static String getSavePhotoPrefixPreferenceKey() {
    	return "preference_save_photo_prefix";
    }

    public static String getSaveVideoPrefixPreferenceKey() {
    	return "preference_save_video_prefix";
    }

    public static String getShowZoomControlsPreferenceKey() {
    	return "preference_show_zoom_controls";
    }

    public static String getShowZoomSliderControlsPreferenceKey() {
    	return "preference_show_zoom_slider_controls";
    }
    
    public static String getShowZoomPreferenceKey() {
    	return "preference_show_zoom";
    }
    
    public static String getShowISOPreferenceKey() {
    	return "preference_show_iso";
    }

    public static String getShowAnglePreferenceKey() {
    	return "preference_show_angle";
    }
    
    public static String getShowAngleLinePreferenceKey() {
    	return "preference_show_angle_line";
    }
    
    public static String getShowAngleHighlightColorPreferenceKey() {
    	return "preference_angle_highlight_color";
    }

    public static String getShowGeoDirectionPreferenceKey() {
    	return "preference_show_geo_direction";
    }
    
    public static String getShowFreeMemoryPreferenceKey() {
    	return "preference_free_memory";
    }
    
    public static String getShowTimePreferenceKey() {
    	return "preference_show_time";
    }
    
    public static String getShowBatteryPreferenceKey() {
    	return "preference_show_battery";
    }
    
    public static String getShowGridPreferenceKey() {
    	return "preference_grid";
    }
    
    public static String getShowCropGuidePreferenceKey() {
    	return "preference_crop_guide";
    }
    
    public static String getFaceDetectionPreferenceKey() {
    	return "preference_face_detection";
    }

    public static String getVideoStabilizationPreferenceKey() {
    	return "preference_video_stabilization";
    }
    
    public static String getForceVideo4KPreferenceKey() {
    	return "preference_force_video_4k";
    }
    
    public static String getVideoBitratePreferenceKey() {
    	return "preference_video_bitrate";
    }

    public static String getVideoFPSPreferenceKey() {
    	return "preference_video_fps";
    }
    
    public static String getVideoMaxDurationPreferenceKey() {
    	return "preference_video_max_duration";
    }
    
    public static String getVideoRestartPreferenceKey() {
    	return "preference_video_restart";
    }
    
    public static String getVideoMaxFileSizePreferenceKey() {
    	return "preference_video_max_filesize";
    }
    
    public static String getVideoRestartMaxFileSizePreferenceKey() {
    	return "preference_video_restart_max_filesize";
    }

    public static String getVideoFlashPreferenceKey() {
    	return "preference_video_flash";
    }

    public static String getLockVideoPreferenceKey() {
    	return "preference_lock_video";
    }
    
    public static String getRecordAudioPreferenceKey() {
    	return "preference_record_audio";
    }

    public static String getRecordAudioChannelsPreferenceKey() {
    	return "preference_record_audio_channels";
    }

    public static String getRecordAudioSourcePreferenceKey() {
    	return "preference_record_audio_src";
    }

    public static String getPreviewSizePreferenceKey() {
    	return "preference_preview_size";
    }

    public static String getRotatePreviewPreferenceKey() {
    	return "preference_rotate_preview";
    }

    public static String getLockOrientationPreferenceKey() {
    	return "preference_lock_orientation";
    }

    public static String getTimerPreferenceKey() {
    	return "preference_timer";
    }
    
    public static String getTimerBeepPreferenceKey() {
    	return "preference_timer_beep";
    }
    
    public static String getTimerSpeakPreferenceKey() {
    	return "preference_timer_speak";
    }
    
    public static String getBurstModePreferenceKey() {
    	return "preference_burst_mode";
    }
    
    public static String getBurstIntervalPreferenceKey() {
    	return "preference_burst_interval";
    }
    
    public static String getShutterSoundPreferenceKey() {
    	return "preference_shutter_sound";
    }
    
    public static String getImmersiveModePreferenceKey() {
    	return "preference_immersive_mode";
    }
}
