package net.sourceforge.opencamera.ui;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

/** This defines the UI for the "popup" button, that provides quick access to a
 *  range of options.
 */
public class PopupView extends LinearLayout {
    private static final String TAG = "PopupView";
    public static final float ALPHA_BUTTON_SELECTED = 1.0f;
    public static final float ALPHA_BUTTON = 0.6f; // 0.4f tends to be hard to see in bright light

    private static final float button_text_size_dip = 12.0f;
    private static final float title_text_size_dip = 17.0f;
    private static final float standard_text_size_dip = 16.0f;
    private static final float arrow_text_size_dip = 16.0f;
    private static final float arrow_button_w_dp = 60.0f;
    private static final float arrow_button_h_dp = 48.0f; // should be at least 48.0 (Google Play's prelaunch warnings)
    private final int arrow_button_w;
    private final int arrow_button_h;

    private int total_width_dp;

    private int picture_size_index = -1;
    private int nr_mode_index = -1;
    private int burst_n_images_index = -1;
    private int video_size_index = -1;
    private int video_capture_rate_index = -1;
    private int timer_index = -1;
    private int repeat_mode_index = -1;
    private int grid_index = -1;

    public PopupView(Context context) {
        super(context);
        if( MyDebug.LOG )
            Log.d(TAG, "new PopupView: " + this);

        final long debug_time = System.nanoTime();
        if( MyDebug.LOG )
            Log.d(TAG, "PopupView time 1: " + (System.nanoTime() - debug_time));
        this.setOrientation(LinearLayout.VERTICAL);

        final float scale = getResources().getDisplayMetrics().density;

        arrow_button_w = (int) (arrow_button_w_dp * scale + 0.5f); // convert dps to pixels
        arrow_button_h = (int) (arrow_button_h_dp * scale + 0.5f); // convert dps to pixels

        final MainActivity main_activity = (MainActivity)this.getContext();

        boolean small_screen = false;
        total_width_dp = 280;
        int max_width_dp = main_activity.getMainUI().getMaxHeightDp(false);
        if( total_width_dp > max_width_dp ) {
            total_width_dp = max_width_dp;
            small_screen = true;
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "max_width_dp: " + max_width_dp);
            Log.d(TAG, "total_width_dp: " + total_width_dp);
            Log.d(TAG, "small_screen: " + small_screen);
        }

		/*{
			int total_width = (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
			if( MyDebug.LOG )
				Log.d(TAG, "total_width: " + total_width);
			ViewGroup.LayoutParams params = new LayoutParams(
					total_width,
					LayoutParams.WRAP_CONTENT);
			this.setLayoutParams(params);
		}*/

