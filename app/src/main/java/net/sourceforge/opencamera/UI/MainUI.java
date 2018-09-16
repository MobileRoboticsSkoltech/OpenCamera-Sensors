package net.sourceforge.opencamera.UI;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.ZoomControls;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/** This contains functionality related to the main UI.
 */
public class MainUI {
	private static final String TAG = "MainUI";

	private final MainActivity main_activity;

	private volatile boolean popup_view_is_open; // must be volatile for test project reading the state
    private PopupView popup_view;
	private final static boolean cache_popup = true; // if false, we recreate the popup each time
	private boolean force_destroy_popup = false; // if true, then the popup isn't cached for only the next time the popup is closed

    private int current_orientation;
	private boolean ui_placement_right = true;
	private boolean view_rotate_animation;

	private boolean immersive_mode;
    private boolean show_gui_photo = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
    private boolean show_gui_video = true;

	private boolean keydown_volume_up;
	private boolean keydown_volume_down;

	// for testing:
	private final Map<String, View> test_ui_buttons = new Hashtable<>();

	public MainUI(MainActivity main_activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "MainUI");
		this.main_activity = main_activity;
		
		this.setSeekbarColors();

		this.setIcon(R.id.gallery);
		this.setIcon(R.id.settings);
		this.setIcon(R.id.popup);
		this.setIcon(R.id.exposure_lock);
		this.setIcon(R.id.exposure);
		//this.setIcon(R.id.switch_video);
		//this.setIcon(R.id.switch_camera);
		this.setIcon(R.id.audio_control);
		this.setIcon(R.id.trash);
		this.setIcon(R.id.share);
	}
	
	private void setIcon(int id) {
		if( MyDebug.LOG )
			Log.d(TAG, "setIcon: " + id);
	    ImageButton button = main_activity.findViewById(id);
	    button.setBackgroundColor(Color.argb(63, 63, 63, 63)); // n.b., rgb color seems to be ignored for Android 6 onwards, but still relevant for older versions
	}
	
	private void setSeekbarColors() {
		if( MyDebug.LOG )
			Log.d(TAG, "setSeekbarColors");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			ColorStateList progress_color = ColorStateList.valueOf( Color.argb(255, 240, 240, 240) );
			ColorStateList thumb_color = ColorStateList.valueOf( Color.argb(255, 255, 255, 255) );

			SeekBar seekBar = main_activity.findViewById(R.id.zoom_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.focus_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.focus_bracketing_target_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.exposure_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.iso_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.exposure_time_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);

			seekBar = main_activity.findViewById(R.id.white_balance_seekbar);
			seekBar.setProgressTintList(progress_color);
			seekBar.setThumbTintList(thumb_color);
		}
	}

	/** Similar view.setRotation(ui_rotation), but achieves this via an animation.
	 */
	private void setViewRotation(View view, float ui_rotation) {
		if( !view_rotate_animation ) {
			view.setRotation(ui_rotation);
		}
		float rotate_by = ui_rotation - view.getRotation();
		if( rotate_by > 181.0f )
			rotate_by -= 360.0f;
		else if( rotate_by < -181.0f )
			rotate_by += 360.0f;
		// view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
		// we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
		view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
	}

    public void layoutUI() {
		layoutUI(false);
	}

    private void layoutUI(boolean popup_container_only) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI");
			debug_time = System.currentTimeMillis();
		}
		//main_activity.getPreview().updateUIPlacement();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		String ui_placement = sharedPreferences.getString(PreferenceKeys.UIPlacementPreferenceKey, "ui_right");
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		this.ui_placement_right = ui_placement.equals("ui_right");
		if( MyDebug.LOG )
			Log.d(TAG, "ui_placement: " + ui_placement);
		// new code for orientation fixed to landscape	
		// the display orientation should be locked to landscape, but how many degrees is that?
	    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
	    int degrees = 0;
	    switch (rotation) {
	    	case Surface.ROTATION_0: degrees = 0; break;
	        case Surface.ROTATION_90: degrees = 90; break;
	        case Surface.ROTATION_180: degrees = 180; break;
	        case Surface.ROTATION_270: degrees = 270; break;
    		default:
    			break;
	    }
	    // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
	    // relative_orientation is clockwise from landscape-left
    	//int relative_orientation = (current_orientation + 360 - degrees) % 360;
    	int relative_orientation = (current_orientation + degrees) % 360;
		if( MyDebug.LOG ) {
			Log.d(TAG, "    current_orientation = " + current_orientation);
			Log.d(TAG, "    degrees = " + degrees);
			Log.d(TAG, "    relative_orientation = " + relative_orientation);
		}
		int ui_rotation = (360 - relative_orientation) % 360;
		main_activity.getPreview().setUIRotation(ui_rotation);
		int align_left = RelativeLayout.ALIGN_LEFT;
		int align_right = RelativeLayout.ALIGN_RIGHT;
		//int align_top = RelativeLayout.ALIGN_TOP;
		//int align_bottom = RelativeLayout.ALIGN_BOTTOM;
		int left_of = RelativeLayout.LEFT_OF;
		int right_of = RelativeLayout.RIGHT_OF;
		int above = RelativeLayout.ABOVE;
		int below = RelativeLayout.BELOW;
		int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
		int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
		int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
		int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
		if( !ui_placement_right ) {
			//align_top = RelativeLayout.ALIGN_BOTTOM;
			//align_bottom = RelativeLayout.ALIGN_TOP;
			above = RelativeLayout.BELOW;
			below = RelativeLayout.ABOVE;
			align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
			align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
		}

		if( !popup_container_only )
		{
			// we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
			View view = main_activity.findViewById(R.id.gui_anchor);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.gallery);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gui_anchor);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.settings);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.gallery);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.popup);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.settings);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.exposure_lock);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.exposure);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.exposure_lock);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			/*view = main_activity.findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.exposure);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);*/
	
			/*view = main_activity.findViewById(R.id.switch_camera);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			//layoutParams.addRule(left_of, R.id.switch_video);
			layoutParams.addRule(left_of, R.id.exposure);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);*/

			view = main_activity.findViewById(R.id.audio_control);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, 0);
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			//layoutParams.addRule(left_of, R.id.switch_camera);
			layoutParams.addRule(left_of, R.id.exposure);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.audio_control);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.trash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.take_photo);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.switch_camera);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.pause_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.take_photo_when_video_recording);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.zoom);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);
			view.setRotation(180.0f); // should always match the zoom_seekbar, so that zoom in and out are in the same directions
	
			view = main_activity.findViewById(R.id.zoom_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			// if we are showing the zoom control, the align next to that; otherwise have it aligned close to the edge of screen
			if( sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false) ) {
				layoutParams.addRule(align_left, 0);
				layoutParams.addRule(align_right, R.id.zoom);
				layoutParams.addRule(above, R.id.zoom);
				layoutParams.addRule(below, 0);
				// need to clear the others, in case we turn zoom controls on/off
				layoutParams.addRule(align_parent_left, 0);
				layoutParams.addRule(align_parent_right, 0);
				layoutParams.addRule(align_parent_top, 0);
				layoutParams.addRule(align_parent_bottom, 0);
			}
			else {
				layoutParams.addRule(align_parent_left, 0);
				layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
				layoutParams.addRule(align_parent_top, 0);
				layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
				// need to clear the others, in case we turn zoom controls on/off
				layoutParams.addRule(align_left, 0);
				layoutParams.addRule(align_right, 0);
				layoutParams.addRule(above, 0);
				layoutParams.addRule(below, 0);
			}
			view.setLayoutParams(layoutParams);

			view = main_activity.findViewById(R.id.focus_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(left_of, R.id.zoom_seekbar);
			layoutParams.addRule(right_of, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			view.setLayoutParams(layoutParams);

			view = main_activity.findViewById(R.id.focus_bracketing_target_seekbar);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_left, R.id.preview);
			layoutParams.addRule(align_right, 0);
			layoutParams.addRule(left_of, R.id.zoom_seekbar);
			layoutParams.addRule(right_of, 0);
			layoutParams.addRule(above, R.id.focus_seekbar);
			layoutParams.addRule(below, 0);
			view.setLayoutParams(layoutParams);
		}

		if( !popup_container_only )
		{
			// set seekbar info
			int width_dp;
			if( ui_rotation == 0 || ui_rotation == 180 ) {
				width_dp = 350;
			}
			else {
				width_dp = 250;
			}
			int height_dp = 50;
			final float scale = main_activity.getResources().getDisplayMetrics().density;
			int width_pixels = (int) (width_dp * scale + 0.5f); // convert dps to pixels
			int height_pixels = (int) (height_dp * scale + 0.5f); // convert dps to pixels

			View view = main_activity.findViewById(R.id.sliders_container);
			setViewRotation(view, ui_rotation);
			view.setTranslationX(0.0f);
			view.setTranslationY(0.0f);
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				view.setTranslationX(2*height_pixels);
			}

			view = main_activity.findViewById(R.id.exposure_seekbar);
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = main_activity.findViewById(R.id.exposure_seekbar_zoom);
			view.setAlpha(0.5f);

			view = main_activity.findViewById(R.id.iso_seekbar);
			lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = main_activity.findViewById(R.id.exposure_time_seekbar);
			lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);

			view = main_activity.findViewById(R.id.white_balance_seekbar);
			lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
			lp.width = width_pixels;
			lp.height = height_pixels;
			view.setLayoutParams(lp);
		}

		if( popupIsOpen() )
		{
			View view = main_activity.findViewById(R.id.popup_container);
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			//layoutParams.addRule(left_of, R.id.popup);
			layoutParams.addRule(align_right, R.id.popup);
			layoutParams.addRule(below, R.id.popup);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(align_parent_top, 0);
			view.setLayoutParams(layoutParams);

			setViewRotation(view, ui_rotation);
			// reset:
			view.setTranslationX(0.0f);
			view.setTranslationY(0.0f);

			int popup_width = view.getWidth();
			int popup_height = view.getHeight();
			if( MyDebug.LOG ) {
				Log.d(TAG, "popup_width: " + popup_width);
				Log.d(TAG, "popup_height: " + popup_height);
				if( popup_view != null )
					Log.d(TAG, "popup total width: " + popup_view.getTotalWidth());
			}
			if( popup_view != null && popup_width > popup_view.getTotalWidth()*1.2  ) {
				// This is a workaround for the rare but annoying bug where the popup window is too large
				// (and appears partially off-screen). Unfortunately have been unable to fix - and trying
				// to force the popup container to have a particular width just means some of the contents
				// (e.g., Timer) are missing. But at least stop caching it, so that reopening the popup
				// should fix it, rather than having to restart or pause/resume Open Camera.
				// Also note, normally we should expect popup_width == popup_view.getTotalWidth(), but
				// have put a fudge factor of 1.2 just in case it's normally slightly larger on some
				// devices.
				Log.e(TAG, "### popup view is too big?!");
				force_destroy_popup = true;
				/*popup_width = popup_view.getTotalWidth();
				ViewGroup.LayoutParams params = new RelativeLayout.LayoutParams(
						popup_width,
						RelativeLayout.LayoutParams.WRAP_CONTENT);
				view.setLayoutParams(params);*/
			}
			else {
				force_destroy_popup = false;
			}
			if( ui_rotation == 0 || ui_rotation == 180 ) {
				view.setPivotX(popup_width/2.0f);
				view.setPivotY(popup_height/2.0f);
			}
			else {
				view.setPivotX(popup_width);
				view.setPivotY(ui_placement_right ? 0.0f : popup_height);
				if( ui_placement_right ) {
					if( ui_rotation == 90 )
						view.setTranslationY( popup_width );
					else if( ui_rotation == 270 )
						view.setTranslationX( - popup_height );
				}
				else {
					if( ui_rotation == 90 )
						view.setTranslationX( - popup_height );
					else if( ui_rotation == 270 )
						view.setTranslationY( - popup_width );
				}
			}
		}

		if( !popup_container_only ) {
			setTakePhotoIcon();
			// no need to call setSwitchCameraContentDescription()
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI: total time: " + (System.currentTimeMillis() - debug_time));
		}
    }

    /** Set icons for taking photos vs videos.
	 *  Also handles content descriptions for the take photo button and switch video button.
     */
    public void setTakePhotoIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "setTakePhotoIcon()");
		if( main_activity.getPreview() != null ) {
			ImageButton view = main_activity.findViewById(R.id.take_photo);
			int resource;
			int content_description;
			int switch_video_content_description;
			if( main_activity.getPreview().isVideo() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "set icon to video");
				resource = main_activity.getPreview().isVideoRecording() ? R.drawable.take_video_recording : R.drawable.take_video_selector;
				content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;
				switch_video_content_description = R.string.switch_to_photo;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "set icon to photo");
				resource = R.drawable.take_photo_selector;
				content_description = R.string.take_photo;
				switch_video_content_description = R.string.switch_to_video;
			}
			view.setImageResource(resource);
			view.setContentDescription( main_activity.getResources().getString(content_description) );
			view.setTag(resource); // for testing

			view = main_activity.findViewById(R.id.switch_video);
			view.setContentDescription( main_activity.getResources().getString(switch_video_content_description) );
			resource = main_activity.getPreview().isVideo() ? R.drawable.take_photo : R.drawable.take_video;
			view.setImageResource(resource);
			view.setTag(resource); // for testing
		}
    }

    /** Set content description for switch camera button.
     */
    public void setSwitchCameraContentDescription() {
		if( MyDebug.LOG )
			Log.d(TAG, "setSwitchCameraContentDescription()");
		if( main_activity.getPreview() != null && main_activity.getPreview().canSwitchCamera() ) {
			ImageButton view = main_activity.findViewById(R.id.switch_camera);
			int content_description;
			int cameraId = main_activity.getNextCameraId();
		    if( main_activity.getPreview().getCameraControllerManager().isFrontFacing( cameraId ) ) {
				content_description = R.string.switch_to_front_camera;
		    }
		    else {
				content_description = R.string.switch_to_back_camera;
		    }
			if( MyDebug.LOG )
				Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
			view.setContentDescription( main_activity.getResources().getString(content_description) );
		}
    }

	/** Set content description for pause video button.
	 */
	public void setPauseVideoContentDescription() {
		if (MyDebug.LOG)
			Log.d(TAG, "setPauseVideoContentDescription()");
		ImageButton pauseVideoButton = main_activity.findViewById(R.id.pause_video);
		int content_description;
		if( main_activity.getPreview().isVideoRecordingPaused() ) {
			content_description = R.string.resume_video;
			pauseVideoButton.setImageResource(R.drawable.ic_play_circle_outline_white_48dp);
		}
		else {
			content_description = R.string.pause_video;
			pauseVideoButton.setImageResource(R.drawable.ic_pause_circle_outline_white_48dp);
		}
		if( MyDebug.LOG )
			Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
		pauseVideoButton.setContentDescription(main_activity.getResources().getString(content_description));
	}

    public boolean getUIPlacementRight() {
    	return this.ui_placement_right;
    }

    public void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
		    orientation = (orientation + 45) / 90 * 90;
		    orientation = orientation % 360;
		    if( orientation != current_orientation ) {
			    this.current_orientation = orientation;
				if( MyDebug.LOG ) {
					Log.d(TAG, "current_orientation is now: " + current_orientation);
				}
				view_rotate_animation = true;
			    layoutUI();
				view_rotate_animation = false;
			}
		}
	}

    public void setImmersiveMode(final boolean immersive_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "setImmersiveMode: " + immersive_mode);
    	this.immersive_mode = immersive_mode;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
				// if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
		    	//final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
		    	final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
				if( MyDebug.LOG )
					Log.d(TAG, "setImmersiveMode: set visibility: " + visibility);
		    	// n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
			    View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = main_activity.findViewById(R.id.switch_video);
			    View exposureButton = main_activity.findViewById(R.id.exposure);
			    View exposureLockButton = main_activity.findViewById(R.id.exposure_lock);
			    View audioControlButton = main_activity.findViewById(R.id.audio_control);
			    View popupButton = main_activity.findViewById(R.id.popup);
			    View galleryButton = main_activity.findViewById(R.id.gallery);
			    View settingsButton = main_activity.findViewById(R.id.settings);
			    View zoomControls = main_activity.findViewById(R.id.zoom);
			    View zoomSeekBar = main_activity.findViewById(R.id.zoom_seekbar);
			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
			    	switchCameraButton.setVisibility(visibility);
		    	switchVideoButton.setVisibility(visibility);
			    if( main_activity.supportsExposureButton() )
			    	exposureButton.setVisibility(visibility);
			    if( main_activity.getPreview().supportsExposureLock() )
			    	exposureLockButton.setVisibility(visibility);
			    if( main_activity.hasAudioControl() )
			    	audioControlButton.setVisibility(visibility);
		    	popupButton.setVisibility(visibility);
			    galleryButton.setVisibility(visibility);
			    settingsButton.setVisibility(visibility);
				if( MyDebug.LOG ) {
					Log.d(TAG, "has_zoom: " + main_activity.getPreview().supportsZoom());
				}
				if( main_activity.getPreview().supportsZoom() && sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false) ) {
					zoomControls.setVisibility(visibility);
				}
				if( main_activity.getPreview().supportsZoom() && sharedPreferences.getBoolean(PreferenceKeys.ShowZoomSliderControlsPreferenceKey, true) ) {
					zoomSeekBar.setVisibility(visibility);
				}
        		String pref_immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_low_profile");
        		if( pref_immersive_mode.equals("immersive_mode_everything") ) {
					if( sharedPreferences.getBoolean(PreferenceKeys.ShowTakePhotoPreferenceKey, true) ) {
						View takePhotoButton = main_activity.findViewById(R.id.take_photo);
						takePhotoButton.setVisibility(visibility);
					}
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && main_activity.getPreview().isVideoRecording() ) {
						View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
						pauseVideoButton.setVisibility(visibility);
					}
					if( main_activity.getPreview().supportsPhotoVideoRecording() && main_activity.getApplicationInterface().usePhotoVideoRecording() && main_activity.getPreview().isVideoRecording() ) {
						View takePhotoVideoButton = main_activity.findViewById(R.id.take_photo_when_video_recording);
						takePhotoVideoButton.setVisibility(visibility);
					}
        		}
				if( !immersive_mode ) {
					// make sure the GUI is set up as expected
					showGUI();
				}
			}
		});
    }
    
    public boolean inImmersiveMode() {
    	return immersive_mode;
    }

    public void showGUI(final boolean show, final boolean is_video) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "showGUI: " + show);
			Log.d(TAG, "is_video: " + is_video);
		}
		if( is_video )
			this.show_gui_video = show;
		else
			this.show_gui_photo = show;
		showGUI();
	}

	private void showGUI() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "showGUI");
			Log.d(TAG, "show_gui_photo: " + show_gui_photo);
			Log.d(TAG, "show_gui_video: " + show_gui_video);
		}
		if( inImmersiveMode() )
			return;
		if( (show_gui_photo || show_gui_video) && main_activity.usingKitKatImmersiveMode() ) {
			// call to reset the timer
			main_activity.initImmersiveMode();
		}
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
		    	final int visibility = (show_gui_photo && show_gui_video) ? View.VISIBLE : View.GONE; // for UI that is hidden while taking photo or video
		    	final int visibility_video = show_gui_photo ? View.VISIBLE : View.GONE; // for UI that is only hidden while taking photo
			    View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = main_activity.findViewById(R.id.switch_video);
			    View exposureButton = main_activity.findViewById(R.id.exposure);
			    View exposureLockButton = main_activity.findViewById(R.id.exposure_lock);
			    View audioControlButton = main_activity.findViewById(R.id.audio_control);
			    View popupButton = main_activity.findViewById(R.id.popup);
			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
			    	switchCameraButton.setVisibility(visibility);
				switchVideoButton.setVisibility(visibility);
			    if( main_activity.supportsExposureButton() )
			    	exposureButton.setVisibility(visibility_video); // still allow exposure when recording video
			    if( main_activity.getPreview().supportsExposureLock() )
			    	exposureLockButton.setVisibility(visibility_video); // still allow exposure lock when recording video
			    if( main_activity.hasAudioControl() )
			    	audioControlButton.setVisibility(visibility);
			    if( !(show_gui_photo && show_gui_video) ) {
			    	closePopup(); // we still allow the popup when recording video, but need to update the UI (so it only shows flash options), so easiest to just close
			    }
				popupButton.setVisibility(main_activity.getPreview().supportsFlash() ? visibility_video : visibility); // still allow popup in order to change flash mode when recording video
			}
		});
    }

    public void audioControlStarted() {
		ImageButton view = main_activity.findViewById(R.id.audio_control);
		view.setImageResource(R.drawable.ic_mic_red_48dp);
		view.setContentDescription( main_activity.getResources().getString(R.string.audio_control_stop) );
    }

    public void audioControlStopped() {
		ImageButton view = main_activity.findViewById(R.id.audio_control);
		view.setImageResource(R.drawable.ic_mic_white_48dp);
		view.setContentDescription( main_activity.getResources().getString(R.string.audio_control_start) );
    }

    private boolean isExposureUIOpen() {
		View exposure_seek_bar = main_activity.findViewById(R.id.exposure_container);
		int exposure_visibility = exposure_seek_bar.getVisibility();
		View manual_exposure_seek_bar = main_activity.findViewById(R.id.manual_exposure_container);
		int manual_exposure_visibility = manual_exposure_seek_bar.getVisibility();
		return exposure_visibility == View.VISIBLE || manual_exposure_visibility == View.VISIBLE;
	}

    public void toggleExposureUI() {
		if( MyDebug.LOG )
			Log.d(TAG, "toggleExposureUI");
		closePopup();
		if( isExposureUIOpen() ) {
			clearSeekBar();
		}
		else if( main_activity.getPreview().getCameraController() != null ) {
			setupExposureUI();
		}
    }

    private List<View> iso_buttons;
	private int iso_button_manual_index = -1;
	private final static String manual_iso_value = "m";

    private void setupExposureUI() {
		if( MyDebug.LOG )
			Log.d(TAG, "setupExposureUI");
		test_ui_buttons.clear();
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		final Preview preview = main_activity.getPreview();
		View sliders_container = main_activity.findViewById(R.id.sliders_container);
		sliders_container.setVisibility(View.VISIBLE);
		ViewGroup iso_buttons_container = main_activity.findViewById(R.id.iso_buttons);
		iso_buttons_container.removeAllViews();
		List<String> supported_isos;
		if( preview.supportsISORange() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "supports ISO range");
			int min_iso = preview.getMinimumISO();
			int max_iso = preview.getMaximumISO();
			List<String> values = new ArrayList<>();
			values.add(CameraController.ISO_DEFAULT);
			values.add(manual_iso_value);
			iso_button_manual_index = 1; // must match where we place the manual button!
			int [] iso_values = {50, 100, 200, 400, 800, 1600, 3200, 6400};
			values.add("" + min_iso);
			for(int iso_value : iso_values) {
				if( iso_value > min_iso && iso_value < max_iso ) {
					values.add("" + iso_value);
				}
			}
			values.add("" + max_iso);
			supported_isos = values;
		}
		else {
			supported_isos = preview.getSupportedISOs();
			iso_button_manual_index = -1;
		}
		String current_iso = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
		// if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
		if( !current_iso.equals(CameraController.ISO_DEFAULT) && supported_isos != null && supported_isos.contains(manual_iso_value) && !supported_isos.contains(current_iso) )
			current_iso = manual_iso_value;
		// n.b., we hardcode the string "ISO" as this isn't a user displayed string, rather it's used to filter out "ISO" included in old Camera API parameters
		iso_buttons = PopupView.createButtonOptions(iso_buttons_container, main_activity, 280, test_ui_buttons, supported_isos, -1, -1, "ISO", false, current_iso, 0, "TEST_ISO", new PopupView.ButtonOptionsPopupListener() {
			@Override
			public void onClick(String option) {
				if( MyDebug.LOG )
					Log.d(TAG, "clicked iso: " + option);
				SharedPreferences.Editor editor = sharedPreferences.edit();
				String old_iso = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
				if( MyDebug.LOG )
					Log.d(TAG, "old_iso: " + old_iso);
				editor.putString(PreferenceKeys.ISOPreferenceKey, option);
				String toast_option = option;

				if( preview.supportsISORange() ) {
					if( option.equals(CameraController.ISO_DEFAULT) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "switched from manual to auto iso");
						// also reset exposure time when changing from manual to auto from the popup menu:
						editor.putLong(PreferenceKeys.ExposureTimePreferenceKey, CameraController.EXPOSURE_TIME_DEFAULT);
						editor.apply();
						main_activity.updateForSettings("ISO: " + toast_option);
					}
					else if( old_iso.equals(CameraController.ISO_DEFAULT) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "switched from auto to manual iso");
						if( option.equals("m") ) {
							// if we used the generic "manual", then instead try to preserve the current iso if it exists
							if( preview.getCameraController() != null && preview.getCameraController().captureResultHasIso() ) {
								int iso = preview.getCameraController().captureResultIso();
								if( MyDebug.LOG )
									Log.d(TAG, "apply existing iso of " + iso);
								editor.putString(PreferenceKeys.ISOPreferenceKey, "" + iso);
								toast_option = "" + iso;
							}
							else {
								if( MyDebug.LOG )
									Log.d(TAG, "no existing iso available");
								// use a default
								final int iso = 800;
								editor.putString(PreferenceKeys.ISOPreferenceKey, "" + iso);
								toast_option = "" + iso;
							}
						}

						// if changing from auto to manual, preserve the current exposure time if it exists
						if( preview.getCameraController() != null && preview.getCameraController().captureResultHasExposureTime() ) {
							long exposure_time = preview.getCameraController().captureResultExposureTime();
							if( MyDebug.LOG )
								Log.d(TAG, "apply existing exposure time of " + exposure_time);
							editor.putLong(PreferenceKeys.ExposureTimePreferenceKey, exposure_time);
						}
						else {
							if( MyDebug.LOG )
								Log.d(TAG, "no existing exposure time available");
						}

						editor.apply();
						main_activity.updateForSettings("ISO: " + toast_option);
					}
					else {
						if( MyDebug.LOG )
							Log.d(TAG, "changed manual iso");
						if( option.equals("m") ) {
							// if user selected the generic "manual", then just keep the previous non-ISO option
							if( MyDebug.LOG )
								Log.d(TAG, "keep existing iso of " + old_iso);
							editor.putString(PreferenceKeys.ISOPreferenceKey, "" + old_iso);
						}

						editor.apply();
						int iso = preview.parseManualISOValue(option);
						if( iso >= 0 ) {
							// if changing between manual ISOs, no need to call updateForSettings, just change the ISO directly (as with changing the ISO via manual slider)
							preview.setISO(iso);
							updateSelectedISOButton();
						}
					}
				}
				else {
					editor.apply();
					if( preview.getCameraController() != null ) {
						preview.getCameraController().setISO(option);
					}
				}

				setupExposureUI();
			}
		});
		if( supported_isos != null ) {
			View iso_container_view = main_activity.findViewById(R.id.iso_container);
			iso_container_view.setVisibility(View.VISIBLE);
		}

		View exposure_seek_bar = main_activity.findViewById(R.id.exposure_container);
		View manual_exposure_seek_bar = main_activity.findViewById(R.id.manual_exposure_container);
		String iso_value = main_activity.getApplicationInterface().getISOPref();
		if( main_activity.getPreview().usingCamera2API() && !iso_value.equals(CameraController.ISO_DEFAULT) ) {
			exposure_seek_bar.setVisibility(View.GONE);

			// with Camera2 API, when using manual ISO we instead show sliders for ISO range and exposure time
			if( main_activity.getPreview().supportsISORange() ) {
				manual_exposure_seek_bar.setVisibility(View.VISIBLE);
				SeekBar exposure_time_seek_bar = main_activity.findViewById(R.id.exposure_time_seekbar);
				if( main_activity.getPreview().supportsExposureTime() ) {
					exposure_time_seek_bar.setVisibility(View.VISIBLE);
				}
				else {
					exposure_time_seek_bar.setVisibility(View.GONE);
				}
			}
			else {
				manual_exposure_seek_bar.setVisibility(View.GONE);
			}
		}
		else {
			manual_exposure_seek_bar.setVisibility(View.GONE);

			if( main_activity.getPreview().supportsExposures() ) {
				exposure_seek_bar.setVisibility(View.VISIBLE);
				ZoomControls seek_bar_zoom = main_activity.findViewById(R.id.exposure_seekbar_zoom);
				seek_bar_zoom.setVisibility(View.VISIBLE);
			}
			else {
				exposure_seek_bar.setVisibility(View.GONE);
			}
		}

		View manual_white_balance_seek_bar = main_activity.findViewById(R.id.manual_white_balance_container);
		if( main_activity.getPreview().supportsWhiteBalanceTemperature()) {
			// we also show slider for manual white balance, if in that mode
			String white_balance_value = main_activity.getApplicationInterface().getWhiteBalancePref();
			if( main_activity.getPreview().usingCamera2API() && white_balance_value.equals("manual") ) {
				manual_white_balance_seek_bar.setVisibility(View.VISIBLE);
			}
			else {
				manual_white_balance_seek_bar.setVisibility(View.GONE);
			}
		}
		else {
			manual_white_balance_seek_bar.setVisibility(View.GONE);
		}
	}

	/** If the exposure panel is open, updates the selected ISO button to match the current ISO value,
	 *  if a continuous range of ISO values are supported by the camera.
	 */
	public void updateSelectedISOButton() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateSelectedISOButton");
		Preview preview = main_activity.getPreview();
		if( preview.supportsISORange() && isExposureUIOpen() ) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
			String current_iso = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
			// if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
			if( MyDebug.LOG )
				Log.d(TAG, "current_iso: " + current_iso);
			boolean found = false;
			for(View view : iso_buttons) {
				Button button = (Button)view;
				if( MyDebug.LOG )
					Log.d(TAG, "button: " + button.getText());
				String button_text = "" + button.getText();
				if( button_text.contains(current_iso) ) {
					PopupView.setButtonSelected(button, true);
					found = true;
				}
				else {
					PopupView.setButtonSelected(button, false);
				}
			}
			if( !found && !current_iso.equals(CameraController.ISO_DEFAULT) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "must be manual");
				if( iso_button_manual_index >= 0 && iso_button_manual_index < iso_buttons.size() ) {
					Button button = (Button)iso_buttons.get(iso_button_manual_index);
					PopupView.setButtonSelected(button, true);
				}
			}
		}
	}

	public void setSeekbarZoom(int new_zoom) {
		if( MyDebug.LOG )
			Log.d(TAG, "setSeekbarZoom: " + new_zoom);
	    SeekBar zoomSeekBar = main_activity.findViewById(R.id.zoom_seekbar);
		if( MyDebug.LOG )
			Log.d(TAG, "progress was: " + zoomSeekBar.getProgress());
		zoomSeekBar.setProgress(main_activity.getPreview().getMaxZoom()-new_zoom);
		if( MyDebug.LOG )
			Log.d(TAG, "progress is now: " + zoomSeekBar.getProgress());
	}
	
	public void changeSeekbar(int seekBarId, int change) {
		if( MyDebug.LOG )
			Log.d(TAG, "changeSeekbar: " + change);
		SeekBar seekBar = main_activity.findViewById(seekBarId);
	    int value = seekBar.getProgress();
	    int new_value = value + change;
	    if( new_value < 0 )
	    	new_value = 0;
	    else if( new_value > seekBar.getMax() )
	    	new_value = seekBar.getMax();
		if( MyDebug.LOG ) {
			Log.d(TAG, "value: " + value);
			Log.d(TAG, "new_value: " + new_value);
			Log.d(TAG, "max: " + seekBar.getMax());
		}
	    if( new_value != value ) {
		    seekBar.setProgress(new_value);
	    }
	}

    public void clearSeekBar() {
		View view = main_activity.findViewById(R.id.sliders_container);
		view.setVisibility(View.GONE);
		view = main_activity.findViewById(R.id.iso_container);
		view.setVisibility(View.GONE);
		view = main_activity.findViewById(R.id.exposure_container);
		view.setVisibility(View.GONE);
		view = main_activity.findViewById(R.id.manual_exposure_container);
		view.setVisibility(View.GONE);
		view = main_activity.findViewById(R.id.manual_white_balance_container);
		view.setVisibility(View.GONE);
    }
    
    public void setPopupIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPopupIcon");
		ImageButton popup = main_activity.findViewById(R.id.popup);
		String flash_value = main_activity.getPreview().getCurrentFlashValue();
		if( MyDebug.LOG )
			Log.d(TAG, "flash_value: " + flash_value);
    	if( flash_value != null && flash_value.equals("flash_off") ) {
			popup.setImageResource(R.drawable.popup_flash_off);
    	}
    	else if( flash_value != null && ( flash_value.equals("flash_torch") || flash_value.equals("flash_frontscreen_torch") ) ) {
    		popup.setImageResource(R.drawable.popup_flash_torch);
    	}
		else if( flash_value != null && ( flash_value.equals("flash_auto") || flash_value.equals("flash_frontscreen_auto") ) ) {
    		popup.setImageResource(R.drawable.popup_flash_auto);
    	}
		else if( flash_value != null && ( flash_value.equals("flash_on") || flash_value.equals("flash_frontscreen_on") ) ) {
    		popup.setImageResource(R.drawable.popup_flash_on);
    	}
    	else if( flash_value != null && flash_value.equals("flash_red_eye") ) {
    		popup.setImageResource(R.drawable.popup_flash_red_eye);
    	}
    	else {
    		popup.setImageResource(R.drawable.popup);
    	}
    }

    public void closePopup() {
		if( MyDebug.LOG )
			Log.d(TAG, "close popup");
		if( popupIsOpen() ) {
			popup_view_is_open = false;
			/* Not destroying the popup doesn't really gain any performance.
			 * Also there are still outstanding bugs to fix if we wanted to do this:
			 *   - Not resetting the popup menu when switching between photo and video mode. See test testVideoPopup().
			 *   - When changing options like flash/focus, the new option isn't selected when reopening the popup menu. See test
			 *     testPopup().
			 *   - Changing settings potentially means we have to recreate the popup, so the natural place to do this is in
			 *     MainActivity.updateForSettings(), but doing so makes the popup close when checking photo or video resolutions!
			 *     See test testSwitchResolution().
			 */
			if( cache_popup && !force_destroy_popup ) {
				popup_view.setVisibility(View.GONE);
			}
			else {
				destroyPopup();
			}
			main_activity.initImmersiveMode(); // to reset the timer when closing the popup
		}
    }

    public boolean popupIsOpen() {
    	return popup_view_is_open;
    }
    
    public void destroyPopup() {
		if( MyDebug.LOG )
			Log.d(TAG, "destroyPopup");
		force_destroy_popup = false;
		if( popupIsOpen() ) {
			closePopup();
		}
		ViewGroup popup_container = main_activity.findViewById(R.id.popup_container);
		popup_container.removeAllViews();
		popup_view = null;
    }

    public void togglePopupSettings() {
		final ViewGroup popup_container = main_activity.findViewById(R.id.popup_container);
		if( popupIsOpen() ) {
			closePopup();
			return;
		}
		if( main_activity.getPreview().getCameraController() == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "open popup");

		clearSeekBar();
		main_activity.getPreview().cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
		main_activity.stopAudioListeners();

    	final long time_s = System.currentTimeMillis();

    	{
			// prevent popup being transparent
			popup_container.setBackgroundColor(Color.BLACK);
			popup_container.setAlpha(0.9f);
		}

    	if( popup_view == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "create new popup_view");
			test_ui_buttons.clear();
    		popup_view = new PopupView(main_activity);
			popup_container.addView(popup_view);
    	}
    	else {
			if( MyDebug.LOG )
				Log.d(TAG, "use cached popup_view");
			popup_view.setVisibility(View.VISIBLE);
    	}
		popup_view_is_open = true;
		
        // need to call layoutUI to make sure the new popup is oriented correctly
		// but need to do after the layout has been done, so we have a valid width/height to use
		// n.b., even though we only need the portion of layoutUI for the popup container, there
		// doesn't seem to be any performance benefit in only calling that part
		popup_container.getViewTreeObserver().addOnGlobalLayoutListener(
			new OnGlobalLayoutListener() {
				@SuppressWarnings("deprecation")
				@Override
			    public void onGlobalLayout() {
					if( MyDebug.LOG )
						Log.d(TAG, "onGlobalLayout()");
					if( MyDebug.LOG )
						Log.d(TAG, "time after global layout: " + (System.currentTimeMillis() - time_s));
					layoutUI(true);
					if( MyDebug.LOG )
						Log.d(TAG, "time after layoutUI: " + (System.currentTimeMillis() - time_s));
		    		// stop listening - only want to call this once!
		            if( Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 ) {
		            	popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		            }
		            else {
		            	popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		            }

		    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		    		String ui_placement = sharedPreferences.getString(PreferenceKeys.UIPlacementPreferenceKey, "ui_right");
		    		boolean ui_placement_right = ui_placement.equals("ui_right");
		            ScaleAnimation animation = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, ui_placement_right ? 0.0f : 1.0f);
		    		animation.setDuration(100);
		    		popup_container.setAnimation(animation);
		        }
			}
		);

		if( MyDebug.LOG )
			Log.d(TAG, "time to create popup: " + (System.currentTimeMillis() - time_s));
    }

	@SuppressWarnings("deprecation")
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyDown: " + keyCode);
		switch( keyCode ) {
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS: // media codes are for "selfie sticks" buttons
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_MEDIA_STOP:
			{
				if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
					keydown_volume_up = true;
				else if( keyCode == KeyEvent.KEYCODE_VOLUME_DOWN )
					keydown_volume_down = true;

				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
				String volume_keys = sharedPreferences.getString(PreferenceKeys.VolumeKeysPreferenceKey, "volume_take_photo");

				if((keyCode==KeyEvent.KEYCODE_MEDIA_PREVIOUS
						||keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
						||keyCode==KeyEvent.KEYCODE_MEDIA_STOP)
						&&!(volume_keys.equals("volume_take_photo"))) {
					AudioManager audioManager = (AudioManager) main_activity.getSystemService(Context.AUDIO_SERVICE);
					if(audioManager==null) break;
					if(!audioManager.isWiredHeadsetOn()) break; // isWiredHeadsetOn() is deprecated, but comment says "Use only to check is a headset is connected or not."
				}

				switch(volume_keys) {
					case "volume_take_photo":
						main_activity.takePicture(false);
						return true;
					case "volume_focus":
						if(keydown_volume_up && keydown_volume_down) {
							if (MyDebug.LOG)
								Log.d(TAG, "take photo rather than focus, as both volume keys are down");
							main_activity.takePicture(false);
						}
						else if (main_activity.getPreview().getCurrentFocusValue() != null && main_activity.getPreview().getCurrentFocusValue().equals("focus_mode_manual2")) {
							if(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
								main_activity.changeFocusDistance(-1, false);
							else
								main_activity.changeFocusDistance(1, false);
						}
						else {
							// important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel, as causes problem if key is held down (e.g., flash gets stuck on)
							// also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down
							if(event.getDownTime() == event.getEventTime() && !main_activity.getPreview().isFocusWaiting()) {
								if(MyDebug.LOG)
									Log.d(TAG, "request focus due to volume key");
								main_activity.getPreview().requestAutoFocus();
							}
						}
						return true;
					case "volume_zoom":
						if(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
							main_activity.zoomIn();
						else
							main_activity.zoomOut();
						return true;
					case "volume_exposure":
						if(main_activity.getPreview().getCameraController() != null) {
							String value = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
							boolean manual_iso = !value.equals(CameraController.ISO_DEFAULT);
							if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
								if(manual_iso) {
									if(main_activity.getPreview().supportsISORange())
										main_activity.changeISO(1);
								}
								else
									main_activity.changeExposure(1);
							}
							else {
								if(manual_iso) {
									if(main_activity.getPreview().supportsISORange())
										main_activity.changeISO(-1);
								}
								else
									main_activity.changeExposure(-1);
							}
						}
						return true;
					case "volume_auto_stabilise":
						if( main_activity.supportsAutoStabilise() ) {
							boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false);
							auto_stabilise = !auto_stabilise;
							SharedPreferences.Editor editor = sharedPreferences.edit();
							editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, auto_stabilise);
							editor.apply();
							String message = main_activity.getResources().getString(R.string.preference_auto_stabilise) + ": " + main_activity.getResources().getString(auto_stabilise ? R.string.on : R.string.off);
							main_activity.getPreview().showToast(main_activity.getChangedAutoStabiliseToastBoxer(), message);
							main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // because we cache the auto-stabilise setting
		    				this.destroyPopup(); // need to recreate popup in order to update the auto-level checkbox
						}
						else {
							main_activity.getPreview().showToast(main_activity.getChangedAutoStabiliseToastBoxer(), R.string.auto_stabilise_not_supported);
						}
						return true;
					case "volume_really_nothing":
						// do nothing, but still return true so we don't change volume either
						return true;
				}
				// else do nothing here, but still allow changing of volume (i.e., the default behaviour)
				break;
			}
			case KeyEvent.KEYCODE_MENU:
			{
				// needed to support hardware menu button
				// tested successfully on Samsung S3 (via RTL)
				// see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
				main_activity.openSettings();
				return true;
			}
			case KeyEvent.KEYCODE_CAMERA:
			{
				if( event.getRepeatCount() == 0 ) {
					main_activity.takePicture(false);
					return true;
				}
			}
			case KeyEvent.KEYCODE_FOCUS:
			{
				// important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel - causes problem with hardware camera key where a half-press means to focus
				// also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down - see https://sourceforge.net/p/opencamera/tickets/174/ ,
				// or same issue above for volume key focus
				if( event.getDownTime() == event.getEventTime() && !main_activity.getPreview().isFocusWaiting() ) {
					if( MyDebug.LOG )
						Log.d(TAG, "request focus due to focus key");
					main_activity.getPreview().requestAutoFocus();
				}
				return true;
			}
			case KeyEvent.KEYCODE_ZOOM_IN:
			{
				main_activity.zoomIn();
				return true;
			}
			case KeyEvent.KEYCODE_ZOOM_OUT:
			{
				main_activity.zoomOut();
				return true;
			}
		}
		return false;
	}

	public void onKeyUp(int keyCode, KeyEvent event) {
		if( MyDebug.LOG )
			Log.d(TAG, "onKeyUp: " + keyCode);
		if( keyCode == KeyEvent.KEYCODE_VOLUME_UP )
			keydown_volume_up = false;
		else if( keyCode == KeyEvent.KEYCODE_VOLUME_DOWN )
			keydown_volume_down = false;
	}

	/** Shows an information dialog, with a button to request not to show again.
	 *  Note it's up to the caller to check whether the info_preference_key (to not show again) was
	 *  already set.
	 * @param title_id Resource id for title string.
	 * @param info_id Resource id for dialog text string.
	 * @param info_preference_key Preference key to set in SharedPreferences if the user selects to
	 *                            not show the dialog again.
	 * @return The AlertDialog that was created.
	 */
	public AlertDialog showInfoDialog(int title_id, int info_id, final String info_preference_key) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(main_activity);
        alertDialog.setTitle(title_id);
        if( info_id != 0 )
        	alertDialog.setMessage(info_id);
        alertDialog.setPositiveButton(android.R.string.ok, null);
        alertDialog.setNegativeButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "user clicked dont_show_again for info dialog");
				final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        		SharedPreferences.Editor editor = sharedPreferences.edit();
        		editor.putBoolean(info_preference_key, true);
        		editor.apply();
			}
        });

		main_activity.showPreview(false);
		main_activity.setWindowFlagsForSettings();

		AlertDialog alert = alertDialog.create();
		// AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
		alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface arg0) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "info dialog dismissed");
        		main_activity.setWindowFlagsForCamera();
        		main_activity.showPreview(true);
			}
        });
		main_activity.showAlert(alert);
		return alert;
    }

	/** Returns a (possibly translated) user readable string for a white balance preference value.
	 *  If the value is not recognised (this can happen for the old Camera API, some devices can
	 *  have device-specific options), then the received value is returned.
	 */
	public String getEntryForWhiteBalance(String value) {
		int id = -1;
		switch( value ) {
			case CameraController.WHITE_BALANCE_DEFAULT:
				id = R.string.white_balance_auto;
				break;
			case "cloudy-daylight":
				id = R.string.white_balance_cloudy;
				break;
			case "daylight":
				id = R.string.white_balance_daylight;
				break;
			case "fluorescent":
				id = R.string.white_balance_fluorescent;
				break;
			case "incandescent":
				id = R.string.white_balance_incandescent;
				break;
			case "shade":
				id = R.string.white_balance_shade;
				break;
			case "twilight":
				id = R.string.white_balance_twilight;
				break;
			case "warm-fluorescent":
				id = R.string.white_balance_warm;
				break;
			case "manual":
				id = R.string.white_balance_manual;
				break;
			default:
				break;
		}
		String entry;
		if( id != -1 ) {
			entry = main_activity.getResources().getString(id);
		}
		else {
			entry = value;
		}
		return entry;
	}

	/** Returns a (possibly translated) user readable string for a scene mode preference value.
	 *  If the value is not recognised (this can happen for the old Camera API, some devices can
	 *  have device-specific options), then the received value is returned.
	 */
	public String getEntryForSceneMode(String value) {
		int id = -1;
		switch( value ) {
			case "action":
				id = R.string.scene_mode_action;
				break;
			case "barcode":
				id = R.string.scene_mode_barcode;
				break;
			case "beach":
				id = R.string.scene_mode_beach;
				break;
			case "candlelight":
				id = R.string.scene_mode_candlelight;
				break;
			case CameraController.SCENE_MODE_DEFAULT:
				id = R.string.scene_mode_auto;
				break;
			case "fireworks":
				id = R.string.scene_mode_fireworks;
				break;
			case "landscape":
				id = R.string.scene_mode_landscape;
				break;
			case "night":
				id = R.string.scene_mode_night;
				break;
			case "night-portrait":
				id = R.string.scene_mode_night_portrait;
				break;
			case "party":
				id = R.string.scene_mode_party;
				break;
			case "portrait":
				id = R.string.scene_mode_portrait;
				break;
			case "snow":
				id = R.string.scene_mode_snow;
				break;
			case "sports":
				id = R.string.scene_mode_sports;
				break;
			case "steadyphoto":
				id = R.string.scene_mode_steady_photo;
				break;
			case "sunset":
				id = R.string.scene_mode_sunset;
				break;
			case "theatre":
				id = R.string.scene_mode_theatre;
				break;
			default:
				break;
		}
		String entry;
		if( id != -1 ) {
			entry = main_activity.getResources().getString(id);
		}
		else {
			entry = value;
		}
		return entry;
	}

	/** Returns a (possibly translated) user readable string for a color effect preference value.
	 *  If the value is not recognised (this can happen for the old Camera API, some devices can
	 *  have device-specific options), then the received value is returned.
	 */
	public String getEntryForColorEffect(String value) {
		int id = -1;
		switch( value ) {
			case "aqua":
				id = R.string.color_effect_aqua;
				break;
			case "blackboard":
				id = R.string.color_effect_blackboard;
				break;
			case "mono":
				id = R.string.color_effect_mono;
				break;
			case "negative":
				id = R.string.color_effect_negative;
				break;
			case CameraController.COLOR_EFFECT_DEFAULT:
				id = R.string.color_effect_none;
				break;
			case "posterize":
				id = R.string.color_effect_posterize;
				break;
			case "sepia":
				id = R.string.color_effect_sepia;
				break;
			case "solarize":
				id = R.string.color_effect_solarize;
				break;
			case "whiteboard":
				id = R.string.color_effect_whiteboard;
				break;
			default:
				break;
		}
		String entry;
		if( id != -1 ) {
			entry = main_activity.getResources().getString(id);
		}
		else {
			entry = value;
		}
		return entry;
	}

	/** Returns a (possibly translated) user readable string for an antibanding preference value.
	 *  If the value is not recognised, then the received value is returned.
	 */
	public String getEntryForAntiBanding(String value) {
		int id = -1;
		switch( value ) {
			case CameraController.ANTIBANDING_DEFAULT:
				id = R.string.anti_banding_auto;
				break;
			case "50hz":
				id = R.string.anti_banding_50hz;
				break;
			case "60hz":
				id = R.string.anti_banding_60hz;
				break;
			case "off":
				id = R.string.anti_banding_off;
				break;
			default:
				break;
		}
		String entry;
		if( id != -1 ) {
			entry = main_activity.getResources().getString(id);
		}
		else {
			entry = value;
		}
		return entry;
	}

	/** Returns a (possibly translated) user readable string for an noise reduction mode preference value.
	 *  If the value is not recognised, then the received value is returned.
	 *  Also used for edge mode.
	 */
	public String getEntryForNoiseReductionMode(String value) {
		int id = -1;
		switch( value ) {
			case CameraController.NOISE_REDUCTION_MODE_DEFAULT:
				id = R.string.noise_reduction_mode_default;
				break;
			case "off":
				id = R.string.noise_reduction_mode_off;
				break;
			case "minimal":
				id = R.string.noise_reduction_mode_minimal;
				break;
			case "fast":
				id = R.string.noise_reduction_mode_fast;
				break;
			case "high_quality":
				id = R.string.noise_reduction_mode_high_quality;
				break;
			default:
				break;
		}
		String entry;
		if( id != -1 ) {
			entry = main_activity.getResources().getString(id);
		}
		else {
			entry = value;
		}
		return entry;
	}

    // for testing
    public View getUIButton(String key) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "getPopupButton(" + key + "): " + test_ui_buttons.get(key));
			Log.d(TAG, "this: " + this);
			Log.d(TAG, "popup_buttons: " + test_ui_buttons);
		}
    	return test_ui_buttons.get(key);
    }

	Map<String, View> getTestUIButtonsMap() {
		return test_ui_buttons;
	}

    public PopupView getPopupView() {
		return popup_view;
	}
}
