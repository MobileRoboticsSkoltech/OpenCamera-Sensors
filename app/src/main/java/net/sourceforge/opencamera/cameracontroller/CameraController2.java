package net.sourceforge.opencamera.cameracontroller;

import net.sourceforge.opencamera.MyDebug;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.location.Location;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.SizeF;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

/** Provides support using Android 5's Camera 2 API
 *  android.hardware.camera2.*.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraController2 extends CameraController {
    private static final String TAG = "CameraController2";

    private final Context context;
    private CameraDevice camera;
    private String cameraIdS;

    private final boolean is_samsung;
    private final boolean is_samsung_s7; // Galaxy S7 or Galaxy S7 Edge

    private CameraCharacteristics characteristics;
    // cached characteristics (use this for values that need to be frequently accessed, e.g., per frame, to improve performance);
    private int characteristics_sensor_orientation;
    private Facing characteristics_facing;

    private List<Integer> zoom_ratios;
    private int current_zoom_value;
    private boolean supports_face_detect_mode_simple;
    private boolean supports_face_detect_mode_full;
    private boolean supports_optical_stabilization;
    private boolean supports_photo_video_recording;
    private boolean supports_white_balance_temperature;

    private final static int tonemap_log_max_curve_points_c = 64;
    private final static float [] jtvideo_values_base = new float[] {
            0.00f,    0.00f,
            0.01f,    0.055f,
            0.02f,    0.1f,
            0.05f,    0.21f,
            0.09f,    0.31f,
            0.13f,    0.38f,
            0.18f,    0.45f,
            0.28f,    0.57f,
            0.35f,    0.64f,
            0.45f,    0.72f,
            0.51f,    0.76f,
            0.60f,    0.82f,
            0.67f,    0.86f,
            0.77f,    0.91f,
            0.88f,    0.96f,
            0.97f,    0.99f,
            1.00f,    1.00f
    };
    private final float [] jtvideo_values;
    private final static float [] jtlog_values_base = new float[] {
            0.00f,    0.00f,
            0.01f,    0.07f,
            0.03f,    0.17f,
            0.05f,    0.25f,
            0.07f,    0.31f,
            0.09f,    0.36f,
            0.13f,    0.44f,
            0.18f,    0.51f,
            0.24f,    0.57f,
            0.31f,    0.64f,
            0.38f,    0.70f,
            0.46f,    0.76f,
            0.58f,    0.83f,
            0.70f,    0.89f,
            0.86f,    0.95f,
            0.99f,    0.99f,
            1.00f,    1.00f
    };
    private final float [] jtlog_values;
    private final static float [] jtlog2_values_base = new float[] {
            0.00f,    0.00f,
            0.01f,    0.09f,
            0.03f,    0.23f,
            0.07f,    0.37f,
            0.12f,    0.48f,
            0.17f,    0.56f,
            0.25f,    0.64f,
            0.32f,    0.70f,
            0.39f,    0.75f,
            0.50f,    0.81f,
            0.59f,    0.85f,
            0.66f,    0.88f,
            0.72f,    0.9f,
            0.78f,    0.92f,
            0.88f,    0.95f,
            0.92f,    0.96f,
            0.99f,    0.98f,
            1.00f,    1.00f
    };
    private final float [] jtlog2_values;

    private final ErrorCallback preview_error_cb;
    private final ErrorCallback camera_error_cb;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private boolean previewIsVideoMode;
    private AutoFocusCallback autofocus_cb;
    private boolean capture_follows_autofocus_hint;
    private FaceDetectionListener face_detection_listener;
    private int last_faces_detected = -1;
    private final Object open_camera_lock = new Object(); // lock to wait for camera to be opened from CameraDevice.StateCallback
    private final Object background_camera_lock = new Object(); // lock to synchronize between UI thread and the background "CameraBackground" thread/handler

    private ImageReader imageReader;

    private BurstType burst_type = BurstType.BURSTTYPE_NONE;
    // for BURSTTYPE_EXPO:
    private final static int max_expo_bracketing_n_images = 5; // could be more, but limit to 5 for now
    private int expo_bracketing_n_images = 3;
    private double expo_bracketing_stops = 2.0;
    private boolean use_expo_fast_burst = true;
    // for BURSTTYPE_FOCUS:
    private boolean focus_bracketing_in_progress; // whether focus bracketing in progress; set back to false to cancel
    private int focus_bracketing_n_images = 3;
    private float focus_bracketing_source_distance = 0.0f;
    private float focus_bracketing_target_distance = 0.0f;
    private boolean focus_bracketing_add_infinity = false;
    // for BURSTTYPE_NORMAL:
    private boolean burst_for_noise_reduction; // chooses number of burst images and other settings for Open Camera's noise reduction (NR) photo mode
    private boolean noise_reduction_low_light; // if burst_for_noise_reduction==true, whether to optimise for low light scenes
    private int burst_requested_n_images; // if burst_for_noise_reduction==false, this gives the number of images for the burst
    //for BURSTTYPE_CONTINUOUS:
    private boolean continuous_burst_in_progress; // whether we're currently taking a continuous burst
    private boolean continuous_burst_requested_last_capture; // whether we've requested the last capture

    private boolean optimise_ae_for_dro = false;
    private boolean want_raw;
    //private boolean want_raw = true;
    private int max_raw_images;
    private android.util.Size raw_size;
    private ImageReader imageReaderRaw;
    private OnRawImageAvailableListener onRawImageAvailableListener;
    private PictureCallback picture_cb;
    private boolean jpeg_todo; // whether we are still waiting for JPEG images
    private boolean raw_todo; // whether we are still waiting for RAW images
    private boolean done_all_captures; // whether we've received the capture for the image (or all images if a burst)
    //private CaptureRequest pending_request_when_ready;
    private int n_burst; // number of expected (remaining) burst JPEG images in this capture
    private int n_burst_taken; // number of burst JPEG images taken so far in this capture
    private int n_burst_total; // total number of expected burst images in this capture (if known) (same for JPEG and RAW)
    private int n_burst_raw; // number of expected (remaining) burst RAW images in this capture
    private boolean burst_single_request; // if true then the burst images are returned in a single call to onBurstPictureTaken(), if false, then multiple calls to onPictureTaken() are made as soon as the image is available
    private final List<byte []> pending_burst_images = new ArrayList<>(); // burst images that have been captured so far, but not yet sent to the application
    private final List<RawImage> pending_burst_images_raw = new ArrayList<>();
    private List<CaptureRequest> slow_burst_capture_requests; // the set of burst capture requests - used when not using captureBurst() (e.g., when use_expo_fast_burst==false, or for focus bracketing)
    private long slow_burst_start_ms = 0; // time when burst started (used for measuring performance of captures when not using captureBurst())
    private RawImage pending_raw_image; // used to ensure that when taking JPEG+RAW, the JPEG picture callback is called first (only used for non-burst cases)
    private ErrorCallback take_picture_error_cb;
    private boolean want_video_high_speed;
    private boolean is_video_high_speed; // whether we're actually recording in high speed
    private List<int[]> ae_fps_ranges;
    private List<int[]> hs_fps_ranges;
    //private ImageReader previewImageReader;
    private SurfaceTexture texture;
    private Surface surface_texture;
    private HandlerThread thread;
    private Handler handler;
    private Surface video_recorder_surface;

    private int preview_width;
    private int preview_height;
    
    private int picture_width;
    private int picture_height;
    
    private static final int STATE_NORMAL = 0;
    private static final int STATE_WAITING_AUTOFOCUS = 1;
    private static final int STATE_WAITING_PRECAPTURE_START = 2;
    private static final int STATE_WAITING_PRECAPTURE_DONE = 3;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_START = 4;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_DONE = 5;
    private int state = STATE_NORMAL;
    private long precapture_state_change_time_ms = -1; // time we changed state for precapture modes
    private static final long precapture_start_timeout_c = 2000;
    private static final long precapture_done_timeout_c = 3000;
    private boolean ready_for_capture;

    private boolean use_fake_precapture; // see CameraController.setUseCamera2FakeFlash() for details - this is the user/application setting, see use_fake_precapture_mode for whether fake precapture is enabled (as we may do this for other purposes, e.g., front screen flash)
    private boolean use_fake_precapture_mode; // true if either use_fake_precapture is true, or we're temporarily using fake precapture mode (e.g., for front screen flash or exposure bracketing)
    private boolean fake_precapture_torch_performed; // whether we turned on torch to do a fake precapture
    private boolean fake_precapture_torch_focus_performed; // whether we turned on torch to do an autofocus, in fake precapture mode
    private boolean fake_precapture_use_flash; // whether we decide to use flash in auto mode (if fake_precapture_use_autoflash_time_ms != -1)
    private long fake_precapture_use_flash_time_ms = -1; // when we last checked to use flash in auto mode

    private ContinuousFocusMoveCallback continuous_focus_move_callback;
    
    private final MediaActionSound media_action_sound = new MediaActionSound();
    private boolean sounds_enabled = true;

    private boolean has_received_frame;
    private boolean capture_result_is_ae_scanning;
    private Integer capture_result_ae; // latest ae_state, null if not available
    private boolean is_flash_required; // whether capture_result_ae suggests FLASH_REQUIRED? Or in neither FLASH_REQUIRED nor CONVERGED, this stores the last known result
    private boolean modified_from_camera_settings;
        // if modified_from_camera_settings set to true, then we've temporarily requested captures with settings such as
        // exposure modified from the normal ones in camera_settings
    private boolean capture_result_has_white_balance_rggb;
    private RggbChannelVector capture_result_white_balance_rggb;
    private boolean capture_result_has_iso;
    private int capture_result_iso;
    private boolean capture_result_has_exposure_time;
    private long capture_result_exposure_time;
    private boolean capture_result_has_frame_duration;
    private long capture_result_frame_duration;
    private boolean capture_result_has_aperture;
    private float capture_result_aperture;
    /*private boolean capture_result_has_focus_distance;
    private float capture_result_focus_distance_min;
    private float capture_result_focus_distance_max;*/
    private final static long max_preview_exposure_time_c = 1000000000L/12;
    
    private enum RequestTagType {
        CAPTURE, // request is either for a regular non-burst capture, or the last of a burst capture sequence
        CAPTURE_BURST_IN_PROGRESS // request is for a burst capture, but isn't the last of the burst capture sequence
        //NONE // should be treated the same as if no tag had been set on the request - but allows the request tag type to be changed later
    }

    /* The class that we use for setTag() and getTag() for capture requests.
       We use this class instead of assigning the RequestTagType directly, so we can modify it
       (even though CaptureRequest only has a getTag() method).
     */
    private static class RequestTagObject {
        private RequestTagType type;

        private RequestTagObject(RequestTagType type) {
            this.type = type;
        }

        private RequestTagType getType() {
            return type;
        }

        private void setType(RequestTagType type) {
            this.type = type;
        }
    }

    private final static int min_white_balance_temperature_c = 1000;
    private final static int max_white_balance_temperature_c = 15000;

    private class CameraSettings {
        // keys that we need to store, to pass to the stillBuilder, but doesn't need to be passed to previewBuilder (should set sensible defaults)
        private int rotation;
        private Location location;
        private byte jpeg_quality = 90;

        // keys that we have passed to the previewBuilder, that we need to store to also pass to the stillBuilder (should set sensible defaults, or use a has_ boolean if we don't want to set a default)
        private int scene_mode = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
        private int color_effect = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
        private int white_balance = CameraMetadata.CONTROL_AWB_MODE_AUTO;
        private boolean has_antibanding;
        private int antibanding = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO;
        private boolean has_edge_mode;
        private int edge_mode = CameraMetadata.EDGE_MODE_FAST;
        private Integer default_edge_mode;
        private boolean has_noise_reduction_mode;
        private int noise_reduction_mode = CameraMetadata.NOISE_REDUCTION_MODE_FAST;
        private Integer default_noise_reduction_mode;
        private int white_balance_temperature = 5000; // used for white_balance == CONTROL_AWB_MODE_OFF
        private String flash_value = "flash_off";
        private boolean has_iso;
        //private int ae_mode = CameraMetadata.CONTROL_AE_MODE_ON;
        //private int flash_mode = CameraMetadata.FLASH_MODE_OFF;
        private int iso;
        private long exposure_time = EXPOSURE_TIME_DEFAULT;
        private boolean has_aperture;
        private float aperture;
        private Rect scalar_crop_region; // no need for has_scalar_crop_region, as we can set to null instead
        private boolean has_ae_exposure_compensation;
        private int ae_exposure_compensation;
        private boolean has_af_mode;
        private int af_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        private float focus_distance; // actual value passed to camera device (set to 0.0 if in infinity mode)
        private float focus_distance_manual; // saved setting when in manual mode (so if user switches to infinity mode and back, we'll still remember the manual focus distance)
        private boolean ae_lock;
        private boolean wb_lock;
        private MeteringRectangle [] af_regions; // no need for has_scalar_crop_region, as we can set to null instead
        private MeteringRectangle [] ae_regions; // no need for has_scalar_crop_region, as we can set to null instead
        private boolean has_face_detect_mode;
        private int face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        private Integer default_optical_stabilization;
        private boolean video_stabilization;
        private TonemapProfile tonemap_profile = TonemapProfile.TONEMAPPROFILE_OFF;
        private float log_profile_strength; // for TONEMAPPROFILE_LOG
        private float gamma_profile; // for TONEMAPPROFILE_GAMMA
        private Integer default_tonemap_mode; // since we don't know what a device's tonemap mode is, we save it so we can switch back to it
        private Range<Integer> ae_target_fps_range;
        private long sensor_frame_duration;

        private int getExifOrientation() {
            int exif_orientation = ExifInterface.ORIENTATION_NORMAL;
            switch( (rotation + 360) % 360 ) {
                case 0:
                    exif_orientation = ExifInterface.ORIENTATION_NORMAL;
                    break;
                case 90:
                    exif_orientation = (getFacing() == Facing.FACING_FRONT) ?
                            ExifInterface.ORIENTATION_ROTATE_270 :
                            ExifInterface.ORIENTATION_ROTATE_90;
                    break;
                case 180:
                    exif_orientation = ExifInterface.ORIENTATION_ROTATE_180;
                    break;
                case 270:
                    exif_orientation = (getFacing() == Facing.FACING_FRONT) ?
                            ExifInterface.ORIENTATION_ROTATE_90 :
                            ExifInterface.ORIENTATION_ROTATE_270;
                    break;
                default:
                    // leave exif_orientation unchanged
                    if( MyDebug.LOG )
                        Log.e(TAG, "unexpected rotation: " + rotation);
                    break;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "rotation: " + rotation);
                Log.d(TAG, "exif_orientation: " + exif_orientation);
            }
            return exif_orientation;
        }

        private void setupBuilder(CaptureRequest.Builder builder, boolean is_still) {
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);

            setSceneMode(builder);
            setColorEffect(builder);
            setWhiteBalance(builder);
            setAntiBanding(builder);
            setAEMode(builder, is_still);
            setCropRegion(builder);
            setExposureCompensation(builder);
            setFocusMode(builder);
            setFocusDistance(builder);
            setAutoExposureLock(builder);
            setAutoWhiteBalanceLock(builder);
            setAFRegions(builder);
            setAERegions(builder);
            setFaceDetectMode(builder);
            setRawMode(builder);
            setStabilization(builder);
            setTonemapProfile(builder);

            if( is_still ) {
                if( location != null ) {
                    builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
                }
                builder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
                builder.set(CaptureRequest.JPEG_QUALITY, jpeg_quality);
            }

            setEdgeMode(builder);
            setNoiseReductionMode(builder);

            /*builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF);
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF);*/

            /*builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE);
                builder.set(CaptureRequest.TONEMAP_GAMMA, 5.0f);
            }*/
            /*if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N ) {
                builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 0);
            }*/
            /*builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF);
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);*/
            /*if( MyDebug.LOG ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
                TonemapCurve original_curve = builder.get(CaptureRequest.TONEMAP_CURVE);
                for(int c=0;c<3;c++) {
                    Log.d(TAG, "color c = " + c);
                    for(int i=0;i<original_curve.getPointCount(c);i++) {
                        PointF point = original_curve.getPoint(c, i);
                        Log.d(TAG, "    i = " + i);
                        Log.d(TAG, "        in: " + point.x);
                        Log.d(TAG, "        out: " + point.y);
                    }
                }
            }*/
            /*if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ) {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);
            }*/

            if( MyDebug.LOG ) {
                if( is_still ) {
                    Integer nr_mode = builder.get(CaptureRequest.NOISE_REDUCTION_MODE);
                    Log.d(TAG, "nr_mode: " + (nr_mode==null ? "null" : nr_mode));
                    Integer edge_mode = builder.get(CaptureRequest.EDGE_MODE);
                    Log.d(TAG, "edge_mode: " + (edge_mode==null ? "null" : edge_mode));
                    Integer cc_mode = builder.get(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE);
                    Log.d(TAG, "cc_mode: " + (cc_mode==null ? "null" : cc_mode));
                    /*if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N ) {
                        Integer raw_sensitivity_boost = builder.get(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST);
                        Log.d(TAG, "raw_sensitivity_boost: " + (raw_sensitivity_boost==null ? "null" : raw_sensitivity_boost));
                    }*/
                }
                //Integer ois_mode = builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
                //Log.d(TAG, "ois_mode: " + (ois_mode==null ? "null" : ois_mode));
            }
        }

        private boolean setSceneMode(CaptureRequest.Builder builder) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "setSceneMode");
                Log.d(TAG, "builder: " + builder);
            }
            Integer current_scene_mode = builder.get(CaptureRequest.CONTROL_SCENE_MODE);
            if( has_face_detect_mode ) {
                // face detection mode overrides scene mode
                if( current_scene_mode == null || current_scene_mode != CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY ) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "setting scene mode for face detection");
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                    builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
                    return true;
                }
            }
            else if( current_scene_mode == null || current_scene_mode != scene_mode ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting scene mode: " + scene_mode);
                if( scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ) {
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                }
                else {
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                }
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, scene_mode);
                return true;
            }
            return false;
        }

        private boolean setColorEffect(CaptureRequest.Builder builder) {
            /*if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null && color_effect == CameraMetadata.CONTROL_EFFECT_MODE_OFF ) {
                // can leave off
            }
            else*/ if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null || builder.get(CaptureRequest.CONTROL_EFFECT_MODE) != color_effect ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting color effect: " + color_effect);
                builder.set(CaptureRequest.CONTROL_EFFECT_MODE, color_effect);
                return true;
            }
            return false;
        }

        private boolean setWhiteBalance(CaptureRequest.Builder builder) {
            boolean changed = false;
            /*if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null && white_balance == CameraMetadata.CONTROL_AWB_MODE_AUTO ) {
                // can leave off
            }
            else*/ if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null || builder.get(CaptureRequest.CONTROL_AWB_MODE) != white_balance ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting white balance: " + white_balance);
                builder.set(CaptureRequest.CONTROL_AWB_MODE, white_balance);
                changed = true;
            }
            if( white_balance == CameraMetadata.CONTROL_AWB_MODE_OFF ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "setting white balance temperature: " + white_balance_temperature);
                // manual white balance
                RggbChannelVector rggbChannelVector = convertTemperatureToRggb(white_balance_temperature);
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
                changed = true;
            }
            return changed;
        }

        private boolean setAntiBanding(CaptureRequest.Builder builder) {
            boolean changed = false;
            if( has_antibanding ) {
                if( builder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) == null || builder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) != antibanding ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting antibanding: " + antibanding);
                    builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, antibanding);
                    changed = true;
                }
            }
            return changed;
        }

        private boolean setEdgeMode(CaptureRequest.Builder builder) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "setEdgeMode");
                Log.d(TAG, "default_edge_mode: " + default_edge_mode);
            }
            boolean changed = false;
            if( has_edge_mode ) {
                if( default_edge_mode == null ) {
                    // save the default_edge_mode edge_mode
                    default_edge_mode = builder.get(CaptureRequest.EDGE_MODE);
                    if( MyDebug.LOG )
                        Log.d(TAG, "default_edge_mode: " + default_edge_mode);
                }
                if( builder.get(CaptureRequest.EDGE_MODE) == null || builder.get(CaptureRequest.EDGE_MODE) != edge_mode ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting edge_mode: " + edge_mode);
                    builder.set(CaptureRequest.EDGE_MODE, edge_mode);
                    changed = true;
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "edge_mode was already set: " + edge_mode);
                }
            }
            else if( is_samsung_s7 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set EDGE_MODE_OFF");
                // see https://sourceforge.net/p/opencamera/discussion/general/thread/48bd836b/ ,
                // https://stackoverflow.com/questions/36028273/android-camera-api-glossy-effect-on-galaxy-s7
                // need EDGE_MODE_OFF to avoid a "glow" effect
                builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            }
            else if( default_edge_mode != null ) {
                if( builder.get(CaptureRequest.EDGE_MODE) != null && !builder.get(CaptureRequest.EDGE_MODE).equals(default_edge_mode) ) {
                    builder.set(CaptureRequest.EDGE_MODE, default_edge_mode);
                    changed = true;
                }
            }
            return changed;
        }

        private boolean setNoiseReductionMode(CaptureRequest.Builder builder) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "setNoiseReductionMode");
                Log.d(TAG, "default_noise_reduction_mode: " + default_noise_reduction_mode);
            }
            boolean changed = false;
            if( has_noise_reduction_mode ) {
                if( default_noise_reduction_mode == null ) {
                    // save the default_noise_reduction_mode noise_reduction_mode
                    default_noise_reduction_mode = builder.get(CaptureRequest.NOISE_REDUCTION_MODE);
                    if( MyDebug.LOG )
                        Log.d(TAG, "default_noise_reduction_mode: " + default_noise_reduction_mode);
                }
                if( builder.get(CaptureRequest.NOISE_REDUCTION_MODE) == null || builder.get(CaptureRequest.NOISE_REDUCTION_MODE) != noise_reduction_mode ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting noise_reduction_mode: " + noise_reduction_mode);
                    builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noise_reduction_mode);
                    changed = true;
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "noise_reduction_mode was already set: " + noise_reduction_mode);
                }
            }
            else if( is_samsung_s7 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set NOISE_REDUCTION_MODE_OFF");
                // see https://sourceforge.net/p/opencamera/discussion/general/thread/48bd836b/ ,
                // https://stackoverflow.com/questions/36028273/android-camera-api-glossy-effect-on-galaxy-s7
                // need NOISE_REDUCTION_MODE_OFF to avoid excessive blurring
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            }
            else if( default_noise_reduction_mode != null ) {
                if( builder.get(CaptureRequest.NOISE_REDUCTION_MODE) != null && !builder.get(CaptureRequest.NOISE_REDUCTION_MODE).equals(default_noise_reduction_mode)) {
                    builder.set(CaptureRequest.NOISE_REDUCTION_MODE, default_noise_reduction_mode);
                    changed = true;
                }
            }
            return changed;
        }

        private boolean setAperture(CaptureRequest.Builder builder) {
            if( MyDebug.LOG )
                Log.d(TAG, "setAperture");
            // don't set at all if has_aperture==false
            if( has_aperture ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "    aperture: " + aperture);
                builder.set(CaptureRequest.LENS_APERTURE, aperture);
                return true;
            }
            return false;
        }

        @SuppressWarnings("SameReturnValue")
        private boolean setAEMode(CaptureRequest.Builder builder, boolean is_still) {
            if( MyDebug.LOG )
                Log.d(TAG, "setAEMode");
            if( has_iso ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "manual mode");
                    Log.d(TAG, "iso: " + iso);
                    Log.d(TAG, "exposure_time: " + exposure_time);
                }
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                long actual_exposure_time = exposure_time;
                if( !is_still ) {
                    // if this isn't for still capture, have a max exposure time of 1/12s
                    actual_exposure_time = Math.min(exposure_time, max_preview_exposure_time_c);
                    if( MyDebug.LOG )
                        Log.d(TAG, "actually using exposure_time of: " + actual_exposure_time);
                }
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, actual_exposure_time);
                if (sensor_frame_duration > 0) {
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, sensor_frame_duration);
                }
                //builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L);
                //builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);
                // only need to account for FLASH_MODE_TORCH, otherwise we use fake flash mode for manual ISO
                if( flash_value.equals("flash_torch") ) {
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                }
                else {
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
            }
            else {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "auto mode");
                    Log.d(TAG, "flash_value: " + flash_value);
                }
                if( ae_target_fps_range != null ) {
                    Log.d(TAG, "set ae_target_fps_range: " + ae_target_fps_range);
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, ae_target_fps_range);
                }

                // prefer to set flash via the ae mode (otherwise get even worse results), except for torch which we can't
                //noinspection DuplicateBranchesInSwitch
                switch(flash_value) {
                    case "flash_off":
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        break;
                    case "flash_auto":
                        // note we set this even in fake flash mode (where we manually turn torch on and off to simulate flash) so we
                        // can read the FLASH_REQUIRED state to determine if flash is required
                    /*if( use_fake_precapture || CameraController2.this.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else*/
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        break;
                    case "flash_on":
                        // see note above for "flash_auto" for why we set this even fake flash mode - arguably we don't need to know
                        // about FLASH_REQUIRED in flash_on mode, but we set it for consistency...
                    /*if( use_fake_precapture || CameraController2.this.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else*/
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        break;
                    case "flash_torch":
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        break;
                    case "flash_red_eye":
                        // not supported for expo bracketing or burst
                        if( CameraController2.this.burst_type != BurstType.BURSTTYPE_NONE )
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        else
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        break;
                    case "flash_frontscreen_auto":
                    case "flash_frontscreen_on":
                    case "flash_frontscreen_torch":
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        break;
                }
            }
            return true;
        }

        private void setCropRegion(CaptureRequest.Builder builder) {
            if( scalar_crop_region != null ) {
                builder.set(CaptureRequest.SCALER_CROP_REGION, scalar_crop_region);
            }
        }

        private boolean setExposureCompensation(CaptureRequest.Builder builder) {
            if( !has_ae_exposure_compensation )
                return false;
            if( has_iso ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "don't set exposure compensation in manual iso mode");
                return false;
            }
            if( builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null || ae_exposure_compensation != builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "change exposure to " + ae_exposure_compensation);
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae_exposure_compensation);
                return true;
            }
            return false;
        }

        private void setFocusMode(CaptureRequest.Builder builder) {
            if( has_af_mode ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "change af mode to " + af_mode);
                builder.set(CaptureRequest.CONTROL_AF_MODE, af_mode);
            }
        }
        
        private void setFocusDistance(CaptureRequest.Builder builder) {
            if( MyDebug.LOG )
                Log.d(TAG, "change focus distance to " + focus_distance);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distance);
        }

        private void setAutoExposureLock(CaptureRequest.Builder builder) {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, ae_lock);
        }

        private void setAutoWhiteBalanceLock(CaptureRequest.Builder builder) {
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, wb_lock);
        }

        private void setAFRegions(CaptureRequest.Builder builder) {
            if( af_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, af_regions);
            }
        }

        private void setAERegions(CaptureRequest.Builder builder) {
            if( ae_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, ae_regions);
            }
        }

        private void setFaceDetectMode(CaptureRequest.Builder builder) {
            if( has_face_detect_mode )
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, face_detect_mode);
            else
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
        }
        
        private void setRawMode(CaptureRequest.Builder builder) {
            // DngCreator says "For best quality DNG files, it is strongly recommended that lens shading map output is enabled if supported"
            // docs also say "ON is always supported on devices with the RAW capability", so we don't check for STATISTICS_LENS_SHADING_MAP_MODE_ON being available
            if( want_raw && !previewIsVideoMode ) {
                builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
        }
        
        private void setStabilization(CaptureRequest.Builder builder) {
            if( MyDebug.LOG )
                Log.d(TAG, "setStabilization: " + video_stabilization);
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, video_stabilization ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
            if( supports_optical_stabilization ) {
                if( video_stabilization ) {
                    // should also disable OIS
                    if( default_optical_stabilization == null ) {
                        // save the default optical_stabilization
                        default_optical_stabilization = builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
                        if( MyDebug.LOG )
                            Log.d(TAG, "default_optical_stabilization: " + default_optical_stabilization);
                    }
                    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                }
                else if( default_optical_stabilization != null ) {
                    if( builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE) != null && !builder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE).equals(default_optical_stabilization) ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "set optical stabilization back to: " + default_optical_stabilization);
                        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, default_optical_stabilization);
                    }
                }
            }
        }

        private float getLogProfile(float in) {
            //final float black_level = 4.0f/255.0f;
            //final float power = 1.0f/2.2f;
            final float log_A = log_profile_strength;
            /*float out;
            if( in <= black_level ) {
                out = in;
            }
            else {
                float in_m = (in - black_level) / (1.0f - black_level);
                out = (float) (Math.log1p(log_A * in_m) / Math.log1p(log_A));
                out = black_level + (1.0f - black_level)*out;
            }*/
            float out = (float) (Math.log1p(log_A * in) / Math.log1p(log_A));

            // apply gamma
            // update: no longer need to do this with improvements made in 1.48 onwards
            //out = (float)Math.pow(out, power);
            //out = Math.max(out, 0.5f);

            return out;
        }

        private float getGammaProfile(float in) {
            return (float)Math.pow(in, 1.0f/gamma_profile);
        }

        private void setTonemapProfile(CaptureRequest.Builder builder) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "setTonemapProfile");
                Log.d(TAG, "tonemap_profile: " + tonemap_profile);
                Log.d(TAG, "log_profile_strength: " + log_profile_strength);
                Log.d(TAG, "gamma_profile: " + gamma_profile);
                Log.d(TAG, "default_tonemap_mode: " + default_tonemap_mode);
            }
            boolean have_tonemap_profile = tonemap_profile != TonemapProfile.TONEMAPPROFILE_OFF;
            if( tonemap_profile == TonemapProfile.TONEMAPPROFILE_LOG && log_profile_strength == 0.0f )
                have_tonemap_profile = false;
            else if( tonemap_profile == TonemapProfile.TONEMAPPROFILE_GAMMA && gamma_profile == 0.0f )
                have_tonemap_profile = false;

            // to use test_new, also need to uncomment the test code in setFocusValue() to call setTonemapProfile()
            //boolean test_new = this.af_mode == CaptureRequest.CONTROL_AF_MODE_AUTO; // testing

            //if( test_new )
            //    have_tonemap_profile = false;

            if( have_tonemap_profile ) {
                if( default_tonemap_mode == null ) {
                    // save the default tonemap_mode
                    default_tonemap_mode = builder.get(CaptureRequest.TONEMAP_MODE);
                    if( MyDebug.LOG )
                        Log.d(TAG, "default_tonemap_mode: " + default_tonemap_mode);
                }

                final boolean use_preset_curve = true;
                //final boolean use_preset_curve = false; // test
                //final boolean use_preset_curve = test_new; // test
                if( use_preset_curve && tonemap_profile == TonemapProfile.TONEMAPPROFILE_REC709 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set TONEMAP_PRESET_CURVE_REC709");
                    builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                    builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_REC709);
                }
                else if( use_preset_curve && tonemap_profile == TonemapProfile.TONEMAPPROFILE_SRGB && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set TONEMAP_PRESET_CURVE_SRGB");
                    builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
                    builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "handle via TONEMAP_MODE_CONTRAST_CURVE / TONEMAP_CURVE");
                    float [] values = null;
                    switch( tonemap_profile ) {
                        case TONEMAPPROFILE_REC709:
                            // y = 4.5x if x < 0.018, else y = 1.099*x^0.45 - 0.099
                            float [] x_values = new float[] {
                                    0.0000f, 0.0667f, 0.1333f, 0.2000f,
                                    0.2667f, 0.3333f, 0.4000f, 0.4667f,
                                    0.5333f, 0.6000f, 0.6667f, 0.7333f,
                                    0.8000f, 0.8667f, 0.9333f, 1.0000f
                            };
                            values = new float[2*x_values.length];
                            int c = 0;
                            for(float x_value : x_values) {
                                float out;
                                if( x_value < 0.018f ) {
                                    out = 4.5f * x_value;
                                }
                                else {
                                    out = (float)(1.099*Math.pow(x_value, 0.45) - 0.099);
                                }
                                values[c++] = x_value;
                                values[c++] = out;
                            }
                            break;
                        case TONEMAPPROFILE_SRGB:
                            values = new float [] {
                                    0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                                    0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
                                    0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
                                    0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f
                            };
                            break;
                        case TONEMAPPROFILE_LOG:
                        case TONEMAPPROFILE_GAMMA:
                        {
                            // better to use uniformly spaced values, otherwise we get a weird looking effect - this can be
                            // seen most prominently when using gamma 1.0f, which should look linear (and hence be independent
                            // of the x values we use)
                            // can be reproduced on at least OnePlus 3T and Galaxy S10e (although the exact behaviour of the
                            // poor results is different on those devices)
                            int n_values = tonemap_log_max_curve_points_c;
                            if( is_samsung ) {
                                // unfortunately odd bug on Samsung devices (at least S7 and S10e) where if more than 32 control points,
                                // the maximum brightness value is reduced (can best be seen with 64 points, and using gamma==1.0)
                                // note that Samsung devices also need at least 16 control points - or in some cases 32, see comments for
                                // enforceMinTonemapCurvePoints().
                                // 32 is better than 16 anyway, as better to have more points for finer curve where possible.
                                n_values = 32;
                            }
                            //int n_values = test_new ? 32 : 128;
                            //int n_values = 32;
                            if( MyDebug.LOG )
                                Log.d(TAG, "n_values: " + n_values);
                            values = new float [2*n_values];
                            for(int i=0;i<n_values;i++) {
                                float in = ((float)i) / (n_values-1.0f);
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                values[2*i] = in;
                                values[2*i+1] = out;
                            }
                        }

                        /*if( test_new ) {
                            // if changing this, make sure we don't exceed tonemap_log_max_curve_points_c
                            // we want:
                            // 0-15: step 1 (16 values)
                            // 16-47: step 2 (16 values)
                            // 48-111: step 4 (16 values)
                            // 112-231 : step 8 (15 values)
                            // 232-255: step 24 (1 value)
                            int step = 1, c = 0;
                            //int step = 4, c = 0;
                            //int step = test_new ? 4 : 1, c = 0;
                            values = new float[2*tonemap_log_max_curve_points_c];
                            for(int i=0;i<232;i+=step) {
                                float in = ((float)i) / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                if( tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG )
                                    out = (float)Math.pow(out, 1.0f/2.2f);
                                values[c++] = in;
                                values[c++] = out;
                                if( (c/2) % 16 == 0 ) {
                                    step *= 2;
                                }
                            }
                            values[c++] = 1.0f;
                            float last_out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(1.0f) : getGammaProfile(1.0f);
                            if( tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG )
                                last_out = (float)Math.pow(last_out, 1.0f/2.2f);
                            values[c++] = last_out;
                            values = Arrays.copyOfRange(values,0,c);
                        }*/
                        /*if( test_new )
                        {
                            // x values are ranged 0 to 255
                            float [] x_values = new float[] {
                                    0.0f, 4.0f, 8.0f, 12.0f, 16.0f, 20.0f, 24.0f, 28.0f,
                                    //0.0f, 8.0f, 16.0f, 24.0f,
                                    32.0f, 40.0f, 48.0f, 56.0f,
                                    64.0f, 72.0f, 80.0f, 88.0f,
                                    96.0f, 104.0f, 112.0f, 120.0f,
                                    128.0f, 136.0f, 144.0f, 152.0f,
                                    160.0f, 168.0f, 176.0f, 184.0f,
                                    192.0f, 200.0f, 208.0f, 216.0f,
                                    224.0f, 232.0f, 240.0f, 248.0f,
                                    255.0f
                            };
                            values = new float[2*x_values.length];
                            c = 0;
                            for(float x_value : x_values) {
                                float in = x_value / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                values[c++] = in;
                                values[c++] = out;
                            }
                        }*/
                        /*if( test_new )
                        {
                            values = new float [2*256];
                            step = 8;
                            c = 0;
                            for(int i=0;i<254;i+=step) {
                                float in = ((float)i) / 255.0f;
                                float out = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(in) : getGammaProfile(in);
                                values[c++] = in;
                                values[c++] = out;
                            }
                            values[c++] = 1.0f;
                            values[c++] = (tonemap_profile==TonemapProfile.TONEMAPPROFILE_LOG) ? getLogProfile(1.0f) : getGammaProfile(1.0f);
                            values = Arrays.copyOfRange(values,0,c);
                        }*/
                        if( MyDebug.LOG ) {
                            int n_values = values.length/2;
                            for(int i=0;i<n_values;i++) {
                                float in = values[2*i];
                                float out = values[2*i+1];
                                Log.d(TAG, "i = " + i);
                                //Log.d(TAG, "    in: " + (int)(in*255.0f+0.5f));
                                //Log.d(TAG, "    out: " + (int)(out*255.0f+0.5f));
                                Log.d(TAG, "    in: " + (in*255.0f));
                                Log.d(TAG, "    out: " + (out*255.0f));
                            }
                        }
                        break;
                        case TONEMAPPROFILE_JTVIDEO:
                            values = jtvideo_values;
                            if( MyDebug.LOG )
                                Log.d(TAG, "setting JTVideo profile");
                            break;
                        case TONEMAPPROFILE_JTLOG:
                            values = jtlog_values;
                            if( MyDebug.LOG )
                                Log.d(TAG, "setting JTLog profile");
                            break;
                        case TONEMAPPROFILE_JTLOG2:
                            values = jtlog2_values;
                            if( MyDebug.LOG )
                                Log.d(TAG, "setting JTLog2 profile");
                            break;
                    }

                    // sRGB:
                    /*values = new float []{0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
                            0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
                            0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f};*/
                    /*values = new float []{0.0000f, 0.0000f, 0.05f, 0.3f, 0.1f, 0.4f, 0.2000f, 0.4845f,
                            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f,
                            0.5f, 0.78f, 1.0000f, 1.0000f};*/
                    /*values = new float []{0.0f, 0.0f, 0.05f, 0.4f, 0.1f, 0.54f, 0.2f, 0.6f, 0.3f, 0.65f, 0.4f, 0.7f,
                            0.5f, 0.78f, 1.0f, 1.0f};*/
                    /*values = new float[]{0.0f, 0.0f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
                            1.0f, 1.0f};*/
                    //values = new float []{0.0f, 0.5f, 0.05f, 0.6f, 0.1f, 0.7f, 0.2f, 0.8f, 0.5f, 0.9f, 1.0f, 1.0f};
                    /*values = new float []{0.0f, 0.0f,
                            0.05f, 0.05f,
                            0.1f, 0.1f,
                            0.15f, 0.15f,
                            0.2f, 0.2f,
                            0.25f, 0.25f,
                            0.3f, 0.3f,
                            0.35f, 0.35f,
                            0.4f, 0.4f,
                            0.5f, 0.5f,
                            0.6f, 0.6f,
                            0.7f, 0.7f,
                            0.8f, 0.8f,
                            0.9f, 0.9f,
                            0.95f, 0.95f,
                            1.0f, 1.0f};*/
                    //values = enforceMinTonemapCurvePoints(new float[]{0.0f, 0.0f, 1.0f, 1.0f});
                    //values = enforceMinTonemapCurvePoints(values);

                    if( MyDebug.LOG  )
                        Log.d(TAG, "values: " + Arrays.toString(values));
                    if( values != null ) {
                        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
                        TonemapCurve tonemap_curve = new TonemapCurve(values, values, values);
                        builder.set(CaptureRequest.TONEMAP_CURVE, tonemap_curve);
                        test_used_tonemap_curve = true;
                    }
                    else {
                        Log.e(TAG, "unknown log type: " + tonemap_profile);
                    }
                }
            }
            else if( default_tonemap_mode != null ) {
                builder.set(CaptureRequest.TONEMAP_MODE, default_tonemap_mode);
            }
        }
        
        // n.b., if we add more methods, remember to update setupBuilder() above!
    }

    /** Converts a white balance temperature to red, green even, green odd and blue components.
     */
    private RggbChannelVector convertTemperatureToRggb(int temperature_kelvin) {
        float temperature = temperature_kelvin / 100.0f;
        float red;
        float green;
        float blue;

        if( temperature <= 66 ) {
            red = 255;
        }
        else {
            red = temperature - 60;
            red = (float)(329.698727446 * (Math.pow(red, -0.1332047592)));
            if( red < 0 )
                red = 0;
            if( red > 255 )
                red = 255;
        }

        if( temperature <= 66 ) {
            green = temperature;
            green = (float)(99.4708025861 * Math.log(green) - 161.1195681661);
            if( green < 0 )
                green = 0;
            if( green > 255 )
                green = 255;
        }
        else {
            green = temperature - 60;
            green = (float)(288.1221695283 * (Math.pow(green, -0.0755148492)));
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        }

        if( temperature >= 66 )
            blue = 255;
        else if( temperature <= 19 )
            blue = 0;
        else {
            blue = temperature - 10;
            blue = (float)(138.5177312231 * Math.log(blue) - 305.0447927307);
            if( blue < 0 )
                blue = 0;
            if( blue > 255 )
                blue = 255;
        }

        if( MyDebug.LOG ) {
            Log.d(TAG, "red: " + red);
            Log.d(TAG, "green: " + green);
            Log.d(TAG, "blue: " + blue);
        }
        return new RggbChannelVector((red/255)*2,(green/255),(green/255),(blue/255)*2);
    }

    /** Converts a red, green even, green odd and blue components to a white balance temperature.
     *  Note that this is not necessarily an inverse of convertTemperatureToRggb, since many rggb
     *  values can map to the same temperature.
     */
    private int convertRggbToTemperature(RggbChannelVector rggbChannelVector) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "temperature:");
            Log.d(TAG, "    red: " + rggbChannelVector.getRed());
            Log.d(TAG, "    green even: " + rggbChannelVector.getGreenEven());
            Log.d(TAG, "    green odd: " + rggbChannelVector.getGreenOdd());
            Log.d(TAG, "    blue: " + rggbChannelVector.getBlue());
        }
        float red = rggbChannelVector.getRed();
        float green_even = rggbChannelVector.getGreenEven();
        float green_odd = rggbChannelVector.getGreenOdd();
        float blue = rggbChannelVector.getBlue();
        float green = 0.5f*(green_even + green_odd);

        float max = Math.max(red, blue);
        if( green > max )
            green = max;

        float scale = 255.0f/max;
        red *= scale;
        green *= scale;
        blue *= scale;

        int red_i = (int)red;
        int green_i = (int)green;
        int blue_i = (int)blue;
        int temperature;
        if( red_i == blue_i ) {
            temperature = 6600;
        }
        else if( red_i > blue_i ) {
            // temperature <= 6600
            int t_g = (int)( 100 * Math.exp((green_i + 161.1195681661) / 99.4708025861) );
            if( blue_i == 0 ) {
                temperature = t_g;
            }
            else {
                int t_b = (int)( 100 * (Math.exp((blue_i + 305.0447927307) / 138.5177312231) + 10) );
                temperature = (t_g + t_b)/2;
            }
        }
        else {
            // temperature >= 6700
            if( red_i <= 1 || green_i <= 1 ) {
                temperature = max_white_balance_temperature_c;
            }
            else {
                int t_r = (int)(100 * (Math.pow(red_i / 329.698727446, 1.0 / -0.1332047592) + 60.0));
                int t_g = (int)(100 * (Math.pow(green_i / 288.1221695283, 1.0 / -0.0755148492) + 60.0));
                temperature = (t_r + t_g) / 2;
            }
        }
        temperature = Math.max(temperature, min_white_balance_temperature_c);
        temperature = Math.min(temperature, max_white_balance_temperature_c);
        if( MyDebug.LOG ) {
            Log.d(TAG, "    temperature: " + temperature);
        }
        return temperature;
    }

    private class OnImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if( MyDebug.LOG )
                Log.d(TAG, "new still image available");
            if( picture_cb == null || !jpeg_todo ) {
                // in theory this shouldn't happen - but if this happens, still free the image to avoid risk of memory leak,
                // or strange behaviour where an old image appears when the user next takes a photo
                Log.e(TAG, "no picture callback available");
                Image image = reader.acquireNextImage();
                image.close();
                return;
            }

            List<byte []> single_burst_complete_images = null;
            boolean call_takePhotoPartial = false;
            boolean call_takePhotoCompleted = false;

            Image image = reader.acquireNextImage();
            if( MyDebug.LOG )
                Log.d(TAG, "image timestamp: " + image.getTimestamp());
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte [] bytes = new byte[buffer.remaining()];
            if( MyDebug.LOG )
                Log.d(TAG, "read " + bytes.length + " bytes");
            buffer.get(bytes);
            image.close();

            synchronized( background_camera_lock ) {
                n_burst_taken++;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "n_burst_taken is now: " + n_burst_taken);
                    Log.d(TAG, "n_burst: " + n_burst);
                    Log.d(TAG, "burst_single_request: " + burst_single_request);
                }
                if( burst_single_request ) {
                    pending_burst_images.add(bytes);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "pending_burst_images size is now: " + pending_burst_images.size());
                    }
                    if( pending_burst_images.size() >= n_burst ) { // shouldn't ever be greater, but just in case
                        if( MyDebug.LOG )
                            Log.d(TAG, "all burst images available");
                        if( pending_burst_images.size() > n_burst ) {
                            Log.e(TAG, "pending_burst_images size " + pending_burst_images.size() + " is greater than n_burst " + n_burst);
                        }
                        // take a copy, so that we can clear pending_burst_images
                        single_burst_complete_images = new ArrayList<>(pending_burst_images);
                        // continued below after lock...
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "number of burst images is now: " + pending_burst_images.size());
                        call_takePhotoPartial = true;
                    }
                }
                // case for burst_single_request==false handled below
            }

            // need to call without a lock
            if( single_burst_complete_images != null ) {
                picture_cb.onBurstPictureTaken(single_burst_complete_images);
            }
            else if( !burst_single_request ) {
                picture_cb.onPictureTaken(bytes);
            }

            synchronized( background_camera_lock ) {
                if( single_burst_complete_images != null ) {
                    pending_burst_images.clear();

                    call_takePhotoCompleted = true;
                }
                else if( !burst_single_request ) {
                    n_burst--;
                    if( MyDebug.LOG )
                        Log.d(TAG, "n_burst is now " + n_burst);
                    if( burst_type == BurstType.BURSTTYPE_CONTINUOUS && !continuous_burst_requested_last_capture ) {
                        // even if n_burst is 0, we don't want to give up if we're still in continuous burst mode
                        // also note if we do have continuous_burst_requested_last_capture==true, we still check for
                        // n_burst==0 below (as there may have been more than one image still to be received)
                        if( MyDebug.LOG )
                            Log.d(TAG, "continuous burst mode still in progress");
                        call_takePhotoPartial = true;
                    }
                    else if( n_burst == 0 ) {
                        call_takePhotoCompleted = true;
                    }
                    else {
                        call_takePhotoPartial = true;
                    }
                }
            }

            // need to call outside of lock (because they can lead to calls to external callbacks)
            if( call_takePhotoPartial ) {
                takePhotoPartial();
            }
            else if( call_takePhotoCompleted ) {
                takePhotoCompleted();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "done onImageAvailable");
        }

        /** Called when an image has been received, but we're in a burst mode, and not all images have
         *  been received.
         */
        private void takePhotoPartial() {
            if( MyDebug.LOG )
                Log.d(TAG, "takePhotoPartial");

            ErrorCallback push_take_picture_error_cb = null;

            synchronized( background_camera_lock ) {
                if( slow_burst_capture_requests != null ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "need to execute the next capture");
                        Log.d(TAG, "time since start: " + (System.currentTimeMillis() - slow_burst_start_ms));
                    }
                    if( burst_type != BurstType.BURSTTYPE_FOCUS ) {
                        try {
                            if( camera != null && captureSession != null ) { // make sure camera wasn't released in the meantime
                                captureSession.capture(slow_burst_capture_requests.get(n_burst_taken), previewCaptureCallback, handler);
                            }
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to take next burst");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                            jpeg_todo = false;
                            raw_todo = false;
                            picture_cb = null;
                            push_take_picture_error_cb = take_picture_error_cb;
                        }
                    }
                    else if( previewBuilder != null ) { // make sure camera wasn't released in the meantime
                        if( MyDebug.LOG )
                            Log.d(TAG, "focus bracketing");

                        if( !focus_bracketing_in_progress ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "focus bracketing was cancelled");
                            // ideally we'd stop altogether, but instead we take one last shot, so that we can mark it with the
                            // RequestTagType.CAPTURE tag, so onCaptureCompleted() is called knowing it's for the last image
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "slow_burst_capture_requests size was: " + slow_burst_capture_requests.size());
                                Log.d(TAG, "n_burst size was: " + n_burst);
                                Log.d(TAG, "n_burst_taken: " + n_burst_taken);
                            }
                            slow_burst_capture_requests.subList(n_burst_taken+1, slow_burst_capture_requests.size()).clear(); // resize to n_burst_taken
                            // if burst_single_request==true, n_burst is constant and we stop when pending_burst_images.size() >= n_burst
                            // if burst_single_request==false, n_burst counts down and we stop when n_burst==0
                            if( burst_single_request ) {
                                n_burst = slow_burst_capture_requests.size();
                                if( n_burst_raw > 0 ) {
                                    n_burst_raw = slow_burst_capture_requests.size();
                                }
                            }
                            else {
                                n_burst = 1;
                                if( n_burst_raw > 0 ) {
                                    n_burst_raw = 1;
                                }
                            }
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "size is now: " + slow_burst_capture_requests.size());
                                Log.d(TAG, "n_burst is now: " + n_burst);
                                Log.d(TAG, "n_burst_raw is now: " + n_burst_raw);
                            }
                            RequestTagObject requestTag = (RequestTagObject)slow_burst_capture_requests.get(slow_burst_capture_requests.size()-1).getTag();
                            requestTag.setType(RequestTagType.CAPTURE);
                        }

                        // code for focus bracketing
                        try {
                            float focus_distance = slow_burst_capture_requests.get(n_burst_taken).get(CaptureRequest.LENS_FOCUS_DISTANCE);
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "prepare preview for next focus_distance: " + focus_distance);
                            }
                            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                            previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distance);

                            setRepeatingRequest(previewBuilder.build());
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to take set focus distance for next focus bracketing burst");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                            jpeg_todo = false;
                            raw_todo = false;
                            picture_cb = null;
                            push_take_picture_error_cb = take_picture_error_cb;
                        }
                        handler.postDelayed(new Runnable(){
                            @Override
                            public void run(){
                                if( MyDebug.LOG )
                                    Log.d(TAG, "take picture after delay for next focus bracket");
                                if( camera != null && captureSession != null ) { // make sure camera wasn't released in the meantime
                                    if( picture_cb.imageQueueWouldBlock(imageReaderRaw != null ? 1 : 0, 1) ) {
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "...but wait for next focus bracket, as image queue would block");
                                        }
                                        handler.postDelayed(this, 100);
                                        //throw new RuntimeException(); // test
                                    }
                                    else {
                                        // For focus bracketing mode, we play the shutter sound per shot (so the user can tell when the sequence is complete).
                                        // From a user mode, the gap between shots in focus bracketing mode makes this more analogous to the auto-repeat mode
                                        // (at the Preview level), which makes the shutter sound per shot.

                                        playSound(MediaActionSound.SHUTTER_CLICK);
                                        try {
                                            captureSession.capture(slow_burst_capture_requests.get(n_burst_taken), previewCaptureCallback, handler);
                                        }
                                        catch(CameraAccessException e) {
                                            if( MyDebug.LOG ) {
                                                Log.e(TAG, "failed to take next focus bracket");
                                                Log.e(TAG, "reason: " + e.getReason());
                                                Log.e(TAG, "message: " + e.getMessage());
                                            }
                                            e.printStackTrace();
                                            jpeg_todo = false;
                                            raw_todo = false;
                                            picture_cb = null;
                                            if( take_picture_error_cb != null ) {
                                                take_picture_error_cb.onError();
                                                take_picture_error_cb = null;
                                            }
                                        }
                                    }
                                }
                            }
                        }, 500);
                    }
                }
            }

            // need to call callbacks without a lock
            if( push_take_picture_error_cb != null ) {
                push_take_picture_error_cb.onError();
            }
        }

        /** Called when an image has been received, but either we're not in a burst mode, or we are
         *  but all images have been received.
         */
        private void takePhotoCompleted() {
            if( MyDebug.LOG )
                Log.d(TAG, "takePhotoCompleted");
            // need to set jpeg_todo to false before calling onCompleted, as that may reenter CameraController to take another photo (if in auto-repeat burst mode) - see testTakePhotoRepeat()
            synchronized( background_camera_lock ) {
                jpeg_todo = false;
            }
            checkImagesCompleted();
        }
    }

    private class OnRawImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private final Queue<CaptureResult> capture_results = new LinkedList<>();
        private final Queue<Image> images = new LinkedList<>();

        void setCaptureResult(CaptureResult capture_result) {
            if( MyDebug.LOG )
                Log.d(TAG, "setCaptureResult()");
            synchronized( background_camera_lock ) {
                /* synchronize, as we don't want to set the capture_result, at the same time that onImageAvailable() is called, as
                 * we'll end up calling processImage() both in onImageAvailable() and here.
                 */
                this.capture_results.add(capture_result);
                if( images.size() > 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can now process the image");
                    // should call processImage() on UI thread, to be consistent with onImageAvailable()->processImage()
                    // important to avoid crash when pause preview is option, tested in testTakePhotoRawWaitCaptureResult()
                    final Activity activity = (Activity)context;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if( MyDebug.LOG )
                                Log.d(TAG, "setCaptureResult UI thread call processImage()");
                            // n.b., intentionally don't set the lock again
                            processImage();
                        }
                    });
                }
            }
        }
        
        void clear() {
            if( MyDebug.LOG )
                Log.d(TAG, "clear()");
            synchronized( background_camera_lock ) {
                // synchronize just to be safe?
                capture_results.clear();
                images.clear();
            }
        }

        private void processImage() {
            if( MyDebug.LOG )
                Log.d(TAG, "processImage()");

            List<RawImage> single_burst_complete_images = null;
            boolean call_takePhotoCompleted = false;
            DngCreator dngCreator;
            CaptureResult capture_result;
            Image image;

            synchronized( background_camera_lock ) {
                if( capture_results.size() == 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "don't yet have still_capture_result");
                    return;
                }
                if( images.size() == 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "don't have image?!");
                    return;
                }
                capture_result = capture_results.remove();
                image = images.remove();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "now have all info to process raw image");
                    Log.d(TAG, "image timestamp: " + image.getTimestamp());
                }
                dngCreator = new DngCreator(characteristics, capture_result);
                // set fields
                dngCreator.setOrientation(camera_settings.getExifOrientation());
                if( camera_settings.location != null ) {
                    dngCreator.setLocation(camera_settings.location);
                }

                if( n_burst_total == 1 && burst_type != BurstType.BURSTTYPE_CONTINUOUS ) {
                    // Rather than call onRawPictureTaken straight away, we set pending_raw_image so that
                    // it's called in checkImagesCompleted, to ensure the RAW callback is taken after the JPEG callback.
                    // This isn't required, but can give an appearance of better performance to the user, as the thumbnail
                    // animation for a photo having been taken comes from the JPEG.
                    // We don't do this for burst mode, as it would get too complicated trying to enforce an ordering...
                    pending_raw_image = new RawImage(dngCreator, image);
                }
                else if( burst_single_request ) {
                    pending_burst_images_raw.add(new RawImage(dngCreator, image));
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "pending_burst_images_raw size is now: " + pending_burst_images_raw.size());
                    }
                    if( pending_burst_images_raw.size() >= n_burst_raw ) { // shouldn't ever be greater, but just in case
                        if( MyDebug.LOG )
                            Log.d(TAG, "all raw burst images available");
                        if( pending_burst_images_raw.size() > n_burst_raw ) {
                            Log.e(TAG, "pending_burst_images_raw size " + pending_burst_images_raw.size() + " is greater than n_burst_raw " + n_burst_raw);
                        }
                        // take a copy, so that we can clear pending_burst_images_raw
                        single_burst_complete_images = new ArrayList<>(pending_burst_images_raw);
                        // continued below after lock...
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "number of raw burst images is now: " + pending_burst_images_raw.size());
                    }
                }
                // case for burst_single_request==false handled below
            }

            if( pending_raw_image != null ) {
                //takePendingRaw(); // test not waiting for JPEG callback

                checkImagesCompleted();
            }
            else {
                // burst-only code
                // need to call without a lock
                if( single_burst_complete_images != null ) {
                    picture_cb.onRawBurstPictureTaken(single_burst_complete_images);
                }
                else if( !burst_single_request ) {
                    picture_cb.onRawPictureTaken(new RawImage(dngCreator, image));
                }

                synchronized( background_camera_lock ) {
                    if( single_burst_complete_images != null ) {
                        pending_burst_images_raw.clear();

                        call_takePhotoCompleted = true;
                    }
                    else if( !burst_single_request ) {
                        n_burst_raw--;
                        if( MyDebug.LOG )
                            Log.d(TAG, "n_burst_raw is now " + n_burst_raw);
                        if( burst_type == BurstType.BURSTTYPE_CONTINUOUS && !continuous_burst_requested_last_capture ) {
                            // even if n_burst_raw is 0, we don't want to give up if we're still in continuous burst mode
                            // also note if we do have continuous_burst_requested_last_capture==true, we still check for
                            // n_burst_raw==0 below (as there may have been more than one image still to be received)
                            if( MyDebug.LOG )
                                Log.d(TAG, "continuous burst mode still in progress");
                        }
                        else if( n_burst_raw == 0 ) {
                            call_takePhotoCompleted = true;
                        }
                    }
                }

                // need to call outside of lock (because they can lead to calls to external callbacks)
                if( call_takePhotoCompleted ) {
                    synchronized( background_camera_lock ) {
                        raw_todo = false;
                    }
                    checkImagesCompleted();
                }
            }

            if( MyDebug.LOG )
                Log.d(TAG, "done processImage");
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if( MyDebug.LOG )
                Log.d(TAG, "new still raw image available");
            if( picture_cb == null || !raw_todo ) {
                // in theory this shouldn't happen - but if this happens, still free the image to avoid risk of memory leak,
                // or strange behaviour where an old image appears when the user next takes a photo
                Log.e(TAG, "no picture callback available");
                Image this_image = reader.acquireNextImage();
                this_image.close();
                return;
            }
            synchronized( background_camera_lock ) {
                // see comment above in setCaptureResult() for why we synchronize
                Image image = reader.acquireNextImage();
                images.add(image);
            }
            processImage();
            if( MyDebug.LOG )
                Log.d(TAG, "done (RAW) onImageAvailable");
        }
    }
    
    private final CameraSettings camera_settings = new CameraSettings();
    private boolean push_repeating_request_when_torch_off = false;
    private CaptureRequest push_repeating_request_when_torch_off_id = null;
    /*private boolean push_set_ae_lock = false;
    private CaptureRequest push_set_ae_lock_id = null;*/

    private CaptureRequest fake_precapture_turn_on_torch_id = null; // the CaptureRequest used to turn on torch when starting the "fake" precapture

    @Override
    public void onError() {
        Log.e(TAG, "onError");
        if( camera != null ) {
            onError(camera);
        }
    }

    private void onError(@NonNull CameraDevice cam) {
        Log.e(TAG, "onError");
        boolean camera_already_opened = this.camera != null;
        // need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
        this.camera = null;
        if( MyDebug.LOG )
            Log.d(TAG, "onError: camera is now set to null");
        cam.close();
        if( MyDebug.LOG )
            Log.d(TAG, "onError: camera is now closed");

        if( camera_already_opened ) {
            // need to communicate the problem to the application
            // n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
            Log.e(TAG, "error occurred after camera was opened");
            camera_error_cb.onError();
        }
    }

    /** Opens the camera device.
     * @param context Application context.
     * @param cameraId Which camera to open (must be between 0 and CameraControllerManager2.getNumberOfCameras()-1).
     * @param preview_error_cb onError() will be called if the preview stops due to error.
     * @param camera_error_cb onError() will be called if the camera closes due to serious error. No more calls to the CameraController2 object should be made (though a new one can be created, to try reopening the camera).
     * @throws CameraControllerException if the camera device fails to open.
     */
    public CameraController2(Context context, int cameraId, final ErrorCallback preview_error_cb, final ErrorCallback camera_error_cb) throws CameraControllerException {
        super(cameraId);
        if( MyDebug.LOG ) {
            Log.d(TAG, "create new CameraController2: " + cameraId);
            Log.d(TAG, "this: " + this);
        }

        this.context = context;
        this.preview_error_cb = preview_error_cb;
        this.camera_error_cb = camera_error_cb;

        this.is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        this.is_samsung_s7 = Build.MODEL.toLowerCase(Locale.US).contains("sm-g93");
        if( MyDebug.LOG ) {
            Log.d(TAG, "is_samsung: " + is_samsung);
            Log.d(TAG, "is_samsung_s7: " + is_samsung_s7);
        }

        thread = new HandlerThread("CameraBackground"); 
        thread.start(); 
        handler = new Handler(thread.getLooper());

        final CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);

        class MyStateCallback extends CameraDevice.StateCallback {
            boolean callback_done; // must sychronize on this and notifyAll when setting to true
            boolean first_callback = true; // Google Camera says we may get multiple callbacks, but only the first indicates the status of the camera opening operation
            @Override
            public void onOpened(@NonNull CameraDevice cam) {
                if( MyDebug.LOG )
                    Log.d(TAG, "camera opened, first_callback? " + first_callback);
                /*if( true ) // uncomment to test timeout code
                    return;*/
                if( first_callback ) {
                    first_callback = false;

                    try {
                        // we should be able to get characteristics at any time, but Google Camera only does so when camera opened - so do so similarly to be safe
                        if( MyDebug.LOG )
                            Log.d(TAG, "try to get camera characteristics");
                        characteristics = manager.getCameraCharacteristics(cameraIdS);
                        if( MyDebug.LOG )
                            Log.d(TAG, "successfully obtained camera characteristics");
                        // now read cached values
                        characteristics_sensor_orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                        switch( characteristics.get(CameraCharacteristics.LENS_FACING) ) {
                            case CameraMetadata.LENS_FACING_FRONT:
                                characteristics_facing = Facing.FACING_FRONT;
                                break;
                            case CameraMetadata.LENS_FACING_BACK:
                                characteristics_facing = Facing.FACING_BACK;
                                break;
                            case CameraMetadata.LENS_FACING_EXTERNAL:
                                characteristics_facing = Facing.FACING_EXTERNAL;
                                break;
                            default:
                                Log.e(TAG, "unknown camera_facing: " + characteristics.get(CameraCharacteristics.LENS_FACING));
                                characteristics_facing = Facing.FACING_UNKNOWN;
                                break;
                        }

                        if( MyDebug.LOG ) {
                            Log.d(TAG, "characteristics_sensor_orientation: " + characteristics_sensor_orientation);
                            Log.d(TAG, "characteristics_facing: " + characteristics_facing);
                        }

                        CameraController2.this.camera = cam;

                        // note, this won't start the preview yet, but we create the previewBuilder in order to start setting camera parameters
                        createPreviewRequest();
                    }
                    catch(CameraAccessException e) {
                        if( MyDebug.LOG ) {
                            Log.e(TAG, "failed to get camera characteristics");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                        // don't throw CameraControllerException here - instead error is handled by setting callback_done to callback_done, and the fact that camera will still be null
                    }

                    if( MyDebug.LOG )
                        Log.d(TAG, "about to synchronize to say callback done");
                    synchronized( open_camera_lock ) {
                        callback_done = true;
                        if( MyDebug.LOG )
                            Log.d(TAG, "callback done, about to notify");
                        open_camera_lock.notifyAll();
                        if( MyDebug.LOG )
                            Log.d(TAG, "callback done, notification done");
                    }
                }
            }

            @Override
            public void onClosed(@NonNull CameraDevice cam) {
                if( MyDebug.LOG )
                    Log.d(TAG, "camera closed, first_callback? " + first_callback);
                // caller should ensure camera variables are set to null
                if( first_callback ) {
                    first_callback = false;
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cam) {
                if( MyDebug.LOG )
                    Log.d(TAG, "camera disconnected, first_callback? " + first_callback);
                if( first_callback ) {
                    first_callback = false;
                    // must call close() if disconnected before camera was opened
                    // need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
                    CameraController2.this.camera = null;
                    if( MyDebug.LOG )
                        Log.d(TAG, "onDisconnected: camera is now set to null");
                    cam.close();
                    if( MyDebug.LOG )
                        Log.d(TAG, "onDisconnected: camera is now closed");
                    if( MyDebug.LOG )
                        Log.d(TAG, "about to synchronize to say callback done");
                    synchronized( open_camera_lock ) {
                        callback_done = true;
                        if( MyDebug.LOG )
                            Log.d(TAG, "callback done, about to notify");
                        open_camera_lock.notifyAll();
                        if( MyDebug.LOG )
                            Log.d(TAG, "callback done, notification done");
                    }
                }
            }

            @Override
            public void onError(@NonNull CameraDevice cam, int error) {
                // n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
                Log.e(TAG, "camera error: " + error);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "received camera: " + cam);
                    Log.d(TAG, "actual camera: " + CameraController2.this.camera);
                    Log.d(TAG, "first_callback? " + first_callback);
                }
                if( first_callback ) {
                    first_callback = false;
                }
                CameraController2.this.onError(cam);
                if( MyDebug.LOG )
                    Log.d(TAG, "about to synchronize to say callback done");
                synchronized( open_camera_lock ) {
                    callback_done = true;
                    if( MyDebug.LOG )
                        Log.d(TAG, "callback done, about to notify");
                    open_camera_lock.notifyAll();
                    if( MyDebug.LOG )
                        Log.d(TAG, "callback done, notification done");
                }
            }
        }
        final MyStateCallback myStateCallback = new MyStateCallback();

        try {
            if( MyDebug.LOG )
                Log.d(TAG, "get camera id list");
            this.cameraIdS = manager.getCameraIdList()[cameraId];
            if( MyDebug.LOG )
                Log.d(TAG, "about to open camera: " + cameraIdS);
            manager.openCamera(cameraIdS, myStateCallback, handler);
            if( MyDebug.LOG )
                Log.d(TAG, "open camera request complete");
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to open camera: CameraAccessException");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
        catch(UnsupportedOperationException e) {
            // Google Camera catches UnsupportedOperationException
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to open camera: UnsupportedOperationException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
        catch(SecurityException e) {
            // Google Camera catches SecurityException
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to open camera: SecurityException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
        catch(IllegalArgumentException e) {
            // have seen this from Google Play
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to open camera: IllegalArgumentException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
        catch(ArrayIndexOutOfBoundsException e) {
            // Have seen this from Google Play - even though the Preview should have checked the
            // cameraId is within the valid range! Although potentially this could happen if
            // getCameraIdList() returns an empty list.
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to open camera: ArrayIndexOutOfBoundsException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }

        // set up a timeout - sometimes if the camera has got in a state where it can't be opened until after a reboot, we'll never even get a myStateCallback callback called
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if( MyDebug.LOG )
                    Log.d(TAG, "check if camera has opened in reasonable time: " + this);
                synchronized( open_camera_lock ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "synchronized on open_camera_lock");
                        Log.d(TAG, "callback_done: " + myStateCallback.callback_done);
                    }
                    if( !myStateCallback.callback_done ) {
                        // n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
                        Log.e(TAG, "timeout waiting for camera callback");
                        myStateCallback.first_callback = true;
                        myStateCallback.callback_done = true;
                        open_camera_lock.notifyAll();
                    }
                }
            }
        }, 10000);

        if( MyDebug.LOG )
            Log.d(TAG, "wait until camera opened...");
        // need to wait until camera is opened
        // whilst this blocks, this should be running on a background thread anyway (see Preview.openCamera()) - due to maintaining
        // compatibility with the way the old camera API works, it's easier to handle running on a background thread at a higher level,
        // rather than exiting here
        synchronized( open_camera_lock ) {
            while( !myStateCallback.callback_done ) {
                try {
                    // release the lock, and wait until myStateCallback calls notifyAll()
                    open_camera_lock.wait();
                }
                catch(InterruptedException e) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "interrupted while waiting until camera opened");
                    e.printStackTrace();
                }
            }
        }
        if( camera == null ) {
            // n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
            Log.e(TAG, "camera failed to open");
            throw new CameraControllerException();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "camera now opened: " + camera);

        /*{
            // test error handling
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "test camera error");
                    myStateCallback.onError(camera, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
                }
            }, 5000);
        }*/

        /*CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
        StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
        imageReader = ImageReader.newInstance(camera_picture_sizes[0].getWidth(), , ImageFormat.JPEG, 2);*/
        
        // preload sounds to reduce latency - important so that START_VIDEO_RECORDING sound doesn't play after video has started (which means it'll be heard in the resultant video)
        media_action_sound.load(MediaActionSound.START_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.STOP_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.SHUTTER_CLICK);

        // expand tonemap curves
        jtvideo_values = enforceMinTonemapCurvePoints(jtvideo_values_base);
        jtlog_values = enforceMinTonemapCurvePoints(jtlog_values_base);
        jtlog2_values = enforceMinTonemapCurvePoints(jtlog2_values_base);
    }

    @Override
    public void release() {
        if( MyDebug.LOG )
            Log.d(TAG, "release: " + this);
        synchronized( background_camera_lock ) {
            if( captureSession != null ) {
                captureSession.close();
                captureSession = null;
                //pending_request_when_ready = null;
            }
        }
        previewBuilder = null;
        previewIsVideoMode = false;
        if( camera != null ) {
            camera.close();
            camera = null;
        }
        closePictureImageReader();
        /*if( previewImageReader != null ) {
            previewImageReader.close();
            previewImageReader = null;
        }*/
        if( thread != null ) {
            // should only close thread after closing the camera, otherwise we get messages "sending message to a Handler on a dead thread"
            // see https://sourceforge.net/p/opencamera/discussion/general/thread/32c2b01b/?limit=25
            thread.quitSafely();
            try {
                thread.join();
                thread = null;
                handler = null;
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /** Enforce a minimum number of points in tonemap curves - needed due to Galaxy S10e having wrong behaviour if fewer
     *  than 16 or in some cases 32 points?! OnePlus 3T meanwhile has more gradual behaviour where it gets better at 64 points.
     */
    private float [] enforceMinTonemapCurvePoints(float[] in_values) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "enforceMinTonemapCurvePoints: " + Arrays.toString(in_values));
            Log.d(TAG, "length: " + in_values.length/2);
        }
        int min_points_c = 64;
        if( is_samsung ) {
            // Unfortunately odd bug on Samsung devices (at least S7 and S10e) where if more than 32 control points,
            // the maximum brightness value is reduced (can best be seen with 64 points, and using gamma==1.0).
            // Also note that Samsung devices also need at least 16 control points, or in some cases 32, due to problem
            // where things come out almost all black with some white. So choose 32!
            //min_points_c = 16;
            min_points_c = 32;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "min_points_c: " + min_points_c);
        if( in_values.length >= 2*min_points_c ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already enough points");
            return in_values; // fine
        }
        List<Pair<Float, Float>> points = new ArrayList<>();
        for(int i=0;i<in_values.length/2;i++) {
            Pair<Float, Float> point = new Pair<>(in_values[2*i], in_values[2*i+1]);
            points.add(point);
        }
        if( points.size() < 2 ) {
            Log.e(TAG, "less than 2 points?!");
            return in_values;
        }

        while( points.size() < min_points_c ) {
            // find largest interval, and subdivide
            int largest_indx = 0;
            float largest_dist = 0.0f;
            for(int i=0;i<points.size()-1;i++) {
                Pair<Float, Float> p0 = points.get(i);
                Pair<Float, Float> p1 = points.get(i+1);
                float dist = p1.first - p0.first;
                if( dist > largest_dist ) {
                    largest_indx = i;
                    largest_dist = dist;
                }
            }
            /*if( MyDebug.LOG )
                Log.d(TAG, "largest indx " + largest_indx + " dist: " + largest_dist);*/
            Pair<Float, Float> p0 = points.get(largest_indx);
            Pair<Float, Float> p1 = points.get(largest_indx+1);
            float mid_x = 0.5f*(p0.first + p1.first);
            float mid_y = 0.5f*(p0.second + p1.second);
            /*if( MyDebug.LOG )
                Log.d(TAG, "    insert: " + mid_x + " , " + mid_y);*/
            points.add(largest_indx+1, new Pair<>(mid_x, mid_y));
        }

        float [] out_values = new float[2*points.size()];
        for(int i=0;i<points.size();i++) {
            Pair<Float, Float> point = points.get(i);
            out_values[2*i] = point.first;
            out_values[2*i+1] = point.second;
            /*if( MyDebug.LOG )
                Log.d(TAG, "out point[" + i + "]: " + point.first + " , " + point.second);*/
        }
        return out_values;
    }

    private void closePictureImageReader() {
        if( MyDebug.LOG )
            Log.d(TAG, "closePictureImageReader()");
        if( imageReader != null ) {
            imageReader.close();
            imageReader = null;
        }
        if( imageReaderRaw != null ) {
            imageReaderRaw.close();
            imageReaderRaw = null;
            onRawImageAvailableListener = null;
        }
    }

    private List<String> convertFocusModesToValues(int [] supported_focus_modes_arr, float minimum_focus_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "convertFocusModesToValues()");
        if( supported_focus_modes_arr.length == 0 )
            return null;
        List<Integer> supported_focus_modes = new ArrayList<>();
        for(Integer supported_focus_mode : supported_focus_modes_arr)
            supported_focus_modes.add(supported_focus_mode);
        List<String> output_modes = new ArrayList<>();
        // also resort as well as converting
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
            output_modes.add("focus_mode_auto");
            if( MyDebug.LOG ) {
                Log.d(TAG, " supports focus_mode_auto");
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ) {
            output_modes.add("focus_mode_macro");
            if( MyDebug.LOG )
                Log.d(TAG, " supports focus_mode_macro");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
            output_modes.add("focus_mode_locked");
            if( MyDebug.LOG ) {
                Log.d(TAG, " supports focus_mode_locked");
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_OFF) ) {
            output_modes.add("focus_mode_infinity");
            if( minimum_focus_distance > 0.0f ) {
                output_modes.add("focus_mode_manual2");
                if( MyDebug.LOG ) {
                    Log.d(TAG, " supports focus_mode_manual2");
                }
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) ) {
            output_modes.add("focus_mode_edof");
            if( MyDebug.LOG )
                Log.d(TAG, " supports focus_mode_edof");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ) {
            output_modes.add("focus_mode_continuous_picture");
            if( MyDebug.LOG )
                Log.d(TAG, " supports focus_mode_continuous_picture");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ) {
            output_modes.add("focus_mode_continuous_video");
            if( MyDebug.LOG )
                Log.d(TAG, " supports focus_mode_continuous_video");
        }
        return output_modes;
    }

    public String getAPI() {
        return "Camera2 (Android L)";
    }
    
    @Override
    public CameraFeatures getCameraFeatures() throws CameraControllerException {
        if( MyDebug.LOG )
            Log.d(TAG, "getCameraFeatures()");
        CameraFeatures camera_features = new CameraFeatures();
        /*if( true )
            throw new CameraControllerException();*/
        if( MyDebug.LOG ) {
            int hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            switch (hardware_level) {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    Log.d(TAG, "Hardware Level: LEGACY");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    Log.d(TAG, "Hardware Level: LIMITED");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    Log.d(TAG, "Hardware Level: FULL");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                    Log.d(TAG, "Hardware Level: Level 3");
                    break;
                default:
                    Log.e(TAG, "Unknown Hardware Level: " + hardware_level);
                    break;
            }

            int [] nr_modes = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
            Log.d(TAG, "nr_modes:");
            if( nr_modes == null ) {
                Log.d(TAG, "    none");
            }
            else {
                for(int i=0;i<nr_modes.length;i++) {
                    Log.d(TAG, "    " + i + ": " + nr_modes[i]);
                }
            }
        }

        float max_zoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        camera_features.is_zoom_supported = max_zoom > 0.0f;
        if( MyDebug.LOG )
            Log.d(TAG, "max_zoom: " + max_zoom);
        if( camera_features.is_zoom_supported ) {
            // set 20 steps per 2x factor
            final int steps_per_2x_factor = 20;
            //final double scale_factor = Math.pow(2.0, 1.0/(double)steps_per_2x_factor);
            int n_steps =(int)( (steps_per_2x_factor * Math.log(max_zoom + 1.0e-11)) / Math.log(2.0));
            final double scale_factor = Math.pow(max_zoom, 1.0/(double)n_steps);
            if( MyDebug.LOG ) {
                Log.d(TAG, "n_steps: " + n_steps);
                Log.d(TAG, "scale_factor: " + scale_factor);
            }
            camera_features.zoom_ratios = new ArrayList<>();
            camera_features.zoom_ratios.add(100);
            double zoom = 1.0;
            for(int i=0;i<n_steps-1;i++) {
                zoom *= scale_factor;
                camera_features.zoom_ratios.add((int)(zoom*100));
            }
            camera_features.zoom_ratios.add((int)(max_zoom*100));
            camera_features.max_zoom = camera_features.zoom_ratios.size()-1;
            this.zoom_ratios = camera_features.zoom_ratios;
        }
        else {
            this.zoom_ratios = null;
        }

        int [] face_modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        camera_features.supports_face_detection = false;
        supports_face_detect_mode_simple = false;
        supports_face_detect_mode_full = false;
        for(int face_mode : face_modes) {
            if( MyDebug.LOG )
                Log.d(TAG, "face detection mode: " + face_mode);
            // we currently only make use of the "SIMPLE" features, documented as:
            // "Return face rectangle and confidence values only."
            // note that devices that support STATISTICS_FACE_DETECT_MODE_FULL (e.g., Nexus 6) don't return
            // STATISTICS_FACE_DETECT_MODE_SIMPLE in the list, so we have check for either
            if( face_mode == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE ) {
                camera_features.supports_face_detection = true;
                supports_face_detect_mode_simple = true;
                if( MyDebug.LOG )
                    Log.d(TAG, "supports simple face detection mode");
            }
            else if( face_mode == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL ) {
                camera_features.supports_face_detection = true;
                supports_face_detect_mode_full = true;
                if( MyDebug.LOG )
                    Log.d(TAG, "supports full face detection mode");
            }
        }
        if( camera_features.supports_face_detection ) {
            int face_count = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
            if( face_count <= 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "can't support face detection, as zero max face count");
                camera_features.supports_face_detection = false;
                supports_face_detect_mode_simple = false;
                supports_face_detect_mode_full = false;
            }
        }
        if( camera_features.supports_face_detection ) {
            // check we have scene mode CONTROL_SCENE_MODE_FACE_PRIORITY
            int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
            boolean has_face_priority = false;
            for(int value2 : values2) {
                if( value2 == CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY ) {
                    has_face_priority = true;
                    break;
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "has_face_priority: " + has_face_priority);
            if( !has_face_priority ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "can't support face detection, as no CONTROL_SCENE_MODE_FACE_PRIORITY");
                camera_features.supports_face_detection = false;
                supports_face_detect_mode_simple = false;
                supports_face_detect_mode_full = false;
            }
        }

        int [] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        //boolean capabilities_manual_sensor = false;
        boolean capabilities_manual_post_processing = false;
        boolean capabilities_raw = false;
        boolean capabilities_high_speed_video = false;
        for(int capability : capabilities) {
            /*if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR ) {
                // At least some Huawei devices (at least, the Huawei device model FIG-LX3, device code-name hi6250) don't
                // have REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR, but I had a user complain that HDR mode and manual ISO
                // had previously worked for them. Note that we still check below for SENSOR_INFO_SENSITIVITY_RANGE and
                // SENSOR_INFO_EXPOSURE_TIME_RANGE, so not checking REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR shouldn't
                // enable manual ISO/exposure on devices that don't support it.
                // Also may affect Samsung Galaxy A8(2018).
                // Instead we just block LEGACY devices (probably don't need to, again because we check
                // SENSOR_INFO_SENSITIVITY_RANGE and SENSOR_INFO_EXPOSURE_TIME_RANGE, but just in case).
                capabilities_manual_sensor = true;
            }
            else*/ if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING ) {
                capabilities_manual_post_processing = true;
            }
            else if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW ) {
                capabilities_raw = true;
            }
            /*else if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE ) {
                // see note below
                camera_features.supports_burst = true;
            }*/
            else if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                // we test for at least Android M just to be safe (this is needed for createConstrainedHighSpeedCaptureSession())
                capabilities_high_speed_video = true;
            }
            else if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "camera is a logical multi-camera");
            }
        }
        // At least some Huawei devices (at least, the Huawei device model FIG-LX3, device code-name hi6250) don't have
        // REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE, but I had a user complain that NR mode at least had previously
        // (before 1.45) worked for them. It might be that this can still work, just not at 20fps.
        // So instead set to true for all LIMITED devices. Still keep block for LEGACY devices (which definitely shouldn't
        // support fast burst - and which Open Camera never allowed with Camera2 before 1.45).
        // Also may affect Samsung Galaxy A8(2018).
        camera_features.supports_burst = CameraControllerManager2.isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED);

        if( MyDebug.LOG ) {
            //Log.d(TAG, "capabilities_manual_sensor?: " + capabilities_manual_sensor);
            Log.d(TAG, "capabilities_manual_post_processing?: " + capabilities_manual_post_processing);
            Log.d(TAG, "capabilities_raw?: " + capabilities_raw);
            Log.d(TAG, "supports_burst?: " + camera_features.supports_burst);
            Log.d(TAG, "capabilities_high_speed_video?: " + capabilities_high_speed_video);
        }

        StreamConfigurationMap configs;
        try {
            configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        }
        catch(IllegalArgumentException | NullPointerException e) {
            // have had IllegalArgumentException crashes from Google Play - unclear what the cause is, but at least fail gracefully
            // similarly for NullPointerException - note, these aren't from characteristics being null, but from
            // com.android.internal.util.Preconditions.checkArrayElementsNotNull (Preconditions.java:395) - all are from
            // Nexus 7 (2013)s running Android 8.1, but again better to fail gracefully
            e.printStackTrace();
            throw new CameraControllerException();
        }

        android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
        camera_features.picture_sizes = new ArrayList<>();
        if( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ) {
            android.util.Size [] camera_picture_sizes_hires = configs.getHighResolutionOutputSizes(ImageFormat.JPEG);
            if( camera_picture_sizes_hires != null ) {
                for(android.util.Size camera_size : camera_picture_sizes_hires) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "high resolution picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
                    // Check not already listed? If it's listed in both, we'll add it later on when scanning camera_picture_sizes
                    // (and we don't want to set supports_burst to false for such a resolution).
                    boolean found = false;
                    for(android.util.Size sz : camera_picture_sizes) {
                        if( sz.equals(camera_size) ) {
                            found = true;
                            break;
                        }
                    }
                    if( !found ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "high resolution [non-burst] picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
                        CameraController.Size size = new CameraController.Size(camera_size.getWidth(), camera_size.getHeight());
                        size.supports_burst = false;
                        camera_features.picture_sizes.add(size);
                    }
                }
            }
        }
        if( camera_picture_sizes == null ) {
            // camera_picture_sizes is null on Samsung Galaxy Note 10+ and S20 for camera ID 4!
            Log.e(TAG, "no picture sizes returned by getOutputSizes");
            throw new CameraControllerException();
        }
        else {
            for(android.util.Size camera_size : camera_picture_sizes) {
                if( MyDebug.LOG )
                    Log.d(TAG, "picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
                camera_features.picture_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
            }
        }
        // sizes are usually already sorted from high to low, but sort just in case
        // note some devices do have sizes in a not fully sorted order (e.g., Nokia 8)
        Collections.sort(camera_features.picture_sizes, new CameraController.SizeSorter());
        // test high resolution modes not supporting burst:
        //camera_features.picture_sizes.get(0).supports_burst = false;

        raw_size = null;
        if( capabilities_raw ) {
            android.util.Size [] raw_camera_picture_sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
            if( raw_camera_picture_sizes == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "RAW not supported, failed to get RAW_SENSOR sizes");
                want_raw = false; // just in case it got set to true somehow
            }
            else {
                for(android.util.Size size : raw_camera_picture_sizes) {
                    if( raw_size == null || size.getWidth()*size.getHeight() > raw_size.getWidth()*raw_size.getHeight() ) {
                        raw_size = size;
                    }
                }
                if( raw_size == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "RAW not supported, failed to find a raw size");
                    want_raw = false; // just in case it got set to true somehow
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "raw supported, raw size: " + raw_size.getWidth() + " x " + raw_size.getHeight());
                    camera_features.supports_raw = true;                
                }
            }
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "RAW capability not supported");
            want_raw = false; // just in case it got set to true somehow
        }

        ae_fps_ranges = new ArrayList<>();
        for (Range<Integer> r : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
            ae_fps_ranges.add(new int[] {r.getLower(), r.getUpper()});
        }
        Collections.sort(ae_fps_ranges, new CameraController.RangeSorter());
        if( MyDebug.LOG ) {
            Log.d(TAG, "Supported AE video fps ranges: ");
            for (int[] f : ae_fps_ranges) {
                Log.d(TAG, "   ae range: [" + f[0] + "-" + f[1] + "]");
            }
        }

        android.util.Size[] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
        camera_features.video_sizes = new ArrayList<>();
        int min_fps = 9999;
        for(int[] r : this.ae_fps_ranges) {
            min_fps = Math.min(min_fps, r[0]);
        }
        if( camera_video_sizes == null ) {
            // camera_video_sizes is null on Samsung Galaxy Note 10+ and S20 for camera ID 4!
            Log.e(TAG, "no video sizes returned by getOutputSizes");
            throw new CameraControllerException();
        }
        else {
            for(android.util.Size camera_size : camera_video_sizes) {
                if( camera_size.getWidth() > 4096 || camera_size.getHeight() > 2160 )
                    continue; // Nexus 6 returns these, even though not supported?!
                long mfd = configs.getOutputMinFrameDuration(MediaRecorder.class, camera_size);
                int  max_fps = (int)((1.0 / mfd) * 1000000000L);
                ArrayList<int[]> fr = new ArrayList<>();
                fr.add(new int[] {min_fps, max_fps});
                CameraController.Size normal_video_size = new CameraController.Size(camera_size.getWidth(), camera_size.getHeight(), fr, false);
                camera_features.video_sizes.add(normal_video_size);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "normal video size: " + normal_video_size);
                }
            }
        }
        Collections.sort(camera_features.video_sizes, new CameraController.SizeSorter());

        if( capabilities_high_speed_video ) {
            hs_fps_ranges = new ArrayList<>();
            camera_features.video_sizes_high_speed = new ArrayList<>();

            for (Range<Integer> r : configs.getHighSpeedVideoFpsRanges()) {
                hs_fps_ranges.add(new int[] {r.getLower(), r.getUpper()});
            }
            Collections.sort(hs_fps_ranges, new CameraController.RangeSorter());
            if( MyDebug.LOG ) {
                Log.d(TAG, "Supported high speed video fps ranges: ");
                for (int[] f : hs_fps_ranges) {
                    Log.d(TAG, "   hs range: [" + f[0] + "-" + f[1] + "]");
                }
            }


            android.util.Size[] camera_video_sizes_high_speed = configs.getHighSpeedVideoSizes();
            for(android.util.Size camera_size : camera_video_sizes_high_speed) {
                ArrayList<int[]> fr = new ArrayList<>();
                for (Range<Integer> r : configs.getHighSpeedVideoFpsRangesFor(camera_size)) {
                    fr.add(new int[] { r.getLower(), r.getUpper()});
                }
                if (camera_size.getWidth() > 4096 || camera_size.getHeight() > 2160)
                    continue; // just in case? see above
                CameraController.Size hs_video_size = new CameraController.Size(camera_size.getWidth(), camera_size.getHeight(), fr, true);
                if (MyDebug.LOG) {
                    Log.d(TAG, "high speed video size: " + hs_video_size);
                }
                camera_features.video_sizes_high_speed.add(hs_video_size);
            }
            Collections.sort(camera_features.video_sizes_high_speed, new CameraController.SizeSorter());
        }

        android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
        camera_features.preview_sizes = new ArrayList<>();
        Point display_size = new Point();
        Activity activity = (Activity)context;
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getRealSize(display_size);
            // getRealSize() is adjusted based on the current rotation, so should already be landscape format, but it
            // would be good to not assume Open Camera runs in landscape mode (if we ever ran in portrait mode,
            // we'd still want display_size.x > display_size.y as preview resolutions also have width > height)
            if( display_size.x < display_size.y ) {
                //noinspection SuspiciousNameCombination
                display_size.set(display_size.y, display_size.x);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        if( camera_preview_sizes == null ) {
            // camera_preview_sizes is null on Samsung Galaxy Note 10+ and S20 for camera ID 4!
            Log.e(TAG, "no preview sizes returned by getOutputSizes");
            throw new CameraControllerException();
        }
        else {
            for(android.util.Size camera_size : camera_preview_sizes) {
                if( MyDebug.LOG )
                    Log.d(TAG, "preview size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
                if( camera_size.getWidth() > display_size.x || camera_size.getHeight() > display_size.y ) {
                    // Nexus 6 returns these, even though not supported?! (get green corruption lines if we allow these)
                    // Google Camera filters anything larger than height 1080, with a todo saying to use device's measurements
                    continue;
                }
                camera_features.preview_sizes.add(new CameraController.Size(camera_size.getWidth(), camera_size.getHeight()));
            }
        }

        if( characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
            camera_features.supported_flash_values = new ArrayList<>();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_auto");
            camera_features.supported_flash_values.add("flash_on");
            camera_features.supported_flash_values.add("flash_torch");
            if( !use_fake_precapture ) {
                camera_features.supported_flash_values.add("flash_red_eye");
            }
        }
        else if( (getFacing() == Facing.FACING_FRONT) ) {
            camera_features.supported_flash_values = new ArrayList<>();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_frontscreen_auto");
            camera_features.supported_flash_values.add("flash_frontscreen_on");
            camera_features.supported_flash_values.add("flash_frontscreen_torch");
        }

        Float minimum_focus_distance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE); // may be null on some devices
        if( minimum_focus_distance != null ) {
            camera_features.minimum_focus_distance = minimum_focus_distance;
            if( MyDebug.LOG )
                Log.d(TAG, "minimum_focus_distance: " + camera_features.minimum_focus_distance);
        }
        else {
            camera_features.minimum_focus_distance = 0.0f;
        }

        int [] supported_focus_modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); // Android format
        camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes, camera_features.minimum_focus_distance); // convert to our format (also resorts)
        if( camera_features.supported_focus_values != null && camera_features.supported_focus_values.contains("focus_mode_manual2") ) {
            camera_features.supports_focus_bracketing = true;
        }
        camera_features.max_num_focus_areas = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

        camera_features.is_exposure_lock_supported = true;

        camera_features.is_white_balance_lock_supported = true;

        camera_features.is_optical_stabilization_supported = false;
        int [] supported_optical_stabilization_modes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if( supported_optical_stabilization_modes != null ) {
            for(int supported_optical_stabilization_mode : supported_optical_stabilization_modes) {
                if( supported_optical_stabilization_mode == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON ) {
                    camera_features.is_optical_stabilization_supported = true;
                    break;
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "is_optical_stabilization_supported: " + camera_features.is_optical_stabilization_supported);
        supports_optical_stabilization = camera_features.is_optical_stabilization_supported;

        camera_features.is_video_stabilization_supported = false;
        int [] supported_video_stabilization_modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        if( supported_video_stabilization_modes != null ) {
            for(int supported_video_stabilization_mode : supported_video_stabilization_modes) {
                if( supported_video_stabilization_mode == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON ) {
                    camera_features.is_video_stabilization_supported = true;
                    break;
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "is_video_stabilization_supported: " + camera_features.is_video_stabilization_supported);

        camera_features.is_photo_video_recording_supported = CameraControllerManager2.isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED);
        supports_photo_video_recording = camera_features.is_photo_video_recording_supported;

        int [] white_balance_modes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        if( white_balance_modes != null ) {
            for(int value : white_balance_modes) {
                // n.b., Galaxy S10e for front and ultra-wide cameras offers CONTROL_AWB_MODE_OFF despite
                // capabilities_manual_post_processing==false; if we don't check for capabilities_manual_post_processing,
                // adjusting white balance temperature seems to work, but seems safest to require
                // capabilities_manual_post_processing anyway
                if( value == CameraMetadata.CONTROL_AWB_MODE_OFF && capabilities_manual_post_processing && allowManualWB() ) {
                    camera_features.supports_white_balance_temperature = true;
                    camera_features.min_temperature = min_white_balance_temperature_c;
                    camera_features.max_temperature = max_white_balance_temperature_c;
                }
            }
        }
        supports_white_balance_temperature = camera_features.supports_white_balance_temperature;

        // see note above
        //if( capabilities_manual_sensor )
        if( CameraControllerManager2.isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) )
        {
            Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE); // may be null on some devices
            if( iso_range != null ) {
                camera_features.supports_iso_range = true;
                camera_features.min_iso = iso_range.getLower();
                camera_features.max_iso = iso_range.getUpper();
                // we only expose exposure_time if iso_range is supported
                Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE); // may be null on some devices
                if( exposure_time_range != null ) {
                    camera_features.supports_exposure_time = true;
                    camera_features.supports_expo_bracketing = true;
                    camera_features.max_expo_bracketing_n_images = max_expo_bracketing_n_images;
                    camera_features.min_exposure_time = exposure_time_range.getLower();
                    camera_features.max_exposure_time = exposure_time_range.getUpper();
                }
            }
        }

        Range<Integer> exposure_range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        camera_features.min_exposure = exposure_range.getLower();
        camera_features.max_exposure = exposure_range.getUpper();
        camera_features.exposure_step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();

        camera_features.can_disable_shutter_sound = true;

        if( capabilities_manual_post_processing ) {
            Integer tonemap_max_curve_points = characteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS);
            if( tonemap_max_curve_points != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemap_max_curve_points: " + tonemap_max_curve_points);
                camera_features.tonemap_max_curve_points = tonemap_max_curve_points;
                // for now we only expose supporting of custom tonemap curves if there are enough curve points for all the
                // profiles we support
                // remember to divide by 2 if we're comparing against the raw array length!
                camera_features.supports_tonemap_curve =
                    tonemap_max_curve_points >= tonemap_log_max_curve_points_c &&
                            tonemap_max_curve_points >= jtvideo_values.length/2 &&
                            tonemap_max_curve_points >= jtlog_values.length/2 &&
                            tonemap_max_curve_points >= jtlog2_values.length/2;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemap_max_curve_points is null");
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_tonemap_curve?: " + camera_features.supports_tonemap_curve);

        float [] apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        //float [] apertures = new float[]{1.5f, 1.9f, 2.0f, 2.2f, 2.4f, 4.0f, 8.0f, 16.0f}; // test
        if( MyDebug.LOG )
            Log.d(TAG, "apertures: " + Arrays.toString(apertures));
        // no point supporting if only a single aperture
        if( apertures != null && apertures.length > 1 ) {
            camera_features.apertures = apertures;
        }

        SizeF view_angle = CameraControllerManager2.computeViewAngles(characteristics);
        camera_features.view_angle_x = view_angle.getWidth();
        camera_features.view_angle_y = view_angle.getHeight();

        return camera_features;
    }

    public boolean shouldCoverPreview() {
        return !has_received_frame;
    }

    private String convertSceneMode(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.CONTROL_SCENE_MODE_ACTION:
            value = "action";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_BARCODE:
            value = "barcode";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_BEACH:
            value = "beach";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT:
            value = "candlelight";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_DISABLED:
            value = SCENE_MODE_DEFAULT;
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS:
            value = "fireworks";
            break;
        // "hdr" no longer available in Camera2
        /*case CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO:
            // new for Camera2
            value = "high-speed-video";
            break;*/
        case CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE:
            value = "landscape";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_NIGHT:
            value = "night";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT:
            value = "night-portrait";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_PARTY:
            value = "party";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT:
            value = "portrait";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_SNOW:
            value = "snow";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_SPORTS:
            value = "sports";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO:
            value = "steadyphoto";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_SUNSET:
            value = "sunset";
            break;
        case CameraMetadata.CONTROL_SCENE_MODE_THEATRE:
            value = "theatre";
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown scene mode: " + value2);
            value = null;
            break;
        }
        return value;
    }

    @Override
    public SupportedValues setSceneMode(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setSceneMode: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        boolean has_disabled = false;
        List<String> values = new ArrayList<>();
        if( values2 != null ) {
            // CONTROL_AVAILABLE_SCENE_MODES is supposed to always be available, but have had some (rare) crashes from Google Play due to being null
            for(int value2 : values2) {
                if( value2 == CameraMetadata.CONTROL_SCENE_MODE_DISABLED )
                    has_disabled = true;
                String this_value = convertSceneMode(value2);
                if( this_value != null ) {
                    values.add(this_value);
                }
            }
        }
        if( !has_disabled ) {
            values.add(0, SCENE_MODE_DEFAULT);
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, SCENE_MODE_DEFAULT);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
            switch(supported_values.selected_value) {
                case "action":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_ACTION;
                    break;
                case "barcode":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_BARCODE;
                    break;
                case "beach":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_BEACH;
                    break;
                case "candlelight":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT;
                    break;
                case SCENE_MODE_DEFAULT:
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
                    break;
                case "fireworks":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS;
                    break;
                // "hdr" no longer available in Camera2
                case "landscape":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE;
                    break;
                case "night":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_NIGHT;
                    break;
                case "night-portrait":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT;
                    break;
                case "party":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_PARTY;
                    break;
                case "portrait":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT;
                    break;
                case "snow":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SNOW;
                    break;
                case "sports":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SPORTS;
                    break;
                case "steadyphoto":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO;
                    break;
                case "sunset":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SUNSET;
                    break;
                case "theatre":
                    selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_THEATRE;
                    break;
                default:
                    if (MyDebug.LOG)
                        Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                    break;
            }

            camera_settings.scene_mode = selected_value2;
            if( camera_settings.setSceneMode(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to set scene mode");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                } 
            }
        }
        return supported_values;
    }
    
    @Override
    public String getSceneMode() {
        if( previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE);
        return convertSceneMode(value2);
    }

    @Override
    public boolean sceneModeAffectsFunctionality() {
        // Camera2 API doesn't seem to have any warnings that changing scene mode can affect available functionality
        return false;
    }

    private String convertColorEffect(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.CONTROL_EFFECT_MODE_AQUA:
            value = "aqua";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD:
            value = "blackboard";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_MONO:
            value = "mono";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE:
            value = "negative";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_OFF:
            value = COLOR_EFFECT_DEFAULT;
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE:
            value = "posterize";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_SEPIA:
            value = "sepia";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE:
            value = "solarize";
            break;
        case CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD:
            value = "whiteboard";
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown effect mode: " + value2);
            value = null;
            break;
        }
        return value;
    }

    @Override
    public SupportedValues setColorEffect(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setColorEffect: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
        if( values2 == null ) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for(int value2 : values2) {
            String this_value = convertColorEffect(value2);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, COLOR_EFFECT_DEFAULT);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
            switch(supported_values.selected_value) {
                case "aqua":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_AQUA;
                    break;
                case "blackboard":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD;
                    break;
                case "mono":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_MONO;
                    break;
                case "negative":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE;
                    break;
                case COLOR_EFFECT_DEFAULT:
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
                    break;
                case "posterize":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE;
                    break;
                case "sepia":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_SEPIA;
                    break;
                case "solarize":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE;
                    break;
                case "whiteboard":
                    selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD;
                    break;
                default:
                    if (MyDebug.LOG)
                        Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                    break;
            }

            camera_settings.color_effect = selected_value2;
            if( camera_settings.setColorEffect(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to set color effect");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                } 
            }
        }
        return supported_values;
    }

    @Override
    public String getColorEffect() {
        if( previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE);
        return convertColorEffect(value2);
    }

    private String convertWhiteBalance(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.CONTROL_AWB_MODE_AUTO:
            value = WHITE_BALANCE_DEFAULT;
            break;
        case CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
            value = "cloudy-daylight";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT:
            value = "daylight";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT:
            value = "fluorescent";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT:
            value = "incandescent";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_SHADE:
            value = "shade";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_TWILIGHT:
            value = "twilight";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT:
            value = "warm-fluorescent";
            break;
        case CameraMetadata.CONTROL_AWB_MODE_OFF:
            value = "manual";
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown white balance: " + value2);
            value = null;
            break;
        }
        return value;
    }

    /** Whether we should allow manual white balance, even if the device supports CONTROL_AWB_MODE_OFF.
     */
    private boolean allowManualWB() {
        boolean is_nexus6 = Build.MODEL.toLowerCase(Locale.US).contains("nexus 6");
        // manual white balance doesn't seem to work on Nexus 6!
        return !is_nexus6;
    }

    @Override
    public SupportedValues setWhiteBalance(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setWhiteBalance: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        if( values2 == null ) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for(int value2 : values2) {
            String this_value = convertWhiteBalance(value2);
            if( this_value != null ) {
                if( value2 == CameraMetadata.CONTROL_AWB_MODE_OFF && !supports_white_balance_temperature ) {
                    // filter
                }
                else {
                    values.add(this_value);
                }
            }
        }
        {
            // re-order so that auto is first, manual is second
            boolean has_auto = values.remove(WHITE_BALANCE_DEFAULT);
            boolean has_manual = values.remove("manual");
            if( has_manual )
                values.add(0, "manual");
            if( has_auto )
                values.add(0, WHITE_BALANCE_DEFAULT);
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, WHITE_BALANCE_DEFAULT);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_AWB_MODE_AUTO;
            switch(supported_values.selected_value) {
                case WHITE_BALANCE_DEFAULT:
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_AUTO;
                    break;
                case "cloudy-daylight":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
                    break;
                case "daylight":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT;
                    break;
                case "fluorescent":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT;
                    break;
                case "incandescent":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT;
                    break;
                case "shade":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_SHADE;
                    break;
                case "twilight":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_TWILIGHT;
                    break;
                case "warm-fluorescent":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT;
                    break;
                case "manual":
                    selected_value2 = CameraMetadata.CONTROL_AWB_MODE_OFF;
                    break;
                default:
                    if (MyDebug.LOG)
                        Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                    break;
            }

            camera_settings.white_balance = selected_value2;
            if( camera_settings.setWhiteBalance(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to set white balance");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                } 
            }
        }
        return supported_values;
    }

    @Override
    public String getWhiteBalance() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE);
        return convertWhiteBalance(value2);
    }

    @Override
    // Returns whether white balance temperature was modified
    public boolean setWhiteBalanceTemperature(int temperature) {
        if( MyDebug.LOG )
            Log.d(TAG, "setWhiteBalanceTemperature: " + temperature);
        if( camera_settings.white_balance == temperature ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        try {
            temperature = Math.max(temperature, min_white_balance_temperature_c);
            temperature = Math.min(temperature, max_white_balance_temperature_c);
            camera_settings.white_balance_temperature = temperature;
            if( camera_settings.setWhiteBalance(previewBuilder) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set white balance temperature");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public int getWhiteBalanceTemperature() {
        return camera_settings.white_balance_temperature;
    }

    private String convertAntiBanding(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO:
            value = ANTIBANDING_DEFAULT;
            break;
        case CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ:
            value = "50hz";
            break;
        case CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ:
            value = "60hz";
            break;
        case CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF:
            value = "off";
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown antibanding: " + value2);
            value = null;
            break;
        }
        return value;
    }

    @Override
    public SupportedValues setAntiBanding(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setAntiBanding: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES );
        if( values2 == null ) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for(int value2 : values2) {
            String this_value = convertAntiBanding(value2);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, ANTIBANDING_DEFAULT);
        if( supported_values != null ) {
            // for antibanding, if the requested value isn't available, we don't modify it at all
            // (so we stick with the device's default setting)
            if( supported_values.selected_value.equals(value) ) {
                int selected_value2 = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO;
                switch(supported_values.selected_value) {
                    case ANTIBANDING_DEFAULT:
                        selected_value2 = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO;
                        break;
                    case "50hz":
                        selected_value2 = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ;
                        break;
                    case "60hz":
                        selected_value2 = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ;
                        break;
                    case "off":
                        selected_value2 = CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF;
                        break;
                    default:
                        if( MyDebug.LOG )
                            Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                        break;
                }

                camera_settings.has_antibanding = true;
                camera_settings.antibanding = selected_value2;
                if( camera_settings.setAntiBanding(previewBuilder) ) {
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( MyDebug.LOG ) {
                            Log.e(TAG, "failed to set antibanding");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                    }
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getAntiBanding() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE);
        return convertAntiBanding(value2);
    }

    private String convertEdgeMode(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.EDGE_MODE_FAST:
            value = "fast";
            break;
        case CameraMetadata.EDGE_MODE_HIGH_QUALITY:
            value = "high_quality";
            break;
        case CameraMetadata.EDGE_MODE_OFF:
            value = "off";
            break;
        case CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG:
            // we don't make use of zero shutter lag
            value = null;
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown edge_mode: " + value2);
            value = null;
            break;
        }
        return value;
    }

    @Override
    public SupportedValues setEdgeMode(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setEdgeMode: " + value);
        int [] values2 = characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
        if( values2 == null ) {
            return null;
        }
        List<String> values = new ArrayList<>();
        values.add(EDGE_MODE_DEFAULT);
        for(int value2 : values2) {
            String this_value = convertEdgeMode(value2);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, EDGE_MODE_DEFAULT);
        if( supported_values != null ) {
            // for edge mode, if the requested value isn't available, we don't modify it at all
            if( supported_values.selected_value.equals(value) ) {
                boolean has_edge_mode = false;
                int selected_value2 = CameraMetadata.EDGE_MODE_FAST;
                // if EDGE_MODE_DEFAULT, this means to stick with the device default
                if( !value.equals(EDGE_MODE_DEFAULT) ) {
                    switch(supported_values.selected_value) {
                        case "fast":
                            has_edge_mode = true;
                            selected_value2 = CameraMetadata.EDGE_MODE_FAST;
                            break;
                        case "high_quality":
                            has_edge_mode = true;
                            selected_value2 = CameraMetadata.EDGE_MODE_HIGH_QUALITY;
                            break;
                        case "off":
                            has_edge_mode = true;
                            selected_value2 = CameraMetadata.EDGE_MODE_OFF;
                            break;
                        default:
                            if( MyDebug.LOG )
                                Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                            break;
                    }
                }

                if( camera_settings.has_edge_mode != has_edge_mode || camera_settings.edge_mode != selected_value2 ) {
                    camera_settings.has_edge_mode = has_edge_mode;
                    camera_settings.edge_mode = selected_value2;
                    if( camera_settings.setEdgeMode(previewBuilder) ) {
                        try {
                            setRepeatingRequest();
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to set edge_mode");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getEdgeMode() {
        if( previewBuilder.get(CaptureRequest.EDGE_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.EDGE_MODE);
        return convertEdgeMode(value2);
    }

    private String convertNoiseReductionMode(int value2) {
        String value;
        switch( value2 ) {
        case CameraMetadata.NOISE_REDUCTION_MODE_FAST:
            value = "fast";
            break;
        case CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY:
            value = "high_quality";
            break;
        case CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL:
            value = "minimal";
            break;
        case CameraMetadata.NOISE_REDUCTION_MODE_OFF:
            value = "off";
            break;
        case CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG:
            // we don't make use of zero shutter lag
            value = null;
            break;
        default:
            if( MyDebug.LOG )
                Log.d(TAG, "unknown noise_reduction_mode: " + value2);
            value = null;
            break;
        }
        return value;
    }

    @Override
    public SupportedValues setNoiseReductionMode(String value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setNoiseReductionMode: " + value);
        int [] values2 = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES );
        if( values2 == null ) {
            return null;
        }
        List<String> values = new ArrayList<>();
        values.add(NOISE_REDUCTION_MODE_DEFAULT);
        for(int value2 : values2) {
            String this_value = convertNoiseReductionMode(value2);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, NOISE_REDUCTION_MODE_DEFAULT);
        if( supported_values != null ) {
            // for noise reduction, if the requested value isn't available, we don't modify it at all
            if( supported_values.selected_value.equals(value) ) {
                boolean has_noise_reduction_mode = false;
                int selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_FAST;
                // if NOISE_REDUCTION_MODE_DEFAULT, this means to stick with the device default
                if( !value.equals(NOISE_REDUCTION_MODE_DEFAULT) ) {
                    switch(supported_values.selected_value) {
                        case "fast":
                            has_noise_reduction_mode = true;
                            selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_FAST;
                            break;
                        case "high_quality":
                            has_noise_reduction_mode = true;
                            selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY;
                            break;
                        case "minimal":
                            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                                has_noise_reduction_mode = true;
                                selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL;
                            }
                            else {
                                // shouldn't ever be here, as NOISE_REDUCTION_MODE_MINIMAL shouldn't be a supported value!
                                // treat as fast instead
                                Log.e(TAG, "noise reduction minimal, but pre-Android M!");
                                has_noise_reduction_mode = true;
                                selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_FAST;
                            }
                            break;
                        case "off":
                            has_noise_reduction_mode = true;
                            selected_value2 = CameraMetadata.NOISE_REDUCTION_MODE_OFF;
                            break;
                        default:
                            if( MyDebug.LOG )
                                Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
                            break;
                    }
                }

                if( camera_settings.has_noise_reduction_mode != has_noise_reduction_mode || camera_settings.noise_reduction_mode != selected_value2 ) {
                    camera_settings.has_noise_reduction_mode = has_noise_reduction_mode;
                    camera_settings.noise_reduction_mode = selected_value2;
                    if( camera_settings.setNoiseReductionMode(previewBuilder) ) {
                        try {
                            setRepeatingRequest();
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to set noise_reduction_mode");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getNoiseReductionMode() {
        if( previewBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE);
        return convertNoiseReductionMode(value2);
    }

    @Override
    public SupportedValues setISO(String value) {
        // not supported for CameraController2 - but Camera2 devices that don't support manual ISO can call this,
        // so assume this is for auto ISO
        this.setManualISO(false, 0);
        return null;
    }

    @Override
    public String getISOKey() {
        return "";
    }

    @Override
    public void setManualISO(boolean manual_iso, int iso) {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualISO: " + manual_iso);
        try {
            if( manual_iso ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "switch to iso: " + iso);
                Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE); // may be null on some devices
                if( iso_range == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "iso not supported");
                    return;
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "iso range from " + iso_range.getLower() + " to " + iso_range.getUpper());

                camera_settings.has_iso = true;
                iso = Math.max(iso, iso_range.getLower());
                iso = Math.min(iso, iso_range.getUpper());
                camera_settings.iso = iso;
            }
            else {
                camera_settings.has_iso = false;
                camera_settings.iso = 0;
            }
            updateUseFakePrecaptureMode(camera_settings.flash_value);

            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set ISO");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public boolean isManualISO() {
        return camera_settings.has_iso;
    }

    @Override
    // Returns whether ISO was modified
    // N.B., use setManualISO() to switch between auto and manual mode
    public boolean setISO(int iso) {
        if( MyDebug.LOG )
            Log.d(TAG, "setISO: " + iso);
        if( camera_settings.iso == iso ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        try {
            camera_settings.iso = iso;
            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set ISO");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public int getISO() {
        return camera_settings.iso;
    }

    @Override
    public long getExposureTime() {
        return camera_settings.exposure_time;
    }

    @Override
    // Returns whether exposure time was modified
    // N.B., use setISO(String) to switch between auto and manual mode
    public boolean setExposureTime(long exposure_time) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setExposureTime: " + exposure_time);
            Log.d(TAG, "current exposure time: " + camera_settings.exposure_time);
        }
        if( camera_settings.exposure_time == exposure_time ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        try {
            camera_settings.exposure_time = exposure_time;
            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set exposure time");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
        return true;
    }

    @Override
    public void setAperture(float aperture) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setAperture: " + aperture);
            Log.d(TAG, "current aperture: " + camera_settings.aperture);
        }
        if( camera_settings.has_aperture && camera_settings.aperture == aperture ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already set");
        }
        try {
            camera_settings.has_aperture = true;
            camera_settings.aperture = aperture;
            if( camera_settings.setAperture(previewBuilder) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set aperture");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public Size getPictureSize() {
        return new Size(picture_width, picture_height);
    }

    @Override
    public void setPictureSize(int width, int height) {
        if( MyDebug.LOG )
            Log.d(TAG, "setPictureSize: " + width + " x " + height);
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
            if( MyDebug.LOG )
                Log.e(TAG, "can't set picture size when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.picture_width = width;
        this.picture_height = height;
    }

    @Override
    public void setRaw(boolean want_raw, int max_raw_images) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setRaw: " + want_raw);
            Log.d(TAG, "max_raw_images: " + max_raw_images);
        }
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.want_raw == want_raw && this.max_raw_images == max_raw_images ) {
            return;
        }
        if( want_raw && this.raw_size == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "can't set raw when raw not supported");
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as it affects how we create the imageReader
            if( MyDebug.LOG )
                Log.e(TAG, "can't set raw when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.want_raw = want_raw;
        this.max_raw_images = max_raw_images;
    }

    @Override
    public void setVideoHighSpeed(boolean want_video_high_speed) {
        if( MyDebug.LOG )
            Log.d(TAG, "setVideoHighSpeed: " + want_video_high_speed);
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.want_video_high_speed == want_video_high_speed ) {
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as it affects how we create the session
            if( MyDebug.LOG )
                Log.e(TAG, "can't set high speed when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.want_video_high_speed = want_video_high_speed;
        this.is_video_high_speed = false; // reset just to be safe
    }

    @Override
    public void setBurstType(BurstType burst_type) {
        if( MyDebug.LOG )
            Log.d(TAG, "setBurstType: " + burst_type);
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.burst_type == burst_type ) {
            return;
        }
        /*if( captureSession != null ) {
            // can only call this when captureSession not created - as it affects how we create the imageReader
            if( MyDebug.LOG )
                Log.e(TAG, "can't set burst type when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }*/
        this.burst_type = burst_type;
        updateUseFakePrecaptureMode(camera_settings.flash_value);
        camera_settings.setAEMode(previewBuilder, false); // may need to set the ae mode, as flash is disabled for burst modes
    }

    @Override
    public BurstType getBurstType() {
        return burst_type;
    }

    @Override
    public void setExpoBracketingNImages(int n_images) {
        if( MyDebug.LOG )
            Log.d(TAG, "setExpoBracketingNImages: " + n_images);
        if( n_images <= 1 || (n_images % 2) == 0 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "n_images should be an odd number greater than 1");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        if( n_images > max_expo_bracketing_n_images ) {
            n_images = max_expo_bracketing_n_images;
            if( MyDebug.LOG )
                Log.e(TAG, "limiting n_images to max of " + n_images);
        }
        this.expo_bracketing_n_images = n_images;
    }

    @Override
    public void setExpoBracketingStops(double stops) {
        if( MyDebug.LOG )
            Log.d(TAG, "setExpoBracketingStops: " + stops);
        if( stops <= 0.0 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "stops should be positive");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.expo_bracketing_stops = stops;
    }

    @Override
    public void setUseExpoFastBurst(boolean use_expo_fast_burst) {
        if( MyDebug.LOG )
            Log.d(TAG, "setUseExpoFastBurst: " + use_expo_fast_burst);
        this.use_expo_fast_burst = use_expo_fast_burst;
    }

    @Override
    public boolean isBurstOrExpo() {
        return this.burst_type != BurstType.BURSTTYPE_NONE;
    }

    @Override
    public boolean isCapturingBurst() {
        if( !isBurstOrExpo() )
            return false;
        if( burst_type == BurstType.BURSTTYPE_CONTINUOUS )
            return continuous_burst_in_progress || n_burst > 0 || n_burst_raw > 0;
        return getBurstTotal() > 1 && getNBurstTaken() < getBurstTotal();
    }

    @Override
    public int getNBurstTaken() {
        return n_burst_taken;
    }

    @Override
    public int getBurstTotal() {
        if( burst_type == BurstType.BURSTTYPE_CONTINUOUS )
            return 0; // total burst size is unknown
        return n_burst_total;
    }
    @Override
    public void setOptimiseAEForDRO(boolean optimise_ae_for_dro) {
        if( MyDebug.LOG )
            Log.d(TAG, "setOptimiseAEForDRO: " + optimise_ae_for_dro);
        boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        if( is_oneplus ) {
            // OnePlus 3T has preview corruption / camera freezing problems when using manual shutter speeds
            // So best not to modify auto-exposure for DRO
            this.optimise_ae_for_dro = false;
            if( MyDebug.LOG )
                Log.d(TAG, "don't modify ae for OnePlus");
        }
        else {
            this.optimise_ae_for_dro = optimise_ae_for_dro;
        }
    }

    @Override
    public void setBurstNImages(int burst_requested_n_images) {
        if( MyDebug.LOG )
            Log.d(TAG, "setBurstNImages: " + burst_requested_n_images);
        this.burst_requested_n_images = burst_requested_n_images;
    }

    @Override
    public void setBurstForNoiseReduction(boolean burst_for_noise_reduction, boolean noise_reduction_low_light) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setBurstForNoiseReduction: " + burst_for_noise_reduction);
            Log.d(TAG, "noise_reduction_low_light: " + noise_reduction_low_light);
        }
        this.burst_for_noise_reduction = burst_for_noise_reduction;
        this.noise_reduction_low_light = noise_reduction_low_light;
    }

    @Override
    public boolean isContinuousBurstInProgress() {
        return continuous_burst_in_progress;
    }

    @Override
    public void stopContinuousBurst() {
        if( MyDebug.LOG )
            Log.d(TAG, "stopContinuousBurst");
        continuous_burst_in_progress = false;
    }

    @Override
    public void stopFocusBracketingBurst() {
        if( MyDebug.LOG )
            Log.d(TAG, "stopFocusBracketingBurst");
        if( burst_type == BurstType.BURSTTYPE_FOCUS ) {
            focus_bracketing_in_progress = false;
        }
        else {
            Log.e(TAG, "stopFocusBracketingBurst burst_type is: " + burst_type);
        }
    }

    @Override
    public void setUseCamera2FakeFlash(boolean use_fake_precapture) {
        if( MyDebug.LOG )
            Log.d(TAG, "setUseCamera2FakeFlash: " + use_fake_precapture);
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.use_fake_precapture == use_fake_precapture ) {
            return;
        }
        this.use_fake_precapture = use_fake_precapture;
        this.use_fake_precapture_mode = use_fake_precapture;
        // no need to call updateUseFakePrecaptureMode(), as this method should only be called after first creating camera controller
    }
    
    @Override
    public boolean getUseCamera2FakeFlash() {
        return this.use_fake_precapture;
    }

    private void createPictureImageReader() {
        if( MyDebug.LOG )
            Log.d(TAG, "createPictureImageReader");
        if( captureSession != null ) {
            // can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
            if( MyDebug.LOG )
                Log.e(TAG, "can't create picture image reader when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        closePictureImageReader();
        if( picture_width == 0 || picture_height == 0 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "application needs to call setPictureSize()");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        // maxImages only needs to be 2, as we always read the JPEG data and close the image straight away in the imageReader
        imageReader = ImageReader.newInstance(picture_width, picture_height, ImageFormat.JPEG, 2);
        //imageReader = ImageReader.newInstance(picture_width, picture_height, ImageFormat.YUV_420_888, 2);
        if( MyDebug.LOG ) {
            Log.d(TAG, "created new imageReader: " + imageReader.toString());
            Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
        }
        // It's intentional that we pass a handler on null, so the OnImageAvailableListener runs on the UI thread.
        // If ever we want to change this on future, we should ensure that all image available listeners (JPEG+RAW) are
        // using the same handler/thread.
        imageReader.setOnImageAvailableListener(new OnImageAvailableListener(), null);
        if( want_raw && raw_size != null&& !previewIsVideoMode  ) {
            // unlike the JPEG imageReader, we can't read the data and close the image straight away, so we need to allow a larger
            // value for maxImages
            imageReaderRaw = ImageReader.newInstance(raw_size.getWidth(), raw_size.getHeight(), ImageFormat.RAW_SENSOR, max_raw_images);
            if( MyDebug.LOG ) {
                Log.d(TAG, "created new imageReaderRaw: " + imageReaderRaw.toString());
                Log.d(TAG, "imageReaderRaw surface: " + imageReaderRaw.getSurface().toString());
            }
            // see note above for imageReader.setOnImageAvailableListener for why we use a null handler
            imageReaderRaw.setOnImageAvailableListener(onRawImageAvailableListener = new OnRawImageAvailableListener(), null);
        }
    }
    
    private void clearPending() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearPending");
        pending_burst_images.clear();
        pending_burst_images_raw.clear();
        pending_raw_image = null;
        if( onRawImageAvailableListener != null ) {
            onRawImageAvailableListener.clear();
        }
        slow_burst_capture_requests = null;
        n_burst = 0;
        n_burst_taken = 0;
        n_burst_total = 0;
        n_burst_raw = 0;
        burst_single_request = false;
        slow_burst_start_ms = 0;
    }
    
    private void takePendingRaw() {
        if( MyDebug.LOG )
            Log.d(TAG, "takePendingRaw");
        // takePendingRaw() always called on UI thread, and pending_raw_image only used on UI thread, so shouldn't need to
        // synchronize for that
        if( pending_raw_image != null ) {
            synchronized( background_camera_lock ) {
                raw_todo = false;
            }
            // don't call callback with lock
            picture_cb.onRawPictureTaken(pending_raw_image);
            // pending_raw_image should be closed by the application (we don't do it here, so that applications can keep hold of the data, e.g., in a queue for background processing)
            pending_raw_image = null;
            if( onRawImageAvailableListener != null ) {
                onRawImageAvailableListener.clear();
            }
        }
    }

    private void checkImagesCompleted() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkImagesCompleted");
        boolean completed = false;
        boolean take_pending_raw = false;
        synchronized( background_camera_lock ) {
            if( !done_all_captures  ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "still waiting for captures");
            }
            else if( picture_cb == null ) {
                // just in case?
                if( MyDebug.LOG )
                    Log.d(TAG, "no picture_cb");
            }
            else if( !jpeg_todo && !raw_todo ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "all image callbacks now completed");
                completed = true;
            }
            else if( !jpeg_todo && pending_raw_image != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "jpeg callback already done, can now call pending raw callback");
                take_pending_raw = true;
                completed = true;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "need to wait for jpeg and/or raw callback");
            }
        }

        // need to call callbacks without a lock
        if( take_pending_raw ) {
            takePendingRaw();
            if( MyDebug.LOG )
                Log.d(TAG, "all image callbacks now completed");
        }
        if( completed ) {
            // need to set picture_cb to null before calling onCompleted, as that may reenter CameraController to take another photo (if in auto-repeat burst mode) - see testTakePhotoRepeat()
            PictureCallback cb = picture_cb;
            picture_cb = null;
            cb.onCompleted();
            synchronized( background_camera_lock ) {
                if( burst_type == BurstType.BURSTTYPE_FOCUS )
                    focus_bracketing_in_progress = false;
            }
        }
    }

    @Override
    public Size getPreviewSize() {
        return new Size(preview_width, preview_height);
    }

    @Override
    public void setPreviewSize(int width, int height) {
        if( MyDebug.LOG )
            Log.d(TAG, "setPreviewSize: " + width + " , " + height);
        preview_width = width;
        preview_height = height;
        /*if( previewImageReader != null ) {
            previewImageReader.close();
        }
        previewImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2); 
        */
    }

    @Override
    public void setVideoStabilization(boolean enabled) {
        if( MyDebug.LOG )
            Log.d(TAG, "setVideoStabilization: " + enabled);
        camera_settings.video_stabilization = enabled;
        camera_settings.setStabilization(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set video stabilization");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }

    @Override
    public boolean getOpticalStabilization() {
        Integer ois_mode = previewBuilder.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
        if( ois_mode == null )
            return false;
        return( ois_mode == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON );
    }

    @Override
    public boolean getVideoStabilization() {
        return camera_settings.video_stabilization;
    }

    @Override
    public void setTonemapProfile(TonemapProfile tonemap_profile, float log_profile_strength, float gamma) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setTonemapProfile: " + tonemap_profile);
            Log.d(TAG, "log_profile_strength: " + log_profile_strength);
            Log.d(TAG, "gamma: " + gamma);
        }
        if( camera_settings.tonemap_profile == tonemap_profile &&
                camera_settings.log_profile_strength == log_profile_strength &&
                camera_settings.gamma_profile == gamma )
            return; // no change

        camera_settings.tonemap_profile = tonemap_profile;

        if( tonemap_profile == TonemapProfile.TONEMAPPROFILE_LOG )
            camera_settings.log_profile_strength = log_profile_strength;
        else
            camera_settings.log_profile_strength = 0.0f;

        if( tonemap_profile == TonemapProfile.TONEMAPPROFILE_GAMMA )
            camera_settings.gamma_profile = gamma;
        else
            camera_settings.gamma_profile = 0.0f;

        camera_settings.setTonemapProfile(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set log profile");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public TonemapProfile getTonemapProfile() {
        return camera_settings.tonemap_profile;
    }

    /** For testing.
     */
    public CaptureRequest.Builder testGetPreviewBuilder() {
        return previewBuilder;
    }

    public TonemapCurve testGetTonemapCurve() {
        return previewBuilder.get(CaptureRequest.TONEMAP_CURVE);
    }

    @Override
    public int getJpegQuality() {
        return this.camera_settings.jpeg_quality;
    }

    @Override
    public void setJpegQuality(int quality) {
        if( quality < 0 || quality > 100 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "invalid jpeg quality" + quality);
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.camera_settings.jpeg_quality = (byte)quality;
    }

    @Override
    public int getZoom() {
        return this.current_zoom_value;
    }

    @Override
    public void setZoom(int value) {
        if( zoom_ratios == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "zoom not supported");
            return;
        }
        if( value < 0 || value > zoom_ratios.size() ) {
            if( MyDebug.LOG )
                Log.e(TAG, "invalid zoom value" + value);
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        float zoom = zoom_ratios.get(value)/100.0f;
        Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int left = sensor_rect.width()/2;
        int right = left;
        int top = sensor_rect.height()/2;
        int bottom = top;
        int hwidth = (int)(sensor_rect.width() / (2.0*zoom));
        int hheight = (int)(sensor_rect.height() / (2.0*zoom));
        left -= hwidth;
        right += hwidth;
        top -= hheight;
        bottom += hheight;
        if( MyDebug.LOG ) {
            Log.d(TAG, "zoom: " + zoom);
            Log.d(TAG, "hwidth: " + hwidth);
            Log.d(TAG, "hheight: " + hheight);
            Log.d(TAG, "sensor_rect left: " + sensor_rect.left);
            Log.d(TAG, "sensor_rect top: " + sensor_rect.top);
            Log.d(TAG, "sensor_rect right: " + sensor_rect.right);
            Log.d(TAG, "sensor_rect bottom: " + sensor_rect.bottom);
            Log.d(TAG, "left: " + left);
            Log.d(TAG, "top: " + top);
            Log.d(TAG, "right: " + right);
            Log.d(TAG, "bottom: " + bottom);
            /*Rect current_rect = previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            Log.d(TAG, "current_rect left: " + current_rect.left);
            Log.d(TAG, "current_rect top: " + current_rect.top);
            Log.d(TAG, "current_rect right: " + current_rect.right);
            Log.d(TAG, "current_rect bottom: " + current_rect.bottom);*/
        }
        camera_settings.scalar_crop_region = new Rect(left, top, right, bottom);
        camera_settings.setCropRegion(previewBuilder);
        this.current_zoom_value = value;
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set zoom");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }
    
    @Override
    public int getExposureCompensation() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null )
            return 0;
        return previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
    }

    @Override
    // Returns whether exposure was modified
    public boolean setExposureCompensation(int new_exposure) {
        camera_settings.has_ae_exposure_compensation = true;
        camera_settings.ae_exposure_compensation = new_exposure;
        if( camera_settings.setExposureCompensation(previewBuilder) ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to set exposure compensation");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            } 
            return true;
        }
        return false;
    }
    
    @Override
    public void setPreviewFpsRange(int min, int max) {
        if( MyDebug.LOG )
            Log.d(TAG, "setPreviewFpsRange: " + min +"-" + max);
        camera_settings.ae_target_fps_range = new Range<>(min / 1000, max / 1000);
//      Frame duration is in nanoseconds.  Using min to be safe.
        camera_settings.sensor_frame_duration =
                (long)(1.0 / (min / 1000.0) * 1000000000L);

        try {
            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set preview fps range to " + min +"-" + max);
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public void clearPreviewFpsRange() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearPreviewFpsRange");
        // needed e.g. on Nokia 8 when switching back from slow motion to regular speed, in order to reset to the regular
        // frame rate
        if( camera_settings.ae_target_fps_range != null || camera_settings.sensor_frame_duration != 0 ) {
            // set back to default
            camera_settings.ae_target_fps_range = null;
            camera_settings.sensor_frame_duration = 0;
            createPreviewRequest();
            // createPreviewRequest() needed so that the values in the previewBuilder reset to default values, for
            // CONTROL_AE_TARGET_FPS_RANGE and SENSOR_FRAME_DURATION

            try {
                if( camera_settings.setAEMode(previewBuilder, false) ) {
                    setRepeatingRequest();
                }
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to clear preview fps range");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<int[]> getSupportedPreviewFpsRange() {
        List<int[]> l = new ArrayList<>();

        List<int[]> rr = want_video_high_speed ? hs_fps_ranges : ae_fps_ranges;
        for (int[] r : rr) {
            int[] ir = { r[0] * 1000, r[1] * 1000 };
            l.add( ir );
        }
        if( MyDebug.LOG )
            Log.d(TAG, "   using " + (want_video_high_speed ? "high speed" : "ae")  + " preview fps ranges");

        return l;
    }

    @Override
    public void setFocusValue(String focus_value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusValue: " + focus_value);
        int focus_mode;
        switch(focus_value) {
            case "focus_mode_auto":
            case "focus_mode_locked":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
                break;
            case "focus_mode_infinity":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
                camera_settings.focus_distance = 0.0f;
                break;
            case "focus_mode_manual2":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
                camera_settings.focus_distance = camera_settings.focus_distance_manual;
                break;
            case "focus_mode_macro":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_MACRO;
                break;
            case "focus_mode_edof":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_EDOF;
                break;
            case "focus_mode_continuous_picture":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                break;
            case "focus_mode_continuous_video":
                focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                break;
            default:
                if (MyDebug.LOG)
                    Log.d(TAG, "setFocusValue() received unknown focus value " + focus_value);
                return;
        }
        camera_settings.has_af_mode = true;
        camera_settings.af_mode = focus_mode;
        camera_settings.setFocusMode(previewBuilder);
        camera_settings.setFocusDistance(previewBuilder); // also need to set distance, in case changed between infinity, manual or other modes
        //camera_settings.setTonemapProfile(previewBuilder); // testing - if using focus mode to test video profiles, see test_new flag
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set focus mode");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }
    
    private String convertFocusModeToValue(int focus_mode) {
        if( MyDebug.LOG )
            Log.d(TAG, "convertFocusModeToValue: " + focus_mode);
        String focus_value = "";
        switch (focus_mode) {
            case CaptureRequest.CONTROL_AF_MODE_AUTO:
                focus_value = "focus_mode_auto";
                break;
            case CaptureRequest.CONTROL_AF_MODE_MACRO:
                focus_value = "focus_mode_macro";
                break;
            case CaptureRequest.CONTROL_AF_MODE_EDOF:
                focus_value = "focus_mode_edof";
                break;
            case CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                focus_value = "focus_mode_continuous_picture";
                break;
            case CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                focus_value = "focus_mode_continuous_video";
                break;
            case CaptureRequest.CONTROL_AF_MODE_OFF:
                focus_value = "focus_mode_manual2"; // n.b., could be infinity
                break;
        }
        return focus_value;
    }
    
    @Override
    public String getFocusValue() {
        Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == null )
            focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        return convertFocusModeToValue(focus_mode);
    }

    @Override
    public float getFocusDistance() {
        return camera_settings.focus_distance;
    }

    @Override
    public boolean setFocusDistance(float focus_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusDistance: " + focus_distance);
        if( camera_settings.focus_distance == focus_distance ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        camera_settings.focus_distance = focus_distance;
        camera_settings.focus_distance_manual = focus_distance;
        camera_settings.setFocusDistance(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set focus distance");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
        return true;
    }

    @Override
    public void setFocusBracketingNImages(int n_images) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusBracketingNImages: " + n_images);
        this.focus_bracketing_n_images = n_images;
    }

    @Override
    public void setFocusBracketingAddInfinity(boolean focus_bracketing_add_infinity) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusBracketingAddInfinity: " + focus_bracketing_add_infinity);
        this.focus_bracketing_add_infinity = focus_bracketing_add_infinity;
    }

    @Override
    public void setFocusBracketingSourceDistance(float focus_bracketing_source_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusBracketingSourceDistance: " + focus_bracketing_source_distance);
        this.focus_bracketing_source_distance = focus_bracketing_source_distance;
    }

    @Override
    public float getFocusBracketingSourceDistance() {
        return this.focus_bracketing_source_distance;
    }

    @Override
    public void setFocusBracketingTargetDistance(float focus_bracketing_target_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFocusBracketingTargetDistance: " + focus_bracketing_target_distance);
        this.focus_bracketing_target_distance = focus_bracketing_target_distance;
    }

    @Override
    public float getFocusBracketingTargetDistance() {
        return this.focus_bracketing_target_distance;
    }

    /** Decides whether we should be using fake precapture mode.
     */
    private void updateUseFakePrecaptureMode(String flash_value) {
        if( MyDebug.LOG )
            Log.d(TAG, "useFakePrecaptureMode: " + flash_value);
        boolean frontscreen_flash = flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on");
        if( frontscreen_flash ) {
            use_fake_precapture_mode = true;
        }
        else if( burst_type != BurstType.BURSTTYPE_NONE )
            use_fake_precapture_mode = true;
        else if( camera_settings.has_iso )
            use_fake_precapture_mode = true;
        else {
            use_fake_precapture_mode = use_fake_precapture;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "use_fake_precapture_mode set to: " + use_fake_precapture_mode);
    }

    @Override
    public void setFlashValue(String flash_value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setFlashValue: " + flash_value);
        if( camera_settings.flash_value.equals(flash_value) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "flash value already set");
            return;
        }

        try {
            updateUseFakePrecaptureMode(flash_value);
            
            if( camera_settings.flash_value.equals("flash_torch") && !flash_value.equals("flash_off") ) {
                // hack - if switching to something other than flash_off, we first need to turn torch off, otherwise torch remains on (at least on Nexus 6 and Nokia 8)
                camera_settings.flash_value = "flash_off";
                camera_settings.setAEMode(previewBuilder, false);
                CaptureRequest request = previewBuilder.build();
    
                // need to wait until torch actually turned off
                camera_settings.flash_value = flash_value;
                camera_settings.setAEMode(previewBuilder, false);
                push_repeating_request_when_torch_off = true;
                push_repeating_request_when_torch_off_id = request;
    
                setRepeatingRequest(request);
            }
            else {
                camera_settings.flash_value = flash_value;
                if( camera_settings.setAEMode(previewBuilder, false) ) {
                    setRepeatingRequest();
                }
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set flash mode");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }

    @Override
    public String getFlashValue() {
        // returns "" if flash isn't supported
        if( !characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
            return "";
        }
        return camera_settings.flash_value;
    }

    @Override
    public void setRecordingHint(boolean hint) {
        // not relevant for CameraController2
    }

    @Override
    public void setAutoExposureLock(boolean enabled) {
        camera_settings.ae_lock = enabled;
        camera_settings.setAutoExposureLock(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set auto exposure lock");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }
    
    @Override
    public boolean getAutoExposureLock() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK) == null )
            return false;
        return previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
    }

    @Override
    public void setAutoWhiteBalanceLock(boolean enabled) {
        camera_settings.wb_lock = enabled;
        camera_settings.setAutoWhiteBalanceLock(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to set auto white balance lock");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public boolean getAutoWhiteBalanceLock() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AWB_LOCK) == null )
            return false;
        return previewBuilder.get(CaptureRequest.CONTROL_AWB_LOCK);
    }

    @Override
    public void setRotation(int rotation) {
        this.camera_settings.rotation = rotation;
    }

    @Override
    public void setLocationInfo(Location location) {
        // don't log location, in case of privacy!
        if( MyDebug.LOG )
            Log.d(TAG, "setLocationInfo");
        this.camera_settings.location = location;
    }

    @Override
    public void removeLocationInfo() {
        this.camera_settings.location = null;
    }

    @Override
    public void enableShutterSound(boolean enabled) {
        this.sounds_enabled = enabled;
    }

    private void playSound(int soundName) {
        if( sounds_enabled ) {
            // on some devices (e.g., Samsung Galaxy S10e), need to check whether phone on silent!
            AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if( audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ) {
                media_action_sound.play(soundName);
            }
        }
    }

    /** Returns the viewable rect - this is crop region if available.
     *  We need this as callers will pass in (or expect returned) CameraController.Area values that
     *  are relative to the current view (i.e., taking zoom into account) (the old Camera API in
     *  CameraController1 always works in terms of the current view, whilst Camera2 works in terms
     *  of the full view always). Similarly for the rect field in CameraController.Face.
     */
    private Rect getViewableRect() {
        if( previewBuilder != null ) {
            Rect crop_rect = previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            if( crop_rect != null ) {
                return crop_rect;
            }
        }
        Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        sensor_rect.right -= sensor_rect.left;
        sensor_rect.left = 0;
        sensor_rect.bottom -= sensor_rect.top;
        sensor_rect.top = 0;
        return sensor_rect;
    }
    
    private Rect convertRectToCamera2(Rect crop_rect, Rect rect) {
        // CameraController.Area is always [-1000, -1000] to [1000, 1000] for the viewable region
        // but for CameraController2, we must convert to be relative to the crop region
        double left_f = (rect.left+1000)/2000.0;
        double top_f = (rect.top+1000)/2000.0;
        double right_f = (rect.right+1000)/2000.0;
        double bottom_f = (rect.bottom+1000)/2000.0;
        int left = (int)(crop_rect.left + left_f * (crop_rect.width()-1));
        int right = (int)(crop_rect.left + right_f * (crop_rect.width()-1));
        int top = (int)(crop_rect.top + top_f * (crop_rect.height()-1));
        int bottom = (int)(crop_rect.top + bottom_f * (crop_rect.height()-1));
        left = Math.max(left, crop_rect.left);
        right = Math.max(right, crop_rect.left);
        top = Math.max(top, crop_rect.top);
        bottom = Math.max(bottom, crop_rect.top);
        left = Math.min(left, crop_rect.right);
        right = Math.min(right, crop_rect.right);
        top = Math.min(top, crop_rect.bottom);
        bottom = Math.min(bottom, crop_rect.bottom);

        return new Rect(left, top, right, bottom);
    }

    private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
        Rect camera2_rect = convertRectToCamera2(sensor_rect, area.rect);
        return new MeteringRectangle(camera2_rect, area.weight);
    }

    private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
        // inverse of convertRectToCamera2()
        double left_f = (camera2_rect.left-crop_rect.left)/(double)(crop_rect.width()-1);
        double top_f = (camera2_rect.top-crop_rect.top)/(double)(crop_rect.height()-1);
        double right_f = (camera2_rect.right-crop_rect.left)/(double)(crop_rect.width()-1);
        double bottom_f = (camera2_rect.bottom-crop_rect.top)/(double)(crop_rect.height()-1);
        int left = (int)(left_f * 2000) - 1000;
        int right = (int)(right_f * 2000) - 1000;
        int top = (int)(top_f * 2000) - 1000;
        int bottom = (int)(bottom_f * 2000) - 1000;

        left = Math.max(left, -1000);
        right = Math.max(right, -1000);
        top = Math.max(top, -1000);
        bottom = Math.max(bottom, -1000);
        left = Math.min(left, 1000);
        right = Math.min(right, 1000);
        top = Math.min(top, 1000);
        bottom = Math.min(bottom, 1000);

        return new Rect(left, top, right, bottom);
    }

    private Area convertMeteringRectangleToArea(Rect sensor_rect, MeteringRectangle metering_rectangle) {
        Rect area_rect = convertRectFromCamera2(sensor_rect, metering_rectangle.getRect());
        return new Area(area_rect, metering_rectangle.getMeteringWeight());
    }
    
    private CameraController.Face convertFromCameraFace(Rect sensor_rect, android.hardware.camera2.params.Face camera2_face) {
        Rect area_rect = convertRectFromCamera2(sensor_rect, camera2_face.getBounds());
        return new CameraController.Face(camera2_face.getScore(), area_rect);
    }

    @Override
    public boolean setFocusAndMeteringArea(List<Area> areas) {
        Rect sensor_rect = getViewableRect();
        if( MyDebug.LOG )
            Log.d(TAG, "sensor_rect: " + sensor_rect.left + " , " + sensor_rect.top + " x " + sensor_rect.right + " , " + sensor_rect.bottom);
        boolean has_focus = false;
        boolean has_metering = false;
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
            has_focus = true;
            camera_settings.af_regions = new MeteringRectangle[areas.size()];
            int i = 0;
            for(CameraController.Area area : areas) {
                camera_settings.af_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
            }
            camera_settings.setAFRegions(previewBuilder);
        }
        else
            camera_settings.af_regions = null;
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
            has_metering = true;
            camera_settings.ae_regions = new MeteringRectangle[areas.size()];
            int i = 0;
            for(CameraController.Area area : areas) {
                camera_settings.ae_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
            }
            camera_settings.setAERegions(previewBuilder);
        }
        else
            camera_settings.ae_regions = null;
        if( has_focus || has_metering ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to set focus and/or metering regions");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            } 
        }
        return has_focus;
    }
    
    @Override
    public void clearFocusAndMetering() {
        Rect sensor_rect = getViewableRect();
        boolean has_focus = false;
        boolean has_metering = false;
        if( sensor_rect.width() <= 0 || sensor_rect.height() <= 0 ) {
            // had a crash on Google Play due to creating a MeteringRectangle with -ve width/height ?!
            camera_settings.af_regions = null;
            camera_settings.ae_regions = null;
        }
        else {
            if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
                has_focus = true;
                camera_settings.af_regions = new MeteringRectangle[1];
                camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
                camera_settings.setAFRegions(previewBuilder);
            }
            else
                camera_settings.af_regions = null;
            if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
                has_metering = true;
                camera_settings.ae_regions = new MeteringRectangle[1];
                camera_settings.ae_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
                camera_settings.setAERegions(previewBuilder);
            }
            else
                camera_settings.ae_regions = null;
        }
        if( has_focus || has_metering ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to clear focus and metering regions");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            } 
        }
    }

    @Override
    public List<Area> getFocusAreas() {
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) == 0 )
            return null;
        MeteringRectangle [] metering_rectangles = previewBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
        if( metering_rectangles == null )
            return null;
        Rect sensor_rect = getViewableRect();
        camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
        if( metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width()-1 && metering_rectangles[0].getRect().bottom == sensor_rect.height()-1 ) {
            // for compatibility with CameraController1
            return null;
        }
        List<Area> areas = new ArrayList<>();
        for(MeteringRectangle metering_rectangle : metering_rectangles) {
            areas.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangle));
        }
        return areas;
    }

    @Override
    public List<Area> getMeteringAreas() {
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) == 0 )
            return null;
        MeteringRectangle [] metering_rectangles = previewBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
        if( metering_rectangles == null )
            return null;
        Rect sensor_rect = getViewableRect();
        if( metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width()-1 && metering_rectangles[0].getRect().bottom == sensor_rect.height()-1 ) {
            // for compatibility with CameraController1
            return null;
        }
        List<Area> areas = new ArrayList<>();
        for(MeteringRectangle metering_rectangle : metering_rectangles) {
            areas.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangle));
        }
        return areas;
    }

    @Override
    public boolean supportsAutoFocus() {
        if( previewBuilder == null )
            return false;
        Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == null )
            return false;
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO || focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO )
            return true;
        return false;
    }

    @Override
    public boolean focusIsContinuous() {
        if( previewBuilder == null )
            return false;
        Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == null )
            return false;
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE || focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO )
            return true;
        return false;
    }

    @Override
    public boolean focusIsVideo() {
        if( previewBuilder == null )
            return false;
        Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == null )
            return false;
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
            return true;
        }
        return false;
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder holder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setPreviewDisplay");
            Log.e(TAG, "SurfaceHolder not supported for CameraController2!");
            Log.e(TAG, "Should use setPreviewTexture() instead");
        }
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public void setPreviewTexture(TextureView texture) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setPreviewTexture: " + texture);
            Log.d(TAG, "surface: " + texture.getSurfaceTexture());
        }
        if( this.texture != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "preview texture already set");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.texture = texture.getSurfaceTexture();
    }

    private void setRepeatingRequest() throws CameraAccessException {
        setRepeatingRequest(previewBuilder.build());
    }

    private void setRepeatingRequest(CaptureRequest request) throws CameraAccessException {
        if( MyDebug.LOG )
            Log.d(TAG, "setRepeatingRequest");
        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            try {
                if( is_video_high_speed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    CameraConstrainedHighSpeedCaptureSession captureSessionHighSpeed = (CameraConstrainedHighSpeedCaptureSession) captureSession;
                    List<CaptureRequest> mPreviewBuilderBurst = captureSessionHighSpeed.createHighSpeedRequestList(request);
                    captureSessionHighSpeed.setRepeatingBurst(mPreviewBuilderBurst, previewCaptureCallback, handler);
                }
                else {
                    captureSession.setRepeatingRequest(request, previewCaptureCallback, handler);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "setRepeatingRequest done");
            }
            catch(IllegalStateException e) {
                if( MyDebug.LOG )
                    Log.d(TAG, "captureSession already closed!");
                e.printStackTrace();
                // got this as a Google Play exception (from onCaptureCompleted->processCompleted) - this means the capture session is already closed
            }
        }
    }

    private void capture() throws CameraAccessException {
        capture(previewBuilder.build());
    }

    private void capture(CaptureRequest request) throws CameraAccessException {
        if( MyDebug.LOG )
            Log.d(TAG, "capture");
        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            captureSession.capture(request, previewCaptureCallback, handler);
        }
    }

    private void createPreviewRequest() {
        if( MyDebug.LOG )
            Log.d(TAG, "createPreviewRequest");
        if( camera == null  ) {
            if( MyDebug.LOG )
                Log.d(TAG, "camera not available!");
            return;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "camera: " + camera);
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewIsVideoMode = false;
            previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
            camera_settings.setupBuilder(previewBuilder, false);
            if( MyDebug.LOG )
                Log.d(TAG, "successfully created preview request");
        }
        catch(CameraAccessException e) {
            //captureSession = null;
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to create capture request");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        } 
    }

    // should synchronize calls to this method using background_camera_lock
    private Surface getPreviewSurface() {
        return surface_texture;
    }

    @Override
    public void updatePreviewTexture() {
        if( MyDebug.LOG )
            Log.d(TAG, "updatePreviewTexture");
        if( texture != null ) {
            if( preview_width == 0 || preview_height == 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "preview size not yet set");
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "preview size: " + preview_width + " x " + preview_height);
                this.test_texture_view_buffer_w = preview_width;
                this.test_texture_view_buffer_h = preview_height;
                texture.setDefaultBufferSize(preview_width, preview_height);
            }
        }
    }

    private void createCaptureSession(final MediaRecorder video_recorder, boolean want_photo_video_recording) throws CameraControllerException {
        if( MyDebug.LOG )
            Log.d(TAG, "create capture session");
        
        if( previewBuilder == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "previewBuilder not present!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        if( camera == null ) {
            if( MyDebug.LOG )
                Log.e(TAG, "no camera");
            return;
        }

        synchronized( background_camera_lock ) {
            if( captureSession != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "close old capture session");
                captureSession.close();
                captureSession = null;
                //pending_request_when_ready = null;
            }
        }

        try {
            if( video_recorder != null ) {
                if( supports_photo_video_recording && !want_video_high_speed && want_photo_video_recording ) {
                    createPictureImageReader();
                }
                else {
                    closePictureImageReader();
                }
            }
            else {
                // in some cases need to recreate picture imageReader and the texture default buffer size (e.g., see test testTakePhotoPreviewPaused())
                createPictureImageReader();
            }
            if( texture != null ) {
                // need to set the texture size
                if( MyDebug.LOG )
                    Log.d(TAG, "set size of preview texture: " + preview_width + " x " + preview_height);
                if( preview_width == 0 || preview_height == 0 ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "application needs to call setPreviewSize()");
                    throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
                }
                updatePreviewTexture();
                // also need to create a new surface for the texture, in case the size has changed - but make sure we remove the old one first!
                synchronized( background_camera_lock ) {
                    if( surface_texture != null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "remove old target: " + surface_texture);
                        previewBuilder.removeTarget(surface_texture);
                    }
                    this.surface_texture = new Surface(texture);
                    if( MyDebug.LOG )
                        Log.d(TAG, "created new target: " + surface_texture);
                }
            }
            if( video_recorder != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "creating capture session for video recording");
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "picture size: " + imageReader.getWidth() + " x " + imageReader.getHeight());
            }
            /*if( MyDebug.LOG )
            Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/
            if( MyDebug.LOG )
                Log.d(TAG, "set preview size: " + this.preview_width + " x " + this.preview_height);

            synchronized( background_camera_lock ) {
                if( video_recorder != null )
                    video_recorder_surface = video_recorder.getSurface();
                else
                    video_recorder_surface = null;
                if( MyDebug.LOG )
                    Log.d(TAG, "video_recorder_surface: " + video_recorder_surface);
            }

            class MyStateCallback extends CameraCaptureSession.StateCallback {
                private boolean callback_done; // must sychronize on this and notifyAll when setting to true
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "onConfigured: " + session);
                        Log.d(TAG, "captureSession was: " + captureSession);
                    }
                    if( camera == null ) {
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "camera is closed");
                        }
                        synchronized( background_camera_lock ) {
                            callback_done = true;
                            background_camera_lock.notifyAll();
                        }
                        return;
                    }
                    synchronized( background_camera_lock ) {
                        captureSession = session;
                        Surface surface = getPreviewSurface();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "add surface to previewBuilder: " + surface);
                        }
                        previewBuilder.addTarget(surface);
                        if( video_recorder != null ) {
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "add video recorder surface to previewBuilder: " + video_recorder_surface);
                            }
                            previewBuilder.addTarget(video_recorder_surface);
                        }
                        try {
                            setRepeatingRequest();
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to start preview");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                            // we indicate that we failed to start the preview by setting captureSession back to null
                            // this will cause a CameraControllerException to be thrown below
                            captureSession = null;
                        }
                    }
                    synchronized( background_camera_lock ) {
                        callback_done = true;
                        background_camera_lock.notifyAll();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "onConfigureFailed: " + session);
                        Log.d(TAG, "captureSession was: " + captureSession);
                    }
                    synchronized( background_camera_lock ) {
                        callback_done = true;
                        background_camera_lock.notifyAll();
                    }
                    // don't throw CameraControllerException here, as won't be caught - instead we throw CameraControllerException below
                }

                /*@Override
                public void onReady(CameraCaptureSession session) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "onReady: " + session);
                    if( pending_request_when_ready != null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "have pending_request_when_ready: " + pending_request_when_ready);
                        CaptureRequest request = pending_request_when_ready;
                        pending_request_when_ready = null;
                        try {
                            captureSession.capture(request, previewCaptureCallback, handler);
                        }
                        catch(CameraAccessException e) {
                            if( MyDebug.LOG ) {
                                Log.e(TAG, "failed to take picture");
                                Log.e(TAG, "reason: " + e.getReason());
                                Log.e(TAG, "message: " + e.getMessage());
                            }
                            e.printStackTrace();
                            jpeg_todo = false;
                            raw_todo = false;
                            picture_cb = null;
                            if( take_picture_error_cb != null ) {
                                take_picture_error_cb.onError();
                                take_picture_error_cb = null;
                            }
                        }
                    }
                }*/
            }
            final MyStateCallback myStateCallback = new MyStateCallback();

            List<Surface> surfaces;
            synchronized( background_camera_lock ) {
                Surface preview_surface = getPreviewSurface();
                if( video_recorder != null ) {
                    if( supports_photo_video_recording && !want_video_high_speed && want_photo_video_recording ) {
                        surfaces = Arrays.asList(preview_surface, video_recorder_surface, imageReader.getSurface());
                    }
                    else {
                        surfaces = Arrays.asList(preview_surface, video_recorder_surface);
                    }
                    // n.b., raw not supported for photo snapshots while video recording
                }
                else if( want_video_high_speed ) {
                    // future proofing - at the time of writing want_video_high_speed is only set when recording video,
                    // but if ever this is changed, can only support the preview_surface as a target
                    surfaces = Collections.singletonList(preview_surface);
                }
                else if( imageReaderRaw != null ) {
                    surfaces = Arrays.asList(preview_surface, imageReader.getSurface(), imageReaderRaw.getSurface());
                }
                else {
                    surfaces = Arrays.asList(preview_surface, imageReader.getSurface());
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "texture: " + texture);
                    Log.d(TAG, "preview_surface: " + preview_surface);
                }
            }
            if( MyDebug.LOG ) {
                if( video_recorder == null ) {
                    if( imageReaderRaw != null ) {
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw);
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getWidth());
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getHeight());
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getImageFormat());
                    }
                    else {
                        Log.d(TAG, "imageReader: " + imageReader);
                        Log.d(TAG, "imageReader width: " + imageReader.getWidth());
                        Log.d(TAG, "imageReader height: " + imageReader.getHeight());
                        Log.d(TAG, "imageReader format: " + imageReader.getImageFormat());
                    }
                }
            }
            if( video_recorder != null && want_video_high_speed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            //if( want_video_high_speed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                camera.createConstrainedHighSpeedCaptureSession(surfaces,
                    myStateCallback,
                    handler);
                is_video_high_speed = true;
            }
            else {
                try {
                    camera.createCaptureSession(surfaces,
                        myStateCallback,
                        handler);
                    is_video_high_speed = false;
                }
                catch(NullPointerException e) {
                    // have had this from some devices on Google Play, from deep within createCaptureSession
                    // note, we put the catch here rather than below, so as to not mask nullpointerexceptions
                    // from my code
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "NullPointerException trying to create capture session");
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    throw new CameraControllerException();
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "wait until session created...");
            // n.b., we use the background_camera_lock lock instead of a separate lock, so that it's safe to call this
            // method under the background_camera_lock (if we did so but used a separate lock, we'd hang here, because
            // MyStateCallback.onConfigured() needs to lock on background_camera_lock, before it completes and sets
            // myStateCallback.callback_done to true.
            synchronized( background_camera_lock ) {
                while( !myStateCallback.callback_done ) {
                    try {
                        // release the lock, and wait until myStateCallback calls notifyAll()
                        background_camera_lock.wait();
                    }
                    catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "created captureSession: " + captureSession);
            }
            synchronized( background_camera_lock ) {
                if( captureSession == null ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "failed to create capture session");
                    throw new CameraControllerException();
                }
            }
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "CameraAccessException trying to create capture session");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
        catch(IllegalArgumentException e) {
            // have had crashes from Google Play, from both createConstrainedHighSpeedCaptureSession and
            // createCaptureSession
            if( MyDebug.LOG ) {
                Log.e(TAG, "IllegalArgumentException trying to create capture session");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    @Override
    public void startPreview() throws CameraControllerException {
        if( MyDebug.LOG )
            Log.d(TAG, "startPreview");
        synchronized( background_camera_lock ) {
            if( captureSession != null ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to start preview");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    // do via CameraControllerException instead of preview_error_cb, so caller immediately knows preview has failed
                    throw new CameraControllerException();
                }
                return;
            }
        }
        createCaptureSession(null, false);
    }

    @Override
    public void stopPreview() {
        if( MyDebug.LOG )
            Log.d(TAG, "stopPreview: " + this);
        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            try {
                //pending_request_when_ready = null;

                try {
                    captureSession.stopRepeating();
                }
                catch(IllegalStateException e) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "captureSession already closed!");
                    e.printStackTrace();
                    // got this as a Google Play exception
                    // we still call close() below, as it has no effect if captureSession is already closed
                }
                // although stopRepeating() alone will pause the preview, seems better to close captureSession altogether - this allows the app to make changes such as changing the picture size
                if( MyDebug.LOG )
                    Log.d(TAG, "close capture session");
                captureSession.close();
                captureSession = null;
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to stop repeating");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
            // simulate CameraController1 behaviour where face detection is stopped when we stop preview
            if( camera_settings.has_face_detect_mode ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "cancel face detection");
                camera_settings.has_face_detect_mode = false;
                camera_settings.setFaceDetectMode(previewBuilder);
                // no need to call setRepeatingRequest(), we're just setting the camera_settings for when we restart the preview
            }
        }
    }

    @Override
    public boolean startFaceDetection() {
        if( MyDebug.LOG )
            Log.d(TAG, "startFaceDetection");
        if( previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) != null && previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF ) {
            if( MyDebug.LOG )
                Log.d(TAG, "face detection already enabled");
            return false;
        }
        if( supports_face_detect_mode_full ) {
            if( MyDebug.LOG )
                Log.d(TAG, "use full face detection");
            camera_settings.has_face_detect_mode = true;
            camera_settings.face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        }
        else if( supports_face_detect_mode_simple ) {
            if( MyDebug.LOG )
                Log.d(TAG, "use simple face detection");
            camera_settings.has_face_detect_mode = true;
            camera_settings.face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE;
        }
        else {
            Log.e(TAG, "startFaceDetection() called but face detection not available");
            return false;
        }
        camera_settings.setFaceDetectMode(previewBuilder);
        camera_settings.setSceneMode(previewBuilder); // also need to set the scene mode
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to start face detection");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    @Override
    public void setFaceDetectionListener(final FaceDetectionListener listener) {
        this.face_detection_listener = listener;
        this.last_faces_detected = -1;
    }

    /* If do_af_trigger_for_continuous is false, doing an autoFocus() in continuous focus mode just
       means we call the autofocus callback the moment focus is not scanning (as with old Camera API).
       If do_af_trigger_for_continuous is true, we set CONTROL_AF_TRIGGER_START, and wait for
       CONTROL_AF_STATE_FOCUSED_LOCKED or CONTROL_AF_STATE_NOT_FOCUSED_LOCKED, similar to other focus
       methods.
       do_af_trigger_for_continuous==true has advantages:
         - On Nexus 6 for flash auto, it means ae state is set to FLASH_REQUIRED if it is required
           when it comes to taking the photo. If do_af_trigger_for_continuous==false, sometimes
           it's set to CONTROL_AE_STATE_CONVERGED even for dark scenes, so we think we can skip
           the precapture, causing photos to come out dark (or we can force always doing precapture,
           but that makes things slower when flash isn't needed)
         - On OnePlus 3T, with do_af_trigger_for_continuous==false photos come out with blue tinge
           if the scene is not dark (but still dark enough that you'd want flash).
           do_af_trigger_for_continuous==true fixes this for cases where the flash fires for autofocus.
           Note that the problem is still not fixed for flash on where the scene is bright enough to
           not need flash (and so we don't fire flash for autofocus).
       do_af_trigger_for_continuous==true has disadvantage:
         - On both Nexus 6 and OnePlus 3T, taking photos with flash is longer, as we have flash firing
           for autofocus and precapture. Though note this is the case with autofocus mode anyway.
       Note for fake flash mode, we still can use do_af_trigger_for_continuous==false (and doing the
       af trigger for fake flash mode can sometimes mean flash fires for too long and we get a worse
       result).
     */
    //private final static boolean do_af_trigger_for_continuous = false;
    private final static boolean do_af_trigger_for_continuous = true;

    @Override
    public void autoFocus(final AutoFocusCallback cb, boolean capture_follows_autofocus_hint) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoFocus");
            Log.d(TAG, "capture_follows_autofocus_hint? " + capture_follows_autofocus_hint);
        }
        AutoFocusCallback push_autofocus_cb = null;
        synchronized( background_camera_lock ) {
            fake_precapture_torch_focus_performed = false;
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                // should call the callback, so the application isn't left waiting (e.g., when we autofocus before trying to take a photo)
                cb.onAutoFocus(false);
                return;
            }
            Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
            if( focus_mode == null ) {
                // we preserve the old Camera API where calling autoFocus() on a device without autofocus immediately calls the callback
                // (unclear if Open Camera needs this, but just to be safe and consistent between camera APIs)
                if( MyDebug.LOG )
                    Log.d(TAG, "no focus mode");
                cb.onAutoFocus(true);
                return;
            }
            else if( (!do_af_trigger_for_continuous || use_fake_precapture_mode) && focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
                // See note above for do_af_trigger_for_continuous
                if( MyDebug.LOG )
                    Log.d(TAG, "skip af trigger due to continuous mode");
                this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
                this.autofocus_cb = cb;
                return;
            }
            else if( is_video_high_speed ) {
                // CONTROL_AF_TRIGGER_IDLE/CONTROL_AF_TRIGGER_START not supported for high speed video
                cb.onAutoFocus(true);
                return;
            }
            /*if( state == STATE_WAITING_AUTOFOCUS ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "already waiting for an autofocus");
                // need to update the callback!
                this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
                this.autofocus_cb = cb;
                return;
            }*/
            CaptureRequest.Builder afBuilder = previewBuilder;
            if( MyDebug.LOG ) {
                {
                    MeteringRectangle [] areas = afBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
                    for(int i=0;areas != null && i<areas.length;i++) {
                        Log.d(TAG, i + " focus area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
                    }
                }
                {
                    MeteringRectangle [] areas = afBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
                    for(int i=0;areas != null && i<areas.length;i++) {
                        Log.d(TAG, i + " metering area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
                    }
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "state is now STATE_WAITING_AUTOFOCUS");
            state = STATE_WAITING_AUTOFOCUS;
            precapture_state_change_time_ms = -1;
            this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
            this.autofocus_cb = cb;
            try {
                if( use_fake_precapture_mode ) {
                    boolean want_flash = false;
                    if( camera_settings.flash_value.equals("flash_auto") || camera_settings.flash_value.equals("flash_frontscreen_auto") ) {
                        // calling fireAutoFlash() also caches the decision on whether to flash - otherwise if the flash fires now, we'll then think the scene is bright enough to not need the flash!
                        if( fireAutoFlash() )
                            want_flash = true;
                    }
                    else if( camera_settings.flash_value.equals("flash_on") ) {
                        want_flash = true;
                    }
                    if( want_flash ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "turn on torch for fake flash");
                        if( !camera_settings.has_iso ) {
                            // in auto-mode, need to ensure CONTROL_AE_MODE isn't est to flash auto/on for torch to work
                            // in manual-mode, fine as CONTROL_AE_MODE will be off
                            afBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        }
                        afBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        test_fake_flash_focus++;
                        fake_precapture_torch_focus_performed = true;
                        setRepeatingRequest(afBuilder.build());
                        // We sleep for a short time as on some devices (e.g., OnePlus 3T), the torch will turn off when autofocus
                        // completes even if we don't want that (because we'll be taking a photo).
                        // Note that on other devices such as Nexus 6, this problem doesn't occur even if we don't have a separate
                        // setRepeatingRequest.
                        // Update for 1.37: now we do need this for Nexus 6 too, after switching to setting CONTROL_AE_MODE_ON_AUTO_FLASH
                        // or CONTROL_AE_MODE_ON_ALWAYS_FLASH even for fake flash (see note in CameraSettings.setAEMode()) - and we
                        // needed to increase to 200ms! Otherwise photos come out too dark for flash on if doing touch to focus then
                        // quickly taking a photo. (It also work to previously switch to CONTROL_AE_MODE_ON/FLASH_MODE_OFF first,
                        // but then the same problem shows up on OnePlus 3T again!)
                        try {
                            Thread.sleep(200);
                        }
                        catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // Camera2Basic sets a trigger with capture
                // Google Camera sets to idle with a repeating request, then sets af trigger to start with a capture
                afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                setRepeatingRequest(afBuilder.build());
                afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                capture(afBuilder.build());
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to autofocus");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                state = STATE_NORMAL;
                precapture_state_change_time_ms = -1;
                push_autofocus_cb = autofocus_cb;
                autofocus_cb = null;
                this.capture_follows_autofocus_hint = false;
            }
            afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
        }

        if( push_autofocus_cb != null ) {
            // should call callbacks without a lock
            push_autofocus_cb.onAutoFocus(false);
        }
    }

    @Override
    public void setCaptureFollowAutofocusHint(boolean capture_follows_autofocus_hint) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setCaptureFollowAutofocusHint");
            Log.d(TAG, "capture_follows_autofocus_hint? " + capture_follows_autofocus_hint);
        }
        synchronized( background_camera_lock ) {
            this.capture_follows_autofocus_hint = capture_follows_autofocus_hint;
        }
    }

    @Override
    public void cancelAutoFocus() {
        if( MyDebug.LOG )
            Log.d(TAG, "cancelAutoFocus");
        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }

            if( is_video_high_speed ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "video is high speed");
                return;
            }

            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            // Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
            try {
                capture();
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to cancel autofocus [capture]");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            this.autofocus_cb = null;
            this.capture_follows_autofocus_hint = false;
            state = STATE_NORMAL;
            precapture_state_change_time_ms = -1;
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to set repeating request after cancelling autofocus");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback cb) {
        if( MyDebug.LOG )
            Log.d(TAG, "setContinuousFocusMoveCallback");
        this.continuous_focus_move_callback = cb;
    }

    static public double getScaleForExposureTime(long exposure_time, long fixed_exposure_time, long scaled_exposure_time, double full_exposure_time_scale) {
        if( MyDebug.LOG )
            Log.d(TAG, "getScaleForExposureTime");
        double alpha = (exposure_time - fixed_exposure_time) / (double) (scaled_exposure_time - fixed_exposure_time);
        if( alpha < 0.0 )
            alpha = 0.0;
        else if( alpha > 1.0 )
            alpha = 1.0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "exposure_time: " + exposure_time);
            Log.d(TAG, "alpha: " + alpha);
        }
        // alpha==0 means exposure_time_scale==1; alpha==1 means exposure_time_scale==full_exposure_time_scale
        return (1.0 - alpha) + alpha * full_exposure_time_scale;
    }

    /** Sets up a builder to have manual exposure time, if supported. The exposure time will be
     *  clamped to the allowed values, and manual ISO will also be set based on the current ISO value.
     */
    private void setManualExposureTime(CaptureRequest.Builder stillBuilder, long exposure_time) {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualExposureTime: " + exposure_time);
        Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE); // may be null on some devices
        Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE); // may be null on some devices
        if( exposure_time_range != null && iso_range != null ) {
            long min_exposure_time = exposure_time_range.getLower();
            long max_exposure_time = exposure_time_range.getUpper();
            if( exposure_time < min_exposure_time )
                exposure_time = min_exposure_time;
            if( exposure_time > max_exposure_time )
                exposure_time = max_exposure_time;
            if (MyDebug.LOG) {
                Log.d(TAG, "exposure_time: " + exposure_time);
            }
            stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            {
                // set ISO
                int iso = 800;
                if( capture_result_has_iso )
                    iso = capture_result_iso;
                // see https://sourceforge.net/p/opencamera/tickets/321/ - some devices may have auto ISO that's
                // outside of the allowed manual iso range!
                iso = Math.max(iso, iso_range.getLower());
                iso = Math.min(iso, iso_range.getUpper());
                stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso );
            }
            if( capture_result_has_frame_duration  )
                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, capture_result_frame_duration);
            else
                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L/30);
            stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
        }
    }

    private void takePictureAfterPrecapture() {
        if( MyDebug.LOG )
            Log.d(TAG, "takePictureAfterPrecapture");
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }

        if( !previewIsVideoMode ) {
            // special burst modes not supported for photo snapshots when recording video
            if( burst_type == BurstType.BURSTTYPE_EXPO || burst_type == BurstType.BURSTTYPE_FOCUS ) {
                takePictureBurstBracketing();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "takePictureAfterPrecapture() took: " + (System.currentTimeMillis() - debug_time));
                }
                return;
            }
            else if( burst_type == BurstType.BURSTTYPE_NORMAL || burst_type == BurstType.BURSTTYPE_CONTINUOUS ) {
                takePictureBurst(false);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "takePictureAfterPrecapture() took: " + (System.currentTimeMillis() - debug_time));
                }
                return;
            }
        }

        CaptureRequest.Builder stillBuilder = null;
        boolean ok = true;
        ErrorCallback push_take_picture_error_cb = null;

        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            try {
                if( MyDebug.LOG ) {
                    if( imageReaderRaw != null ) {
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.toString());
                        Log.d(TAG, "imageReaderRaw surface: " + imageReaderRaw.getSurface().toString());
                    }
                    else {
                        Log.d(TAG, "imageReader: " + imageReader.toString());
                        Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
                    }
                }
                stillBuilder = camera.createCaptureRequest(previewIsVideoMode ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT : CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE));
                camera_settings.setupBuilder(stillBuilder, true);
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting torch for capture");
                    if( !camera_settings.has_iso )
                        stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    test_fake_flash_photo++;
                }
                if( !camera_settings.has_iso && this.optimise_ae_for_dro && capture_result_has_exposure_time && (camera_settings.flash_value.equals("flash_off") || camera_settings.flash_value.equals("flash_auto") || camera_settings.flash_value.equals("flash_frontscreen_auto") ) ) {
                    final double full_exposure_time_scale = Math.pow(2.0, -0.5);
                    final long fixed_exposure_time = 1000000000L/60; // we only scale the exposure time at all if it's less than this value
                    final long scaled_exposure_time = 1000000000L/120; // we only scale the exposure time by the full_exposure_time_scale if the exposure time is less than this value
                    long exposure_time = capture_result_exposure_time;
                    if( exposure_time <= fixed_exposure_time ) {
                        double exposure_time_scale = getScaleForExposureTime(exposure_time, fixed_exposure_time, scaled_exposure_time, full_exposure_time_scale);
                        exposure_time *= exposure_time_scale;
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "reduce exposure shutter speed further, was: " + exposure_time);
                            Log.d(TAG, "exposure_time_scale: " + exposure_time_scale);
                        }
                        modified_from_camera_settings = true;
                        setManualExposureTime(stillBuilder, exposure_time);
                    }
                }
                //stillBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                //stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ) {
                    // unclear why we wouldn't want to request ZSL
                    // this is also required to enable HDR+ on Google Pixel devices when using Camera2: https://opensource.google.com/projects/pixelvisualcorecamera
                    stillBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
                    if( MyDebug.LOG ) {
                        Boolean zsl = stillBuilder.get(CaptureRequest.CONTROL_ENABLE_ZSL);
                        Log.d(TAG, "CONTROL_ENABLE_ZSL: " + (zsl==null ? "null" : zsl));
                    }
                }
                clearPending();
                // shouldn't add preview surface as a target - no known benefit to doing so
                stillBuilder.addTarget(imageReader.getSurface());
                if( imageReaderRaw != null )
                    stillBuilder.addTarget(imageReaderRaw.getSurface());

                n_burst = 1;
                n_burst_taken = 0;
                n_burst_total = n_burst;
                n_burst_raw = raw_todo ? n_burst : 0;
                burst_single_request = false;
                if( !previewIsVideoMode ) {
                    // need to stop preview before capture (as done in Camera2Basic; otherwise we get bugs such as flash remaining on after taking a photo with flash)
                    // but don't do this in video mode - if we're taking photo snapshots while video recording, we don't want to pause video!
                    // update: bug with flash may have been device specific (things are fine with Nokia 8)
                    captureSession.stopRepeating();
                }
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to take picture");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                ok = false;
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                push_take_picture_error_cb = take_picture_error_cb;
                take_picture_error_cb = null;
            }
            catch(IllegalStateException e) {
                if( MyDebug.LOG )
                    Log.d(TAG, "captureSession already closed!");
                e.printStackTrace();
                ok = false;
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                // don't report error, as camera is closed or closing
            }
        }

        // need to call callbacks without a lock
        if( ok && picture_cb != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "call onStarted() in callback");
            picture_cb.onStarted();
        }

        if( ok ) {
            synchronized( background_camera_lock ) {
                if( camera == null || captureSession == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "no camera or capture session");
                    return;
                }
                if( test_release_during_photo ) {
                    final Activity activity = (Activity)context;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if( MyDebug.LOG )
                                Log.d(TAG, "test UI thread call release()");
                            release();
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    }
                    catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if( MyDebug.LOG )
                        Log.d(TAG, "capture with stillBuilder");
                    //pending_request_when_ready = stillBuilder.build();
                    captureSession.capture(stillBuilder.build(), previewCaptureCallback, handler);
                    //captureSession.capture(stillBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                    //}, handler);
                    playSound(MediaActionSound.SHUTTER_CLICK); // play shutter sound asap, otherwise user has the illusion of being slow to take photos
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to take picture");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    //noinspection UnusedAssignment
                    ok = false;
                    jpeg_todo = false;
                    raw_todo = false;
                    picture_cb = null;
                    push_take_picture_error_cb = take_picture_error_cb;
                }
                catch(IllegalStateException e) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "captureSession already closed!");
                    e.printStackTrace();
                    //noinspection UnusedAssignment
                    ok = false;
                    jpeg_todo = false;
                    raw_todo = false;
                    picture_cb = null;
                    // don't report error, as camera is closed or closing
                }
            }
        }

        // need to call callbacks without a lock
        if( push_take_picture_error_cb != null ) {
            push_take_picture_error_cb.onError();
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "takePictureAfterPrecapture() took: " + (System.currentTimeMillis() - debug_time));
        }
    }

    public static List<Float> setupFocusBracketingDistances(float source, float target, int count) {
        List<Float> focus_distances = new ArrayList<>();
        float focus_distance_s = source;
        float focus_distance_e = target;
        final float max_focus_bracket_distance_c = 0.1f; // 10m
        focus_distance_s = Math.max(focus_distance_s, max_focus_bracket_distance_c); // since we'll dealing with 1/distance, use Math.max
        focus_distance_e = Math.max(focus_distance_e, max_focus_bracket_distance_c); // since we'll dealing with 1/distance, use Math.max
        if( MyDebug.LOG ) {
            Log.d(TAG, "focus_distance_s: " + focus_distance_s);
            Log.d(TAG, "focus_distance_e: " + focus_distance_e);
        }
        // we want to interpolate linearly in distance, not 1/distance
        float real_focus_distance_s = 1.0f/focus_distance_s;
        float real_focus_distance_e = 1.0f/focus_distance_e;
        if( MyDebug.LOG ) {
            Log.d(TAG, "real_focus_distance_s: " + real_focus_distance_s);
            Log.d(TAG, "real_focus_distance_e: " + real_focus_distance_e);
        }
        for(int i=0;i<count;i++) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "i: " + i);
            }
            // for first and last, we still use the real focus distances; for intermediate values, we interpolate
            // with first/last clamped to max of 10m (to avoid taking reciprocal of 0)
            float distance;
            if( i == 0 ) {
                distance = source;
            }
            else if( i == count-1 ) {
                distance = target;
            }
            else {
                //float alpha = ((float)i)/(count-1.0f);
                // rather than linear interpolation, we use log, see https://stackoverflow.com/questions/5215459/android-mediaplayer-setvolume-function
                // this gives more shots are closer focus distances
                int value = i;
                if( real_focus_distance_s > real_focus_distance_e ) {
                    // if source is further than target, we still want the interpolation distances to be the same, but in reversed order
                    value = count-1-i;
                }
                float alpha = (float)(1.0-Math.log(count-value)/Math.log(count));
                if( real_focus_distance_s > real_focus_distance_e ) {
                    alpha = 1.0f-alpha;
                }
                float real_distance = (1.0f-alpha)*real_focus_distance_s + alpha*real_focus_distance_e;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "    alpha: " + alpha);
                    Log.d(TAG, "    real_distance: " + real_distance);
                }
                distance = 1.0f/real_distance;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "    distance: " + distance);
            }
            focus_distances.add(distance);
        }
        return focus_distances;
    }

    private void takePictureBurstBracketing() {
        if( MyDebug.LOG )
            Log.d(TAG, "takePictureBurstBracketing");
        if( burst_type != BurstType.BURSTTYPE_EXPO && burst_type != BurstType.BURSTTYPE_FOCUS ) {
            Log.e(TAG, "takePictureBurstBracketing called but unexpected burst_type: " + burst_type);
        }

        List<CaptureRequest> requests = new ArrayList<>();
        boolean ok = true;
        ErrorCallback push_take_picture_error_cb = null;

        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            try {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "imageReader: " + imageReader.toString());
                    Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
                }

                CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(previewIsVideoMode ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT : CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                // n.b., don't set RequestTagType.CAPTURE here - we only do it for the last of the burst captures (see below)
                camera_settings.setupBuilder(stillBuilder, true);
                clearPending();
                // shouldn't add preview surface as a target - see note in takePictureAfterPrecapture()
                // but also, adding the preview surface causes the dark/light exposures to be visible, which we don't want
                stillBuilder.addTarget(imageReader.getSurface());
                if( raw_todo )
                    stillBuilder.addTarget(imageReaderRaw.getSurface());

                if( burst_type == BurstType.BURSTTYPE_EXPO ) {

                if( MyDebug.LOG )
                    Log.d(TAG, "expo bracketing");

                /*stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

                stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -6);
                requests.add( stillBuilder.build() );
                stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                requests.add( stillBuilder.build() );
                stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6);
                requests.add( stillBuilder.build() );*/

                stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting torch for capture");
                    stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    test_fake_flash_photo++;
                }
                // else don't turn torch off, as user may be in torch on mode

                Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE); // may be null on some devices
                if( iso_range == null ) {
                    Log.e(TAG, "takePictureBurstBracketing called but null iso_range");
                }
                else {
                    // set ISO
                    int iso = 800;
                    // obtain current ISO/etc settings from the capture result - but if we're in manual ISO mode,
                    // might as well use the settings the user has actually requested (also useful for workaround for
                    // OnePlus 3T bug where the reported ISO and exposure_time are wrong in dark scenes)
                    if( camera_settings.has_iso )
                        iso = camera_settings.iso;
                    else if( capture_result_has_iso )
                        iso = capture_result_iso;
                    // see https://sourceforge.net/p/opencamera/tickets/321/ - some devices may have auto ISO that's
                    // outside of the allowed manual iso range!
                    iso = Math.max(iso, iso_range.getLower());
                    iso = Math.min(iso, iso_range.getUpper());
                    stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso );
                }
                if( capture_result_has_frame_duration  )
                    stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, capture_result_frame_duration);
                else
                    stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L/30);

                long base_exposure_time = 1000000000L/30;
                if( camera_settings.has_iso )
                    base_exposure_time = camera_settings.exposure_time;
                else if( capture_result_has_exposure_time )
                    base_exposure_time = capture_result_exposure_time;

                int n_half_images = expo_bracketing_n_images/2;
                long min_exposure_time = base_exposure_time;
                long max_exposure_time = base_exposure_time;
                final double scale = Math.pow(2.0, expo_bracketing_stops/(double)n_half_images);
                Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE); // may be null on some devices
                if( exposure_time_range != null ) {
                    min_exposure_time = exposure_time_range.getLower();
                    max_exposure_time = exposure_time_range.getUpper();
                }

                if( MyDebug.LOG ) {
                    Log.d(TAG, "taking expo bracketing with n_images: " + expo_bracketing_n_images);
                    Log.d(TAG, "ISO: " + stillBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
                    Log.d(TAG, "Frame duration: " + stillBuilder.get(CaptureRequest.SENSOR_FRAME_DURATION));
                    Log.d(TAG, "Base exposure time: " + base_exposure_time);
                    Log.d(TAG, "Min exposure time: " + min_exposure_time);
                    Log.d(TAG, "Max exposure time: " + max_exposure_time);
                }

                // darker images
                for(int i=0;i<n_half_images;i++) {
                    long exposure_time = base_exposure_time;
                    if( exposure_time_range != null ) {
                        double this_scale = scale;
                        for(int j=i;j<n_half_images-1;j++)
                            this_scale *= scale;
                        exposure_time /= this_scale;
                        if( exposure_time < min_exposure_time )
                            exposure_time = min_exposure_time;
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "add burst request for " + i + "th dark image:");
                            Log.d(TAG, "    this_scale: " + this_scale);
                            Log.d(TAG, "    exposure_time: " + exposure_time);
                        }
                        stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
                        stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                        requests.add( stillBuilder.build() );
                    }
                }

                // base image
                if( MyDebug.LOG )
                    Log.d(TAG, "add burst request for base image");
                stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, base_exposure_time);
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                requests.add( stillBuilder.build() );

                // lighter images
                for(int i=0;i<n_half_images;i++) {
                    long exposure_time = base_exposure_time;
                    if( exposure_time_range != null ) {
                        double this_scale = scale;
                        for(int j=0;j<i;j++)
                            this_scale *= scale;
                        exposure_time *= this_scale;
                        if( exposure_time > max_exposure_time )
                            exposure_time = max_exposure_time;
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "add burst request for " + i + "th light image:");
                            Log.d(TAG, "    this_scale: " + this_scale);
                            Log.d(TAG, "    exposure_time: " + exposure_time);
                        }
                        stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
                        if( i == n_half_images - 1 ) {
                            // RequestTagType.CAPTURE should only be set for the last request, otherwise we'll may do things like turning
                            // off torch (for fake flash) before all images are received
                            // More generally, doesn't seem a good idea to be doing the post-capture commands (resetting ae state etc)
                            // multiple times, and before all captures are complete!
                            if( MyDebug.LOG )
                                Log.d(TAG, "set RequestTagType.CAPTURE for last burst request");
                            stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE));
                        }
                        else {
                            stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                        }
                        requests.add( stillBuilder.build() );
                    }
                }

                burst_single_request = true;
                }
                else {
                    // BURSTTYPE_FOCUS
                    if( MyDebug.LOG )
                        Log.d(TAG, "focus bracketing");

                    if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "setting torch for capture");
                        if( !camera_settings.has_iso )
                            stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        test_fake_flash_photo++;
                    }

                    stillBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF); // just in case

                    if( Math.abs(camera_settings.focus_distance - focus_bracketing_source_distance) < 1.0e-5 ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "current focus matches source");
                    }
                    else if( Math.abs(camera_settings.focus_distance - focus_bracketing_target_distance) < 1.0e-5 ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "current focus matches target");
                    }
                    else {
                        Log.d(TAG, "current focus matches neither source nor target");
                    }

                    List<Float> focus_distances = setupFocusBracketingDistances(focus_bracketing_source_distance, focus_bracketing_target_distance, focus_bracketing_n_images);
                    if( focus_bracketing_add_infinity ) {
                        focus_distances.add(0.0f);
                    }
                    for(int i=0;i<focus_distances.size();i++) {
                        stillBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distances.get(i));
                        if( i == focus_distances.size()-1 ) {
                            stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE)); // set capture tag for last only
                        }
                        else {
                            // note, even if we didn't need to set CAPTURE_BURST_IN_PROGRESS, we'd still want
                            // to set a RequestTagObject (e.g., type NONE) so that it can be changed later,
                            // so that cancelling focus bracketing works
                            //stillBuilder.setTag(new RequestTagObject(RequestTagType.NONE));
                            stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                        }
                        requests.add( stillBuilder.build() );

                        focus_bracketing_in_progress = true;
                    }

                    burst_single_request = false; // we set to false for focus bracketing, as we support bracketing with large numbers of images in this mode
                    //burst_single_request = true; // test
                }

                /*
                // testing:
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                requests.add( stillBuilder.build() );
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                requests.add( stillBuilder.build() );
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                requests.add( stillBuilder.build() );
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                requests.add( stillBuilder.build() );
                if( MyDebug.LOG )
                    Log.d(TAG, "set RequestTagType.CAPTURE for last burst request");
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE));
                requests.add( stillBuilder.build() );
                */

                n_burst = requests.size();
                n_burst_total = n_burst;
                n_burst_taken = 0;
                n_burst_raw = raw_todo ? n_burst : 0;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "n_burst: " + n_burst);
                    Log.d(TAG, "burst_single_request: " + burst_single_request);
                }

                if( !previewIsVideoMode ) {
                    captureSession.stopRepeating(); // see note under takePictureAfterPrecapture()
                }
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to take picture expo burst");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                ok = false;
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                push_take_picture_error_cb = take_picture_error_cb;
            }
            catch(IllegalStateException e) {
                if( MyDebug.LOG )
                    Log.d(TAG, "captureSession already closed!");
                e.printStackTrace();
                ok = false;
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                // don't report error, as camera is closed or closing
            }
        }

        // need to call callbacks without a lock
        if( ok && picture_cb != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "call onStarted() in callback");
            picture_cb.onStarted();
        }

        if( ok ) {
            synchronized( background_camera_lock ) {
                if( camera == null || captureSession == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "no camera or capture session");
                    return;
                }
                try {
                    modified_from_camera_settings = true;
                    if( use_expo_fast_burst && burst_type == BurstType.BURSTTYPE_EXPO ) { // alway use slow burst for focus bracketing
                        if( MyDebug.LOG )
                            Log.d(TAG, "using fast burst");
                        int sequenceId = captureSession.captureBurst(requests, previewCaptureCallback, handler);
                        if( MyDebug.LOG )
                            Log.d(TAG, "sequenceId: " + sequenceId);
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "using slow burst");
                        slow_burst_capture_requests = requests;
                        slow_burst_start_ms = System.currentTimeMillis();
                        captureSession.capture(requests.get(0), previewCaptureCallback, handler);
                    }

                    playSound(MediaActionSound.SHUTTER_CLICK); // play shutter sound asap, otherwise user has the illusion of being slow to take photos
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to take picture expo burst");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    //noinspection UnusedAssignment
                    ok = false;
                    jpeg_todo = false;
                    raw_todo = false;
                    picture_cb = null;
                    push_take_picture_error_cb = take_picture_error_cb;
                }
                catch(IllegalStateException e) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "captureSession already closed!");
                    e.printStackTrace();
                    //noinspection UnusedAssignment
                    ok = false;
                    jpeg_todo = false;
                    raw_todo = false;
                    picture_cb = null;
                    // don't report error, as camera is closed or closing
                }
            }
        }

        // need to call callbacks without a lock
        if( push_take_picture_error_cb != null ) {
            push_take_picture_error_cb.onError();
        }
    }

    private void takePictureBurst(boolean continuing_fast_burst) {
        if( MyDebug.LOG )
            Log.d(TAG, "takePictureBurst");
        if( burst_type != BurstType.BURSTTYPE_NORMAL && burst_type != BurstType.BURSTTYPE_CONTINUOUS ) {
            Log.e(TAG, "takePictureBurstBracketing called but unexpected burst_type: " + burst_type);
        }

        boolean is_new_burst = true;
        CaptureRequest request = null;
        CaptureRequest last_request = null;
        boolean ok = true;
        ErrorCallback push_take_picture_error_cb = null;

        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                return;
            }
            try {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "imageReader: " + imageReader.toString());
                    Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
                }

                CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(previewIsVideoMode ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT : CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                // n.b., don't set RequestTagType.CAPTURE here - we only do it for the last of the burst captures (see below)
                camera_settings.setupBuilder(stillBuilder, true);
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "setting torch for capture");
                    if( !camera_settings.has_iso )
                        stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    test_fake_flash_photo++;
                }

                if( burst_type == BurstType.BURSTTYPE_NORMAL && burst_for_noise_reduction ) {
                    // must be done after calling setupBuilder(), so we override the default EDGE_MODE and NOISE_REDUCTION_MODE
                    if( MyDebug.LOG )
                        Log.d(TAG, "optimise settings for burst_for_noise_reduction");
                    stillBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
                    stillBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
                    stillBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
                }

                if( !continuing_fast_burst ) {
                    clearPending();
                }
                // shouldn't add preview surface as a target - see note in takePictureAfterPrecapture()
                stillBuilder.addTarget(imageReader.getSurface());
                // RAW target added below

                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    test_fake_flash_photo++;
                }
                // else don't turn torch off, as user may be in torch on mode

                if( burst_type == BurstType.BURSTTYPE_CONTINUOUS ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "continuous burst mode");
                    raw_todo = false; // RAW works in continuous burst mode, but makes things very slow...
                    if( continuing_fast_burst ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "continuing fast burst");
                        n_burst++;
                        is_new_burst = false;
                        /*if( !continuous_burst_in_progress ) // test bug where we call callback onCompleted() before all burst images are received
                            n_burst = 1;*/
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "start continuous burst");
                        continuous_burst_in_progress = true;
                        n_burst = 1;
                        n_burst_taken = 0;
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "n_burst is now " + n_burst);
                }
                else if( burst_for_noise_reduction ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "choose n_burst for burst_for_noise_reduction");
                    n_burst = 4;
                    n_burst_taken = 0;

                    if( capture_result_has_iso ) {
                        // For Nexus 6, max reported ISO is 1196, so the limit for dark scenes shouldn't be more than this
                        // Nokia 8's max reported ISO is 1551
                        // Note that OnePlus 3T has max reported ISO of 800, but this is a device bug
                        if( capture_result_iso >= ISO_FOR_DARK ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "optimise for dark scene");
                            n_burst = noise_reduction_low_light ? N_IMAGES_NR_DARK_LOW_LIGHT : N_IMAGES_NR_DARK;
                            boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
                            // OnePlus 3T at least has bug where manual ISO can't be set to above 800, so dark images end up too dark -
                            // so no point enabling this code, which is meant to brighten the scene, not make it darker!
                            if( !camera_settings.has_iso && !is_oneplus ) {
                                long exposure_time = noise_reduction_low_light ? 1000000000L/3 : 1000000000L/10;
                                if( !capture_result_has_exposure_time || capture_result_exposure_time < exposure_time ) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "also set long exposure time");
                                    modified_from_camera_settings = true;
                                    setManualExposureTime(stillBuilder, exposure_time);
                                }
                                else {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "no need to extend exposure time for dark scene, already long enough: " + exposure_time);
                                }
                            }
                        }
                        else if( capture_result_has_exposure_time ) {
                            //final double full_exposure_time_scale = 0.5;
                            final double full_exposure_time_scale = Math.pow(2.0, -0.5);
                            final long fixed_exposure_time = 1000000000L/60; // we only scale the exposure time at all if it's less than this value
                            final long scaled_exposure_time = 1000000000L/120; // we only scale the exposure time by the full_exposure_time_scale if the exposure time is less than this value
                            long exposure_time = capture_result_exposure_time;
                            if( exposure_time <= fixed_exposure_time ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "optimise for bright scene");
                                //n_burst = 2;
                                n_burst = 3;
                                if( !camera_settings.has_iso ) {
                                    double exposure_time_scale = getScaleForExposureTime(exposure_time, fixed_exposure_time, scaled_exposure_time, full_exposure_time_scale);
                                    exposure_time *= exposure_time_scale;
                                    if( MyDebug.LOG ) {
                                        Log.d(TAG, "reduce exposure shutter speed further, was: " + exposure_time);
                                        Log.d(TAG, "exposure_time_scale: " + exposure_time_scale);
                                    }
                                    modified_from_camera_settings = true;
                                    setManualExposureTime(stillBuilder, exposure_time);
                                }
                            }
                        }
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "user requested n_burst");
                    n_burst = burst_requested_n_images;
                    n_burst_taken = 0;
                }
                if( raw_todo )
                    stillBuilder.addTarget(imageReaderRaw.getSurface());
                n_burst_total = n_burst;
                n_burst_raw = raw_todo ? n_burst : 0;
                burst_single_request = false;

                if( MyDebug.LOG )
                    Log.d(TAG, "n_burst: " + n_burst);

                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROGRESS));
                request = stillBuilder.build();
                stillBuilder.setTag(new RequestTagObject(RequestTagType.CAPTURE));
                last_request = stillBuilder.build();

                // n.b., don't stop the preview with stop.Repeating when capturing a burst
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to take picture burst");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                ok = false;
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                push_take_picture_error_cb = take_picture_error_cb;
            }
        }

        // need to call callbacks without a lock
        if( ok && picture_cb != null && is_new_burst ) {
            if( MyDebug.LOG )
                Log.d(TAG, "call onStarted() in callback");
            picture_cb.onStarted();
        }

        if( ok ) {
            synchronized( background_camera_lock ) {
                if( camera == null || captureSession == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "no camera or capture session");
                    return;
                }
                try {
                    final boolean use_burst = true;
                    //final boolean use_burst = false;

                    if( burst_type == BurstType.BURSTTYPE_CONTINUOUS ) {
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "continuous capture");
                            if( !continuous_burst_in_progress )
                                Log.d(TAG, "    last continuous capture");
                        }
                        continuous_burst_requested_last_capture = !continuous_burst_in_progress;
                        captureSession.capture(continuous_burst_in_progress ? request : last_request, previewCaptureCallback, handler);

                        if( continuous_burst_in_progress ) {
                            final int continuous_burst_rate_ms = 100;
                            // also take the next burst after a delay
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // note, even if continuous_burst_in_progress has become false by this point, still take one last
                                    // photo, as need to ensure that we have a request with RequestTagType.CAPTURE, as well as ensuring
                                    // we call the onCompleted() method of the callback
                                    if( MyDebug.LOG ) {
                                        Log.d(TAG, "take next continuous burst");
                                        Log.d(TAG, "continuous_burst_in_progress: " + continuous_burst_in_progress);
                                        Log.d(TAG, "n_burst: " + n_burst);
                                    }
                                    if( n_burst >= 10 || n_burst_raw >= 10 ) {
                                        // Nokia 8 in std mode without post-processing options doesn't hit this limit (we only hit this
                                        // if it's set to "n_burst >= 5")
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "...but wait for continuous burst, as waiting for too many photos");
                                        }
                                        //throw new RuntimeException(); // test
                                        handler.postDelayed(this, continuous_burst_rate_ms);
                                    }
                                    else if( picture_cb.imageQueueWouldBlock(n_burst_raw, n_burst+1) ) {
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "...but wait for continuous burst, as image queue would block");
                                        }
                                        //throw new RuntimeException(); // test
                                        handler.postDelayed(this, continuous_burst_rate_ms);
                                    }
                                    else {
                                        takePictureBurst(true);
                                    }
                                }
                            }, continuous_burst_rate_ms);
                        }
                    }
                    else if( use_burst ) {
                        List<CaptureRequest> requests = new ArrayList<>();
                        for(int i=0;i<n_burst-1;i++)
                            requests.add(request);
                        requests.add(last_request);
                        if( MyDebug.LOG )
                            Log.d(TAG, "captureBurst");
                        int sequenceId = captureSession.captureBurst(requests, previewCaptureCallback, handler);
                        if( MyDebug.LOG )
                            Log.d(TAG, "sequenceId: " + sequenceId);
                    }
                    else {
                        final int burst_delay = 100;
                        final CaptureRequest request_f = request;
                        final CaptureRequest last_request_f = last_request;

                        new Runnable() {
                            int n_remaining = n_burst;

                            @Override
                            public void run() {
                                if( MyDebug.LOG ) {
                                    Log.d(TAG, "takePictureBurst runnable");
                                    if( n_remaining == 1 ) {
                                        Log.d(TAG, "    is last request");
                                    }
                                }
                                ErrorCallback push_take_picture_error_cb = null;

                                synchronized( background_camera_lock ) {
                                    if( camera == null || captureSession == null ) {
                                        if( MyDebug.LOG )
                                            Log.d(TAG, "no camera or capture session");
                                        return;
                                    }
                                    try {
                                        captureSession.capture(n_remaining == 1 ? last_request_f : request_f, previewCaptureCallback, handler);
                                        n_remaining--;
                                        if( MyDebug.LOG )
                                            Log.d(TAG, "takePictureBurst n_remaining: " + n_remaining);
                                        if( n_remaining > 0 ) {
                                            handler.postDelayed(this, burst_delay);
                                        }
                                    }
                                    catch(CameraAccessException e) {
                                        if( MyDebug.LOG ) {
                                            Log.e(TAG, "failed to take picture burst");
                                            Log.e(TAG, "reason: " + e.getReason());
                                            Log.e(TAG, "message: " + e.getMessage());
                                        }
                                        e.printStackTrace();
                                        jpeg_todo = false;
                                        raw_todo = false;
                                        picture_cb = null;
                                        push_take_picture_error_cb = take_picture_error_cb;
                                    }

                                    // need to call callbacks without a lock
                                    if( push_take_picture_error_cb != null ) {
                                        push_take_picture_error_cb.onError();
                                    }
                                }
                            }
                        }.run();
                    }

                    if( !continuing_fast_burst ) {
                        playSound(MediaActionSound.SHUTTER_CLICK); // play shutter sound asap, otherwise user has the illusion of being slow to take photos
                    }
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to take picture burst");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    //noinspection UnusedAssignment
                    ok = false;
                    jpeg_todo = false;
                    raw_todo = false;
                    picture_cb = null;
                    push_take_picture_error_cb = take_picture_error_cb;
                }
            }
        }

        // need to call callbacks without a lock
        if( push_take_picture_error_cb != null ) {
            push_take_picture_error_cb.onError();
        }
    }

    private void runPrecapture() {
        if( MyDebug.LOG )
            Log.d(TAG, "runPrecapture");
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }
        // first run precapture sequence

        ErrorCallback push_take_picture_error_cb = null;

        synchronized( background_camera_lock ) {
            if( MyDebug.LOG ) {
                if( use_fake_precapture_mode )
                    Log.e(TAG, "shouldn't be doing standard precapture when use_fake_precapture_mode is true!");
                else if( burst_type != BurstType.BURSTTYPE_NONE )
                    Log.e(TAG, "shouldn't be doing precapture for burst - should be using fake precapture!");
            }
            try {
                // use a separate builder for precapture - otherwise have problem that if we take photo with flash auto/on of dark scene, then point to a bright scene, the autoexposure isn't running until we autofocus again
                final CaptureRequest.Builder precaptureBuilder = camera.createCaptureRequest(previewIsVideoMode ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT : CameraDevice.TEMPLATE_STILL_CAPTURE);
                precaptureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

                camera_settings.setupBuilder(precaptureBuilder, false);
                precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                precaptureBuilder.addTarget(getPreviewSurface());

                state = STATE_WAITING_PRECAPTURE_START;
                precapture_state_change_time_ms = System.currentTimeMillis();

                // first set precapture to idle - this is needed, otherwise we hang in state STATE_WAITING_PRECAPTURE_START, because precapture already occurred whilst autofocusing, and it doesn't occur again unless we first set the precapture trigger to idle
                if( MyDebug.LOG )
                    Log.d(TAG, "capture with precaptureBuilder");
                captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
                captureSession.setRepeatingRequest(precaptureBuilder.build(), previewCaptureCallback, handler);

                // now set precapture
                precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to precapture");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                push_take_picture_error_cb = take_picture_error_cb;
            }
        }

        // need to call callbacks without a lock
        if( push_take_picture_error_cb != null ) {
            push_take_picture_error_cb.onError();
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "runPrecapture() took: " + (System.currentTimeMillis() - debug_time));
        }
    }
    
    private void runFakePrecapture() {
        if( MyDebug.LOG )
            Log.d(TAG, "runFakePrecapture");
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }

        boolean turn_frontscreen_on = false;
        ErrorCallback push_take_picture_error_cb = null;

        synchronized( background_camera_lock ) {
            switch(camera_settings.flash_value) {
                case "flash_auto":
                case "flash_on":
                    if(MyDebug.LOG)
                        Log.d(TAG, "turn on torch");
                    if( !camera_settings.has_iso ) {
                        // in auto-mode, need to ensure CONTROL_AE_MODE isn't est to flash auto/on for torch to work
                        // in manual-mode, fine as CONTROL_AE_MODE will be off
                        previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    }
                    previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    test_fake_flash_precapture++;
                    fake_precapture_torch_performed = true;
                    break;
                case "flash_frontscreen_auto":
                case "flash_frontscreen_on":
                    turn_frontscreen_on = true;
                    break;
                default:
                    if(MyDebug.LOG)
                        Log.e(TAG, "runFakePrecapture called with unexpected flash value: " + camera_settings.flash_value);
                    break;
            }
        }

        // need to call callbacks without a lock
        if( turn_frontscreen_on ) {
            if(picture_cb != null) {
                if(MyDebug.LOG)
                    Log.d(TAG, "request screen turn on for frontscreen flash");
                picture_cb.onFrontScreenTurnOn();
            }
            else {
                if (MyDebug.LOG)
                    Log.e(TAG, "can't request screen turn on for frontscreen flash, as no picture_cb");
            }
        }

        synchronized( background_camera_lock ) {
            state = STATE_WAITING_FAKE_PRECAPTURE_START;
            precapture_state_change_time_ms = System.currentTimeMillis();
            fake_precapture_turn_on_torch_id = null;
            try {
                CaptureRequest request = previewBuilder.build();
                if( fake_precapture_torch_performed ) {
                    fake_precapture_turn_on_torch_id = request;
                    if( MyDebug.LOG )
                        Log.d(TAG, "fake_precapture_turn_on_torch_id: " + request);
                }
                setRepeatingRequest(request);
            }
            catch(CameraAccessException e) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "failed to start fake precapture");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                jpeg_todo = false;
                raw_todo = false;
                picture_cb = null;
                push_take_picture_error_cb = take_picture_error_cb;
            }
        }

        // need to call callbacks without a lock
        if( push_take_picture_error_cb != null ) {
            push_take_picture_error_cb.onError();
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "runFakePrecapture() took: " + (System.currentTimeMillis() - debug_time));
        }
    }

    private boolean fireAutoFlashFrontScreen() {
        // iso_threshold fine-tuned for Nexus 6 - front camera ISO never goes above 805, but a threshold of 700 is too low
        final int iso_threshold = 750;
        return capture_result_has_iso && capture_result_iso >= iso_threshold;
    }
    
    /** Used in use_fake_precapture mode when flash is auto, this returns whether we fire the flash.
     *  If the decision was recently calculated, we return that same decision - used to fix problem that if
     *  we fire flash during autofocus (for autofocus mode), we don't then want to decide the scene is too
     *  bright to not need flash for taking photo!
     */
    private boolean fireAutoFlash() {
        if( MyDebug.LOG )
            Log.d(TAG, "fireAutoFlash");
        long time_now = System.currentTimeMillis();
        if( MyDebug.LOG && fake_precapture_use_flash_time_ms != -1 ) {
            Log.d(TAG, "fake_precapture_use_flash_time_ms: " + fake_precapture_use_flash_time_ms);
            Log.d(TAG, "time_now: " + time_now);
            Log.d(TAG, "time since last flash auto decision: " + (time_now - fake_precapture_use_flash_time_ms));
        }
        final long cache_time_ms = 3000; // needs to be at least the time of a typical autoflash, see comment for this function above
        if( fake_precapture_use_flash_time_ms != -1 && time_now - fake_precapture_use_flash_time_ms < cache_time_ms ) {
            if( MyDebug.LOG )
                Log.d(TAG, "use recent decision: " + fake_precapture_use_flash);
            fake_precapture_use_flash_time_ms = time_now;
            return fake_precapture_use_flash;
        }
        switch(camera_settings.flash_value) {
            case "flash_auto":
                fake_precapture_use_flash = is_flash_required;
                break;
            case "flash_frontscreen_auto":
                fake_precapture_use_flash = fireAutoFlashFrontScreen();
                if(MyDebug.LOG)
                    Log.d(TAG, "    ISO was: " + capture_result_iso);
                break;
            default:
                // shouldn't really be calling this function if not flash auto...
                fake_precapture_use_flash = false;
                break;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "fake_precapture_use_flash: " + fake_precapture_use_flash);
        // We only cache the result if we decide to turn on torch, as that mucks up our ability to tell if we need the flash (since once the torch
        // is on, the ae_state thinks it's bright enough to not need flash!)
        // But if we don't turn on torch, this problem doesn't occur, so no need to cache - and good that the next time we should make an up-to-date
        // decision.
        if( fake_precapture_use_flash ) {
            fake_precapture_use_flash_time_ms = time_now;
        }
        else {
            fake_precapture_use_flash_time_ms = -1;
        }
        return fake_precapture_use_flash;
    }
    
    @Override
    public void takePicture(final PictureCallback picture, final ErrorCallback error) {
        if( MyDebug.LOG )
            Log.d(TAG, "takePicture");
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }

        boolean call_takePictureAfterPrecapture = false;
        boolean call_runFakePrecapture = false;
        boolean call_runPrecapture = false;

        synchronized( background_camera_lock ) {
            if( camera == null || captureSession == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "no camera or capture session");
                error.onError();
                return;
            }
            this.picture_cb = picture;
            this.jpeg_todo = true;
            this.raw_todo = imageReaderRaw != null;
            this.done_all_captures = false;
            this.take_picture_error_cb = error;
            this.fake_precapture_torch_performed = false; // just in case still on?
            if( !ready_for_capture ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "takePicture: not ready for capture!");
                //throw new RuntimeException(); // debugging
            }

            {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "current flash value: " + camera_settings.flash_value);
                    Log.d(TAG, "use_fake_precapture_mode: " + use_fake_precapture_mode);
                }
                // Don't need precapture if flash off or torch
                if( camera_settings.flash_value.equals("flash_off") || camera_settings.flash_value.equals("flash_torch") || camera_settings.flash_value.equals("flash_frontscreen_torch") ) {
                    call_takePictureAfterPrecapture = true;
                }
                else if( use_fake_precapture_mode ) {
                    // fake flash auto/on mode
                    // fake precapture works by turning on torch (or using a "front screen flash"), so we can't use the camera's own decision for flash auto
                    // instead we check the current ISO value
                    boolean auto_flash = camera_settings.flash_value.equals("flash_auto") || camera_settings.flash_value.equals("flash_frontscreen_auto");
                    Integer flash_mode = previewBuilder.get(CaptureRequest.FLASH_MODE);
                    if( MyDebug.LOG )
                        Log.d(TAG, "flash_mode: " + flash_mode);
                    if( auto_flash && !fireAutoFlash() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "fake precapture flash auto: seems bright enough to not need flash");
                        call_takePictureAfterPrecapture = true;
                    }
                    else if( flash_mode != null && flash_mode == CameraMetadata.FLASH_MODE_TORCH ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "fake precapture flash: torch already on (presumably from autofocus)");
                        // On some devices (e.g., OnePlus 3T), if we've already turned on torch for an autofocus immediately before
                        // taking the photo, ae convergence may have already occurred - so if we called runFakePrecapture(), we'd just get
                        // stuck waiting for CONTROL_AE_STATE_SEARCHING which will never happen, until we hit the timeout - it works,
                        // but it means taking photos is slower as we have to wait until the timeout
                        // Instead we assume that ae scanning has already started, so go straight to STATE_WAITING_FAKE_PRECAPTURE_DONE,
                        // which means wait until we're no longer CONTROL_AE_STATE_SEARCHING.
                        // (Note, we don't want to go straight to takePictureAfterPrecapture(), as it might be that ae scanning is still
                        // taking place.)
                        // An alternative solution would be to switch torch off and back on again to cause ae scanning to start - but
                        // at worst this is tricky to get working, and at best, taking photos would be slower.
                        fake_precapture_torch_performed = true; // so we know to fire the torch when capturing
                        test_fake_flash_precapture++; // for testing, should treat this same as if we did do the precapture
                        state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                        precapture_state_change_time_ms = System.currentTimeMillis();
                    }
                    else {
                        call_runFakePrecapture = true;
                    }
                }
                else {
                    // standard flash, flash auto or on
                    // note that we don't call needsFlash() (or use is_flash_required) - as if ae state is neither CONVERGED nor FLASH_REQUIRED, we err on the side
                    // of caution and don't skip the precapture
                    //boolean needs_flash = capture_result_ae != null && capture_result_ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED;
                    boolean needs_flash = capture_result_ae != null && capture_result_ae != CaptureResult.CONTROL_AE_STATE_CONVERGED;
                    if( camera_settings.flash_value.equals("flash_auto") && !needs_flash ) {
                        // if we call precapture anyway, flash wouldn't fire - but we tend to have a pause
                        // so skipping the precapture if flash isn't going to fire makes this faster
                        if( MyDebug.LOG )
                            Log.d(TAG, "flash auto, but we don't need flash");
                        call_takePictureAfterPrecapture = true;
                    }
                    else {
                        call_runPrecapture = true;
                    }
                }
            }

            /*camera_settings.setupBuilder(previewBuilder, false);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            state = STATE_WAITING_AUTOFOCUS;
            precapture_started = -1;
            //capture();
            setRepeatingRequest();*/
        }

        // important to call functions outside of locks, so that they can in turn call callbacks without a lock
        if( call_takePictureAfterPrecapture ) {
            takePictureAfterPrecapture();
        }
        else if( call_runFakePrecapture ) {
            runFakePrecapture();
        }
        else if( call_runPrecapture ) {
            runPrecapture();
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "takePicture() took: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    public void setDisplayOrientation(int degrees) {
        // for CameraController2, the preview display orientation is handled via the TextureView's transform
        if( MyDebug.LOG )
            Log.d(TAG, "setDisplayOrientation not supported by this API");
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public int getDisplayOrientation() {
        if( MyDebug.LOG )
            Log.d(TAG, "getDisplayOrientation not supported by this API");
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public int getCameraOrientation() {
        // cached for performance, as this method is frequently called from Preview.onOrientationChanged
        return characteristics_sensor_orientation;
    }

    @Override
    public Facing getFacing() {
        // cached for performance, as this method is frequently called from Preview.onOrientationChanged
        return characteristics_facing;
    }

    @Override
    public void unlock() {
        // do nothing at this stage
    }

    @Override
    public void initVideoRecorderPrePrepare(MediaRecorder video_recorder) {
        // if we change where we play the START_VIDEO_RECORDING sound, make sure it can't be heard in resultant video
        playSound(MediaActionSound.START_VIDEO_RECORDING);
    }

    @Override
    public void initVideoRecorderPostPrepare(MediaRecorder video_recorder, boolean want_photo_video_recording) throws CameraControllerException {
        if( MyDebug.LOG )
            Log.d(TAG, "initVideoRecorderPostPrepare");
        if( camera == null ) {
            Log.e(TAG, "no camera");
            throw new CameraControllerException();
        }
        try {
            if( MyDebug.LOG )
                Log.d(TAG, "obtain video_recorder surface");
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            if( MyDebug.LOG )
                Log.d(TAG, "done");
            previewIsVideoMode = true;
            previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
            camera_settings.setupBuilder(previewBuilder, false);
            createCaptureSession(video_recorder, want_photo_video_recording);
        }
        catch(CameraAccessException e) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "failed to create capture request for video");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new CameraControllerException();
        }
    }

    @Override
    public void reconnect() throws CameraControllerException {
        if( MyDebug.LOG )
            Log.d(TAG, "reconnect");
        // if we change where we play the STOP_VIDEO_RECORDING sound, make sure it can't be heard in resultant video
        playSound(MediaActionSound.STOP_VIDEO_RECORDING);
        createPreviewRequest();
        createCaptureSession(null, false);
        /*if( MyDebug.LOG )
            Log.d(TAG, "add preview surface to previewBuilder");
        Surface surface = getPreviewSurface();
        previewBuilder.addTarget(surface);*/
        //setRepeatingRequest();
    }

    @Override
    public String getParametersString() {
        return null;
    }

    @Override
    public boolean captureResultIsAEScanning() {
        return capture_result_is_ae_scanning;
    }

    @Override
    public boolean needsFlash() {
        //boolean needs_flash = capture_result_ae != null && capture_result_ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED;
        //return needs_flash;
        return is_flash_required;
    }

    @Override
    public boolean needsFrontScreenFlash() {
        return camera_settings.flash_value.equals("flash_frontscreen_on") ||
                ( camera_settings.flash_value.equals("flash_frontscreen_auto") && fireAutoFlashFrontScreen() );
    }

    @Override
    public boolean captureResultHasWhiteBalanceTemperature() {
        return capture_result_has_white_balance_rggb;
    }

    @Override
    public int captureResultWhiteBalanceTemperature() {
        // for performance reasons, we don't convert from rggb to temperature in every frame, rather only when requested
        return convertRggbToTemperature(capture_result_white_balance_rggb);
    }

    @Override
    public boolean captureResultHasIso() {
        return capture_result_has_iso;
    }

    @Override
    public int captureResultIso() {
        return capture_result_iso;
    }
    
    @Override
    public boolean captureResultHasExposureTime() {
        return capture_result_has_exposure_time;
    }

    @Override
    public long captureResultExposureTime() {
        return capture_result_exposure_time;
    }

    @Override
    public boolean captureResultHasFrameDuration() {
        return capture_result_has_frame_duration;
    }

    @Override
    public long captureResultFrameDuration() {
        return capture_result_frame_duration;
    }

    @Override
    public boolean captureResultHasAperture() {
        return capture_result_has_aperture;
    }

    @Override
    public float captureResultAperture() {
        return capture_result_aperture;
    }

    /*
    @Override
    public boolean captureResultHasFocusDistance() {
        return capture_result_has_focus_distance;
    }

    @Override
    public float captureResultFocusDistanceMin() {
        return capture_result_focus_distance_min;
    }

    @Override
    public float captureResultFocusDistanceMax() {
        return capture_result_focus_distance_max;
    }
    */

    private final CameraCaptureSession.CaptureCallback previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private long last_process_frame_number = 0;
        private int last_af_state = -1;

        private RequestTagType getRequestTagType(@NonNull CaptureRequest request) {
            Object tag = request.getTag();
            if( tag == null )
                return null;
            RequestTagObject requestTag = (RequestTagObject)tag;
            return requestTag.getType();
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            if( MyDebug.LOG )
                Log.d(TAG, "onCaptureBufferLost: " + frameNumber);
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "onCaptureFailed: " + failure);
                Log.d(TAG, "reason: " + failure.getReason());
                Log.d(TAG, "was image captured?: " + failure.wasImageCaptured());
                Log.d(TAG, "sequenceId: " + failure.getSequenceId());
            }
            super.onCaptureFailed(session, request, failure); // API docs say this does nothing, but call it just to be safe
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "onCaptureSequenceAborted");
                Log.d(TAG, "sequenceId: " + sequenceId);
            }
            super.onCaptureSequenceAborted(session, sequenceId); // API docs say this does nothing, but call it just to be safe
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "onCaptureSequenceCompleted");
                Log.d(TAG, "sequenceId: " + sequenceId);
                Log.d(TAG, "frameNumber: " + frameNumber);
            }
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber); // API docs say this does nothing, but call it just to be safe
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            if( MyDebug.LOG ) {
                if( getRequestTagType(request) == RequestTagType.CAPTURE ) {
                    Log.d(TAG, "onCaptureStarted: capture");
                    Log.d(TAG, "frameNumber: " + frameNumber);
                    Log.d(TAG, "exposure time: " + request.get(CaptureRequest.SENSOR_EXPOSURE_TIME));
                }
            }
            // n.b., we don't play the shutter sound here for RequestTagType.CAPTURE, as it typically sounds "too late"
            // (if ever we changed this, would also need to fix for burst, where we only set the RequestTagType.CAPTURE for the last image)
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            /*if( MyDebug.LOG )
                Log.d(TAG, "onCaptureProgressed");*/
            //process(request, partialResult);
            // Note that we shouldn't try to process partial results - or if in future we decide to, remember that it's documented that
            // not all results may be available. E.g., OnePlus 3T on Android 7 (OxygenOS 4.0.2) reports null for AF_STATE from this method.
            // We'd also need to fix up the discarding of old frames in process(), as we probably don't want to be discarding the
            // complete results from onCaptureCompleted()!
            super.onCaptureProgressed(session, request, partialResult); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            /*if( MyDebug.LOG )
                Log.d(TAG, "onCaptureCompleted");*/
            if( MyDebug.LOG ) {
                if( getRequestTagType(request) == RequestTagType.CAPTURE ) {
                    Log.d(TAG, "onCaptureCompleted: capture");
                    Log.d(TAG, "sequenceId: " + result.getSequenceId());
                    Log.d(TAG, "frameNumber: " + result.getFrameNumber());
                    Log.d(TAG, "exposure time: " + request.get(CaptureRequest.SENSOR_EXPOSURE_TIME));
                    Log.d(TAG, "frame duration: " + request.get(CaptureRequest.SENSOR_FRAME_DURATION));
                }
            }
            process(request, result);
            processCompleted(request, result);
            super.onCaptureCompleted(session, request, result); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
        }

        /** Updates cached information regarding the capture result status related to auto-exposure.
         */
        private void updateCachedAECaptureStatus(CaptureResult result) {
            Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
            /*if( MyDebug.LOG ) {
                if( ae_state == null )
                    Log.d(TAG, "CONTROL_AE_STATE is null");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_INACTIVE )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_INACTIVE");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_SEARCHING");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_CONVERGED )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_CONVERGED");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_LOCKED )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_LOCKED");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_FLASH_REQUIRED");
                else if( ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE )
                    Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_PRECAPTURE");
                else
                    Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
            }*/
            Integer flash_mode = result.get(CaptureResult.FLASH_MODE);
            /*if( MyDebug.LOG ) {
                if( flash_mode == null )
                    Log.d(TAG, "FLASH_MODE is null");
                else if( flash_mode == CaptureResult.FLASH_MODE_OFF )
                    Log.d(TAG, "FLASH_MODE = FLASH_MODE_OFF");
                else if( flash_mode == CaptureResult.FLASH_MODE_SINGLE )
                    Log.d(TAG, "FLASH_MODE = FLASH_MODE_SINGLE");
                else if( flash_mode == CaptureResult.FLASH_MODE_TORCH )
                    Log.d(TAG, "FLASH_MODE = FLASH_MODE_TORCH");
                else
                    Log.d(TAG, "FLASH_MODE = " + flash_mode);
            }*/

            if( use_fake_precapture_mode && ( fake_precapture_torch_focus_performed || fake_precapture_torch_performed ) && flash_mode != null && flash_mode == CameraMetadata.FLASH_MODE_TORCH ) {
                // don't change ae state while torch is on for fake flash
            }
            else if( ae_state == null ) {
                capture_result_ae = null;
                is_flash_required = false;
            }
            else if( !ae_state.equals(capture_result_ae) ) {
                // need to store this before calling the autofocus callbacks below
                if( MyDebug.LOG )
                    Log.d(TAG, "CONTROL_AE_STATE changed from " + capture_result_ae + " to " + ae_state);
                capture_result_ae = ae_state;
                // capture_result_ae should always be non-null here, as we've already handled ae_state separately
                if( capture_result_ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED && !is_flash_required ) {
                    is_flash_required = true;
                    if( MyDebug.LOG )
                        Log.d(TAG, "flash now required");
                }
                else if( capture_result_ae == CaptureResult.CONTROL_AE_STATE_CONVERGED && is_flash_required ) {
                    is_flash_required = false;
                    if( MyDebug.LOG )
                        Log.d(TAG, "flash no longer required");
                }
            }

            if( ae_state != null && ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING ) {
                /*if( MyDebug.LOG && !capture_result_is_ae_scanning )
                    Log.d(TAG, "ae_state now searching");*/
                capture_result_is_ae_scanning = true;
            }
            else {
                /*if( MyDebug.LOG && capture_result_is_ae_scanning )
                    Log.d(TAG, "ae_state stopped searching");*/
                capture_result_is_ae_scanning = false;
            }
        }

        private void handleStateChange(CaptureRequest request, CaptureResult result) {
            // use Integer instead of int, so can compare to null: Google Play crashes confirmed that this can happen; Google Camera also ignores cases with null af state
            Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
            /*if( MyDebug.LOG ) {
                if( af_state == null )
                    Log.d(TAG, "CONTROL_AF_STATE is null");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_INACTIVE )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_INACTIVE");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_PASSIVE_SCAN");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_PASSIVE_FOCUSED");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_ACTIVE_SCAN");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_FOCUSED_LOCKED");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_NOT_FOCUSED_LOCKED");
                else if( af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED )
                    Log.d(TAG, "CONTROL_AF_STATE = CONTROL_AF_STATE_PASSIVE_UNFOCUSED");
                else
                    Log.d(TAG, "CONTROL_AF_STATE = " + af_state);
            }*/
            // CONTROL_AE_STATE can be null on some devices, so as with af_state, use Integer
            Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
            /*Integer awb_state = result.get(CaptureResult.CONTROL_AWB_STATE);
            if( MyDebug.LOG ) {
                if( awb_state == null )
                    Log.d(TAG, "CONTROL_AWB_STATE is null");
                else if( awb_state == CaptureResult.CONTROL_AWB_STATE_INACTIVE )
                    Log.d(TAG, "CONTROL_AWB_STATE = CONTROL_AWB_STATE_INACTIVE");
                else if( awb_state == CaptureResult.CONTROL_AWB_STATE_SEARCHING )
                    Log.d(TAG, "CONTROL_AWB_STATE = CONTROL_AWB_STATE_SEARCHING");
                else if( awb_state == CaptureResult.CONTROL_AWB_STATE_CONVERGED )
                    Log.d(TAG, "CONTROL_AWB_STATE = CONTROL_AWB_STATE_CONVERGED");
                else if( awb_state == CaptureResult.CONTROL_AWB_STATE_LOCKED )
                    Log.d(TAG, "CONTROL_AWB_STATE = CONTROL_AWB_STATE_LOCKED");
                else
                    Log.d(TAG, "CONTROL_AWB_STATE = " + awb_state);
            }*/

            if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "not ready for capture: " + af_state);*/
                ready_for_capture = false;
            }
            else {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "ready for capture: " + af_state);*/
                ready_for_capture = true;
                if( autofocus_cb != null && (!do_af_trigger_for_continuous || use_fake_precapture_mode) && focusIsContinuous() ) {
                    Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                    if( focus_mode != null && focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "call autofocus callback, as continuous mode and not focusing: " + af_state);
                        // need to check af_state != null, I received Google Play crash in 1.33 where it was null
                        boolean focus_success = af_state != null && ( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED );
                        if( MyDebug.LOG ) {
                            if( focus_success )
                                Log.d(TAG, "autofocus success");
                            else
                                Log.d(TAG, "autofocus failed");
                            if( af_state == null )
                                Log.e(TAG, "continuous focus mode but af_state is null");
                            else
                                Log.d(TAG, "af_state: " + af_state);
                        }
                        if( af_state == null ) {
                            test_af_state_null_focus++;
                        }
                        autofocus_cb.onAutoFocus(focus_success);
                        autofocus_cb = null;
                        capture_follows_autofocus_hint = false;
                    }
                }
            }

            /*if( MyDebug.LOG ) {
                if( autofocus_cb == null ) {
                    if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED )
                        Log.d(TAG, "processAF: autofocus success but no callback set");
                    else if( af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED )
                        Log.d(TAG, "processAF: autofocus failed but no callback set");
                }
            }*/

            if( fake_precapture_turn_on_torch_id != null && fake_precapture_turn_on_torch_id == request ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "torch turned on for fake precapture");
                fake_precapture_turn_on_torch_id = null;
            }

            if( state == STATE_NORMAL ) {
                // do nothing
            }
            else if( state == STATE_WAITING_AUTOFOCUS ) {
                if( af_state == null ) {
                    // autofocus shouldn't really be requested if af not available, but still allow this rather than getting stuck waiting for autofocus to complete
                    if( MyDebug.LOG )
                        Log.e(TAG, "waiting for autofocus but af_state is null");
                    test_af_state_null_focus++;
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    if( autofocus_cb != null ) {
                        autofocus_cb.onAutoFocus(false);
                        autofocus_cb = null;
                    }
                    capture_follows_autofocus_hint = false;
                }
                else if( af_state != last_af_state ) {
                    // check for autofocus completing
                    if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED /*||
                            af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED*/
                    ) {
                        boolean focus_success = af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
                        if( MyDebug.LOG ) {
                            if( focus_success )
                                Log.d(TAG, "onCaptureCompleted: autofocus success");
                            else
                                Log.d(TAG, "onCaptureCompleted: autofocus failed");
                            Log.d(TAG, "af_state: " + af_state);
                        }
                        state = STATE_NORMAL;
                        precapture_state_change_time_ms = -1;
                        if( use_fake_precapture_mode && fake_precapture_torch_focus_performed ) {
                            fake_precapture_torch_focus_performed = false;
                            if( !capture_follows_autofocus_hint ) {
                                // If we're going to be taking a photo immediately after the autofocus, it's better for the fake flash
                                // mode to leave the torch on. If we don't do this, one of the following issues can happen:
                                // - On OnePlus 3T, the torch doesn't get turned off, but because we've switched off the torch flag
                                //   in previewBuilder, we go ahead with the precapture routine instead of
                                if( MyDebug.LOG )
                                    Log.d(TAG, "turn off torch after focus (fake precapture code)");

                                // same hack as in setFlashValue() - for fake precapture we need to turn off the torch mode that was set, but
                                // at least on Nexus 6, we need to turn to flash_off to turn off the torch!
                                String saved_flash_value = camera_settings.flash_value;
                                camera_settings.flash_value = "flash_off";
                                camera_settings.setAEMode(previewBuilder, false);
                                try {
                                    capture();
                                }
                                catch(CameraAccessException e) {
                                    if( MyDebug.LOG ) {
                                        Log.e(TAG, "failed to do capture to turn off torch after autofocus");
                                        Log.e(TAG, "reason: " + e.getReason());
                                        Log.e(TAG, "message: " + e.getMessage());
                                    }
                                    e.printStackTrace();
                                }

                                // now set the actual (should be flash auto or flash on) mode
                                camera_settings.flash_value = saved_flash_value;
                                camera_settings.setAEMode(previewBuilder, false);
                                try {
                                    setRepeatingRequest();
                                }
                                catch(CameraAccessException e) {
                                    if( MyDebug.LOG ) {
                                        Log.e(TAG, "failed to set repeating request to turn off torch after autofocus");
                                        Log.e(TAG, "reason: " + e.getReason());
                                        Log.e(TAG, "message: " + e.getMessage());
                                    }
                                    e.printStackTrace();
                                }
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "torch was enabled for autofocus, leave it on for capture (fake precapture code)");
                            }
                        }
                        if( autofocus_cb != null ) {
                            autofocus_cb.onAutoFocus(focus_success);
                            autofocus_cb = null;
                        }
                        capture_follows_autofocus_hint = false;
                    }
                }
            }
            else if( state == STATE_WAITING_PRECAPTURE_START ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "waiting for precapture start...");
                if( MyDebug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( ae_state == null || ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE /*|| ae_state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED*/ ) {
                    // we have to wait for CONTROL_AE_STATE_PRECAPTURE; if we allow CONTROL_AE_STATE_FLASH_REQUIRED, then on Nexus 6 at least we get poor quality results with flash:
                    // varying levels of brightness, sometimes too bright or too dark, sometimes with blue tinge, sometimes even with green corruption
                    // similarly photos with flash come out too dark on OnePlus 3T
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "precapture started after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_WAITING_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_start_timeout_c ) {
                    // hack - give up waiting - sometimes we never get a CONTROL_AE_STATE_PRECAPTURE so would end up stuck
                    // always log error, so we can look for it when manually testing with logging disabled
                    Log.e(TAG, "precapture start timeout");
                    count_precapture_timeout++;
                    state = STATE_WAITING_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
            }
            else if( state == STATE_WAITING_PRECAPTURE_DONE ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "waiting for precapture done...");
                if( MyDebug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_PRECAPTURE ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "precapture completed after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_done_timeout_c ) {
                    // just in case
                    // always log error, so we can look for it when manually testing with logging disabled
                    Log.e(TAG, "precapture done timeout");
                    count_precapture_timeout++;
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
            }
            else if( state == STATE_WAITING_FAKE_PRECAPTURE_START ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "waiting for fake precapture start...");
                if( MyDebug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( fake_precapture_turn_on_torch_id != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "still waiting for torch to come on for fake precapture");
                }

                if( fake_precapture_turn_on_torch_id == null && (ae_state == null || ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING) ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "fake precapture started after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
                else if( fake_precapture_turn_on_torch_id == null && camera_settings.has_iso && precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > 100 ) {
                    // When using manual ISO, we can't make use of changes to the ae_state - but at the same time, we don't
                    // need ISO/exposure to re-adjust anyway.
                    // If fake_precapture_turn_on_torch_id != null, we still wait for the physical torch to turn on.
                    // But if fake_precapture_turn_on_torch_id==null (i.e., for flash_frontscreen_torch), just wait a short
                    // period to ensure the frontscreen flash has enabled.
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "fake precapture started after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_start_timeout_c ) {
                    // just in case
                    // always log error, so we can look for it when manually testing with logging disabled
                    Log.e(TAG, "fake precapture start timeout");
                    count_precapture_timeout++;
                    state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                    fake_precapture_turn_on_torch_id = null;
                }
            }
            else if( state == STATE_WAITING_FAKE_PRECAPTURE_DONE ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "waiting for fake precapture done...");
                if( MyDebug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                    Log.d(TAG, "ready_for_capture? " + ready_for_capture);
                }
                // wait for af and ae scanning to end (need to check af too, as in continuous focus mode, a focus may start again after switching torch on for the fake precapture)
                if( ready_for_capture && ( ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_SEARCHING)  ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "fake precapture completed after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_done_timeout_c ) {
                    // sometimes camera can take a while to stop ae/af scanning, better to just go ahead and take photo
                    // always log error, so we can look for it when manually testing with logging disabled
                    Log.e(TAG, "fake precapture done timeout");
                    count_precapture_timeout++;
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
            }
        }

        private void handleContinuousFocusMove(CaptureResult result) {
            Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
            if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "continuous focusing started");*/
                if( continuous_focus_move_callback != null ) {
                    continuous_focus_move_callback.onContinuousFocusMove(true);
                }
            }
            else if( af_state != null && last_af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "continuous focusing stopped");*/
                if( continuous_focus_move_callback != null ) {
                    continuous_focus_move_callback.onContinuousFocusMove(false);
                }
            }
        }

        /** Processes either a partial or total result.
         */
        private void process(CaptureRequest request, CaptureResult result) {
            /*if( MyDebug.LOG )
            Log.d(TAG, "process, state: " + state);*/
            if( result.getFrameNumber() < last_process_frame_number ) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "processAF discarded outdated frame " + result.getFrameNumber() + " vs " + last_process_frame_number);*/
                return;
            }
            /*long debug_time = 0;
            if( MyDebug.LOG ) {
                debug_time = System.currentTimeMillis();
            }*/
            last_process_frame_number = result.getFrameNumber();

            updateCachedAECaptureStatus(result);

            handleStateChange(request, result);

            handleContinuousFocusMove(result);

            Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
            if( af_state != null && af_state != last_af_state ) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "CONTROL_AF_STATE changed from " + last_af_state + " to " + af_state);*/
                last_af_state = af_state;
            }

            /*if( MyDebug.LOG ) {
                Log.d(TAG, "process() took: " + (System.currentTimeMillis() - debug_time));
            }*/
        }

        /** Updates cached information regarding the capture result.
         */
        private void updateCachedCaptureResult(CaptureResult result) {
            if( modified_from_camera_settings ) {
                // don't update capture results!
                // otherwise have problem taking HDR photos twice in a row, the second one will pick up the exposure time as
                // being from the long exposure of the previous HDR/expo burst!
            }
            else if( result.get(CaptureResult.SENSOR_SENSITIVITY) != null ) {
                capture_result_has_iso = true;
                capture_result_iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                /*if( MyDebug.LOG )
                    Log.d(TAG, "capture_result_iso: " + capture_result_iso);*/
                /*if( camera_settings.has_iso && Math.abs(camera_settings.iso - capture_result_iso) > 10 && previewBuilder != null ) {
                    // ugly hack: problem (on Nexus 6 at least) that when we start recording video (video_recorder.start() call), this often causes the ISO setting to reset to the wrong value!
                    // seems to happen more often with shorter exposure time
                    // seems to happen on other camera apps with Camera2 API too
                    // update: allow some tolerance, as on OnePlus 3T it's normal to have some slight difference between requested and actual
                    // this workaround still means a brief flash with incorrect ISO, but is best we can do for now!
                    // check previewBuilder != null as we have had Google Play crashes from the setRepeatingRequest() call via here
                    // Update 20180326: can no longer reproduce original problem on Nexus 6 (at FullHD or 4K); no evidence of
                    // problems on OnePlus 3T or Nokia 8.
                    // Also note that this code was being activated whenever manual ISO is changed (since we don't immediately
                    // update to the new ISO). At the least, this should be restricted to when recording video, but best to
                    // disable completely now that we don't seem to need it.
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "ISO " + capture_result_iso + " different to requested ISO " + camera_settings.iso);
                        Log.d(TAG, "    requested ISO was: " + request.get(CaptureRequest.SENSOR_SENSITIVITY));
                        Log.d(TAG, "    requested AE mode was: " + request.get(CaptureRequest.CONTROL_AE_MODE));
                    }
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( MyDebug.LOG ) {
                            Log.e(TAG, "failed to set repeating request after ISO hack");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                    }
                }*/
            }
            else {
                capture_result_has_iso = false;
            }

            if( modified_from_camera_settings ) {
                // see note above
            }
            else if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
                capture_result_has_exposure_time = true;
                capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

                // If using manual exposure time longer than max_preview_exposure_time_c, the preview will be fixed to
                // max_preview_exposure_time_c, so we should just use the requested manual exposure time.
                // (This affects the exposure time shown on on-screen preview - whilst showing the preview exposure time
                // isn't necessarily wrong, it tended to confuse people, thinking that manual exposure time wasn't working
                // when set above max_preview_exposure_time_c.)
                if( camera_settings.has_iso && camera_settings.exposure_time > max_preview_exposure_time_c )
                    capture_result_exposure_time = camera_settings.exposure_time;

                if( capture_result_exposure_time <= 0 ) {
                    // wierd bug seen on Nokia 8
                    capture_result_has_exposure_time = false;
                }
            }
            else {
                capture_result_has_exposure_time = false;
            }

            if( modified_from_camera_settings ) {
                // see note above
            }
            else if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
                capture_result_has_frame_duration = true;
                capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            }
            else {
                capture_result_has_frame_duration = false;
            }
            /*if( MyDebug.LOG ) {
                if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
                    long capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Log.d(TAG, "capture_result_exposure_time: " + capture_result_exposure_time);
                }
                if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
                    long capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                    Log.d(TAG, "capture_result_frame_duration: " + capture_result_frame_duration);
                }
            }*/
            /*if( modified_from_camera_settings ) {
                // see note above
            }
            else if( result.get(CaptureResult.LENS_FOCUS_RANGE) != null ) {
                Pair<Float, Float> focus_range = result.get(CaptureResult.LENS_FOCUS_RANGE);
                capture_result_has_focus_distance = true;
                capture_result_focus_distance_min = focus_range.first;
                capture_result_focus_distance_max = focus_range.second;
            }
            else {
                capture_result_has_focus_distance = false;
            }*/
            if( modified_from_camera_settings ) {
                // see note above
            }
            else if( result.get(CaptureResult.LENS_APERTURE) != null ) {
                capture_result_has_aperture = true;
                capture_result_aperture = result.get(CaptureResult.LENS_APERTURE);
                /*if( MyDebug.LOG ) {
                    Log.d(TAG, "capture_result_aperture: " + capture_result_aperture);
                }*/
            }
            else {
                capture_result_has_aperture = false;
            }
            {
                RggbChannelVector vector = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
                if( modified_from_camera_settings ) {
                    // see note above
                }
                else if( vector != null ) {
                    capture_result_has_white_balance_rggb = true;
                    capture_result_white_balance_rggb = vector;
                }
            }

            /*if( MyDebug.LOG ) {
                RggbChannelVector vector = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
                if( vector != null ) {
                    convertRggbToTemperature(vector); // logging will occur in this function
                }
            }*/
        }

        private void handleFaceDetection(CaptureResult result) {
            if( face_detection_listener != null && previewBuilder != null ) {
                Integer face_detect_mode = previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE);
                if( face_detect_mode != null && face_detect_mode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF ) {
                    Rect sensor_rect = getViewableRect();
                    android.hardware.camera2.params.Face [] camera_faces = result.get(CaptureResult.STATISTICS_FACES);
                    if( camera_faces != null ) {
                        if( camera_faces.length == 0 && last_faces_detected == 0 ) {
                            // no point continually calling the callback if 0 faces detected (same behaviour as CameraController1)
                        }
                        else {
                            last_faces_detected = camera_faces.length;
                            CameraController.Face [] faces = new CameraController.Face[camera_faces.length];
                            for(int i=0;i<camera_faces.length;i++) {
                                faces[i] = convertFromCameraFace(sensor_rect, camera_faces[i]);
                            }
                            face_detection_listener.onFaceDetection(faces);
                        }
                    }
                }
            }
        }

        /** Passes the capture result to the RAW onImageAvailableListener, if it exists.
         */
        private void handleRawCaptureResult(CaptureResult result) {
            //test_wait_capture_result = true;
            if( test_wait_capture_result ) {
                // For RAW capture, we require the capture result before creating DngCreator
                // but for testing purposes, we need to test the possibility where onImageAvailable() for
                // the RAW image is called before we receive the capture result here.
                // Also with JPEG only capture, there are problems with repeat mode and continuous focus if
                // onImageAvailable() is called before this code is called, because it means here we cancel the
                // focus and lose the focus callback that was going to trigger the next repeat photo! This shows
                // up on testContinuousPictureFocusRepeat() on Nexus 7, but can be autotested on other devices
                // with the flag, see testContinuousPictureFocusRepeatWaitCaptureResult().
                try {
                    if( MyDebug.LOG )
                        Log.d(TAG, "test_wait_capture_result: waiting...");
                    // 200ms is enough to test the problem with testTakePhotoRawWaitCaptureResult() on Nexus 6, but use 500ms to be sure
                    // 200ms is enough to test the problem with testContinuousPictureFocusRepeatWaitCaptureResult() on Nokia 8, but use 500ms to be sure
                    Thread.sleep(500);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if( onRawImageAvailableListener != null ) {
                onRawImageAvailableListener.setCaptureResult(result);
            }
        }

        /** This should be called when a capture result corresponds to a capture that is for a burst
         *  sequence, that isn't the last capture (for the last capture, handleCaptureCompleted() is
         *  instead called).
         */
        private void handleCaptureBurstInProgress(CaptureResult result) {
            if( MyDebug.LOG )
                Log.d(TAG, "handleCaptureBurstInProgress");

            handleRawCaptureResult(result);
        }

        /** This should be called when a capture result corresponds to a capture that has completed.
         */
        private void handleCaptureCompleted(CaptureResult result) {
            if( MyDebug.LOG )
                Log.d(TAG, "capture request completed");
            test_capture_results++;
            modified_from_camera_settings = false;

            handleRawCaptureResult(result);

            // actual parsing of image data is done in the imageReader's OnImageAvailableListener()
            // need to cancel the autofocus, and restart the preview after taking the photo
            // Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
            if( previewBuilder != null ) {
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                if( MyDebug.LOG )
                    Log.d(TAG, "### reset ae mode");
                String saved_flash_value = camera_settings.flash_value;
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    // same hack as in setFlashValue() - for fake precapture we need to turn off the torch mode that was set, but
                    // at least on Nexus 6, we need to turn to flash_off to turn off the torch!
                    camera_settings.flash_value = "flash_off";
                }
                // if not using fake precapture, not sure if we need to set the ae mode, but the AE mode is set again in Camera2Basic
                camera_settings.setAEMode(previewBuilder, false);
                // n.b., if capture/setRepeatingRequest throw exception, we don't call the take_picture_error_cb.onError() callback, as the photo should have been taken by this point
                try {
                    capture();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to cancel autofocus after taking photo");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    // now set up the request to switch to the correct flash value
                    camera_settings.flash_value = saved_flash_value;
                    camera_settings.setAEMode(previewBuilder, false);
                }
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to start preview after taking photo");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    preview_error_cb.onError();
                }
            }
            fake_precapture_torch_performed = false;

            if( burst_type == BurstType.BURSTTYPE_FOCUS && previewBuilder != null ) { // make sure camera wasn't released in the meantime
                if( MyDebug.LOG )
                    Log.d(TAG, "focus bracketing complete, reset manual focus");
                camera_settings.setFocusDistance(previewBuilder);
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to set focus distance");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }


            // Important that we only call the picture onCompleted callback after we've received the capture request, so
            // we need to check if we already received all the images.
            // Also needs to be run on UI thread.
            // Needed for testContinuousPictureFocusRepeat on Nexus 7; also testable on other devices via
            // testContinuousPictureFocusRepeatWaitCaptureResult.
            final Activity activity = (Activity)context;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "processCompleted UI thread call checkImagesCompleted()");
                    synchronized( background_camera_lock ) {
                        done_all_captures = true;
                        if( MyDebug.LOG )
                            Log.d(TAG, "done all captures");
                    }
                    checkImagesCompleted();
                }
            });
        }

        /** Processes a total result.
         */
        private void processCompleted(CaptureRequest request, CaptureResult result) {
            /*if( MyDebug.LOG )
                Log.d(TAG, "processCompleted");*/

            if( !has_received_frame ) {
                has_received_frame = true;
                if( MyDebug.LOG )
                    Log.d(TAG, "has_received_frame now set to true");
            }

            updateCachedCaptureResult(result);
            handleFaceDetection(result);

            if( push_repeating_request_when_torch_off && push_repeating_request_when_torch_off_id == request && previewBuilder != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "received push_repeating_request_when_torch_off");
                Integer flash_state = result.get(CaptureResult.FLASH_STATE);
                if( MyDebug.LOG ) {
                    if( flash_state != null )
                        Log.d(TAG, "flash_state: " + flash_state);
                    else
                        Log.d(TAG, "flash_state is null");
                }
                if( flash_state != null && flash_state == CaptureResult.FLASH_STATE_READY ) {
                    push_repeating_request_when_torch_off = false;
                    push_repeating_request_when_torch_off_id = null;
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( MyDebug.LOG ) {
                            Log.e(TAG, "failed to set flash [from torch/flash off hack]");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                    } 
                }
            }
            /*if( push_set_ae_lock && push_set_ae_lock_id == request && previewBuilder != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "received push_set_ae_lock");
                // hack - needed to fix bug on Nexus 6 where auto-exposure sometimes locks when taking a photo of bright scene with flash on!
                // this doesn't completely resolve the issue, but seems to make it far less common; also when it does happen, taking another photo usually fixes it
                push_set_ae_lock = false;
                push_set_ae_lock_id = null;
                camera_settings.setAutoExposureLock(previewBuilder);
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( MyDebug.LOG ) {
                        Log.e(TAG, "failed to set ae lock [from ae lock hack]");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                } 
            }*/

            RequestTagType tag_type = getRequestTagType(request);
            if( tag_type == RequestTagType.CAPTURE ) {
                handleCaptureCompleted(result);
            }
            else if( tag_type == RequestTagType.CAPTURE_BURST_IN_PROGRESS ) {
                handleCaptureBurstInProgress(result);
            }
        }
    };
}