        final Preview preview = main_activity.getPreview();
        if( MyDebug.LOG )
            Log.d(TAG, "PopupView time 2: " + (System.nanoTime() - debug_time));
        if( !main_activity.getMainUI().showCycleFlashIcon() )
        {
            List<String> supported_flash_values = preview.getSupportedFlashValues();
            if( preview.isVideo() && supported_flash_values != null ) {
                // filter flash modes we don't want to show
                List<String> filter = new ArrayList<>();
                for(String flash_value : supported_flash_values) {
                    if( Preview.isFlashSupportedForVideo(flash_value) )
                        filter.add(flash_value);
                }
                supported_flash_values = filter;
            }
            if( supported_flash_values != null && supported_flash_values.size() > 1 ) { // no point showing flash options if only one available!
                addButtonOptionsToPopup(supported_flash_values, R.array.flash_icons, R.array.flash_values, getResources().getString(R.string.flash_mode), preview.getCurrentFlashValue(), 0, "TEST_FLASH", new ButtonOptionsPopupListener() {
                    @Override
                    public void onClick(String option) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "clicked flash: " + option);
                        preview.updateFlash(option);
                        main_activity.getMainUI().setPopupIcon();
                        main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
                    }
                });
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "PopupView time 3: " + (System.nanoTime() - debug_time));

        //if( preview.isVideo() && preview.isTakingPhoto() ) {
        if( preview.isVideo() && preview.isVideoRecording() ) {
            // don't add any more options
        }
        else {
            // make a copy of getSupportedFocusValues() so we can modify it
            List<String> supported_focus_values = preview.getSupportedFocusValues();
            MyApplicationInterface.PhotoMode photo_mode = main_activity.getApplicationInterface().getPhotoMode();
            if( !preview.isVideo() && photo_mode == MyApplicationInterface.PhotoMode.FocusBracketing ) {
                // don't show focus modes in focus bracketing mode (as we'll always run in manual focus mode)
                supported_focus_values = null;
            }
            if( supported_focus_values != null ) {
                supported_focus_values = new ArrayList<>(supported_focus_values);
                // only show appropriate continuous focus mode
                if( preview.isVideo() ) {
                    supported_focus_values.remove("focus_mode_continuous_picture");
                }
                else {
                    supported_focus_values.remove("focus_mode_continuous_video");
                }
            }
            addButtonOptionsToPopup(supported_focus_values, R.array.focus_mode_icons, R.array.focus_mode_values, getResources().getString(R.string.focus_mode), preview.getCurrentFocusValue(), 0, "TEST_FOCUS", new ButtonOptionsPopupListener() {
                @Override
                public void onClick(String option) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked focus: " + option);
                    preview.updateFocus(option, false, true);
                    main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
                }
            });
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 4: " + (System.nanoTime() - debug_time));

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);

            //final boolean use_expanded_menu = true;
            final boolean use_expanded_menu = false;
            final List<String> photo_modes = new ArrayList<>();
            final List<MyApplicationInterface.PhotoMode> photo_mode_values = new ArrayList<>();
            photo_modes.add( getResources().getString(use_expanded_menu ? R.string.photo_mode_standard_full : R.string.photo_mode_standard) );
            photo_mode_values.add( MyApplicationInterface.PhotoMode.Standard );
            if( main_activity.supportsNoiseReduction() ) {
                photo_modes.add(getResources().getString(use_expanded_menu ? R.string.photo_mode_noise_reduction_full : R.string.photo_mode_noise_reduction));
                photo_mode_values.add(MyApplicationInterface.PhotoMode.NoiseReduction);
            }
            if( main_activity.supportsDRO() ) {
                photo_modes.add( getResources().getString(R.string.photo_mode_dro) );
                photo_mode_values.add( MyApplicationInterface.PhotoMode.DRO );
            }
            if( main_activity.supportsHDR() ) {
                photo_modes.add( getResources().getString(R.string.photo_mode_hdr) );
                photo_mode_values.add( MyApplicationInterface.PhotoMode.HDR );
            }
            if( main_activity.supportsPanorama() ) {
                photo_modes.add(getResources().getString(use_expanded_menu ? R.string.photo_mode_panorama_full : R.string.photo_mode_panorama));
                photo_mode_values.add(MyApplicationInterface.PhotoMode.Panorama);
            }
            if( main_activity.supportsFastBurst() ) {
                photo_modes.add(getResources().getString(use_expanded_menu ? R.string.photo_mode_fast_burst_full : R.string.photo_mode_fast_burst));
                photo_mode_values.add(MyApplicationInterface.PhotoMode.FastBurst);
            }
            if( main_activity.supportsExpoBracketing() ) {
                photo_modes.add( getResources().getString(use_expanded_menu ? R.string.photo_mode_expo_bracketing_full : R.string.photo_mode_expo_bracketing) );
                photo_mode_values.add( MyApplicationInterface.PhotoMode.ExpoBracketing );
            }
            if( main_activity.supportsFocusBracketing() ) {
                photo_modes.add( getResources().getString(use_expanded_menu ? R.string.photo_mode_focus_bracketing_full : R.string.photo_mode_focus_bracketing) );
                photo_mode_values.add( MyApplicationInterface.PhotoMode.FocusBracketing );
            }
            if( preview.isVideo() ) {
                // only show photo modes when in photo mode, not video mode!
                // (photo modes not supported for photo snapshot whilst recording video)
            }
            else if( photo_modes.size() > 1 ) {
                String current_mode = null;
                for(int i=0;i<photo_modes.size() && current_mode==null;i++) {
                    if( photo_mode_values.get(i) == photo_mode ) {
                        current_mode = photo_modes.get(i);
                    }
                }
                if( current_mode == null ) {
                    // applicationinterface should only report we're in a mode if it's supported, but just in case...
                    if( MyDebug.LOG )
                        Log.e(TAG, "can't find current mode for mode: " + photo_mode);
                    current_mode = ""; // this will mean no photo mode is highlighted in the UI
                }

                if( use_expanded_menu ) {
                    addRadioOptionsToPopup(sharedPreferences, photo_modes, photo_modes, getResources().getString(R.string.photo_mode), null, null, current_mode, "TEST_PHOTO_MODE", new RadioOptionsListener() {
                        @Override
                        public void onClick(String selected_value) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "clicked photo mode: " + selected_value);

                            changePhotoMode(photo_modes, photo_mode_values, selected_value);
                        }
                    });
                }
                else {
                    addTitleToPopup(getResources().getString(R.string.photo_mode));
                    if( MyDebug.LOG )
                        Log.d(TAG, "PopupView time 6: " + (System.nanoTime() - debug_time));

                    addButtonOptionsToPopup(photo_modes, -1, -1, "", current_mode, 4, "TEST_PHOTO_MODE", new ButtonOptionsPopupListener() {
                        @Override
                        public void onClick(String option) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "clicked photo mode: " + option);

                            changePhotoMode(photo_modes, photo_mode_values, option);
                        }
                    });
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 7: " + (System.nanoTime() - debug_time));

            if( !preview.isVideo() && photo_mode == MyApplicationInterface.PhotoMode.NoiseReduction ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "add noise reduction options");

                final String [] nr_mode_values = getResources().getStringArray(R.array.preference_nr_mode_values);
                String [] nr_mode_entries = getResources().getStringArray(R.array.preference_nr_mode_entries);

                if( nr_mode_values.length != nr_mode_entries.length ) {
                    Log.e(TAG, "preference_nr_mode_values and preference_nr_mode_entries are different lengths");
                    throw new RuntimeException();
                }

                //String nr_mode_value = sharedPreferences.getString(PreferenceKeys.NRModePreferenceKey, "preference_nr_mode_normal");
                String nr_mode_value = main_activity.getApplicationInterface().getNRMode();
                nr_mode_index = Arrays.asList(nr_mode_values).indexOf(nr_mode_value);
                if( nr_mode_index == -1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find nr_mode_value " + nr_mode_value + " in nr_mode_values!");
                    nr_mode_index = 0;
                }
                addArrayOptionsToPopup(Arrays.asList(nr_mode_entries), getResources().getString(R.string.preference_nr_mode), true, true, nr_mode_index, false, "NR_MODE", new ArrayOptionsPopupListener() {
                    private void update() {
                        if( nr_mode_index == -1 )
                            return;
                        String new_nr_mode_value = nr_mode_values[nr_mode_index];
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        //editor.putString(PreferenceKeys.NRModePreferenceKey, new_nr_mode_value);
                        main_activity.getApplicationInterface().setNRMode(new_nr_mode_value);
                        editor.apply();
                        if( preview.getCameraController() != null ) {
                            preview.setupBurstMode();
                        }
                    }
                    @Override
                    public int onClickPrev() {
                        if( nr_mode_index != -1 && nr_mode_index > 0 ) {
                            nr_mode_index--;
                            update();
                            return nr_mode_index;
                        }
                        return -1;
                    }
                    @Override
                    public int onClickNext() {
                        if( nr_mode_index != -1 && nr_mode_index < nr_mode_values.length-1 ) {
                            nr_mode_index++;
                            update();
                            return nr_mode_index;
                        }
                        return -1;
                    }
                });
            }

            if( main_activity.supportsAutoStabilise() && !main_activity.getMainUI().showAutoLevelIcon() ) {
                // don't show auto-stabilise checkbox on popup if there's an on-screen icon
                CheckBox checkBox = new CheckBox(main_activity);
                checkBox.setText(getResources().getString(R.string.preference_auto_stabilise));
                checkBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, standard_text_size_dip);
                checkBox.setTextColor(Color.WHITE);
                {
                    // align the checkbox a bit better
                    LayoutParams params = new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT
                    );
                    final int left_padding = (int) (10 * scale + 0.5f); // convert dps to pixels
                    params.setMargins(left_padding, 0, 0, 0);
                    checkBox.setLayoutParams(params);
                }

                boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false);
                if( auto_stabilise )
                    checkBox.setChecked(auto_stabilise);
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView,
                                                 boolean isChecked) {
                        main_activity.clickedAutoLevel();
                    }
                });

                this.addView(checkBox);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 8: " + (System.nanoTime() - debug_time));

            if( !preview.isVideo() && photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
                // Only show photo resolutions in photo mode - even if photo snapshots whilst recording video is supported, the
                // resolutions for that won't match what the user has requested for photo mode resolutions.
                // And Panorama mode chooses its own resolution.
                final List<CameraController.Size> picture_sizes = new ArrayList<>(preview.getSupportedPictureSizes(true));
                // take a copy so that we can reorder
                // picture_sizes is sorted high to low, but we want to order low to high
                Collections.reverse(picture_sizes);
                picture_size_index = -1;
                CameraController.Size current_picture_size = preview.getCurrentPictureSize();
                final List<String> picture_size_strings = new ArrayList<>();
                for(int i=0;i<picture_sizes.size();i++) {
                    CameraController.Size picture_size = picture_sizes.get(i);
                    // don't display MP here, as call to Preview.getMPString() here would contribute to poor performance!
                    String size_string = picture_size.width + " x " + picture_size.height;
                    picture_size_strings.add(size_string);
                    if( picture_size.equals( current_picture_size ) ) {
                        picture_size_index = i;
                    }
                }
                if( picture_size_index == -1 ) {
                    Log.e(TAG, "couldn't find index of current picture size");
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "picture_size_index: " + picture_size_index);
                }
                addArrayOptionsToPopup(picture_size_strings, getResources().getString(R.string.preference_resolution), false, false, picture_size_index, false, "PHOTO_RESOLUTIONS", new ArrayOptionsPopupListener() {
                    final Handler handler = new Handler();
                    final Runnable update_runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (MyDebug.LOG)
                                Log.d(TAG, "update settings due to resolution change");
                            main_activity.updateForSettings("", true); // keep the popupview open
                        }
                    };

                    private void update() {
                        if( picture_size_index == -1 )
                            return;
                        CameraController.Size new_size = picture_sizes.get(picture_size_index);
                        String resolution_string = new_size.width + " " + new_size.height;
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.getResolutionPreferenceKey(preview.getCameraId()), resolution_string);
                        editor.apply();

                        // make it easier to scroll through the list of resolutions without a pause each time
                        handler.removeCallbacks(update_runnable);
                        handler.postDelayed(update_runnable, 400);
                    }

                    @Override
                    public int onClickPrev() {
                        if( picture_size_index != -1 && picture_size_index > 0 ) {
                            picture_size_index--;
                            update();
                            return picture_size_index;
                        }
                        return -1;
                    }

                    @Override
                    public int onClickNext() {
                        if( picture_size_index != -1 && picture_size_index < picture_sizes.size() - 1 ) {
                            picture_size_index++;
                            update();
                            return picture_size_index;
                        }
                        return -1;
                    }
                });
            }
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 9: " + (System.nanoTime() - debug_time));

            if( preview.isVideo() ) {
                // only show video resolutions in video mode
                //final List<String> video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality();
                //video_size_index = preview.getVideoQualityHander().getCurrentVideoQualityIndex();
                List<String> video_sizes = preview.getSupportedVideoQuality(main_activity.getApplicationInterface().getVideoFPSPref());
                if( video_sizes.size() == 0 ) {
                    Log.e(TAG, "can't find any supported video sizes for current fps!");
                    // fall back to unfiltered list
                    video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality();
                }
                // take a copy so that we can reorder
                video_sizes = new ArrayList<>(video_sizes);
                // video_sizes is sorted high to low, but we want to order low to high
                Collections.reverse(video_sizes);

                final List<String> video_sizes_f = video_sizes;
                video_size_index = video_sizes.size()-1; // default to largest (just in case current size not found??)
                for(int i=0;i<video_sizes.size();i++) {
                    String video_size = video_sizes.get(i);
                    if( video_size.equals(preview.getVideoQualityHander().getCurrentVideoQuality()) ) {
                        video_size_index = i;
                        break;
                    }
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "video_size_index:" + video_size_index);
                final List<String> video_size_strings = new ArrayList<>();
                for(String video_size : video_sizes) {
                    String quality_string = preview.getCamcorderProfileDescriptionShort(video_size);
                    video_size_strings.add(quality_string);
                }
                addArrayOptionsToPopup(video_size_strings, getResources().getString(R.string.video_quality), false, false, video_size_index, false, "VIDEO_RESOLUTIONS", new ArrayOptionsPopupListener() {
                    final Handler handler = new Handler();
                    final Runnable update_runnable = new Runnable() {
                        @Override
                        public void run() {
                            if( MyDebug.LOG )
                                Log.d(TAG, "update settings due to video resolution change");
                            main_activity.updateForSettings("", true); // keep the popupview open
                        }
                    };

                    private void update() {
                        if( video_size_index == -1 )
                            return;
                        String quality = video_sizes_f.get(video_size_index);
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(preview.getCameraId(), main_activity.getApplicationInterface().fpsIsHighSpeed()), quality);
                        editor.apply();

                        // make it easier to scroll through the list of resolutions without a pause each time
                        handler.removeCallbacks(update_runnable);
                        handler.postDelayed(update_runnable, 400);
                    }

                    @Override
                    public int onClickPrev() {
                        if( video_size_index != -1 && video_size_index > 0 ) {
                            video_size_index--;
                            update();
                            return video_size_index;
                        }
                        return -1;
                    }

                    @Override
                    public int onClickNext() {
                        if( video_size_index != -1 && video_size_index < video_sizes_f.size() - 1 ) {
                            video_size_index++;
                            update();
                            return video_size_index;
                        }
                        return -1;
                    }
                });
            }
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 10: " + (System.nanoTime() - debug_time));

            if( !preview.isVideo() && photo_mode == MyApplicationInterface.PhotoMode.FastBurst ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "add fast burst options");

                final String [] all_burst_mode_values = getResources().getStringArray(R.array.preference_fast_burst_n_images_values);
                String [] all_burst_mode_entries = getResources().getStringArray(R.array.preference_fast_burst_n_images_entries);

                //String [] burst_mode_values = new String[all_burst_mode_values.length];
                //String [] burst_mode_entries = new String[all_burst_mode_entries.length];
                if( all_burst_mode_values.length != all_burst_mode_entries.length ) {
                    Log.e(TAG, "preference_fast_burst_n_images_values and preference_fast_burst_n_images_entries are different lengths");
                    throw new RuntimeException();
                }

                int max_burst_images = main_activity.getApplicationInterface().getImageSaver().getQueueSize()+1;
                max_burst_images = Math.max(2, max_burst_images); // make sure we at least allow the minimum of 2 burst images!
                if( MyDebug.LOG )
                    Log.d(TAG, "max_burst_images: " + max_burst_images);

                // filter number of burst images - don't allow more than max_burst_images
                List<String> burst_mode_values_l = new ArrayList<>();
                List<String> burst_mode_entries_l = new ArrayList<>();
                for(int i=0;i<all_burst_mode_values.length;i++) {
                    int n_images;
                    try {
                        n_images = Integer.parseInt(all_burst_mode_values[i]);
                    }
                    catch(NumberFormatException e) {
                        Log.e(TAG, "failed to parse " + i + "th preference_fast_burst_n_images_values value: " + all_burst_mode_values[i]);
                        e.printStackTrace();
                        continue;
                    }
                    if( n_images > max_burst_images ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "n_images " + n_images + " is more than max_burst_images: " + max_burst_images);
                        continue;
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "n_images " + n_images);
                    burst_mode_values_l.add( all_burst_mode_values[i] );
                    burst_mode_entries_l.add( all_burst_mode_entries[i] );
                }
                final String [] burst_mode_values = burst_mode_values_l.toArray(new String[0]);
                final String [] burst_mode_entries = burst_mode_entries_l.toArray(new String[0]);

                String burst_mode_value = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
                burst_n_images_index = Arrays.asList(burst_mode_values).indexOf(burst_mode_value);
                if( burst_n_images_index == -1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!");
                    burst_n_images_index = 0;
                }
                addArrayOptionsToPopup(Arrays.asList(burst_mode_entries), getResources().getString(R.string.preference_fast_burst_n_images), true, false, burst_n_images_index, false, "FAST_BURST_N_IMAGES", new ArrayOptionsPopupListener() {
                    private void update() {
                        if( burst_n_images_index == -1 )
                            return;
                        String new_burst_mode_value = burst_mode_values[burst_n_images_index];
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.FastBurstNImagesPreferenceKey, new_burst_mode_value);
                        editor.apply();
                        if( preview.getCameraController() != null ) {
                            preview.getCameraController().setBurstNImages(main_activity.getApplicationInterface().getBurstNImages());
                        }
                    }
                    @Override
                    public int onClickPrev() {
                        if( burst_n_images_index != -1 && burst_n_images_index > 0 ) {
                            burst_n_images_index--;
                            update();
                            return burst_n_images_index;
                        }
                        return -1;
                    }
                    @Override
                    public int onClickNext() {
                        if( burst_n_images_index != -1 && burst_n_images_index < burst_mode_values.length-1 ) {
                            burst_n_images_index++;
                            update();
                            return burst_n_images_index;
                        }
                        return -1;
                    }
                });
            }
            else if( !preview.isVideo() && photo_mode == MyApplicationInterface.PhotoMode.FocusBracketing ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "add focus bracketing options");

                final String [] burst_mode_values = getResources().getStringArray(R.array.preference_focus_bracketing_n_images_values);
                String [] burst_mode_entries = getResources().getStringArray(R.array.preference_focus_bracketing_n_images_entries);

                if( burst_mode_values.length != burst_mode_entries.length ) {
                    Log.e(TAG, "preference_focus_bracketing_n_images_values and preference_focus_bracketing_n_images_entries are different lengths");
                    throw new RuntimeException();
                }

                String burst_mode_value = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
                burst_n_images_index = Arrays.asList(burst_mode_values).indexOf(burst_mode_value);
                if( burst_n_images_index == -1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!");
                    burst_n_images_index = 0;
                }
                addArrayOptionsToPopup(Arrays.asList(burst_mode_entries), getResources().getString(R.string.preference_focus_bracketing_n_images), true, false, burst_n_images_index, false, "FOCUS_BRACKETING_N_IMAGES", new ArrayOptionsPopupListener() {
                    private void update() {
                        if( burst_n_images_index == -1 )
                            return;
                        String new_burst_mode_value = burst_mode_values[burst_n_images_index];
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, new_burst_mode_value);
                        editor.apply();
                        if( preview.getCameraController() != null ) {
                            preview.getCameraController().setFocusBracketingNImages(main_activity.getApplicationInterface().getFocusBracketingNImagesPref());
                        }
                    }
                    @Override
                    public int onClickPrev() {
                        if( burst_n_images_index != -1 && burst_n_images_index > 0 ) {
                            burst_n_images_index--;
                            update();
                            return burst_n_images_index;
                        }
                        return -1;
                    }
                    @Override
                    public int onClickNext() {
                        if( burst_n_images_index != -1 && burst_n_images_index < burst_mode_values.length-1 ) {
                            burst_n_images_index++;
                            update();
                            return burst_n_images_index;
                        }
                        return -1;
                    }
                });

                //CheckBox checkBox = new CheckBox(main_activity);
                Switch checkBox = new Switch(main_activity);
                checkBox.setText(getResources().getString(R.string.focus_bracketing_add_infinity));
                {
                    // align the checkbox a bit better
                    checkBox.setGravity(Gravity.RIGHT);
                    LayoutParams params = new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT
                    );
                    final int right_padding = (int) (20 * scale + 0.5f); // convert dps to pixels
                    params.setMargins(0, 0, right_padding, 0);
                    checkBox.setLayoutParams(params);
                }

                boolean add_infinity = sharedPreferences.getBoolean(PreferenceKeys.FocusBracketingAddInfinityPreferenceKey, false);
                if( add_infinity )
                    checkBox.setChecked(add_infinity);
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView,
                                                 boolean isChecked) {
                        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(PreferenceKeys.FocusBracketingAddInfinityPreferenceKey, isChecked);
                        editor.apply();
                        if( preview.getCameraController() != null ) {
                            preview.getCameraController().setFocusBracketingAddInfinity(main_activity.getApplicationInterface().getFocusBracketingAddInfinityPref());
                        }
                    }
                });

                this.addView(checkBox);
            }

            if( preview.isVideo() ) {
                final List<Float> capture_rate_values = main_activity.getApplicationInterface().getSupportedVideoCaptureRates();
                if( capture_rate_values.size() > 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "add slow motion / timelapse video options");
                    float capture_rate_value = sharedPreferences.getFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(preview.getCameraId()), 1.0f);
                    final List<String> capture_rate_str = new ArrayList<>();
                    int capture_rate_std_index = -1;
                    for(int i=0;i<capture_rate_values.size();i++) {
                        float this_capture_rate = capture_rate_values.get(i);
                        if( Math.abs(1.0f - this_capture_rate) < 1.0e-5 ) {
                            capture_rate_str.add(getResources().getString(R.string.preference_video_capture_rate_normal));
                            capture_rate_std_index = i;
                        }
                        else {
                            capture_rate_str.add("" + this_capture_rate + "x");
                        }
                        if( Math.abs(capture_rate_value - this_capture_rate) < 1.0e-5 ) {
                            video_capture_rate_index = i;
                        }
                    }
                    if( video_capture_rate_index == -1 ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "can't find video_capture_rate_index");
                        // default to no slow motion or timelapse
                        video_capture_rate_index = capture_rate_std_index;
                        if( video_capture_rate_index == -1 ) {
                            Log.e(TAG, "can't find capture_rate_std_index");
                            video_capture_rate_index = 0;
                        }
                    }
                    addArrayOptionsToPopup(capture_rate_str, getResources().getString(R.string.preference_video_capture_rate), true, false, video_capture_rate_index, false, "VIDEOCAPTURERATE", new ArrayOptionsPopupListener() {
                        private int old_video_capture_rate_index = video_capture_rate_index;

                        final Handler handler = new Handler();
                        final Runnable update_runnable = new Runnable() {
                            @Override
                            public void run() {
                                if (MyDebug.LOG)
                                    Log.d(TAG, "update settings due to resolution change");
                                main_activity.updateForSettings("", true); // keep the popupview open
                            }
                        };

                        private void update() {
                            if( video_capture_rate_index == -1 )
                                return;
                            float new_capture_rate_value = capture_rate_values.get(video_capture_rate_index);
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(preview.getCameraId()), new_capture_rate_value);
                            editor.apply();

                            float old_capture_rate_value = capture_rate_values.get(old_video_capture_rate_index);
                            boolean old_slow_motion = (old_capture_rate_value < 1.0f-1.0e-5f);
                            boolean new_slow_motion = (new_capture_rate_value < 1.0f-1.0e-5f);
                            // if changing to/from a slow motion mode, this will in general switch on/off high fps frame
                            // rates, which changes the available video resolutions, so we need to re-open the popup
                            boolean keep_popup = (old_slow_motion==new_slow_motion);
                            // only display a toast if the popup is closing
                            //String toast_message = getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_str.get(video_capture_rate_index);
                            String toast_message = "";
                            if( !keep_popup ) {
                                if( new_slow_motion )
                                    toast_message = getResources().getString(R.string.slow_motion_enabled) + "\n" + getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_str.get(video_capture_rate_index);
                                else
                                    toast_message = getResources().getString(R.string.slow_motion_disabled);
                            }
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "update settings due to capture rate change");
                                Log.d(TAG, "old_capture_rate_value: " + old_capture_rate_value);
                                Log.d(TAG, "new_capture_rate_value: " + new_capture_rate_value);
                                Log.d(TAG, "old_slow_motion: " + old_slow_motion);
                                Log.d(TAG, "new_slow_motion: " + new_slow_motion);
                                Log.d(TAG, "keep_popup: " + keep_popup);
                                Log.d(TAG, "toast_message: " + toast_message);
                            }
                            old_video_capture_rate_index = video_capture_rate_index;

                            if( keep_popup ) {
                                // make it easier to scroll through the list of resolutions without a pause each time
                                handler.removeCallbacks(update_runnable);
                                handler.postDelayed(update_runnable, 400);
                            }
                            else {
                                main_activity.updateForSettings(toast_message, keep_popup);
                            }
                        }
                        @Override
                        public int onClickPrev() {
                            if( video_capture_rate_index != -1 && video_capture_rate_index > 0 ) {
                                video_capture_rate_index--;
                                update();
                                return video_capture_rate_index;
                            }
                            return -1;
                        }
                        @Override
                        public int onClickNext() {
                            if( video_capture_rate_index != -1 && video_capture_rate_index < capture_rate_values.size()-1 ) {
                                video_capture_rate_index++;
                                update();
                                return video_capture_rate_index;
                            }
                            return -1;
                        }
                    });
                }
            }

            if( photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
                // timer not supported with panorama

                final String [] timer_values = getResources().getStringArray(R.array.preference_timer_values);
                String [] timer_entries = getResources().getStringArray(R.array.preference_timer_entries);
                String timer_value = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
                timer_index = Arrays.asList(timer_values).indexOf(timer_value);
                if( timer_index == -1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find timer_value " + timer_value + " in timer_values!");
                    timer_index = 0;
                }
                // title_in_options should be false for small screens: e.g., problems with pt-rBR or pt-rPT on 4.5" screens or less, see https://sourceforge.net/p/opencamera/discussion/photography/thread/3aa940c636/
                addArrayOptionsToPopup(Arrays.asList(timer_entries), getResources().getString(R.string.preference_timer), !small_screen, false, timer_index, false, "TIMER", new ArrayOptionsPopupListener() {
                    private void update() {
                        if( timer_index == -1 )
                            return;
                        String new_timer_value = timer_values[timer_index];
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.getTimerPreferenceKey(), new_timer_value);
                        editor.apply();
                    }
                    @Override
                    public int onClickPrev() {
                        if( timer_index != -1 && timer_index > 0 ) {
                            timer_index--;
                            update();
                            return timer_index;
                        }
                        return -1;
                    }
                    @Override
                    public int onClickNext() {
                        if( timer_index != -1 && timer_index < timer_values.length-1 ) {
                            timer_index++;
                            update();
                            return timer_index;
                        }
                        return -1;
                    }
                });
            }
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 11: " + (System.nanoTime() - debug_time));

            if( photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
                // auto-repeat not supported with panorama

                final String [] repeat_mode_values = getResources().getStringArray(R.array.preference_burst_mode_values);
                String [] repeat_mode_entries = getResources().getStringArray(R.array.preference_burst_mode_entries);
                String repeat_mode_value = sharedPreferences.getString(PreferenceKeys.getRepeatModePreferenceKey(), "1");
                repeat_mode_index = Arrays.asList(repeat_mode_values).indexOf(repeat_mode_value);
                if( repeat_mode_index == -1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find repeat_mode_value " + repeat_mode_value + " in repeat_mode_values!");
                    repeat_mode_index = 0;
                }
                // title_in_options should be false for small screens: e.g., problems with pt-rBR or pt-rPT on 4.5" screens or less, see https://sourceforge.net/p/opencamera/discussion/photography/thread/3aa940c636/
                // set title_in_options_first_only to true, as displaying "Repeat: Unlimited" can be too long in some languages, e.g., Vietnamese (vi)
                addArrayOptionsToPopup(Arrays.asList(repeat_mode_entries), getResources().getString(R.string.preference_burst_mode), !small_screen, true, repeat_mode_index, false, "REPEAT_MODE", new ArrayOptionsPopupListener() {
                    private void update() {
                        if( repeat_mode_index == -1 )
                            return;
                        String new_repeat_mode_value = repeat_mode_values[repeat_mode_index];
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.getRepeatModePreferenceKey(), new_repeat_mode_value);
                        editor.apply();
                    }
                    @Override
                    public int onClickPrev() {
                        if( repeat_mode_index != -1 && repeat_mode_index > 0 ) {
                            repeat_mode_index--;
                            update();
                            return repeat_mode_index;
                        }
                        return -1;
                    }
                    @Override
                    public int onClickNext() {
                        if( repeat_mode_index != -1 && repeat_mode_index < repeat_mode_values.length-1 ) {
                            repeat_mode_index++;
                            update();
                            return repeat_mode_index;
                        }
                        return -1;
                    }
                });
                if( MyDebug.LOG )
                    Log.d(TAG, "PopupView time 12: " + (System.nanoTime() - debug_time));
            }

            final String [] grid_values = getResources().getStringArray(R.array.preference_grid_values);
            String [] grid_entries = getResources().getStringArray(R.array.preference_grid_entries);
            String grid_value = sharedPreferences.getString(PreferenceKeys.ShowGridPreferenceKey, "preference_grid_none");
            grid_index = Arrays.asList(grid_values).indexOf(grid_value);
            if( grid_index == -1 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "can't find grid_value " + grid_value + " in grid_values!");
                grid_index = 0;
            }
            addArrayOptionsToPopup(Arrays.asList(grid_entries), getResources().getString(R.string.grid), true, true, grid_index, true, "GRID", new ArrayOptionsPopupListener() {
                private void update() {
                    if( grid_index == -1 )
                        return;
                    String new_grid_value = grid_values[grid_index];
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(PreferenceKeys.ShowGridPreferenceKey, new_grid_value);
                    editor.apply();
                    main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // because we cache the grid
                }
                @Override
                public int onClickPrev() {
                    if( grid_index != -1 ) {
                        grid_index--;
                        if( grid_index < 0 )
                            grid_index += grid_values.length;
                        update();
                        return grid_index;
                    }
                    return -1;
                }
                @Override
                public int onClickNext() {
                    if( grid_index != -1 ) {
                        grid_index++;
                        if( grid_index >= grid_values.length )
                            grid_index -= grid_values.length;
                        update();
                        return grid_index;
                    }
                    return -1;
                }
            });
            if( MyDebug.LOG )
                Log.d(TAG, "PopupView time 13: " + (System.nanoTime() - debug_time));

            // popup should only be opened if we have a camera controller, but check just to be safe
            if( preview.getCameraController() != null ) {
                List<String> supported_white_balances = preview.getSupportedWhiteBalances();
                List<String> supported_white_balances_entries = null;
                if( supported_white_balances != null ) {
                    supported_white_balances_entries = new ArrayList<>();
                    for(String value : supported_white_balances) {
                        String entry = main_activity.getMainUI().getEntryForWhiteBalance(value);
                        supported_white_balances_entries.add(entry);
                    }
                }
                addRadioOptionsToPopup(sharedPreferences, supported_white_balances_entries, supported_white_balances, getResources().getString(R.string.white_balance), PreferenceKeys.WhiteBalancePreferenceKey, CameraController.WHITE_BALANCE_DEFAULT, null, "TEST_WHITE_BALANCE", new RadioOptionsListener() {
                    @Override
                    public void onClick(String selected_value) {
                        switchToWhiteBalance(selected_value);
                    }
                });
                if( MyDebug.LOG )
                    Log.d(TAG, "PopupView time 14: " + (System.nanoTime() - debug_time));

                List<String> supported_scene_modes = preview.getSupportedSceneModes();
                List<String> supported_scene_modes_entries = null;
                if( supported_scene_modes != null ) {
                    supported_scene_modes_entries = new ArrayList<>();
                    for(String value : supported_scene_modes) {
                        String entry = main_activity.getMainUI().getEntryForSceneMode(value);
                        supported_scene_modes_entries.add(entry);
                    }
                }
                addRadioOptionsToPopup(sharedPreferences, supported_scene_modes_entries, supported_scene_modes, getResources().getString(R.string.scene_mode), PreferenceKeys.SceneModePreferenceKey, CameraController.SCENE_MODE_DEFAULT, null, "TEST_SCENE_MODE", new RadioOptionsListener() {
                    @Override
                    public void onClick(String selected_value) {
                        if( preview.getCameraController() != null ) {
                            if( preview.getCameraController().sceneModeAffectsFunctionality() ) {
                                // need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
                                main_activity.updateForSettings(getResources().getString(R.string.scene_mode) + ": " + main_activity.getMainUI().getEntryForSceneMode(selected_value));
                                main_activity.closePopup();
                            }
                            else {
                                preview.getCameraController().setSceneMode(selected_value);
                                // keep popup open
                            }
                        }
                    }
                });
                if( MyDebug.LOG )
                    Log.d(TAG, "PopupView time 15: " + (System.nanoTime() - debug_time));

                List<String> supported_color_effects = preview.getSupportedColorEffects();
                List<String> supported_color_effects_entries = null;
                if( supported_color_effects != null ) {
                    supported_color_effects_entries = new ArrayList<>();
                    for(String value : supported_color_effects) {
                        String entry = main_activity.getMainUI().getEntryForColorEffect(value);
                        supported_color_effects_entries.add(entry);
                    }
                }
                addRadioOptionsToPopup(sharedPreferences, supported_color_effects_entries, supported_color_effects, getResources().getString(R.string.color_effect), PreferenceKeys.ColorEffectPreferenceKey, CameraController.COLOR_EFFECT_DEFAULT, null, "TEST_COLOR_EFFECT", new RadioOptionsListener() {
                    @Override
                    public void onClick(String selected_value) {
                        if( preview.getCameraController() != null ) {
                            preview.getCameraController().setColorEffect(selected_value);
                        }
                        // keep popup open
                    }
                });
                if( MyDebug.LOG )
                    Log.d(TAG, "PopupView time 16: " + (System.nanoTime() - debug_time));
            }

        }
    }

    int getTotalWidth() {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
    }

    private void changePhotoMode(List<String> photo_modes, List<MyApplicationInterface.PhotoMode> photo_mode_values, String option) {
        if( MyDebug.LOG )
            Log.d(TAG, "changePhotoMode: " + option);

        final MainActivity main_activity = (MainActivity)this.getContext();
        int option_id = -1;
        for(int i=0;i<photo_modes.size() && option_id==-1;i++) {
            if( option.equals( photo_modes.get(i) ) )
                option_id = i;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "mode id: " + option_id);
        if( option_id == -1 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "unknown mode id: " + option_id);
        }
        else {
            MyApplicationInterface.PhotoMode new_photo_mode = photo_mode_values.get(option_id);
            String toast_message = option;
            switch (new_photo_mode) {
                case Standard:
                    toast_message = getResources().getString(R.string.photo_mode_standard_full);
                    break;
                case ExpoBracketing:
                    toast_message = getResources().getString(R.string.photo_mode_expo_bracketing_full);
                    break;
                case FocusBracketing:
                    toast_message = getResources().getString(R.string.photo_mode_focus_bracketing_full);
                    break;
                case FastBurst:
                    toast_message = getResources().getString(R.string.photo_mode_fast_burst_full);
                    break;
                case NoiseReduction:
                    toast_message = getResources().getString(R.string.photo_mode_noise_reduction_full);
                    break;
                case Panorama:
                    toast_message = getResources().getString(R.string.photo_mode_panorama_full);
                    break;
            }
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            switch (new_photo_mode) {
                case Standard:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
                    break;
                case DRO:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_dro");
                    break;
                case HDR:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
                    break;
                case ExpoBracketing:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
                    break;
                case FocusBracketing:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
                    break;
                case FastBurst:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_fast_burst");
                    break;
                case NoiseReduction:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_noise_reduction");
                    break;
                case Panorama:
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_panorama");
                    break;
                default:
                    if (MyDebug.LOG)
                        Log.e(TAG, "unknown new_photo_mode: " + new_photo_mode);
                    break;
            }
            editor.apply();

            boolean done_dialog = false;
            if( new_photo_mode == MyApplicationInterface.PhotoMode.HDR ) {
                boolean done_hdr_info = sharedPreferences.contains(PreferenceKeys.HDRInfoPreferenceKey);
                if( !done_hdr_info ) {
                    main_activity.getMainUI().showInfoDialog(R.string.photo_mode_hdr, R.string.hdr_info, PreferenceKeys.HDRInfoPreferenceKey);
                    done_dialog = true;
                }
            }
            else if( new_photo_mode == MyApplicationInterface.PhotoMode.Panorama ) {
                boolean done_panorama_info = sharedPreferences.contains(PreferenceKeys.PanoramaInfoPreferenceKey);
                if( !done_panorama_info ) {
                    main_activity.getMainUI().showInfoDialog(R.string.photo_mode_panorama_full, R.string.panorama_info, PreferenceKeys.PanoramaInfoPreferenceKey);
                    done_dialog = true;
                }
            }

            if( done_dialog ) {
                // no need to show toast
                toast_message = null;
            }

            main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // because we cache the photomode
            main_activity.updateForSettings(toast_message); // need to setup the camera again, as options may change (e.g., required burst mode, or whether RAW is allowed in this mode)
            main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
        }
    }

    public void switchToWhiteBalance(String selected_value) {
        if( MyDebug.LOG )
            Log.d(TAG, "switchToWhiteBalance: " + selected_value);
        final MainActivity main_activity = (MainActivity)this.getContext();
        final Preview preview = main_activity.getPreview();
        boolean close_popup = false;
        int temperature = -1;
        if( selected_value.equals("manual") ) {
            if( preview.getCameraController() != null ) {
                String current_white_balance = preview.getCameraController().getWhiteBalance();
                if( current_white_balance == null || !current_white_balance.equals("manual") ) {
                    // try to choose a default manual white balance temperature as close as possible to the current auto
                    if( MyDebug.LOG )
                        Log.d(TAG, "changed to manual white balance");
                    close_popup = true;
                    if( preview.getCameraController().captureResultHasWhiteBalanceTemperature() ) {
                        temperature = preview.getCameraController().captureResultWhiteBalanceTemperature();
                        if( MyDebug.LOG )
                            Log.d(TAG, "default to manual white balance temperature: " + temperature);
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, temperature);
                        editor.apply();
                    }
                    // otherwise default to the saved value
                }
            }
        }

        if( preview.getCameraController() != null ) {
            preview.getCameraController().setWhiteBalance(selected_value);
            if( temperature > 0 ) {
                preview.getCameraController().setWhiteBalanceTemperature(temperature);
                // also need to update the slider!
                main_activity.setManualWBSeekbar();
            }
        }
        // keep popup open, unless switching to manual
        if( close_popup ) {
            main_activity.closePopup();
        }
        //main_activity.updateForSettings(getResources().getString(R.string.white_balance) + ": " + selected_value);
        //main_activity.closePopup();
    }

    static abstract class ButtonOptionsPopupListener {
        public abstract void onClick(String option);
    }

    /** Creates UI for selecting an option for multiple possibilites, by placing buttons in one or
     *  more rows.
     * @param max_buttons_per_row If 0, then all buttons will be placed on the same row. Otherwise,
     *                            this is the number of buttons per row, multiple rows will be
     *                            created if necessary.
     */
    private void addButtonOptionsToPopup(List<String> supported_options, int icons_id, int values_id, String prefix_string, String current_value, int max_buttons_per_row, String test_key, final ButtonOptionsPopupListener listener) {
        if(MyDebug.LOG)
            Log.d(TAG, "addButtonOptionsToPopup");
        MainActivity main_activity = (MainActivity)this.getContext();
        createButtonOptions(this, this.getContext(), total_width_dp, main_activity.getMainUI().getTestUIButtonsMap(), supported_options, icons_id, values_id, prefix_string, true, current_value, max_buttons_per_row, test_key, listener);
    }

    static List<View> createButtonOptions(ViewGroup parent, Context context, int total_width_dp, Map<String, View> test_ui_buttons, List<String> supported_options, int icons_id, int values_id, String prefix_string, boolean include_prefix, String current_value, int max_buttons_per_row, String test_key, final ButtonOptionsPopupListener listener) {
        if( MyDebug.LOG )
            Log.d(TAG, "createButtonOptions");
        final List<View> buttons = new ArrayList<>();
        if( supported_options != null ) {
            final long debug_time = System.nanoTime();
            LinearLayout ll2 = new LinearLayout(context);
            ll2.setOrientation(LinearLayout.HORIZONTAL);
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 1: " + (System.nanoTime() - debug_time));
            String [] icons = icons_id != -1 ? context.getResources().getStringArray(icons_id) : null;
            String [] values = values_id != -1 ? context.getResources().getStringArray(values_id) : null;
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 2: " + (System.nanoTime() - debug_time));

            final float scale = context.getResources().getDisplayMetrics().density;
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 2.04: " + (System.nanoTime() - debug_time));
            int actual_max_per_row = supported_options.size();
            if( max_buttons_per_row > 0 )
                actual_max_per_row = Math.min(actual_max_per_row, max_buttons_per_row);
            int button_width_dp = total_width_dp/actual_max_per_row;
            boolean use_scrollview = false;
            final int min_button_width_dp = 48; // needs to be at least 48dp to avoid Google Play pre-launch accessibility report warnings
            if( button_width_dp < min_button_width_dp && max_buttons_per_row == 0 ) {
                button_width_dp = min_button_width_dp;
                use_scrollview = true;
            }
            int button_width = (int)(button_width_dp * scale + 0.5f); // convert dps to pixels
            if( MyDebug.LOG ) {
                Log.d(TAG, "actual_max_per_row: " + actual_max_per_row);
                Log.d(TAG, "button_width_dp: " + button_width_dp);
                Log.d(TAG, "button_width: " + button_width);
                Log.d(TAG, "use_scrollview: " + use_scrollview);
            }

            View.OnClickListener on_click_listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String supported_option = (String)v.getTag();
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked: " + supported_option);
                    listener.onClick(supported_option);
                }
            };
            View current_view = null;
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 2.05: " + (System.nanoTime() - debug_time));

            for(int button_indx=0;button_indx<supported_options.size();button_indx++) {
                final String supported_option = supported_options.get(button_indx);
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.06: " + (System.nanoTime() - debug_time));
                if( MyDebug.LOG )
                    Log.d(TAG, "button_indx = " + button_indx);

                if( max_buttons_per_row > 0 && button_indx > 0 && button_indx % max_buttons_per_row == 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "start a new row");
                    // add the previous row
                    // no need to handle use_scrollview, as we don't support scrollviews with multiple rows
                    parent.addView(ll2);
                    ll2 = new LinearLayout(context);
                    ll2.setOrientation(LinearLayout.HORIZONTAL);

                    int n_remaining = supported_options.size() - button_indx;
                    if( MyDebug.LOG )
                        Log.d(TAG, "n_remaining: " + n_remaining);
                    if( n_remaining <= max_buttons_per_row ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "final row");
                        button_width_dp = total_width_dp/n_remaining;
                        button_width = (int)(button_width_dp * scale + 0.5f); // convert dps to pixels
                    }
                }

                if( MyDebug.LOG )
                    Log.d(TAG, "supported_option: " + supported_option);
                int resource = -1;
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.08: " + (System.nanoTime() - debug_time));
                if( icons != null && values != null ) {
                    int index = -1;
                    for(int i=0;i<values.length && index==-1;i++) {
                        if( values[i].equals(supported_option) )
                            index = i;
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "index: " + index);
                    if( index != -1 ) {
                        resource = context.getResources().getIdentifier(icons[index], null, context.getApplicationContext().getPackageName());
                    }
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.1: " + (System.nanoTime() - debug_time));

                String button_string;
                // hacks for ISO mode ISO_HJR (e.g., on Samsung S5)
                // also some devices report e.g. "ISO100" etc
                if( prefix_string.length() == 0 ) {
                    button_string = supported_option;
                }
                else if( prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 4 && supported_option.substring(0, 4).equalsIgnoreCase("ISO_") ) {
                    button_string = (include_prefix ? prefix_string : "") + "\n" + supported_option.substring(4);
                }
                else if( prefix_string.equalsIgnoreCase("ISO") && supported_option.length() >= 3 && supported_option.substring(0, 3).equalsIgnoreCase("ISO") ) {
                    button_string = (include_prefix ? prefix_string : "") + "\n" + supported_option.substring(3);
                }
                else {
                    button_string = (include_prefix ? prefix_string : "") + "\n" + supported_option;
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "button_string: " + button_string);
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.105: " + (System.nanoTime() - debug_time));
                View view;
                if( resource != -1 ) {
                    ImageButton image_button = new ImageButton(context);
                    if( MyDebug.LOG )
                        Log.d(TAG, "addButtonOptionsToPopup time 2.11: " + (System.nanoTime() - debug_time));
                    view = image_button;
                    buttons.add(view);
                    ll2.addView(view);
                    if( MyDebug.LOG )
                        Log.d(TAG, "addButtonOptionsToPopup time 2.12: " + (System.nanoTime() - debug_time));

                    //image_button.setImageResource(resource);
                    final MainActivity main_activity = (MainActivity)context;
                    Bitmap bm = main_activity.getPreloadedBitmap(resource);
                    if( bm != null )
                        image_button.setImageBitmap(bm);
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "failed to find bitmap for resource " + resource + "!");
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "addButtonOptionsToPopup time 2.13: " + (System.nanoTime() - debug_time));
                    image_button.setScaleType(ScaleType.FIT_CENTER);
                    image_button.setBackgroundColor(Color.TRANSPARENT);
                    final int padding = (int) (7 * scale + 0.5f); // convert dps to pixels
                    view.setPadding(padding, padding, padding, padding);
                }
                else {
                    Button button = new Button(context);
                    button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash! Also looks nicer anyway...
                    view = button;
                    buttons.add(view);
                    ll2.addView(view);

                    button.setText(button_string);
                    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, button_text_size_dip);
                    button.setTextColor(Color.WHITE);
                    // need 0 padding so we have enough room to display text for ISO buttons, when there are 6 ISO settings
                    final int padding = (int) (0 * scale + 0.5f); // convert dps to pixels
                    view.setPadding(padding, padding, padding, padding);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.2: " + (System.nanoTime() - debug_time));

                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = button_width;
                // be careful of making the height too smaller, as harder to touch buttons; remember that this also affects the
                // ISO buttons on exposure panel, and not just the main popup!
                params.height = (int) (55 * scale + 0.5f); // convert dps to pixels
                view.setLayoutParams(params);

                view.setContentDescription(button_string);
                if( supported_option.equals(current_value) ) {
                    setButtonSelected(view, true);
                    current_view = view;
                }
                else {
                    setButtonSelected(view, false);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.3: " + (System.nanoTime() - debug_time));
                view.setTag(supported_option);
                view.setOnClickListener(on_click_listener);
                if( MyDebug.LOG )
                    Log.d(TAG, "addButtonOptionsToPopup time 2.35: " + (System.nanoTime() - debug_time));
                if( test_ui_buttons != null )
                    test_ui_buttons.put(test_key + "_" + supported_option, view);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "addButtonOptionsToPopup time 2.4: " + (System.nanoTime() - debug_time));
                    Log.d(TAG, "added to popup_buttons: " + test_key + "_" + supported_option + " view: " + view);
                    if( test_ui_buttons != null )
                        Log.d(TAG, "test_ui_buttons is now: " + test_ui_buttons);
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 3: " + (System.nanoTime() - debug_time));
            if( use_scrollview ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "using scrollview");
                final int total_width = (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
                final HorizontalScrollView scroll = new HorizontalScrollView(context);
                scroll.addView(ll2);
                {
                    ViewGroup.LayoutParams params = new LayoutParams(
                            total_width,
                            LayoutParams.WRAP_CONTENT);
                    scroll.setLayoutParams(params);
                }
                parent.addView(scroll);
                if( current_view != null ) {
                    // scroll to the selected button
                    final View final_current_view = current_view;
                    final int final_button_width = button_width;
                    parent.getViewTreeObserver().addOnGlobalLayoutListener(
                            new OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    // scroll so selected button is centred
                                    int jump_x = final_current_view.getLeft() - (total_width-final_button_width)/2;
                                    // scrollTo should automatically clamp to the bounds of the view, but just in case
                                    jump_x = Math.min(jump_x, total_width-1);
                                    if( jump_x > 0 ) {
                                        scroll.scrollTo(jump_x, 0);
                                    }
                                }
                            }
                    );
                }
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "not using scrollview");
                parent.addView(ll2);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "addButtonOptionsToPopup time 4: " + (System.nanoTime() - debug_time));
        }
        return buttons;
    }

    static void setButtonSelected(View view, boolean selected) {
        view.setAlpha(selected ? ALPHA_BUTTON_SELECTED : ALPHA_BUTTON);
    }

    private void addTitleToPopup(final String title) {
        TextView text_view = new TextView(this.getContext());
        text_view.setText(title + ":");
        text_view.setTextColor(Color.WHITE);
        text_view.setGravity(Gravity.CENTER);
        text_view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, title_text_size_dip);
        text_view.setTypeface(null, Typeface.BOLD);
        //text_view.setBackgroundColor(Color.GRAY); // debug
        this.addView(text_view);
    }

    private abstract class RadioOptionsListener {
        /** Called when a radio option is selected.
         * @param selected_value The entry in the supplied supported_options_values list (received
         *                       by addRadioOptionsToPopup) that corresponds to the selected radio
         *                       option.
         */
        protected abstract void onClick(String selected_value);
    }

    /** Adds a set of radio options to the popup menu.
     * @param sharedPreferences         The SharedPreferences.
     * @param supported_options_entries The strings to display on the radio options.
     * @param supported_options_values  A corresponding array of values. These aren't shown to the
     *                                  user, but are the values that will be set in the
     *                                  sharedPreferences, and passed to the listener.
     * @param title                     The text to display as a title for this radio group.
     * @param preference_key            The preference key to use for the values in the
     *                                  sharedPreferences. May be null, in which case it's up to
     *                                  the user to save the new preference via a listener.
     * @param default_value             The default value for the preference_key in the
     *                                  sharedPreferences. Only needed if preference_key is
     *                                  non-null.
     * @param current_option_value      If preference_key is null, this should be the currently
     *                                  selected value. Otherwise, this is ignored.
     * @param test_key                  Used for testing, a tag to identify the RadioGroup that's
     *                                  created.
     * @param listener                  If null, selecting an option will call
     *                                  MainActivity.updateForSettings() and close the popup. If
     *                                  not null, instead selecting an option will call the
     *                                  listener.
     */
    private void addRadioOptionsToPopup(final SharedPreferences sharedPreferences, final List<String> supported_options_entries, final List<String> supported_options_values, final String title, final String preference_key, final String default_value, final String current_option_value, final String test_key, final RadioOptionsListener listener) {
        if( MyDebug.LOG )
            Log.d(TAG, "addRadioOptionsToPopup: " + title);
        if( supported_options_entries != null ) {
            final MainActivity main_activity = (MainActivity)this.getContext();
            final long debug_time = System.nanoTime();

            final Button button = new Button(this.getContext());
            button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
            button.setText(title + "...");
            button.setAllCaps(false);
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, title_text_size_dip);
            this.addView(button);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToPopup time 1: " + (System.nanoTime() - debug_time));

            final RadioGroup rg = new RadioGroup(this.getContext());
            rg.setOrientation(RadioGroup.VERTICAL);
            rg.setVisibility(View.GONE);
            main_activity.getMainUI().getTestUIButtonsMap().put(test_key, rg);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToPopup time 2: " + (System.nanoTime() - debug_time));

            button.setOnClickListener(new OnClickListener() {
                private boolean opened = false;
                private boolean created = false;

                @Override
                public void onClick(View view) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked to open radio buttons menu: " + title);
                    if( opened ) {
                        //rg.removeAllViews();
                        rg.setVisibility(View.GONE);
                        final ScrollView popup_container = main_activity.findViewById(R.id.popup_container);
                        // need to invalidate/requestLayout so that the scrollview's scroll positions update - otherwise scrollBy below doesn't work properly, when the user reopens the radio buttons
                        popup_container.invalidate();
                        popup_container.requestLayout();
                    }
                    else {
                        if( !created ) {
                            addRadioOptionsToGroup(rg, sharedPreferences, supported_options_entries, supported_options_values, title, preference_key, default_value, current_option_value, test_key, listener);
                            created = true;
                        }
                        rg.setVisibility(View.VISIBLE);
                        final ScrollView popup_container = main_activity.findViewById(R.id.popup_container);
                        popup_container.getViewTreeObserver().addOnGlobalLayoutListener(
                                new OnGlobalLayoutListener() {
                                    @Override
                                    public void onGlobalLayout() {
                                        if( MyDebug.LOG )
                                            Log.d(TAG, "onGlobalLayout()");
                                        // stop listening - only want to call this once!
                                        if( Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 ) {
                                            popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                        }
                                        else {
                                            popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                        }

                                        // so that the user sees the options appear, if the button is at the bottom of the current scrollview position
                                        if( rg.getChildCount() > 0 ) {
                                            int id = rg.getCheckedRadioButtonId();
                                            if( id >= 0 && id < rg.getChildCount() ) {
                                                popup_container.smoothScrollBy(0, rg.getChildAt(id).getBottom());
                                            }
                                        }
                                    }
                                }
                        );
                    }
                    opened = !opened;
                }
            });

            this.addView(rg);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToPopup time 5: " + (System.nanoTime() - debug_time));
        }
    }

    private void addRadioOptionsToGroup(final RadioGroup rg, SharedPreferences sharedPreferences, List<String> supported_options_entries, List<String> supported_options_values, final String title, final String preference_key, final String default_value, String current_option_value, final String test_key, final RadioOptionsListener listener) {
        if( MyDebug.LOG )
            Log.d(TAG, "addRadioOptionsToGroup: " + title);
        if( preference_key != null )
            current_option_value = sharedPreferences.getString(preference_key, default_value);
        final long debug_time = System.nanoTime();
        final MainActivity main_activity = (MainActivity)this.getContext();
        int count = 0;
        for(int i=0;i<supported_options_entries.size();i++) {
            final String supported_option_entry = supported_options_entries.get(i);
            final String supported_option_value = supported_options_values.get(i);
            if( MyDebug.LOG ) {
                Log.d(TAG, "supported_option_entry: " + supported_option_entry);
                Log.d(TAG, "supported_option_value: " + supported_option_value);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 1: " + (System.nanoTime() - debug_time));
            RadioButton button = new RadioButton(this.getContext());
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 2: " + (System.nanoTime() - debug_time));

            button.setId(count);

            button.setText(supported_option_entry);
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, standard_text_size_dip);
            button.setTextColor(Color.WHITE);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 3: " + (System.nanoTime() - debug_time));
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 4: " + (System.nanoTime() - debug_time));
            rg.addView(button);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 5: " + (System.nanoTime() - debug_time));

            if( supported_option_value.equals(current_option_value) ) {
                //button.setChecked(true);
                rg.check(count);
            }
            count++;

            button.setContentDescription(supported_option_entry);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 6: " + (System.nanoTime() - debug_time));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "clicked current_option entry: " + supported_option_entry);
                        Log.d(TAG, "clicked current_option entry: " + supported_option_value);
                    }
                    if( preference_key != null ) {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(preference_key, supported_option_value);
                        editor.apply();
                    }

                    if( listener != null ) {
                        listener.onClick(supported_option_value);
                    }
                    else {
                        main_activity.updateForSettings(title + ": " + supported_option_entry);
                        main_activity.closePopup();
                    }
                }
            });
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 7: " + (System.nanoTime() - debug_time));
            main_activity.getMainUI().getTestUIButtonsMap().put(test_key + "_" + supported_option_value, button);
            if( MyDebug.LOG )
                Log.d(TAG, "addRadioOptionsToGroup time 8: " + (System.nanoTime() - debug_time));
        }
        if( MyDebug.LOG )
            Log.d(TAG, "addRadioOptionsToGroup time total: " + (System.nanoTime() - debug_time));
    }

    private abstract class ArrayOptionsPopupListener {
        protected abstract int onClickPrev();
        protected abstract int onClickNext();
    }

    private void setArrayOptionsText(List<String> supported_options, String title, TextView textView, boolean title_in_options, boolean title_in_options_first_only, int current_index) {
        if( title_in_options && !( current_index != 0 && title_in_options_first_only ) )
            textView.setText(title + ": " + supported_options.get(current_index));
        else
            textView.setText(supported_options.get(current_index));
    }

    /** Adds a set of options to the popup menu, where there user can select one option out of an array of values, using previous or
     *  next buttons to switch between them.
     * @param supported_options The strings for the array of values to choose from.
     * @param title Title to display.
     * @param title_in_options Prepend the title to each of the values, rather than above the values.
     * @param title_in_options_first_only If title_in_options is true, only prepend to the first option.
     * @param current_index Index in the supported_options array of the currently selected option.
     * @param cyclic Whether the user can cycle beyond the start/end, to wrap around.
     * @param test_key Used to keep track of the UI elements created, for testing.
     * @param listener Listener called when previous/next buttons are clicked (and hence the option
     *                 changed).
     */
    private void addArrayOptionsToPopup(final List<String> supported_options, final String title, final boolean title_in_options, final boolean title_in_options_first_only, final int current_index, final boolean cyclic, final String test_key, final ArrayOptionsPopupListener listener) {
        if( supported_options != null && current_index != -1 ) {
            if( !title_in_options ) {
                addTitleToPopup(title);
            }

            final MainActivity main_activity = (MainActivity)this.getContext();
			/*final Button prev_button = new Button(this.getContext());
			//prev_button.setBackgroundResource(R.drawable.exposure);
			prev_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			prev_button.setText("<");
			this.addView(prev_button);*/

            LinearLayout ll2 = new LinearLayout(this.getContext());
            ll2.setOrientation(LinearLayout.HORIZONTAL);

            final TextView text_view = new TextView(this.getContext());
            setArrayOptionsText(supported_options, title, text_view, title_in_options, title_in_options_first_only, current_index);
            text_view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, standard_text_size_dip);
            text_view.setTextColor(Color.WHITE);
            text_view.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            text_view.setLayoutParams(params);

            final float scale = getResources().getDisplayMetrics().density;
            final int padding = (int) (0 * scale + 0.5f); // convert dps to pixels
            final Button prev_button = new Button(this.getContext());
            prev_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
            ll2.addView(prev_button);
            prev_button.setText("<");
            prev_button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, arrow_text_size_dip);
            prev_button.setTypeface(null, Typeface.BOLD);
            prev_button.setPadding(padding, padding, padding, padding);
            ViewGroup.LayoutParams vg_params = prev_button.getLayoutParams();
            vg_params.width = arrow_button_w;
            vg_params.height = arrow_button_h;
            prev_button.setLayoutParams(vg_params);
            prev_button.setVisibility( (cyclic || current_index > 0) ? View.VISIBLE : View.INVISIBLE);
            prev_button.setContentDescription( getResources().getString(R.string.previous) + " " + title);
            main_activity.getMainUI().getTestUIButtonsMap().put(test_key + "_PREV", prev_button);

            ll2.addView(text_view);
            main_activity.getMainUI().getTestUIButtonsMap().put(test_key, text_view);

            final Button next_button = new Button(this.getContext());
            next_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
            ll2.addView(next_button);
            next_button.setText(">");
            next_button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, arrow_text_size_dip);
            next_button.setTypeface(null, Typeface.BOLD);
            next_button.setPadding(padding, padding, padding, padding);
            vg_params = next_button.getLayoutParams();
            vg_params.width = arrow_button_w;
            vg_params.height = arrow_button_h;
            next_button.setLayoutParams(vg_params);
            next_button.setVisibility( (cyclic || current_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
            next_button.setContentDescription( getResources().getString(R.string.next) + " " + title);
            main_activity.getMainUI().getTestUIButtonsMap().put(test_key + "_NEXT", next_button);

            // test:
			/*prev_button.setText(prev_button.getContentDescription());
			prev_button.setAllCaps(false);
			next_button.setText(next_button.getContentDescription());
			next_button.setAllCaps(false);*/

            prev_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int new_index = listener.onClickPrev();
                    if( new_index != -1 ) {
                        setArrayOptionsText(supported_options, title, text_view, title_in_options, title_in_options_first_only, new_index);
                        prev_button.setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
                        next_button.setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
                    }
                }
            });
            next_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int new_index = listener.onClickNext();
                    if( new_index != -1 ) {
                        setArrayOptionsText(supported_options, title, text_view, title_in_options, title_in_options_first_only, new_index);
                        prev_button.setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
                        next_button.setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
                    }
                }
            });

            this.addView(ll2);
        }
    }
}
