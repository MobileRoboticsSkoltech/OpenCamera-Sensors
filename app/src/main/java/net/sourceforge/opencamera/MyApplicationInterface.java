package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.RawImage;
import net.sourceforge.opencamera.preview.ApplicationInterface;
import net.sourceforge.opencamera.preview.BasicApplicationInterface;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.preview.VideoProfile;
import net.sourceforge.opencamera.ui.DrawPreview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface extends BasicApplicationInterface {
    private static final String TAG = "MyApplicationInterface";

    // note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
    public enum PhotoMode {
        Standard,
        DRO, // single image "fake" HDR
        HDR, // HDR created from multiple (expo bracketing) images
        ExpoBracketing, // take multiple expo bracketed images, without combining to a single image
        FocusBracketing, // take multiple focus bracketed images, without combining to a single image
        FastBurst,
        NoiseReduction,
        Panorama
    }

    private final MainActivity main_activity;
    private final LocationSupplier locationSupplier;
    private final GyroSensor gyroSensor;
    private final StorageUtils storageUtils;
    private final DrawPreview drawPreview;
    private final ImageSaver imageSaver;

    private final static float panorama_pics_per_screen = 3.33333f;
    private int n_capture_images = 0; // how many calls to onPictureTaken() since the last call to onCaptureStarted()
    private int n_capture_images_raw = 0; // how many calls to onRawPictureTaken() since the last call to onCaptureStarted()
    private int n_panorama_pics = 0;
    public final static int max_panorama_pics_c = 10; // if we increase this, review against memory requirements under MainActivity.supportsPanorama()
    private boolean panorama_pic_accepted; // whether the last panorama picture was accepted, or else needs to be retaken
    private boolean panorama_dir_left_to_right = true; // direction of panorama (set after we've captured two images)

    private File last_video_file = null;
    private Uri last_video_file_uri = null;

    private final Timer subtitleVideoTimer = new Timer();
    private TimerTask subtitleVideoTimerTask;

    private final Rect text_bounds = new Rect();
    private boolean used_front_screen_flash ;

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private final SharedPreferences sharedPreferences;

    private enum LastImagesType {
        FILE,
        SAF,
        MEDIASTORE
    }
    private LastImagesType last_images_type = LastImagesType.FILE; // whether the last images array are using File API, SAF or MediaStore

    /** This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
     */
    private static class LastImage {
        final boolean share; // one of the images in the list should have share set to true, to indicate which image to share
        final String name;
        Uri uri;

        LastImage(Uri uri, boolean share) {
            this.name = null;
            this.uri = uri;
            this.share = share;
        }

        LastImage(String filename, boolean share) {
            this.name = filename;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                // previous to Android 7, we could just use a "file://" uri, but this is no longer supported on Android 7, and
                // results in a android.os.FileUriExposedException when trying to share!
                // see https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
                // so instead we leave null for now, and set it from MyApplicationInterface.scannedFile().
                this.uri = null;
            }
            else {
                this.uri = Uri.parse("file://" + this.name);
            }
            this.share = share;
        }
    }
    private final List<LastImage> last_images = new ArrayList<>();

    private final ToastBoxer photo_delete_toast = new ToastBoxer();

    // camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
    private final static int cameraId_default = 0;
    private boolean has_set_cameraId;
    private int cameraId = cameraId_default;
    private final static String nr_mode_default = "preference_nr_mode_normal";
    private String nr_mode = nr_mode_default;
    private final static float aperture_default = -1.0f;
    private float aperture = aperture_default;
    // camera properties that aren't saved even in the bundle; these should be initialised/reset in reset()
    private int zoom_factor; // don't save zoom, as doing so tends to confuse users; other camera applications don't seem to save zoom when pause/resuming

    // for testing:
    public volatile int test_n_videos_scanned;
    public volatile int test_max_mp;

    MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "MyApplicationInterface");
            debug_time = System.currentTimeMillis();
        }
        this.main_activity = main_activity;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        this.locationSupplier = new LocationSupplier(main_activity);
        if( MyDebug.LOG )
            Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
        this.gyroSensor = new GyroSensor(main_activity);
        this.storageUtils = new StorageUtils(main_activity, this);
        if( MyDebug.LOG )
            Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
        this.drawPreview = new DrawPreview(main_activity, this);

        this.imageSaver = new ImageSaver(main_activity);
        this.imageSaver.start();

        this.reset(false);
        if( savedInstanceState != null ) {
            // load the things we saved in onSaveInstanceState().
            if( MyDebug.LOG )
                Log.d(TAG, "read from savedInstanceState");
            has_set_cameraId = true;
            cameraId = savedInstanceState.getInt("cameraId", cameraId_default);
            if( MyDebug.LOG )
                Log.d(TAG, "found cameraId: " + cameraId);
            nr_mode = savedInstanceState.getString("nr_mode", nr_mode_default);
            if( MyDebug.LOG )
                Log.d(TAG, "found nr_mode: " + nr_mode);
            aperture = savedInstanceState.getFloat("aperture", aperture_default);
            if( MyDebug.LOG )
                Log.d(TAG, "found aperture: " + aperture);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
    }

    /** Here we save states which aren't saved in preferences (we don't want them to be saved if the
     *  application is restarted from scratch), but we do want to preserve if Android has to recreate
     *  the application (e.g., configuration change, or it's destroyed while in background).
     */
    void onSaveInstanceState(Bundle state) {
        if( MyDebug.LOG )
            Log.d(TAG, "onSaveInstanceState");
        if( MyDebug.LOG )
            Log.d(TAG, "save cameraId: " + cameraId);
        state.putInt("cameraId", cameraId);
        if( MyDebug.LOG )
            Log.d(TAG, "save nr_mode: " + nr_mode);
        state.putString("nr_mode", nr_mode);
        if( MyDebug.LOG )
            Log.d(TAG, "save aperture: " + aperture);
        state.putFloat("aperture", aperture);
    }

    void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        if( drawPreview != null ) {
            drawPreview.onDestroy();
        }
        if( imageSaver != null ) {
            imageSaver.onDestroy();
        }
    }

    LocationSupplier getLocationSupplier() {
        return locationSupplier;
    }

    public GyroSensor getGyroSensor() {
        return gyroSensor;
    }

    StorageUtils getStorageUtils() {
        return storageUtils;
    }

    public ImageSaver getImageSaver() {
        return imageSaver;
    }

    public DrawPreview getDrawPreview() {
        return drawPreview;
    }

    @Override
    public Context getContext() {
        return main_activity;
    }

    @Override
    public boolean useCamera2() {
        if( main_activity.supportsCamera2() ) {
            String camera_api = sharedPreferences.getString(PreferenceKeys.CameraAPIPreferenceKey, PreferenceKeys.CameraAPIPreferenceDefault);
            if( "preference_camera_api_camera2".equals(camera_api) ) {
                return true;
            }
        }
        return false;
    }

    /** If adding extra calls to this, consider whether explicit user permission is required, and whether
     *  privacy policy needs updating.
     *  Returns null if location not available.
     */
    @Override
    public Location getLocation() {
        return locationSupplier.getLocation();
    }

    /** If adding extra calls to this, consider whether explicit user permission is required, and whether
     *  privacy policy needs updating.
     *  Returns null if location not available.
     */
    public Location getLocation(LocationSupplier.LocationInfo locationInfo) {
        return locationSupplier.getLocation(locationInfo);
    }

    @Override
    public VideoMethod createOutputVideoMethod() {
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            Bundle myExtras = main_activity.getIntent().getExtras();
            if (myExtras != null) {
                Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
                if( intent_uri != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "save to: " + intent_uri);
                    return VideoMethod.URI;
                }
            }
            // if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
            if( MyDebug.LOG )
                Log.d(TAG, "intent uri not specified");
            if( MainActivity.useScopedStorage() ) {
                // can't use file method with scoped storage
                return VideoMethod.MEDIASTORE;
            }
            else {
                // note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
                return VideoMethod.FILE;
            }
        }
        else if( storageUtils.isUsingSAF() ) {
            return VideoMethod.SAF;
        }
        else if( MainActivity.useScopedStorage() ) {
            return VideoMethod.MEDIASTORE;
        }
        else {
            return VideoMethod.FILE;
        }
    }

    @Override
    public File createOutputVideoFile(String extension) throws IOException {
        last_video_file = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "", extension, new Date());
        return last_video_file;
    }

    @Override
    public Uri createOutputVideoSAF(String extension) throws IOException {
        last_video_file_uri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_VIDEO, "", extension, new Date());
        return last_video_file_uri;
    }

    @Override
    public Uri createOutputVideoMediaStore(String extension) throws IOException {
        Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ContentValues contentValues = new ContentValues();
        String filename = storageUtils.createMediaFilename(StorageUtils.MEDIA_TYPE_VIDEO, "", 0, "." + extension, new Date());
        if( MyDebug.LOG )
            Log.d(TAG, "filename: " + filename);
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        String mime_type = storageUtils.getVideoMimeType(extension);
        if( MyDebug.LOG )
            Log.d(TAG, "mime_type: " + mime_type);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mime_type);
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
            String relative_path = storageUtils.getSaveRelativeFolder();
            if( MyDebug.LOG )
                Log.d(TAG, "relative_path: " + relative_path);
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, relative_path);
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        last_video_file_uri = main_activity.getContentResolver().insert(folder, contentValues);
        if( MyDebug.LOG )
            Log.d(TAG, "uri: " + last_video_file_uri);
        if( last_video_file_uri == null ) {
            throw new IOException();
        }

        return last_video_file_uri;
    }

    @Override
    public Uri createOutputVideoUri() {
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            Bundle myExtras = main_activity.getIntent().getExtras();
            if (myExtras != null) {
                Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
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
        return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
    }

    @Override
    public String getFocusPref(boolean is_video) {
        if( getPhotoMode() == PhotoMode.FocusBracketing && !main_activity.getPreview().isVideo() ) {
            // alway run in manual focus mode for focus bracketing
            return "focus_mode_manual2";
        }
        return sharedPreferences.getString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), "");
    }

    int getFocusAssistPref() {
        String focus_assist_value = sharedPreferences.getString(PreferenceKeys.FocusAssistPreferenceKey, "0");
        int focus_assist;
        try {
            focus_assist = Integer.parseInt(focus_assist_value);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse focus_assist_value: " + focus_assist_value);
            e.printStackTrace();
            focus_assist = 0;
        }
        if( focus_assist > 0 && main_activity.getPreview().isVideoRecording() ) {
            // focus assist not currently supported while recording video - don't want to zoom the resultant video!
            focus_assist = 0;
        }
        return focus_assist;
    }

    @Override
    public boolean isVideoPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.IsVideoPreferenceKey, false);
    }

    @Override
    public String getSceneModePref() {
        return sharedPreferences.getString(PreferenceKeys.SceneModePreferenceKey, CameraController.SCENE_MODE_DEFAULT);
    }

    @Override
    public String getColorEffectPref() {
        return sharedPreferences.getString(PreferenceKeys.ColorEffectPreferenceKey, CameraController.COLOR_EFFECT_DEFAULT);
    }

    @Override
    public String getWhiteBalancePref() {
        return sharedPreferences.getString(PreferenceKeys.WhiteBalancePreferenceKey, CameraController.WHITE_BALANCE_DEFAULT);
    }

    @Override
    public int getWhiteBalanceTemperaturePref() {
        return sharedPreferences.getInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, 5000);
    }

    @Override
    public String getAntiBandingPref() {
        return sharedPreferences.getString(PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT);
    }

    @Override
    public String getEdgeModePref() {
        return sharedPreferences.getString(PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT);
    }

    @Override
    public String getCameraNoiseReductionModePref() {
        return sharedPreferences.getString(PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT);
    }

    @Override
    public String getISOPref() {
        return sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
    }

    @Override
    public int getExposureCompensationPref() {
        String value = sharedPreferences.getString(PreferenceKeys.ExposurePreferenceKey, "0");
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

    public static CameraController.Size choosePanoramaResolution(List<CameraController.Size> sizes) {
        // if we allow panorama with higher resolutions, review against memory requirements under MainActivity.supportsPanorama()
        // also may need to update the downscaling in the testing code
        final int max_width_c = 2080;
        boolean found = false;
        CameraController.Size best_size = null;
        // find largest width <= max_width_c with aspect ratio 4:3
        for(CameraController.Size size : sizes) {
            if( size.width <= max_width_c ) {
                double aspect_ratio = ((double)size.width) / (double)size.height;
                if( Math.abs(aspect_ratio - 4.0/3.0) < 1.0e-5 ) {
                    if( !found || size.width > best_size.width ) {
                        found = true;
                        best_size = size;
                    }
                }
            }
        }
        if( found ) {
            return best_size;
        }
        // else find largest width <= max_width_c
        for(CameraController.Size size : sizes) {
            if( size.width <= max_width_c ) {
                if( !found || size.width > best_size.width ) {
                    found = true;
                    best_size = size;
                }
            }
        }
        if( found ) {
            return best_size;
        }
        // else find smallest width
        for(CameraController.Size size : sizes) {
            if( !found || size.width < best_size.width ) {
                found = true;
                best_size = size;
            }
        }
        return best_size;
    }

    @Override
    public Pair<Integer, Integer> getCameraResolutionPref(CameraResolutionConstraints constraints) {
        PhotoMode photo_mode = getPhotoMode();
        if( photo_mode == PhotoMode.Panorama ) {
            CameraController.Size best_size = choosePanoramaResolution(main_activity.getPreview().getSupportedPictureSizes(false));
            return new Pair<>(best_size.width, best_size.height);
        }

        String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
        if( MyDebug.LOG )
            Log.d(TAG, "resolution_value: " + resolution_value);
        Pair<Integer, Integer> result = null;
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
                    result = new Pair<>(resolution_w, resolution_h);
                }
                catch(NumberFormatException exception) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
                }
            }
        }

        if( photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.HDR ) {
            // set a maximum resolution for modes that require decompressing multiple images for processing,
            // due to risk of running out of memory!
            constraints.has_max_mp = true;
            constraints.max_mp = 22000000; // max of 22MP
            //constraints.max_mp = 7800000; // test!
            if( main_activity.is_test && test_max_mp != 0 ) {
                constraints.max_mp = test_max_mp;
            }
        }

        return result;
    }

    /** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
     *  photo - in some cases, we may set that to a higher value, then perform processing on the
     *  resultant JPEG before resaving. This method returns the image quality setting to be used for
     *  saving the final image (as specified by the user).
     */
    private int getSaveImageQualityPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getSaveImageQualityPref");
        String image_quality_s = sharedPreferences.getString(PreferenceKeys.QualityPreferenceKey, "90");
        int image_quality;
        try {
            image_quality = Integer.parseInt(image_quality_s);
        }
        catch(NumberFormatException exception) {
            if( MyDebug.LOG )
                Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
            image_quality = 90;
        }
        if( isRawOnly() ) {
            // if raw only mode, we can set a lower quality for the JPEG, as it isn't going to be saved - only used for
            // the thumbnail and pause preview option
            if( MyDebug.LOG )
                Log.d(TAG, "set lower quality for raw_only mode");
            image_quality = Math.min(image_quality, 70);
        }
        return image_quality;
    }

    @Override
    public int getImageQualityPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getImageQualityPref");
        // see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
        // at 100% quality for post-processing, the final image will then be saved at the user requested
        // setting
        PhotoMode photo_mode = getPhotoMode();
        if( main_activity.getPreview().isVideo() )
            ; // for video photo snapshot mode, the photo modes for 100% quality won't be enabled
        else if( photo_mode == PhotoMode.DRO )
            return 100;
        else if( photo_mode == PhotoMode.HDR )
            return 100;
        else if( photo_mode == PhotoMode.NoiseReduction )
            return 100;

        if( getImageFormatPref() != ImageSaver.Request.ImageFormat.STD )
            return 100;

        return getSaveImageQualityPref();
    }

    @Override
    public boolean getFaceDetectionPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false);
    }

    /** Returns whether the current fps preference is one that requires a "high speed" video size/
     *  frame rate.
     */
    public boolean fpsIsHighSpeed() {
        return main_activity.getPreview().fpsIsHighSpeed(getVideoFPSPref());
    }

    @Override
    public String getVideoQualityPref() {
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            if( main_activity.getIntent().hasExtra(MediaStore.EXTRA_VIDEO_QUALITY) ) {
                int intent_quality = main_activity.getIntent().getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                if( MyDebug.LOG )
                    Log.d(TAG, "intent_quality: " + intent_quality);
                if( intent_quality == 0 || intent_quality == 1 ) {
                    List<String> video_quality = main_activity.getPreview().getVideoQualityHander().getSupportedVideoQuality();
                    if( intent_quality == 0 ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "return lowest quality");
                        // return lowest quality, video_quality is sorted high to low
                        return video_quality.get(video_quality.size()-1);
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "return highest quality");
                        // return highest quality, video_quality is sorted high to low
                        return video_quality.get(0);
                    }
                }
            }
        }

        // Conceivably, we might get in a state where the fps isn't supported at all (e.g., an upgrade changes the available
        // supported video resolutions/frame-rates).
        return sharedPreferences.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId, fpsIsHighSpeed()), "");
    }

    @Override
    public boolean getVideoStabilizationPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, false);
    }

    @Override
    public boolean getForce4KPref() {
        return cameraId == 0 && sharedPreferences.getBoolean(PreferenceKeys.ForceVideo4KPreferenceKey, false) && main_activity.supportsForceVideo4K();
    }

    @Override
    public String getRecordVideoOutputFormatPref() {
        return sharedPreferences.getString(PreferenceKeys.VideoFormatPreferenceKey, "preference_video_output_format_default");
    }

    @Override
    public String getVideoBitratePref() {
        return sharedPreferences.getString(PreferenceKeys.VideoBitratePreferenceKey, "default");
    }

    @Override
    public String getVideoFPSPref() {
        // if check for EXTRA_VIDEO_QUALITY, if set, best to fall back to default FPS - see corresponding code in getVideoQualityPref
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            if( main_activity.getIntent().hasExtra(MediaStore.EXTRA_VIDEO_QUALITY) ) {
                int intent_quality = main_activity.getIntent().getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                if (MyDebug.LOG)
                    Log.d(TAG, "intent_quality: " + intent_quality);
                if (intent_quality == 0 || intent_quality == 1) {
                    return "default";
                }
            }
        }

        float capture_rate_factor = getVideoCaptureRateFactor();
        if( capture_rate_factor < 1.0f-1.0e-5f ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set fps for slow motion, capture rate: " + capture_rate_factor);
            int preferred_fps = (int)(30.0/capture_rate_factor+0.5);
            if( MyDebug.LOG )
                Log.d(TAG, "preferred_fps: " + preferred_fps);
            if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(preferred_fps) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(preferred_fps) )
                return "" + preferred_fps;
            // just in case say we support 120fps but NOT 60fps, getSupportedSlowMotionRates() will have returned that 2x slow
            // motion is supported, but we need to set 120fps instead of 60fps
            while( preferred_fps < 240 ) {
                preferred_fps *= 2;
                if( MyDebug.LOG )
                    Log.d(TAG, "preferred_fps not supported, try: " + preferred_fps);
                if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(preferred_fps) ||
                        main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(preferred_fps) )
                    return "" + preferred_fps;
            }
            // shouln't happen based on getSupportedSlowMotionRates()
            Log.e(TAG, "can't find valid fps for slow motion");
            return "default";
        }
        return sharedPreferences.getString(PreferenceKeys.getVideoFPSPreferenceKey(cameraId), "default");
    }

    @Override
    public float getVideoCaptureRateFactor() {
        float capture_rate_factor = sharedPreferences.getFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(main_activity.getPreview().getCameraId()), 1.0f);
        if( MyDebug.LOG )
            Log.d(TAG, "capture_rate_factor: " + capture_rate_factor);
        if( Math.abs(capture_rate_factor - 1.0f) > 1.0e-5 ) {
            // check stored capture rate is valid
            if( MyDebug.LOG )
                Log.d(TAG, "check stored capture rate is valid");
            List<Float> supported_capture_rates = getSupportedVideoCaptureRates();
            if( MyDebug.LOG )
                Log.d(TAG, "supported_capture_rates: " + supported_capture_rates);
            boolean found = false;
            for(float this_capture_rate : supported_capture_rates) {
                if( Math.abs(capture_rate_factor - this_capture_rate) < 1.0e-5 ) {
                    found = true;
                    break;
                }
            }
            if( !found ) {
                Log.e(TAG, "stored capture_rate_factor: " + capture_rate_factor + " not supported");
                capture_rate_factor = 1.0f;
            }
        }
        return capture_rate_factor;
    }

    /** This will always return 1, even if slow motion isn't supported (i.e.,
     *  slow motion should only be considered as supported if at least 2 entries
     *  are returned. Entries are returned in increasing order.
     */
    public List<Float> getSupportedVideoCaptureRates() {
        List<Float> rates = new ArrayList<>();
        if( main_activity.getPreview().supportsVideoHighSpeed() ) {
            // We consider a slow motion rate supported if we can get at least 30fps in slow motion.
            // If this code is updated, see if we also need to update how slow motion fps is chosen
            // in getVideoFPSPref().
            if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(240) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(240) ) {
                rates.add(1.0f/8.0f);
                rates.add(1.0f/4.0f);
                rates.add(1.0f/2.0f);
            }
            else if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(120) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(120) ) {
                rates.add(1.0f/4.0f);
                rates.add(1.0f/2.0f);
            }
            else if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(60) ||
                    main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(60) ) {
                rates.add(1.0f/2.0f);
            }
        }
        rates.add(1.0f);
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            // add timelapse options
            // in theory this should work on any Android version, though video fails to record in timelapse mode on Galaxy Nexus...
            rates.add(2.0f);
            rates.add(3.0f);
            rates.add(4.0f);
            rates.add(5.0f);
            rates.add(10.0f);
            rates.add(20.0f);
            rates.add(30.0f);
            rates.add(60.0f);
            rates.add(120.0f);
            rates.add(240.0f);
        }
        return rates;
    }

    @Override
    public CameraController.TonemapProfile getVideoTonemapProfile() {
        String video_log = sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off");
        // only return TONEMAPPROFILE_LOG for values recognised by getVideoLogProfileStrength()
        switch( video_log ) {
            case "off":
                return CameraController.TonemapProfile.TONEMAPPROFILE_OFF;
            case "rec709":
                return CameraController.TonemapProfile.TONEMAPPROFILE_REC709;
            case "srgb":
                return CameraController.TonemapProfile.TONEMAPPROFILE_SRGB;
            case "fine":
            case "low":
            case "medium":
            case "strong":
            case "extra_strong":
                return CameraController.TonemapProfile.TONEMAPPROFILE_LOG;
            case "gamma":
                return CameraController.TonemapProfile.TONEMAPPROFILE_GAMMA;
            case "jtvideo":
                return CameraController.TonemapProfile.TONEMAPPROFILE_JTVIDEO;
            case "jtlog":
                return CameraController.TonemapProfile.TONEMAPPROFILE_JTLOG;
            case "jtlog2":
                return CameraController.TonemapProfile.TONEMAPPROFILE_JTLOG2;
        }
        return CameraController.TonemapProfile.TONEMAPPROFILE_OFF;
    }

    @Override
    public float getVideoLogProfileStrength() {
        String video_log = sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off");
        // remember to update getVideoTonemapProfile() if adding/changing modes
        switch( video_log ) {
            case "off":
            case "rec709":
            case "srgb":
            case "gamma":
            case "jtvideo":
            case "jtlog":
            case "jtlog2":
                return 0.0f;
            /*case "fine":
                return 1.0f;
            case "low":
                return 5.0f;
            case "medium":
                return 10.0f;
            case "strong":
                return 100.0f;
            case "extra_strong":
                return 500.0f;*/
            // need a range of values as behaviour can vary between devices - e.g., "fine" has more effect on Nexus 6 than
            // other devices such as OnePlus 3T or Galaxy S10e
            // recalibrated in v1.48 to correspond to improvements made in CameraController2
            case "fine":
                return 10.0f;
            case "low":
                return 32.0f;
            case "medium":
                return 100.0f;
            case "strong":
                return 224.0f;
            case "extra_strong":
                return 500.0f;
        }
        return 0.0f;
    }

    @Override
    public float getVideoProfileGamma() {
        String gamma_value = sharedPreferences.getString(PreferenceKeys.VideoProfileGammaPreferenceKey, "2.2");
        float gamma = 0.0f;
        try {
            gamma = Float.parseFloat(gamma_value);
            if( MyDebug.LOG )
                Log.d(TAG, "gamma: " + gamma);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse gamma value: " + gamma_value);
            e.printStackTrace();
        }
        return gamma;
    }

    @Override
    public long getVideoMaxDurationPref() {
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            if( main_activity.getIntent().hasExtra(MediaStore.EXTRA_DURATION_LIMIT) ) {
                int intent_duration_limit = main_activity.getIntent().getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
                if( MyDebug.LOG )
                    Log.d(TAG, "intent_duration_limit: " + intent_duration_limit);
                return intent_duration_limit * 1000;
            }
        }

        String video_max_duration_value = sharedPreferences.getString(PreferenceKeys.VideoMaxDurationPreferenceKey, "0");
        long video_max_duration;
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
        String restart_value = sharedPreferences.getString(PreferenceKeys.VideoRestartPreferenceKey, "0");
        int remaining_restart_video;
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

    long getVideoMaxFileSizeUserPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getVideoMaxFileSizeUserPref");

        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            if( main_activity.getIntent().hasExtra(MediaStore.EXTRA_SIZE_LIMIT) ) {
                long intent_size_limit = main_activity.getIntent().getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0);
                if( MyDebug.LOG )
                    Log.d(TAG, "intent_size_limit: " + intent_size_limit);
                return intent_size_limit;
            }
        }

        String video_max_filesize_value = sharedPreferences.getString(PreferenceKeys.VideoMaxFileSizePreferenceKey, "0");
        long video_max_filesize;
        try {
            video_max_filesize = Long.parseLong(video_max_filesize_value);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse preference_video_max_filesize value: " + video_max_filesize_value);
            e.printStackTrace();
            video_max_filesize = 0;
        }
        //video_max_filesize = 1024*1024; // test
        if( MyDebug.LOG )
            Log.d(TAG, "video_max_filesize: " + video_max_filesize);
        return video_max_filesize;
    }

    private boolean getVideoRestartMaxFileSizeUserPref() {
        if( isVideoCaptureIntent() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            if( main_activity.getIntent().hasExtra(MediaStore.EXTRA_SIZE_LIMIT) ) {
                // if called from a video capture intent that set a max file size, this will be expecting a single file with that maximum size
                return false;
            }
        }

        return sharedPreferences.getBoolean(PreferenceKeys.VideoRestartMaxFileSizePreferenceKey, true);
    }

    @Override
    public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
        if( MyDebug.LOG )
            Log.d(TAG, "getVideoMaxFileSizePref");
        VideoMaxFileSize video_max_filesize = new VideoMaxFileSize();
        video_max_filesize.max_filesize = getVideoMaxFileSizeUserPref();
        video_max_filesize.auto_restart = getVideoRestartMaxFileSizeUserPref();
		
		/* Try to set the max filesize so we don't run out of space.
		   If using SD card without storage access framework, it's not reliable to get the free storage
		   (see https://sourceforge.net/p/opencamera/tickets/153/ ).
		   If using Storage Access Framework, getting the available space seems to be reliable for
		   internal storage or external SD card.
		   */
        boolean set_max_filesize;
        if( storageUtils.isUsingSAF() ) {
            set_max_filesize = true;
        }
        else {
            String folder_name = storageUtils.getSaveLocation();
            if( MyDebug.LOG )
                Log.d(TAG, "saving to: " + folder_name);
            boolean is_internal = false;
            if( !StorageUtils.saveFolderIsFull(folder_name) ) {
                is_internal = true;
            }
            else {
                // If save folder path is a full path, see if it matches the "external" storage (which actually means "primary", which typically isn't an SD card these days).
                File storage = Environment.getExternalStorageDirectory();
                if( MyDebug.LOG )
                    Log.d(TAG, "compare to: " + storage.getAbsolutePath());
                if( folder_name.startsWith( storage.getAbsolutePath() ) )
                    is_internal = true;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "using internal storage?" + is_internal);
            set_max_filesize = is_internal;
        }
        if( set_max_filesize ) {
            if( MyDebug.LOG )
                Log.d(TAG, "try setting max filesize");
            long free_memory = storageUtils.freeMemory();
            if( free_memory >= 0 ) {
                free_memory = free_memory * 1024 * 1024;

                final long min_free_memory = 50000000; // how much free space to leave after video
                // min_free_filesize is the minimum value to set for max file size:
                //   - no point trying to create a really short video
                //   - too short videos can end up being corrupted
                //   - also with auto-restart, if this is too small we'll end up repeatedly restarting and creating shorter and shorter videos
                final long min_free_filesize = 20000000;
                long available_memory = free_memory - min_free_memory;
                if( test_set_available_memory ) {
                    available_memory = test_available_memory;
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "free_memory: " + free_memory);
                    Log.d(TAG, "available_memory: " + available_memory);
                }
                if( available_memory > min_free_filesize ) {
                    if( video_max_filesize.max_filesize == 0 || video_max_filesize.max_filesize > available_memory ) {
                        video_max_filesize.max_filesize = available_memory;
                        // still leave auto_restart set to true - because even if we set a max filesize for running out of storage, the video may still hit a maximum limit beforehand, if there's a device max limit set (typically ~2GB)
                        if( MyDebug.LOG )
                            Log.d(TAG, "set video_max_filesize to avoid running out of space: " + video_max_filesize);
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.e(TAG, "not enough free storage to record video");
                    throw new NoFreeStorageException();
                }
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "can't determine remaining free space");
            }
        }

        return video_max_filesize;
    }

    @Override
    public boolean getVideoFlashPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoFlashPreferenceKey, false);
    }

    @Override
    public boolean getVideoLowPowerCheckPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoLowPowerCheckPreferenceKey, true);
    }

    @Override
    public String getPreviewSizePref() {
        return sharedPreferences.getString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_wysiwyg");
    }

    @Override
    public String getPreviewRotationPref() {
        return sharedPreferences.getString(PreferenceKeys.RotatePreviewPreferenceKey, "0");
    }

    @Override
    public String getLockOrientationPref() {
        if( getPhotoMode() == PhotoMode.Panorama )
            return "portrait"; // for now panorama only supports portrait
        return sharedPreferences.getString(PreferenceKeys.LockOrientationPreferenceKey, "none");
    }

    @Override
    public boolean getTouchCapturePref() {
        String value = sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none");
        return value.equals("single");
    }

    @Override
    public boolean getDoubleTapCapturePref() {
        String value = sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none");
        return value.equals("double");
    }

    @Override
    public boolean getPausePreviewPref() {
        if( main_activity.getPreview().isVideoRecording() ) {
            // don't pause preview when taking photos while recording video!
            return false;
        }
        else if( main_activity.lastContinuousFastBurst() ) {
            // Don't use pause preview mode when doing a continuous fast burst
            // Firstly due to not using background thread for pause preview mode, this will be
            // sluggish anyway, but even when this is fixed, I'm not sure it makes sense to use
            // pause preview in this mode.
            return false;
        }
        else if( getPhotoMode() == PhotoMode.Panorama ) {
            // don't pause preview when taking photos for panorama mode
            return false;
        }
        return sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
    }

    @Override
    public boolean getShowToastsPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.ShowToastsPreferenceKey, true);
    }

    public boolean getThumbnailAnimationPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, true);
    }

    @Override
    public boolean getShutterSoundPref() {
        if( getPhotoMode() == PhotoMode.Panorama )
            return false;
        return sharedPreferences.getBoolean(PreferenceKeys.ShutterSoundPreferenceKey, true);
    }

    @Override
    public boolean getStartupFocusPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.StartupFocusPreferenceKey, true);
    }

    @Override
    public long getTimerPref() {
        if( getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama )
            return 0; // don't support timer with panorama
        String timer_value = sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0");
        long timer_delay;
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
        if( getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama )
            return "1"; // don't support repeat with panorama
        return sharedPreferences.getString(PreferenceKeys.RepeatModePreferenceKey, "1");
    }

    @Override
    public long getRepeatIntervalPref() {
        String timer_value = sharedPreferences.getString(PreferenceKeys.RepeatIntervalPreferenceKey, "0");
        long timer_delay;
        try {
            float timer_delay_s = Float.parseFloat(timer_value);
            if( MyDebug.LOG )
                Log.d(TAG, "timer_delay_s: " + timer_delay_s);
            timer_delay = (long)(timer_delay_s * 1000);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse repeat interval value: " + timer_value);
            e.printStackTrace();
            timer_delay = 0;
        }
        return timer_delay;
    }

    @Override
    public boolean getGeotaggingPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.LocationPreferenceKey, false);
    }

    @Override
    public boolean getRequireLocationPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.RequireLocationPreferenceKey, false);
    }

    boolean getGeodirectionPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.GPSDirectionPreferenceKey, false);
    }

    @Override
    public boolean getRecordAudioPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.RecordAudioPreferenceKey, true);
    }

    @Override
    public String getRecordAudioChannelsPref() {
        return sharedPreferences.getString(PreferenceKeys.RecordAudioChannelsPreferenceKey, "audio_default");
    }

    @Override
    public String getRecordAudioSourcePref() {
        return sharedPreferences.getString(PreferenceKeys.RecordAudioSourcePreferenceKey, "audio_src_camcorder");
    }

    public boolean getAutoStabilisePref() {
        boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false);
        return auto_stabilise && main_activity.supportsAutoStabilise();
    }

    /** Returns the alpha value to use for ghost image, as a number from 0 to 255.
     *  Note that we store the preference as a percentage from 0 to 100, but scale this to 0 to 255.
     */
    public int getGhostImageAlpha() {
        String ghost_image_alpha_value = sharedPreferences.getString(PreferenceKeys.GhostImageAlphaPreferenceKey, "50");
        int ghost_image_alpha;
        try {
            ghost_image_alpha = Integer.parseInt(ghost_image_alpha_value);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse ghost_image_alpha_value: " + ghost_image_alpha_value);
            e.printStackTrace();
            ghost_image_alpha = 50;
        }
        ghost_image_alpha = (int)(ghost_image_alpha*2.55f+0.1f);
        return ghost_image_alpha;
    }

    public String getStampPref() {
        return sharedPreferences.getString(PreferenceKeys.StampPreferenceKey, "preference_stamp_no");
    }

    private String getStampDateFormatPref() {
        return sharedPreferences.getString(PreferenceKeys.StampDateFormatPreferenceKey, "preference_stamp_dateformat_default");
    }

    private String getStampTimeFormatPref() {
        return sharedPreferences.getString(PreferenceKeys.StampTimeFormatPreferenceKey, "preference_stamp_timeformat_default");
    }

    private String getStampGPSFormatPref() {
        return sharedPreferences.getString(PreferenceKeys.StampGPSFormatPreferenceKey, "preference_stamp_gpsformat_default");
    }

    private String getStampGeoAddressPref() {
        return sharedPreferences.getString(PreferenceKeys.StampGeoAddressPreferenceKey, "preference_stamp_geo_address_no");
    }

    private String getUnitsDistancePref() {
        return sharedPreferences.getString(PreferenceKeys.UnitsDistancePreferenceKey, "preference_units_distance_m");
    }

    public String getTextStampPref() {
        return sharedPreferences.getString(PreferenceKeys.TextStampPreferenceKey, "");
    }

    private int getTextStampFontSizePref() {
        int font_size = 12;
        String value = sharedPreferences.getString(PreferenceKeys.StampFontSizePreferenceKey, "12");
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

    private String getVideoSubtitlePref() {
        return sharedPreferences.getString(PreferenceKeys.VideoSubtitlePref, "preference_video_subtitle_no");
    }

    @Override
    public int getZoomPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getZoomPref: " + zoom_factor);
        return zoom_factor;
    }

    @Override
    public double getCalibratedLevelAngle() {
        return sharedPreferences.getFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
    }

    @Override
    public boolean canTakeNewPhoto() {
        if( MyDebug.LOG )
            Log.d(TAG, "canTakeNewPhoto");

        int n_raw, n_jpegs;
        if( main_activity.getPreview().isVideo() ) {
            // video snapshot mode
            n_raw = 0;
            n_jpegs = 1;
        }
        else {
            n_jpegs = 1; // default

            if( main_activity.getPreview().supportsExpoBracketing() && this.isExpoBracketingPref() ) {
                n_jpegs = this.getExpoBracketingNImagesPref();
            }
            else if( main_activity.getPreview().supportsFocusBracketing() && this.isFocusBracketingPref() ) {
                // focus bracketing mode always avoids blocking the image queue, no matter how many images are being taken
                // so all that matters is that we can take at least 1 photo (for the first shot)
                //n_jpegs = this.getFocusBracketingNImagesPref();
                n_jpegs = 1;
            }
            else if( main_activity.getPreview().supportsBurst() && this.isCameraBurstPref() ) {
                if( this.getBurstForNoiseReduction() ) {
                    if( this.getNRModePref() == ApplicationInterface.NRModePref.NRMODE_LOW_LIGHT ) {
                        n_jpegs = CameraController.N_IMAGES_NR_DARK_LOW_LIGHT;
                    }
                    else {
                        n_jpegs = CameraController.N_IMAGES_NR_DARK;
                    }
                }
                else {
                    n_jpegs = this.getBurstNImages();
                }
            }

            if( main_activity.getPreview().supportsRaw() && this.getRawPref() == RawPref.RAWPREF_JPEG_DNG ) {
                // note, even in RAW only mode, the CameraController will still take JPEG+RAW (we still need to JPEG to
                // generate a bitmap from for thumbnail and pause preview option), so this still generates a request in
                // the ImageSaver
                n_raw = n_jpegs;
            }
            else {
                n_raw = 0;
            }
        }

        int photo_cost = imageSaver.computePhotoCost(n_raw, n_jpegs);
        if( imageSaver.queueWouldBlock(photo_cost) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "canTakeNewPhoto: no, as queue would block");
            return false;
        }

        // even if the queue isn't full, we may apply additional limits
        int n_images_to_save = imageSaver.getNImagesToSave();
        PhotoMode photo_mode = getPhotoMode();
        if( photo_mode == PhotoMode.FastBurst || photo_mode == PhotoMode.Panorama ) {
            // only allow one fast burst at a time, so require queue to be empty
            if( n_images_to_save > 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "canTakeNewPhoto: no, as too many for fast burst");
                return false;
            }
        }
        if( photo_mode == PhotoMode.NoiseReduction ) {
            // allow a max of 2 photos in memory when at max of 8 images
            if( n_images_to_save >= 2*photo_cost ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "canTakeNewPhoto: no, as too many for nr");
                return false;
            }
        }
        if( n_jpegs > 1 ) {
            // if in any other kind of burst mode (e.g., expo burst, HDR), allow a max of 3 photos in memory
            if( n_images_to_save >= 3*photo_cost ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "canTakeNewPhoto: no, as too many for burst");
                return false;
            }
        }
        if( n_raw > 0 ) {
            // if RAW mode, allow a max of 3 photos
            if( n_images_to_save >= 3*photo_cost ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "canTakeNewPhoto: no, as too many for raw");
                return false;
            }
        }
        // otherwise, still have a max limit of 5 photos
        if( n_images_to_save >= 5*photo_cost ) {
            if( main_activity.supportsNoiseReduction() && n_images_to_save <= 8 ) {
                // if we take a photo in NR mode, then switch to std mode, it doesn't make sense to suddenly block!
                // so need to at least allow a new photo, if the number of photos is less than 1 NR photo
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "canTakeNewPhoto: no, as too many for regular");
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean imageQueueWouldBlock(int n_raw, int n_jpegs) {
        if( MyDebug.LOG )
            Log.d(TAG, "imageQueueWouldBlock");
        return imageSaver.queueWouldBlock(n_raw, n_jpegs);
    }

    @Override
    public long getExposureTimePref() {
        return sharedPreferences.getLong(PreferenceKeys.ExposureTimePreferenceKey, CameraController.EXPOSURE_TIME_DEFAULT);
    }

    @Override
    public float getFocusDistancePref(boolean is_target_distance) {
        return sharedPreferences.getFloat(is_target_distance ? PreferenceKeys.FocusBracketingTargetDistancePreferenceKey : PreferenceKeys.FocusDistancePreferenceKey, 0.0f);
    }

    @Override
    public boolean isExpoBracketingPref() {
        PhotoMode photo_mode = getPhotoMode();
        return photo_mode == PhotoMode.HDR || photo_mode == PhotoMode.ExpoBracketing;
    }

    @Override
    public boolean isFocusBracketingPref() {
        PhotoMode photo_mode = getPhotoMode();
        return photo_mode == PhotoMode.FocusBracketing;
    }

    @Override
    public boolean isCameraBurstPref() {
        PhotoMode photo_mode = getPhotoMode();
        return photo_mode == PhotoMode.FastBurst || photo_mode == PhotoMode.NoiseReduction;
    }

    @Override
    public int getBurstNImages() {
        PhotoMode photo_mode = getPhotoMode();
        if( photo_mode == PhotoMode.FastBurst ) {
            String n_images_value = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
            int n_images;
            try {
                n_images = Integer.parseInt(n_images_value);
            }
            catch(NumberFormatException e) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to parse FastBurstNImagesPreferenceKey value: " + n_images_value);
                e.printStackTrace();
                n_images = 5;
            }
            return n_images;
        }
        return 1;
    }

    @Override
    public boolean getBurstForNoiseReduction() {
        PhotoMode photo_mode = getPhotoMode();
        return photo_mode == PhotoMode.NoiseReduction;
    }

    public void setNRMode(String nr_mode) {
        this.nr_mode = nr_mode;
    }

    public String getNRMode() {
		/*if( MyDebug.LOG )
			Log.d(TAG, "nr_mode: " + nr_mode);*/
        return nr_mode;
    }

    @Override
    public NRModePref getNRModePref() {
		/*if( MyDebug.LOG )
			Log.d(TAG, "nr_mode: " + nr_mode);*/
        //noinspection SwitchStatementWithTooFewBranches
        switch( nr_mode ) {
            case "preference_nr_mode_low_light":
                return NRModePref.NRMODE_LOW_LIGHT;
        }
        return NRModePref.NRMODE_NORMAL;
    }

    public void setAperture(float aperture) {
        this.aperture = aperture;
    }

    @Override
    public float getAperturePref() {
        return aperture;
    }

    @Override
    public int getExpoBracketingNImagesPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getExpoBracketingNImagesPref");
        int n_images;
        PhotoMode photo_mode = getPhotoMode();
        if( photo_mode == PhotoMode.HDR ) {
            // always set 3 images for HDR
            n_images = 3;
        }
        else {
            String n_images_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3");
            try {
                n_images = Integer.parseInt(n_images_s);
            }
            catch(NumberFormatException exception) {
                if( MyDebug.LOG )
                    Log.e(TAG, "n_images_s invalid format: " + n_images_s);
                n_images = 3;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "n_images = " + n_images);
        return n_images;
    }

    @Override
    public double getExpoBracketingStopsPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getExpoBracketingStopsPref");
        double n_stops;
        PhotoMode photo_mode = getPhotoMode();
        if( photo_mode == PhotoMode.HDR ) {
            // always set 2 stops for HDR
            n_stops = 2.0;
        }
        else {
            String n_stops_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingStopsPreferenceKey, "2");
            try {
                n_stops = Double.parseDouble(n_stops_s);
            }
            catch(NumberFormatException exception) {
                if( MyDebug.LOG )
                    Log.e(TAG, "n_stops_s invalid format: " + n_stops_s);
                n_stops = 2.0;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "n_stops = " + n_stops);
        return n_stops;
    }

    @Override
    public int getFocusBracketingNImagesPref() {
        if( MyDebug.LOG )
            Log.d(TAG, "getFocusBracketingNImagesPref");
        int n_images;
        String n_images_s = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
        try {
            n_images = Integer.parseInt(n_images_s);
        }
        catch(NumberFormatException exception) {
            if( MyDebug.LOG )
                Log.e(TAG, "n_images_s invalid format: " + n_images_s);
            n_images = 3;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "n_images = " + n_images);
        return n_images;
    }

    @Override
    public boolean getFocusBracketingAddInfinityPref() {
        return sharedPreferences.getBoolean(PreferenceKeys.FocusBracketingAddInfinityPreferenceKey, false);
    }

    /** Returns the current photo mode.
     *  Note, this always should return the true photo mode - if we're in video mode and taking a photo snapshot while
     *  video recording, the caller should override. We don't override here, as this preference may be used to affect how
     *  the CameraController is set up, and we don't always re-setup the camera when switching between photo and video modes.
     */
    public PhotoMode getPhotoMode() {
        String photo_mode_pref = sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
		/*if( MyDebug.LOG )
			Log.d(TAG, "photo_mode_pref: " + photo_mode_pref);*/
        boolean dro = photo_mode_pref.equals("preference_photo_mode_dro");
        if( dro && main_activity.supportsDRO() )
            return PhotoMode.DRO;
        boolean hdr = photo_mode_pref.equals("preference_photo_mode_hdr");
        if( hdr && main_activity.supportsHDR() )
            return PhotoMode.HDR;
        boolean expo_bracketing = photo_mode_pref.equals("preference_photo_mode_expo_bracketing");
        if( expo_bracketing && main_activity.supportsExpoBracketing() )
            return PhotoMode.ExpoBracketing;
        boolean focus_bracketing = photo_mode_pref.equals("preference_photo_mode_focus_bracketing");
        if( focus_bracketing && main_activity.supportsFocusBracketing() )
            return PhotoMode.FocusBracketing;
        boolean fast_burst = photo_mode_pref.equals("preference_photo_mode_fast_burst");
        if( fast_burst && main_activity.supportsFastBurst() )
            return PhotoMode.FastBurst;
        boolean noise_reduction = photo_mode_pref.equals("preference_photo_mode_noise_reduction");
        if( noise_reduction && main_activity.supportsNoiseReduction() )
            return PhotoMode.NoiseReduction;
        boolean panorama = photo_mode_pref.equals("preference_photo_mode_panorama");
        if( panorama && !main_activity.getPreview().isVideo() && main_activity.supportsPanorama() )
            return PhotoMode.Panorama;
        return PhotoMode.Standard;
    }

    @Override
    public boolean getOptimiseAEForDROPref() {
        PhotoMode photo_mode = getPhotoMode();
        return( photo_mode == PhotoMode.DRO );
    }

    private ImageSaver.Request.ImageFormat getImageFormatPref() {
        switch( sharedPreferences.getString(PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg") ) {
            case "preference_image_format_webp":
                return ImageSaver.Request.ImageFormat.WEBP;
            case "preference_image_format_png":
                return ImageSaver.Request.ImageFormat.PNG;
            default:
                return ImageSaver.Request.ImageFormat.STD;
        }
    }

    /** Returns whether RAW is currently allowed, even if RAW is enabled in the preference (RAW
     *  isn't allowed for some photo modes, or in video mode, or when called from an intent).
     *  Note that this doesn't check whether RAW is supported by the camera.
     */
    public boolean isRawAllowed(PhotoMode photo_mode) {
        if( isImageCaptureIntent() )
            return false;
        if( main_activity.getPreview().isVideo() )
            return false; // video snapshot mode
        //return photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.DRO;
        if( photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.DRO ) {
            return true;
        }
        else if( photo_mode == PhotoMode.ExpoBracketing ) {
            return sharedPreferences.getBoolean(PreferenceKeys.AllowRawForExpoBracketingPreferenceKey, true) &&
                    main_activity.supportsBurstRaw();
        }
        else if( photo_mode == PhotoMode.HDR ) {
            // for HDR, RAW is only relevant if we're going to be saving the base expo images (otherwise there's nothing to save)
            return sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false) &&
                    sharedPreferences.getBoolean(PreferenceKeys.AllowRawForExpoBracketingPreferenceKey, true) &&
                    main_activity.supportsBurstRaw();
        }
        else if( photo_mode == PhotoMode.FocusBracketing ) {
            return sharedPreferences.getBoolean(PreferenceKeys.AllowRawForFocusBracketingPreferenceKey, true) &&
                    main_activity.supportsBurstRaw();
        }
        // not supported for panorama mode
        return false;
    }

    /** Return whether to capture JPEG, or RAW+JPEG.
     *  Note even if in RAW only mode, we still capture RAW+JPEG - the JPEG is needed for things like
     *  getting the bitmap for the thumbnail and pause preview option; we simply don't do any post-
     *  processing or saving on the JPEG.
     */
    @Override
    public RawPref getRawPref() {
        PhotoMode photo_mode = getPhotoMode();
        if( isRawAllowed(photo_mode) ) {
            switch( sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no") ) {
                case "preference_raw_yes":
                case "preference_raw_only":
                    return RawPref.RAWPREF_JPEG_DNG;
            }
        }
        return RawPref.RAWPREF_JPEG_ONLY;
    }

    /** Whether RAW only mode is enabled.
     */
    public boolean isRawOnly() {
        PhotoMode photo_mode = getPhotoMode();
        return isRawOnly(photo_mode);
    }

    /** Use this instead of isRawOnly() if the photo mode is already known - useful to call e.g. from MainActivity.supportsDRO()
     *  without causing an infinite loop!
     */
    boolean isRawOnly(PhotoMode photo_mode) {
        if( isRawAllowed(photo_mode) ) {
            //noinspection SwitchStatementWithTooFewBranches
            switch( sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no") ) {
                case "preference_raw_only":
                    return true;
            }
        }
        return false;
    }

    @Override
    public int getMaxRawImages() {
        return imageSaver.getMaxDNG();
    }

    @Override
    public boolean useCamera2FakeFlash() {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, false);
    }

    @Override
    public boolean useCamera2FastBurst() {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, true);
    }

    @Override
    public boolean usePhotoVideoRecording() {
        // we only show the preference for Camera2 API (since there's no point disabling the feature for old API)
        if( !useCamera2() )
            return true;
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2PhotoVideoRecordingPreferenceKey, true);
    }

    @Override
    public boolean isPreviewInBackground() {
        return main_activity.isCameraInBackground();
    }

    @Override
    public boolean allowZoom() {
        if( getPhotoMode() == PhotoMode.Panorama ) {
            // don't allow zooming in panorama mode, the algorithm isn't set up to support this!
            return false;
        }
        return true;
    }

    @Override
    public boolean isTestAlwaysFocus() {
        if( MyDebug.LOG ) {
            Log.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test);
        }
        return main_activity.is_test;
    }

    @Override
    public void cameraSetup() {
        main_activity.cameraSetup();
        drawPreview.clearContinuousFocusMove();
        // Need to cause drawPreview.updateSettings(), otherwise icons like HDR won't show after force-restart, because we only
        // know that HDR is supported after the camera is opened
        // Also needed for settings which update when switching between photo and video mode.
        drawPreview.updateSettings();
    }

    @Override
    public void onContinuousFocusMove(boolean start) {
        if( MyDebug.LOG )
            Log.d(TAG, "onContinuousFocusMove: " + start);
        drawPreview.onContinuousFocusMove(start);
    }

    void startPanorama() {
        if( MyDebug.LOG )
            Log.d(TAG, "startPanorama");
        gyroSensor.startRecording();
        n_panorama_pics = 0;
        panorama_pic_accepted = false;
        panorama_dir_left_to_right = true;

        main_activity.getMainUI().setTakePhotoIcon();
        View cancelPanoramaButton = main_activity.findViewById(R.id.cancel_panorama);
        cancelPanoramaButton.setVisibility(View.VISIBLE);
        main_activity.getMainUI().closeExposureUI(); // close seekbars if open (popup is already closed when taking a photo)
        // taking the photo will end up calling MainUI.showGUI(), which will hide the other on-screen icons
    }

    /** Ends panorama and submits the panoramic images to be processed.
     */
    void finishPanorama() {
        if( MyDebug.LOG )
            Log.d(TAG, "finishPanorama");

        imageSaver.getImageBatchRequest().panorama_dir_left_to_right = this.panorama_dir_left_to_right;

        stopPanorama(false);

        boolean image_capture_intent = isImageCaptureIntent();
        boolean do_in_background = saveInBackground(image_capture_intent);
        imageSaver.finishImageBatch(do_in_background);
    }

    /** Stop the panorama recording. Does nothing if panorama isn't currently recording.
     * @param is_cancelled Whether the panorama has been cancelled.
     */
    void stopPanorama(boolean is_cancelled) {
        if( MyDebug.LOG )
            Log.d(TAG, "stopPanorama");
        if( !gyroSensor.isRecording() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "...nothing to stop");
            return;
        }
        gyroSensor.stopRecording();
        clearPanoramaPoint();
        if( is_cancelled ) {
            imageSaver.flushImageBatch();
        }
        main_activity.getMainUI().setTakePhotoIcon();
        View cancelPanoramaButton = main_activity.findViewById(R.id.cancel_panorama);
        cancelPanoramaButton.setVisibility(View.GONE);
        main_activity.getMainUI().showGUI(); // refresh UI icons now that we've stopped panorama
    }

    private void setNextPanoramaPoint(boolean repeat) {
        if( MyDebug.LOG )
            Log.d(TAG, "setNextPanoramaPoint");
        float camera_angle_y = main_activity.getPreview().getViewAngleY(false);
        if( !repeat )
            n_panorama_pics++;
        if( MyDebug.LOG )
            Log.d(TAG, "n_panorama_pics is now: " + n_panorama_pics);
        if( n_panorama_pics == max_panorama_pics_c ) {
            if( MyDebug.LOG )
                Log.d(TAG, "reached max panorama limit");
            finishPanorama();
            return;
        }
        float angle = (float) Math.toRadians(camera_angle_y) * n_panorama_pics;
        if( n_panorama_pics > 1 && !panorama_dir_left_to_right ) {
            angle = - angle; // for right-to-left
        }
        float x = (float) Math.sin(angle / panorama_pics_per_screen);
        float z = (float) -Math.cos(angle / panorama_pics_per_screen);
        setNextPanoramaPoint(x, 0.0f, z);

        if( n_panorama_pics == 1 ) {
            // also set target for right-to-left
            angle = - angle;
            x = (float) Math.sin(angle / panorama_pics_per_screen);
            z = (float) -Math.cos(angle / panorama_pics_per_screen);
            gyroSensor.addTarget(x, 0.0f, z);
            drawPreview.addGyroDirectionMarker(x, 0.0f, z);
        }
    }

    private void setNextPanoramaPoint(float x, float y, float z) {
        if( MyDebug.LOG )
            Log.d(TAG, "setNextPanoramaPoint : " + x + " , " + y + " , " + z);

        final float target_angle = 1.0f * 0.01745329252f;
        //final float target_angle = 0.5f * 0.01745329252f;
        final float upright_angle_tol = 2.0f * 0.017452406437f;
        //final float upright_angle_tol = 1.0f * 0.017452406437f;
        final float too_far_angle = 45.0f * 0.01745329252f;
        gyroSensor.setTarget(x, y, z, target_angle, upright_angle_tol, too_far_angle, new GyroSensor.TargetCallback() {
            @Override
            public void onAchieved(int indx) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "TargetCallback.onAchieved: " + indx);
                    Log.d(TAG, "    n_panorama_pics: " + n_panorama_pics);
                }
                // Disable the target callback so we avoid risk of multiple callbacks - but note we don't call
                // clearPanoramaPoint(), as we don't want to call drawPreview.clearGyroDirectionMarker()
                // at this stage (looks better to keep showing the target market on-screen whilst photo
                // is being taken, user more likely to keep the device still).
                // Also we still keep the target active (and don't call clearTarget() so we can monitor if
                // the target is still achieved or not (for panorama_pic_accepted).
                //gyroSensor.clearTarget();
                gyroSensor.disableTargetCallback();
                if( n_panorama_pics == 1 ) {
                    panorama_dir_left_to_right = indx == 0;
                    if( MyDebug.LOG )
                        Log.d(TAG, "set panorama_dir_left_to_right to " + panorama_dir_left_to_right);
                }
                main_activity.takePicturePressed(false, false);
            }

            @Override
            public void onTooFar() {
                if( MyDebug.LOG )
                    Log.d(TAG, "TargetCallback.onTooFar");

                if( !main_activity.is_test ) {
                    main_activity.getPreview().showToast(null, R.string.panorama_cancelled);
                    MyApplicationInterface.this.stopPanorama(true);
                }
            }

        });
        drawPreview.setGyroDirectionMarker(x, y, z);
    }

    private void clearPanoramaPoint() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearPanoramaPoint");
        gyroSensor.clearTarget();
        drawPreview.clearGyroDirectionMarker();
    }

    static float getPanoramaPicsPerScreen() {
        return panorama_pics_per_screen;
    }

    @Override
    public void touchEvent(MotionEvent event) {
        main_activity.getMainUI().closeExposureUI();
        main_activity.getMainUI().closePopup();
        if( main_activity.usingKitKatImmersiveMode() ) {
            main_activity.setImmersiveMode(false);
        }
    }

    @Override
    public void startingVideo() {
        if( sharedPreferences.getBoolean(PreferenceKeys.LockVideoPreferenceKey, false) ) {
            main_activity.lockScreen();
        }
        main_activity.stopAudioListeners(); // important otherwise MediaRecorder will fail to start() if we have an audiolistener! Also don't want to have the speech recognizer going off
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.take_video_recording);
        view.setContentDescription( getContext().getResources().getString(R.string.stop_video) );
        view.setTag(R.drawable.take_video_recording); // for testing
        main_activity.getMainUI().destroyPopup(); // as the available popup options change while recording video
    }

    @Override
    public void startedVideo() {
        if( MyDebug.LOG )
            Log.d(TAG, "startedVideo()");
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
            if( !( main_activity.getMainUI().inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything() ) ) {
                View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
                pauseVideoButton.setVisibility(View.VISIBLE);
            }
            main_activity.getMainUI().setPauseVideoContentDescription();
        }
        if( main_activity.getPreview().supportsPhotoVideoRecording() && this.usePhotoVideoRecording() ) {
            if( !( main_activity.getMainUI().inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything() ) ) {
                View takePhotoVideoButton = main_activity.findViewById(R.id.take_photo_when_video_recording);
                takePhotoVideoButton.setVisibility(View.VISIBLE);
            }
        }
        if( main_activity.getMainUI().isExposureUIOpen() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to update exposure UI for start video recording");
            // need to update the exposure UI when starting/stopping video recording, to remove/add
            // ability to switch between auto and manual
            main_activity.getMainUI().setupExposureUI();
        }
        final VideoMethod video_method = this.createOutputVideoMethod();
        boolean dategeo_subtitles = getVideoSubtitlePref().equals("preference_video_subtitle_yes");
        if( dategeo_subtitles && video_method != ApplicationInterface.VideoMethod.URI ) {
            final String preference_stamp_dateformat = this.getStampDateFormatPref();
            final String preference_stamp_timeformat = this.getStampTimeFormatPref();
            final String preference_stamp_gpsformat = this.getStampGPSFormatPref();
            final String preference_units_distance = this.getUnitsDistancePref();
            final String preference_stamp_geo_address = this.getStampGeoAddressPref();
            final boolean store_location = getGeotaggingPref();
            final boolean store_geo_direction = getGeodirectionPref();
            class SubtitleVideoTimerTask extends TimerTask {
                // need to keep a reference to pfd_saf for as long as writer, to avoid getting garbage collected - see https://sourceforge.net/p/opencamera/tickets/417/
                private ParcelFileDescriptor pfd_saf;
                private OutputStreamWriter writer;
                private Uri uri;
                private int count = 1;
                private long min_video_time_from = 0;

                private String getSubtitleFilename(String video_filename) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "getSubtitleFilename");
                    int indx = video_filename.indexOf('.');
                    if( indx != -1 ) {
                        video_filename = video_filename.substring(0, indx);
                    }
                    video_filename = video_filename + ".srt";
                    if( MyDebug.LOG )
                        Log.d(TAG, "return filename: " + video_filename);
                    return video_filename;
                }

                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "SubtitleVideoTimerTask run");
                    long video_time = main_activity.getPreview().getVideoTime();
                    if( !main_activity.getPreview().isVideoRecording() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "no longer video recording");
                        return;
                    }
                    if( main_activity.getPreview().isVideoRecordingPaused() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "video recording is paused");
                        return;
                    }
                    Date current_date = new Date();
                    Calendar current_calendar = Calendar.getInstance();
                    int offset_ms = current_calendar.get(Calendar.MILLISECOND);
                    // We subtract an offset, because if the current time is say 00:00:03.425 and the video has been recording for
                    // 1s, we instead need to record the video time when it became 00:00:03.000. This does mean that the GPS
                    // location is going to be off by up to 1s, but that should be less noticeable than the clock being off.
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "count: " + count);
                        Log.d(TAG, "offset_ms: " + offset_ms);
                        Log.d(TAG, "video_time: " + video_time);
                    }
                    String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, current_date);
                    String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, current_date);
                    Location location = store_location ? getLocation() : null;
                    double geo_direction = store_geo_direction && main_activity.getPreview().hasGeoDirection() ? main_activity.getPreview().getGeoDirection() : 0.0;
                    String gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, preference_units_distance, store_location && location!=null, location, store_geo_direction && main_activity.getPreview().hasGeoDirection(), geo_direction);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "date_stamp: " + date_stamp);
                        Log.d(TAG, "time_stamp: " + time_stamp);
                        // don't log gps_stamp, in case of privacy!
                    }

                    String datetime_stamp = "";
                    if( date_stamp.length() > 0 )
                        datetime_stamp += date_stamp;
                    if( time_stamp.length() > 0 ) {
                        if( datetime_stamp.length() > 0 )
                            datetime_stamp += " ";
                        datetime_stamp += time_stamp;
                    }

                    // build subtitles
                    StringBuilder subtitles = new StringBuilder();
                    if( datetime_stamp.length() > 0 )
                        subtitles.append(datetime_stamp).append("\n");

                    if( gps_stamp.length() > 0 ) {
                        Address address = null;
                        if( store_location && !preference_stamp_geo_address.equals("preference_stamp_geo_address_no") ) {
                            // try to find an address
                            if( main_activity.isAppPaused() ) {
                                // seems safer to not try to initiate potential network connections (via geocoder) if Open Camera
                                // is paused - this shouldn't happen, since we stop video when paused, but just to be safe
                                if( MyDebug.LOG )
                                    Log.d(TAG, "don't call geocoder for video subtitles  as app is paused?!");
                            }
                            else if( Geocoder.isPresent() ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder is present");
                                Geocoder geocoder = new Geocoder(main_activity, Locale.getDefault());
                                try {
                                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                                    if( addresses != null && addresses.size() > 0 ) {
                                        address = addresses.get(0);
                                        // don't log address, in case of privacy!
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "max line index: " + address.getMaxAddressLineIndex());
                                        }
                                    }
                                }
                                catch(Exception e) {
                                    Log.e(TAG, "failed to read from geocoder");
                                    e.printStackTrace();
                                }
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder not present");
                            }
                        }

                        if( address != null ) {
                            for(int i=0;i<=address.getMaxAddressLineIndex();i++) {
                                // write in forward order
                                String addressLine = address.getAddressLine(i);
                                subtitles.append(addressLine).append("\n");
                            }
                        }

                        if( address == null || preference_stamp_geo_address.equals("preference_stamp_geo_address_both") ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "display gps coords");
                            subtitles.append(gps_stamp).append("\n");
                        }
                        else if( store_geo_direction ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "not displaying gps coords, but need to display geo direction");
                            gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, preference_units_distance, false, null, store_geo_direction && main_activity.getPreview().hasGeoDirection(), geo_direction);
                            if( gps_stamp.length() > 0 ) {
                                // don't log gps_stamp, in case of privacy!
                                subtitles.append(gps_stamp).append("\n");
                            }
                        }
                    }

                    if( subtitles.length() == 0 ) {
                        return;
                    }
                    long video_time_from = video_time - offset_ms;
                    long video_time_to = video_time_from + 999;
                    // don't want to start from before 0; also need to keep track of min_video_time_from to avoid bug reported at
                    // https://forum.xda-developers.com/showpost.php?p=74827802&postcount=345 for pause video where we ended up
                    // with overlapping times when resuming
                    if( video_time_from < min_video_time_from )
                        video_time_from = min_video_time_from;
                    min_video_time_from = video_time_to + 1;
                    String subtitle_time_from = TextFormatter.formatTimeMS(video_time_from);
                    String subtitle_time_to = TextFormatter.formatTimeMS(video_time_to);
                    try {
                        synchronized( this ) {
                            if( writer == null ) {
                                if( video_method == VideoMethod.FILE ) {
                                    String subtitle_filename = last_video_file.getAbsolutePath();
                                    subtitle_filename = getSubtitleFilename(subtitle_filename);
                                    writer = new FileWriter(subtitle_filename);
                                }
                                else if( video_method == VideoMethod.SAF || video_method == VideoMethod.MEDIASTORE ) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "last_video_file_uri: " + last_video_file_uri);
                                    String subtitle_filename = storageUtils.getFileName(last_video_file_uri);
                                    subtitle_filename = getSubtitleFilename(subtitle_filename);
                                    if( video_method == VideoMethod.SAF ) {
                                        uri = storageUtils.createOutputFileSAF(subtitle_filename, ""); // don't set a mimetype, as we don't want it to append a new extension
                                    }
                                    else {
                                        Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                                                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                                        ContentValues contentValues = new ContentValues();
                                        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, subtitle_filename);
                                        // set mime type - it's unclear if .SRT files have an official mime type, but (a) we must set a mime type otherwise
                                        // resultant files are named "*.srt.mp4", and (b) the mime type must be video/*, otherwise we get exception:
                                        // "java.lang.IllegalArgumentException: MIME type text/plain cannot be inserted into content://media/external_primary/video/media; expected MIME type under video/*"
                                        // and we need the file to be saved in the same folder (in DCIM/ ) as the video
                                        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "video/x-srt");
                                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                                            String relative_path = storageUtils.getSaveRelativeFolder();
                                            if( MyDebug.LOG )
                                                Log.d(TAG, "relative_path: " + relative_path);
                                            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, relative_path);
                                            contentValues.put(MediaStore.Video.Media.IS_PENDING, 1);
                                        }
                                        uri = main_activity.getContentResolver().insert(folder, contentValues);
                                        if( uri == null ) {
                                            throw new IOException();
                                        }
                                    }
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "uri: " + uri);
                                    pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "w");
                                    writer = new FileWriter(pfd_saf.getFileDescriptor());
                                }
                            }
                            if( writer != null ) {
                                writer.append(Integer.toString(count));
                                writer.append('\n');
                                writer.append(subtitle_time_from);
                                writer.append(" --> ");
                                writer.append(subtitle_time_to);
                                writer.append('\n');
                                writer.append(subtitles.toString()); // subtitles should include the '\n' at the end
                                writer.append('\n'); // additional newline to indicate end of this subtitle
                                writer.flush();
                                // n.b., we flush rather than closing/reopening the writer each time, as appending doesn't seem to work with storage access framework
                            }
                        }
                        count++;
                    }
                    catch(IOException e) {
                        if( MyDebug.LOG )
                            Log.e(TAG, "SubtitleVideoTimerTask failed to create or write");
                        e.printStackTrace();
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "SubtitleVideoTimerTask exit");
                }

                public boolean cancel() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "SubtitleVideoTimerTask cancel");
                    synchronized( this ) {
                        if( writer != null ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "close writer");
                            try {
                                writer.close();
                            }
                            catch(IOException e) {
                                e.printStackTrace();
                            }
                            writer = null;
                        }
                        if( pfd_saf != null ) {
                            try {
                                pfd_saf.close();
                            }
                            catch(IOException e) {
                                e.printStackTrace();
                            }
                            pfd_saf = null;
                        }
                        if( video_method == VideoMethod.MEDIASTORE ) {
                            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                                ContentValues contentValues = new ContentValues();
                                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                                main_activity.getContentResolver().update(uri, contentValues, null, null);
                            }
                        }
                    }
                    return super.cancel();
                }
            }
            subtitleVideoTimer.schedule(subtitleVideoTimerTask = new SubtitleVideoTimerTask(), 0, 1000);
        }
    }

    @Override
    public void stoppingVideo() {
        if( MyDebug.LOG )
            Log.d(TAG, "stoppingVideo()");
        main_activity.unlockScreen();
        ImageButton view = main_activity.findViewById(R.id.take_photo);
        view.setImageResource(R.drawable.take_video_selector);
        view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
        view.setTag(R.drawable.take_video_selector); // for testing
    }

    @Override
    public void stoppedVideo(final VideoMethod video_method, final Uri uri, final String filename) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "stoppedVideo");
            Log.d(TAG, "video_method " + video_method);
            Log.d(TAG, "uri " + uri);
            Log.d(TAG, "filename " + filename);
        }
        View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
        pauseVideoButton.setVisibility(View.GONE);
        View takePhotoVideoButton = main_activity.findViewById(R.id.take_photo_when_video_recording);
        takePhotoVideoButton.setVisibility(View.GONE);
        main_activity.getMainUI().setPauseVideoContentDescription(); // just to be safe
        main_activity.getMainUI().destroyPopup(); // as the available popup options change while recording video
        if( main_activity.getMainUI().isExposureUIOpen() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to update exposure UI for stop video recording");
            // need to update the exposure UI when starting/stopping video recording, to remove/add
            // ability to switch between auto and manual
            main_activity.getMainUI().setupExposureUI();
        }
        if( subtitleVideoTimerTask != null ) {
            subtitleVideoTimerTask.cancel();
            subtitleVideoTimerTask = null;
        }

        completeVideo(video_method, uri);
        boolean done = broadcastVideo(video_method, uri, filename);
        if( MyDebug.LOG )
            Log.d(TAG, "done? " + done);

        if( isVideoCaptureIntent() ) {
            if( done && video_method == VideoMethod.FILE ) {
                // do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "from video capture intent");
                Intent output = null;
                if( done ) {
                    // may need to pass back the Uri we saved to, if the calling application didn't specify a Uri
                    // set note above for VideoMethod.FILE
                    // n.b., currently this code is not used, as we always switch to VideoMethod.FILE if the calling application didn't specify a Uri, but I've left this here for possible future behaviour
                    if( video_method == VideoMethod.SAF || video_method == VideoMethod.MEDIASTORE ) {
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
            long debug_time = System.currentTimeMillis();
            Bitmap thumbnail = null;
            ParcelFileDescriptor pfd_saf = null; // keep a reference to this as long as retriever, to avoid risk of pfd_saf being garbage collected
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                if( video_method == VideoMethod.FILE ) {
                    File file = new File(filename);
                    retriever.setDataSource(file.getPath());
                }
                else {
                    pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "r");
                    retriever.setDataSource(pfd_saf.getFileDescriptor());
                }
                thumbnail = retriever.getFrameAtTime(-1);
            }
            catch(FileNotFoundException | /*IllegalArgumentException |*/ RuntimeException e) {
                // video file wasn't saved or corrupt video file?
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
                try {
                    if( pfd_saf != null ) {
                        pfd_saf.close();
                    }
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
            if( thumbnail != null ) {
                ImageButton galleryButton = main_activity.findViewById(R.id.gallery);
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
                        updateThumbnail(thumbnail_f, true);
                    }
                });
            }
            if( MyDebug.LOG )
                Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    public void restartedVideo(final VideoMethod video_method, final Uri uri, final String filename) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "restartedVideo");
            Log.d(TAG, "video_method " + video_method);
            Log.d(TAG, "uri " + uri);
            Log.d(TAG, "filename " + filename);
        }
        completeVideo(video_method, uri);
        broadcastVideo(video_method, uri, filename);
    }

    /** Called when we've finished recording to a video file, to do any necessary cleanup for the
     *  file.
     */
    private void completeVideo(final VideoMethod video_method, final Uri uri) {
        if( MyDebug.LOG )
            Log.d(TAG, "completeVideo");
        if( video_method == VideoMethod.MEDIASTORE ) {
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                main_activity.getContentResolver().update(uri, contentValues, null, null);
            }
        }
    }

    private boolean broadcastVideo(final VideoMethod video_method, final Uri uri, final String filename) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "broadcastVideo");
            Log.d(TAG, "video_method " + video_method);
            Log.d(TAG, "uri " + uri);
            Log.d(TAG, "filename " + filename);
        }
        boolean done = false;
        if( video_method == VideoMethod.MEDIASTORE ) {
            // no need to broadcast when using mediastore

            if( uri != null ) {
                // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                // and mediastore method is only used on Android 10+, but keep this just in case
                // announceUri does something in future
                storageUtils.announceUri(uri, false, true);

                // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                storageUtils.setLastMediaScanned(uri);

                done = true;
            }
        }
        else if( video_method == VideoMethod.FILE ) {
            if( filename != null ) {
                File file = new File(filename);
                storageUtils.broadcastFile(file, false, true, true);
                done = true;
            }
        }
        else {
            if( uri != null ) {
                // see note in onPictureTaken() for where we call broadcastFile for SAF photos
                storageUtils.broadcastUri(uri, false, true, true, false);
                done = true;
            }
        }
        if( done ) {
            test_n_videos_scanned++;
            if( MyDebug.LOG )
                Log.d(TAG, "test_n_videos_scanned is now: " + test_n_videos_scanned);
        }

        if( video_method == VideoMethod.MEDIASTORE && isVideoCaptureIntent() ) {
            finishVideoIntent(uri);
        }
        return done;
    }

    /** For use when called from a video capture intent. This returns the supplied uri to the
     *  caller, and finishes the activity.
     */
    void finishVideoIntent(Uri uri) {
        if( MyDebug.LOG )
            Log.d(TAG, "finishVideoIntent:" + uri);
        Intent output = new Intent();
        output.setData(uri);
        main_activity.setResult(Activity.RESULT_OK, output);
        main_activity.finish();
    }

    @Override
    public void deleteUnusedVideo(final VideoMethod video_method, final Uri uri, final String filename) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "deleteUnusedVideo");
            Log.d(TAG, "video_method " + video_method);
            Log.d(TAG, "uri " + uri);
            Log.d(TAG, "filename " + filename);
        }
        if( video_method == VideoMethod.FILE ) {
            trashImage(LastImagesType.FILE, uri, filename, false);
        }
        else if( video_method == VideoMethod.SAF ) {
            trashImage(LastImagesType.SAF, uri, filename, false);
        }
        else if( video_method == VideoMethod.MEDIASTORE ) {
            trashImage(LastImagesType.MEDIASTORE, uri, filename, false);
        }
        // else can't delete Uri
    }

    @Override
    public void onVideoInfo(int what, int extra) {
        // we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && what == MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED ) {
            if( MyDebug.LOG )
                Log.d(TAG, "next output file started");
            int message_id = R.string.video_max_filesize;
            main_activity.getPreview().showToast(null, message_id);
        }
        else if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
            if( MyDebug.LOG )
                Log.d(TAG, "max filesize reached");
            int message_id = R.string.video_max_filesize;
            main_activity.getPreview().showToast(null, message_id);
        }
        // in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
        // fixed in 1.25; also was correct for 1.23 and earlier
        String debug_value = "info_" + what + "_" + extra;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("last_video_error", debug_value);
        editor.apply();
    }

    @Override
    public void onFailedStartPreview() {
        main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
    }

    @Override
    public void onCameraError() {
        main_activity.getPreview().showToast(null, R.string.camera_error);
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
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("last_video_error", debug_value);
        editor.apply();
    }

    @Override
    public void onVideoRecordStartError(VideoProfile profile) {
        if( MyDebug.LOG )
            Log.d(TAG, "onVideoRecordStartError");
        String error_message;
        String features = main_activity.getPreview().getErrorFeatures(profile);
        if( features.length() > 0 ) {
            error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
        }
        else {
            error_message = getContext().getResources().getString(R.string.failed_to_record_video);
        }
        main_activity.getPreview().showToast(null, error_message);
    }

    @Override
    public void onVideoRecordStopError(VideoProfile profile) {
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
        if( MyDebug.LOG )
            Log.d(TAG, "onFailedCreateVideoFileError");
        main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
    }

    @Override
    public void hasPausedPreview(boolean paused) {
        if( MyDebug.LOG )
            Log.d(TAG, "hasPausedPreview: " + paused);
        View shareButton = main_activity.findViewById(R.id.share);
        View trashButton = main_activity.findViewById(R.id.trash);
        if( paused ) {
            shareButton.setVisibility(View.VISIBLE);
            trashButton.setVisibility(View.VISIBLE);
        }
        else {
            shareButton.setVisibility(View.GONE);
            trashButton.setVisibility(View.GONE);
            this.clearLastImages();
        }
    }

    @Override
    public void cameraInOperation(boolean in_operation, boolean is_video) {
        if( MyDebug.LOG )
            Log.d(TAG, "cameraInOperation: " + in_operation);
        if( !in_operation && used_front_screen_flash ) {
            main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
            used_front_screen_flash = false;
        }
        drawPreview.cameraInOperation(in_operation);
        main_activity.getMainUI().showGUI(!in_operation, is_video);
    }

    @Override
    public void turnFrontScreenFlashOn() {
        if( MyDebug.LOG )
            Log.d(TAG, "turnFrontScreenFlashOn");
        used_front_screen_flash = true;
        main_activity.setBrightnessForCamera(true); // ensure we have max screen brightness, even if user preference not set for max brightness
        drawPreview.turnFrontScreenFlashOn();
    }

    @Override
    public void onCaptureStarted() {
        if( MyDebug.LOG )
            Log.d(TAG, "onCaptureStarted");
        n_capture_images = 0;
        n_capture_images_raw = 0;
        drawPreview.onCaptureStarted();
    }

    @Override
    public void onPictureCompleted() {
        if( MyDebug.LOG )
            Log.d(TAG, "onPictureCompleted");

        PhotoMode photo_mode = getPhotoMode();
        if( main_activity.getPreview().isVideo() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "snapshot mode");
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard;
        }
        if( photo_mode == PhotoMode.NoiseReduction ) {
            boolean image_capture_intent = isImageCaptureIntent();
            boolean do_in_background = saveInBackground(image_capture_intent);
            imageSaver.finishImageBatch(do_in_background);
        }
        else if( photo_mode == MyApplicationInterface.PhotoMode.Panorama && gyroSensor.isRecording() ) {
            if( panorama_pic_accepted ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set next panorama point");
                this.setNextPanoramaPoint(false);
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "panorama pic wasn't accepted");
                this.setNextPanoramaPoint(true);
            }
        }
        else if( photo_mode == PhotoMode.FocusBracketing ) {
            if( MyDebug.LOG )
                Log.d(TAG, "focus bracketing completed");
            if( getShutterSoundPref() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "play completion sound");
                MediaPlayer player = MediaPlayer.create(getContext(), Settings.System.DEFAULT_NOTIFICATION_URI);
                if( player != null ) {
                    player.start();
                }
            }
        }

        // call this, so that if pause-preview-after-taking-photo option is set, we remove the "taking photo" border indicator straight away
        // also even for normal (not pausing) behaviour, good to remove the border asap
        drawPreview.cameraInOperation(false);
    }

    @Override
    public void cameraClosed() {
        if( MyDebug.LOG )
            Log.d(TAG, "cameraClosed");
        this.stopPanorama(true);
        main_activity.getMainUI().closeExposureUI();
        main_activity.getMainUI().destroyPopup(); // need to close popup - and when camera reopened, it may have different settings
        drawPreview.clearContinuousFocusMove();
    }

    void updateThumbnail(Bitmap thumbnail, boolean is_video) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateThumbnail");
        main_activity.updateGalleryIcon(thumbnail);
        drawPreview.updateThumbnail(thumbnail, is_video, true);
        if( !is_video && this.getPausePreviewPref() ) {
            drawPreview.showLastImage();
        }
    }

    @Override
    public void timerBeep(long remaining_time) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "timerBeep()");
            Log.d(TAG, "remaining_time: " + remaining_time);
        }
        if( sharedPreferences.getBoolean(PreferenceKeys.TimerBeepPreferenceKey, true) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "play beep!");
            boolean is_last = remaining_time <= 1000;
            main_activity.getSoundPoolManager().playSound(is_last ? R.raw.mybeep_hi : R.raw.mybeep);
        }
        if( sharedPreferences.getBoolean(PreferenceKeys.TimerSpeakPreferenceKey, false) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "speak countdown!");
            int remaining_time_s = (int)(remaining_time/1000);
            if( remaining_time_s <= 60 )
                main_activity.speak("" + remaining_time_s);
        }
    }

    @Override
    public void multitouchZoom(int new_zoom) {
        main_activity.getMainUI().setSeekbarZoom(new_zoom);
    }

    /** Switch to the first available camera that is front or back facing as desired.
     * @param front_facing Whether to switch to a front or back facing camera.
     */
    void switchToCamera(boolean front_facing) {
        if( MyDebug.LOG )
            Log.d(TAG, "switchToCamera: " + front_facing);
        int n_cameras = main_activity.getPreview().getCameraControllerManager().getNumberOfCameras();
        CameraController.Facing want_facing = front_facing ? CameraController.Facing.FACING_FRONT : CameraController.Facing.FACING_BACK;
        for(int i=0;i<n_cameras;i++) {
            if( main_activity.getPreview().getCameraControllerManager().getFacing(i) == want_facing ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "found desired camera: " + i);
                this.setCameraIdPref(i);
                break;
            }
        }
    }

    /* Note that the cameraId is still valid if this returns false, it just means that a cameraId hasn't be explicitly set yet.
     */
    boolean hasSetCameraId() {
        return has_set_cameraId;
    }

    @Override
    public void setCameraIdPref(int cameraId) {
        this.has_set_cameraId = true;
        this.cameraId = cameraId;
    }

    @Override
    public void setFlashPref(String flash_value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
        editor.apply();
    }

    @Override
    public void setFocusPref(String focus_value, boolean is_video) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), focus_value);
        editor.apply();
        // focus may be updated by preview (e.g., when switching to/from video mode)
        main_activity.setManualFocusSeekBarVisibility(false);
    }

    @Override
    public void setVideoPref(boolean is_video) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.IsVideoPreferenceKey, is_video);
        editor.apply();
    }

    @Override
    public void setSceneModePref(String scene_mode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.SceneModePreferenceKey, scene_mode);
        editor.apply();
    }

    @Override
    public void clearSceneModePref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.SceneModePreferenceKey);
        editor.apply();
    }

    @Override
    public void setColorEffectPref(String color_effect) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.ColorEffectPreferenceKey, color_effect);
        editor.apply();
    }

    @Override
    public void clearColorEffectPref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.ColorEffectPreferenceKey);
        editor.apply();
    }

    @Override
    public void setWhiteBalancePref(String white_balance) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.WhiteBalancePreferenceKey, white_balance);
        editor.apply();
    }

    @Override
    public void clearWhiteBalancePref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.WhiteBalancePreferenceKey);
        editor.apply();
    }

    @Override
    public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, white_balance_temperature);
        editor.apply();
    }

    @Override
    public void setISOPref(String iso) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.ISOPreferenceKey, iso);
        editor.apply();
    }

    @Override
    public void clearISOPref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.ISOPreferenceKey);
        editor.apply();
    }

    @Override
    public void setExposureCompensationPref(int exposure) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.ExposurePreferenceKey, "" + exposure);
        editor.apply();
    }

    @Override
    public void clearExposureCompensationPref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.ExposurePreferenceKey);
        editor.apply();
    }

    @Override
    public void setCameraResolutionPref(int width, int height) {
        if( getPhotoMode() == PhotoMode.Panorama ) {
            // in Panorama mode we'll have set a different resolution to the user setting, so don't want that to then be saved!
            return;
        }
        String resolution_value = width + " " + height;
        if( MyDebug.LOG ) {
            Log.d(TAG, "save new resolution_value: " + resolution_value);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
        editor.apply();
    }

    @Override
    public void setVideoQualityPref(String video_quality) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId, fpsIsHighSpeed()), video_quality);
        editor.apply();
    }

    @Override
    public void setZoomPref(int zoom) {
        if( MyDebug.LOG )
            Log.d(TAG, "setZoomPref: " + zoom);
        this.zoom_factor = zoom;
    }

    @Override
    public void requestCameraPermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "requestCameraPermission");
        main_activity.getPermissionHandler().requestCameraPermission();
    }

    @Override
    public boolean needsStoragePermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "needsStoragePermission");
        if( MainActivity.useScopedStorage() )
            return false; // no longer need storage permission with scoped storage - and shouldn't request it either
        return true;
    }

    @Override
    public void requestStoragePermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "requestStoragePermission");
        main_activity.getPermissionHandler().requestStoragePermission();
    }

    @Override
    public void requestRecordAudioPermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "requestRecordAudioPermission");
        main_activity.getPermissionHandler().requestRecordAudioPermission();
    }

    @Override
    public void setExposureTimePref(long exposure_time) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(PreferenceKeys.ExposureTimePreferenceKey, exposure_time);
        editor.apply();
    }

    @Override
    public void clearExposureTimePref() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PreferenceKeys.ExposureTimePreferenceKey);
        editor.apply();
    }

    @Override
    public void setFocusDistancePref(float focus_distance, boolean is_target_distance) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(is_target_distance ? PreferenceKeys.FocusBracketingTargetDistancePreferenceKey : PreferenceKeys.FocusDistancePreferenceKey, focus_distance);
        editor.apply();
    }

    private int getStampFontColor() {
        String color = sharedPreferences.getString(PreferenceKeys.StampFontColorPreferenceKey, "#ffffff");
        return Color.parseColor(color);
    }

    /** Should be called to reset parameters which aren't expected to be saved (e.g., resetting zoom when application is paused,
     *  when switching between photo/video modes, or switching cameras).
     */
    void reset(boolean switched_camera) {
        if( MyDebug.LOG )
            Log.d(TAG, "reset");
        if( switched_camera ) {
            // aperture is reset when switching camera, but not when application is paused or switching between photo/video etc
            this.aperture = aperture_default;
        }
        this.zoom_factor = 0;
    }

    @Override
    public void onDrawPreview(Canvas canvas) {
        if( !main_activity.isCameraInBackground() ) {
            // no point drawing when in background (e.g., settings open)
            drawPreview.onDrawPreview(canvas);
        }
    }

    public enum Alignment {
        ALIGNMENT_TOP,
        ALIGNMENT_CENTRE,
        ALIGNMENT_BOTTOM
    }

    public enum Shadow {
        SHADOW_NONE,
        SHADOW_OUTLINE,
        SHADOW_BACKGROUND
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, Alignment.ALIGNMENT_BOTTOM);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, Shadow.SHADOW_OUTLINE);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, Shadow shadow) {
        return drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, shadow, null);
    }

    public int drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, Shadow shadow, Rect bounds) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        paint.setAlpha(64);
        if( bounds != null ) {
            text_bounds.set(bounds);
        }
        else {
            int alt_height = 0;
            if( ybounds_text != null ) {
                paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
                alt_height = text_bounds.bottom - text_bounds.top;
            }
            paint.getTextBounds(text, 0, text.length(), text_bounds);
            if( ybounds_text != null ) {
                text_bounds.bottom = text_bounds.top + alt_height;
            }
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
        // unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
        int top_y_diff = - text_bounds.top + padding - 1;
        if( alignment_y == Alignment.ALIGNMENT_TOP ) {
            int height = text_bounds.bottom - text_bounds.top + 2*padding;
            text_bounds.top = location_y - 1;
            text_bounds.bottom = text_bounds.top + height;
            location_y += top_y_diff;
        }
        else if( alignment_y == Alignment.ALIGNMENT_CENTRE ) {
            int height = text_bounds.bottom - text_bounds.top + 2*padding;
            //int y_diff = - text_bounds.top + padding - 1;
            text_bounds.top = (int)(0.5 * ( (location_y - 1) + (text_bounds.top + location_y - padding) )); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
            text_bounds.bottom = text_bounds.top + height;
            location_y += (int)(0.5*top_y_diff); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
        }
        else {
            text_bounds.top += location_y - padding;
            text_bounds.bottom += location_y + padding;
        }
        if( shadow == Shadow.SHADOW_BACKGROUND ) {
            paint.setColor(background);
            paint.setAlpha(64);
            canvas.drawRect(text_bounds, paint);
            paint.setAlpha(255);
        }
        paint.setColor(foreground);
        canvas.drawText(text, location_x, location_y, paint);
        if( shadow == Shadow.SHADOW_OUTLINE ) {
            paint.setColor(background);
            paint.setStyle(Paint.Style.STROKE);
            float current_stroke_width = paint.getStrokeWidth();
            paint.setStrokeWidth(1);
            canvas.drawText(text, location_x, location_y, paint);
            paint.setStyle(Paint.Style.FILL); // set back to default
            paint.setStrokeWidth(current_stroke_width); // reset
        }
        return text_bounds.bottom - text_bounds.top;
    }

    private boolean saveInBackground(boolean image_capture_intent) {
        boolean do_in_background = true;
		/*if( !sharedPreferences.getBoolean(PreferenceKeys.BackgroundPhotoSavingPreferenceKey, true) )
			do_in_background = false;
		else*/ if( image_capture_intent )
            do_in_background = false;
        else if( getPausePreviewPref() )
            do_in_background = false;
        return do_in_background;
    }

    boolean isImageCaptureIntent() {
        boolean image_capture_intent = false;
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from image capture intent");
            image_capture_intent = true;
        }
        return image_capture_intent;
    }

    boolean isVideoCaptureIntent() {
        boolean video_capture_intent = false;
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from video capture intent");
            video_capture_intent = true;
        }
        return video_capture_intent;
    }

    /** Whether the photos will be part of a burst, even if we're receiving via the non-burst callbacks.
     */
    private boolean forceSuffix(PhotoMode photo_mode) {
        // focus bracketing and fast burst shots come is as separate requests, so we need to make sure we get the filename suffixes right
        return photo_mode == PhotoMode.FocusBracketing || photo_mode == PhotoMode.FastBurst ||
                (
                        main_activity.getPreview().getCameraController() != null &&
                                main_activity.getPreview().getCameraController().isCapturingBurst()
                );
    }

    /** Saves the supplied image(s)
     * @param save_expo If the photo mode is one where multiple images are saved to a single
     *                  resultant image, this indicates if all the base images should also be saved
     *                  as separate images.
     * @param images The set of images.
     * @param current_date The current date/time stamp for the images.
     * @return Whether saving was successful.
     */
    private boolean saveImage(boolean save_expo, List<byte []> images, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveImage");

        System.gc();

        boolean image_capture_intent = isImageCaptureIntent();
        Uri image_capture_intent_uri = null;
        if( image_capture_intent ) {
            if( MyDebug.LOG )
                Log.d(TAG, "from image capture intent");
            Bundle myExtras = main_activity.getIntent().getExtras();
            if( myExtras != null ) {
                image_capture_intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + image_capture_intent_uri);
            }
        }

        boolean using_camera2 = main_activity.getPreview().usingCamera2API();
        ImageSaver.Request.ImageFormat image_format = getImageFormatPref();
        boolean store_ypr = sharedPreferences.getBoolean(PreferenceKeys.AddYPRToComments, false) &&
                main_activity.getPreview().hasLevelAngle() &&
                main_activity.getPreview().hasPitchAngle() &&
                main_activity.getPreview().hasGeoDirection();
        if( MyDebug.LOG ) {
            Log.d(TAG, "store_ypr: " + store_ypr);
            Log.d(TAG, "has level angle: " + main_activity.getPreview().hasLevelAngle());
            Log.d(TAG, "has pitch angle: " + main_activity.getPreview().hasPitchAngle());
            Log.d(TAG, "has geo direction: " + main_activity.getPreview().hasGeoDirection());
        }
        int image_quality = getSaveImageQualityPref();
        if( MyDebug.LOG )
            Log.d(TAG, "image_quality: " + image_quality);
        boolean do_auto_stabilise = getAutoStabilisePref() && main_activity.getPreview().hasLevelAngleStable();
        double level_angle = (main_activity.getPreview().hasLevelAngle()) ? main_activity.getPreview().getLevelAngle() : 0.0;
        double pitch_angle = (main_activity.getPreview().hasPitchAngle()) ? main_activity.getPreview().getPitchAngle() : 0.0;
        if( do_auto_stabilise && main_activity.test_have_angle )
            level_angle = main_activity.test_angle;
        if( do_auto_stabilise && main_activity.test_low_memory )
            level_angle = 45.0;
        // I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
        boolean is_front_facing = main_activity.getPreview().getCameraController() != null && (main_activity.getPreview().getCameraController().getFacing() == CameraController.Facing.FACING_FRONT);
        boolean mirror = is_front_facing && sharedPreferences.getString(PreferenceKeys.FrontCameraMirrorKey, "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo");
        String preference_stamp = this.getStampPref();
        String preference_textstamp = this.getTextStampPref();
        int font_size = getTextStampFontSizePref();
        int color = getStampFontColor();
        String pref_style = sharedPreferences.getString(PreferenceKeys.StampStyleKey, "preference_stamp_style_shadowed");
        String preference_stamp_dateformat = this.getStampDateFormatPref();
        String preference_stamp_timeformat = this.getStampTimeFormatPref();
        String preference_stamp_gpsformat = this.getStampGPSFormatPref();
        String preference_stamp_geo_address = this.getStampGeoAddressPref();
        String preference_units_distance = this.getUnitsDistancePref();
        boolean panorama_crop = sharedPreferences.getString(PreferenceKeys.PanoramaCropPreferenceKey, "preference_panorama_crop_on").equals("preference_panorama_crop_on");
        boolean store_location = getGeotaggingPref() && getLocation() != null;
        Location location = store_location ? getLocation() : null;
        boolean store_geo_direction = main_activity.getPreview().hasGeoDirection() && getGeodirectionPref();
        double geo_direction = main_activity.getPreview().hasGeoDirection() ? main_activity.getPreview().getGeoDirection() : 0.0;
        String custom_tag_artist = sharedPreferences.getString(PreferenceKeys.ExifArtistPreferenceKey, "");
        String custom_tag_copyright = sharedPreferences.getString(PreferenceKeys.ExifCopyrightPreferenceKey, "");
        String preference_hdr_contrast_enhancement = sharedPreferences.getString(PreferenceKeys.HDRContrastEnhancementPreferenceKey, "preference_hdr_contrast_enhancement_smart");

        int iso = 800; // default value if we can't get ISO
        long exposure_time = 1000000000L/30; // default value if we can't get shutter speed
        float zoom_factor = 1.0f;
        if( main_activity.getPreview().getCameraController() != null ) {
            if( main_activity.getPreview().getCameraController().captureResultHasIso() ) {
                iso = main_activity.getPreview().getCameraController().captureResultIso();
                if( MyDebug.LOG )
                    Log.d(TAG, "iso: " + iso);
            }
            if( main_activity.getPreview().getCameraController().captureResultHasExposureTime() ) {
                exposure_time = main_activity.getPreview().getCameraController().captureResultExposureTime();
                if( MyDebug.LOG )
                    Log.d(TAG, "exposure_time: " + exposure_time);
            }

            zoom_factor = main_activity.getPreview().getZoomRatio();
        }

        boolean has_thumbnail_animation = getThumbnailAnimationPref();

        boolean do_in_background = saveInBackground(image_capture_intent);

        String ghost_image_pref = sharedPreferences.getString(PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off");

        int sample_factor = 1;
        if( !this.getPausePreviewPref() && !ghost_image_pref.equals("preference_ghost_image_last") ) {
            // if pausing the preview, we use the thumbnail also for the preview, so don't downsample
            // similarly for ghosting last image
            // otherwise, we can downsample by 4 to increase performance, without noticeable loss in visual quality (even for the thumbnail animation)
            sample_factor *= 4;
            if( !has_thumbnail_animation ) {
                // can use even lower resolution if we don't have the thumbnail animation
                sample_factor *= 4;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "sample_factor: " + sample_factor);

        boolean success;
        PhotoMode photo_mode = getPhotoMode();
        if( main_activity.getPreview().isVideo() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "snapshot mode");
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard;
        }

        if( !main_activity.is_test && photo_mode == PhotoMode.Panorama && gyroSensor.isRecording() && gyroSensor.hasTarget() && !gyroSensor.isTargetAchieved() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "ignore panorama image as target no longer achieved!");
            // n.b., gyroSensor.hasTarget() will be false if this is the first picture in the panorama series
            panorama_pic_accepted = false;
            success = true; // still treat as success
        }
        else if( photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.Panorama ) {
            boolean first_image;
            if( photo_mode == PhotoMode.Panorama ) {
                panorama_pic_accepted = true;
                first_image = n_panorama_pics == 0;
            }
            else
                first_image = n_capture_images == 1;
            if( first_image ) {
                ImageSaver.Request.SaveBase save_base = ImageSaver.Request.SaveBase.SAVEBASE_NONE;
                if( photo_mode == PhotoMode.NoiseReduction ) {
                    String save_base_preference = sharedPreferences.getString(PreferenceKeys.NRSaveExpoPreferenceKey, "preference_nr_save_no");
                    switch( save_base_preference ) {
                        case "preference_nr_save_single":
                            save_base = ImageSaver.Request.SaveBase.SAVEBASE_FIRST;
                            break;
                        case "preference_nr_save_all":
                            save_base = ImageSaver.Request.SaveBase.SAVEBASE_ALL;
                            break;
                    }
                }
                else if( photo_mode == PhotoMode.Panorama ) {
                    String save_base_preference = sharedPreferences.getString(PreferenceKeys.PanoramaSaveExpoPreferenceKey, "preference_panorama_save_no");
                    switch( save_base_preference ) {
                        case "preference_panorama_save_all":
                            save_base = ImageSaver.Request.SaveBase.SAVEBASE_ALL;
                            break;
                        case "preference_panorama_save_all_plus_debug":
                            save_base = ImageSaver.Request.SaveBase.SAVEBASE_ALL_PLUS_DEBUG;
                            break;
                    }
                }

                imageSaver.startImageBatch(true,
                        photo_mode == PhotoMode.NoiseReduction ? ImageSaver.Request.ProcessType.AVERAGE : ImageSaver.Request.ProcessType.PANORAMA,
                        save_base,
                        image_capture_intent, image_capture_intent_uri,
                        using_camera2,
                        image_format, image_quality,
                        do_auto_stabilise, level_angle, photo_mode == PhotoMode.Panorama,
                        is_front_facing,
                        mirror,
                        current_date,
                        iso,
                        exposure_time,
                        zoom_factor,
                        preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, preference_stamp_geo_address, preference_units_distance,
                        panorama_crop,
                        store_location, location, store_geo_direction, geo_direction,
                        pitch_angle, store_ypr,
                        custom_tag_artist, custom_tag_copyright,
                        sample_factor);

                if( photo_mode == PhotoMode.Panorama ) {
                    imageSaver.getImageBatchRequest().camera_view_angle_x = main_activity.getPreview().getViewAngleX(false);
                    imageSaver.getImageBatchRequest().camera_view_angle_y = main_activity.getPreview().getViewAngleY(false);
                }
            }

            float [] gyro_rotation_matrix = null;
            if( photo_mode == PhotoMode.Panorama ) {
                gyro_rotation_matrix = new float[9];
                this.gyroSensor.getRotationMatrix(gyro_rotation_matrix);
            }

            imageSaver.addImageBatch(images.get(0), gyro_rotation_matrix);
            success = true;
        }
        else {
            boolean is_hdr = photo_mode == PhotoMode.DRO || photo_mode == PhotoMode.HDR;
            boolean force_suffix = forceSuffix(photo_mode);
            success = imageSaver.saveImageJpeg(do_in_background, is_hdr,
                    force_suffix,
                    // N.B., n_capture_images will be 1 for first image, not 0, so subtract 1 so we start off from _0.
                    // (It wouldn't be a huge problem if we did start from _1, but it would be inconsistent with the naming
                    // of images where images.size() > 1 (e.g., expo bracketing mode) where we also start from _0.)
                    force_suffix ? (n_capture_images-1) : 0,
                    save_expo, images,
                    image_capture_intent, image_capture_intent_uri,
                    using_camera2,
                    image_format, image_quality,
                    do_auto_stabilise, level_angle,
                    is_front_facing,
                    mirror,
                    current_date,
                    preference_hdr_contrast_enhancement,
                    iso,
                    exposure_time,
                    zoom_factor,
                    preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat, preference_stamp_geo_address, preference_units_distance,
                    false, // panorama doesn't use this codepath
                    store_location, location, store_geo_direction, geo_direction,
                    pitch_angle, store_ypr,
                    custom_tag_artist, custom_tag_copyright,
                    sample_factor);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "saveImage complete, success: " + success);

        return success;
    }

    @Override
    public boolean onPictureTaken(byte [] data, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "onPictureTaken");

        n_capture_images++;
        if( MyDebug.LOG )
            Log.d(TAG, "n_capture_images is now " + n_capture_images);

        List<byte []> images = new ArrayList<>();
        images.add(data);

        boolean success = saveImage(false, images, current_date);

        if( MyDebug.LOG )
            Log.d(TAG, "onPictureTaken complete, success: " + success);

        return success;
    }

    @Override
    public boolean onBurstPictureTaken(List<byte []> images, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "onBurstPictureTaken: received " + images.size() + " images");

        boolean success;
        PhotoMode photo_mode = getPhotoMode();
        if( main_activity.getPreview().isVideo() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "snapshot mode");
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard;
        }
        if( photo_mode == PhotoMode.HDR ) {
            if( MyDebug.LOG )
                Log.d(TAG, "HDR mode");
            boolean save_expo = sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false);
            if( MyDebug.LOG )
                Log.d(TAG, "save_expo: " + save_expo);

            success = saveImage(save_expo, images, current_date);
        }
        else {
            if( MyDebug.LOG ) {
                Log.d(TAG, "exposure/focus bracketing mode mode");
                if( photo_mode != PhotoMode.ExpoBracketing && photo_mode != PhotoMode.FocusBracketing )
                    Log.e(TAG, "onBurstPictureTaken called with unexpected photo mode?!: " + photo_mode);
            }

            success = saveImage(true, images, current_date);
        }
        return success;
    }

    @Override
    public boolean onRawPictureTaken(RawImage raw_image, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "onRawPictureTaken");
        System.gc();

        n_capture_images_raw++;
        if( MyDebug.LOG )
            Log.d(TAG, "n_capture_images_raw is now " + n_capture_images_raw);

        boolean do_in_background = saveInBackground(false);

        PhotoMode photo_mode = getPhotoMode();
        if( main_activity.getPreview().isVideo() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "snapshot mode");
            // must be in photo snapshot while recording video mode, only support standard photo mode
            // (RAW not supported anyway for video snapshot mode, but have this code just to be safe)
            photo_mode = PhotoMode.Standard;
        }
        boolean force_suffix = forceSuffix(photo_mode);
        // N.B., n_capture_images_raw will be 1 for first image, not 0, so subtract 1 so we start off from _0.
        // (It wouldn't be a huge problem if we did start from _1, but it would be inconsistent with the naming
        // of images where images.size() > 1 (e.g., expo bracketing mode) where we also start from _0.)
        int suffix_offset = force_suffix ? (n_capture_images_raw-1) : 0;
        boolean success = imageSaver.saveImageRaw(do_in_background, force_suffix, suffix_offset, raw_image, current_date);

        if( MyDebug.LOG )
            Log.d(TAG, "onRawPictureTaken complete");
        return success;
    }

    @Override
    public boolean onRawBurstPictureTaken(List<RawImage> raw_images, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "onRawBurstPictureTaken");
        System.gc();

        boolean do_in_background = saveInBackground(false);

        // currently we don't ever do post processing with RAW burst images, so just save them all
        boolean success = true;
        for(int i=0;i<raw_images.size() && success;i++) {
            success = imageSaver.saveImageRaw(do_in_background, true, i, raw_images.get(i), current_date);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onRawBurstPictureTaken complete");
        return success;
    }

    void addLastImage(File file, boolean share) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "addLastImage: " + file);
            Log.d(TAG, "share?: " + share);
        }
        last_images_type = LastImagesType.FILE;
        LastImage last_image = new LastImage(file.getAbsolutePath(), share);
        last_images.add(last_image);
    }

    void addLastImageSAF(Uri uri, boolean share) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "addLastImageSAF: " + uri);
            Log.d(TAG, "share?: " + share);
        }
        last_images_type = LastImagesType.SAF;
        LastImage last_image = new LastImage(uri, share);
        last_images.add(last_image);
    }

    void addLastImageMediaStore(Uri uri, boolean share) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "addLastImageMediaStore: " + uri);
            Log.d(TAG, "share?: " + share);
        }
        last_images_type = LastImagesType.MEDIASTORE;
        LastImage last_image = new LastImage(uri, share);
        last_images.add(last_image);
    }

    void clearLastImages() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearLastImages");
        last_images_type = LastImagesType.FILE;
        last_images.clear();
        drawPreview.clearLastImage();
    }

    void shareLastImage() {
        if( MyDebug.LOG )
            Log.d(TAG, "shareLastImage");
        Preview preview  = main_activity.getPreview();
        if( preview.isPreviewPaused() ) {
            LastImage share_image = null;
            for(int i=0;i<last_images.size() && share_image == null;i++) {
                LastImage last_image = last_images.get(i);
                if( last_image.share ) {
                    share_image = last_image;
                }
            }
            boolean done = true;
            if( share_image != null ) {
                Uri last_image_uri = share_image.uri;
                if( MyDebug.LOG )
                    Log.d(TAG, "Share: " + last_image_uri);
                if( last_image_uri == null ) {
                    // could happen with Android 7+ with non-SAF if the image hasn't been scanned yet,
                    // so we don't know the uri yet
                    Log.e(TAG, "can't share last image as don't yet have uri");
                    done = false;
                }
                else {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/jpeg");
                    intent.putExtra(Intent.EXTRA_STREAM, last_image_uri);
                    main_activity.startActivity(Intent.createChooser(intent, "Photo"));
                }
            }
            if( done ) {
                clearLastImages();
                preview.startCameraPreview();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void trashImage(LastImagesType image_type, Uri image_uri, String image_name, boolean from_user) {
        if( MyDebug.LOG )
            Log.d(TAG, "trashImage");
        Preview preview  = main_activity.getPreview();
        if( image_type == LastImagesType.SAF && image_uri != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Delete SAF: " + image_uri);
            File file = storageUtils.getFileFromDocumentUriSAF(image_uri, false); // need to get file before deleting it, as fileFromDocumentUriSAF may depend on the file still existing
            try {
                if( !DocumentsContract.deleteDocument(main_activity.getContentResolver(), image_uri) ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "failed to delete " + image_uri);
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "successfully deleted " + image_uri);
                    if( from_user )
                        preview.showToast(null, R.string.photo_deleted);
                    if( file != null ) {
                        // SAF doesn't broadcast when deleting them
                        storageUtils.broadcastFile(file, false, false, true);
                    }
                }
            }
            catch(FileNotFoundException e) {
                // note, Android Studio reports a warning that FileNotFoundException isn't thrown, but it can be
                // thrown by DocumentsContract.deleteDocument - and we get an error if we try to remove the catch!
                if( MyDebug.LOG )
                    Log.e(TAG, "exception when deleting " + image_uri);
                e.printStackTrace();
            }
        }
        else if( image_type == LastImagesType.MEDIASTORE && image_uri != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Delete MediaStore: " + image_uri);
            main_activity.getContentResolver().delete(image_uri, null, null);
        }
        else if( image_name != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "Delete: " + image_name);
            File file = new File(image_name);
            if( !file.delete() ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to delete " + image_name);
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "successfully deleted " + image_name);
                if( from_user )
                    preview.showToast(photo_delete_toast, R.string.photo_deleted);
                storageUtils.broadcastFile(file, false, false, true);
            }
        }
    }

    void trashLastImage() {
        if( MyDebug.LOG )
            Log.d(TAG, "trashLastImage");
        Preview preview  = main_activity.getPreview();
        if( preview.isPreviewPaused() ) {
            for(int i=0;i<last_images.size();i++) {
                LastImage last_image = last_images.get(i);
                trashImage(last_images_type, last_image.uri, last_image.name, true);
            }
            clearLastImages();
            drawPreview.clearGhostImage(); // doesn't make sense to show the last image as a ghost, if the user has trashed it!
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

    /** Called when StorageUtils scans a saved photo with MediaScannerConnection.scanFile.
     * @param file The file that was scanned.
     * @param uri  The file's corresponding uri.
     */
    void scannedFile(File file, Uri uri) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "scannedFile");
            Log.d(TAG, "file: " + file);
            Log.d(TAG, "uri: " + uri);
        }
        // see note under LastImage constructor for why we need to update the Uris
        for(int i=0;i<last_images.size();i++) {
            LastImage last_image = last_images.get(i);
            if( MyDebug.LOG )
                Log.d(TAG, "compare to last_image: " + last_image.name);
            if( last_image.uri == null && last_image.name != null && last_image.name.equals(file.getAbsolutePath()) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "updated last_image : " + i);
                last_image.uri = uri;
            }
        }
    }

    // for testing

    boolean hasThumbnailAnimation() {
        return this.drawPreview.hasThumbnailAnimation();
    }

    public HDRProcessor getHDRProcessor() {
        return imageSaver.getHDRProcessor();
    }

    public PanoramaProcessor getPanoramaProcessor() {
        return imageSaver.getPanoramaProcessor();
    }

    public boolean test_set_available_memory = false;
    public long test_available_memory = 0;
}
