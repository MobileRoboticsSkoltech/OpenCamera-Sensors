package net.sourceforge.opencamera.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.sourceforge.opencamera.MyPreferenceFragment;
import net.sourceforge.opencamera.PanoramaProcessorException;
import net.sourceforge.opencamera.cameracontroller.CameraController2;
import net.sourceforge.opencamera.HDRProcessor;
import net.sourceforge.opencamera.HDRProcessorException;
import net.sourceforge.opencamera.ImageSaver;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.preview.ApplicationInterface;
import net.sourceforge.opencamera.preview.VideoProfile;
import net.sourceforge.opencamera.SaveLocationHistory;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.ui.FolderChooserDialog;
import net.sourceforge.opencamera.ui.PopupView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
//import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
//import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.TonemapCurve;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
//import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.support.annotation.RequiresApi;
import android.test.ActivityInstrumentationTestCase2;
import android.test.TouchUtils;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ZoomControls;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private static final String TAG = "MainActivityTest";
    private MainActivity mActivity = null;
    private Preview mPreview = null;
    public static final boolean test_camera2 = false;
    //public static final boolean test_camera2 = true;

    public MainActivityTest() {
        super("net.sourceforge.opencamera", MainActivity.class);
    }

    private Intent createDefaultIntent() {
        Intent intent = new Intent();
        intent.putExtra("test_project", true);
        return intent;
    }

    @Override
    protected void setUp() throws Exception {
        Log.d(TAG, "setUp");
        super.setUp();

        setActivityInitialTouchMode(false);
        Log.d(TAG, "setUp: 1");

        // initialise test statics (to avoid the persisting between tests in a test suite run!)
        ImageSaver.test_small_queue_size = false;

        // use getTargetContext() as we haven't started the activity yet (and don't want to, as we want to set prefs before starting)
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this.getInstrumentation().getTargetContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        if( test_camera2 ) {
            MainActivity.test_force_supports_camera2 = true;
            //editor.putBoolean(PreferenceKeys.UseCamera2PreferenceKey, true);
            editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
        }
        editor.apply();
        Log.d(TAG, "setUp: 2");

        Intent intent = createDefaultIntent();
        setActivityIntent(intent);
        Log.d(TAG, "setUp: about to get activity");
        mActivity = getActivity();
        Log.d(TAG, "setUp: activity: " + mActivity);
        mPreview = mActivity.getPreview();
        Log.d(TAG, "setUp: preview: " + mPreview);

        // don't waitUntilCameraOpened() here, as if an assertion fails in setUp(), it can cause later tests to hang in the suite
        // instead we now wait for camera to open in setToDefault()
        //waitUntilCameraOpened();

        //restart(); // no longer need to restart, as we reset prefs before starting up; not restarting makes tests run faster!

        //Camera camera = mPreview.getCamera();
        /*mSpinner = (Spinner) mActivity.findViewById(
            com.android.example.spinner.R.id.Spinner01
          );*/

        //mPlanetData = mSpinner.getAdapter();
    }

    @Override
    protected void tearDown() throws Exception {
        Log.d(TAG, "tearDown");

        // shouldn't have assertions in tearDown, otherwise we'll never cleanup properly - when run as suite, the next test will either fail or hang!
        //assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_camera_parameters_exception == 0 );
        //assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );

        // reset back to defaults
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.apply();

        super.tearDown();
    }

    public void testPreConditions() {
        assertTrue(mPreview != null);
        //assertTrue(mPreview.getCamera() != null);
        //assertTrue(mCamera != null);
        //assertTrue(mSpinner.getOnItemSelectedListener() != null);
        //assertTrue(mPlanetData != null);
        //assertEquals(mPlanetData.getCount(),ADAPTER_COUNT);
    }

    private void waitUntilCameraOpened() {
        Log.d(TAG, "wait until camera opened");
        long time_s = System.currentTimeMillis();
        while( !mPreview.openCameraAttempted() ) {
            assertTrue( System.currentTimeMillis() - time_s < 20000 );
        }
        Log.d(TAG, "camera is open!");
        this.getInstrumentation().waitForIdleSync(); // allow the onPostExecute of open camera task run
        Log.d(TAG, "done idle sync");
        try {
            Thread.sleep(100); // sleep a bit just to be safe
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /** Restarts Open Camera.
     *  WARNING: Make sure that any assigned variables related to the activity, e.g., anything
     *  returned by findViewById(), is updated to the new mActivity after calling this method!
     */
    private void restart() {
        Log.d(TAG, "restart");
        mActivity.finish();
        setActivity(null);
        Log.d(TAG, "now starting");
        mActivity = getActivity();
        Log.d(TAG, "mActivity is now: " + mActivity);
        mPreview = mActivity.getPreview();
        Log.d(TAG, "mPreview is now: " + mPreview);
        waitUntilCameraOpened();
        Log.d(TAG, "restart done");
    }

    private void pauseAndResume() {
        Log.d(TAG, "pauseAndResume");
        pauseAndResume(true);
    }

    private void pauseAndResume(boolean wait_until_camera_opened) {
        Log.d(TAG, "pauseAndResume: " + wait_until_camera_opened);
        // onResume has code that must run on UI thread
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "pause...");
                getInstrumentation().callActivityOnPause(mActivity);
                Log.d(TAG, "resume...");
                getInstrumentation().callActivityOnResume(mActivity);
                /*Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        Log.d(TAG, "resume...");
                        getInstrumentation().callActivityOnResume(mActivity);
                    }
                }, 500);*/
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        if( wait_until_camera_opened ) {
            waitUntilCameraOpened();
        }
    }

    private void updateForSettings() {
        Log.d(TAG, "updateForSettings");
        // updateForSettings has code that must run on UI thread
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.getApplicationInterface().getDrawPreview().updateSettings();
                mActivity.updateForSettings();
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        waitUntilCameraOpened(); // may need to wait if camera is reopened, e.g., when changing scene mode - see testSceneMode()
    }

    private void clickView(final View view) {
        // TouchUtils.clickView doesn't work properly if phone held in portrait mode!
        //TouchUtils.clickView(MainActivityTest.this, view);
        Log.d(TAG, "clickView: "+ view);
        assertTrue(view.getVisibility() == View.VISIBLE);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                assertTrue(view.performClick());
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
    }

    private void switchToCamera(int cameraId) {
        int origCameraId = mPreview.getCameraId();
        Log.d(TAG, "switchToCamera: "+ cameraId);
        Log.d(TAG, "origCameraId: "+ origCameraId);
        int newCameraId = origCameraId;
        while( newCameraId != cameraId ) {
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            this.getInstrumentation().waitForIdleSync();
            waitUntilCameraOpened();
            newCameraId = mPreview.getCameraId();
            Log.d(TAG, "changed cameraId to: "+ newCameraId);
            assertTrue(newCameraId != origCameraId);
        }
    }

    private void switchToFlashValue(String required_flash_value) {
        if( mPreview.supportsFlash() ) {
            String flash_value = mPreview.getCurrentFlashValue();
            Log.d(TAG, "start flash_value: "+ flash_value);
            Log.d(TAG, "required_flash_value: "+ required_flash_value);
            if( !flash_value.equals(required_flash_value) ) {
                assertFalse( mActivity.popupIsOpen() );
                View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
                clickView(popupButton);
                Log.d(TAG, "wait for popup to open");
                while( !mActivity.popupIsOpen() ) {
                }
                Log.d(TAG, "popup is now open");
                View currentFlashButton = mActivity.getUIButton("TEST_FLASH_" + flash_value);
                assertTrue(currentFlashButton != null);
                assertTrue(currentFlashButton.getAlpha() == PopupView.ALPHA_BUTTON_SELECTED);
                View flashButton = mActivity.getUIButton("TEST_FLASH_" + required_flash_value);
                assertTrue(flashButton != null);
                assertEquals(flashButton.getAlpha(), PopupView.ALPHA_BUTTON, 1.0e-5);
                clickView(flashButton);
                flash_value = mPreview.getCurrentFlashValue();
                Log.d(TAG, "changed flash_value to: "+ flash_value);
            }
            assertTrue(flash_value.equals(required_flash_value));
            String controller_flash_value = mPreview.getCameraController().getFlashValue();
            Log.d(TAG, "controller_flash_value: "+ controller_flash_value);
            if( flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on") ) {
                // for frontscreen flash, the controller flash value will be "" (due to real flash not supported) - although on Galaxy Nexus this is "flash_off" due to parameters.getFlashMode() returning Camera.Parameters.FLASH_MODE_OFF
                assertTrue(controller_flash_value.equals("") || controller_flash_value.equals("flash_off"));
            }
            else {
                Log.d(TAG, "expected_flash_value: "+ flash_value);
                assertTrue(flash_value.equals( controller_flash_value ));
            }
        }
    }

    private void switchToFocusValue(String required_focus_value) {
        Log.d(TAG, "switchToFocusValue: "+ required_focus_value);
        if( mPreview.supportsFocus() ) {
            String focus_value = mPreview.getCurrentFocusValue();
            Log.d(TAG, "start focus_value: "+ focus_value);
            if( !focus_value.equals(required_focus_value) ) {
                assertFalse( mActivity.popupIsOpen() );
                View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
                clickView(popupButton);
                while( !mActivity.popupIsOpen() ) {
                }
                View focusButton = mActivity.getUIButton("TEST_FOCUS_" + required_focus_value);
                assertTrue(focusButton != null);
                clickView(focusButton);
                focus_value = mPreview.getCurrentFocusValue();
                Log.d(TAG, "changed focus_value to: "+ focus_value);
            }
            assertTrue(focus_value.equals(required_focus_value));
            String actual_focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "actual_focus_value: "+ actual_focus_value);
            String compare_focus_value = focus_value;
            if( compare_focus_value.equals("focus_mode_locked") )
                compare_focus_value = "focus_mode_auto";
            else if( compare_focus_value.equals("focus_mode_infinity") && mPreview.usingCamera2API() )
                compare_focus_value = "focus_mode_manual2";
            assertTrue(compare_focus_value.equals(actual_focus_value));
        }
    }

    private void switchToISO(int required_iso) {
        Log.d(TAG, "switchToISO: "+ required_iso);
        int iso = mPreview.getCameraController().getISO();
        Log.d(TAG, "start iso: "+ iso);
        if( iso != required_iso ) {
            /*assertFalse( mActivity.popupIsOpen() );
            View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
            clickView(popupButton);
            while( !mActivity.popupIsOpen() ) {
            }
            View isoButton = mActivity.getUIButton("TEST_ISO_" + required_iso);
            assertTrue(isoButton != null);
            clickView(isoButton);
            iso = mPreview.getCameraController().getISO();
            Log.d(TAG, "changed iso to: "+ iso);*/
            View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            View exposureContainer = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
            assertTrue(exposureContainer.getVisibility() == View.GONE);
            clickView(exposureButton);
            assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
            View isoButton = mActivity.getUIButton("TEST_ISO_" + required_iso);
            assertTrue(isoButton != null);
            clickView(isoButton);
            iso = mPreview.getCameraController().getISO();
            Log.d(TAG, "changed iso to: "+ iso);
            clickView(exposureButton);
            assertTrue(exposureContainer.getVisibility() == View.GONE);
        }
        assertTrue(iso == required_iso);
    }

    /* Sets the camera up to a predictable state:
     * - Back camera
     * - Photo mode
     * - Flash off (if flash supported)
     * - Focus mode picture continuous (if focus modes supported)
     * As a side-effect, the camera and/or camera parameters values may become invalid.
     */
    private void setToDefault() {
        waitUntilCameraOpened();

        if( mPreview.isVideo() ) {
            Log.d(TAG, "turn off video mode");
            View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(!mPreview.isVideo());

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            switchToCamera(0);
        }

        switchToFlashValue("flash_off");
        switchToFocusValue("focus_mode_continuous_picture");
        // pause for safety - needed for Nokia 8 at least otherwise some tests like testContinuousPictureFocusRepeat,
        // testLocationOff result in hang whilst waiting for photo to be taken, and hit the timeout in waitForTakePhoto()
        try {
            Thread.sleep(200);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* Ensures that shut down properly when pausing.
     */
    public void testPause() throws InterruptedException {
        Log.d(TAG, "testPause");

        setToDefault();
        Thread.sleep(1000);

        // checker ticker is running okay
        assertTrue(mPreview.test_ticker_called);
        mPreview.test_ticker_called = false;
        Thread.sleep(300);
        assertTrue(mPreview.test_ticker_called);

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "pause...");
                getInstrumentation().callActivityOnPause(mActivity);
            }
        });
        this.getInstrumentation().waitForIdleSync();

        // ensure ticker is turned off after certain time
        Thread.sleep(3000);
        mPreview.test_ticker_called = false;
        Thread.sleep(1000);
        assertFalse(mPreview.test_ticker_called);

        // resume, and assume we've started the ticker again
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "resume...");
                getInstrumentation().callActivityOnResume(mActivity);
            }
        });
        Thread.sleep(3000);
        waitUntilCameraOpened();
        assertTrue(mPreview.test_ticker_called);
        mPreview.test_ticker_called = false;
        Thread.sleep(300);
        assertTrue(mPreview.test_ticker_called);
    }

    /** Tests that we clean up the background task for opening camera properly.
     */
    public void testImmediatelyQuit() throws InterruptedException {
        Log.d(TAG, "testImmediatelyQuit");
        setToDefault();

        for(int i=0;i<5;i++) {
            // like restart, but don't wait for camera to be opened
            Log.d(TAG, "call finish");
            mActivity.finish();
            setActivity(null);
            Log.d(TAG, "now starting");
            mActivity = getActivity();
            mPreview = mActivity.getPreview();

            // now restart straight away
            restart();

            Thread.sleep(1000);
        }
    }

    /* Ensures that we only start the camera preview once when starting up.
     */
    public void testStartCameraPreviewCount() {
        Log.d(TAG, "testStartCameraPreviewCount");
        /*Log.d(TAG, "1 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
        int init_count_cameraStartPreview = mPreview.count_cameraStartPreview;
        mActivity.finish();
        setActivity(null);
        mActivity = this.getActivity();
        mPreview = mActivity.getPreview();
        Log.d(TAG, "2 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
        assertTrue(mPreview.count_cameraStartPreview == init_count_cameraStartPreview);
        this.getInstrumentation().callActivityOnPause(mActivity);
        Log.d(TAG, "3 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
        assertTrue(mPreview.count_cameraStartPreview == init_count_cameraStartPreview);
        this.getInstrumentation().callActivityOnResume(mActivity);
        Log.d(TAG, "4 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
        assertTrue(mPreview.count_cameraStartPreview == init_count_cameraStartPreview+1);*/
        setToDefault();

        restart();
        // onResume has code that must run on UI thread
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "1 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
                assertTrue(mPreview.count_cameraStartPreview == 1);
                getInstrumentation().callActivityOnPause(mActivity);
                Log.d(TAG, "2 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
                assertTrue(mPreview.count_cameraStartPreview == 1);
                getInstrumentation().callActivityOnResume(mActivity);
            }
        });
        // need to wait for UI code to finish before leaving
        Log.d(TAG, "wait for idle sync");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "done waiting for idle sync");
        // waiting for camera to open can't be on the ui thread, as it's on the ui thread that Open Camera sets that we've opened the camera
        waitUntilCameraOpened();
        Log.d(TAG, "3 count_cameraStartPreview: " + mPreview.count_cameraStartPreview);
        assertTrue(mPreview.count_cameraStartPreview == 2);
    }

    /* Ensures that we save the video mode.
     * Also tests the icons and content descriptions of the take photo and switch photo/video buttons are as expected.
     */
    private void subTestSaveVideoMode() {
        Log.d(TAG, "subTestSaveVideoMode");
        setToDefault();

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);

        assertTrue(!mPreview.isVideo());
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.take_photo) ) );
        assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_video) ) );

        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
        assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo) ) );

        restart();
        assertTrue(mPreview.isVideo());
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
        assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo) ) );

        pauseAndResume();
        assertTrue(mPreview.isVideo());
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
        assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo) ) );
    }

    /* Returns a focus mode that is supported by the device, but not the default focus mode.
     */
    private String getNonDefaultFocus() {
        String non_default_focus;
        if( mPreview.getSupportedFocusValues().contains("focus_mode_macro") ) {
            non_default_focus = "focus_mode_macro";
        }
        else if( mPreview.getSupportedFocusValues().contains("focus_mode_infinity") ) {
            non_default_focus = "focus_mode_infinity";
        }
        else {
            non_default_focus = null;
            fail("can't choose a non-default focus for this device");
        }
        return non_default_focus;
    }

    /* Ensures that we save the focus mode for photos when restarting.
     * Note that saving the focus mode for video mode is tested in testFocusSwitchVideoResetContinuous.
     */
    private void subTestSaveFocusMode() {
        Log.d(TAG, "subTestSaveFocusMode");
        if( !mPreview.supportsFocus() ) {
            return;
        }

        setToDefault();

        String non_default_focus = getNonDefaultFocus();

        String focus_value = mPreview.getCameraController().getFocusValue();
        assertFalse(focus_value.equals(non_default_focus));

        switchToFocusValue(non_default_focus);

        restart();
        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals(non_default_focus));

        pauseAndResume();
        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals(non_default_focus));
    }

    /* Ensures that we save the flash mode torch when quitting and restarting.
     */
    private void subTestSaveFlashTorchQuit() throws InterruptedException {
        Log.d(TAG, "subTestSaveFlashTorchQuit");

        setToDefault();

        if( !mPreview.supportsFlash() ) {
            Log.d(TAG, "doesn't support flash");
            return;
        }

        switchToFlashValue("flash_torch");

        restart();
        Thread.sleep(4000); // needs to be long enough for the autofocus to complete
        String controller_flash_value = mPreview.getCameraController().getFlashValue();
        Log.d(TAG, "controller_flash_value: " + controller_flash_value);
        assertTrue(controller_flash_value.equals("flash_torch"));
        String flash_value = mPreview.getCurrentFlashValue();
        Log.d(TAG, "flash_value: " + flash_value);
        assertTrue(flash_value.equals("flash_torch"));
    }

    private void subTestExposureLockNotSaved() {
        Log.d(TAG, "subTestExposureLockNotSaved");

        if( !mPreview.supportsExposureLock() ) {
            return;
        }

        setToDefault();

        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        assertTrue( exposureLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.exposure_lock) ) );
        clickView(exposureLockButton);
        assertTrue(mPreview.getCameraController().getAutoExposureLock());
        assertTrue( exposureLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.exposure_unlock) ) );

        this.pauseAndResume();
        assertTrue(!mPreview.getCameraController().getAutoExposureLock());
        assertTrue( exposureLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.exposure_lock) ) );

        // now with restart

        clickView(exposureLockButton);
        assertTrue(mPreview.getCameraController().getAutoExposureLock());
        assertTrue( exposureLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.exposure_unlock) ) );

        restart();
        assertTrue(!mPreview.getCameraController().getAutoExposureLock());
        exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        assertTrue( exposureLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.exposure_lock) ) );
    }

    private void subTestWhiteBalanceLockNotSaved() {
        Log.d(TAG, "subTestWhiteBalanceLockNotSaved");

        if( !mPreview.supportsWhiteBalanceLock() ) {
            return;
        }

        setToDefault();

        View whiteBalanceLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.white_balance_lock);
        assertTrue( whiteBalanceLockButton.getVisibility() == View.GONE );

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.ShowWhiteBalanceLockPreferenceKey, true);
        editor.apply();
        updateForSettings();
        assertTrue( whiteBalanceLockButton.getVisibility() == View.VISIBLE );

        assertTrue( whiteBalanceLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.white_balance_lock) ) );
        clickView(whiteBalanceLockButton);
        assertTrue(mPreview.getCameraController().getAutoWhiteBalanceLock());
        assertTrue( whiteBalanceLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.white_balance_unlock) ) );

        this.pauseAndResume();
        assertTrue(!mPreview.getCameraController().getAutoWhiteBalanceLock());
        assertTrue( whiteBalanceLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.white_balance_lock) ) );

        // now with restart

        clickView(whiteBalanceLockButton);
        assertTrue(mPreview.getCameraController().getAutoWhiteBalanceLock());
        assertTrue( whiteBalanceLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.white_balance_unlock) ) );

        restart();
        assertTrue(!mPreview.getCameraController().getAutoWhiteBalanceLock());
        whiteBalanceLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.white_balance_lock);
        assertTrue( whiteBalanceLockButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.white_balance_lock) ) );
    }

    /** Tests for things which should (or shouldn't) be saved.
     */
    public void testSaveModes() throws InterruptedException {
        Log.d(TAG, "testSaveModes");
        subTestSaveVideoMode();
        subTestSaveFocusMode();
        subTestSaveFlashTorchQuit();
        subTestExposureLockNotSaved();
        subTestWhiteBalanceLockNotSaved();
    }

    /* Ensures that the flash mode changes as expected when switching between photo and video modes.
     */
    public void testFlashVideoMode() {
        Log.d(TAG, "testFlashVideoMode");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        assertTrue(!mPreview.isVideo());

        switchToFlashValue("flash_auto");
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_auto"));

        Log.d(TAG, "switch to video");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        // flash should turn off when in video mode, so that flash doesn't fire for photo snapshot while recording video
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_off"));

        restart();
        switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_off"));

        // switch back to photo mode, should return to flash auto
        Log.d(TAG, "switch to photo");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(!mPreview.isVideo());
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_auto"));

        // turn on torch, check it remains on for video
        switchToFlashValue("flash_torch");
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_torch"));

        Log.d(TAG, "switch to video");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_torch"));

        restart();
        switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_torch"));

        // switch back to photo mode, should remain in flash torch
        Log.d(TAG, "switch to photo");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(!mPreview.isVideo());
        assertTrue(mPreview.getCurrentFlashValue().equals("flash_torch"));
    }

    /* Ensures that we save the flash mode torch when switching to front camera and then to back
     * Note that this sometimes fail on Galaxy Nexus, because flash turns off after autofocus (and other camera apps do this too), but this only seems to happen some of the time!
     * And Nexus 7 has no flash anyway.
     * So commented out test for now.
     */
    /*public void testSaveFlashTorchSwitchCamera() {
        Log.d(TAG, "testSaveFlashTorchSwitchCamera");

        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }
        else if( Camera.getNumberOfCameras() <= 1 ) {
            return;
        }

        switchToFlashValue("flash_torch");

        int cameraId = mPreview.getCameraId();
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();
        int new_cameraId = mPreview.getCameraId();
        assertTrue(cameraId != new_cameraId);

        clickView(switchCameraButton);
        waitUntilCameraOpened();
        new_cameraId = mPreview.getCameraId();
        assertTrue(cameraId == new_cameraId);

        Camera camera = mPreview.getCamera();
        Camera.Parameters parameters = camera.getParameters();
        Log.d(TAG, "parameters flash mode: " + parameters.getFlashMode());
        assertTrue(parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH));
        String flash_value = mPreview.getCurrentFlashValue();
        Log.d(TAG, "flash_value: " + flash_value);
        assertTrue(flash_value.equals("flash_torch"));
    }*/

    public void testFlashStartup() throws InterruptedException {
        Log.d(TAG, "testFlashStartup");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        Log.d(TAG, "# switch to flash on");
        switchToFlashValue("flash_on");
        Log.d(TAG, "# restart");
        restart();

        Log.d(TAG, "# switch flash mode");
        // now switch to torch - the idea is that this is done while the camera is starting up
        // though note that sometimes we might not be quick enough here!
        // don't use switchToFlashValue here, it'll get confused due to the autofocus changing the parameters flash mode
        // update: now okay to use it, now we have the popup UI
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //clickView(flashButton);
        switchToFlashValue("flash_torch");

        //Camera camera = mPreview.getCamera();
        //Camera.Parameters parameters = camera.getParameters();
        //String flash_mode = mPreview.getCurrentFlashMode();
        String flash_value = mPreview.getCurrentFlashValue();
        Log.d(TAG, "# flash value is now: " + flash_value);
        Log.d(TAG, "# sleep");
        Thread.sleep(4000); // needs to be long enough for the autofocus to complete
        /*parameters = camera.getParameters();
        Log.d(TAG, "# parameters flash mode: " + parameters.getFlashMode());
        assertTrue(parameters.getFlashMode().equals(flash_mode));*/
        String camera_flash_value = mPreview.getCameraController().getFlashValue();
        Log.d(TAG, "# camera flash value: " + camera_flash_value);
        assertTrue(camera_flash_value.equals(flash_value));
    }

    /** Tests that flash remains on, with the startup focus flash hack.
     */
    public void testFlashStartup2() throws InterruptedException {
        Log.d(TAG, "testFlashStartup2");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        Log.d(TAG, "# switch to flash on");
        switchToFlashValue("flash_on");
        Log.d(TAG, "# restart");
        restart();
        Thread.sleep(3000);
        String flash_value = mPreview.getCameraController().getFlashValue();
        Log.d(TAG, "1 flash value is now: " + flash_value);
        assertTrue(flash_value.equals("flash_on"));

        switchToFocusValue("focus_mode_continuous_picture");
        restart();
        Thread.sleep(3000);
        flash_value = mPreview.getCameraController().getFlashValue();
        Log.d(TAG, "2 flash value is now: " + flash_value);
        assertTrue(flash_value.equals("flash_on"));
    }

    private void checkOptimalPreviewSize() {
        Log.d(TAG, "preview size: " + mPreview.getCameraController().getPreviewSize().width + ", " + mPreview.getCameraController().getPreviewSize().height);
        List<CameraController.Size> sizes = mPreview.getSupportedPreviewSizes();
        CameraController.Size best_size = mPreview.getOptimalPreviewSize(sizes);
        Log.d(TAG, "best size: " + best_size.width + ", " + best_size.height);
        assertTrue( best_size.width == mPreview.getCameraController().getPreviewSize().width );
        assertTrue( best_size.height == mPreview.getCameraController().getPreviewSize().height );
    }

    private void checkOptimalVideoPictureSize(double targetRatio) {
        // even the picture resolution should have same aspect ratio for video - otherwise have problems on Nexus 7 with Android 4.4.3
        Log.d(TAG, "video picture size: " + mPreview.getCameraController().getPictureSize().width + ", " + mPreview.getCameraController().getPictureSize().height);
        List<CameraController.Size> sizes = mPreview.getSupportedPictureSizes(false);
        CameraController.Size best_size = mPreview.getOptimalVideoPictureSize(sizes, targetRatio);
        Log.d(TAG, "best size: " + best_size.width + ", " + best_size.height);
        assertTrue( best_size.width == mPreview.getCameraController().getPictureSize().width );
        assertTrue( best_size.height == mPreview.getCameraController().getPictureSize().height );
    }

    private void checkSquareAspectRatio() {
        Log.d(TAG, "preview size: " + mPreview.getCameraController().getPreviewSize().width + ", " + mPreview.getCameraController().getPreviewSize().height);
        Log.d(TAG, "frame size: " + mPreview.getView().getWidth() + ", " + mPreview.getView().getHeight());
        double frame_aspect_ratio = ((double)mPreview.getView().getWidth()) / (double)mPreview.getView().getHeight();
        double preview_aspect_ratio = ((double)mPreview.getCameraController().getPreviewSize().width) / (double)mPreview.getCameraController().getPreviewSize().height;
        Log.d(TAG, "frame_aspect_ratio: " + frame_aspect_ratio);
        Log.d(TAG, "preview_aspect_ratio: " + preview_aspect_ratio);
        // we calculate etol like this, due to errors from rounding
        //double etol = 1.0f / Math.min((double)mPreview.getWidth(), (double)mPreview.getHeight()) + 1.0e-5;
        double etol = (double)mPreview.getView().getWidth() / (double)(mPreview.getView().getHeight() * (mPreview.getView().getHeight()-1) ) + 1.0e-5;
        assertTrue( Math.abs(frame_aspect_ratio - preview_aspect_ratio) <= etol );
    }

    /* Ensures that preview resolution is set as expected in non-WYSIWYG mode
     */
    public void testPreviewSize() {
        Log.d(TAG, "testPreviewSize");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_display");
        editor.apply();
        updateForSettings();

        Point display_size = new Point();
        {
            Display display = mActivity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
            Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        //double targetRatio = mPreview.getTargetRatioForPreview(display_size);
        double targetRatio = mPreview.getTargetRatio();
        double expTargetRatio = ((double)display_size.x) / (double)display_size.y;
        Log.d(TAG, "targetRatio: " + targetRatio);
        Log.d(TAG, "expTargetRatio: " + expTargetRatio);
        assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
        checkOptimalPreviewSize();
        checkSquareAspectRatio();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();

            //targetRatio = mPreview.getTargetRatioForPreview(display_size);
            targetRatio = mPreview.getTargetRatio();
            Log.d(TAG, "targetRatio: " + targetRatio);
            Log.d(TAG, "expTargetRatio: " + expTargetRatio);
            assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
            checkOptimalPreviewSize();
            checkSquareAspectRatio();
        }
    }

    /* Ensures that preview resolution is set as expected in WYSIWYG mode
     */
    public void testPreviewSizeWYSIWYG() {
        Log.d(TAG, "testPreviewSizeWYSIWYG");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_wysiwyg");
        editor.apply();
        updateForSettings();

        Point display_size = new Point();
        {
            Display display = mActivity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
            Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        CameraController.Size picture_size = mPreview.getCameraController().getPictureSize();
        CameraController.Size preview_size = mPreview.getCameraController().getPreviewSize();
        //double targetRatio = mPreview.getTargetRatioForPreview(display_size);
        double targetRatio = mPreview.getTargetRatio();
        double expTargetRatio = ((double)picture_size.width) / (double)picture_size.height;
        double previewRatio = ((double)preview_size.width) / (double)preview_size.height;
        assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
        assertTrue( Math.abs(previewRatio - expTargetRatio) <= 1.0e-5 );
        checkOptimalPreviewSize();
        checkSquareAspectRatio();

        Log.d(TAG, "switch to video");
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        VideoProfile profile = mPreview.getVideoProfile();
        CameraController.Size video_preview_size = mPreview.getCameraController().getPreviewSize();
        //targetRatio = mPreview.getTargetRatioForPreview(display_size);
        targetRatio = mPreview.getTargetRatio();
        expTargetRatio = ((double)profile.videoFrameWidth) / (double)profile.videoFrameHeight;
        previewRatio = ((double)video_preview_size.width) / (double)video_preview_size.height;
        assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
        assertTrue( Math.abs(previewRatio - expTargetRatio) <= 1.0e-5 );
        checkOptimalPreviewSize();
        checkSquareAspectRatio();
        checkOptimalVideoPictureSize(expTargetRatio);

        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(!mPreview.isVideo());
        CameraController.Size new_picture_size = mPreview.getCameraController().getPictureSize();
        CameraController.Size new_preview_size = mPreview.getCameraController().getPreviewSize();
        Log.d(TAG, "picture_size: " + picture_size.width + " x " + picture_size.height);
        Log.d(TAG, "new_picture_size: " + new_picture_size.width + " x " + new_picture_size.height);
        Log.d(TAG, "preview_size: " + preview_size.width + " x " + preview_size.height);
        Log.d(TAG, "new_preview_size: " + new_preview_size.width + " x " + new_preview_size.height);
        assertTrue(new_picture_size.equals(picture_size));
        assertTrue(new_preview_size.equals(preview_size));

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();

            picture_size = mPreview.getCameraController().getPictureSize();
            preview_size = mPreview.getCameraController().getPreviewSize();
            //targetRatio = mPreview.getTargetRatioForPreview(display_size);
            targetRatio = mPreview.getTargetRatio();
            expTargetRatio = ((double)picture_size.width) / (double)picture_size.height;
            previewRatio = ((double)preview_size.width) / (double)preview_size.height;
            assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
            assertTrue( Math.abs(previewRatio - expTargetRatio) <= 1.0e-5 );
            checkOptimalPreviewSize();
            checkSquareAspectRatio();

            Log.d(TAG, "switch to video again");
            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.isVideo());
            profile = mPreview.getVideoProfile();
            video_preview_size = mPreview.getCameraController().getPreviewSize();
            //targetRatio = mPreview.getTargetRatioForPreview(display_size);
            targetRatio = mPreview.getTargetRatio();
            expTargetRatio = ((double)profile.videoFrameWidth) / (double)profile.videoFrameHeight;
            previewRatio = ((double)video_preview_size.width) / (double)video_preview_size.height;
            assertTrue( Math.abs(targetRatio - expTargetRatio) <= 1.0e-5 );
            assertTrue( Math.abs(previewRatio - expTargetRatio) <= 1.0e-5 );
            checkOptimalPreviewSize();
            checkSquareAspectRatio();
            checkOptimalVideoPictureSize(expTargetRatio);

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(!mPreview.isVideo());
            new_picture_size = mPreview.getCameraController().getPictureSize();
            new_preview_size = mPreview.getCameraController().getPreviewSize();
            assertTrue(new_picture_size.equals(picture_size));
            assertTrue(new_preview_size.equals(preview_size));
        }
    }

    /* Tests camera error handling.
     */
    public void testOnError() {
        Log.d(TAG, "testOnError");
        setToDefault();

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "onError...");
                mPreview.getCameraController().onError();
            }
        });
        this.getInstrumentation().waitForIdleSync();
        assertTrue( mPreview.getCameraController() == null );
    }

    /* Various tests for auto-focus.
     */
    public void testAutoFocus() throws InterruptedException {
        Log.d(TAG, "testAutoFocus");
        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }
        //int saved_count = mPreview.count_cameraAutoFocus;
        int saved_count = 0; // set to 0 rather than count_cameraAutoFocus, as on Galaxy Nexus, it can happen that startup autofocus has already occurred by the time we reach here
        Log.d(TAG, "saved_count: " + saved_count);
        switchToFocusValue("focus_mode_auto");

        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        Thread.sleep(2000); // wait until autofocus startup
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        // touch to auto-focus with focus area
        saved_count = mPreview.count_cameraAutoFocus;
        Log.d(TAG, "about to touch preview to auto-focus");
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "done touch preview to auto-focus");
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        saved_count = mPreview.count_cameraAutoFocus;
        // test selecting same mode doesn't set off an autofocus or reset the focus area
        switchToFocusValue("focus_mode_auto");
        Log.d(TAG, "3 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        if( mPreview.getSupportedFocusValues().contains("focus_mode_macro") ) {
            saved_count = mPreview.count_cameraAutoFocus;
            // test switching mode sets off an autofocus, and resets the focus area
            switchToFocusValue("focus_mode_macro");
            Log.d(TAG, "4 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
        }

        saved_count = mPreview.count_cameraAutoFocus;
        // switching to focus locked shouldn't set off an autofocus
        switchToFocusValue("focus_mode_locked");
        Log.d(TAG, "5 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count);

        saved_count = mPreview.count_cameraAutoFocus;
        // touch to focus should autofocus
        Thread.sleep(2000);
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "6 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);

        saved_count = mPreview.count_cameraAutoFocus;
        // switching to focus continuous shouldn't set off an autofocus
        switchToFocusValue("focus_mode_continuous_picture");
        Log.d(TAG, "7 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(!mPreview.isFocusWaiting());
        assertTrue(mPreview.count_cameraAutoFocus == saved_count);

        // but touch to focus should
        Thread.sleep(2000);
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "8 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        switchToFocusValue("focus_mode_locked"); // change to a mode that isn't auto (so that the first iteration of the next loop will set of an autofocus, due to changing the focus mode)
        List<String> supported_focus_values = mPreview.getSupportedFocusValues();
        assertTrue( supported_focus_values != null );
        assertTrue( supported_focus_values.size() > 1 );
        for(String supported_focus_value : supported_focus_values) {
            Log.d(TAG, "supported_focus_value: " + supported_focus_value);
            saved_count = mPreview.count_cameraAutoFocus;
            Log.d(TAG, "saved autofocus count: " + saved_count);
            //View focusModeButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
            //clickView(focusModeButton);
            switchToFocusValue(supported_focus_value);
            // test that switching focus mode resets the focus area
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
            // test that switching focus mode sets off an autofocus in focus auto or macro mode
            String focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "changed focus_value to: "+ focus_value);
            Log.d(TAG, "count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
            if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro") ) {
                assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
            }
            else {
                assertTrue(!mPreview.isFocusWaiting());
                assertTrue(mPreview.count_cameraAutoFocus == saved_count);
            }

            // test that touch to auto-focus region only works in focus auto, macro or continuous mode, and that we set off an autofocus for focus auto and macro
            // test that touch to set metering area works in any focus mode
            saved_count = mPreview.count_cameraAutoFocus;
            Thread.sleep(2000);
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            Log.d(TAG, "count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
            if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro") || focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") ) {
                if( focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") ) {
                    assertTrue(!mPreview.isFocusWaiting());
                    assertTrue(mPreview.count_cameraAutoFocus == saved_count);
                }
                else {
                    assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
                }
                assertTrue(mPreview.hasFocusArea());
                assertTrue(mPreview.getCameraController().getFocusAreas() != null);
                assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
                assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
                assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
            }
            else {
                assertTrue(mPreview.count_cameraAutoFocus == saved_count);
                assertTrue(!mPreview.hasFocusArea());
                assertTrue(mPreview.getCameraController().getFocusAreas() == null);
                assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
                assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
            }
            // also check that focus mode is unchanged
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
            if( focus_value.equals("focus_mode_auto") ) {
                break;
            }
        }
    }

    /* Test we do startup autofocus as expected depending on focus mode.
     */
    public void testStartupAutoFocus() throws InterruptedException {
        Log.d(TAG, "testStartupAutoFocus");
        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }
        //int saved_count = mPreview.count_cameraAutoFocus;
        int saved_count = 0; // set to 0 rather than count_cameraAutoFocus, as on Galaxy Nexus, it can happen that startup autofocus has already occurred by the time we reach here
        Log.d(TAG, "saved_count: " + saved_count);
        switchToFocusValue("focus_mode_auto");

        Thread.sleep(1000);
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);

        restart();
        //saved_count = mPreview.count_cameraAutoFocus;
        saved_count = 0;
        Log.d(TAG, "saved_count: " + saved_count);
        Thread.sleep(1000);
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);

        if( mPreview.getSupportedFocusValues().contains("focus_mode_infinity") ) {
            switchToFocusValue("focus_mode_infinity");
            restart();
            //saved_count = mPreview.count_cameraAutoFocus;
            saved_count = 0;
            Log.d(TAG, "saved_count: " + saved_count);
            Thread.sleep(1000);
            Log.d(TAG, "3 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count);
        }

        if( mPreview.getSupportedFocusValues().contains("focus_mode_macro") ) {
            switchToFocusValue("focus_mode_macro");
            restart();
            //saved_count = mPreview.count_cameraAutoFocus;
            saved_count = 0;
            Log.d(TAG, "saved_count: " + saved_count);
            Thread.sleep(1000);
            Log.d(TAG, "4 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        }

        if( mPreview.getSupportedFocusValues().contains("focus_mode_locked") ) {
            switchToFocusValue("focus_mode_locked");
            restart();
            //saved_count = mPreview.count_cameraAutoFocus;
            saved_count = 0;
            Log.d(TAG, "saved_count: " + saved_count);
            Thread.sleep(1000);
            Log.d(TAG, "5 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        }

        if( mPreview.getSupportedFocusValues().contains("focus_mode_continuous_picture") ) {
            switchToFocusValue("focus_mode_continuous_picture");
            restart();
            //saved_count = mPreview.count_cameraAutoFocus;
            saved_count = 0;
            Log.d(TAG, "saved_count: " + saved_count);
            Thread.sleep(1000);
            Log.d(TAG, "6 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus + " compare to saved_count: " + saved_count);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count);
        }
    }

    /* Test doing touch to auto-focus region by swiping to all four corners works okay.
     */
    public void testAutoFocusCorners() {
        Log.d(TAG, "testAutoFocusCorners");
        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        int [] gui_location = new int[2];
        mPreview.getView().getLocationOnScreen(gui_location);
        final int step_dist_c = 2;
        final float scale = mActivity.getResources().getDisplayMetrics().density;
        final int large_step_dist_c = (int) (80 * scale + 0.5f); // convert dps to pixels
        final int step_count_c = 10;
        int width = mPreview.getView().getWidth();
        int height = mPreview.getView().getHeight();
        Log.d(TAG, "preview size: " + width + " x " + height);

        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        Log.d(TAG, "top-left");
        TouchUtils.drag(MainActivityTest.this, gui_location[0] + step_dist_c, gui_location[0], gui_location[1] + step_dist_c, gui_location[1], step_count_c);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        mPreview.clearFocusAreas();
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        // do larger step at top-right, due to conflicting with Settings button
        // but we now ignore swipes - so we now test for that instead
        Log.d(TAG, "top-right");
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+width-1-large_step_dist_c, gui_location[0]+width-1, gui_location[1]+large_step_dist_c, gui_location[1], step_count_c);
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        Log.d(TAG, "bottom-left");
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+step_dist_c, gui_location[0], gui_location[1]+height-1-step_dist_c, gui_location[1]+height-1, step_count_c);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        mPreview.clearFocusAreas();
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        // skip bottom right, conflicts with zoom on various devices
    }

    /* Test face detection, and that we don't get the focus/metering areas set.
     */
    public void testFaceDetection() throws InterruptedException {
        Log.d(TAG, "testFaceDetection");
        setToDefault();

        View faceDetectionButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.face_detection);
        assertTrue( faceDetectionButton.getVisibility() == View.GONE );

        if( !mPreview.supportsFaceDetection() ) {
            Log.d(TAG, "face detection not supported");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.FaceDetectionPreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( faceDetectionButton.getVisibility() == View.GONE );

        int saved_count;
        Log.d(TAG, "0 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        /*
        // autofocus shouldn't be immediately, but after a delay
        saved_count = mPreview.count_cameraAutoFocus;
        Thread.sleep(1000);
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        */
        Thread.sleep(2000);
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
        // check face detection already started
        assertFalse( mPreview.getCameraController().startFaceDetection() );

        // touch to auto-focus with focus area
        saved_count = mPreview.count_cameraAutoFocus;
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1); // for autofocus
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            // check face detection already started
            assertFalse( mPreview.getCameraController().startFaceDetection() );

            // return to back camera
            switchToCamera(cameraId);
        }

        // test show face detection icon

        editor.putBoolean(PreferenceKeys.ShowFaceDetectionPreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( faceDetectionButton.getVisibility() == View.VISIBLE );
        assertTrue( faceDetectionButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.face_detection_disable) ) );

        // check face detection already started
        assertFalse( mPreview.getCameraController().startFaceDetection() );

        // restart and check still enabled
        restart();
        faceDetectionButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.face_detection);

        assertTrue( faceDetectionButton.getVisibility() == View.VISIBLE );
        assertTrue( faceDetectionButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.face_detection_disable) ) );

        clickView(faceDetectionButton);
        waitUntilCameraOpened();
        assertFalse( settings.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false) );
        assertTrue( faceDetectionButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.face_detection_enable) ) );

        // check face detection not already started
        assertTrue( mPreview.getCameraController().startFaceDetection() );
    }

    private void subTestPopupButtonAvailability(String test_key, String option, boolean expected) {
        Log.d(TAG, "test_key: "+ test_key);
        Log.d(TAG, "option: "+ option);
        Log.d(TAG, "expected?: "+ expected);
        View button = mActivity.getUIButton(test_key + "_" + option);
        if( expected ) {
            boolean is_video = mPreview.isVideo();
            if( option.equals("focus_mode_continuous_picture") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else if( option.equals("focus_mode_continuous_video") && !is_video ) {
                // not allowed in picture mode
                assertTrue(button == null);
            }
            else if( option.equals("flash_auto") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else if( option.equals("flash_on") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else if( option.equals("flash_red_eye") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else if( option.equals("flash_frontscreen_auto") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else if( option.equals("flash_frontscreen_on") && is_video ) {
                // not allowed in video mode
                assertTrue(button == null);
            }
            else {
                assertTrue(button != null);
            }
        }
        else {
            Log.d(TAG, "option? "+ option);
            Log.d(TAG, "button? "+ button);
            assertTrue(button == null);
        }
    }

    private void subTestPopupButtonAvailability(String test_key, String option, List<String> options) {
        subTestPopupButtonAvailability(test_key, option, options != null && options.contains(option));
    }

    private void subTestPopupButtonAvailability(String option, boolean expected) {
        View button = mActivity.getUIButton(option);
        if( expected ) {
            assertTrue(button != null);
        }
        else {
            assertTrue(button == null);
        }
    }

    private void subTestPopupButtonAvailability() {
        List<String> supported_flash_values = mPreview.getSupportedFlashValues();
        Log.d(TAG, "supported_flash_values: "+ supported_flash_values);
        subTestPopupButtonAvailability("TEST_FLASH", "flash_off", supported_flash_values);
        subTestPopupButtonAvailability("TEST_FLASH", "flash_auto", supported_flash_values);
        subTestPopupButtonAvailability("TEST_FLASH", "flash_on", supported_flash_values);
        subTestPopupButtonAvailability("TEST_FLASH", "flash_torch", supported_flash_values);
        subTestPopupButtonAvailability("TEST_FLASH", "flash_red_eye", supported_flash_values);
        List<String> supported_focus_values = mPreview.getSupportedFocusValues();
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_auto", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_locked", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_infinity", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_macro", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_fixed", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_edof", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_continuous_picture", supported_focus_values);
        subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_continuous_video", supported_focus_values);
        subTestPopupButtonAvailability("TEST_WHITE_BALANCE", mPreview.getSupportedWhiteBalances() != null);
        subTestPopupButtonAvailability("TEST_SCENE_MODE", mPreview.getSupportedSceneModes() != null);
        subTestPopupButtonAvailability("TEST_COLOR_EFFECT", mPreview.getSupportedColorEffects() != null);
    }

    private void subTestFocusFlashAvailability() {
        //View focusModeButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        /*boolean focus_visible = focusModeButton.getVisibility() == View.VISIBLE;
        Log.d(TAG, "focus_visible? "+ focus_visible);
        boolean flash_visible = flashButton.getVisibility() == View.VISIBLE;
        Log.d(TAG, "flash_visible? "+ flash_visible);*/
        boolean exposure_visible = exposureButton.getVisibility() == View.VISIBLE;
        Log.d(TAG, "exposure_visible? "+ exposure_visible);
        boolean exposure_lock_visible = exposureLockButton.getVisibility() == View.VISIBLE;
        Log.d(TAG, "exposure_lock_visible? "+ exposure_lock_visible);
        boolean popup_visible = popupButton.getVisibility() == View.VISIBLE;
        Log.d(TAG, "popup_visible? "+ popup_visible);
        boolean has_focus = mPreview.supportsFocus();
        Log.d(TAG, "has_focus? "+ has_focus);
        boolean has_flash = mPreview.supportsFlash();
        Log.d(TAG, "has_flash? "+ has_flash);
        boolean has_exposure = mPreview.supportsExposures();
        Log.d(TAG, "has_exposure? "+ has_exposure);
        boolean has_exposure_lock = mPreview.supportsExposureLock();
        Log.d(TAG, "has_exposure_lock? "+ has_exposure_lock);
        //assertTrue(has_focus == focus_visible);
        //assertTrue(has_flash == flash_visible);
        assertTrue(has_exposure == exposure_visible);
        assertTrue(has_exposure_lock == exposure_lock_visible);
        assertTrue(popup_visible);

        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }
        subTestPopupButtonAvailability();
    }

    /*
     * For each camera, test that visibility of flash and focus etc buttons matches the availability of those camera parameters.
     * Added to guard against a bug where on Nexus 7, the flash and focus buttons were made visible by showGUI, even though they aren't supported by Nexus 7 front camera.
     */
    public void testFocusFlashAvailability() {
        Log.d(TAG, "testFocusFlashAvailability");
        setToDefault();

        subTestFocusFlashAvailability();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            Log.d(TAG, "cameraId? "+ cameraId);
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            //mActivity.clickedSwitchCamera(switchCameraButton);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            int new_cameraId = mPreview.getCameraId();
            Log.d(TAG, "new_cameraId? "+ new_cameraId);
            assertTrue(cameraId != new_cameraId);

            subTestFocusFlashAvailability();
        }
    }

    private void subTestPopupButtonContentDescription(int title_id, String test_key, boolean next, boolean expected) {
        Log.d(TAG, "subTestPopupButtonContentDescription");
        String title = mActivity.getResources().getString(title_id);
        Log.d(TAG, "title: " + title);
        Log.d(TAG, "test_key: " + test_key);
        Log.d(TAG, "next: " + next);
        Log.d(TAG, "expected: " + expected);
        View main_button = mActivity.getUIButton(test_key);
        assertTrue(main_button != null);
        View button = mActivity.getUIButton(test_key + (next ? "_NEXT" : "_PREV"));
        if( expected ) {
            assertTrue(button != null);
        }
        if( button != null ) {
            assertTrue(button.getContentDescription() != null);
            String content_description = button.getContentDescription().toString();
            assertTrue(content_description.length() > 0);
            String next_string = mActivity.getResources().getString(next ? net.sourceforge.opencamera.R.string.next : net.sourceforge.opencamera.R.string.previous);
            assertTrue(next_string.length() > 0);
            assertTrue(content_description.startsWith(next_string + " " + title));
        }
        else {
            Log.d(TAG, "no button found");
        }
    }

    /* Tests switching to/from video mode, for front and back cameras, and tests the focus mode changes as expected.
     * If this test fails with nullpointerexception on preview.getCameraController() after switching to video mode, check
     * that record audio permission is granted!
     * Also tests content descriptions of <> buttons on the popup menu.
     */
    public void testSwitchVideo() {
        Log.d(TAG, "testSwitchVideo");

        setToDefault();
        assertTrue(!mPreview.isVideo());
        String photo_focus_value = mPreview.getCameraController().getFocusValue();

        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        clickView(popupButton);
        Log.d(TAG, "wait for popup to open");
        while( !mActivity.popupIsOpen() ) {
        }
        Log.d(TAG, "popup is now open");

        // test popup buttons for photo mode:
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_resolution, "PHOTO_RESOLUTIONS", false, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_resolution, "PHOTO_RESOLUTIONS", true, false);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", false, false);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", true, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", false, false);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", true, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", false, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", true, true);

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "video focus_value: "+ focus_value);
        if( mPreview.supportsFocus() ) {
            assertTrue(focus_value.equals("focus_mode_continuous_video"));
        }

        // test popup buttons for video mode:
        clickView(popupButton);
        Log.d(TAG, "wait for popup to open");
        while( !mActivity.popupIsOpen() ) {
        }
        Log.d(TAG, "popup is now open");
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.video_quality, "VIDEO_RESOLUTIONS", false, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.video_quality, "VIDEO_RESOLUTIONS", true, false);
        if( mActivity.getApplicationInterface().getSupportedVideoCaptureRates().size() > 1 ) {
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_video_capture_rate, "VIDEOCAPTURERATE", false, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_video_capture_rate, "VIDEOCAPTURERATE", true, true);
        }
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", false, false);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", true, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", false, false);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", true, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", false, true);
        subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", true, true);

        int saved_count = mPreview.count_cameraAutoFocus;
        Log.d(TAG, "0 count_cameraAutoFocus: " + saved_count);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(!mPreview.isVideo());
        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "picture focus_value: "+ focus_value);
        if( mPreview.supportsFocus() ) {
            assertTrue(focus_value.equals(photo_focus_value));
            // check that this doesn't cause an autofocus
            assertTrue(!mPreview.isFocusWaiting());
            Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count);
        }

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            int new_cameraId = mPreview.getCameraId();
            assertTrue(cameraId != new_cameraId);
            focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "front picture focus_value: "+ focus_value);
            if( mPreview.supportsFocus() ) {
                assertTrue(focus_value.equals(photo_focus_value));
            }

            // test popup buttons for photo mode:
            clickView(popupButton);
            Log.d(TAG, "wait for popup to open");
            while( !mActivity.popupIsOpen() ) {
            }
            Log.d(TAG, "popup is now open");
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_resolution, "PHOTO_RESOLUTIONS", false, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_resolution, "PHOTO_RESOLUTIONS", true, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", false, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", true, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", false, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", true, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", false, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", true, true);

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.isVideo());
            focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "front video focus_value: "+ focus_value);
            if( mPreview.supportsFocus() ) {
                assertTrue(focus_value.equals("focus_mode_continuous_video"));
            }

            // test popup buttons for video mode:
            clickView(popupButton);
            Log.d(TAG, "wait for popup to open");
            while( !mActivity.popupIsOpen() ) {
            }
            Log.d(TAG, "popup is now open");
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.video_quality, "VIDEO_RESOLUTIONS", false, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.video_quality, "VIDEO_RESOLUTIONS", true, false);
            if( mActivity.getApplicationInterface().getSupportedVideoCaptureRates().size() > 1 ) {
                subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_video_capture_rate, "VIDEOCAPTURERATE", false, false);
                subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_video_capture_rate, "VIDEOCAPTURERATE", true, true);
            }
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", false, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_timer, "TIMER", true, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", false, false);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.preference_burst_mode, "REPEAT_MODE", true, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", false, true);
            subTestPopupButtonContentDescription(net.sourceforge.opencamera.R.string.grid, "GRID", true, true);

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(!mPreview.isVideo());
            focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "front picture focus_value: "+ focus_value);
            if( mPreview.supportsFocus() ) {
                assertTrue(focus_value.equals(photo_focus_value));
            }

            // now switch back
            switchToCamera(cameraId);
        }

        if( mPreview.supportsFocus() ) {
            // now test we remember the focus mode for photo and video

            switchToFocusValue("focus_mode_continuous_picture");

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.isVideo());
            focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "video focus_value: "+ focus_value);
            assertTrue(focus_value.equals("focus_mode_continuous_video"));

            String non_default_focus = getNonDefaultFocus();
            switchToFocusValue(non_default_focus);

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(!mPreview.isVideo());
            focus_value = mPreview.getCameraController().getFocusValue();
            Log.d(TAG, "picture focus_value: "+ focus_value);
            assertTrue(focus_value.equals("focus_mode_continuous_picture"));

            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.isVideo());
            focus_value = mPreview.getCameraController().getFocusValue();
            if( non_default_focus.equals("focus_mode_infinity") && focus_value.equals("focus_mode_manual2") ) {
                // for Camera2, focus_mode_infinity is represented as focus_mode_manual2
                focus_value = "focus_mode_infinity";
            }
            Log.d(TAG, "video focus_value: "+ focus_value);
            assertEquals(non_default_focus, focus_value);
        }
    }

    /* Tests continuous picture focus, including switching to video and back.
     * Tends to fail on Galaxy Nexus, where the continuous picture focusing doesn't happen too often.
     */
    public void testContinuousPictureFocus() throws InterruptedException {
        Log.d(TAG, "testContinuousPictureFocus");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        // first switch to auto-focus (if we're already in continuous picture mode, we might have already done the continuous focus moving
        switchToFocusValue("focus_mode_auto");
        pauseAndResume();
        switchToFocusValue("focus_mode_continuous_picture");

        // check continuous focus is working
        int saved_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        Thread.sleep(2000); // n.b., Galaxy S10e seems to need longer delay than other devices for continuous focus to occur
        int new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        Log.d(TAG, "count_cameraContinuousFocusMoving compare saved: "+ saved_count_cameraContinuousFocusMoving + " to new: " + new_count_cameraContinuousFocusMoving);
        assertTrue( mPreview.getCameraController().test_af_state_null_focus == 0 );
        assertTrue( new_count_cameraContinuousFocusMoving > saved_count_cameraContinuousFocusMoving );

        // switch to video
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "video focus_value: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_continuous_video"));

        // switch to photo
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "video focus_value: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_continuous_picture"));

        // check continuous focus is working
        saved_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        Thread.sleep(3000);
        new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        Log.d(TAG, "count_cameraContinuousFocusMoving compare saved: "+ saved_count_cameraContinuousFocusMoving + " to new: " + new_count_cameraContinuousFocusMoving);
        assertTrue( mPreview.getCameraController().test_af_state_null_focus == 0 );
        assertTrue( new_count_cameraContinuousFocusMoving > saved_count_cameraContinuousFocusMoving );
    }

    /* Tests everything works okay if starting in continuous video focus mode when in photo mode, including opening popup, and switching to video and back.
     * This shouldn't be possible normal, but could happen if a user is upgrading from version 1.28 or earlier, to version 1.29 or later.
     */
    public void testContinuousVideoFocusForPhoto() throws InterruptedException {
        Log.d(TAG, "testContinuousVideoFocusForPhoto");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getFocusPreferenceKey(mPreview.getCameraId(), false), "focus_mode_continuous_video");
        editor.apply();
        restart();

        Thread.sleep(1000);

        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        Thread.sleep(1000);

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
    }

    /** Return the number of files in the supplied folder. 0 will be returned if the folder doesn't
     *  exist.
     */
    private int getNFiles(File folder) {
        File [] files = folder.listFiles();
        Log.d(TAG, "getNFiles: " + folder + " has: " + Arrays.toString(files));
        return files == null ? 0 : files.length;
    }

    private void subTestContinuousPictureFocusRepeat() throws InterruptedException {
        Log.d(TAG, "subTestContinuousPictureFocusRepeat");

        if( !mPreview.supportsFocus() ) {
            return;
        }

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getRepeatModePreferenceKey(), "3");
            editor.apply();
        }
        switchToFocusValue("focus_mode_continuous_picture");

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");
        assertTrue(!mPreview.isOnTimer());

        // wait until photos taken
        // wait, and test that we've taken the photos by then
        long time_s = System.currentTimeMillis();
        while( mPreview.count_cameraTakePicture < 3 ) {
            assertTrue( System.currentTimeMillis() - time_s < 20000 );
        }
        Thread.sleep(2000); // allow pictures to save
        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        Log.d(TAG, "count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==3);
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 3);
    }

    /* Tests continuous picture focus with repeat mode.
     */
    public void testContinuousPictureFocusRepeat() throws InterruptedException {
        Log.d(TAG, "testContinuousPictureFocusRepeat");

        setToDefault();

        subTestContinuousPictureFocusRepeat();
    }

    /* As testContinuousPictureFocusRepeat, but with test_wait_capture_result flag set.
     */
    public void testContinuousPictureFocusRepeatWaitCaptureResult() throws InterruptedException {
        Log.d(TAG, "testContinuousPictureFocusRepeatWaitCaptureResult");

        setToDefault();

        mPreview.getCameraController().test_wait_capture_result = true;
        subTestContinuousPictureFocusRepeat();
    }

    /* Test for continuous picture photo mode.
     * Touch, wait 8s, check that continuous focus mode has resumed, then take photo.
     */
    public void testContinuousPicture1() throws InterruptedException {
        Log.d(TAG, "testContinuousPicture1");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_continuous_picture");

        String focus_value = "focus_mode_continuous_picture";
        String focus_value_ui = "focus_mode_continuous_picture";

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        Thread.sleep(1000);
        assertTrue(mPreview.count_cameraTakePicture==0);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        Log.d(TAG, "about to click preview for autofocus");
        int saved_count = mPreview.count_cameraAutoFocus;
        Thread.sleep(1000); // needed for Galaxy S10e for the touch to register
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.getCurrentFocusValue().equals("focus_mode_continuous_picture"));
        assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto"));
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        if( focus_value.equals("focus_mode_continuous_picture") )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        Thread.sleep(8000);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        assertTrue(mPreview.count_cameraTakePicture==1);
        mActivity.waitUntilImageQueueEmpty();

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);
    }

    /* Test for continuous picture photo mode.
     * Touch, wait 1s, check that continuous focus mode hasn't resumed, then take photo, then check continuous focus mode has resumed.
     */
    public void testContinuousPicture2() throws InterruptedException {
        Log.d(TAG, "testContinuousPicture1");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_continuous_picture");

        String focus_value = "focus_mode_continuous_picture";
        String focus_value_ui = "focus_mode_continuous_picture";

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        Thread.sleep(1000);
        assertTrue(mPreview.count_cameraTakePicture==0);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        Log.d(TAG, "about to click preview for autofocus");
        int saved_count = mPreview.count_cameraAutoFocus;
        Thread.sleep(1000); // needed for Galaxy S10e for the touch to register
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.getCurrentFocusValue().equals("focus_mode_continuous_picture"));
        assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto"));
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        if( focus_value.equals("focus_mode_continuous_picture") )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        int saved_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;

        Thread.sleep(1000);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        if( focus_value.equals("focus_mode_continuous_picture") )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        int new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        assertTrue(mPreview.count_cameraTakePicture==1);
        Log.d(TAG, "3 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        mActivity.waitUntilImageQueueEmpty();

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);
    }

    /* Test for continuous picture photo mode.
     * Touch repeatedly with 1s delays for 8 times, make sure continuous focus mode hasn't resumed.
     * Then wait 5s, and check continuous focus mode has resumed.
     */
    public void testContinuousPictureRepeatTouch() throws InterruptedException {
        Log.d(TAG, "testContinuousPictureRepeatTouch");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_continuous_picture");

        String focus_value = "focus_mode_continuous_picture";
        String focus_value_ui = "focus_mode_continuous_picture";

        Thread.sleep(1000);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        for(int i=0;i<8;i++) {
            Log.d(TAG, "about to click preview for autofocus: " + i);
            int saved_count = mPreview.count_cameraAutoFocus;
            Thread.sleep(1000); // needed for Galaxy S10e for the touch to register
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
            assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
            int saved_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
            Thread.sleep(1000);

            assertTrue(mPreview.getCurrentFocusValue().equals("focus_mode_continuous_picture"));
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto"));
            assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
            if( focus_value.equals("focus_mode_continuous_picture") )
                assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
            else
                assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
            int new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
            assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );
        }

        int saved_count = mPreview.count_cameraAutoFocus;
        Thread.sleep(6000);
        assertEquals(focus_value_ui, mPreview.getCurrentFocusValue());
        assertEquals(focus_value, mPreview.getCameraController().getFocusValue());
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count);
    }

    /* Test for continuous picture photo mode.
     * Touch, then after 1s switch to focus auto in UI, wait 8s, ensure still in autofocus mode.
     */
    public void testContinuousPictureSwitchAuto() throws InterruptedException {
        Log.d(TAG, "testContinuousPictureSwitchAuto");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_continuous_picture");

        String focus_value = "focus_mode_continuous_picture";
        String focus_value_ui = "focus_mode_continuous_picture";

        Thread.sleep(1000);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));

        Log.d(TAG, "about to click preview for autofocus");
        int saved_count = mPreview.count_cameraAutoFocus;
        Thread.sleep(2000); // needed for Galaxy S10e for the touch to register
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        int saved_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        Thread.sleep(1000);

        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        if( focus_value.equals("focus_mode_continuous_picture") )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        int new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );

        Thread.sleep(1000);
        assertTrue(mPreview.getCurrentFocusValue().equals(focus_value_ui));
        if( focus_value.equals("focus_mode_continuous_picture") )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );

        switchToFocusValue("focus_mode_auto");
        assertTrue(mPreview.getCurrentFocusValue().equals("focus_mode_auto"));
        assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto"));
        new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );

        Thread.sleep(8000);
        assertTrue(mPreview.getCurrentFocusValue().equals("focus_mode_auto"));
        assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto"));
        new_count_cameraContinuousFocusMoving = mPreview.count_cameraContinuousFocusMoving;
        assertTrue( new_count_cameraContinuousFocusMoving == saved_count_cameraContinuousFocusMoving );
    }

    /* Test for taking HDR photo then going to background, also tests notifications.
     */
    public void testPhotoBackgroundHDR() throws InterruptedException {
        Log.d(TAG, "testPhotoBackgroundHDR");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true); // also set auto-stabilise so we have a photo that takes longer to process
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        Thread.sleep(1000);
        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        // go to background after a short pause
        Thread.sleep(500);
        assertFalse(mActivity.testHasNotification());
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "pause...");
                getInstrumentation().callActivityOnPause(mActivity);
                Log.d(TAG, "done pause");
            }
        });
        this.getInstrumentation().waitForIdleSync();

        assertTrue(mPreview.count_cameraTakePicture==1);
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            assertTrue(mActivity.testHasNotification());
        }
        mActivity.waitUntilImageQueueEmpty();
        this.getInstrumentation().waitForIdleSync();
        assertFalse(mActivity.testHasNotification());

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);
    }

    /* Start in photo mode with auto focus:
     * - go to video mode
     * - then switch to front camera
     * - then switch back to photo mode
     * - then go to back camera
     * Check focus mode has returned to auto.
     * This test is important when front camera doesn't support focus modes, but back camera does - we won't be able to reset to auto focus for the front camera, but need to do so when returning to back camera
     */
    public void testFocusSwitchVideoSwitchCameras() {
        Log.d(TAG, "testFocusSwitchVideoSwitchCameras");

        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        if( !mPreview.supportsFocus() ) {
            return;
        }

        int cameraId = mPreview.getCameraId();

        switchToFocusValue("focus_mode_auto");

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "video focus_value: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_continuous_video"));

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();
        // camera becomes invalid when switching cameras
        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "front video focus_value: "+ focus_value);
        // don't care when focus mode is for front camera (focus may not be supported for front camera)

        clickView(switchVideoButton);
        waitUntilCameraOpened();
        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "front focus_value: "+ focus_value);
        // don't care when focus mode is for front camera (focus may not be supported for front camera)

        switchToCamera(cameraId);

        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "end focus_value: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_auto"));
    }

    /* Start in photo mode with non-default focus mode:
     * - switch to front camera
     * - switch to back camera
     * Check focus mode is still what we set.
     * This test is important when front camera doesn't support focus modes, but back camera does - need to remain in same focus mode for the back camera.
     */
    public void testFocusRemainMacroSwitchCamera() {
        Log.d(TAG, "testFocusRemainMacroSwitchCamera");

        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        if( !mPreview.supportsFocus() ) {
            return;
        }

        String non_default_focus_mode = getNonDefaultFocus();
        switchToFocusValue(non_default_focus_mode);

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        // n.b., switch to front then to back
        int cameraId = mPreview.getCameraId();
        clickView(switchCameraButton);
        waitUntilCameraOpened();
        switchToCamera(cameraId);

        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value: "+ focus_value);
        assertTrue(focus_value.equals(non_default_focus_mode));
    }

    /* Start in photo mode with focus auto:
     * - switch to video mode
     * - switch to non-default focus mode
     * - switch to picture mode
     * Check focus mode is now auto.
     * As of 1.26, we now remember the focus mode for photos.
     */
    public void testFocusRemainMacroSwitchPhoto() {
        Log.d(TAG, "testFocusRemainMacroSwitchPhoto");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_auto");

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value after switching to video mode: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_continuous_video"));

        String non_default_focus_mode = getNonDefaultFocus();
        switchToFocusValue(non_default_focus_mode);

        clickView(switchVideoButton);
        waitUntilCameraOpened();

        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value after switching to picture mode: " + focus_value);
        assertTrue(focus_value.equals("focus_mode_auto"));
    }

    /* Start in photo mode with focus auto:
     * - switch to non-default focus mode
     * - switch to video mode
     * - switch to picture mode
     * Check focus mode is still what we set.
     * As of 1.26, we now remember the focus mode for photos.
     */
    public void testFocusSaveMacroSwitchPhoto() {
        Log.d(TAG, "testFocusSaveMacroSwitchPhoto");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        String non_default_focus_mode = getNonDefaultFocus();
        switchToFocusValue(non_default_focus_mode);

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        String focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value after switching to video mode: "+ focus_value);
        assertTrue(focus_value.equals("focus_mode_continuous_video"));

        clickView(switchVideoButton);
        waitUntilCameraOpened();

        focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value after switching to picture mode: " + focus_value);
        assertTrue(focus_value.equals(non_default_focus_mode));
    }

    /* Start in photo mode with auto focus:
     * - go to video mode
     * - check in continuous focus mode
     * - switch to auto focus mode
     * - then pause and resume
     * - then check still in video mode, still in auto focus mode
     * - then repeat with restarting instead
     * (Note the name is a bit misleading - it used to be that we reset to continuous mode, now we don't.)
     */
    public void testFocusSwitchVideoResetContinuous() {
        Log.d(TAG, "testFocusSwitchVideoResetContinuous");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        switchToFocusValue("focus_mode_auto");

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        String focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals("focus_mode_continuous_video"));

        switchToFocusValue("focus_mode_auto");
        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals("focus_mode_auto"));

        this.pauseAndResume();
        assertTrue(mPreview.isVideo());

        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals("focus_mode_auto"));

        // now with restart

        switchToFocusValue("focus_mode_auto");
        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals("focus_mode_auto"));

        restart();
        assertTrue(mPreview.isVideo());

        focus_value = mPreview.getCameraController().getFocusValue();
        assertTrue(focus_value.equals("focus_mode_auto"));
    }

    private void subTestISOButtonAvailability() {
        if( mPreview.isVideoRecording() ) {
            // shouldn't show ISO buttons when video recording
            subTestPopupButtonAvailability("TEST_ISO", "auto", false);
            subTestPopupButtonAvailability("TEST_ISO", "100", false);
            subTestPopupButtonAvailability("TEST_ISO", "200", false);
            subTestPopupButtonAvailability("TEST_ISO", "400", false);
            subTestPopupButtonAvailability("TEST_ISO", "800", false);
            subTestPopupButtonAvailability("TEST_ISO", "1600", false);
        }
        else if( mPreview.supportsISORange() ) {
            subTestPopupButtonAvailability("TEST_ISO", "auto", true);
            int [] test_isos = {0, 50, 100, 200, 400, 800, 1600, 3200, 6400, 10000};
            int min_iso = mPreview.getMinimumISO();
            int max_iso = mPreview.getMaximumISO();
            for(int test_iso : test_isos) {
                subTestPopupButtonAvailability("TEST_ISO", "" + test_iso, test_iso >= min_iso && test_iso <= max_iso);
            }
            subTestPopupButtonAvailability("TEST_ISO", "" + (min_iso-1), false);
            subTestPopupButtonAvailability("TEST_ISO", "" + min_iso, true);
            subTestPopupButtonAvailability("TEST_ISO", "" + max_iso, true);
            subTestPopupButtonAvailability("TEST_ISO", "" + (max_iso+1), false);
        }
        else {
            List<String> supported_iso_values = mPreview.getSupportedISOs();
            subTestPopupButtonAvailability("TEST_ISO", "auto", supported_iso_values);
            subTestPopupButtonAvailability("TEST_ISO", "100", supported_iso_values);
            subTestPopupButtonAvailability("TEST_ISO", "200", supported_iso_values);
            subTestPopupButtonAvailability("TEST_ISO", "400", supported_iso_values);
            subTestPopupButtonAvailability("TEST_ISO", "800", supported_iso_values);
            subTestPopupButtonAvailability("TEST_ISO", "1600", supported_iso_values);
        }
    }

    /* Tests enabling and disabling the preview bitmap.
     */
    public void testPreviewBitmap() throws InterruptedException {
        Log.d(TAG, "testPreviewBitmap");

        if( !mActivity.supportsPreviewBitmaps() ) {
            Log.d(TAG, "preview bitmaps not supported");
            return;
        }

        setToDefault();
        Thread.sleep(1000);

        long [] delays = {20, 50, 100, 1000};
        int [] n_iters = {50, 30, 30, 3};
        assertEquals(delays.length, n_iters.length);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);

        assertFalse(mPreview.isPreviewBitmapEnabled());
        assertFalse(mPreview.refreshPreviewBitmapTaskIsRunning());

        for(int i=0;i<delays.length;i++) {
            Log.d(TAG, ">>> i = " + i + " delay: " + delays[i]);
            for(int j=0;j<n_iters[i];j++) {
                Log.d(TAG, "    >>> j = " + j + " / " + n_iters[i]);
                SharedPreferences.Editor editor = settings.edit();

                editor.putString(PreferenceKeys.HistogramPreferenceKey, "preference_histogram_rgb");
                editor.apply();
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mActivity.getApplicationInterface().getDrawPreview().updateSettings();
                    }
                });
                this.getInstrumentation().waitForIdleSync();
                Thread.sleep(delays[i]);
                if (delays[i] >= 1000) {
                    assertTrue(mPreview.isPreviewBitmapEnabled());
                }

                editor.putString(PreferenceKeys.HistogramPreferenceKey, "preference_histogram_off");
                editor.apply();
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mActivity.getApplicationInterface().getDrawPreview().updateSettings();
                    }
                });
                this.getInstrumentation().waitForIdleSync();
                Thread.sleep(delays[i]);
                if (delays[i] >= 1000) {
                    assertFalse(mPreview.isPreviewBitmapEnabled());
                    assertFalse(mPreview.refreshPreviewBitmapTaskIsRunning());
                }
            }
        }

        Thread.sleep(500);
        assertFalse(mPreview.isPreviewBitmapEnabled());
        assertFalse(mPreview.refreshPreviewBitmapTaskIsRunning());
    }

    public void testTakePhotoExposureCompensation() throws InterruptedException {
        Log.d(TAG, "testTakePhotoExposureCompensation");
        setToDefault();

        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureContainer = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
        SeekBar seekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_seekbar);
        assertTrue(exposureButton.getVisibility() == (mPreview.supportsExposures() ? View.VISIBLE : View.GONE));
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        if( !mPreview.supportsExposures() ) {
            return;
        }

        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);

        subTestISOButtonAvailability();

        assertTrue( mPreview.getMaximumExposure() - mPreview.getMinimumExposure() == seekBar.getMax() );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );
        Log.d(TAG, "change exposure to 1");
        mActivity.changeExposure(1);
        this.getInstrumentation().waitForIdleSync();
        assertTrue( mPreview.getCurrentExposure() == 1 );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );
        Log.d(TAG, "set exposure to min");
        seekBar.setProgress(0);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "actual exposure is now " + mPreview.getCurrentExposure());
        Log.d(TAG, "expected exposure to be " + mPreview.getMinimumExposure());
        assertTrue( mPreview.getCurrentExposure() == mPreview.getMinimumExposure() );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );

        // test the exposure button clears and reopens without changing exposure level
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue( mPreview.getCurrentExposure() == mPreview.getMinimumExposure() );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );

        // test touch to focus clears the exposure controls
        int [] gui_location = new int[2];
        mPreview.getView().getLocationOnScreen(gui_location);
        final int step_dist_c = 2;
        final int step_count_c = 10;
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+step_dist_c, gui_location[0], gui_location[1]+step_dist_c, gui_location[1], step_count_c);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue( mPreview.getCurrentExposure() == mPreview.getMinimumExposure() );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );

        Log.d(TAG, "set exposure to -1");
        seekBar.setProgress(-1 - mPreview.getMinimumExposure());
        this.getInstrumentation().waitForIdleSync();
        assertTrue( mPreview.getCurrentExposure() == -1 );
        assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );

        // clear again so as to not interfere with take photo routine
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+step_dist_c, gui_location[0], gui_location[1]+step_dist_c, gui_location[1], step_count_c);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        // test that switching to video mode removes the ISO buttons
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        assertFalse(mPreview.isVideo());
        Log.d(TAG, "switch to video mode");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        subTestISOButtonAvailability(); // check that ISO buttons are shown

        assertFalse(mPreview.isVideoRecording());
        Log.d(TAG, "about to click take video");
        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.isVideoRecording());

        Thread.sleep(100);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        subTestISOButtonAvailability(); // check that ISO buttons are not shown

        Thread.sleep(3000);

        assertTrue(mPreview.isVideoRecording());
        Log.d(TAG, "about to click stop video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking stop video");
        this.getInstrumentation().waitForIdleSync();
        assertFalse(mPreview.isVideoRecording());

        assertTrue(mPreview.isVideo());
        Log.d(TAG, "switch to photo mode");
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertFalse(mPreview.isVideo());
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();

            assertTrue(exposureButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureContainer.getVisibility() == View.GONE);
            assertTrue( mPreview.getCurrentExposure() == -1 );
            assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );

            clickView(exposureButton);
            assertTrue(exposureButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
            assertTrue( mPreview.getCurrentExposure() == -1 );
            assertTrue( mPreview.getCurrentExposure() - mPreview.getMinimumExposure() == seekBar.getProgress() );
        }
    }

    public void testTakePhotoManualISOExposure() throws InterruptedException {
        Log.d(TAG, "testTakePhotoManualISOExposure");
        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }
        else if( !mPreview.supportsISORange() ) {
            Log.d(TAG, "test requires manual iso range");
            return;
        }

        switchToISO(100);

        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureContainer = mActivity.findViewById(net.sourceforge.opencamera.R.id.manual_exposure_container);
        SeekBar isoSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.iso_seekbar);
        SeekBar exposureTimeSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_time_seekbar);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue(isoSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(exposureTimeSeekBar.getVisibility() == (mPreview.supportsExposureTime() ? View.VISIBLE : View.GONE));
        subTestISOButtonAvailability();

        /*final int manual_n = 1000; // should match MainActivity.manual_n
        assertTrue( isoSeekBar.getMax() == manual_n );
        if( mPreview.supportsExposureTime() )
            assertTrue( exposureTimeSeekBar.getMax() == manual_n );*/

        Log.d(TAG, "change ISO to min");
        isoSeekBar.setProgress(0);
        this.getInstrumentation().waitForIdleSync();
        assertTrue( mPreview.getCameraController().getISO() == mPreview.getMinimumISO() );

        if( mPreview.supportsExposureTime() ) {
            Log.d(TAG, "change exposure time to min");
            exposureTimeSeekBar.setProgress(0);
            this.getInstrumentation().waitForIdleSync();
            assertTrue( mPreview.getCameraController().getISO() == mPreview.getMinimumISO() );
            assertTrue( mPreview.getCameraController().getExposureTime() == mPreview.getMinimumExposureTime() );
        }

        Log.d(TAG, "camera_controller ISO: " + mPreview.getCameraController().getISO());
        Log.d(TAG, "change ISO to max");
        isoSeekBar.setProgress(isoSeekBar.getMax());
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "camera_controller ISO: " + mPreview.getCameraController().getISO());
        Log.d(TAG, "reported max ISO: " + mPreview.getMaximumISO());
        assertTrue( mPreview.getCameraController().getISO() == mPreview.getMaximumISO() );

        // n.b., currently don't test this on devices with long shutter times (e.g., OnePlus 3T)
        if( mPreview.supportsExposureTime() && mPreview.getMaximumExposureTime() < 1000000000 ) {
            Log.d(TAG, "change exposure time to max");
            exposureTimeSeekBar.setProgress(exposureTimeSeekBar.getMax());
            this.getInstrumentation().waitForIdleSync();
            assertTrue( mPreview.getCameraController().getISO() == mPreview.getMaximumISO() );
            assertTrue( mPreview.getCameraController().getExposureTime() == mPreview.getMaximumExposureTime() );
        }
        else {
            Log.d(TAG, "change exposure time to middle");
            //mActivity.setProgressSeekbarExponential(exposureTimeSeekBar, mPreview.getMinimumExposureTime(), mPreview.getMaximumExposureTime(), 1000000000);
            exposureTimeSeekBar.setProgress(exposureTimeSeekBar.getMax()/2);
            this.getInstrumentation().waitForIdleSync();
            assertTrue( mPreview.getCameraController().getISO() == mPreview.getMaximumISO() );
            assertTrue( mPreview.getCameraController().getExposureTime() != mPreview.getMaximumExposureTime() );
        }
        long saved_exposure_time = mPreview.getCameraController().getExposureTime();

        // test the exposure button clears and reopens without changing exposure level
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue(isoSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(exposureTimeSeekBar.getVisibility() == (mPreview.supportsExposureTime() ? View.VISIBLE : View.GONE));
        assertTrue( mPreview.getCameraController().getISO() == mPreview.getMaximumISO() );
        if( mPreview.supportsExposureTime() )
            assertTrue( mPreview.getCameraController().getExposureTime() == saved_exposure_time );

        // test touch to focus clears the exposure controls
        int [] gui_location = new int[2];
        mPreview.getView().getLocationOnScreen(gui_location);
        final int step_dist_c = 2;
        final int step_count_c = 10;
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+step_dist_c, gui_location[0], gui_location[1]+step_dist_c, gui_location[1], step_count_c);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);
        clickView(exposureButton);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue(isoSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(exposureTimeSeekBar.getVisibility() == (mPreview.supportsExposureTime() ? View.VISIBLE : View.GONE));
        assertTrue( mPreview.getCameraController().getISO() == mPreview.getMaximumISO() );
        if( mPreview.supportsExposureTime() )
            assertTrue( mPreview.getCameraController().getExposureTime() == saved_exposure_time );

        // clear again so as to not interfere with take photo routine
        TouchUtils.drag(MainActivityTest.this, gui_location[0]+step_dist_c, gui_location[0], gui_location[1]+step_dist_c, gui_location[1], step_count_c);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.GONE);

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            int old_max = mPreview.getMaximumISO();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();

            assertTrue(exposureButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureContainer.getVisibility() == View.GONE);
            // we use same ISO for all cameras, but if new camera has lower max, it should automatically reduce
            assertEquals(Math.min(old_max, mPreview.getMaximumISO()), mPreview.getCameraController().getISO());
            if( mPreview.supportsExposureTime() ) {
                Log.d(TAG, "exposure time: " + mPreview.getCameraController().getExposureTime());
                Log.d(TAG, "min exposure time: " + mPreview.getMinimumExposureTime());
                Log.d(TAG, "max exposure time: " + mPreview.getMaximumExposureTime());
                if( saved_exposure_time < mPreview.getMinimumExposureTime() )
                    saved_exposure_time = mPreview.getMinimumExposureTime();
                if( saved_exposure_time > mPreview.getMaximumExposureTime() )
                    saved_exposure_time = mPreview.getMaximumExposureTime();
                assertTrue( mPreview.getCameraController().getExposureTime() == saved_exposure_time );
            }

            clickView(exposureButton);
            assertTrue(exposureButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
            assertTrue(isoSeekBar.getVisibility() == View.VISIBLE);
            assertTrue(exposureTimeSeekBar.getVisibility() == (mPreview.supportsExposureTime() ? View.VISIBLE : View.GONE));
            assertEquals(Math.min(old_max, mPreview.getMaximumISO()), mPreview.getCameraController().getISO());
            if( mPreview.supportsExposureTime() )
                assertTrue( mPreview.getCameraController().getExposureTime() == saved_exposure_time );
        }
    }

    public void testTakePhotoManualWB() throws InterruptedException {
        Log.d(TAG, "testTakePhotoManualWB");
        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            return;
        }
        if( !mPreview.supportsWhiteBalanceTemperature() ) {
            return;
        }

        assertTrue( mPreview.getCameraController().getWhiteBalance().equals("auto"));
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int initial_temperature = mPreview.getCameraController().getWhiteBalanceTemperature();
        int initial_temperature_setting = settings.getInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, 5000);
        assertTrue(initial_temperature == initial_temperature_setting);
        SeekBar white_balance_seek_bar = mActivity.findViewById(net.sourceforge.opencamera.R.id.white_balance_seekbar);
        int initial_white_balance_seek_bar_pos = white_balance_seek_bar.getProgress();

        /*SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getWhiteBalancePreferenceKey(), "manual");
        editor.apply();
        updateForSettings();*/

        // simulate having changed this through popup view:
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        clickView(popupButton);
        Log.d(TAG, "wait for popup to open");
        while( !mActivity.popupIsOpen() ) {
        }
        Log.d(TAG, "popup is now open");
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.WhiteBalancePreferenceKey, "manual");
        editor.apply();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.getMainUI().getPopupView().switchToWhiteBalance("manual");
            }
        });
        this.getInstrumentation().waitForIdleSync();

        /*View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        clickView(popupButton);
        Log.d(TAG, "wait for popup to open");
        while( !mActivity.popupIsOpen() ) {
        }
        Log.d(TAG, "popup is now open");
        // first need to open the white balance sub-menu
        View wbButton = mActivity.getUIButton("TEST_WHITE_BALANCE");
        assertTrue(wbButton != null);
        ScrollView popupContainer = (ScrollView)mActivity.findViewById(net.sourceforge.opencamera.R.id.popup_container);
        popupContainer.scrollTo(0, wbButton.getBottom());
        this.getInstrumentation().waitForIdleSync();
        Thread.sleep(1000);

        clickView(wbButton);
        Log.d(TAG, "clicked wb button");
        // check popup still opened
        assertTrue( mActivity.popupIsOpen() );

        RadioButton manualWBButton = (RadioButton)mActivity.getUIButton("TEST_WHITE_BALANCE_manual");
        assertTrue(manualWBButton != null);
        assertTrue(!manualWBButton.isChecked());
        clickView(manualWBButton);
        Log.d(TAG, "clicked manual wb button");
        // check popup still opened
        assertTrue( mActivity.popupIsOpen() );
        // check now selected
        assertTrue(manualWBButton.isChecked());
        */

        // check we switched to manual mode
        assertTrue( mPreview.getCameraController().getWhiteBalance().equals("manual"));

        // check that the wb temperature has been updated, both in preferences, and the camera controller
        int new_temperature = mPreview.getCameraController().getWhiteBalanceTemperature();
        int new_temperature_setting = settings.getInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, 5000);
        assertTrue(new_temperature == new_temperature_setting);
        Log.d(TAG, "initial_temperature: " + initial_temperature);
        Log.d(TAG, "new_temperature: " + new_temperature);
        assertTrue(new_temperature != initial_temperature);
        // check we moved the wb slider too
        int new_white_balance_seek_bar_pos = white_balance_seek_bar.getProgress();
        Log.d(TAG, "initial_white_balance_seek_bar_pos: " + initial_white_balance_seek_bar_pos);
        Log.d(TAG, "new_white_balance_seek_bar_pos: " + new_white_balance_seek_bar_pos);
        assertTrue(new_white_balance_seek_bar_pos != initial_white_balance_seek_bar_pos);

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureContainer = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
        SeekBar seekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_seekbar);
        View manualWBContainer = mActivity.findViewById(net.sourceforge.opencamera.R.id.manual_white_balance_container);
        SeekBar seekBarWB = mActivity.findViewById(net.sourceforge.opencamera.R.id.white_balance_seekbar);

        assertTrue(exposureButton.getVisibility() == (mPreview.supportsExposures() ? View.VISIBLE : View.GONE));
        assertTrue(exposureContainer.getVisibility() == View.GONE);
        assertTrue(manualWBContainer.getVisibility() == View.GONE);

        if( !mPreview.supportsExposures() ) {
            return;
        }

        clickView(exposureButton);
        subTestISOButtonAvailability();

        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureContainer.getVisibility() == View.VISIBLE);
        assertTrue(seekBar.getVisibility() == View.VISIBLE);
        assertTrue(manualWBContainer.getVisibility() == View.VISIBLE);
        assertTrue(seekBarWB.getVisibility() == View.VISIBLE);
    }

    /** Tests that the audio control icon is visible or not as expect (guards against bug fixed in 1.30)
     */
    public void testAudioControlIcon() {
        Log.d(TAG, "testAudioControlIcon");

        setToDefault();

        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        assertTrue( audioControlButton.getVisibility() == View.GONE );

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "noise");
        editor.apply();
        updateForSettings();
        assertTrue( audioControlButton.getVisibility() == View.VISIBLE );

        restart();
        // reset due to restarting!
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);

        assertTrue( audioControlButton.getVisibility() == View.VISIBLE );

        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "none");
        editor.apply();
        updateForSettings();
        Log.d(TAG, "visibility is now: " + audioControlButton.getVisibility());
        assertTrue( audioControlButton.getVisibility() == View.GONE );

        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "voice");
        editor.apply();
        updateForSettings();
        assertTrue( audioControlButton.getVisibility() == View.VISIBLE );

        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "none");
        editor.apply();
        updateForSettings();
        Log.d(TAG, "visibility is now: " + audioControlButton.getVisibility());
        assertTrue( audioControlButton.getVisibility() == View.GONE );
    }

    private void waitForTakePhoto() {
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean is_focus_bracketing = mActivity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_panorama = mActivity.supportsPanorama() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_panorama");

        Log.d(TAG, "wait until finished taking photo");
        long time_s = System.currentTimeMillis();
        while( mPreview.isTakingPhoto() || !mActivity.getApplicationInterface().canTakeNewPhoto() ) {
            // make sure the test fails rather than hanging, if for some reason we get stuck (note that testTakePhotoManualISOExposure takes over 10s on Nexus 6)
            // also see note at end of setToDefault for Nokia 8, need to sleep briefly to avoid hanging here
            if( !is_focus_bracketing ) {
                assertTrue(System.currentTimeMillis() - time_s < (is_panorama ? 40000 : 20000)); // need longer for panorama on Nexus 7 for testTakePhotoPanoramaMax
            }
            assertTrue(!mPreview.isTakingPhoto() || switchCameraButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || switchVideoButton.getVisibility() == View.GONE);
            //assertTrue(!mPreview.isTakingPhoto() || flashButton.getVisibility() == View.GONE);
            //assertTrue(!mPreview.isTakingPhoto() || focusButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || exposureButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || exposureLockButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || audioControlButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || popupButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || trashButton.getVisibility() == View.GONE);
            assertTrue(!mPreview.isTakingPhoto() || shareButton.getVisibility() == View.GONE);
        }
        Log.d(TAG, "done taking photo");
    }

    private void subTestTouchToFocus(final boolean wait_after_focus, final boolean single_tap_photo, final boolean double_tap_photo, final boolean manual_can_auto_focus, final boolean can_focus_area, final String focus_value, final String focus_value_ui) throws InterruptedException {
        // touch to auto-focus with focus area (will also exit immersive mode)
        // autofocus shouldn't be immediately, but after a delay
        // and Galaxy S10e needs a longer delay for some reason, for the subsequent touch of the preview view to register
        Thread.sleep(2000);
        int saved_count = mPreview.count_cameraAutoFocus;
        Log.d(TAG, "saved count_cameraAutoFocus: " + saved_count);
        Log.d(TAG, "about to click preview for autofocus");
        if( double_tap_photo ) {
            TouchUtils.tapView(MainActivityTest.this, mPreview.getView());
        }
        else {
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        }
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == (manual_can_auto_focus ? saved_count+1 : saved_count));
        assertTrue(mPreview.hasFocusArea() == can_focus_area);
        if( can_focus_area ) {
            assertTrue(mPreview.getCameraController().getFocusAreas() != null);
            assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
            assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
            assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
        }
        else {
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            // we still set metering areas
            assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
            assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
        }
        String new_focus_value_ui = mPreview.getCurrentFocusValue();
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        if( focus_value.equals("focus_mode_continuous_picture") && !single_tap_photo )
            assertTrue(mPreview.getCameraController().getFocusValue().equals("focus_mode_auto")); // continuous focus mode switches to auto focus on touch (unless single_tap_photo)
        else
            assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
        if( double_tap_photo ) {
            Thread.sleep(100);
            Log.d(TAG, "about to click preview again for double tap");
            //TouchUtils.tapView(MainActivityTest.this, mPreview.getView());
            mPreview.onDoubleTap(); // calling tapView twice doesn't seem to work consistently, so we call this directly!
            this.getInstrumentation().waitForIdleSync();
        }
        if( wait_after_focus && !single_tap_photo && !double_tap_photo) {
            // don't wait after single or double tap photo taking, as the photo taking operation is already started
            Log.d(TAG, "wait after focus...");
            Thread.sleep(3000);
        }
    }

    private void checkFocusInitial(final String focus_value, final String focus_value_ui) {
        String new_focus_value_ui = mPreview.getCurrentFocusValue();
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        assertTrue(mPreview.getCameraController().getFocusValue().equals(focus_value));
    }

    private void checkFocusAfterTakePhoto(final String focus_value, final String focus_value_ui) {
        // focus should be back to normal now:
        String new_focus_value_ui = mPreview.getCurrentFocusValue();
        Log.d(TAG, "focus_value_ui: " + focus_value_ui);
        Log.d(TAG, "new new_focus_value_ui: " + new_focus_value_ui);
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        String new_focus_value = mPreview.getCameraController().getFocusValue();
        Log.d(TAG, "focus_value: " + focus_value);
        Log.d(TAG, "new focus_value: " + new_focus_value);
        if( new_focus_value_ui != null && new_focus_value_ui.equals("focus_mode_continuous_picture") && focus_value.equals("focus_mode_auto") && new_focus_value.equals("focus_mode_continuous_picture") ) {
            // this is fine, it just means we were temporarily in touch-to-focus mode
        }
        else {
            assertTrue(new_focus_value.equals(focus_value));
        }
    }

    private void checkFocusAfterTakePhoto2(final boolean touch_to_focus, final boolean test_wait_capture_result, final boolean locked_focus, final boolean can_auto_focus, final boolean can_focus_area, final int saved_count) {
        // in locked focus mode, taking photo should never redo an auto-focus
        // if photo mode, we may do a refocus if the previous auto-focus failed, but not if it succeeded
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        if( locked_focus ) {
            assertTrue(mPreview.count_cameraAutoFocus == (can_auto_focus ? saved_count+1 : saved_count));
        }
        if( test_wait_capture_result ) {
            // if test_wait_capture_result, then we'll have waited too long, so focus settings may have changed
        }
        else if( touch_to_focus ) {
            Log.d(TAG, "can_focus_area?: " + can_focus_area);
            Log.d(TAG, "hasFocusArea?: " + mPreview.hasFocusArea());
            assertTrue(mPreview.hasFocusArea() == can_focus_area);
            if( can_focus_area ) {
                assertTrue(mPreview.getCameraController().getFocusAreas() != null);
                assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
                assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
                assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
            }
            else {
                assertTrue(mPreview.getCameraController().getFocusAreas() == null);
                // we still set metering areas
                assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
                assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
            }
        }
        else {
            assertFalse(mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
        }
    }

    private int getExpNNewFiles(final boolean is_raw) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean hdr_save_expo =  sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false);
        boolean is_hdr = mActivity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_expo = mActivity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = mActivity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_fast_burst = mActivity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        String n_expo_images_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3");
        int n_expo_images = Integer.parseInt(n_expo_images_s);
        String n_focus_bracketing_images_s = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
        int n_focus_bracketing_images = Integer.parseInt(n_focus_bracketing_images_s);
        String n_fast_burst_images_s = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
        int n_fast_burst_images = Integer.parseInt(n_fast_burst_images_s);

        int exp_n_new_files;
        if( is_hdr && hdr_save_expo ) {
            exp_n_new_files = 4;
            if( is_raw && !mActivity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files += 3;
            }
        }
        else if( is_expo ) {
            exp_n_new_files = n_expo_images;
            if( is_raw && !mActivity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }
        else if( is_focus_bracketing ) {
            exp_n_new_files = n_focus_bracketing_images;
            if( is_raw && !mActivity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }
        else if( is_fast_burst )
            exp_n_new_files = n_fast_burst_images;
        else {
            exp_n_new_files = 1;
            if( is_raw && !mActivity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }
        Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
        return exp_n_new_files;
    }

    private void checkFilenames(final boolean is_raw, final File [] files, final File [] files2) {
        Log.d(TAG, "checkFilenames");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean hdr_save_expo =  sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false);
        boolean is_hdr = mActivity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_fast_burst = mActivity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        boolean is_expo = mActivity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = mActivity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");

        // check files have names as expected
        String filename_jpeg = null;
        String filename_dng = null;
        int n_files = files == null ? 0 : files.length;
        for(File file : files2) {
            Log.d(TAG, "check file: " + file);
            boolean is_new = true;
            for(int j=0;j<n_files && is_new;j++) {
                if( file.equals( files[j] ) ) {
                    is_new = false;
                }
            }
            if( is_new ) {
                Log.d(TAG, "file is new");
                String filename = file.getName();
                assertTrue(filename.startsWith("IMG_"));
                if( filename.endsWith(".jpg") ) {
                    assertTrue(hdr_save_expo || is_expo || is_focus_bracketing || is_fast_burst || filename_jpeg == null);
                    if( is_hdr && hdr_save_expo ) {
                        // only look for the "_HDR" image
                        if( filename.contains("_HDR") )
                            filename_jpeg = filename;
                    }
                    else if( is_expo || is_focus_bracketing ) {
                        if( filename_jpeg != null ) {
                            // check same root
                            int last_underscore_jpeg = filename_jpeg.lastIndexOf('_');
                            assertTrue(last_underscore_jpeg != -1);
                            String filename_base_jpeg = filename_jpeg.substring(0, last_underscore_jpeg+1);

                            int last_underscore = filename.lastIndexOf('_');
                            assertTrue(last_underscore != -1);
                            String filename_base = filename.substring(0, last_underscore+1);
                            Log.d(TAG, "filename_base: " + filename_base);

                            assertTrue( filename_base_jpeg.equals(filename_base) );
                        }
                        filename_jpeg = filename; // store the last name, to match mActivity.test_last_saved_image
                    }
                    else {
                        filename_jpeg = filename;
                    }
                }
                else if( filename.endsWith(".dng") ) {
                    assertTrue(is_raw);
                    assertTrue(hdr_save_expo || is_expo || is_focus_bracketing || filename_dng == null);
                    filename_dng = filename;
                }
                else {
                    assertTrue(false);
                }
            }
        }
        assertTrue( (filename_jpeg == null) == (is_raw && mActivity.getApplicationInterface().isRawOnly() && !is_hdr) );
        assertTrue( (filename_dng != null) == is_raw );
        if( is_raw && !mActivity.getApplicationInterface().isRawOnly() ) {
            // check we have same filenames (ignoring extensions)
            // if HDR, then we should exclude the "_HDR" vs "_x" of the base filenames
            // if expo, then exclude the "_x" as values may be different due to different order of JPEG vs DNG files in the files2 array (at least on Galaxy S10e)
            String filename_base_jpeg;
            String filename_base_dng;
            if( is_hdr ) {
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-7);
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-5);
            }
            else if( is_expo ) {
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-5);
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-5);
            }
            else {
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-4);
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-4);
            }
            Log.d(TAG, "filename_base_jpeg: " + filename_base_jpeg);
            Log.d(TAG, "filename_base_dng: " + filename_base_dng);
            assertEquals(filename_base_jpeg, filename_base_dng);
        }
    }

    private void checkFilesAfterTakePhoto(final boolean is_raw, final boolean test_wait_capture_result, final File [] files, final String suffix, final int max_time_s, final Date date) throws InterruptedException {
        File folder = mActivity.getImageFolder();
        int n_files = files == null ? 0 : files.length;
        assertTrue( folder.exists() );
        File [] files2 = folder.listFiles();
        int n_new_files = (files2 == null ? 0 : files2.length) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        int exp_n_new_files = getExpNNewFiles(is_raw);
        assertTrue(n_new_files == exp_n_new_files);
        checkFilenames(is_raw, files, files2);
        Thread.sleep(1500); // wait until we've scanned
        if( test_wait_capture_result ) {
            // if test_wait_capture_result, then it may take longer before we've scanned
        }
        else {
            Log.d(TAG, "failed to scan: " + mActivity.getStorageUtils().failed_to_scan);
            assertFalse(mActivity.getStorageUtils().failed_to_scan);
        }

        if( !mActivity.getApplicationInterface().isRawOnly() ) {
            assertTrue(mActivity.test_last_saved_image != null);
            File saved_image_file = new File(mActivity.test_last_saved_image);
            Log.d(TAG, "saved name: " + saved_image_file.getName());
            /*Log.d(TAG, "expected name: " + expected_filename);
            Log.d(TAG, "expected name1: " + expected_filename1);
            assertTrue(expected_filename.equals(saved_image_file.getName()) || expected_filename1.equals(saved_image_file.getName()));*/
            // allow for possibility that the time has passed since taking the photo
            boolean matched = false;
            for(int i=0;i<=max_time_s && !matched;i++) {
                Date test_date = new Date(date.getTime() - 1000*i);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(test_date);
                String expected_filename = "IMG_" + timeStamp + suffix + ".jpg";
                Log.d(TAG, "expected name: " + expected_filename);
                if( expected_filename.equals(saved_image_file.getName() ) )
                    matched = true;
            }
            assertTrue(matched);
        }
    }

    private void postTakePhotoChecks(final boolean immersive_mode, final int exposureVisibility, final int exposureLockVisibility) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);

        // trash/share only shown when preview is paused after taking a photo
        boolean pause_preview =  sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
        if( pause_preview ) {
            assertFalse(mPreview.isPreviewStarted());
            assertTrue(switchCameraButton.getVisibility() == View.GONE);
            assertTrue(switchVideoButton.getVisibility() == View.GONE);
            assertTrue(exposureButton.getVisibility() == View.GONE);
            assertTrue(exposureLockButton.getVisibility() == View.GONE);
            assertTrue(audioControlButton.getVisibility() == View.GONE);
            assertTrue(popupButton.getVisibility() == View.GONE);
            assertTrue(trashButton.getVisibility() == View.VISIBLE);
            assertTrue(shareButton.getVisibility() == View.VISIBLE);
        }
        else {
            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
            assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
            if( !immersive_mode ) {
                assertTrue(exposureButton.getVisibility() == exposureVisibility);
                assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
            }
            assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
            assertTrue(popupButton.getVisibility() == View.VISIBLE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);
        }
    }

    /*
     * Note that we pass test_wait_capture_result as a parameter rather than reading from the activity, as for some reason this sometimes resets to false?! Declaring it volatile doesn't fix the problem.
     */
    private void subTestTakePhoto(boolean locked_focus, boolean immersive_mode, boolean touch_to_focus, boolean wait_after_focus, boolean single_tap_photo, boolean double_tap_photo, boolean is_raw, boolean test_wait_capture_result) throws InterruptedException {
        assertTrue(mPreview.isPreviewStarted());
        assertFalse(mActivity.getApplicationInterface().getImageSaver().test_queue_blocked);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_thumbnail_anim = sharedPreferences.getBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, true);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");
        boolean is_dro = mActivity.supportsDRO() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_dro");
        boolean is_hdr = mActivity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_nr = mActivity.supportsNoiseReduction() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_noise_reduction");
        boolean is_expo = mActivity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = mActivity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_fast_burst = mActivity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        String n_expo_images_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3");
        int n_expo_images = Integer.parseInt(n_expo_images_s);
        String n_focus_bracketing_images_s = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
        int n_focus_bracketing_images = Integer.parseInt(n_focus_bracketing_images_s);
        String n_fast_burst_images_s = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
        int n_fast_burst_images = Integer.parseInt(n_fast_burst_images_s);

        int saved_count_cameraTakePicture = mPreview.count_cameraTakePicture;

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        Log.d(TAG, "folder: " + folder);
        File [] files = folder.listFiles();
        int n_files = files == null ? 0 : files.length;
        Log.d(TAG, "n_files at start: " + n_files);

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        String focus_value = mPreview.getCameraController().getFocusValue();
        String focus_value_ui = mPreview.getCurrentFocusValue();
        boolean can_auto_focus = false;
        boolean manual_can_auto_focus = false;
        boolean can_focus_area = false;
        if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro") ) {
            can_auto_focus = true;
        }

        if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro") ) {
            manual_can_auto_focus = true;
        }
        else if( focus_value.equals("focus_mode_continuous_picture") && !single_tap_photo ) {
            // if single_tap_photo and continuous mode, we go straight to taking a photo rather than doing a touch to focus
            manual_can_auto_focus = true;
        }

        if( mPreview.getMaxNumFocusAreas() != 0 && ( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro") || focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") || focus_value.equals("focus_mode_manual2") ) ) {
            can_focus_area = true;
        }
        Log.d(TAG, "focus_value? " + focus_value);
        Log.d(TAG, "can_auto_focus? " + can_auto_focus);
        Log.d(TAG, "manual_can_auto_focus? " + manual_can_auto_focus);
        Log.d(TAG, "can_focus_area? " + can_focus_area);
        int saved_count = mPreview.count_cameraAutoFocus;

        checkFocusInitial(focus_value, focus_value_ui);

        if( touch_to_focus ) {
            subTestTouchToFocus(wait_after_focus, single_tap_photo, double_tap_photo, manual_can_auto_focus, can_focus_area, focus_value, focus_value_ui);
        }
        Log.d(TAG, "saved count_cameraAutoFocus: " + saved_count);

        if( !single_tap_photo && !double_tap_photo ) {
            View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            assertFalse( mActivity.hasThumbnailAnimation() );
            Log.d(TAG, "about to click take photo");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take photo");
        }

        waitForTakePhoto();

        Date date = new Date();
        String suffix = "";
        int max_time_s = 1;
        if( is_dro ) {
            suffix = "_DRO";
        }
        else if( is_hdr ) {
            suffix = "_HDR";
        }
        else if( is_nr ) {
            suffix = "_NR";
        }
        else if( is_expo ) {
            suffix = "_" + (n_expo_images-1);
        }
        else if( is_focus_bracketing ) {
            suffix = "_" + (n_focus_bracketing_images-1); // when focus bracketing starts from _0
            //suffix = "_" + (n_focus_bracketing_images); // when focus bracketing starts from _1
            max_time_s = 60; // can take much longer to save in focus bracketing mode!
        }
        else if( is_fast_burst ) {
            suffix = "_" + (n_fast_burst_images-1); // when burst numbering starts from _0
            //suffix = "_" + (n_fast_burst_images); // when burst numbering starts from _1
            max_time_s = 3; // takes longer to save 20 images!
        }

        if( is_raw ) {
            max_time_s += 6; // extra time needed for Nexus 6 at least
        }

        boolean pause_preview =  sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
        if( pause_preview ) {
            max_time_s += 3; // need to allow longer for testTakePhotoRawWaitCaptureResult with Nexus 6 at least
        }

        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        Log.d(TAG, "take picture count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==saved_count_cameraTakePicture+1);
        if( test_wait_capture_result ) {
            // if test_wait_capture_result, then we'll have waited too long for thumbnail animation
        }
        else if( is_focus_bracketing ) {
            // thumbnail animation may have already occurred (e.g., see testTakePhotoFocusBracketingHeavy()
        }
        else if( has_thumbnail_anim ) {
            long time_s = System.currentTimeMillis();
            while( !mActivity.hasThumbnailAnimation() ) {
                Log.d(TAG, "waiting for thumbnail animation");
                Thread.sleep(10);
                int allowed_time_ms = 10000;
                if( is_hdr || is_nr || is_expo ) {
                    // some devices need longer time (especially Nexus 6)
                    allowed_time_ms = 16000;
                }
                assertTrue( System.currentTimeMillis() - time_s < allowed_time_ms );
            }
        }
        else {
            assertFalse( mActivity.hasThumbnailAnimation() );
        }
        mActivity.waitUntilImageQueueEmpty();
        Log.d(TAG, "mActivity.hasThumbnailAnimation()?: " + mActivity.hasThumbnailAnimation());

        checkFocusAfterTakePhoto(focus_value, focus_value_ui);

        checkFilesAfterTakePhoto(is_raw, test_wait_capture_result, files, suffix, max_time_s, date);

        checkFocusAfterTakePhoto2(touch_to_focus, test_wait_capture_result, locked_focus, can_auto_focus, can_focus_area, saved_count);

        postTakePhotoChecks(immersive_mode, exposureVisibility, exposureLockVisibility);

        assertFalse(mActivity.getApplicationInterface().getImageSaver().test_queue_blocked);
    }

    public void testTakePhoto() throws InterruptedException {
        Log.d(TAG, "testTakePhoto");
        setToDefault();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /** Test taking photo with JPEG + DNG (RAW).
     */
    public void testTakePhotoRaw() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRaw");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, true, false);
    }

    /** Test taking photo with JPEG + DNG (RAW), with test_wait_capture_result.
     */
    public void testTakePhotoRawWaitCaptureResult() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawWaitCaptureResult");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.apply();
        updateForSettings();

        mPreview.getCameraController().test_wait_capture_result = true;
        subTestTakePhoto(false, false, true, true, false, false, true, true);

        // now repeat with pause preview (guards against crash fixed in 1.44.1 where we got CalledFromWrongThreadException when
        // setting visibility for icons with pause preview mode with test_wait_capture_result
        editor = settings.edit();
        editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
        editor.apply();
        subTestTakePhoto(false, false, true, true, false, false, true, true);
    }

    /** Test taking multiple RAW photos.
     */
    public void testTakePhotoRawMulti() {
        Log.d(TAG, "testTakePhotoRawMulti");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.apply();
        updateForSettings();

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        int start_count = mPreview.count_cameraTakePicture;
        final int n_photos = 5;
        for(int i=0;i<n_photos;i++) {
            View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            Log.d(TAG, "about to click take photo count: " + i);
            clickView(takePhotoButton);
            Log.d(TAG, "wait until finished taking photo count: " + i);
            waitForTakePhoto();
            Log.d(TAG, "done taking photo count: " + i);
            this.getInstrumentation().waitForIdleSync();

            /*int n_new_files = folder.listFiles().length - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == mPreview.count_cameraTakePicture - start_count);*/
            assertTrue(i+1 == mPreview.count_cameraTakePicture - start_count);
        }

        mActivity.waitUntilImageQueueEmpty();
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 2*n_photos); // if we fail here, be careful we haven't lost images (i.e., waitUntilImageQueueEmpty() returns before all images are saved)
    }

    /** Test taking photo with JPEG + DNG (RAW) and repeat mode.
     */
    public void testTakePhotoRawRepeat() {
        Log.d(TAG, "testTakePhotoRawRepeat");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }

        final int n_repeat = 10;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.putString(PreferenceKeys.getRepeatModePreferenceKey(), "" + n_repeat);
        editor.apply();
        updateForSettings();

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();

        // wait until photos taken
        // wait, and test that we've taken the photos by then
        long time_s = System.currentTimeMillis();
        while( mPreview.count_cameraTakePicture < n_repeat ) {
            // Nexus 6 needs a lot more time than OnePlus 3T or Nokia 8! Needs more like 50s rather than 22s.
            assertTrue( System.currentTimeMillis() - time_s < 52000 );
        }
        // since we're in repeat mode, the original waitForTakePhoto() call above will only have waited for the first photo
        Log.d(TAG, "wait until finished taking final photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking final photo");
        this.getInstrumentation().waitForIdleSync();
        mActivity.waitUntilImageQueueEmpty();
        //Thread.sleep(500); // wait a bit longer just in case
        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        Log.d(TAG, "count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==n_repeat);

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 2*n_repeat); // if we fail here, be careful we haven't lost images (i.e., waitUntilImageQueueEmpty() returns before all images are saved)
    }

    /** Test taking photo with DNG (RAW) only.
     */
    public void testTakePhotoRawOnly() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawOnly");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }

        boolean supports_auto_stabilise = mActivity.supportsAutoStabilise();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_only");
        editor.apply();
        updateForSettings();

        // test modes not supported in RAW only mode
        assertFalse(mActivity.supportsAutoStabilise());
        assertFalse(mActivity.supportsDRO());

        subTestTakePhoto(false, false, true, true, false, false, true, false);

        // switch to video mode
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());

        // check auto-stabilise mode now available (since it'll apply to the snapshots, which are always JPEG)
        assertTrue(mActivity.supportsAutoStabilise() == supports_auto_stabilise);

        if( !mPreview.supportsPhotoVideoRecording() ) {
            Log.d(TAG, "video snapshot not supported");
        }
        else {
            subTestTakeVideoSnapshot();
        }
    }

    /** Test taking photo with JPEG + DNG (RAW) in Expo photo mode.
     */
    public void testTakePhotoRawExpo() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawExpo");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, true, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Test taking photo with JPEG + DNG (RAW) in Expo photo mode, with test_wait_capture_result.
     */
    public void testTakePhotoRawExpoWaitCaptureResult() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawExpoWaitCaptureResult");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
        editor.apply();
        updateForSettings();

        mPreview.getCameraController().test_wait_capture_result = true;
        subTestTakePhoto(false, false, true, true, false, false, true, true);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Test taking photo with DNG (RAW) only in Expo photo mode.
     */
    public void testTakePhotoRawOnlyExpo() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawOnlyExpo");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_only");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
        editor.apply();
        updateForSettings();

        // test modes not supported in RAW only mode
        assertFalse(mActivity.supportsAutoStabilise());
        assertFalse(mActivity.supportsDRO());

        subTestTakePhoto(false, false, true, true, false, false, true, false);
    }

    public void testTakePhotoAutoStabilise() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAutoStabilise");
        setToDefault();
        assertFalse(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
        editor.apply();
        updateForSettings();
        assertTrue(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /** Test taking photo with continuous photo mode.
     *  Touching to focus will mean the photo is taken whilst the camera controller is actually
     *  in autofocus mode.
     */
    public void testTakePhotoContinuous() throws InterruptedException {
        Log.d(TAG, "testTakePhotoContinuous");
        setToDefault();
        switchToFocusValue("focus_mode_continuous_picture");
        subTestTakePhoto(false, false, true, true, false, false, false, false);

        assertTrue( mPreview.getCameraController().test_af_state_null_focus == 0 );
    }

    /** Test taking photo with continuous photo mode. Don't touch to focus first, so we take the
     *  photo in continuous focus mode.
     */
    public void testTakePhotoContinuousNoTouch() throws InterruptedException {
        Log.d(TAG, "testTakePhotoContinuousNoTouch");
        setToDefault();
        switchToFocusValue("focus_mode_continuous_picture");
        subTestTakePhoto(false, false, false, false, false, false, false, false);

        assertTrue( mPreview.getCameraController().test_af_state_null_focus == 0 );
    }

    /**  May have precapture timeout if phone is face down and devices uses fake flash by default (e.g., OnePlus 3T) - see testTakePhotoFlashOnFakeMode.
     */
    public void testTakePhotoFlashAuto() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashAuto");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        switchToFlashValue("flash_auto");
        Thread.sleep(2000); // wait so we don't take the photo immediately, to be more realistic
        subTestTakePhoto(false, false, false, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
    }

    /**  May have precapture timeout if phone is face down and devices uses fake flash by default (e.g., OnePlus 3T) - see testTakePhotoFlashOnFakeMode.
     */
    public void testTakePhotoFlashOn() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashOn");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        switchToFlashValue("flash_on");
        Thread.sleep(2000); // wait so we don't take the photo immediately, to be more realistic
        subTestTakePhoto(false, false, false, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
    }

    public void testTakePhotoFlashTorch() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashTorch");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        switchToFlashValue("flash_torch");
        Thread.sleep(2000); // wait so we don't take the photo immediately, to be more realistic
        subTestTakePhoto(false, false, false, false, false, false, false, false);
    }

    /** Tests the "fake" flash mode. Important to do this even for devices where standard Camera2 flash work fine, as we use
     *  fake flash for modes like HDR (plus it's good to still test the fake flash mode on as many devices as possible).
     *  We do more tests with flash on than flash auto (especially due to bug on OnePlus 3T where fake flash auto never fires the flash
     *  anyway).
     *  May have precapture timeout if phone is face down, see note for testTakePhotoFlashOnFakeMode.
     */
    public void testTakePhotoFlashAutoFakeMode() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashAutoFakeMode");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }
        if( !mPreview.usingCamera2API() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
        editor.apply();
        updateForSettings();

        switchToFlashValue("flash_auto");
        switchToFocusValue("focus_mode_auto");
        Thread.sleep(2000); // wait so we don't take the photo immediately, to be more realistic
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, false, false, false, false, false, false);

        // now test continuous focus mode
        Thread.sleep(1000);
        switchToFocusValue("focus_mode_continuous_picture");
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, false, false, false, false, false, false);
    }

    /** Tests the "fake" flash mode. Important to do this even for devices where standard Camera2 flash work fine, as we use
     *  fake flash for modes like HDR (plus it's good to still test the fake flash mode on as many devices as possible).
     *  We do more tests with flash on than flash auto (especially due to bug on OnePlus 3T where fake flash auto never fires the flash
     *  anyway).
     *  May have precapture timeout if phone is face down (at least on Nexus 6 and OnePlus 3T) - issue that we've already ae converged,
     *  so we think fake-precapture never starts when firing the flash for taking photo. I think this is when being face down means that
     *  although flash fires, it doesn't light up the picture.
     */
    public void testTakePhotoFlashOnFakeMode() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashOnFakeMode");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }
        if( !mPreview.usingCamera2API() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
        editor.apply();
        updateForSettings();

        switchToFocusValue("focus_mode_auto");
        switchToFlashValue("flash_on");
        Thread.sleep(2000); // wait so we don't take the photo immediately, to be more realistic
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, false, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == 1 );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 1 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 1 );

        // now test doing autofocus, waiting, then taking photo
        Thread.sleep(1000);
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == 2 );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 2 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 2 );

        // now test doing autofocus, then taking photo immediately
        Thread.sleep(1000);
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, true, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        Log.d(TAG, "test_fake_flash_focus: " + mPreview.getCameraController().test_fake_flash_focus);
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == 3 );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 3 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 3 );

        // this should match CameraController2.do_af_trigger_for_continuous
        //final boolean do_af_for_continuous = true;
        // actually for fake flash, we no longer do af trigger for continuous
        final boolean do_af_for_continuous = false;

        // now test it all again with continuous focus mode
        switchToFocusValue("focus_mode_continuous_picture");
        Thread.sleep(1000);
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, false, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        Log.d(TAG, "test_fake_flash_focus: " + mPreview.getCameraController().test_fake_flash_focus);
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == (do_af_for_continuous ? 4 : 3) );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 4 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 4 );

        // now test doing autofocus, waiting, then taking photo
        Thread.sleep(1000);
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == (do_af_for_continuous ? 5 : 4) );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 5 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 5 );

        // now test doing autofocus, then taking photo immediately
        Thread.sleep(1000);
        assertTrue( mPreview.getCameraController().getUseCamera2FakeFlash() ); // make sure we turned on the option in the camera controller
        subTestTakePhoto(false, false, true, false, false, false, false, false);
        assertTrue( mPreview.getCameraController() == null || mPreview.getCameraController().count_precapture_timeout == 0 );
        assertTrue( mPreview.getCameraController().test_fake_flash_focus == (do_af_for_continuous ? 6 : 5) );
        assertTrue( mPreview.getCameraController().test_fake_flash_precapture == 6 );
        assertTrue( mPreview.getCameraController().test_fake_flash_photo == 6 );

        //mPreview.getCameraController().count_precapture_timeout = 0; // hack - precapture timeouts are more common with fake flash precapture mode, especially when phone is face down during testing
    }

    public void testTakePhotoSingleTap() throws InterruptedException {
        Log.d(TAG, "testTakePhotoSingleTap");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.TouchCapturePreferenceKey, "single");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, true, false, false, false);
    }

    public void testTakePhotoDoubleTap() throws InterruptedException {
        Log.d(TAG, "testTakePhotoDoubleTap");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.TouchCapturePreferenceKey, "double");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, true, false, false);
    }

    public void testTakePhotoNoAutofocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoNoAutofocus");
        setToDefault();
        subTestTakePhoto(false, false, false, false, false, false, false, false);
    }

    public void testTakePhotoNoThumbnail() throws InterruptedException {
        Log.d(TAG, "testTakePhotoNoThumbnail");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, false);
        editor.apply();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /* Tests manually focusing, then immediately taking a photo.
     */
    public void testTakePhotoAfterFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAfterFocus");
        setToDefault();
        subTestTakePhoto(false, false, true, false, false, false, false, false);
    }

    /* Tests bug fixed by take_photo_after_autofocus in Preview, where the app would hang due to taking a photo after touching to focus. */
    public void testTakePhotoFlashBug() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFlashBug");
        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        switchToFlashValue("flash_on");
        subTestTakePhoto(false, false, true, false, false, false, false, false);
    }

    /* Tests taking a photo with front camera.
     * Also tests the content descriptions for switch camera button.
     * And tests that we save the current camera when pausing and resuming.
     */
    public void testTakePhotoFrontCamera() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFrontCamera");
        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }
        int cameraId = mPreview.getCameraId();
        boolean is_front_facing = mPreview.getCameraControllerManager().isFrontFacing(cameraId);

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        CharSequence contentDescription = switchCameraButton.getContentDescription();
        clickView(switchCameraButton);
        waitUntilCameraOpened();

        int new_cameraId = mPreview.getCameraId();
        assertTrue(new_cameraId != cameraId);
        boolean new_is_front_facing = mPreview.getCameraControllerManager().isFrontFacing(new_cameraId);
        CharSequence new_contentDescription = switchCameraButton.getContentDescription();

        Log.d(TAG, "cameraId: " + cameraId);
        Log.d(TAG, "is_front_facing: " + is_front_facing);
        Log.d(TAG, "contentDescription: " + contentDescription);
        Log.d(TAG, "new_cameraId: " + new_cameraId);
        Log.d(TAG, "new_is_front_facing: " + new_is_front_facing);
        Log.d(TAG, "new_contentDescription: " + new_contentDescription);

        assertTrue(cameraId != new_cameraId);
        assertTrue( contentDescription.equals( mActivity.getResources().getString(new_is_front_facing ? net.sourceforge.opencamera.R.string.switch_to_front_camera : net.sourceforge.opencamera.R.string.switch_to_back_camera) ) );
        assertTrue( new_contentDescription.equals( mActivity.getResources().getString(is_front_facing ? net.sourceforge.opencamera.R.string.switch_to_front_camera : net.sourceforge.opencamera.R.string.switch_to_back_camera) ) );
        subTestTakePhoto(false, false, true, true, false, false, false, false);

        // check still front camera after pause/resume
        pauseAndResume();

        int restart_cameraId = mPreview.getCameraId();
        CharSequence restart_contentDescription = switchCameraButton.getContentDescription();
        Log.d(TAG, "restart_contentDescription: " + restart_contentDescription);
        assertTrue(restart_cameraId == new_cameraId);
        assertTrue( restart_contentDescription.equals( mActivity.getResources().getString(is_front_facing ? net.sourceforge.opencamera.R.string.switch_to_front_camera : net.sourceforge.opencamera.R.string.switch_to_back_camera) ) );

        // now test mirror mode
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.FrontCameraMirrorKey, "preference_front_camera_mirror_photo");
        editor.apply();
        updateForSettings();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /* Tests taking a photo with front camera and screen flash.
     * And tests that we save the current camera when pausing and resuming.
     */
    public void testTakePhotoFrontCameraScreenFlash() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFrontCameraScreenFlash");
        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        int cameraId = mPreview.getCameraId();

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        this.getInstrumentation().waitForIdleSync();
        waitUntilCameraOpened();

        int new_cameraId = mPreview.getCameraId();

        Log.d(TAG, "cameraId: " + cameraId);
        Log.d(TAG, "new_cameraId: " + new_cameraId);

        assertTrue(cameraId != new_cameraId);

        switchToFlashValue("flash_frontscreen_on");

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /** Take a photo in auto focus mode.
     */
    public void testTakePhotoAutoFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAutoFocus");
        setToDefault();
        switchToFocusValue("focus_mode_auto");
        subTestTakePhoto(false, false, true, true, false, false, false, false);

        assertTrue( mPreview.getCameraController().test_af_state_null_focus == 0 );
    }

    /** Take a photo for Camera2 API when camera is released on UI thread whilst photo is taken on background thread (via
     *  autofocus callback).
     */
    public void testTakePhotoAutoFocusReleaseDuringPhoto() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAutoFocusReleaseDuringPhoto");

        if( !mPreview.usingCamera2API() ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }

        setToDefault();
        switchToFocusValue("focus_mode_auto");

        mPreview.getCameraController().test_release_during_photo = true;

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        assertFalse( mActivity.hasThumbnailAnimation() );
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Thread.sleep(5000);
    }

    public void testTakePhotoLockedFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoLockedFocus");
        setToDefault();
        switchToFocusValue("focus_mode_locked");
        subTestTakePhoto(true, false, true, true, false, false, false, false);
    }

    public void testTakePhotoManualFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoManualFocus");
        setToDefault();

        if( !mPreview.getSupportedFocusValues().contains("focus_mode_manual2") ) {
            return;
        }
        SeekBar seekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(seekBar.getVisibility() == View.GONE);
        switchToFocusValue("focus_mode_manual2");
        assertTrue(seekBar.getVisibility() == View.VISIBLE);
        seekBar.setProgress( (int)(0.25*(seekBar.getMax()-1)) );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    public void testTakePhotoLockedLandscape() throws InterruptedException {
        Log.d(TAG, "testTakePhotoLockedLandscape");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getLockOrientationPreferenceKey(), "landscape");
        editor.apply();
        updateForSettings();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    public void testTakePhotoLockedPortrait() throws InterruptedException {
        Log.d(TAG, "testTakePhotoLockedPortrait");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getLockOrientationPreferenceKey(), "portrait");
        editor.apply();
        updateForSettings();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    // If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
    public void testTakePhotoSAF() throws InterruptedException {
        Log.d(TAG, "testTakePhotoSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    public void testTakePhotoAudioButton() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAudioButton");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "voice");
        editor.apply();
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    // If this fails with a SecurityException about needing INJECT_EVENTS permission, this seems to be due to the "help popup" that Android shows - can be fixed by clearing that manually, then rerunning the test.
    public void testImmersiveMode() throws InterruptedException {
        Log.d(TAG, "testImmersiveMode");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
            Log.d(TAG, "immersive mode requires Android Kitkat or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_gui");
        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "voice");
        editor.apply();
        updateForSettings();

        boolean has_audio_control_button = true;

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        View zoomSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom_seekbar);
        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // now wait for immersive mode to kick in
        Thread.sleep(6000);
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.GONE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        subTestTakePhoto(false, true, true, true, false, false, false, false);

        // test now exited immersive mode
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // wait for immersive mode to kick in again
        Thread.sleep(6000);
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.GONE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        subTestTakePhotoPreviewPaused(true, false);

        // test now exited immersive mode
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // need to switch video before going back to immersive mode
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        // test now exited immersive mode
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // wait for immersive mode to kick in again
        Thread.sleep(6000);
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.GONE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        subTestTakeVideo(false, false, false, true, null, 5000, false, false);

        // test touch exits immersive mode
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // switch back to photo mode
        if( mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }

        if( mPreview.usingCamera2API() && mPreview.supportsISORange() ) {
            // now test exposure button disappears when in manual ISO mode
            switchToISO(100);

            // wait for immersive mode to kick in again
            Thread.sleep(6000);
            assertTrue(switchCameraButton.getVisibility() == View.GONE);
            assertTrue(switchVideoButton.getVisibility() == View.GONE);
            assertTrue(exposureButton.getVisibility() == View.GONE);
            assertTrue(exposureLockButton.getVisibility() == View.GONE);
            assertTrue(audioControlButton.getVisibility() == View.GONE);
            assertTrue(popupButton.getVisibility() == View.GONE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);
            assertTrue(zoomSeekBar.getVisibility() == View.GONE);
            assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
            assertTrue(pauseVideoButton.getVisibility() == View.GONE);
            assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);
        }
    }

    // See note under testImmersiveMode() if this fails with a SecurityException about needing INJECT_EVENTS permission.
    public void testImmersiveModeEverything() throws InterruptedException {
        Log.d(TAG, "testImmersiveModeEverything");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
            Log.d(TAG, "immersive mode requires Android Kitkat or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_everything");
        editor.apply();
        updateForSettings();

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        View zoomSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom_seekbar);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // now wait for immersive mode to kick in
        Thread.sleep(6000);
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.GONE);
        assertTrue(takePhotoButton.getVisibility() == View.GONE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // now touch to exit immersive mode
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Thread.sleep(500);

        // test now exited immersive mode
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);

        // test touch exits immersive mode
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(pauseVideoButton.getVisibility() == View.GONE);
        assertTrue(takePhotoVideoButton.getVisibility() == View.GONE);
    }

    private void subTestTakePhotoPreviewPaused(boolean immersive_mode, boolean is_raw) throws InterruptedException {
        mPreview.count_cameraTakePicture = 0;

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
        editor.apply();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        Log.d(TAG, "check if preview is started");
        assertTrue(mPreview.isPreviewStarted());

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        // store status to compare with later
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        // don't need to wait until image queue empty, as Open Camera shouldn't use background thread for preview pause option

        Bitmap thumbnail = mActivity.gallery_bitmap;
        assertTrue(thumbnail != null);

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        int exp_n_new_files = is_raw ? 2 : 1;
        Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
        assertTrue(n_new_files == exp_n_new_files);

        // now preview should be paused
        assertTrue(!mPreview.isPreviewStarted()); // check preview paused
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.VISIBLE);
        assertTrue(shareButton.getVisibility() == View.VISIBLE);

        Thread.sleep(1000); // needed for Galaxy S10e
        Log.d(TAG, "about to click preview");
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "done click preview");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync 3");

        // check photo not deleted
        n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
        assertTrue(n_new_files == exp_n_new_files);

        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        //assertTrue(flashButton.getVisibility() == flashVisibility);
        //assertTrue(focusButton.getVisibility() == focusVisibility);
        if( !immersive_mode ) {
            assertTrue(exposureButton.getVisibility() == exposureVisibility);
            assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        }
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        // check still same icon even after a delay
        Log.d(TAG, "thumbnail:" + thumbnail);
        Log.d(TAG, "mActivity.gallery_bitmap: " + mActivity.gallery_bitmap);
        assertTrue(mActivity.gallery_bitmap == thumbnail);
        Thread.sleep(1000);
        Log.d(TAG, "thumbnail:" + thumbnail);
        Log.d(TAG, "mActivity.gallery_bitmap: " + mActivity.gallery_bitmap);
        assertTrue(mActivity.gallery_bitmap == thumbnail);

        mActivity.waitUntilImageQueueEmpty();
    }

    public void testTakePhotoPreviewPaused() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPaused");
        setToDefault();
        subTestTakePhotoPreviewPaused(false, false);
    }

    public void testTakePhotoPreviewPausedAudioButton() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedAudioButton");
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "voice");
        editor.apply();
        updateForSettings();

        subTestTakePhotoPreviewPaused(false, false);
    }

    // If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
    public void testTakePhotoPreviewPausedSAF() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestTakePhotoPreviewPaused(false, false);
    }

    /** Tests pause preview option.
     * @param share If true, share the image; else, trash it. A test with share==true should be the
     *              last test if run in a suite, as sharing the image may sometimes cause later
     *              tests to hang.
     */
    private void subTestTakePhotoPreviewPausedShareTrash(boolean is_raw, boolean share) throws InterruptedException {
        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
        editor.apply();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        assertTrue(mPreview.isPreviewStarted());

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        // flash and focus etc default visibility tested in another test
        // but store status to compare with later
        //int flashVisibility = flashButton.getVisibility();
        //int focusVisibility = focusButton.getVisibility();
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        Log.d(TAG, "count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==1);

        // don't need to wait until image queue empty, as Open Camera shouldn't use background thread for preview pause option

        Bitmap thumbnail = mActivity.gallery_bitmap;
        assertTrue(thumbnail != null);

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        int exp_n_new_files = is_raw ? 2 : 1;
        Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
        assertTrue(n_new_files == exp_n_new_files);

        // now preview should be paused
        assertTrue(!mPreview.isPreviewStarted()); // check preview restarted
        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        //assertTrue(flashButton.getVisibility() == View.GONE);
        //assertTrue(focusButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == View.GONE);
        assertTrue(exposureLockButton.getVisibility() == View.GONE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.GONE);
        assertTrue(trashButton.getVisibility() == View.VISIBLE);
        assertTrue(shareButton.getVisibility() == View.VISIBLE);

        if( share ) {
            Log.d(TAG, "about to click share");
            clickView(shareButton);
            Log.d(TAG, "done click share");

            // check photo(s) not deleted
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == exp_n_new_files);
        }
        else {
            Log.d(TAG, "about to click trash");
            clickView(trashButton);
            Log.d(TAG, "done click trash");

            // check photo(s) deleted
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 0);

            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
            assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
            //assertTrue(flashButton.getVisibility() == flashVisibility);
            //assertTrue(focusButton.getVisibility() == focusVisibility);
            assertTrue(exposureButton.getVisibility() == exposureVisibility);
            assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
            assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
            assertTrue(popupButton.getVisibility() == View.VISIBLE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);

            // icon may be null, or have been set to another image - only changed after a delay
            Thread.sleep(2000);
            Log.d(TAG, "gallery_bitmap: " + mActivity.gallery_bitmap);
            Log.d(TAG, "thumbnail: " + thumbnail);
            assertTrue(mActivity.gallery_bitmap != thumbnail);
        }
        mActivity.waitUntilImageQueueEmpty();
    }

    public void testTakePhotoPreviewPausedTrash() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedTrash");
        setToDefault();
        subTestTakePhotoPreviewPausedShareTrash(false, false);
    }

    /** Equivalent of testTakePhotoPreviewPausedTrash(), but for Storage Access Framework.
     *  If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
     */
    public void testTakePhotoPreviewPausedTrashSAF() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedTrashSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestTakePhotoPreviewPausedShareTrash(false, false);
    }

    /** Like testTakePhotoPreviewPausedTrash() but taking 2 photos, only deleting the most recent - make
     *  sure we don't delete both images!
     */
    public void testTakePhotoPreviewPausedTrash2() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedTrash2");
        setToDefault();

        subTestTakePhotoPreviewPaused(false, false);

        mPreview.count_cameraTakePicture = 0; // need to reset

        subTestTakePhotoPreviewPausedShareTrash(false, false);
    }

    /** Equivalent of testTakePhotoPreviewPausedTrash(), but with Raw enabled.
     */
    public void testTakePhotoPreviewPausedTrashRaw() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedTrashRaw");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.apply();
        updateForSettings();

        subTestTakePhotoPreviewPausedShareTrash(true, false);
    }

    /** Take a photo with RAW that we keep, then take a photo without RAW that we delete, and ensure we
     *  don't delete the previous RAW image!
     */
    public void testTakePhotoPreviewPausedTrashRaw2() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedTrashRaw2");
        setToDefault();

        if( !mPreview.supportsRaw() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.apply();
        updateForSettings();

        subTestTakePhotoPreviewPaused(false, true);

        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_no");
        editor.apply();
        updateForSettings();
        mPreview.count_cameraTakePicture = 0; // need to reset

        subTestTakePhotoPreviewPausedShareTrash(false, false);
    }

    /** Tests sharing an image. If run in a suite, this test should be last, as sharing the image
     *  may sometimes cause later tests to hang.
     */
    public void testTakePhotoPreviewPausedShare() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreviewPausedShare");
        setToDefault();
        subTestTakePhotoPreviewPausedShareTrash(false, true);
    }

    /* Tests that we don't do an extra autofocus when taking a photo, if recently touch-focused.
     */
    public void testTakePhotoQuickFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoQuickFocus");
        setToDefault();

        assertTrue(mPreview.isPreviewStarted());

        // touch to auto-focus with focus area
        // autofocus shouldn't be immediately, but after a delay
        // and Galaxy S10e needs a longer delay for some reason, for the subsequent touch of the preview view to register
        Thread.sleep(2000);
        int saved_count = mPreview.count_cameraAutoFocus;
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        // wait 3s for auto-focus to complete
        Thread.sleep(3000);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        // taking photo shouldn't have done an auto-focus, and still have focus areas
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        mActivity.waitUntilImageQueueEmpty();
    }

    private void takePhotoRepeatFocus(boolean locked) throws InterruptedException {
        Log.d(TAG, "takePhotoRepeatFocus");
        setToDefault();
        if( locked ) {
            switchToFocusValue("focus_mode_locked");
        }
        else {
            switchToFocusValue("focus_mode_auto");
        }

        assertTrue(mPreview.isPreviewStarted());

        // touch to auto-focus with focus area
        // autofocus shouldn't be immediately, but after a delay
        // and Galaxy S10e needs a longer delay for some reason, for the subsequent touch of the preview view to register
        Thread.sleep(2000);
        int saved_count = mPreview.count_cameraAutoFocus;
        TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        Log.d(TAG, "1 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        // wait 3s for auto-focus to complete, and 5s to require additional auto-focus when taking a photo
        // need a bit longer on Galaxy S10e
        Thread.sleep(9000);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        // taking photo should have done an auto-focus iff in automatic mode, and still have focus areas
        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        assertEquals((locked ? saved_count + 1 : saved_count + 2), mPreview.count_cameraAutoFocus);
        assertTrue(mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() != null);
        assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
        assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
        assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);

        mActivity.waitUntilImageQueueEmpty();
    }

    /* Tests that we do an extra autofocus when taking a photo, if too long since last touch-focused.
     */
    public void testTakePhotoRepeatFocus() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRepeatFocus");
        takePhotoRepeatFocus(false);
    }

    /* Tests that we don't do an extra autofocus when taking a photo, if too long since last touch-focused, when in locked focus mode.
     */
    public void testTakePhotoRepeatFocusLocked() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRepeatFocusLocked");
        takePhotoRepeatFocus(true);
    }

    /* Tests taking a photo with animation and shutter disabled, and not setting focus areas
     */
    public void testTakePhotoAlt() throws InterruptedException {
        Log.d(TAG, "testTakePhotoAlt");
        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), false);
        editor.apply();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        assertTrue(mPreview.isPreviewStarted());

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        // flash and focus etc default visibility tested in another test
        // but store status to compare with later
        //int flashVisibility = flashButton.getVisibility();
        //int focusVisibility = focusButton.getVisibility();
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        // autofocus shouldn't be immediately, but after a delay
        Thread.sleep(2000);
        int saved_count = mPreview.count_cameraAutoFocus;

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        mActivity.waitUntilImageQueueEmpty();

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);

        Log.d(TAG, "2 count_cameraAutoFocus: " + mPreview.count_cameraAutoFocus);
        Log.d(TAG, "saved_count: " + saved_count);
        /*
        // taking photo should have done an auto-focus, and no focus areas [focus auto]
        assertTrue(mPreview.count_cameraAutoFocus == saved_count+1);
        */
        // taking photo shouldn't have done an auto-focus, and no focus areas [focus continuous]
        assertTrue(mPreview.count_cameraAutoFocus == saved_count);
        assertTrue(!mPreview.hasFocusArea());
        assertTrue(mPreview.getCameraController().getFocusAreas() == null);
        assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

        // trash/share only shown when preview is paused after taking a photo

        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        //assertTrue(flashButton.getVisibility() == flashVisibility);
        //assertTrue(focusButton.getVisibility() == focusVisibility);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
    }

    private void takePhotoLoop(int count) {
        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        int start_count = mPreview.count_cameraTakePicture;
        for(int i=0;i<count;i++) {
            View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            Log.d(TAG, "about to click take photo: " + i);
            clickView(takePhotoButton);
            Log.d(TAG, "wait until finished taking photo: " + i);
            waitForTakePhoto();
            Log.d(TAG, "done taking photo: " + i);
            this.getInstrumentation().waitForIdleSync();

            /*int n_new_files = folder.listFiles().length - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == mPreview.count_cameraTakePicture - start_count);*/
            assertTrue(i+1 == mPreview.count_cameraTakePicture - start_count);
        }

        mActivity.waitUntilImageQueueEmpty();
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == count);
    }

    private void subTestTakePhotoAutoLevel() {
        Log.d(TAG, "subTestTakePhotoAutoLevel");
        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue(mPreview.isPreviewStarted());
        assertTrue(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());
        final int n_photos_c = 5;

        takePhotoLoop(n_photos_c);
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            while( switchCameraButton.getVisibility() != View.VISIBLE ) {
                // wait until photo is taken and button is visible again
            }
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            int new_cameraId = mPreview.getCameraId();
            assertTrue(cameraId != new_cameraId);
            takePhotoLoop(n_photos_c);
            while( switchCameraButton.getVisibility() != View.VISIBLE ) {
                // wait until photo is taken and button is visible again
            }

            // return to back camera
            switchToCamera(cameraId);
        }
    }

    /* Tests taking photos repeatedly with auto-stabilise enabled.
     * Tests with front and back.
     */
    public void testTakePhotoAutoLevel() {
        Log.d(TAG, "testTakePhotoAutoLevel");

        subTestTakePhotoAutoLevel();
    }

    /* As testTakePhotoAutoLevel(), but with test_low_memory set.
     */
    public void testTakePhotoAutoLevelLowMemory() {
        Log.d(TAG, "testTakePhotoAutoLevelLowMemory");

        mActivity.test_low_memory = true;

        subTestTakePhotoAutoLevel();
    }

    private void takePhotoLoopAngles(int [] angles) {
        // count initial files in folder
        mActivity.test_have_angle = true;
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        int start_count = mPreview.count_cameraTakePicture;
        for(int i=0;i<angles.length;i++) {
            mActivity.test_angle = angles[mPreview.count_cameraTakePicture - start_count];
            View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            Log.d(TAG, "about to click take photo count: " + i);
            clickView(takePhotoButton);
            Log.d(TAG, "wait until finished taking photo count: " + i);
            waitForTakePhoto();
            Log.d(TAG, "done taking photo count: " + i);
            this.getInstrumentation().waitForIdleSync();

            /*int n_new_files = folder.listFiles().length - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == mPreview.count_cameraTakePicture - start_count);*/
            assertTrue(i+1 == mPreview.count_cameraTakePicture - start_count);
        }

        mActivity.waitUntilImageQueueEmpty();
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == angles.length); // if we fail here, be careful we haven't lost images (i.e., waitUntilImageQueueEmpty() returns before all images are saved); note that in some cases, this test fails here because the activity onPause() after clicking take photo?!

        mActivity.test_have_angle = false;
    }

    private void subTestTakePhotoAutoLevelAngles() {
        Log.d(TAG, "subTestTakePhotoAutoLevelAngles");
        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue(mPreview.isPreviewStarted());
        assertTrue(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());
        final int [] angles = new int[]{0, -129, 30, -44, 61, -89, 179};

        takePhotoLoopAngles(angles);
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            while( switchCameraButton.getVisibility() != View.VISIBLE ) {
                // wait until photo is taken and button is visible again
            }
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            int new_cameraId = mPreview.getCameraId();
            assertTrue(cameraId != new_cameraId);
            takePhotoLoopAngles(angles);
            while( switchCameraButton.getVisibility() != View.VISIBLE ) {
                // wait until photo is taken and button is visible again
            }

            // return to back camera
            switchToCamera(cameraId);
        }
    }

    /* Tests taking photos repeatedly with auto-stabilise enabled, at various angles.
     * Tests with front and back.
     */
    public void testTakePhotoAutoLevelAngles() {
        Log.d(TAG, "testTakePhotoAutoLevel");

        subTestTakePhotoAutoLevelAngles();
    }

    /* As testTakePhotoAutoLevelAngles(), but with test_low_memory set.
     */
    public void testTakePhotoAutoLevelAnglesLowMemory() {
        Log.d(TAG, "testTakePhotoAutoLevelAnglesLowMemory");

        mActivity.test_low_memory = true;

        subTestTakePhotoAutoLevelAngles();
    }

    private interface VideoTestCallback {
        int doTest(); // return expected number of new files (or -1 to indicate not to check this)
    }

    /**
     * @return The number of resultant video files
     * @throws InterruptedException
     */
    private int subTestTakeVideo(boolean test_exposure_lock, boolean test_focus_area, boolean allow_failure, boolean immersive_mode, VideoTestCallback test_cb, long time_ms, boolean max_filesize, boolean subtitles) throws InterruptedException {
        assertTrue(mPreview.isPreviewStarted());

        if( test_exposure_lock && !mPreview.supportsExposureLock() ) {
            return 0;
        }

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( mPreview.isVideo() ) {
            assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_selector );
            assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
            assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
            assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
            assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo) ) );
        }
        else {
            assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo_selector );
            assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video );
            assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.take_photo) ) );
            assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
            assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_video) ) );
        }
        assertTrue( pauseVideoButton.getVisibility() == View.GONE );
        assertTrue( takePhotoVideoButton.getVisibility() == View.GONE );

        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());
        assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_selector );
        assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
        assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
        assertTrue( switchVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo) ) );
        assertTrue( pauseVideoButton.getVisibility() == View.GONE );
        assertTrue( takePhotoVideoButton.getVisibility() == View.GONE );

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        Log.d(TAG, "folder: " + folder);
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        // but store status to compare with later
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_selector );
        assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
        assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) ) );
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        if( mPreview.isOnTimer() ) {
            Log.d(TAG, "wait for timer");
            while( mPreview.isOnTimer() ) {
            }
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
        }

        int exp_n_new_files = 0;
        if( mPreview.isVideoRecording() ) {
            assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_recording );
            assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
            assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
            assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
            else
                assertTrue( pauseVideoButton.getVisibility() == View.GONE );
            if( mPreview.supportsPhotoVideoRecording() )
                assertTrue( takePhotoVideoButton.getVisibility() == View.VISIBLE );
            else
                assertTrue( takePhotoVideoButton.getVisibility() == View.GONE );
            assertTrue(switchCameraButton.getVisibility() == View.GONE);
            //assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
            assertTrue(switchVideoButton.getVisibility() == View.GONE);
            assertTrue(audioControlButton.getVisibility() == View.GONE);
            assertTrue(popupButton.getVisibility() == (!immersive_mode && mPreview.supportsFlash() ? View.VISIBLE : View.GONE)); // popup button only visible when recording video if flash supported
            assertTrue(exposureButton.getVisibility() == exposureVisibility);
            assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);

            if( test_cb == null ) {
                if( !immersive_mode && time_ms > 500 ) {
                    // test turning torch on/off (if in immersive mode, popup button will be hidden)
                    switchToFlashValue("flash_torch");
                    Thread.sleep(500);
                    switchToFlashValue("flash_off");
                }

                Thread.sleep(time_ms);
                assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_recording );
                assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );

                assertTrue(!mPreview.hasFocusArea());
                if( !allow_failure ) {
                    assertTrue(mPreview.getCameraController().getFocusAreas() == null);
                    assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
                }

                if( test_focus_area ) {
                    // touch to auto-focus with focus area
                    Log.d(TAG, "touch to focus");
                    TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
                    Thread.sleep(1000); // wait for autofocus
                    assertTrue(mPreview.hasFocusArea());
                    assertTrue(mPreview.getCameraController().getFocusAreas() != null);
                    assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
                    assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
                    assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
                    Log.d(TAG, "done touch to focus");

                    // this time, don't wait
                    Log.d(TAG, "touch again to focus");
                    TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
                }

                if( test_exposure_lock ) {
                    Log.d(TAG, "test exposure lock");
                    assertTrue( !mPreview.getCameraController().getAutoExposureLock() );
                    clickView(exposureLockButton);
                    this.getInstrumentation().waitForIdleSync();
                    Log.d(TAG, "after idle sync");
                    assertTrue( mPreview.getCameraController().getAutoExposureLock() );
                    Thread.sleep(2000);
                }

                assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_recording );
                assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
                Log.d(TAG, "about to click stop video");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking stop video");
                this.getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");
            }
            else {
                exp_n_new_files = test_cb.doTest();

                if( mPreview.isVideoRecording() ) {
                    Log.d(TAG, "about to click stop video");
                    clickView(takePhotoButton);
                    Log.d(TAG, "done clicking stop video");
                    this.getInstrumentation().waitForIdleSync();
                    Log.d(TAG, "after idle sync");
                }
            }
        }
        else {
            Log.d(TAG, "didn't start video");
            assertTrue(allow_failure);
        }

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        if( test_cb == null ) {
            if( time_ms <= 500 ) {
                // if quick, should have deleted corrupt video - but may be device dependent, sometimes we manage to record a video anyway!
                assertTrue(n_new_files == 0 || n_new_files == 1);
            }
            else if( subtitles ) {
                assertEquals(2, n_new_files);
            }
            else {
                assertEquals(1, n_new_files);
            }
        }
        else {
            Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
            if( exp_n_new_files >= 0 ) {
                assertEquals(exp_n_new_files, n_new_files);
            }
        }

        // trash/share only shown when preview is paused after taking a photo

        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        if( !max_filesize ) {
            // if doing restart on max filesize, we may have already restarted by now (on Camera2 API at least)
            Log.d(TAG, "switchCameraButton.getVisibility(): " + switchCameraButton.getVisibility());
            assertTrue(switchCameraButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
            assertTrue(audioControlButton.getVisibility() == ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        }
        assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(popupButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        assertFalse( mPreview.isVideoRecording() );
        assertTrue( (Integer)takePhotoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_video_selector );
        assertTrue( (Integer)switchVideoButton.getTag() == net.sourceforge.opencamera.R.drawable.take_photo );
        assertEquals( takePhotoButton.getContentDescription(), mActivity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) );
        assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
        Log.d(TAG, "pauseVideoButton.getVisibility(): " + pauseVideoButton.getVisibility());
        assertTrue( pauseVideoButton.getVisibility() == View.GONE );
        assertTrue( takePhotoVideoButton.getVisibility() == View.GONE );

        return n_new_files;
    }

    public void testTakeVideo() throws InterruptedException {
        Log.d(TAG, "testTakeVideo");

        setToDefault();

        subTestTakeVideo(false, false, false, false, null, 5000, false, false);
    }

    public void testTakeVideoAudioControl() throws InterruptedException {
        Log.d(TAG, "testTakeVideoAudioControl");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.AudioControlPreferenceKey, "voice");
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, false, false, null, 5000, false, false);
    }

    // If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
    public void testTakeVideoSAF() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, false, false, null, 5000, false, false);
    }

    public void testTakeVideoSubtitles() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSubtitles");

        setToDefault();
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.VideoSubtitlePref, "preference_video_subtitle_yes");
            editor.apply();
            updateForSettings();
        }

        subTestTakeVideo(false, false, false, false, null, 5000, false, true);
    }

    /** Tests video subtitles option, including GPS - also tests losing the connection.
     */
    public void testTakeVideoSubtitlesGPS() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSubtitlesGPS");

        setToDefault();
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.VideoSubtitlePref, "preference_video_subtitle_yes");
            editor.putBoolean(PreferenceKeys.LocationPreferenceKey, true);
            editor.apply();
            updateForSettings();
        }

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                // wait for location
                long start_t = System.currentTimeMillis();
                while( !mActivity.getLocationSupplier().testHasReceivedLocation() ) {
                    getInstrumentation().waitForIdleSync();
                    if( System.currentTimeMillis() - start_t > 20000 ) {
                        // need to allow long time for testing devices without mobile network; will likely fail altogether if don't even have wifi
                        assertTrue(false);
                    }
                }
                getInstrumentation().waitForIdleSync();
                assertTrue(mActivity.getLocationSupplier().getLocation() != null);

                Log.d(TAG, "have location");
                try {
                    Thread.sleep(2000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }

                // now test losing gps
                Log.d(TAG, "test losing location");
                mActivity.getLocationSupplier().setForceNoLocation(true);

                try {
                    Thread.sleep(2000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }

                return 2;
            }
        }, 5000, false, true);
    }

    /** Test pausing and resuming video.
     */
    public void testTakeVideoPause() throws InterruptedException {
        Log.d(TAG, "testTakeVideoPause");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
            Log.d(TAG, "pause video requires Android N or better");
            return;
        }

        setToDefault();

        final View pauseVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        assertTrue( pauseVideoButton.getVisibility() == View.GONE );

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                final long time_tol_ms = 1000;

                Log.d(TAG, "wait before pausing");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );
                long video_time = mPreview.getVideoTime();
                Log.d(TAG, "video time: " + video_time);
                assertTrue( video_time >= 3000 - time_tol_ms );
                assertTrue( video_time <= 3000 + time_tol_ms );

                Log.d(TAG, "about to click pause video");
                clickView(pauseVideoButton);
                Log.d(TAG, "done clicking pause video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.resume_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( mPreview.isVideoRecordingPaused() );

                Log.d(TAG, "wait before resuming");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.resume_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( mPreview.isVideoRecordingPaused() );
                video_time = mPreview.getVideoTime();
                Log.d(TAG, "video time: " + video_time);
                assertTrue( video_time >= 3000 - time_tol_ms );
                assertTrue( video_time <= 3000 + time_tol_ms );

                Log.d(TAG, "about to click resume video");
                clickView(pauseVideoButton);
                Log.d(TAG, "done clicking resume video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );

                Log.d(TAG, "wait before stopping");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                Log.d(TAG, "takePhotoButton description: " + takePhotoButton.getContentDescription());
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );
                video_time = mPreview.getVideoTime();
                Log.d(TAG, "video time: " + video_time);
                assertTrue( video_time >= 6000 - time_tol_ms );
                assertTrue( video_time <= 6000 + time_tol_ms );

                Log.d(TAG, "about to click stop video");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking stop video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                return 1;
            }
        }, 5000, false, false);
    }

    /** Test pausing and stopping video.
     */
    public void testTakeVideoPauseStop() throws InterruptedException {
        Log.d(TAG, "testTakeVideoPauseStop");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
            Log.d(TAG, "pause video requires Android N or better");
            return;
        }

        setToDefault();

        final View pauseVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        assertTrue( pauseVideoButton.getVisibility() == View.GONE );

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                final long time_tol_ms = 1000;

                Log.d(TAG, "wait before pausing");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );
                long video_time = mPreview.getVideoTime();
                Log.d(TAG, "video time: " + video_time);
                assertTrue( video_time >= 3000 - time_tol_ms );
                assertTrue( video_time <= 3000 + time_tol_ms );

                Log.d(TAG, "about to click pause video");
                clickView(pauseVideoButton);
                Log.d(TAG, "done clicking pause video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.resume_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( mPreview.isVideoRecordingPaused() );

                Log.d(TAG, "wait before stopping");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                Log.d(TAG, "takePhotoButton description: " + takePhotoButton.getContentDescription());
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( pauseVideoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.resume_video) ) );
                assertTrue( pauseVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( mPreview.isVideoRecordingPaused() );
                video_time = mPreview.getVideoTime();
                Log.d(TAG, "video time: " + video_time);
                assertTrue( video_time >= 3000 - time_tol_ms );
                assertTrue( video_time <= 3000 + time_tol_ms );

                Log.d(TAG, "about to click stop video");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking stop video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                return 1;
            }
        }, 5000, false, false);
    }

    private void subTestTakeVideoSnapshot() throws InterruptedException {
        Log.d(TAG, "subTestTakeVideoSnapshot");

        final View takePhotoVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        assertTrue( takePhotoVideoButton.getVisibility() == View.GONE );

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);

                Log.d(TAG, "wait before taking photo");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( takePhotoVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );

                Log.d(TAG, "about to click take photo snapshot");
                clickView(takePhotoVideoButton);
                Log.d(TAG, "done clicking take photo snapshot");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                waitForTakePhoto();

                assertTrue( takePhotoVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mPreview.isVideoRecordingPaused() );

                Log.d(TAG, "wait before stopping");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                assertTrue( takePhotoButton.getContentDescription().equals( mActivity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video) ) );
                assertTrue( takePhotoVideoButton.getVisibility() == View.VISIBLE );
                assertTrue( mPreview.isVideoRecording() );

                Log.d(TAG, "about to click stop video");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking stop video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                return 2;
            }
        }, 5000, false, false);

        mActivity.waitUntilImageQueueEmpty();
    }

    /** Test taking photo while recording video.
     */
    public void testTakeVideoSnapshot() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshot");

        setToDefault();

        if( !mPreview.supportsPhotoVideoRecording() ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }

        subTestTakeVideoSnapshot();
    }

    /** Test taking photo while recording video, with timer.
     */
    public void testTakeVideoSnapshotTimer() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshotTimer");

        setToDefault();

        if( !mPreview.supportsPhotoVideoRecording() ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getTimerPreferenceKey(), "5");
        editor.putBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), false);
        editor.apply();

        subTestTakeVideoSnapshot();
    }

    /** Test taking photo while recording video, with pause preview.
     */
    public void testTakeVideoSnapshotPausePreview() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshotPausePreview");

        setToDefault();

        if( !mPreview.supportsPhotoVideoRecording() ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
        editor.apply();

        subTestTakeVideoSnapshot();
    }

    /** Test taking photo while recording video at max video quality.
     */
    public void testTakeVideoSnapshotMax() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshotMax");

        setToDefault();

        if( !mPreview.supportsPhotoVideoRecording() ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), false), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6 or OnePlus 3T)
        editor.apply();
        updateForSettings();

        subTestTakeVideoSnapshot();
    }

    /** Set available memory to make sure that we stop before running out of memory.
     *  This test is fine-tuned to Nexus 6, OnePlus 3T, Nokia 8, Galaxy S10e as we measure hitting max filesize based on time.
     */
    public void testTakeVideoAvailableMemory() throws InterruptedException {
        Log.d(TAG, "testTakeVideoAvailableMemory");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            // as not fine-tuned to pre-Android 5 devices
            return;
        }
        setToDefault();

        mActivity.getApplicationInterface().test_set_available_memory = true;
        mActivity.getApplicationInterface().test_available_memory = 50000000;
        boolean is_nokia = Build.MANUFACTURER.toLowerCase(Locale.US).contains("hmd global");
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        if( is_nokia || is_samsung )
        {
            // Nokia 8 has much smaller video sizes, at least when recording with phone face down, so we both set
            // 4K, and lower test_available_memory.
            mActivity.getApplicationInterface().test_available_memory = 21000000; // must be at least MyApplicationInterface.getVideoMaxFileSizePref().min_free_filesize
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), false), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6 or OnePlus 3T)
            editor.apply();
            updateForSettings();
        }

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                // wait until automatically stops
                Log.d(TAG, "wait until video recording stops");
                long time_s = System.currentTimeMillis();
                long video_time_s = mPreview.getVideoTime();
                // simulate remaining memory now being reduced, so we don't keep trying to restart
                mActivity.getApplicationInterface().test_available_memory = 10000000;
                while( mPreview.isVideoRecording() ) {
                    assertTrue( System.currentTimeMillis() - time_s <= 35000 );
                    long video_time = mPreview.getVideoTime();
                    assertTrue( video_time >= video_time_s );
                }
                Log.d(TAG, "video recording now stopped");
                // now allow time for video recording to properly shut down
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                Log.d(TAG, "done waiting");

                return 1;
            }
        }, 5000, true, false);
    }

    /** Set available memory small enough to make sure we don't even attempt to record video.
     */
    public void testTakeVideoAvailableMemory2() throws InterruptedException {
        Log.d(TAG, "testTakeVideoAvailableMemory2");

        setToDefault();

        mActivity.getApplicationInterface().test_set_available_memory = true;
        mActivity.getApplicationInterface().test_available_memory = 5000000;

        subTestTakeVideo(false, false, true, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                // wait until automatically stops
                Log.d(TAG, "wait until video recording stops");
                assertFalse( mPreview.isVideoRecording() );
                Log.d(TAG, "video recording now stopped");
                return 0;
            }
        }, 5000, true, false);
    }

    /** Set maximum filesize so that we get approx 3s of video time. Check that recording stops and restarts within 10s.
     *  Then check recording stops again within 10s.
     *  On Android 8+, we use MediaRecorder.setNextOutputFile() (see Preview.onVideoInfo()), so instead we just wait 10s and
     *  check video is still recording, then expect at least 2 resultant video files. If this fails on Android 8+, ensure
     *  that the video lengths aren't too short (if less than 3s, we sometimes seem to fall back to the pre-Android 8
     *  behaviour, presumably because setNextOutputFile() can't take effect in time).
     *  This test is fine-tuned to Nexus 6, OnePlus 3T, Nokia 8, Galaxy S10e, as we measure hitting max filesize based on time.
     */
    public void testTakeVideoMaxFileSize1() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxFileSize1");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            // as not fine-tuned to pre-Android 5 devices
            return;
        }
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        boolean is_nokia = Build.MANUFACTURER.toLowerCase(Locale.US).contains("hmd global");
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        if( is_nokia || is_samsung ) {
            // Nokia 8 has much smaller video sizes, at least when recording with phone face down, so we also set 4K
            editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), false), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6)
            //editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "2000000"); // approx 3s on Nokia 8 at 4K
            editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "10000000"); // approx 3s on Nokia 8 at 4K
        }
        else {
            //editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId()), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6)
            //editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "15728640"); // approx 3-4s on Nexus 6 at 4K
            editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "9437184"); // approx 3-4s on Nexus 6 at FullHD
        }
        editor.apply();
        updateForSettings();

        int n_new_files = subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
                    assertTrue(mPreview.isVideoRecording());
                    Log.d(TAG, "wait");
                    try {
                        Thread.sleep(10000);
                    }
                    catch(InterruptedException e) {
                        e.printStackTrace();
                        assertTrue(false);
                    }
                    Log.d(TAG, "check still recording");
                    assertTrue(mPreview.isVideoRecording());
                    return -1; // the number of videos recorded can vary, as the max duration corresponding to max filesize can vary wildly, so we check the number of files afterwards (below)
                }

                // pre-Android 8 code:

                // wait until automatically stops
                Log.d(TAG, "wait until video recording stops");
                long time_s = System.currentTimeMillis();
                long video_time_s = mPreview.getVideoTime();
                while( mPreview.isVideoRecording() ) {
                    assertTrue( System.currentTimeMillis() - time_s <= 8000 );
                    long video_time = mPreview.getVideoTime();
                    assertTrue( video_time >= video_time_s );
                }
                Log.d(TAG, "video recording now stopped - wait for restart");
                video_time_s = mPreview.getVideoAccumulatedTime();
                Log.d(TAG, "video_time_s: " + video_time_s);
                // now ensure we'll restart within a reasonable time
                time_s = System.currentTimeMillis();
                while( !mPreview.isVideoRecording() ) {
                    long c_time = System.currentTimeMillis();
                    if( c_time - time_s > 10000 ) {
                        Log.e(TAG, "time: " + (c_time - time_s));
                    }
                    assertTrue( c_time - time_s <= 10000 );
                }
                // wait for stop again
                time_s = System.currentTimeMillis();
                while( mPreview.isVideoRecording() ) {
                    long c_time = System.currentTimeMillis();
                    if( c_time - time_s > 10000 ) {
                        Log.e(TAG, "time: " + (c_time - time_s));
                    }
                    assertTrue( c_time - time_s <= 10000 );
                    long video_time = mPreview.getVideoTime();
                    if( video_time < video_time_s )
                        Log.d(TAG, "compare: " + video_time_s + " to " + video_time);
                    assertTrue( video_time + 1 >= video_time_s );
                }
                Log.d(TAG, "video recording now stopped again");

                // now start again
                time_s = System.currentTimeMillis();
                while( !mPreview.isVideoRecording() ) {
                    long c_time = System.currentTimeMillis();
                    if( c_time - time_s > 10000 ) {
                        Log.e(TAG, "time: " + (c_time - time_s));
                    }
                    assertTrue( c_time - time_s <= 10000 );
                }
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }

                // now properly stop - need to wait first so that stopping video isn't ignored (due to too quick after video start)
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                Log.d(TAG, "about to click stop video");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking stop video");
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");
                Log.d(TAG, "wait for stop");
                try {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                Log.d(TAG, "done wait for stop");
                return -1; // the number of videos recorded can vary, as the max duration corresponding to max filesize can vary widly
            }
        }, 5000, true, false);

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            assertTrue( n_new_files >= 2 );
        }
    }

    /** Max filesize is for ~4.5s, and max duration is 5s, check we only get 1 video.
     *  This test is fine-tuned to OnePlus 3T, as we measure hitting max filesize based on time.
     */
    public void testTakeVideoMaxFileSize2() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxFileSize2");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            // as not fine-tuned to pre-Android 5 devices
            return;
        }
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), false), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6 or OnePlus 3T)
        //editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "23592960"); // approx 4.5s on Nexus 6 at 4K
        editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "35389440"); // approx 4.5s on OnePlus 3T at 4K
        editor.putString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "5");
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                // wait until automatically stops
                Log.d(TAG, "wait until video recording stops");
                long time_s = System.currentTimeMillis();
                long video_time_s = mPreview.getVideoTime();
                while( mPreview.isVideoRecording() ) {
                    assertTrue( System.currentTimeMillis() - time_s <= 8000 );
                    long video_time = mPreview.getVideoTime();
                    assertTrue( video_time >= video_time_s );
                }
                Log.d(TAG, "video recording now stopped - check we don't restart");
                video_time_s = mPreview.getVideoAccumulatedTime();
                Log.d(TAG, "video_time_s: " + video_time_s);
                // now ensure we don't restart
                time_s = System.currentTimeMillis();
                while( System.currentTimeMillis() - time_s <= 5000 ) {
                    assertFalse( mPreview.isVideoRecording() );
                }
                return 1;
            }
        }, 5000, true, false);
    }

    /* Max filesize for ~5s, max duration 7s, max n_repeats 1 - to ensure we're not repeating indefinitely.
     * This test is fine-tuned to OnePlus 3T, as we measure hitting max filesize based on time.
     */
    public void testTakeVideoMaxFileSize3() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxFileSize3");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            // as not fine-tuned to pre-Android 5 devices
            return;
        }
        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), false), "" + CamcorderProfile.QUALITY_HIGH); // set to highest quality (4K on Nexus 6)
        //editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "26214400"); // approx 5s on Nexus 6 at 4K
        //editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "15728640"); // approx 5s on Nexus 6 at 4K
        editor.putString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "26214400"); // approx 5s on OnePlus 3T at 4K
        editor.putString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "7");
        editor.putString(PreferenceKeys.getVideoRestartPreferenceKey(), "1");
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, false, false, new VideoTestCallback() {
            @Override
            public int doTest() {
                // wait until we should have stopped - 2x7s, but add 6s for each of 4 restarts
                Log.d(TAG, "wait until video recording completely stopped");
                try {
                    Thread.sleep(38000);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                    assertTrue(false);
                }
                Log.d(TAG, "ensure we've really stopped");
                long time_s = System.currentTimeMillis();
                while( System.currentTimeMillis() - time_s <= 5000 ) {
                    assertFalse( mPreview.isVideoRecording() );
                }
                return -1; // the number of videos recorded can very, as the max duration corresponding to max filesize can vary widly
            }
        }, 5000, true, false);
    }

    public void testTakeVideoStabilization() throws InterruptedException {
        Log.d(TAG, "testTakeVideoStabilization");

        setToDefault();

        if( !mPreview.supportsVideoStabilization() ) {
            Log.d(TAG, "video stabilization not supported");
            return;
        }
        assertFalse(mPreview.getCameraController().getVideoStabilization());

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getVideoStabilizationPreferenceKey(), true);
        editor.apply();
        updateForSettings();
        assertTrue(mPreview.getCameraController().getVideoStabilization());

        subTestTakeVideo(false, false, false, false, null, 5000, false, false);

        assertTrue(mPreview.getCameraController().getVideoStabilization());
    }

    public void testTakeVideoExposureLock() throws InterruptedException {
        Log.d(TAG, "testTakeVideoExposureLock");

        setToDefault();

        subTestTakeVideo(true, false, false, false, null, 5000, false, false);
    }

    public void testTakeVideoFocusArea() throws InterruptedException {
        Log.d(TAG, "testTakeVideoFocusArea");

        setToDefault();

        subTestTakeVideo(false, true, false, false, null, 5000, false, false);
    }

    public void testTakeVideoQuick() throws InterruptedException {
        Log.d(TAG, "testTakeVideoQuick");

        setToDefault();

        // still need a short delay (at least 500ms, otherwise Open Camera will ignore the repeated stop)
        subTestTakeVideo(false, false, false, false, null, 500, false, false);
    }

    // If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
    public void testTakeVideoQuickSAF() throws InterruptedException {
        Log.d(TAG, "testTakeVideoQuickSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        // still need a short delay (at least 500ms, otherwise Open Camera will ignore the repeated stop)
        subTestTakeVideo(false, false, false, false, null, 500, false, false);
    }

    public void testTakeVideoForceFailure() throws InterruptedException {
        Log.d(TAG, "testTakeVideoForceFailure");

        setToDefault();

        mActivity.getPreview().test_video_failure = true;
        subTestTakeVideo(false, false, true, false, null, 5000, false, false);
    }

    /* Test can be reliable on some devices, test no longer run as part of test suites.
     */
    public void testTakeVideo4K() throws InterruptedException {
        Log.d(TAG, "testTakeVideo4K");

        setToDefault();

        if( !mActivity.supportsForceVideo4K() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getForceVideo4KPreferenceKey(), true);
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, true, false, null, 5000, false, false);
    }

    /** Will likely be unreliable on OnePlus 3T and Galaxy S10e with Camera2.
     */
    public void testTakeVideoFPS() throws InterruptedException {
        Log.d(TAG, "testTakeVideoFPS");

        setToDefault();
        // different frame rates only reliable for Camera2, but at least make sure we don't crash on old api
        final int [] fps_values = mPreview.usingCamera2API() ? new int[]{15, 25, 30, 60, 120, 240} : new int[]{30};
        for(int fps_value : fps_values) {
            if( mPreview.usingCamera2API() ) {
                if( mPreview.getVideoQualityHander().videoSupportsFrameRate(fps_value) ) {
                    Log.d(TAG, "fps supported at normal speed: " + fps_value);
                }
                else if( mPreview.getVideoQualityHander().videoSupportsFrameRateHighSpeed(fps_value) ) {
                    Log.d(TAG, "fps supported at HIGH SPEED: " + fps_value);
                }
                else {
                    Log.d(TAG, "fps is NOT supported: " + fps_value);
                    continue;
                }
                boolean expect_high_speed;
                boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
                if( is_samsung ) {
                    // tested on Galaxy S10e at least
                    expect_high_speed = (fps_value > 60);
                }
                else {
                    expect_high_speed = (fps_value >= 60);
                }
                assertEquals(expect_high_speed, mPreview.fpsIsHighSpeed("" + fps_value));
            }

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getVideoFPSPreferenceKey(mPreview.getCameraId()), "" + fps_value);
            editor.apply();
            updateForSettings();

            Log.d(TAG, "test video with fps: " + fps_value);
            //boolean allow_failure = fps_value.equals("24") || fps_value.equals("25") || fps_value.equals("60");
            boolean allow_failure = false;
            subTestTakeVideo(false, false, allow_failure, false, null, 5000, false, false);
        }
    }

    /** Will likely be unreliable on OnePlus 3T and Galaxy S10e.
     *  Manual mode should be ignored by high speed video, but check this doesn't crash at least!
     */
    public void testTakeVideoFPSHighSpeedManual() throws InterruptedException {
        Log.d(TAG, "testTakeVideoFPSHighSpeedManual");

        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            return;
        }
        else if( !mPreview.supportsISORange() ) {
            return;
        }

        int fps_value = 120;
        if( mPreview.getVideoQualityHander().videoSupportsFrameRate(fps_value) ) {
            Log.d(TAG, "fps supported at normal speed: " + fps_value);
            return;
        }
        else if( !mPreview.getVideoQualityHander().videoSupportsFrameRateHighSpeed(fps_value) ) {
            Log.d(TAG, "fps is NOT supported: " + fps_value);
            return;
        }

        assertTrue( mPreview.fpsIsHighSpeed("" + fps_value) );

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoFPSPreferenceKey(mPreview.getCameraId()), "" + fps_value);
        editor.apply();
        updateForSettings();

        Log.d(TAG, "test video with fps: " + fps_value);

        switchToISO(100);

        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);

        // switch to video mode, ensure that exposure button disappears due to high speed video
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());
        assertTrue(exposureButton.getVisibility() == View.GONE);

        // test recording video
        subTestTakeVideo(false, false, false, false, null, 5000, false, false);

        // switch to photo mode, ensure that exposure button re-appears
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(!mPreview.isVideo());
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
    }

    /** Tests that video resolutions are stored separately for high speed fps, for Camera2.
     */
    public void testVideoFPSHighSpeed() {
        Log.d(TAG, "testVideoFPSHighSpeed");

        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            return;
        }

        int fps_value = 120;
        if( mPreview.getVideoQualityHander().videoSupportsFrameRate(fps_value) ) {
            Log.d(TAG, "fps supported at normal speed: " + fps_value);
            return;
        }
        else if( !mPreview.getVideoQualityHander().videoSupportsFrameRateHighSpeed(fps_value) ) {
            Log.d(TAG, "fps is NOT supported: " + fps_value);
            return;
        }

        assertTrue( mPreview.fpsIsHighSpeed("" + fps_value) );

        // switch to video mode
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        // get initial video resolution, for non-high-speed
        String saved_quality = mActivity.getApplicationInterface().getVideoQualityPref();
        VideoProfile profile = mPreview.getVideoProfile();
        int saved_video_width = profile.videoFrameWidth;
        int saved_video_height = profile.videoFrameHeight;
        Log.d(TAG, "saved_quality: " + saved_quality);
        Log.d(TAG, "saved_video_width: " + saved_video_width);
        Log.d(TAG, "saved_video_height: " + saved_video_height);

        // switch to high speed fps
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoFPSPreferenceKey(mPreview.getCameraId()), "" + fps_value);
        editor.apply();
        updateForSettings();

        Log.d(TAG, "test video with fps: " + fps_value);

        // change video resolution
        List<String> video_sizes = mPreview.getSupportedVideoQuality(mActivity.getApplicationInterface().getVideoFPSPref());
        assertTrue(video_sizes.size() >= 2);
        // find current index
        int video_size_index = -1;
        for(int i=0;i<video_sizes.size();i++) {
            String video_size = video_sizes.get(i);
            if( video_size.equals(mPreview.getVideoQualityHander().getCurrentVideoQuality()) ) {
                video_size_index = i;
                break;
            }
        }
        Log.d(TAG, "video_size_index: " + video_size_index);
        assertTrue(video_size_index != -1);
        // should have defaulted to largest resolution
        assertTrue(video_size_index == 0);
        video_size_index++;
        String quality = video_sizes.get(video_size_index);
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(mPreview.getCameraId(), mActivity.getApplicationInterface().fpsIsHighSpeed()), quality);
        editor.apply();
        updateForSettings();

        quality = mActivity.getApplicationInterface().getVideoQualityPref();
        profile = mPreview.getVideoProfile();
        int video_width = profile.videoFrameWidth;
        int video_height = profile.videoFrameHeight;
        Log.d(TAG, "quality: " + quality);
        Log.d(TAG, "video_width: " + video_width);
        Log.d(TAG, "video_height: " + video_height);
        assertFalse(saved_quality.equals(quality));
        assertFalse(video_width == saved_video_width && video_height == saved_video_height);
        String high_speed_quality = quality;
        int high_speed_video_width = video_width;
        int high_speed_video_height = video_height;

        // switch to normal fps
        Log.d(TAG, "switch to normal fps");
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoFPSPreferenceKey(mPreview.getCameraId()), "30");
        editor.apply();
        updateForSettings();

        // check resolution reverts to original
        quality = mActivity.getApplicationInterface().getVideoQualityPref();
        profile = mPreview.getVideoProfile();
        video_width = profile.videoFrameWidth;
        video_height = profile.videoFrameHeight;
        Log.d(TAG, "quality: " + quality);
        Log.d(TAG, "video_width: " + video_width);
        Log.d(TAG, "video_height: " + video_height);
        assertTrue(saved_quality.equals(quality));
        assertTrue(video_width == saved_video_width && video_height == saved_video_height);

        // switch to high speed fps again
        Log.d(TAG, "switch to high speed fps again");
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.putString(PreferenceKeys.getVideoFPSPreferenceKey(mPreview.getCameraId()), "" + fps_value);
        editor.apply();
        updateForSettings();

        // check resolution reverts to high speed
        quality = mActivity.getApplicationInterface().getVideoQualityPref();
        profile = mPreview.getVideoProfile();
        video_width = profile.videoFrameWidth;
        video_height = profile.videoFrameHeight;
        Log.d(TAG, "quality: " + quality);
        Log.d(TAG, "video_width: " + video_width);
        Log.d(TAG, "video_height: " + video_height);
        assertTrue(high_speed_quality.equals(quality));
        assertTrue(video_width == high_speed_video_width && video_height == high_speed_video_height);
    }

    /** Will likely be unreliable on OnePlus 3T and Galaxy S10e.
     */
    public void testTakeVideoSlowMotion() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSlowMotion");

        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            return;
        }

        List<Float> supported_capture_rates = mActivity.getApplicationInterface().getSupportedVideoCaptureRates();
        if( supported_capture_rates.size() <= 1 ) {
            Log.d(TAG, "slow motion not supported");
            return;
        }

        float capture_rate = supported_capture_rates.get(0);
        if( capture_rate > 1.0f-1.0e-5f ) {
            Log.d(TAG, "slow motion not supported");
            return;
        }
        Log.d(TAG, "capture_rate: " + capture_rate);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(mPreview.getCameraId()), capture_rate);
        editor.apply();
        updateForSettings();

        // switch to video, and check we've set a high speed fps
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        String fps_value = mActivity.getApplicationInterface().getVideoFPSPref();
        int fps = Integer.parseInt(fps_value);
        Log.d(TAG, "fps: " + fps);
        assertTrue(fps >= 60);
        assertTrue(mPreview.isVideoHighSpeed());

        // check video profile
        VideoProfile profile = mPreview.getVideoProfile();
        assertEquals(profile.videoCaptureRate, (double)fps, 1.0e-5);
        assertEquals((float)profile.videoFrameRate, (float)(profile.videoCaptureRate*capture_rate), 1.0e-5);

        boolean allow_failure = false;
        subTestTakeVideo(false, false, allow_failure, false, null, 5000, false, false);
    }

    /** Take video with timelapse mode.
     */
    public void testTakeVideoTimeLapse() throws InterruptedException {
        Log.d(TAG, "testTakeVideoTimeLapse");

        setToDefault();

        List<Float> supported_capture_rates = mActivity.getApplicationInterface().getSupportedVideoCaptureRates();
        if( supported_capture_rates.size() <= 1 ) {
            Log.d(TAG, "timelapse not supported");
            return;
        }

        float capture_rate = -1.0f;
        // find the first timelapse rate
        for(float this_capture_rate : supported_capture_rates) {
            if( this_capture_rate > 1.0f+1.0e-5f ) {
                capture_rate = this_capture_rate;
                break;
            }
        }
        if( capture_rate < 0.0f ) {
            Log.d(TAG, "timelapse not supported");
            return;
        }
        Log.d(TAG, "capture_rate: " + capture_rate);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(PreferenceKeys.getVideoCaptureRatePreferenceKey(mPreview.getCameraId()), capture_rate);
        editor.apply();
        updateForSettings();

        // switch to video, and check we've set a non-high speed fps
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        String fps_value = mActivity.getApplicationInterface().getVideoFPSPref();
        Log.d(TAG, "fps_value: " + fps_value);
        assertTrue(fps_value.equals("default"));
        assertTrue(!mPreview.isVideoHighSpeed());

        // check video profile
        VideoProfile profile = mPreview.getVideoProfile();
        // note, need to allow a larger delta, due to the fudge factor applied for 2x timelapse
        assertEquals((float)profile.videoFrameRate, (float)(profile.videoCaptureRate*capture_rate), 5.0e-3);

        boolean allow_failure = false;
        subTestTakeVideo(false, false, allow_failure, false, null, 5000, false, false);
    }

    /* Test can be reliable on some devices, test no longer run as part of test suites.
     */
    public void testTakeVideoBitrate() throws InterruptedException {
        Log.d(TAG, "testTakeVideoBitrate");

        setToDefault();
        final String [] bitrate_values = new String[]{"1000000", "10000000", "20000000", "50000000"};
        //final String [] bitrate_values = new String[]{"1000000", "10000000", "20000000", "30000000"};
        for(String bitrate_value : bitrate_values) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getVideoBitratePreferenceKey(), bitrate_value);
            editor.apply();
            updateForSettings();

            Log.d(TAG, "test video with bitrate: " + bitrate_value);
            boolean allow_failure = bitrate_value.equals("30000000") || bitrate_value.equals("50000000");
            subTestTakeVideo(false, false, allow_failure, false, null, 5000, false, false);
        }
    }

    /* Test recording video with a flat (log) profile.
     */
    public void testVideoLogProfile() throws InterruptedException {
        Log.d(TAG, "testVideoLogProfile");

        setToDefault();

        if( !mPreview.supportsTonemapCurve() ) {
            Log.d(TAG, "test requires tonemap curve");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "strong");
        editor.apply();
        updateForSettings();

        subTestTakeVideo(false, false, true, false, null, 5000, false, false);

        assertTrue( mPreview.getCameraController().test_used_tonemap_curve );
    }

    /* Test recording video with non-default edge and noise reduction modes.
     */
    public void testVideoEdgeModeNoiseReductionMode() throws InterruptedException {
        Log.d(TAG, "testVideoEdgeModeNoiseReductionMode");

        setToDefault();

        if( !mPreview.usingCamera2API() ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }

        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        Integer default_edge_mode = previewBuilder.get(CaptureRequest.EDGE_MODE);
        Integer default_noise_reduction_mode = previewBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.EdgeModePreferenceKey, "off");
        editor.putString(PreferenceKeys.CameraNoiseReductionModePreferenceKey, "fast");
        editor.apply();
        updateForSettings();

        if( mPreview.getSupportedEdgeModes() != null ) {
            Integer new_edge_mode = previewBuilder.get(CaptureRequest.EDGE_MODE);
            assertEquals(CameraMetadata.EDGE_MODE_OFF, new_edge_mode.intValue());
        }
        if( mPreview.getSupportedNoiseReductionModes() != null ) {
            Integer new_noise_reduction_mode = previewBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE);
            assertEquals(CameraMetadata.NOISE_REDUCTION_MODE_FAST, new_noise_reduction_mode.intValue());
        }

        subTestTakeVideo(false, false, true, false, null, 5000, false, false);

        editor = settings.edit();
        editor.putString(PreferenceKeys.EdgeModePreferenceKey, "default");
        editor.putString(PreferenceKeys.CameraNoiseReductionModePreferenceKey, "default");
        editor.apply();
        updateForSettings();

        camera_controller2 = (CameraController2)mPreview.getCameraController();
        previewBuilder = camera_controller2.testGetPreviewBuilder();
        if( mPreview.getSupportedEdgeModes() != null ) {
            Integer new_edge_mode = previewBuilder.get(CaptureRequest.EDGE_MODE);
            assertEquals(default_edge_mode, new_edge_mode);
        }
        if( mPreview.getSupportedNoiseReductionModes() != null ) {
            Integer new_noise_reduction_mode = previewBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE);
            assertEquals(default_noise_reduction_mode, new_noise_reduction_mode);
        }
    }

    private void subTestTakeVideoMaxDuration(boolean restart, boolean interrupt) throws InterruptedException {
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "15");
            if( restart ) {
                editor.putString(PreferenceKeys.getVideoRestartPreferenceKey(), "1");
            }
            editor.apply();
        }

        assertTrue(mPreview.isPreviewStarted());

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        //View flashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        // flash and focus etc default visibility tested in another test
        // but store status to compare with later
        //int flashVisibility = flashButton.getVisibility();
        //int focusVisibility = focusButton.getVisibility();
        int exposureVisibility = exposureButton.getVisibility();
        int exposureLockVisibility = exposureLockButton.getVisibility();
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        // workaround for Android 7.1 bug at https://stackoverflow.com/questions/47548317/what-belong-is-badtokenexception-at-classes-of-project
        // without this, we get a crash due to that problem on Nexus (old API at least) in testTakeVideoMaxDuration
        Thread.sleep(1000);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        assertTrue(switchCameraButton.getVisibility() == View.GONE);
        assertTrue(switchVideoButton.getVisibility() == View.GONE);
        //assertTrue(flashButton.getVisibility() == flashVisibility);
        //assertTrue(focusButton.getVisibility() == View.GONE);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == (mPreview.supportsFlash() ? View.VISIBLE : View.GONE)); // popup button only visible when recording video if flash supported
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);

        Thread.sleep(10000);
        Log.d(TAG, "check still taking video");
        assertTrue( mPreview.isVideoRecording() );

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);

        if( restart ) {
            if( interrupt ) {
                Thread.sleep(5100);
                restart();
                Log.d(TAG, "done restart");
                // now wait, and check we don't crash
                Thread.sleep(5000);
                return;
            }
            else {
                Thread.sleep(10000);
                Log.d(TAG, "check restarted video");
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( folder.exists() );
                n_new_files = getNFiles(folder) - n_files;
                Log.d(TAG, "n_new_files: " + n_new_files);
                assertTrue(n_new_files == 2);

                Thread.sleep(15000);
            }
        }
        else {
            Thread.sleep(8000);
        }
        Log.d(TAG, "check stopped taking video");
        assertTrue( !mPreview.isVideoRecording() );

        assertTrue( folder.exists() );
        n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == (restart ? 2 : 1));

        // trash/share only shown when preview is paused after taking a photo

        assertTrue(mPreview.isPreviewStarted()); // check preview restarted
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        //assertTrue(flashButton.getVisibility() == flashVisibility);
        //assertTrue(focusButton.getVisibility() == focusVisibility);
        assertTrue(exposureButton.getVisibility() == exposureVisibility);
        assertTrue(exposureLockButton.getVisibility() == exposureLockVisibility);
        assertTrue(audioControlButton.getVisibility() == (has_audio_control_button ? View.VISIBLE : View.GONE));
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
    }

    public void testTakeVideoMaxDuration() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxDuration");

        setToDefault();

        subTestTakeVideoMaxDuration(false, false);
    }

    public void testTakeVideoMaxDurationRestart() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxDurationRestart");

        setToDefault();

        subTestTakeVideoMaxDuration(true, false);
    }

    public void testTakeVideoMaxDurationRestartInterrupt() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMaxDurationRestartInterrupt");

        setToDefault();

        subTestTakeVideoMaxDuration(true, true);
    }

    public void testTakeVideoSettings() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSettings");

        setToDefault();

        assertTrue(mPreview.isPreviewStarted());

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        Thread.sleep(2000);
        Log.d(TAG, "check still taking video");
        assertTrue( mPreview.isVideoRecording() );

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);

        // now go to settings
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        Log.d(TAG, "about to click settings");
        clickView(settingsButton);
        Log.d(TAG, "done clicking settings");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue( !mPreview.isVideoRecording() );

        assertTrue( folder.exists() );
        n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);

        Thread.sleep(500);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "on back pressed...");
                mActivity.onBackPressed();
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
        assertTrue( !mPreview.isVideoRecording() );

        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        assertTrue( folder.exists() );
        n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 2);

    }

    /** Switch to non-default focus, go to settings, check still in focus mode that we set, then test recording.
     */
    public void testTakeVideoMacro() throws InterruptedException {
        Log.d(TAG, "testTakeVideoMacro");

        setToDefault();

        if( !mPreview.supportsFocus() ) {
            return;
        }

        assertTrue(mPreview.isPreviewStarted());

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());

        String non_default_focus_mode = getNonDefaultFocus();
        switchToFocusValue(non_default_focus_mode);

        // now go to settings
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        Log.d(TAG, "about to click settings");
        clickView(settingsButton);
        Log.d(TAG, "done clicking settings");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue( !mPreview.isVideoRecording() );

        Thread.sleep(500);

        assertTrue(mPreview.getCurrentFocusValue().equals(non_default_focus_mode));

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "on back pressed...");
                mActivity.onBackPressed();
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        Thread.sleep(500);

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        Thread.sleep(2000);
        Log.d(TAG, "check still taking video");
        assertTrue( mPreview.isVideoRecording() );

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == 1);

    }

    public void testTakeVideoFlashVideo() throws InterruptedException {
        Log.d(TAG, "testTakeVideoFlashVideo");

        setToDefault();

        if( !mPreview.supportsFlash() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getVideoFlashPreferenceKey(), true);
        editor.apply();
        updateForSettings();

        assertTrue(mPreview.isPreviewStarted());

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        if( !mPreview.isVideo() ) {
            clickView(switchVideoButton);
            waitUntilCameraOpened();
        }
        assertTrue(mPreview.isVideo());
        assertTrue(mPreview.isPreviewStarted());

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        Thread.sleep(1500);
        Log.d(TAG, "check still taking video");
        assertTrue( mPreview.isVideoRecording() );

        // wait until flash off
        long time_s = System.currentTimeMillis();
        while (mPreview.getCameraController().getFlashValue().equals("flash_torch")) {
            assertTrue(System.currentTimeMillis() - time_s <= 200);
        }

        // wait until flash on
        time_s = System.currentTimeMillis();
        while (!mPreview.getCameraController().getFlashValue().equals("flash_torch")) {
            assertTrue(System.currentTimeMillis() - time_s <= 1100);
        }

        // wait until flash off
        time_s = System.currentTimeMillis();
        while (mPreview.getCameraController().getFlashValue().equals("flash_torch")) {
            assertTrue(System.currentTimeMillis() - time_s <= 200);
        }

        // wait until flash on
        time_s = System.currentTimeMillis();
        while (!mPreview.getCameraController().getFlashValue().equals("flash_torch")) {
            assertTrue(System.currentTimeMillis() - time_s <= 1100);
        }

        Log.d(TAG, "about to click stop video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking stop video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        // test flash now off
        assertTrue( !mPreview.getCameraController().getFlashValue().equals("flash_torch") );
    }

    // type: 0 - go to background; 1 - go to settings; 2 - go to popup
    private void subTestTimer(int type) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getTimerPreferenceKey(), "10");
        editor.putBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), false);
        editor.apply();

        assertTrue(!mPreview.isOnTimer());

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");
        assertTrue(mPreview.isOnTimer());
        assertTrue(mPreview.count_cameraTakePicture==0);

        try {
            // wait 2s, and check we are still on timer, and not yet taken a photo
            Thread.sleep(2000);
            assertTrue(mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==0);
            // quit and resume
            if( type == 0 )
                restart();
            else if( type == 1 ) {
                View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
                Log.d(TAG, "about to click settings");
                clickView(settingsButton);
                Log.d(TAG, "done clicking settings");
                this.getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "on back pressed...");
                        mActivity.onBackPressed();
                    }
                });
                // need to wait for UI code to finish before leaving
                this.getInstrumentation().waitForIdleSync();
                Thread.sleep(500);
            }
            else {
                View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
                clickView(popupButton);
                while( !mActivity.popupIsOpen() ) {
                }
            }
            takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            // check timer cancelled, and not yet taken a photo
            assertTrue(!mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==0);
            int n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 0);

            // start timer again
            Log.d(TAG, "about to click take photo");
            assertTrue(mPreview.getCameraController() != null);
            clickView(takePhotoButton);
            assertTrue(mPreview.getCameraController() != null);
            Log.d(TAG, "done clicking take photo");
            assertTrue(mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==0);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 0);

            // wait 15s, and ensure we took a photo
            Thread.sleep(15000);
            Log.d(TAG, "waited, count now " + mPreview.count_cameraTakePicture);
            assertTrue(!mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==1);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 1);

            // now set timer to 5s, and turn on pause_preview
            editor.putString(PreferenceKeys.getTimerPreferenceKey(), "5");
            editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
            editor.apply();

            Log.d(TAG, "about to click take photo");
            assertTrue(mPreview.getCameraController() != null);
            clickView(takePhotoButton);
            assertTrue(mPreview.getCameraController() != null);
            Log.d(TAG, "done clicking take photo");
            assertTrue(mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==1);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 1);

            // wait 10s, and ensure we took a photo
            Thread.sleep(10000);
            Log.d(TAG, "waited, count now " + mPreview.count_cameraTakePicture);
            assertTrue(!mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==2);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 2);

            // now test cancelling
            Log.d(TAG, "about to click take photo");
            assertTrue(mPreview.getCameraController() != null);
            clickView(takePhotoButton);
            assertTrue(mPreview.getCameraController() != null);
            Log.d(TAG, "done clicking take photo");
            assertTrue(mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==2);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 2);

            // wait 2s, and cancel
            Thread.sleep(2000);
            Log.d(TAG, "about to click take photo to cance");
            assertTrue(mPreview.getCameraController() != null);
            clickView(takePhotoButton);
            assertTrue(mPreview.getCameraController() != null);
            Log.d(TAG, "done clicking take photo to cancel");
            assertTrue(!mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==2);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 2);

            // wait 8s, and ensure we didn't take a photo
            Thread.sleep(8000);
            Log.d(TAG, "waited, count now " + mPreview.count_cameraTakePicture);
            assertTrue(!mPreview.isOnTimer());
            assertTrue(mPreview.count_cameraTakePicture==2);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 2);
        }
        catch(InterruptedException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    /* Test with 10s timer, start a photo, go to background, then back, then take another photo. We should only take 1 photo - the original countdown should not be active (nor should we crash)!
     */
    public void testTimerBackground() {
        Log.d(TAG, "testTimerBackground");
        setToDefault();

        subTestTimer(0);
    }

    /* Test and going to settings.
     */
    public void testTimerSettings() {
        Log.d(TAG, "testTimerSettings");
        setToDefault();

        subTestTimer(1);
    }

    /* Test and going to popup.
     */
    public void testTimerPopup() {
        Log.d(TAG, "testTimerPopup");
        setToDefault();

        subTestTimer(2);
    }

    /* Takes video on a timer, but interrupts with restart.
     */
    public void testVideoTimerInterrupt() {
        Log.d(TAG, "testVideoTimerInterrupt");
        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getTimerPreferenceKey(), "5");
        editor.putBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), false);
        editor.apply();

        assertTrue(!mPreview.isOnTimer());

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");
        assertTrue(mPreview.isOnTimer());
        assertTrue(mPreview.count_cameraTakePicture==0);

        try {
            // wait a moment after 5s, then restart
            Thread.sleep(5100);
            assertTrue(mPreview.count_cameraTakePicture==0);
            // quit and resume
            restart();
            Log.d(TAG, "done restart");

            // check timer cancelled; may or may not have managed to take a photo
            assertTrue(!mPreview.isOnTimer());
        }
        catch(InterruptedException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    /* Tests that selecting a new flash and focus option, then reopening the popup menu, still has the correct option highlighted.
     */
    public void testPopup() {
        Log.d(TAG, "testPopup");
        setToDefault();

        switchToFlashValue("flash_off");
        switchToFlashValue("flash_on");

        if( mPreview.getSupportedFocusValues().contains("focus_mode_macro") ) {
            switchToFocusValue("focus_mode_macro");
        }
        else if( mPreview.getSupportedFocusValues().contains("focus_mode_infinity") ) {
            switchToFocusValue("focus_mode_infinity");
        }

        if( mPreview.getSupportedFocusValues().contains("focus_mode_auto") ) {
            switchToFocusValue("focus_mode_auto");
        }

        // now open popup, pause and resume, then reopen popup
        // this tests against a crash, if we don't remove the popup from the popup container in MainUI.destroyPopup()
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        assertTrue(!mActivity.popupIsOpen());
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        pauseAndResume();
        assertTrue(!mActivity.popupIsOpen());
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }
    }

    /* Tests against a bug where popup wouldn't show with left UI placement, due to 0 popup view height.
     */
    public void testPopupLeftLayout() {
        Log.d(TAG, "testPopupLeftLayout");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.UIPlacementPreferenceKey, "ui_left");
        editor.apply();
        updateForSettings();

        View popup_view = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup_container);

        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        assertTrue(!mActivity.popupIsOpen());
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        int popup_width = popup_view.getWidth();
        int popup_height = popup_view.getHeight();
        int test_popup_width = mActivity.getMainUI().test_saved_popup_width;
        int test_popup_height = mActivity.getMainUI().test_saved_popup_height;
        Log.d(TAG, "popup_width: " + popup_width);
        Log.d(TAG, "popup_height: " + popup_height);
        Log.d(TAG, "test_popup_width: " + test_popup_width);
        Log.d(TAG, "test_popup_height: " + test_popup_height);
        assertTrue(popup_width > 0);
        assertTrue(popup_height > 0);
        assertEquals(popup_width, test_popup_width);
        assertEquals(popup_height, test_popup_height);

        // now reopen popup view, and check the same dimensions
        clickView(popupButton);
        while( mActivity.popupIsOpen() ) {
        }

        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        int new_popup_width = popup_view.getWidth();
        int new_popup_height = popup_view.getHeight();
        test_popup_width = mActivity.getMainUI().test_saved_popup_width;
        test_popup_height = mActivity.getMainUI().test_saved_popup_height;
        Log.d(TAG, "new_popup_width: " + new_popup_width);
        Log.d(TAG, "new_popup_height: " + new_popup_height);
        Log.d(TAG, "test_popup_width: " + test_popup_width);
        Log.d(TAG, "test_popup_height: " + test_popup_height);
        assertEquals(popup_width, new_popup_width);
        assertEquals(popup_height, new_popup_height);
        assertEquals(popup_width, test_popup_width);
        assertEquals(popup_height, test_popup_height);
    }

    /* Tests with ui_right vs ui_top layout.
     */
    public void testRightLayout() {
        Log.d(TAG, "testRightLayout");

        setToDefault();
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.UIPlacementPreferenceKey, "ui_right");
            editor.apply();
            updateForSettings();
        }

        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        assertTrue(!mActivity.popupIsOpen());
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        Point display_size = new Point();
        {
            Display display = mActivity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
            Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        View galleryButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.gallery);

        Log.d(TAG, "settings right: " + settingsButton.getRight());
        Log.d(TAG, "settings top: " + settingsButton.getTop());
        Log.d(TAG, "gallery right: " + galleryButton.getRight());
        Log.d(TAG, "gallery top: " + galleryButton.getTop());

        assertTrue(settingsButton.getRight() > (int)(0.8*display_size.x));
        assertEquals(0, settingsButton.getTop());
        assertEquals(display_size.x, galleryButton.getRight());
        assertEquals(0, galleryButton.getTop());

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.UIPlacementPreferenceKey, "ui_top");
            editor.apply();
            updateForSettings();
        }

        Log.d(TAG, "settings right: " + settingsButton.getRight());
        Log.d(TAG, "settings top: " + settingsButton.getTop());
        Log.d(TAG, "gallery right: " + galleryButton.getRight());
        Log.d(TAG, "gallery top: " + galleryButton.getTop());

        assertTrue(settingsButton.getRight() < (int)(0.2*display_size.x));
        assertEquals(0, settingsButton.getTop());
        assertEquals(display_size.x, galleryButton.getRight());
        assertEquals(0, galleryButton.getTop());

        assertTrue(!mActivity.popupIsOpen());
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }
    }

    /* Tests layout bug with popup menu.
     * Note, in practice this doesn't seem to reproduce the problem, but keep the test anyway.
     * Currently not autotested as the problem isn't fixed, and this would just be a test that
     * occasionally fails (instead we work round the problem but not caching the popup when the
     * bug occurs).
     */
    public void testPopupLayout() throws InterruptedException {
        Log.d(TAG, "testPopupLayout");
        setToDefault();

        for(int i=0;i<50;i++) {
            View popup_container = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup_container);
            View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
            final float scale = mActivity.getResources().getDisplayMetrics().density;
            int max_width = (int) (280 * scale + 0.5f); // convert dps to pixels;

            Thread.sleep(400);

            // open popup
            assertTrue(!mActivity.popupIsOpen());
            clickView(popupButton);
            while( !mActivity.popupIsOpen() ) {
            }

            // check popup width is not larger than expected
            int popup_container_width = popup_container.getWidth();
            Log.d(TAG, "i = : " + i);
            Log.d(TAG, "    popup_container_width: " + popup_container_width);
            Log.d(TAG, "    max_width: " + max_width);
            assertTrue(popup_container_width <= max_width);

            /*View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
            Log.d(TAG, "about to click settings");
            clickView(settingsButton);
            Log.d(TAG, "done clicking settings");
            this.getInstrumentation().waitForIdleSync();*/

            if( i % 10 == 0 ) {
                restart();
            }
            else {
                pauseAndResume();
            }
        }
    }

    /* Tests to do with video and popup menu.
     */
    private void subTestVideoPopup(boolean on_timer) {
        Log.d(TAG, "subTestVideoPopup");

        assertTrue(!mPreview.isOnTimer());
        assertTrue(!mActivity.popupIsOpen());
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);

        if( !mPreview.isVideo() ) {
            View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
            clickView(switchVideoButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.isVideo());
        }

        if( !on_timer ) {
            // open popup now
            clickView(popupButton);
            while( !mActivity.popupIsOpen() ) {
            }
        }

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");
        if( on_timer ) {
            assertTrue(mPreview.isOnTimer());
        }

        try {
            if( on_timer ) {
                Thread.sleep(2000);

                // now open popup
                clickView(popupButton);
                while( !mActivity.popupIsOpen() ) {
                }

                // check timer is cancelled
                assertTrue( !mPreview.isOnTimer() );

                // wait for timer (if it was still going)
                Thread.sleep(4000);

                // now check we still aren't recording, and that popup is still open
                assertTrue( mPreview.isVideo() );
                assertTrue( !mPreview.isVideoRecording() );
                assertTrue( !mPreview.isOnTimer() );
                assertTrue( mActivity.popupIsOpen() );
            }
            else {
                Thread.sleep(1000);

                // now check we are recording video, and that popup is closed
                assertTrue( mPreview.isVideo() );
                assertTrue( mPreview.isVideoRecording() );
                assertTrue( !mActivity.popupIsOpen() );
            }

            if( !on_timer ) {
                // (if on timer, the video will have stopped)
                List<String> supported_flash_values = mPreview.getSupportedFlashValues();
                if( supported_flash_values == null ) {
                    // button shouldn't show at all
                    assertTrue( popupButton.getVisibility() == View.GONE );
                }
                else {
                    // now open popup again
                    clickView(popupButton);
                    while( !mActivity.popupIsOpen() ) {
                    }
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_off", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_auto", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_on", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_torch", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_red_eye", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_frontscreen_auto", supported_flash_values);
                    subTestPopupButtonAvailability("TEST_FLASH", "flash_frontscreen_on", supported_flash_values);
                    // only flash should be available
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_auto", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_locked", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_infinity", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_macro", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_fixed", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_edof", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_continuous_picture", null);
                    subTestPopupButtonAvailability("TEST_FOCUS", "focus_mode_continuous_video", null);
                    subTestPopupButtonAvailability("TEST_ISO", "auto", null);
                    subTestPopupButtonAvailability("TEST_ISO", "100", null);
                    subTestPopupButtonAvailability("TEST_ISO", "200", null);
                    subTestPopupButtonAvailability("TEST_ISO", "400", null);
                    subTestPopupButtonAvailability("TEST_ISO", "800", null);
                    subTestPopupButtonAvailability("TEST_ISO", "1600", null);
                    subTestPopupButtonAvailability("TEST_WHITE_BALANCE", false);
                    subTestPopupButtonAvailability("TEST_SCENE_MODE", false);
                    subTestPopupButtonAvailability("TEST_COLOR_EFFECT", false);
                }
            }

            Log.d(TAG, "now stop video");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking stop video");
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
            assertTrue( !mPreview.isVideoRecording() );
            assertTrue( !mActivity.popupIsOpen() );

        }
        catch(InterruptedException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // now open popup again
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }
        subTestPopupButtonAvailability();
    }

    /* Tests that popup menu closes when we record video; then tests behaviour of popup.
     */
    public void testVideoPopup() {
        Log.d(TAG, "testVideoPopup");
        setToDefault();

        subTestVideoPopup(false);

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            subTestVideoPopup(false);
        }
    }

    /* Takes video on a timer, but checks that the popup menu stops video timer; then tests behaviour of popup.
     */
    public void testVideoTimerPopup() {
        Log.d(TAG, "testVideoTimerPopup");
        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getTimerPreferenceKey(), "5");
        editor.putBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), false);
        editor.apply();

        subTestVideoPopup(true);

        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            subTestVideoPopup(true);
        }
    }

    /* Tests for USB/bluetooth keyboard controls.
     */
    public void testKeyboardControls() throws InterruptedException {
        Log.d(TAG, "testKeyboardControls");

        setToDefault();

        if( !mPreview.supportsFlash() ) {
            Log.d(TAG, "doesn't support flash");
            return;
        }
        else if( !mPreview.supportsFocus() ) {
            Log.d(TAG, "doesn't support focus");
            return;
        }

        switchToFlashValue("flash_auto");

        // open popup
        assertFalse( mActivity.popupIsOpen() );
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_FUNCTION);
        getInstrumentation().waitForIdleSync();
        assertTrue( mActivity.popupIsOpen() );

        // arrow down
        assertFalse(mActivity.getMainUI().testGetRemoteControlMode());
        assertFalse(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // arrow down again
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_2);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertEquals(1, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // arrow down again
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_2);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertEquals(3, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // arrow up
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertEquals(1, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // arrow up again
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_8);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // select
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_5);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertTrue(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // arrow down
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertTrue(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(1, mActivity.getMainUI().testGetPopupIcon());

        // arrow down again
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_2);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertTrue(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(2, mActivity.getMainUI().testGetPopupIcon());

        // arrow up
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertTrue(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(1, mActivity.getMainUI().testGetPopupIcon());

        // arrow up again
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_8);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertTrue(mActivity.getMainUI().selectingLines());
        assertTrue(mActivity.getMainUI().selectingIcons());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());

        // select
        assertEquals("flash_auto", mPreview.getCurrentFlashValue());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_5);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
        assertFalse( mActivity.popupIsOpen() );
        assertEquals("flash_off", mPreview.getCurrentFlashValue());
        assertFalse(mActivity.getMainUI().testGetRemoteControlMode());
        assertFalse(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());

        Thread.sleep(500);

        // open exposure panel
        assertFalse( mActivity.getMainUI().isExposureUIOpen() );
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_SLASH);
        getInstrumentation().waitForIdleSync();
        assertTrue( mActivity.getMainUI().isExposureUIOpen() );

        assertFalse(mActivity.getMainUI().testGetRemoteControlMode());
        if( mPreview.supportsISORange() || mPreview.getSupportedISOs() != null ) {
            // need to skip past the ISO line
            assertFalse(mActivity.getMainUI().selectingLines());
            assertFalse(mActivity.getMainUI().selectingIcons());
            getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_2);
            getInstrumentation().waitForIdleSync();
            assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
            assertFalse(mActivity.getMainUI().selectingLines());
            assertFalse(mActivity.getMainUI().selectingIcons());
            assertFalse(mActivity.getMainUI().isSelectingExposureUIElement());
            assertEquals(0, mActivity.getMainUI().testGetPopupLine());
            assertEquals(0, mActivity.getMainUI().testGetPopupIcon());
            assertEquals(0, mActivity.getMainUI().testGetExposureLine());
        }

        // arrow down
        assertFalse(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertFalse(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertFalse(mActivity.getMainUI().isSelectingExposureUIElement());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());
        assertEquals(3, mActivity.getMainUI().testGetExposureLine());

        // select
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_5);
        getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getMainUI().testGetRemoteControlMode());
        assertFalse(mActivity.getMainUI().selectingLines());
        assertFalse(mActivity.getMainUI().selectingIcons());
        assertTrue(mActivity.getMainUI().isSelectingExposureUIElement());
        assertEquals(0, mActivity.getMainUI().testGetPopupLine());
        assertEquals(0, mActivity.getMainUI().testGetPopupIcon());
        assertEquals(3, mActivity.getMainUI().testGetExposureLine());

        // arrow down
        for(int i=0;i<6;i++) {
            assertEquals(-i, mPreview.getCurrentExposure());
            getInstrumentation().sendKeyDownUpSync((i%2==0) ? KeyEvent.KEYCODE_NUMPAD_2 : KeyEvent.KEYCODE_DPAD_DOWN);
            getInstrumentation().waitForIdleSync();
            assertEquals(-(i+1), mPreview.getCurrentExposure());
        }

        // arrow up
        for(int i=0;i<6;i++) {
            assertEquals(-6+i, mPreview.getCurrentExposure());
            getInstrumentation().sendKeyDownUpSync((i%2==0) ? KeyEvent.KEYCODE_NUMPAD_8 : KeyEvent.KEYCODE_DPAD_UP);
            getInstrumentation().waitForIdleSync();
            assertEquals(-6+(i+1), mPreview.getCurrentExposure());
        }

        // close exposure panel
        assertTrue( mActivity.getMainUI().isExposureUIOpen() );
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_SLASH);
        getInstrumentation().waitForIdleSync();
        assertFalse( mActivity.getMainUI().isExposureUIOpen() );

        // take photo
        assertTrue(mPreview.count_cameraTakePicture==0);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_NUMPAD_5);
        getInstrumentation().waitForIdleSync();
        waitForTakePhoto();
        assertTrue(mPreview.count_cameraTakePicture==1);
        mActivity.waitUntilImageQueueEmpty();

        // open settings
        assertFalse(mActivity.isCameraInBackground());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.isCameraInBackground());

        //Thread.sleep(3000);
        Thread.sleep(500);
    }

    /* Tests taking photos repeatedly with auto-repeat method.
     */
    public void testTakePhotoRepeat() {
        Log.d(TAG, "testTakePhotoRepeat");
        setToDefault();

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getRepeatModePreferenceKey(), "3");
            editor.apply();
        }

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");
        assertTrue(!mPreview.isOnTimer());

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);

        try {
            // wait 7s, and test that we've taken the photos by then
            Thread.sleep(7000);
            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            Log.d(TAG, "count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
            assertTrue(mPreview.count_cameraTakePicture==3);
            int n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 3);

            // now test pausing and resuming
            pauseAndResume();
            // wait 5s, and test that we haven't taken any photos
            Thread.sleep(5000);
            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            Log.d(TAG, "mPreview.count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
            assertTrue(mPreview.count_cameraTakePicture==3);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 3);

            // test with preview paused
            {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, true);
                editor.apply();
            }
            clickView(takePhotoButton);
            Thread.sleep(7000);
            assertTrue(mPreview.count_cameraTakePicture==6);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 6);
            assertTrue(!mPreview.isPreviewStarted()); // check preview paused

            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            this.getInstrumentation().waitForIdleSync();
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 6);
            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
                editor.apply();
            }

            // now test repeat interval
            {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PreferenceKeys.getRepeatModePreferenceKey(), "2");
                editor.putString(PreferenceKeys.getRepeatIntervalPreferenceKey(), "3");
                editor.putBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), false);
                editor.apply();
            }
            assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
            assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureButton.getVisibility() == (mPreview.supportsExposures() ? View.VISIBLE : View.GONE));
            assertTrue(exposureLockButton.getVisibility() == (mPreview.supportsExposureLock() ? View.VISIBLE : View.GONE));
            assertTrue(popupButton.getVisibility() == View.VISIBLE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);

            clickView(takePhotoButton);
            waitForTakePhoto();
            Log.d(TAG, "done taking 1st photo");
            this.getInstrumentation().waitForIdleSync();
            assertTrue(mPreview.count_cameraTakePicture==7);
            mActivity.waitUntilImageQueueEmpty();
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 7);

            // wait 2s, should still not have taken another photo
            Thread.sleep(2000);
            assertTrue(mPreview.count_cameraTakePicture==7);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 7);
            // check GUI has returned to correct state
            assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
            assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
            assertTrue(exposureButton.getVisibility() == (mPreview.supportsExposures() ? View.VISIBLE : View.GONE));
            assertTrue(exposureLockButton.getVisibility() == (mPreview.supportsExposureLock() ? View.VISIBLE : View.GONE));
            assertTrue(popupButton.getVisibility() == View.VISIBLE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);

            // wait another 5s, should have taken another photo (need to allow time for the extra auto-focus)
            Thread.sleep(5000);
            assertTrue(mPreview.count_cameraTakePicture==8);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 8);
            // wait 4s, should not have taken any more photos
            Thread.sleep(4000);
            assertTrue(mPreview.count_cameraTakePicture==8);
            n_new_files = getNFiles(folder) - n_files;
            Log.d(TAG, "n_new_files: " + n_new_files);
            assertTrue(n_new_files == 8);
        }
        catch(InterruptedException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    /* Tests that saving quality (i.e., resolution) settings can be done per-camera. Also checks that the supported picture sizes is as expected.
     */
    public void testSaveQuality() {
        Log.d(TAG, "testSaveQuality");

        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        List<CameraController.Size> picture_sizes = mPreview.getSupportedPictureSizes(true);

        // change back camera to the last size
        CameraController.Size size = picture_sizes.get(picture_sizes.size()-1);
        {
            Log.d(TAG, "set size to " + size.width + " x " + size.height);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getResolutionPreferenceKey(mPreview.getCameraId()), size.width + " " + size.height);
            editor.apply();
        }

        // need to resume activity for it to take effect (for camera to be reopened)
        pauseAndResume();
        CameraController.Size new_size = mPreview.getCameraController().getPictureSize();
        Log.d(TAG, "size is now " + new_size.width + " x " + new_size.height);
        assertTrue(size.equals(new_size));

        // switch camera to front
        int cameraId = mPreview.getCameraId();
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();
        int new_cameraId = mPreview.getCameraId();
        assertTrue(cameraId != new_cameraId);

        List<CameraController.Size> front_picture_sizes = mPreview.getSupportedPictureSizes(true);

        // change front camera to the last size
        CameraController.Size front_size = front_picture_sizes.get(front_picture_sizes.size()-1);
        {
            Log.d(TAG, "set front_size to " + front_size.width + " x " + front_size.height);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getResolutionPreferenceKey(mPreview.getCameraId()), front_size.width + " " + front_size.height);
            editor.apply();
        }

        // need to resume activity for it to take effect (for camera to be reopened)
        pauseAndResume();
        // check still on front camera
        Log.d(TAG, "camera id " + mPreview.getCameraId());
        assertTrue(mPreview.getCameraId() == new_cameraId);
        CameraController.Size front_new_size = mPreview.getCameraController().getPictureSize();
        Log.d(TAG, "front size is now " + front_new_size.width + " x " + front_new_size.height);
        assertTrue(front_size.equals(front_new_size));

        // change front camera to the first size
        front_size = front_picture_sizes.get(0);
        {
            Log.d(TAG, "set front_size to " + front_size.width + " x " + front_size.height);
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getResolutionPreferenceKey(mPreview.getCameraId()), front_size.width + " " + front_size.height);
            editor.apply();
        }

        // need to resume activity for it to take effect (for camera to be reopened)
        pauseAndResume();
        front_new_size = mPreview.getCameraController().getPictureSize();
        Log.d(TAG, "front size is now " + front_new_size.width + " x " + front_new_size.height);
        assertTrue(front_size.equals(front_new_size));

        // return to back camera
        switchToCamera(cameraId);

        // now back camera size should still be what it was
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            String settings_size = settings.getString(PreferenceKeys.getResolutionPreferenceKey(mPreview.getCameraId()), "");
            Log.d(TAG, "settings key is " + PreferenceKeys.getResolutionPreferenceKey(mPreview.getCameraId()));
            Log.d(TAG, "settings size is " + settings_size);
        }
        new_size = mPreview.getCameraController().getPictureSize();
        Log.d(TAG, "size is now " + new_size.width + " x " + new_size.height);
        assertTrue(size.equals(new_size));
    }

    private void testExif(String file, boolean expect_gps) throws IOException {
        //final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
        //final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";
        ExifInterface exif = new ExifInterface(file);
        assertTrue(exif.getAttribute(ExifInterface.TAG_ORIENTATION) != null);
        assertTrue(exif.getAttribute(ExifInterface.TAG_MAKE) != null);
        assertTrue(exif.getAttribute(ExifInterface.TAG_MODEL) != null);
        if( expect_gps ) {
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) != null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) != null);
            // can't read custom tags, even though we can write them?!
            //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION) != null);
            //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION_REF) != null);
        }
        else {
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) == null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) == null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) == null);
            assertTrue(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) == null);
            // can't read custom tags, even though we can write them?!
            //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION) == null);
            //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION_REF) == null);
        }
    }

    private void subTestLocationOn(boolean gps_direction) throws IOException {
        Log.d(TAG, "subTestLocationOn");

        assertTrue(!mActivity.getLocationSupplier().hasLocationListeners());
        Log.d(TAG, "turn on location");
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.LocationPreferenceKey, true);
            if( gps_direction ) {
                editor.putBoolean(PreferenceKeys.GPSDirectionPreferenceKey, true);
            }
            editor.apply();
            Log.d(TAG, "update settings after turning on location");
            updateForSettings();
            Log.d(TAG, "location should now be on");
        }

        assertTrue(mActivity.getLocationSupplier().hasLocationListeners());
        Log.d(TAG, "wait until received location");

        long start_t = System.currentTimeMillis();
        while( !mActivity.getLocationSupplier().testHasReceivedLocation() ) {
            this.getInstrumentation().waitForIdleSync();
            if( System.currentTimeMillis() - start_t > 20000 ) {
                // need to allow long time for testing devices without mobile network; will likely fail altogether if don't even have wifi
                assertTrue(false);
            }
        }
        Log.d(TAG, "have received location");
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getLocationSupplier().getLocation() != null);
        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        mActivity.test_last_saved_image = null;
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mPreview.count_cameraTakePicture==1);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, true);

        // now test with auto-stabilise
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
            editor.apply();
            updateForSettings();
        }
        mActivity.test_last_saved_image = null;
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mPreview.count_cameraTakePicture==2);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, true);

        // switch to front camera
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            assertTrue(mActivity.getLocationSupplier().hasLocationListeners());
            // shouldn't need to wait for test_has_received_location to be true, as should remember from before switching camera
            assertTrue(mActivity.getLocationSupplier().getLocation() != null);
        }
    }

    /* Tests we save location data; also tests that we save other exif data.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testLocationOn() throws IOException {
        Log.d(TAG, "testLocationOn");
        setToDefault();

        subTestLocationOn(false);
    }

    /* Tests we save location and gps direction.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testLocationDirectionOn() throws IOException {
        Log.d(TAG, "testLocationDirectionOn");
        setToDefault();

        subTestLocationOn(true);
    }

    /* As testLocationOn, but with SAF enabled.
     * Important for Camera2 API at least to test the codepath for when
     * ImageSaver.needGPSTimestampHack() returns true, when using SAF.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testLocationOnSAF() throws IOException {
        Log.d(TAG, "testLocationOnSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestLocationOn(false);
    }

    /* Tests we don't save location data; also tests that we save other exif data.
     */
    private void subTestLocationOff(boolean gps_direction) throws IOException {
        setToDefault();

        if( gps_direction ) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.GPSDirectionPreferenceKey, true);
            editor.apply();
            updateForSettings();
        }
        this.getInstrumentation().waitForIdleSync();
        assertTrue(!mActivity.getLocationSupplier().hasLocationListeners());
        assertTrue(mActivity.getLocationSupplier().getLocation() == null);
        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        mActivity.test_last_saved_image = null;
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mPreview.count_cameraTakePicture==1);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, false);

        // now test with auto-stabilise
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
            editor.apply();
            updateForSettings();
        }
        mActivity.test_last_saved_image = null;
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mPreview.count_cameraTakePicture==2);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, false);

        // switch to front camera
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            int cameraId = mPreview.getCameraId();
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            this.getInstrumentation().waitForIdleSync();
            assertTrue(mActivity.getLocationSupplier().getLocation() == null);

            // return to back camera
            switchToCamera(cameraId);
            assertTrue(mActivity.getLocationSupplier().getLocation() == null);
        }

        // now switch location back on
        Log.d(TAG, "now switch location back on");
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.LocationPreferenceKey, true);
            editor.apply();
            updateForSettings();
        }

        long start_t = System.currentTimeMillis();
        while( !mActivity.getLocationSupplier().testHasReceivedLocation() ) {
            this.getInstrumentation().waitForIdleSync();
            if( System.currentTimeMillis() - start_t > 20000 ) {
                // need to allow long time for testing devices without mobile network; will likely fail altogether if don't even have wifi
                assertTrue(false);
            }
        }
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getLocationSupplier().getLocation() != null);

        // switch to front camera
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            // shouldn't need to wait for test_has_received_location to be true, as should remember from before switching camera
            assertTrue(mActivity.getLocationSupplier().getLocation() != null);
        }
    }

    /* Tests we don't save location data; also tests that we save other exif data.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testLocationOff() throws IOException {
        Log.d(TAG, "testLocationOff");
        subTestLocationOff(false);
    }

    /* Tests we save gps direction.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testDirectionOn() throws IOException {
        Log.d(TAG, "testDirectionOn");
        subTestLocationOff(true);
    }

    /* As testDirectionOn() but for SAF.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     * If this test fails, make sure we've manually selected that folder (as permission can't be given through the test
     * framework).
     */
    public void testDirectionOnSAF() throws IOException {
        Log.d(TAG, "testDirectionOnSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestLocationOff(true);
    }

    private void subTestPhotoStamp() throws IOException {
        {
            assertFalse(mActivity.getApplicationInterface().getDrawPreview().getStoredHasStampPref());
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.StampPreferenceKey, "preference_stamp_yes");
            editor.apply();
            updateForSettings();
            assertTrue(mActivity.getApplicationInterface().getDrawPreview().getStoredHasStampPref());
        }

        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==1);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, false);

        // now again with location
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.LocationPreferenceKey, true);
            editor.apply();
            updateForSettings();
        }

        assertTrue( mActivity.getLocationSupplier().hasLocationListeners() );
        long start_t = System.currentTimeMillis();
        while( !mActivity.getLocationSupplier().testHasReceivedLocation() ) {
            this.getInstrumentation().waitForIdleSync();
            if( System.currentTimeMillis() - start_t > 20000 ) {
                // need to allow long time for testing devices without mobile network; will likely fail altogether if don't even have wifi
                assertTrue(false);
            }
        }
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getLocationSupplier().getLocation() != null);

        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==2);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, true);

        // now again with custom text
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.TextStampPreferenceKey, "Test stamp!£$");
            editor.apply();
            updateForSettings();
        }

        assertTrue( mActivity.getLocationSupplier().hasLocationListeners() );
        while( !mActivity.getLocationSupplier().testHasReceivedLocation() ) {
        }
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.getLocationSupplier().getLocation() != null);

        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==3);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, true);

        // now test with auto-stabilise
        {
            assertFalse(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
            editor.apply();
            updateForSettings();
            assertTrue(mActivity.getApplicationInterface().getDrawPreview().getStoredAutoStabilisePref());
        }

        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==4);
        mActivity.waitUntilImageQueueEmpty();
        assertTrue(mActivity.test_last_saved_image != null);
        testExif(mActivity.test_last_saved_image, true);

        mActivity.waitUntilImageQueueEmpty();
    }

    /* Tests we can stamp date/time and location to photo.
     * May fail on devices without mobile network, especially if we don't even have wifi.
     */
    public void testPhotoStamp() throws IOException {
        Log.d(TAG, "testPhotoStamp");

        setToDefault();

        subTestPhotoStamp();
    }

    /** As testPhotoStamp() but with SAF.
     *  If this test fails, make sure we've manually selected that folder (as permission can't be given through the test
     *  framework).
     */
    public void testPhotoStampSAF() throws IOException {
        Log.d(TAG, "testPhotoStampSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
        editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
        editor.apply();
        updateForSettings();

        subTestPhotoStamp();
    }

    /* Tests we can stamp custom text to photo.
     */
    public void testCustomTextStamp() {
        Log.d(TAG, "testCustomTextStamp");

        setToDefault();

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.TextStampPreferenceKey, "Test stamp!£$");
            editor.apply();
            updateForSettings();
        }

        assertTrue(mPreview.count_cameraTakePicture==0);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==1);

        // now test with auto-stabilise
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
            editor.apply();
            updateForSettings();
        }

        clickView(takePhotoButton);

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "photo count: " + mPreview.count_cameraTakePicture);
        assertTrue(mPreview.count_cameraTakePicture==2);

        mActivity.waitUntilImageQueueEmpty();
    }

    /* Tests zoom.
     */
    public void testZoom() throws InterruptedException {
        Log.d(TAG, "testZoom");
        setToDefault();

        if( !mPreview.supportsZoom() ) {
            Log.d(TAG, "zoom not supported");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();

        final ZoomControls zoomControls = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom);
        assertTrue(zoomControls.getVisibility() == View.GONE);

        final SeekBar zoomSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom_seekbar);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        int max_zoom = mPreview.getMaxZoom();
        assertTrue(zoomSeekBar.getMax() == max_zoom);
        Log.d(TAG, "zoomSeekBar progress = " + zoomSeekBar.getProgress());
        Log.d(TAG, "actual zoom = " + mPreview.getCameraController().getZoom());
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        if( mPreview.supportsFocus() ) {
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

            // touch to auto-focus with focus area
            Thread.sleep(2000); // needed for Galaxy S10e for the touch to register
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            assertTrue(mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() != null);
            assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
            assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
            assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
        }

        int zoom = mPreview.getCameraController().getZoom();

        // now test multitouch zoom
        mPreview.scaleZoom(2.0f);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() > zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        mPreview.scaleZoom(0.5f);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        // test to max/min
        mPreview.scaleZoom(10000.0f);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to max_zoom " + max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        mPreview.scaleZoom(1.0f/10000.0f);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zero");
        assertTrue(mPreview.getCameraController().getZoom() == 0);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        // use seekbar to zoom
        Log.d(TAG, "zoom to max");
        Log.d(TAG, "progress was: " + zoomSeekBar.getProgress());
        zoomSeekBar.setProgress(0);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to max_zoom " + max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
        if( mPreview.supportsFocus() ) {
            // check that focus areas cleared
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);
        }

        Log.d(TAG, "zoom to min");
        Log.d(TAG, "progress was: " + zoomSeekBar.getProgress());
        zoomSeekBar.setProgress(zoomSeekBar.getMax());
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        // use volume keys to zoom in/out
        editor.putString(PreferenceKeys.VolumeKeysPreferenceKey, "volume_zoom");
        editor.apply();

        Log.d(TAG, "zoom in with volume keys");
        this.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom+1);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        Log.d(TAG, "zoom out with volume keys");
        this.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        // now test with -/+ controls

        editor.putBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, true);
        editor.apply();
        updateForSettings();
        assertTrue(zoomControls.getVisibility() == View.VISIBLE);

        Log.d(TAG, "zoom in");
        mActivity.zoomIn();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom+1);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
        if( mPreview.supportsFocus() ) {
            // check that focus areas cleared
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

            // touch to auto-focus with focus area
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            assertTrue(mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() != null);
            assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
            assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
            assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
        }

        Log.d(TAG, "zoom out");
        mActivity.zoomOut();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
        if( mPreview.supportsFocus() ) {
            // check that focus areas cleared
            assertTrue(!mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() == null);
            assertTrue(mPreview.getCameraController().getMeteringAreas() == null);

            // touch to auto-focus with focus area
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
            assertTrue(mPreview.hasFocusArea());
            assertTrue(mPreview.getCameraController().getFocusAreas() != null);
            assertTrue(mPreview.getCameraController().getFocusAreas().size() == 1);
            assertTrue(mPreview.getCameraController().getMeteringAreas() != null);
            assertTrue(mPreview.getCameraController().getMeteringAreas().size() == 1);
        }

        // now test with slider invisible

        editor.putBoolean(PreferenceKeys.ShowZoomSliderControlsPreferenceKey, false);
        editor.apply();
        updateForSettings();
        assertTrue(zoomSeekBar.getVisibility() == View.INVISIBLE);

        Log.d(TAG, "zoom in");
        mActivity.zoomIn();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom+1);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        Log.d(TAG, "zoom out");
        mActivity.zoomOut();
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + zoom);
        assertTrue(mPreview.getCameraController().getZoom() == zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
    }

    public void testZoomIdle() {
        Log.d(TAG, "testZoomIdle");
        setToDefault();

        if( !mPreview.supportsZoom() ) {
            Log.d(TAG, "zoom not supported");
            return;
        }

        final SeekBar zoomSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom_seekbar);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        int init_zoom = mPreview.getCameraController().getZoom();
        int max_zoom = mPreview.getMaxZoom();
        zoomSeekBar.setProgress(0);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        pauseAndResume();
        Log.d(TAG, "after pause and resume: compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + max_zoom);
        // as of Open Camera v1.43, zoom is reset when pause/resuming
        //assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == init_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
    }

    public void testZoomSwitchCamera() {
        Log.d(TAG, "testZoomSwitchCamera");
        setToDefault();

        if( !mPreview.supportsZoom() ) {
            Log.d(TAG, "zoom not supported");
            return;
        }
        else if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        final SeekBar zoomSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.zoom_seekbar);
        assertTrue(zoomSeekBar.getVisibility() == View.VISIBLE);
        int init_zoom = mPreview.getCameraController().getZoom();
        int max_zoom = mPreview.getMaxZoom();
        zoomSeekBar.setProgress(0);
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());

        int cameraId = mPreview.getCameraId();
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();
        int new_cameraId = mPreview.getCameraId();
        assertTrue(cameraId != new_cameraId);

        max_zoom = mPreview.getMaxZoom();
        Log.d(TAG, "after pause and resume: compare actual zoom " + mPreview.getCameraController().getZoom() + " to zoom " + max_zoom);
        // as of Open Camera v1.43, zoom is reset when pause/resuming
        //assertTrue(mPreview.getCameraController().getZoom() == max_zoom);
        assertTrue(mPreview.getCameraController().getZoom() == init_zoom);
        assertTrue(max_zoom-zoomSeekBar.getProgress() == mPreview.getCameraController().getZoom());
    }

    /** Switch to front camera, pause and resume, check still on the front camera.
     */
    public void testSwitchCameraIdle() {
        Log.d(TAG, "testSwitchCameraIdle");
        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        int cameraId = mPreview.getCameraId();
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();

        int new_cameraId = mPreview.getCameraId();
        assertTrue(cameraId != new_cameraId);

        pauseAndResume();

        int new2_cameraId = mPreview.getCameraId();
        assertTrue(new2_cameraId == new_cameraId);

    }

    /** Tests touching the screen before camera has opened.
     */
    public void testTouchFocusQuick() {
        Log.d(TAG, "testTouchFocusQuick");
        setToDefault();

        pauseAndResume(false); // don't wait for camera to be reopened, as we want to test touch focus whilst it's opening

        for(int i=0;i<10;i++) {
            TouchUtils.clickView(MainActivityTest.this, mPreview.getView());
        }
    }

    /** Tests trying to switch camera repeatedly, without waiting for camera to open.
     */
    public void testSwitchCameraRepeat() {
        Log.d(TAG, "testSwitchCameraRepeat");
        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        for(int i=0;i<100;i++) {
            clickView(switchCameraButton);
        }
        waitUntilCameraOpened();
        // n.b., don't check the new camera Id, as it's ill-defined which camera will be open
        // the main point of this test is to check we don't crash due to opening camera on background thread
    }

    /* Tests repeatedly switching camera, waiting for camera to reopen each time.
     * Guards against a bug fixed in 1.44 where we would crash due to memory leak in
     * OrientationEventListener.enable() (from Preview.cameraOpened()) when called too many times.
     * Note, takes a while (over 1m) to run, test may look like it's hung whilst running!
     */
    public void testSwitchCameraRepeat2() {
        Log.d(TAG, "testSwitchCameraRepeat2");
        setToDefault();

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        int cameraId = mPreview.getCameraId();

        for(int i=0;i<130;i++) {
            Log.d(TAG, "i = " + i);

            clickView(switchCameraButton);
            waitUntilCameraOpened();

            int new_cameraId = mPreview.getCameraId();
            assertTrue(new_cameraId != cameraId);
            cameraId = new_cameraId;
        }
    }

    /* Tests going to gallery.
     */
    public void testGallery() {
        Log.d(TAG, "testGallery");
        setToDefault();

        View galleryButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.gallery);
        clickView(galleryButton);

    }

    /* Tests going to settings.
     */
    public void testSettings() {
        Log.d(TAG, "testSettings");
        setToDefault();

        assertFalse(mActivity.isCameraInBackground());
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        clickView(settingsButton);
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.isCameraInBackground());
    }

    /* Tests going to settings and opening the privacy policy window.
     */
    public void testSettingsPrivacyPolicy() throws InterruptedException {
        Log.d(TAG, "testSettingsPrivacyPolicy");
        setToDefault();

        assertFalse(mActivity.isCameraInBackground());
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        clickView(settingsButton);
        this.getInstrumentation().waitForIdleSync();
        assertTrue(mActivity.isCameraInBackground());
        Thread.sleep(500);

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                MyPreferenceFragment fragment = mActivity.getPreferenceFragment();
                assertNotNull(fragment);
                fragment.clickedPrivacyPolicy();
            }
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(1000);
    }

    /* Tests save and load settings.
     */
    public void testSettingsSaveLoad() throws InterruptedException {
        Log.d(TAG, "testSettingsSaveLoad");
        setToDefault();

        final String test_string = "Test stamp!£$ <&"; // intentionally include characters that need escaping in xml

        // set a non-default setting
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.TextStampPreferenceKey, test_string);
            editor.apply();
            updateForSettings();
        }

        mActivity.getSettingsManager().saveSettings("test_testSettingsSaveLoad.xml");
        assertTrue(mActivity.test_save_settings_file != null);

        // now modify the aforementioned setting
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.TextStampPreferenceKey, "");
            editor.apply();
            updateForSettings();
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        String new_string = settings.getString(PreferenceKeys.TextStampPreferenceKey, "");
        assertEquals("", new_string);

        // now load settings
        assertTrue( mActivity.getSettingsManager().loadSettings(mActivity.test_save_settings_file) );

        // wait - n.b., loadSettings() won't restart due to being test code
        Thread.sleep(3000);
        /*mActivity = getActivity();
        Log.d(TAG, "mActivity is now: " + mActivity);
        mPreview = mActivity.getPreview();
        Log.d(TAG, "mPreview is now: " + mPreview);*/

        // now check setting is as expected
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        new_string = settings.getString(PreferenceKeys.TextStampPreferenceKey, "");
        Log.d(TAG, "new_string: " + new_string);
        assertEquals(test_string, new_string);

        // check again after a restart
        restart();
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        new_string = settings.getString(PreferenceKeys.TextStampPreferenceKey, "");
        Log.d(TAG, "new_string: " + new_string);
        assertEquals(test_string, new_string);
    }

    private void subTestCreateSaveFolder(boolean use_saf, String save_folder, boolean delete_folder) {
        setToDefault();

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            if( use_saf ) {
                editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
                editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), save_folder);
            }
            else {
                editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), save_folder);
            }
            editor.apply();
            updateForSettings();
            if( use_saf ) {
                // need to call this directly, as we don't call mActivity.onActivityResult
                mActivity.updateFolderHistorySAF(save_folder);
            }
        }

        SaveLocationHistory save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        assertTrue(save_location_history.size() > 0);
        assertTrue(save_location_history.contains(save_folder));
        assertTrue(save_location_history.get( save_location_history.size()-1 ).equals(save_folder));

        File folder = mActivity.getImageFolder();
        if( folder.exists() && delete_folder ) {
            assertTrue(folder.isDirectory());
            // delete folder - need to delete contents first
            if( folder.isDirectory() ) {
                String [] children = folder.list();
                if( children != null ) {
                    for(String child : children) {
                        File file = new File(folder, child);
                        file.delete();
                        MediaScannerConnection.scanFile(mActivity, new String[] { file.getAbsolutePath() }, null, null);
                    }
                }
            }
            folder.delete();
        }
        int n_old_files = 0;
        if( folder.exists() ) {
            n_old_files = getNFiles(folder);
        }
        Log.d(TAG, "n_old_files: " + n_old_files);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        mActivity.waitUntilImageQueueEmpty();

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder);
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == n_old_files+1);

        // change back to default, so as to not be annoying
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            if( use_saf ) {
                editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FOpenCamera");
            }
            else {
                editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera");
            }
            editor.apply();
        }
    }

    /** Tests taking a photo with a new save folder.
     */
    public void testCreateSaveFolder1() {
        Log.d(TAG, "testCreateSaveFolder1");
        subTestCreateSaveFolder(false, "OpenCameraTest", true);
    }

    /** Tests taking a photo with a new save folder.
     */
    public void testCreateSaveFolder2() {
        Log.d(TAG, "testCreateSaveFolder2");
        subTestCreateSaveFolder(false, "OpenCameraTest/", true);
    }

    /** Tests taking a photo with a new save folder.
     */
    public void testCreateSaveFolder3() {
        Log.d(TAG, "testCreateSaveFolder3");
        subTestCreateSaveFolder(false, "OpenCameraTest_a/OpenCameraTest_b", true);
    }

    /** Tests taking a photo with a new save folder.
     */
    @SuppressLint("SdCardPath")
    public void testCreateSaveFolder4() {
        Log.d(TAG, "testCreateSaveFolder4");
        subTestCreateSaveFolder(false, "/sdcard/Pictures/OpenCameraTest", true);
    }

    /** Tests taking a photo with a new save folder.
     */
    public void testCreateSaveFolderUnicode() {
        Log.d(TAG, "testCreateSaveFolderUnicode");
        subTestCreateSaveFolder(false, "éúíóá!£$%^&()", true);
    }

    /** Tests taking a photo with a new save folder.
     */
    public void testCreateSaveFolderEmpty() {
        Log.d(TAG, "testCreateSaveFolderEmpty");
        subTestCreateSaveFolder(false, "", false);
    }

    /** Tests taking a photo with a new save folder.
     *  If this test fails, make sure we've manually selected that folder (as permission can't be given through the test framework).
     */
    public void testCreateSaveFolderSAF() {
        Log.d(TAG, "testCreateSaveFolderSAF");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        subTestCreateSaveFolder(true, "content://com.android.externalstorage.documents/tree/primary%3ADCIM", true);
    }

    /** Tests launching the folder chooser on a new folder.
     */
    public void testFolderChooserNew() throws InterruptedException {
        Log.d(TAG, "testFolderChooserNew");
        setToDefault();

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCameraTest");
            editor.apply();
            updateForSettings();
        }

        File folder = mActivity.getImageFolder();
        if( folder.exists() ) {
            assertTrue(folder.isDirectory());
            // delete folder - need to delete contents first
            if( folder.isDirectory() ) {
                String [] children = folder.list();
                if( children != null ) {
                    for(String child : children) {
                        File file = new File(folder, child);
                        file.delete();
                        MediaScannerConnection.scanFile(mActivity, new String[] { file.getAbsolutePath() }, null, null);
                    }
                }
            }
            folder.delete();
        }

        FolderChooserDialog fragment = new FolderChooserDialog();
		fragment.setStartFolder(mActivity.getImageFolder());
        fragment.show(mActivity.getFragmentManager(), "FOLDER_FRAGMENT");
        Thread.sleep(1000); // wait until folderchooser started up
        Log.d(TAG, "started folderchooser");
        assertTrue(fragment.getCurrentFolder() != null);
        assertTrue(fragment.getCurrentFolder().equals(folder));
        assertTrue(folder.exists());
    }

    /** Tests launching the folder chooser on a folder we don't have access to.
     * (Shouldn't be possible to get into this state, but just in case.)
     */
    public void testFolderChooserInvalid() throws InterruptedException {
        Log.d(TAG, "testFolderChooserInvalid");
        setToDefault();

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), "/OpenCameraTest");
            editor.apply();
            updateForSettings();
        }

        FolderChooserDialog fragment = new FolderChooserDialog();
		fragment.setStartFolder(mActivity.getImageFolder());
        fragment.show(mActivity.getFragmentManager(), "FOLDER_FRAGMENT");
        Thread.sleep(1000); // wait until folderchooser started up
        Log.d(TAG, "started folderchooser");
        assertTrue(fragment.getCurrentFolder() != null);
        Log.d(TAG, "current folder: " + fragment.getCurrentFolder());
        assertTrue(fragment.getCurrentFolder().exists());
    }

    private void subTestSaveFolderHistory(final boolean use_saf) {
        // clearFolderHistory has code that must be run on UI thread
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.d(TAG, "clearFolderHistory");
                if( use_saf )
                    mActivity.clearFolderHistorySAF();
                else
                    mActivity.clearFolderHistory();
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        SaveLocationHistory save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        Log.d(TAG, "save_location_history size: " + save_location_history.size());
        assertTrue(save_location_history.size() == 1);
        String current_folder;
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            current_folder = use_saf ? settings.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "") : settings.getString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera");
            Log.d(TAG, "current_folder: " + current_folder);
            Log.d(TAG, "save_location_history entry: " + save_location_history.get(0));
            assertTrue(save_location_history.get(0).equals(current_folder));
        }

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(use_saf ? PreferenceKeys.getSaveLocationSAFPreferenceKey() : PreferenceKeys.getSaveLocationPreferenceKey(), "new_folder_history_entry");
            editor.apply();
            updateForSettings();
            if( use_saf ) {
                // need to call this directly, as we don't call mActivity.onActivityResult
                mActivity.updateFolderHistorySAF("new_folder_history_entry");
            }
        }
        save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        Log.d(TAG, "save_location_history size: " + save_location_history.size());
        for(int i=0;i<save_location_history.size();i++) {
            Log.d(TAG, save_location_history.get(i));
        }
        assertTrue(save_location_history.size() == 2);
        assertTrue(save_location_history.get(0).equals(current_folder));
        assertTrue(save_location_history.get(1).equals("new_folder_history_entry"));

        restart();

        save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        Log.d(TAG, "save_location_history size: " + save_location_history.size());
        for(int i=0;i<save_location_history.size();i++) {
            Log.d(TAG, save_location_history.get(i));
        }
        assertTrue(save_location_history.size() == 2);
        Log.d(TAG, "current_folder: " + current_folder);
        assertTrue(save_location_history.get(0).equals(current_folder));
        assertTrue(save_location_history.get(1).equals("new_folder_history_entry"));

        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(use_saf ? PreferenceKeys.getSaveLocationSAFPreferenceKey() : PreferenceKeys.getSaveLocationPreferenceKey(), current_folder);
            editor.apply();
            // now (for non-SAF) call testUsedFolderPicker() instead of updateForSettings(), to simulate using the recent folder picker
            // clearFolderHistory has code that must be run on UI thread
            final String current_folder_f = current_folder;
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if( use_saf ) {
                        // need to call this directly, as we don't call mActivity.onActivityResult
                        mActivity.updateFolderHistorySAF(current_folder_f);
                    }
                    else {
                        mActivity.usedFolderPicker();
                    }
                }
            });
            // need to wait for UI code to finish before leaving
            this.getInstrumentation().waitForIdleSync();
        }
        save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        assertTrue(save_location_history.size() == 2);
        assertTrue(save_location_history.get(0).equals("new_folder_history_entry"));
        assertTrue(save_location_history.get(1).equals(current_folder));

        // clearFolderHistory has code that must be run on UI thread
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if( use_saf )
                    mActivity.clearFolderHistorySAF();
                else
                    mActivity.clearFolderHistory();
            }
        });
        // need to wait for UI code to finish before leaving
        this.getInstrumentation().waitForIdleSync();
        save_location_history = use_saf ? mActivity.getSaveLocationHistorySAF() : mActivity.getSaveLocationHistory();
        assertTrue(save_location_history.size() == 1);
        assertTrue(save_location_history.get(0).equals(current_folder));
    }

    public void testSaveFolderHistory() {
        Log.d(TAG, "testSaveFolderHistory");
        setToDefault();

        subTestSaveFolderHistory(false);
    }

    public void testSaveFolderHistorySAF() {
        Log.d(TAG, "testSaveFolderHistorySAF");
        setToDefault();

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "SAF requires Android Lollipop or better");
            return;
        }

        {
            String save_folder = "content://com.android.externalstorage.documents/tree/primary%3ADCIM/OpenCamera";
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(PreferenceKeys.getUsingSAFPreferenceKey(), true);
            editor.putString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), save_folder);
            editor.apply();
            updateForSettings();
            // need to call this directly, as we don't call mActivity.onActivityResult
            mActivity.updateFolderHistorySAF(save_folder);
        }

        subTestSaveFolderHistory(true);
    }

    public void testPreviewRotation() {
        Log.d(TAG, "testPreviewRotation");

        setToDefault();

        int display_orientation = mPreview.getDisplayRotation();
        Log.d(TAG, "display_orientation = " + display_orientation);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.getRotatePreviewPreferenceKey(), "180");
        editor.apply();
        updateForSettings();

        int new_display_orientation = mPreview.getDisplayRotation();
        Log.d(TAG, "new_display_orientation = " + new_display_orientation);
        assertTrue( new_display_orientation == ((display_orientation + 2) % 4) );
    }

    private void subTestSceneMode() {
        Log.d(TAG, "subTestSceneMode");

        setToDefault();

        List<String> scene_modes = mPreview.getSupportedSceneModes();
        if( scene_modes == null ) {
            return;
        }
        Log.d(TAG, "scene mode: " + mPreview.getCameraController().getSceneMode());
        assertTrue( mPreview.getCameraController().getSceneMode() == null || mPreview.getCameraController().getSceneMode().equals(CameraController.SCENE_MODE_DEFAULT) );

        String scene_mode = null;
        // find a scene mode that isn't default
        for(String this_scene_mode : scene_modes) {
            if( !this_scene_mode.equals(CameraController.SCENE_MODE_DEFAULT) ) {
                scene_mode = this_scene_mode;
                break;
            }
        }
        if( scene_mode == null ) {
            return;
        }
        Log.d(TAG, "change to scene_mode: " + scene_mode);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.SceneModePreferenceKey, scene_mode);
        editor.apply();
        updateForSettings();

        String new_scene_mode = mPreview.getCameraController().getSceneMode();
        Log.d(TAG, "scene_mode is now: " + new_scene_mode);
        assertTrue( new_scene_mode.equals(scene_mode) );

        // Now set back to default - important as on some devices, non-default scene modes may override e.g. what
        // white balance mode can be set.
        // This was needed to fix the test testCameraModes() on Galaxy Nexus, which started failing in
        // April 2018 for v1.43. Earlier versions (e.g., 1.42) still had the problem despite previously
        // testing fine, so something must have changed on the device?

        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.putString(PreferenceKeys.SceneModePreferenceKey, CameraController.SCENE_MODE_DEFAULT);
        editor.apply();
        updateForSettings();

        new_scene_mode = mPreview.getCameraController().getSceneMode();
        Log.d(TAG, "scene_mode is now: " + new_scene_mode);
        assertTrue( new_scene_mode.equals(CameraController.SCENE_MODE_DEFAULT) );
    }

    private void subTestColorEffect() {
        Log.d(TAG, "subTestColorEffect");

        setToDefault();

        List<String> color_effects = mPreview.getSupportedColorEffects();
        if( color_effects == null ) {
            return;
        }
        Log.d(TAG, "color effect: " + mPreview.getCameraController().getColorEffect());
        assertTrue( mPreview.getCameraController().getColorEffect() == null || mPreview.getCameraController().getColorEffect().equals(CameraController.COLOR_EFFECT_DEFAULT) );

        String color_effect = null;
        // find a color effect that isn't default
        for(String this_color_effect : color_effects) {
            if( !this_color_effect.equals(CameraController.COLOR_EFFECT_DEFAULT) ) {
                color_effect = this_color_effect;
                break;
            }
        }
        if( color_effect == null ) {
            return;
        }
        Log.d(TAG, "change to color_effect: " + color_effect);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.ColorEffectPreferenceKey, color_effect);
        editor.apply();
        updateForSettings();

        String new_color_effect = mPreview.getCameraController().getColorEffect();
        Log.d(TAG, "color_effect is now: " + new_color_effect);
        assertTrue( new_color_effect.equals(color_effect) );
    }

    private void subTestWhiteBalance() {
        Log.d(TAG, "subTestWhiteBalance");

        setToDefault();

        List<String> white_balances = mPreview.getSupportedWhiteBalances();
        if( white_balances == null ) {
            return;
        }
        Log.d(TAG, "white balance: " + mPreview.getCameraController().getWhiteBalance());
        assertTrue( mPreview.getCameraController().getWhiteBalance() == null || mPreview.getCameraController().getWhiteBalance().equals(CameraController.WHITE_BALANCE_DEFAULT) );

        String white_balance = null;
        // find a white balance that isn't default
        for(String this_white_balances : white_balances) {
            if( !this_white_balances.equals(CameraController.WHITE_BALANCE_DEFAULT) ) {
                white_balance = this_white_balances;
                break;
            }
        }
        if( white_balance == null ) {
            return;
        }
        Log.d(TAG, "change to white_balance: " + white_balance);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.WhiteBalancePreferenceKey, white_balance);
        editor.apply();
        updateForSettings();

        String new_white_balance = mPreview.getCameraController().getWhiteBalance();
        Log.d(TAG, "white_balance is now: " + new_white_balance);
        assertTrue( new_white_balance.equals(white_balance) );
    }

    private void subTestImageQuality() {
        Log.d(TAG, "subTestImageQuality");

        setToDefault();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.QualityPreferenceKey, "100");
        editor.apply();
        updateForSettings();

        int quality = mPreview.getCameraController().getJpegQuality();
        Log.d(TAG, "quality is: " + quality);
        assertTrue( quality == 100 );
    }

    public void testCameraModes() {
        Log.d(TAG, "testCameraModes");
        subTestSceneMode();
        subTestColorEffect();
        subTestWhiteBalance();
        subTestImageQuality();
    }

    /** Tests that changing resolutions doesn't close the popup.
     */
    public void testSwitchResolution() throws InterruptedException {
        Log.d(TAG, "testSwitchResolution");

        setToDefault();

        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        CameraController.Size old_picture_size = mPreview.getCameraController().getPictureSize();

        // open popup
        assertFalse( mActivity.popupIsOpen() );
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        TextView photoResolutionButton = (TextView)mActivity.getUIButton("PHOTO_RESOLUTIONS");
        assertTrue(photoResolutionButton != null);
        //String exp_size_string = old_picture_size.width + " x " + old_picture_size.height + " " + Preview.getMPString(old_picture_size.width, old_picture_size.height);
        String exp_size_string = old_picture_size.width + " x " + old_picture_size.height;
        Log.d(TAG, "size string: " + photoResolutionButton.getText());
        assertTrue( photoResolutionButton.getText().equals(exp_size_string) );

        // change photo resolution
        View photoResolutionChangeButton = mActivity.getUIButton("PHOTO_RESOLUTIONS_PREV");
        assertTrue(photoResolutionChangeButton != null);
        this.getInstrumentation().waitForIdleSync();
        clickView(photoResolutionChangeButton);

        // check
        Thread.sleep(2000);
        CameraController.Size new_picture_size = mPreview.getCameraController().getPictureSize();
        Log.d(TAG, "old picture size: " + old_picture_size.width + " x " + old_picture_size.height);
        Log.d(TAG, "old new_picture_size size: " + new_picture_size.width + " x " + new_picture_size.height);
        assertTrue( !new_picture_size.equals(old_picture_size) );
        assertTrue( mActivity.popupIsOpen() );

        //exp_size_string = new_picture_size.width + " x " + new_picture_size.height + " " + Preview.getMPString(new_picture_size.width, new_picture_size.height);
        exp_size_string = new_picture_size.width + " x " + new_picture_size.height;
        Log.d(TAG, "size string: " + photoResolutionButton.getText());
        assertTrue( photoResolutionButton.getText().equals(exp_size_string) );

        // switch to video mode
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue(mPreview.isVideo());

        // open popup
        assertFalse( mActivity.popupIsOpen() );
        clickView(popupButton);
        while( !mActivity.popupIsOpen() ) {
        }

        TextView videoResolutionButton = (TextView)mActivity.getUIButton("VIDEO_RESOLUTIONS");
        assertTrue(videoResolutionButton != null);
        CharSequence oldVideoResolutionString = videoResolutionButton.getText();

        // change video resolution
        View videoResolutionChangeButton = mActivity.getUIButton("VIDEO_RESOLUTIONS_PREV");
        assertTrue(videoResolutionChangeButton != null);
        clickView(videoResolutionChangeButton);

        // check
        Thread.sleep(500);
        assertTrue( mActivity.popupIsOpen() );
        assertTrue( !videoResolutionButton.getText().equals(oldVideoResolutionString) );

    }

    /* Test for failing to open camera.
     */
    public void testFailOpenCamera() throws InterruptedException {
        Log.d(TAG, "testFailOpenCamera");

        setToDefault();

        assertTrue(mPreview.getCameraControllerManager() != null);
        assertTrue(mPreview.getCameraController() != null);
        mPreview.test_fail_open_camera = true;

        // can't test on startup, as camera is created when we create activity, so instead test by switching camera
        if( mPreview.getCameraControllerManager().getNumberOfCameras() > 1 ) {
            Log.d(TAG, "switch camera");
            View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
            clickView(switchCameraButton);
            waitUntilCameraOpened();
            assertTrue(mPreview.getCameraControllerManager() != null);
            assertTrue(mPreview.getCameraController() == null);
            this.getInstrumentation().waitForIdleSync();
        
            assertFalse( mActivity.popupIsOpen() );
            View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
            Log.d(TAG, "about to click popup");
            clickView(popupButton);
            Log.d(TAG, "done clicking popup");
            Thread.sleep(500);
            // if camera isn't opened, popup shouldn't open
            assertFalse( mActivity.popupIsOpen() );

            View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
            Log.d(TAG, "about to click settings");
            clickView(settingsButton);
            Log.d(TAG, "done clicking settings");
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VolumeKeysPreferenceKey, "volume_exposure");
        editor.apply();
        this.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);
    }

    public void testTakePhotoDRO() throws InterruptedException {
        Log.d(TAG, "testTakePhotoDRO");

        setToDefault();

        if( !mActivity.supportsDRO() ) {
            return;
        }

        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 90 );

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_dro");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.DRO );
        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 100 );

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 100 );

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();

        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 90 );

        clickView(switchVideoButton);
        waitUntilCameraOpened();
        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 100 );

        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.Standard );
        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 90 );
    }

    public void testTakePhotoDROPhotoStamp() throws InterruptedException {
        Log.d(TAG, "testTakePhotoDROPhotoStamp");

        setToDefault();

        if( !mActivity.supportsDRO() ) {
            return;
        }

        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 90 );

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_dro");
        editor.putString(PreferenceKeys.StampPreferenceKey, "preference_stamp_yes");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.DRO );
        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 100 );

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 100 );

        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.Standard );
        assertTrue( mActivity.getApplicationInterface().getImageQualityPref() == 90 );
    }

    /** Tests restarting in HDR mode.
     */
    public void testHDRRestart() {
        Log.d(TAG, "testHDRRestart");
        setToDefault();
        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.Standard );

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.apply();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        restart();
        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
    }

    public void testTakePhotoHDR() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDR");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Tests taking photo in HDR photo mode with saving base expo images.
     */
    public void testTakePhotoHDRSaveExpo() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRSaveExpo");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Tests taking photo in HDR photo mode with saving base expo images, with RAW.
     */
    public void testTakePhotoHDRSaveExpoRaw() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRSaveExpoRaw");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }
        if( !mPreview.supportsRaw() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, true, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Tests taking photo in HDR photo mode with saving base expo images, with RAW only.
     */
    public void testTakePhotoHDRSaveExpoRawOnly() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRSaveExpoRawOnly");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }
        if( !mPreview.supportsRaw() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_only");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, true, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Take photo in HDR mode with front camera.
     *  Note that this fails on OnePlus 3T with old camera API, due to bug where photo resolution changes when
     *  exposure compensation set for front camera.
     */
    public void testTakePhotoHDRFrontCamera() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRFrontCamera");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }
        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );

        int cameraId = mPreview.getCameraId();

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();

        int new_cameraId = mPreview.getCameraId();

        Log.d(TAG, "cameraId: " + cameraId);
        Log.d(TAG, "new_cameraId: " + new_cameraId);

        assertTrue(cameraId != new_cameraId);

        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    public void testTakePhotoHDRAutoStabilise() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRAutoStabilise");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    public void testTakePhotoHDRPhotoStamp() throws InterruptedException {
        Log.d(TAG, "testTakePhotoHDRPhotoStamp");

        setToDefault();

        if( !mActivity.supportsHDR() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr");
        editor.putString(PreferenceKeys.StampPreferenceKey, "preference_stamp_yes");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.HDR );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Tests expo bracketing with default values.
     */
    public void testTakePhotoExpo() throws InterruptedException {
        Log.d(TAG, "testTakePhotoExpo");

        setToDefault();

        if( !mActivity.supportsExpoBracketing() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.ExpoBracketing );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /** Tests expo bracketing with 5 images, 1 stop.
     */
    public void testTakePhotoExpo5() throws InterruptedException {
        Log.d(TAG, "testTakePhotoExpo5");

        setToDefault();

        if( !mActivity.supportsExpoBracketing() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_expo_bracketing");
        editor.putString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "5");
        editor.putString(PreferenceKeys.ExpoBracketingStopsPreferenceKey, "1");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.ExpoBracketing );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    /* Sets focus bracketing seek bars to some test positions.
     */
    private void setUpFocusBracketing() throws InterruptedException {
        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.FocusBracketing );
        assertTrue(focusSeekBar.getVisibility() == View.VISIBLE);
        focusSeekBar.setProgress( (int)(0.9*(focusSeekBar.getMax()-1)) );
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "source focus_distance: " + mPreview.getCameraController().getFocusDistance());
        mPreview.stoppedSettingFocusDistance(false); // hack, since onStopTrackingTouch() isn't called programmatically!
        this.getInstrumentation().waitForIdleSync();
        Thread.sleep(500);

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        assertTrue(focusTargetSeekBar.getVisibility() == View.VISIBLE);
        focusTargetSeekBar.setProgress( (int)(0.25*(focusTargetSeekBar.getMax()-1)) );
        this.getInstrumentation().waitForIdleSync();
        // test that we temporarily set the focus to the target distance
        float target_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "target_actual_focus_distance: " + target_actual_focus_distance);
        assertTrue(Math.abs(initial_focus_distance - target_actual_focus_distance) > 1.0e-5f); // no assertNotEquals!
        mPreview.stoppedSettingFocusDistance(true); // hack, since onStopTrackingTouch() isn't called programmatically!
        this.getInstrumentation().waitForIdleSync();
        Thread.sleep(500); // wait for initial focus to be set

        // test that we've reset back to the source distance
        float new_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "new_focus_distance: " + new_focus_distance);
        assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

        float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
        assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
    }

    /** Tests taking a photo in focus bracketing mode.
     */
    public void testTakePhotoFocusBracketing() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFocusBracketing");

        setToDefault();

        if( !mActivity.supportsFocusBracketing() ) {
            return;
        }

        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(focusSeekBar.getVisibility() == View.GONE);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);
        assertTrue(focusTargetSeekBar.getVisibility() == View.GONE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
        editor.apply();
        updateForSettings();

        setUpFocusBracketing();

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        subTestTakePhoto(false, false, true, true, false, false, false, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);

        float new_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "new_focus_distance: " + new_focus_distance);
        assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

        float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
        assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
    }

    /** Tests taking a photo in focus bracketing mode, with auto-level and 50 images.
     */
    public void testTakePhotoFocusBracketingHeavy() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFocusBracketingHeavy");

        setToDefault();

        if( !mActivity.supportsFocusBracketing() ) {
            return;
        }

        ImageSaver.test_small_queue_size = true;
        mActivity.getApplicationInterface().getImageSaver().test_slow_saving = true;
        // need to restart for test_small_queue_size to take effect
        restart();

        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(focusSeekBar.getVisibility() == View.GONE);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);
        assertTrue(focusTargetSeekBar.getVisibility() == View.GONE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
        editor.putString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "20");
        editor.apply();
        updateForSettings();

        setUpFocusBracketing();

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        subTestTakePhoto(false, false, true, true, false, false, false, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);

        float new_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "new_focus_distance: " + new_focus_distance);
        assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

        float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
        assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
    }

    /** Tests taking a photo in focus bracketing mode, but with cancelling.
     */
    public void testTakePhotoFocusBracketingCancel() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFocusBracketingCancel");

        setToDefault();

        if( !mActivity.supportsFocusBracketing() ) {
            return;
        }

        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(focusSeekBar.getVisibility() == View.GONE);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);
        assertTrue(focusTargetSeekBar.getVisibility() == View.GONE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
        editor.putString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "200");
        editor.apply();
        updateForSettings();

        setUpFocusBracketing();

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        assertFalse( mPreview.isTakingPhoto() );
        assertTrue( mActivity.getApplicationInterface().canTakeNewPhoto() );

        for(int i=0;i<2;i++) {
            View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            Log.d(TAG, "about to click take photo");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take photo");
            assertTrue( mPreview.isTakingPhoto() );

            Thread.sleep(i==0 ? 500 : 3000); // wait before cancelling
            assertTrue( mPreview.isTakingPhoto() );

            Log.d(TAG, "about to click take photo to cancel");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take photo cancel");

            // need to wait until cancelled
            Thread.sleep(3000);
            assertFalse( mPreview.isTakingPhoto() );
            assertTrue( mActivity.getApplicationInterface().canTakeNewPhoto() );

            assertTrue(mPreview.isPreviewStarted()); // check preview restarted
            Log.d(TAG, "count_cameraTakePicture: " + mPreview.count_cameraTakePicture);
            assertTrue(mPreview.count_cameraTakePicture==i+1);
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == i+1);

            float new_focus_distance = mPreview.getCameraController().getFocusDistance();
            Log.d(TAG, "new_focus_distance: " + new_focus_distance);
            assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

            float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
            Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
            assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
        }
    }

    /** Tests taking a photo with RAW and focus bracketing mode.
     */
    public void testTakePhotoRawFocusBracketing() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawFocusBracketing");

        setToDefault();

        if( !mActivity.supportsFocusBracketing() ) {
            return;
        }
        if( !mPreview.supportsRaw() ) {
            return;
        }

        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(focusSeekBar.getVisibility() == View.GONE);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);
        assertTrue(focusTargetSeekBar.getVisibility() == View.GONE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_yes");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
        editor.apply();
        updateForSettings();

        setUpFocusBracketing();

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        subTestTakePhoto(false, false, true, true, false, false, true, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);

        float new_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "new_focus_distance: " + new_focus_distance);
        assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

        float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
        assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
    }

    /** Tests taking a photo with RAW only and focus bracketing mode.
     */
    public void testTakePhotoRawOnlyFocusBracketing() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRawOnlyFocusBracketing");

        setToDefault();

        if( !mActivity.supportsFocusBracketing() ) {
            return;
        }
        if( !mPreview.supportsRaw() ) {
            return;
        }

        SeekBar focusSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_seekbar);
        assertTrue(focusSeekBar.getVisibility() == View.GONE);
        SeekBar focusTargetSeekBar = mActivity.findViewById(net.sourceforge.opencamera.R.id.focus_bracketing_target_seekbar);
        assertTrue(focusTargetSeekBar.getVisibility() == View.GONE);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_only");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_focus_bracketing");
        editor.apply();
        updateForSettings();

        setUpFocusBracketing();

        float initial_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "initial_focus_distance: " + initial_focus_distance);
        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        CaptureRequest.Builder previewBuilder = camera_controller2.testGetPreviewBuilder();
        // need to use LENS_FOCUS_DISTANCE rather than mPreview.getCameraController().getFocusDistance(), as the latter
        // will always return the source focus distance, even if the preview was set to something else
        float actual_initial_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        assertEquals(initial_focus_distance, actual_initial_focus_distance, 1.0e-5f);

        subTestTakePhoto(false, false, true, true, false, false, true, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);

        float new_focus_distance = mPreview.getCameraController().getFocusDistance();
        Log.d(TAG, "new_focus_distance: " + new_focus_distance);
        assertEquals(initial_focus_distance, new_focus_distance, 1.0e-5f);

        float new_actual_focus_distance = previewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.d(TAG, "new_actual_focus_distance: " + new_actual_focus_distance);
        assertEquals(initial_focus_distance, new_actual_focus_distance, 1.0e-5f);
    }

    /** Tests NR photo mode.
     */
    public void testTakePhotoNR() throws InterruptedException {
        Log.d(TAG, "testTakePhotoNR");

        setToDefault();

        if( !mActivity.supportsNoiseReduction() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_noise_reduction");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.NoiseReduction );

        final int n_back_photos = 3;
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);

        // then try again without waiting
        for(int i=1;i<n_back_photos;i++) {
            subTestTakePhoto(false, false, true, false, false, false, false, false);
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == i+1);
        }

        // then try front camera

        if( mPreview.getCameraControllerManager().getNumberOfCameras() <= 1 ) {
            return;
        }

        int cameraId = mPreview.getCameraId();

        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        clickView(switchCameraButton);
        waitUntilCameraOpened();

        int new_cameraId = mPreview.getCameraId();

        Log.d(TAG, "cameraId: " + cameraId);
        Log.d(TAG, "new_cameraId: " + new_cameraId);

        assertTrue(cameraId != new_cameraId);

        subTestTakePhoto(false, false, true, true, false, false, false, false);
        Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
        assertTrue(mPreview.getCameraController().test_capture_results == 1);
    }

    /** Tests fast burst with 20 images.
     */
    public void testTakePhotoFastBurst() throws InterruptedException {
        Log.d(TAG, "testTakePhotoFastBurst");

        setToDefault();

        if( !mActivity.supportsFastBurst() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_fast_burst");
        editor.putString(PreferenceKeys.FastBurstNImagesPreferenceKey, "20");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.FastBurst );
        subTestTakePhoto(false, false, true, true, false, false, false, false);
        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }
    }

    private void subTestTakePhotoContinuousBurst(boolean is_slow) throws InterruptedException {
        Log.d(TAG, "subTestTakePhotoContinuousBurst");

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        Log.d(TAG, "folder: " + folder);
        File [] files = folder.listFiles();
        int n_files = files == null ? 0 : files.length;
        Log.d(TAG, "n_files at start: " + n_files);

        final View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take photo");
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                assertTrue(takePhotoButton.performLongClick());
            }
        });
        this.getInstrumentation().waitForIdleSync();

        Thread.sleep(3000);

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.takePhotoButtonLongClickCancelled();
            }
        });
        this.getInstrumentation().waitForIdleSync();

        waitForTakePhoto();

        if( mPreview.usingCamera2API() ) {
            Log.d(TAG, "test_capture_results: " + mPreview.getCameraController().test_capture_results);
            assertTrue(mPreview.getCameraController().test_capture_results == 1);
        }

        assertFalse(mActivity.getApplicationInterface().getImageSaver().test_queue_blocked);

        File [] files2 = folder.listFiles();
        int n_new_files = (files2 == null ? 0 : files2.length) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        if( is_slow ) {
            // with limited queue, won't be able to save as many files
            assertTrue(n_new_files >= 3 && n_new_files <= 31);
        }
        else {
            // at one photo per 100ms, should have approximately 30 - note that long press can take longer to kick in on some devices, e.g., OnePlus 3T
            assertTrue(n_new_files >= 12 && n_new_files <= 31);
        }

        mActivity.waitUntilImageQueueEmpty();
    }

    /** Tests continuous burst.
     */
    public void testTakePhotoContinuousBurst() throws InterruptedException {
        Log.d(TAG, "testTakePhotoContinuousBurst");

        setToDefault();

        if( !mActivity.supportsFastBurst() ) {
            return;
        }

        subTestTakePhotoContinuousBurst(false);

        Thread.sleep(1000);

        // now take a regular photo
        subTestTakePhoto(false, false, false, false, false, false, false, false);
    }

    /** Tests continuous burst, but with flags set for slow saving and shorter queue.
     */
    public void testTakePhotoContinuousBurstSlow() throws InterruptedException {
        Log.d(TAG, "testTakePhotoContinuousBurstSlow");

        setToDefault();

        if( !mActivity.supportsFastBurst() ) {
            return;
        }

        ImageSaver.test_small_queue_size = true;
        mActivity.getApplicationInterface().getImageSaver().test_slow_saving = true;
        // need to restart for test_small_queue_size to take effect
        restart();

        subTestTakePhotoContinuousBurst(true);
    }

    private void subTestTakePhotoPanorama(boolean to_max, boolean cancel, boolean cancel_by_settings) throws InterruptedException {
        Log.d(TAG, "subTestTakePhotoPanorama");
        setToDefault();

        if( !mActivity.supportsPanorama() ) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_panorama");
        editor.apply();
        updateForSettings();

        assertTrue( mActivity.getApplicationInterface().getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama );

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        Thread.sleep(1000);
        assertTrue(mPreview.count_cameraTakePicture==0);

        assertFalse( mActivity.getApplicationInterface().getGyroSensor().isRecording() );

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View switchCameraButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.share);
        View settingsButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.settings);
        View cancelPanoramaButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.cancel_panorama);

        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureLockButton.getVisibility() == View.VISIBLE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(settingsButton.getVisibility() == View.VISIBLE);
        assertTrue(cancelPanoramaButton.getVisibility() == View.GONE);

        Log.d(TAG, "about to click take photo");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take photo");

        Log.d(TAG, "wait until finished taking photo");
        waitForTakePhoto();
        Log.d(TAG, "done taking photo");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");
        assertTrue(mPreview.count_cameraTakePicture==1);

        for(int i=0;i<(to_max ? MyApplicationInterface.max_panorama_pics_c-1 : 4);i++) {
            Log.d(TAG, "i = " + i);
            assertTrue( mActivity.getApplicationInterface().getGyroSensor().isRecording() );

            assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
            assertTrue(switchCameraButton.getVisibility() == View.GONE);
            assertTrue(switchVideoButton.getVisibility() == View.GONE);
            assertTrue(exposureButton.getVisibility() == View.GONE);
            assertTrue(exposureLockButton.getVisibility() == View.GONE);
            assertTrue(audioControlButton.getVisibility() == View.GONE);
            assertTrue(popupButton.getVisibility() == View.GONE);
            assertTrue(trashButton.getVisibility() == View.GONE);
            assertTrue(shareButton.getVisibility() == View.GONE);
            assertTrue(settingsButton.getVisibility() == View.VISIBLE);
            assertTrue(cancelPanoramaButton.getVisibility() == View.VISIBLE);

            Thread.sleep(2000);

            assertTrue( mActivity.getApplicationInterface().getGyroSensor().isRecording() );
            mActivity.getApplicationInterface().getGyroSensor().testForceTargetAchieved(0);
            Log.d(TAG, "wait for taking photo");
            waitForTakePhoto();
            Log.d(TAG, "done taking photo");
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
            assertTrue(mPreview.count_cameraTakePicture==i+2);
        }

        Thread.sleep(2000);

        if( !to_max ) {
            assertTrue( mActivity.getApplicationInterface().getGyroSensor().isRecording() );

            if( cancel ) {
                if( cancel_by_settings ) {
                    Log.d(TAG, "about to click settings");
                    clickView(settingsButton);
                    Log.d(TAG, "done clicking settings");
                    this.getInstrumentation().waitForIdleSync();
                }
                else {
                    Log.d(TAG, "about to click cancel");
                    clickView(cancelPanoramaButton);
                    Log.d(TAG, "done clicking cancel");
                    this.getInstrumentation().waitForIdleSync();
                }
            }
            else {
                // finish panorama (if to_max, this should have happened automatically)
                Log.d(TAG, "about to click take photo");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking take photo");
                this.getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");
            }
        }

        assertFalse( mActivity.getApplicationInterface().getGyroSensor().isRecording() );

        assertTrue(takePhotoButton.getVisibility() == View.VISIBLE);
        assertTrue(switchCameraButton.getVisibility() == View.VISIBLE);
        assertTrue(switchVideoButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureButton.getVisibility() == View.VISIBLE);
        assertTrue(exposureLockButton.getVisibility() == View.VISIBLE);
        assertTrue(audioControlButton.getVisibility() == View.GONE);
        assertTrue(popupButton.getVisibility() == View.VISIBLE);
        assertTrue(trashButton.getVisibility() == View.GONE);
        assertTrue(shareButton.getVisibility() == View.GONE);
        assertTrue(settingsButton.getVisibility() == View.VISIBLE);
        assertTrue(cancelPanoramaButton.getVisibility() == View.GONE);

        if( !cancel && !to_max ) {
            // test trying to take another photo whilst saving
            Thread.sleep(500);
            assertTrue( mActivity.getApplicationInterface().getImageSaver().getNImagesToSave() > 0 );
            Log.d(TAG, "about to click take photo whilst saving images");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take photo");
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
            assertFalse( mActivity.getApplicationInterface().getGyroSensor().isRecording() );

            // and again (test for crash that occured in 1.47!)
            Thread.sleep(500);
            assertTrue( mActivity.getApplicationInterface().getImageSaver().getNImagesToSave() > 0 );
            Log.d(TAG, "about to click take photo whilst saving images");
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take photo");
            this.getInstrumentation().waitForIdleSync();
            Log.d(TAG, "after idle sync");
            assertFalse( mActivity.getApplicationInterface().getGyroSensor().isRecording() );
        }

        mActivity.waitUntilImageQueueEmpty();

        assertTrue( folder.exists() );
        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertTrue(n_new_files == (cancel ? 0 : 1));
    }

    /* Test for panorama photo mode.
     */
    public void testTakePhotoPanorama() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPanorama");

        subTestTakePhotoPanorama(false, false, false);
    }

    /* Test for panorama photo mode, taking max number of panorama shots.
     */
    public void testTakePhotoPanoramaMax() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPanoramaMax");

        subTestTakePhotoPanorama(true, false, false);
    }

    /* Test for panorama photo mode, but cancelling.
     */
    public void testTakePhotoPanoramaCancel() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPanoramaCancel");

        subTestTakePhotoPanorama(false, true, false);
    }

    /* Test for panorama photo mode, but cancelling by going to settings.
     */
    public void testTakePhotoPanoramaCancelBySettings() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPanoramaCancelBySettings");

        subTestTakePhotoPanorama(false, true, true);
    }

    /*private Bitmap getBitmapFromAssets(String filename) throws IOException {
        Log.d(TAG, "getBitmapFromAssets: " + filename);
        AssetManager assetManager = getInstrumentation().getContext().getResources().getAssets();
        InputStream is = assetManager.open(filename);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
        is.close();
        Log.d(TAG, "    done: " + bitmap);
        return bitmap;
    }*/

    private Bitmap getBitmapFromFile(String filename) throws FileNotFoundException {
        return getBitmapFromFile(filename, 1);
    }

    private Bitmap getBitmapFromFile(String filename, int inSampleSize) throws FileNotFoundException {
        Log.d(TAG, "getBitmapFromFile: " + filename);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        //options.inSampleSize = inSampleSize;
        if( inSampleSize > 1 ) {
            // use inDensity for better quality, as inSampleSize uses nearest neighbour
            // see same code in ImageSaver.setBitmapOptionsSampleSize()
            options.inDensity = inSampleSize;
            options.inTargetDensity = 1;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(filename, options);
        if( bitmap == null )
            throw new FileNotFoundException();
        Log.d(TAG, "    done: " + bitmap);

        // now need to take exif orientation into account, as some devices or camera apps store the orientation in the exif tag,
        // which getBitmap() doesn't account for
        try {
            ExifInterface exif = new ExifInterface(filename);
            if( exif != null ) {
                int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                boolean needs_tf = false;
                int exif_orientation = 0;
                // from http://jpegclub.org/exif_orientation.html
                // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
                if( exif_orientation_s == ExifInterface.ORIENTATION_UNDEFINED || exif_orientation_s == ExifInterface.ORIENTATION_NORMAL ) {
                    // leave unchanged
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_180 ) {
                    needs_tf = true;
                    exif_orientation = 180;
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_90 ) {
                    needs_tf = true;
                    exif_orientation = 90;
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_270 ) {
                    needs_tf = true;
                    exif_orientation = 270;
                }
                else {
                    // just leave unchanged for now
                    Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
                }
                Log.d(TAG, "    exif orientation: " + exif_orientation);

                if( needs_tf ) {
                    Log.d(TAG, "    need to rotate bitmap due to exif orientation tag");
                    Matrix m = new Matrix();
                    m.setRotate(exif_orientation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
                    Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
                    if( rotated_bitmap != bitmap ) {
                        bitmap.recycle();
                        bitmap = rotated_bitmap;
                    }
                }
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        /*{
            for(int y=0;y<bitmap.getHeight();y++) {
                for(int x=0;x<bitmap.getWidth();x++) {
                    int color = bitmap.getPixel(x, y);
                    Log.d(TAG, x + "," + y + ": " + Color.red(color) + "," + Color.green(color) + "," + Color.blue(color));
                }
            }
        }*/
        return bitmap;
    }

    /* Tests restarting a large number of times - can be useful for testing for memory/resource leaks.
     */
    public void testRestart() {
        Log.d(TAG, "testRestart");
        setToDefault();

        final int n_restarts = 150;
        for(int i=0;i<n_restarts;i++) {
            Log.d(TAG, "restart: " + i + " / " + n_restarts);
            restart();
        }
    }

    public void testGPSString() {
        Log.d(TAG, "testGPSString");
        setToDefault();

        Location location1 = new Location("");
        location1.setLatitude(0.0);
        location1.setLongitude(0.0);
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_none", "preference_units_distance_m", true, location1, true, Math.toRadians(180)), "");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", true, location1, true, Math.toRadians(180)), "0, 0, 180°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", true, location1, true, Math.toRadians(180)), "0°0'0\", 0°0'0\", 180°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", true, location1, false, Math.toRadians(180)), "0, 0");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", true, location1, false, Math.toRadians(180)), "0°0'0\", 0°0'0\"");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", false, null, true, Math.toRadians(180)), "180°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", false, null, true, Math.toRadians(180)), "180°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_ft", true, location1, true, Math.toRadians(180)), "0, 0, 180°");

        Location location2 = new Location("");
        location2.setLatitude(-29.3);
        location2.setLongitude(47.6173);
        location2.setAltitude(106.5);
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_none", "preference_units_distance_m", true, location2, true, Math.toRadians(74)), "");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", true, location2, true, Math.toRadians(74)), "-29.3, 47.6173, 106.5m, 74°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", true, location2, true, Math.toRadians(74)), "-29°18'0\", 47°37'2\", 106.5m, 74°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", true, location2, false, Math.toRadians(74)), "-29.3, 47.6173, 106.5m");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", true, location2, false, Math.toRadians(74)), "-29°18'0\", 47°37'2\", 106.5m");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_m", false, null, true, Math.toRadians(74)), "74°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_dms", "preference_units_distance_m", false, null, true, Math.toRadians(74)), "74°");
        assertEquals(mActivity.getTextFormatter().getGPSString("preference_stamp_gpsformat_default", "preference_units_distance_ft", true, location2, true, Math.toRadians(74)), "-29.3, 47.6173, 349.4ft, 74°");
    }

    /* Tests launching with ACTION_VIDEO_CAPTURE intent, along with EXTRA_SIZE_LIMIT and
     * EXTRA_VIDEO_QUALITY.
     */
    public void testIntentVideo() throws ApplicationInterface.NoFreeStorageException {
        Log.d(TAG, "testIntentVideo");

        setToDefault();

        List<String> video_quality = mActivity.getPreview().getVideoQualityHander().getSupportedVideoQuality();

        assertFalse( mActivity.getApplicationInterface().isVideoPref() );
        assertEquals( 0, mActivity.getApplicationInterface().getVideoMaxDurationPref() );
        // n.b., will fail if not enough storage space on device!:
        MyApplicationInterface.VideoMaxFileSize videomaxfilesize = mActivity.getApplicationInterface().getVideoMaxFileSizePref();
        long max_filesize = videomaxfilesize.max_filesize;
        assertTrue( max_filesize > 100000000);
        assertTrue(videomaxfilesize.auto_restart);

        Intent intent = createDefaultIntent();
        intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 50123456L);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        setActivityIntent(intent);

        restart();

        assertTrue( mActivity.getApplicationInterface().isVideoPref() );
        assertEquals( 0, mActivity.getApplicationInterface().getVideoMaxDurationPref() );
        videomaxfilesize = mActivity.getApplicationInterface().getVideoMaxFileSizePref();
        assertEquals( 50123456, videomaxfilesize.max_filesize );
        assertFalse(videomaxfilesize.auto_restart);
        assertEquals(video_quality.get(video_quality.size()-1), mActivity.getApplicationInterface().getVideoQualityPref());

        intent = createDefaultIntent();
        intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        setActivityIntent(intent);

        restart();

        assertTrue( mActivity.getApplicationInterface().isVideoPref() );
        assertEquals( 0, mActivity.getApplicationInterface().getVideoMaxDurationPref() );
        videomaxfilesize = mActivity.getApplicationInterface().getVideoMaxFileSizePref();
        assertTrue( videomaxfilesize.max_filesize > 100000000);
        assertTrue( Math.abs(max_filesize - videomaxfilesize.max_filesize) < 5000000 ); // remaining storage may vary whilst test is running!
        assertTrue(videomaxfilesize.auto_restart);

        assertEquals(video_quality.get(0), mActivity.getApplicationInterface().getVideoQualityPref());
    }

    /* Tests launching with ACTION_VIDEO_CAPTURE intent, along with EXTRA_DURATION_LIMIT. The test
     * then tests we actually record video with the duration limit set.
     */
    public void testIntentVideoDurationLimit() throws InterruptedException, ApplicationInterface.NoFreeStorageException {
        Log.d(TAG, "testIntentVideoDurationLimit");

        setToDefault();

        assertFalse( mActivity.getApplicationInterface().isVideoPref() );
        assertEquals( 0, mActivity.getApplicationInterface().getVideoMaxDurationPref() );
        // n.b., will fail if not enough storage space on device!:
        MyApplicationInterface.VideoMaxFileSize videomaxfilesize = mActivity.getApplicationInterface().getVideoMaxFileSizePref();
        long max_filesize = videomaxfilesize.max_filesize;
        assertTrue( max_filesize > 100000000);
        assertTrue(videomaxfilesize.auto_restart);

        Intent intent = createDefaultIntent();
        intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 5);
        setActivityIntent(intent);

        restart();

        assertTrue( mActivity.getApplicationInterface().isVideoPref() );
        assertEquals( 5000, mActivity.getApplicationInterface().getVideoMaxDurationPref() );
        assertEquals( max_filesize, mActivity.getApplicationInterface().getVideoMaxFileSizePref().max_filesize );

        // count initial files in folder
        File folder = mActivity.getImageFolder();
        int n_files = getNFiles(folder);
        Log.d(TAG, "n_files at start: " + n_files);

        View takePhotoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        Log.d(TAG, "about to click take video");
        clickView(takePhotoButton);
        Log.d(TAG, "done clicking take video");
        this.getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        assertTrue( mPreview.isVideoRecording() );

        Thread.sleep(4000);
        Log.d(TAG, "check still taking video");
        assertTrue( mPreview.isVideoRecording() );

        int n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertEquals(1, n_new_files);

        Thread.sleep(3000);

        Log.d(TAG, "check stopped taking video");
        assertTrue( !mPreview.isVideoRecording() );

        assertTrue( folder.exists() );
        n_new_files = getNFiles(folder) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        assertEquals(1, n_new_files);

    }

    /** Tests that we handle the upgrade from the preference boolean key "preference_use_camera2"
     *  to the string key PreferenceKeys.CameraAPIPreferenceKey that occured in v1.48.
     */
    public void testCamera2PrefUpgrade() {
        Log.d(TAG, "testCamera2PrefUpgrade");

        // n.b., don't bother calling setToDefault()
        waitUntilCameraOpened();

        if( !mActivity.supportsCamera2() ) {
            Log.d(TAG, "test requires camera2 support");
            return;
        }

        assertFalse(mPreview.usingCamera2API());

        // test legacy key present, but set to old api
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.putBoolean("preference_use_camera2", false);
        editor.apply();
        restart();
        assertFalse(mPreview.usingCamera2API());

        // now test legacy key present for camera2 api
        settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        editor = settings.edit();
        editor.clear();
        editor.putBoolean("preference_use_camera2", true);
        editor.apply();

        for(int i=0;i<2;i++) {
            restart();
            assertTrue(mPreview.usingCamera2API());

            // also check we switched over to the new key
            settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
            assertFalse(settings.contains("preference_use_camera2"));
            assertTrue(settings.contains(PreferenceKeys.CameraAPIPreferenceKey));
            assertEquals("preference_camera_api_camera2", settings.getString(PreferenceKeys.CameraAPIPreferenceKey, PreferenceKeys.CameraAPIPreferenceDefault));
        }
    }

    private static class HistogramDetails {
        final int min_value;
        final int median_value;
        final int max_value;

        HistogramDetails(int min_value, int median_value, int max_value) {
            this.min_value = min_value;
            this.median_value = median_value;
            this.max_value = max_value;
        }
    }

    /** Checks for the resultant histogram.
     *  We check that we have a single range of non-zero values.
     * @param bitmap The bitmap to compute and check a histogram for.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private HistogramDetails checkHistogram(Bitmap bitmap) {
        int [] histogram = mActivity.getApplicationInterface().getHDRProcessor().computeHistogram(bitmap, true);
        assertEquals(256, histogram.length);
        int total = 0;
        for(int i=0;i<histogram.length;i++) {
            Log.d(TAG, "histogram[" + i + "]: " + histogram[i]);
            total += histogram[i];
        }
        Log.d(TAG, "total: " + total);
        boolean started = false, ended = false;
        int min_value = -1, median_value = -1, max_value = -1;
        int count = 0;
        int middle = total/2;
        for(int i=0;i<histogram.length;i++) {
            int value = histogram[i];
            if( !started ) {
                started = value != 0;
            }
            else {
                ended = value == 0;
                if( ended ) {
                    assertTrue(value == 0);
                }
            }
            if( value != 0 ) {
                if( min_value == -1 )
                    min_value = i;
                max_value = i;
                count += value;
                if( count >= middle && median_value == -1 )
                    median_value = i;
            }
        }
        Log.d(TAG, "min_value: " + min_value);
        Log.d(TAG, "median_value: " + median_value);
        Log.d(TAG, "max_value: " + max_value);
        return new HistogramDetails(min_value, median_value, max_value);
    }

    private HistogramDetails subTestHDR(List<Bitmap> inputs, String output_name, boolean test_dro, int iso, long exposure_time) throws IOException, InterruptedException {
        return subTestHDR(inputs, output_name, test_dro, iso, exposure_time, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD);
    }

    /** The following testHDRX tests test the HDR algorithm on a given set of input images.
     *  By testing on a fixed sample, this makes it easier to finetune the HDR algorithm for quality and performance.
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device everytime we run the tests.
     * @param iso The ISO of the middle image (for testing Open Camera's "smart" contrast enhancement). If set to -1, then use "always" contrast enhancement.
     * @param exposure_time The exposure time of the middle image (for testing Open Camera's "smart" contrast enhancement)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private HistogramDetails subTestHDR(List<Bitmap> inputs, String output_name, boolean test_dro, int iso, long exposure_time, HDRProcessor.TonemappingAlgorithm tonemapping_algorithm) throws IOException, InterruptedException {
        Log.d(TAG, "subTestHDR");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "renderscript requires Android Lollipop or better");
            return null;
        }

        Thread.sleep(1000); // wait for camera to open

        Bitmap dro_bitmap_in = null;
        if( test_dro ) {
            // save copy of input bitmap to also test DRO (since the HDR routine will free the inputs)
            int mid = (inputs.size()-1)/2;
            dro_bitmap_in = inputs.get(mid);
            dro_bitmap_in = dro_bitmap_in.copy(dro_bitmap_in.getConfig(), true);
        }

        HistogramDetails hdrHistogramDetails = null;
        if( inputs.size() > 1 ) {
            String preference_hdr_contrast_enhancement = (iso==-1) ? "preference_hdr_contrast_enhancement_always" : "preference_hdr_contrast_enhancement_smart";
    		float hdr_alpha = ImageSaver.getHDRAlpha(preference_hdr_contrast_enhancement, exposure_time, inputs.size());
            long time_s = System.currentTimeMillis();
            try {
                mActivity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, hdr_alpha, 4, true, tonemapping_algorithm, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA);
            }
            catch(HDRProcessorException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            Log.d(TAG, "HDR time: " + (System.currentTimeMillis() - time_s));

            saveBitmap(inputs.get(0), output_name);
            hdrHistogramDetails = checkHistogram(inputs.get(0));
        }
        inputs.get(0).recycle();
        inputs.clear();

        if( test_dro ) {
            inputs.add(dro_bitmap_in);
            long time_s = System.currentTimeMillis();
            try {
                mActivity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, 0.5f, 4, true, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA);
            }
            catch(HDRProcessorException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            Log.d(TAG, "DRO time: " + (System.currentTimeMillis() - time_s));

            saveBitmap(inputs.get(0), "dro" + output_name);
            checkHistogram(inputs.get(0));
            inputs.get(0).recycle();
            inputs.clear();
        }
        Thread.sleep(500);

        return hdrHistogramDetails;
    }

    private void checkHDROffsets(int [] exp_offsets_x, int [] exp_offsets_y) {
        checkHDROffsets(exp_offsets_x, exp_offsets_y, 1);
    }

    /** Checks that the HDR offsets used for auto-alignment are as expected.
     */
    private void checkHDROffsets(int [] exp_offsets_x, int [] exp_offsets_y, int scale) {
        int [] offsets_x = mActivity.getApplicationInterface().getHDRProcessor().offsets_x;
        int [] offsets_y = mActivity.getApplicationInterface().getHDRProcessor().offsets_y;
        for(int i=0;i<offsets_x.length;i++) {
            Log.d(TAG, "offsets " + i + " ( " + offsets_x[i]*scale + " , " + offsets_y[i]*scale + " ), expected ( " + exp_offsets_x[i] + " , " + exp_offsets_y[i] + " )");
            // we allow some tolerance as different devices can produce different results (e.g., Nexus 6 vs OnePlus 3T; see testHDR5 on Nexus 6)
            assertTrue(Math.abs(offsets_x[i]*scale - exp_offsets_x[i]) <= 1);
            assertTrue(Math.abs(offsets_y[i]*scale - exp_offsets_y[i]) <= 1);
        }
    }

    private void checkHistogramDetails(HistogramDetails hdrHistogramDetails, int exp_min_value, int exp_median_value, int exp_max_value) {
        Log.d(TAG, "compare min value " + hdrHistogramDetails.min_value + " to expected " + exp_min_value);
        Log.d(TAG, "compare median value " + hdrHistogramDetails.median_value + " to expected " + exp_median_value);
        Log.d(TAG, "compare max value " + hdrHistogramDetails.max_value + " to expected " + exp_max_value);
        // we allow some tolerance as different devices can produce different results (e.g., Nexus 6 vs OnePlus 3T; see testHDR18 on Nexus 6 which needs a tolerance of 2)
        // interestingly it's testHDR18 that also needs a higher tolerance for Nokia 8 vs Galaxy S10e
        assertTrue(Math.abs(exp_min_value - hdrHistogramDetails.min_value) <= 3);
        assertTrue(Math.abs(exp_median_value - hdrHistogramDetails.median_value) <= 3);
        assertTrue(Math.abs(exp_max_value - hdrHistogramDetails.max_value) <= 3);
    }

    final private String hdr_images_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/testOpenCamera/testdata/hdrsamples/";
    final private String avg_images_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/testOpenCamera/testdata/avgsamples/";
    final private String logprofile_images_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/testOpenCamera/testdata/logprofilesamples/";
    final private String panorama_images_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/testOpenCamera/testdata/panoramasamples/";

    /** Tests HDR algorithm on test samples "saintpaul".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR1() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR1");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input3.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input4.jpg") );

        // actual ISO unknown, so guessing
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR1_output.jpg", false, 1600, 1000000000L);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        //checkHistogramDetails(hdrHistogramDetails, 1, 44, 253);
        //checkHistogramDetails(hdrHistogramDetails, 1, 42, 253);
        checkHistogramDetails(hdrHistogramDetails, 1, 24, 254);
    }

    /** Tests HDR algorithm on test samples "saintpaul", but with 5 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR1_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR1_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input3.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input4.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "saintpaul/input5.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR1_exp5_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0, 0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 3, 43, 251);
        checkHistogramDetails(hdrHistogramDetails, 6, 42, 251);
    }

    /** Tests HDR algorithm on test samples "stlouis".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR2");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "stlouis/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "stlouis/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "stlouis/input3.jpg") );

        // actual ISO unknown, so guessing
        subTestHDR(inputs, "testHDR2_output.jpg", false, 1600, (long)(1000000000L*2.5));

        int [] exp_offsets_x = {0, 0, 2};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR3".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR3() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR3");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR3/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR3/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR3/input2.jpg") );
        
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR3_output.jpg", false, 40, 1000000000L/680);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {1, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 3, 104, 255);
        //checkHistogramDetails(hdrHistogramDetails, 4, 113, 255);
        checkHistogramDetails(hdrHistogramDetails, 8, 113, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR4".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR4");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR4/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR4/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR4/input2.jpg") );
        
        subTestHDR(inputs, "testHDR4_output.jpg", true, 102, 1000000000L/60);

        int [] exp_offsets_x = {-2, 0, 2};
        int [] exp_offsets_y = {-1, 0, 1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR5".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR5");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR5/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR5/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR5/input2.jpg") );
        
        subTestHDR(inputs, "testHDR5_output.jpg", false, 40, 1000000000L/398);

        // Nexus 6:
        //int [] exp_offsets_x = {0, 0, 0};
        //int [] exp_offsets_y = {-1, 0, 0};
        // OnePlus 3T:
        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR6".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR6() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR6");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR6/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR6/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR6/input2.jpg") );
        
        subTestHDR(inputs, "testHDR6_output.jpg", false, 40, 1000000000L/2458);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {1, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR7".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR7");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR7/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR7/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR7/input2.jpg") );
        
        subTestHDR(inputs, "testHDR7_output.jpg", false, 40, 1000000000L/538);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR8".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR8() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR8");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR8/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR8/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR8/input2.jpg") );
        
        subTestHDR(inputs, "testHDR8_output.jpg", false, 40, 1000000000L/148);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR9".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR9() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR9");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR9/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR9/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR9/input2.jpg") );
        
        subTestHDR(inputs, "testHDR9_output.jpg", false, 40, 1000000000L/1313);

        int [] exp_offsets_x = {-1, 0, 1};
        int [] exp_offsets_y = {0, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR10".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR10() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR10");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR10/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR10/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR10/input2.jpg") );
        
        subTestHDR(inputs, "testHDR10_output.jpg", false, 107, 1000000000L/120);

        int [] exp_offsets_x = {2, 0, 0};
        int [] exp_offsets_y = {5, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR11".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR11() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR11");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR11/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR11/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR11/input2.jpg") );
        
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR11_output.jpg", true, 40, 1000000000L/2662);

        int [] exp_offsets_x = {-2, 0, 1};
        int [] exp_offsets_y = {1, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 0, 48, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 65, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 62, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR12".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR12() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR12");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR12/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR12/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR12/input2.jpg") );
        
        subTestHDR(inputs, "testHDR12_output.jpg", true, 1196, 1000000000L/12);

        int [] exp_offsets_x = {0, 0, 7};
        int [] exp_offsets_y = {0, 0, 8};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR13".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR13() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR13");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR13/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR13/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR13/input2.jpg") );
        
        subTestHDR(inputs, "testHDR13_output.jpg", false, 323, 1000000000L/24);

        int [] exp_offsets_x = {0, 0, 2};
        int [] exp_offsets_y = {0, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR14".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR14() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR14");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR14/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR14/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR14/input2.jpg") );
        
        subTestHDR(inputs, "testHDR14_output.jpg", false, 40, 1000000000L/1229);

        int [] exp_offsets_x = {0, 0, 1};
        int [] exp_offsets_y = {0, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR15".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR15() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR15");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR15/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR15/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR15/input2.jpg") );
        
        subTestHDR(inputs, "testHDR15_output.jpg", false, 40, 1000000000L/767);

        int [] exp_offsets_x = {1, 0, -1};
        int [] exp_offsets_y = {2, 0, -3};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR16".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR16() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR16");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR16/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR16/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR16/input2.jpg") );
        
        subTestHDR(inputs, "testHDR16_output.jpg", false, 52, 1000000000L/120);

        int [] exp_offsets_x = {-1, 0, 2};
        int [] exp_offsets_y = {1, 0, -6};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR17".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR17() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR17");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR17/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR17/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR17/input2.jpg") );
        
        subTestHDR(inputs, "testHDR17_output.jpg", true, 557, 1000000000L/12);

        // Nexus 6:
        //int [] exp_offsets_x = {0, 0, -3};
        //int [] exp_offsets_y = {0, 0, -4};
        // OnePlus 3T:
        int [] exp_offsets_x = {0, 0, -2};
        int [] exp_offsets_y = {0, 0, -3};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR18".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR18() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR18");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR18/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR18/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR18/input2.jpg") );
        
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR18_output.jpg", true, 100, 1000000000L/800);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 1, 113, 254);
        //checkHistogramDetails(hdrHistogramDetails, 1, 119, 255);
        //checkHistogramDetails(hdrHistogramDetails, 5, 120, 255);
        checkHistogramDetails(hdrHistogramDetails, 2, 120, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR19".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR19() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR19");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR19/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR19/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR19/input2.jpg") );
        
        subTestHDR(inputs, "testHDR19_output.jpg", true, 100, 1000000000L/160);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR20".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR20() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR20");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR20/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR20/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR20/input2.jpg") );
        
        subTestHDR(inputs, "testHDR20_output.jpg", true, 100, 1000000000L*2);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {-1, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR21".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR21() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR21");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR21/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR21/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR21/input2.jpg") );

        // ISO and exposure unknown, so guessing
        subTestHDR(inputs, "testHDR21_output.jpg", true, 800, 1000000000L/12);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR22".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR22() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR22");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR22/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR22/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR22/input2.jpg") );
        
        subTestHDR(inputs, "testHDR22_output.jpg", true, 391, 1000000000L/12);

        // Nexus 6:
        //int [] exp_offsets_x = {1, 0, -5};
        //int [] exp_offsets_y = {1, 0, -6};
        // OnePlus 3T:
        int [] exp_offsets_x = {0, 0, -5};
        int [] exp_offsets_y = {1, 0, -6};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 2 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp2");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp2_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0};
        int [] exp_offsets_y = {0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 13, 72, 250);
        checkHistogramDetails(hdrHistogramDetails, 24, 72, 250);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 2 images, and greater exposure gap.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp2b() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp2b");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0070.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0062.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp2b_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0};
        int [] exp_offsets_y = {0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR23".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR23() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0066.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );

        // ISO unknown, so guessing
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_output.jpg", false, 1600, 1000000000L);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
        checkHistogramDetails(hdrHistogramDetails, 32, 74, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 4 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp4");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0070.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0062.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp4_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 15, 69, 254);
        checkHistogramDetails(hdrHistogramDetails, 24, 70, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 5 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0070.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0066.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0062.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp5_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0, 0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
        checkHistogramDetails(hdrHistogramDetails, 28, 82, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 6 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp6() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp6");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0072.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0070.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0062.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0061.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp6_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0, 0, 0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0, 0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 15, 70, 254);
        checkHistogramDetails(hdrHistogramDetails, 25, 71, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 7 images.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR23_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp7");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0072.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0070.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0068.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0066.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0064.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0062.png") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR23/memorial0061.png") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR23_exp7_output.jpg", false, -1, -1);

        int [] exp_offsets_x = {0, 0, 0, 0, 0, 0, 0};
        int [] exp_offsets_y = {0, 0, 0, 0, 0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
        checkHistogramDetails(hdrHistogramDetails, 28, 82, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR24".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR24() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR24");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR24/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR24/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR24/input2.jpg") );
        
        subTestHDR(inputs, "testHDR24_output.jpg", true, 40, 1000000000L/422);

        int [] exp_offsets_x = {0, 0, 1};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR25".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR25() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR25");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR25/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR25/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR25/input2.jpg") );
        
        subTestHDR(inputs, "testHDR25_output.jpg", true, 40, 1000000000L/1917);

        int [] exp_offsets_x = {0, 0, 0};
        int [] exp_offsets_y = {1, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR26".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR26() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR26");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR26/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR26/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR26/input2.jpg") );
        
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR26_output.jpg", true, 40, 1000000000L/5325);

        int [] exp_offsets_x = {-1, 0, 1};
        int [] exp_offsets_y = {1, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 0, 104, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 119, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR27".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR27() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR27");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR27/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR27/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR27/input2.jpg") );
        
        subTestHDR(inputs, "testHDR27_output.jpg", true, 40, 1000000000L/949);

        int [] exp_offsets_x = {0, 0, 2};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR28".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR28() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR28");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR28/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR28/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR28/input2.jpg") );
        
        subTestHDR(inputs, "testHDR28_output.jpg", true, 294, 1000000000L/20);

        int [] exp_offsets_x = {0, 0, 2};
        int [] exp_offsets_y = {0, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR29".
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDR29() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR29");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR29/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR29/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR29/input2.jpg") );
        
        subTestHDR(inputs, "testHDR29_output.jpg", false, 40, 1000000000L/978);

        int [] exp_offsets_x = {-1, 0, 3};
        int [] exp_offsets_y = {0, 0, -1};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR30".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR30() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR30");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR30/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR30/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR30/input2.jpg") );

        subTestHDR(inputs, "testHDR30_output.jpg", false, 40, 1000000000L/978);

        // offsets for full image
        //int [] exp_offsets_x = {-6, 0, -1};
        //int [] exp_offsets_y = {23, 0, -13};
        // offsets using centre quarter image
        int [] exp_offsets_x = {-5, 0, 0};
        int [] exp_offsets_y = {22, 0, -13};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR31".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR31() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR31");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR31/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR31/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR31/input2.jpg") );

        subTestHDR(inputs, "testHDR31_output.jpg", false, 40, 1000000000L/422);

        // offsets for full image
        //int [] exp_offsets_x = {0, 0, 4};
        //int [] exp_offsets_y = {21, 0, -11};
        // offsets using centre quarter image
        int [] exp_offsets_x = {0, 0, 3};
        int [] exp_offsets_y = {21, 0, -11};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR32".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR32() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR32");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR32/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR32/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR32/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR32_output.jpg", true, 40, 1000000000L/1331);

        int [] exp_offsets_x = {1, 0, 0};
        int [] exp_offsets_y = {13, 0, -10};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 3, 101, 251);
        //checkHistogramDetails(hdrHistogramDetails, 3, 109, 251);
        checkHistogramDetails(hdrHistogramDetails, 6, 111, 252);
    }

    /** Tests HDR algorithm on test samples "testHDR33".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR33() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR33");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR33/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR33/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR33/input2.jpg") );

        subTestHDR(inputs, "testHDR33_output.jpg", true, 40, 1000000000L/354);

        int [] exp_offsets_x = {13, 0, -10};
        int [] exp_offsets_y = {24, 0, -12};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR34".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR34() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR34");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR34/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR34/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR34/input2.jpg") );

        subTestHDR(inputs, "testHDR34_output.jpg", true, 40, 1000000000L/4792);

        int [] exp_offsets_x = {5, 0, -8};
        int [] exp_offsets_y = {0, 0, -2};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR35".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR35() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR35");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR35/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR35/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR35/input2.jpg") );

        subTestHDR(inputs, "testHDR35_output.jpg", true, 40, 1000000000L/792);

        int [] exp_offsets_x = {-10, 0, 3};
        int [] exp_offsets_y = {7, 0, -3};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR36".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR36() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR36");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR36/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR36/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR36/input2.jpg") );

        subTestHDR(inputs, "testHDR36_output.jpg", false, 100, 1000000000L/1148);

        int [] exp_offsets_x = {2, 0, -2};
        int [] exp_offsets_y = {-4, 0, 2};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR37".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR37() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR37");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR37/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR37/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR37/input2.jpg") );

        subTestHDR(inputs, "testHDR37_output.jpg", false, 46, 1000000000L/120);

        int [] exp_offsets_x = {0, 0, 3};
        int [] exp_offsets_y = {2, 0, -19};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);
    }

    /** Tests HDR algorithm on test samples "testHDR38".
     *  Tests with Filmic tonemapping.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR38Filmic() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR38Filmic");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR38/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR38/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR38/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR38_filmic_output.jpg", false, 125, 1000000000L/2965, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_FILMIC);

        int [] exp_offsets_x = {-1, 0, 0};
        int [] exp_offsets_y = {0, 0, 0};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        //checkHistogramDetails(hdrHistogramDetails, 0, 92, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 93, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR39".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR39() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR39");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR39/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR39/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR39/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR39_output.jpg", false, 125, 1000000000L/2135);

        int [] exp_offsets_x = {-6, 0, -2};
        int [] exp_offsets_y = {6, 0, -8};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        checkHistogramDetails(hdrHistogramDetails, 0, 128, 222);
    }

    /** Tests HDR algorithm on test samples "testHDR40".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR40() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR40_output.jpg", false, 50, 1000000000L/262);

        int [] exp_offsets_x = {5, 0, -2};
        int [] exp_offsets_y = {13, 0, 24};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        checkHistogramDetails(hdrHistogramDetails, 1, 138, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR40" with Exponential tonemapping.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR40Exponential() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40Exponential");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR40_exponential_output.jpg", false, 50, 1000000000L/262, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_EXPONENTIAL);

        int [] exp_offsets_x = {5, 0, -2};
        int [] exp_offsets_y = {13, 0, 24};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        checkHistogramDetails(hdrHistogramDetails, 1, 138, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR40" with Filmic tonemapping.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR40Filmic() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40Filmic");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR40/input2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR40_filmic_output.jpg", false, 50, 1000000000L/262, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_FILMIC);

        int [] exp_offsets_x = {5, 0, -2};
        int [] exp_offsets_y = {13, 0, 24};
        checkHDROffsets(exp_offsets_x, exp_offsets_y);

        checkHistogramDetails(hdrHistogramDetails, 1, 130, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR41".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR41() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR41");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR41/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR41/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR41/input2.jpg") );

        subTestHDR(inputs, "testHDR41_output.jpg", false, 925, 1000000000L/25);
    }

    /** Tests HDR algorithm on test samples "testHDR42".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR42() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR42");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR42/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR42/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR42/input2.jpg") );

        subTestHDR(inputs, "testHDR42_output.jpg", false, 112, 1000000000L/679);
    }

    /** Tests HDR algorithm on test samples "testHDR43".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR43() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR43");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR43/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR43/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR43/input2.jpg") );

        subTestHDR(inputs, "testHDR43_output.jpg", false, 1196, 1000000000L/12);
    }

    /** Tests HDR algorithm on test samples "testHDR44".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR44() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR44");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR44/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR44/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR44/input2.jpg") );

        subTestHDR(inputs, "testHDR44_output.jpg", false, 100, 1000000000L/1016);
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR45() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6314.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6312.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6310.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6309.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6311.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6313.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6315.jpg") );

        // ISO 100, exposure time 2s, but pass in -1 since these are HDRNTests
        subTestHDR(inputs, "testHDR45_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR45_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6314.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6312.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6310.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6309.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6311.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6313.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6315.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR45_exp5_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR45_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45_exp7");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6314.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6312.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6310.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6309.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6311.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6313.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR45/IMG_6315.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR45_exp7_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR46".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR46() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR46");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 06.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 04.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 03.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 02.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 01.jpg") );

        // ISO 100, exposure time 1/60s, but pass in -1 since these are HDRNTests
        subTestHDR(inputs, "testHDR46_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR46".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR46_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR46_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 06.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 04.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 03.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 02.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR46/Izmir Harbor - ppw - 01.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR46_exp5_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR47_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp2");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();

        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );

        subTestHDR(inputs, "testHDR47_exp2_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR47() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();

        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

        // ISO 400, exposure time 1/60s, but pass in -1 since these are HDRNTests
        subTestHDR(inputs, "testHDR47_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR47_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR47_exp5_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 1, 73, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR47_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp7");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR47_exp7_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 1, 73, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR48".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR48() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR48");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();

        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input3.jpg") );
        //inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input4.jpg") );

        // ISO 100, exposure time 1/716s, but pass in -1 since these are HDRNTests
        subTestHDR(inputs, "testHDR48_output.jpg", false, -1, -1);
    }

    /** Tests HDR algorithm on test samples "testHDR48".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR48_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR48_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();

        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input3.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR48/input4.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR48_exp5_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 0, 59, 241);
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR49_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp2");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input3.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR49_exp2_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 0, 92, 250);
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR49() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input3.jpg") );

        // ISO 100, exposure time 1/417s, but pass in -1 since these are HDRNTests
        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR49_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR49_exp4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp4");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input3.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input4.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR49_exp4_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 0, 100, 245);
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR49_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp5");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input2.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input3.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR49/input4.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR49_exp5_output.jpg", false, -1, -1);

        checkHistogramDetails(hdrHistogramDetails, 0, 72, 244);
    }

    /** Tests HDR algorithm on test samples "testHDR50".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR50() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR50");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR50/IMG_20180626_221357_0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR50/IMG_20180626_221357_1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR50/IMG_20180626_221357_2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR50_output.jpg", false, 867, 1000000000L/14);

        checkHistogramDetails(hdrHistogramDetails, 0, 69, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR51".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR51() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR51");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR51/IMG_20180323_104702_0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR51/IMG_20180323_104702_1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR51/IMG_20180323_104702_2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR51_output.jpg", true, 1600, 1000000000L/11);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR52".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR52() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR52");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR52/IMG_20181023_143633_EXP0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR52/IMG_20181023_143633_EXP1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR52/IMG_20181023_143633_EXP2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR52_output.jpg", false, 100, 1000000000L/2105);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR53".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR53() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR53");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR53/IMG_20181106_135411_EXP0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR53/IMG_20181106_135411_EXP1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR53/IMG_20181106_135411_EXP2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR53_output.jpg", false, 103, 1000000000L/5381);

        checkHistogramDetails(hdrHistogramDetails, 0, 55, 254);
    }

    /** Tests HDR algorithm on test samples "testHDR54".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR54() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR54");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR54/IMG_20181107_115508_EXP0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR54/IMG_20181107_115508_EXP1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR54/IMG_20181107_115508_EXP2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR54_output.jpg", false, 752, 1000000000L/14);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR55".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR55() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR55");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR55/IMG_20181107_115608_EXP0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR55/IMG_20181107_115608_EXP1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR55/IMG_20181107_115608_EXP2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR55_output.jpg", false, 1505, 1000000000L/10);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR56".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR56() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR56");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR56/180502_141722_OC_0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR56/180502_141722_OC_1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR56/180502_141722_OC_2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR56_output.jpg", false, 50, 1000000000L/40);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDR57".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testHDR57() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR57");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR57/IMG_20181119_145313_EXP0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR57/IMG_20181119_145313_EXP1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDR57/IMG_20181119_145313_EXP2.jpg") );

        HistogramDetails hdrHistogramDetails = subTestHDR(inputs, "testHDR57_output.jpg", true, 100, 1000000000L/204);

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests HDR algorithm on test samples "testHDRtemp".
     *  Used for one-off testing, or to recreate HDR images from the base exposures to test an updated alorithm.
     *  The test images should be copied to the test device into DCIM/testOpenCamera/testdata/hdrsamples/testHDRtemp/ .
     * @throws IOException
     * @throws InterruptedException 
     */
    public void testHDRtemp() throws IOException, InterruptedException {
        Log.d(TAG, "testHDRtemp");

        setToDefault();
        
        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDRtemp/input0.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDRtemp/input1.jpg") );
        inputs.add( getBitmapFromFile(hdr_images_path + "testHDRtemp/input2.jpg") );
        
        subTestHDR(inputs, "testHDRtemp_output.jpg", true, 100, 1000000000L/100);
    }

    /** Tests DRO only on a dark image.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testDRODark0() throws IOException, InterruptedException {
        Log.d(TAG, "testDRODark0");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(avg_images_path + "testAvg3/input0.jpg") );

        subTestHDR(inputs, "testDRODark0_output.jpg", true, -1, -1);
    }

    /** Tests DRO only on a dark image.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testDRODark1() throws IOException, InterruptedException {
        Log.d(TAG, "testDRODark1");

        setToDefault();

        // list assets
        List<Bitmap> inputs = new ArrayList<>();
        inputs.add( getBitmapFromFile(avg_images_path + "testAvg8/input0.jpg") );

        subTestHDR(inputs, "testDRODark1_output.jpg", true, -1, -1);
    }

    /** Tests calling the DRO routine with 0.0 factor, and DROALGORITHM_NONE - and that the resultant image is identical.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testDROZero() throws IOException, InterruptedException {
        Log.d(TAG, "testDROZero");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "renderscript requires Android Lollipop or better");
            return;
        }

        setToDefault();

        Bitmap bitmap = getBitmapFromFile(hdr_images_path + "testHDR3/input1.jpg");
        Bitmap bitmap_saved = bitmap.copy(bitmap.getConfig(), false);

        Thread.sleep(1000); // wait for camera to open

        List<Bitmap> inputs = new ArrayList<>();
        inputs.add(bitmap);
        try {
            mActivity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, 0.0f, 4, true, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_NONE);
        }
        catch(HDRProcessorException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        saveBitmap(inputs.get(0), "droZerotestHDR3_output.jpg");
        checkHistogram(bitmap);

        // check bitmaps are the same
        Log.d(TAG, "compare bitmap " + bitmap);
        Log.d(TAG, "with bitmap_saved " + bitmap_saved);
        // sameAs doesn't seem to work
        //assertTrue( bitmap.sameAs(bitmap_saved) );
        assertTrue( bitmap.getWidth() == bitmap_saved.getWidth() );
        assertTrue( bitmap.getHeight() == bitmap_saved.getHeight() );
        int [] old_row = new int[bitmap.getWidth()];
        int [] new_row = new int[bitmap.getWidth()];
        for(int y=0;y<bitmap.getHeight();y++) {
            //Log.d(TAG, "check row " + y + " / " + bitmap.getHeight());
            bitmap_saved.getPixels(old_row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
            bitmap.getPixels(new_row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
            for(int x=0;x<bitmap.getWidth();x++) {
                //int old_pixel = bitmap_saved.getPixel(x, y);
                //int new_pixel = bitmap.getPixel(x, y);
                int old_pixel = old_row[x];
                int new_pixel = new_row[x];
                assertTrue( old_pixel == new_pixel );
            }
        }

        bitmap.recycle();
        bitmap_saved.recycle();
        Thread.sleep(500);

    }

    private interface TestAvgCallback {
        void doneProcessAvg(int index); // called after every call to HDRProcessor.processAvg()
    }

    /** The following testAvgX tests test the Avg noise reduction algorithm on a given set of input images.
     *  By testing on a fixed sample, this makes it easier to finetune the algorithm for quality and performance.
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device everytime we run the tests.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private HistogramDetails subTestAvg(List<String> inputs, String output_name, int iso, long exposure_time, float zoom_factor, TestAvgCallback cb) throws IOException, InterruptedException {
        Log.d(TAG, "subTestAvg");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Log.d(TAG, "renderscript requires Android Lollipop or better");
            return null;
        }

        Thread.sleep(1000); // wait for camera to open

        /*Bitmap nr_bitmap = getBitmapFromFile(inputs.get(0));
        long time_s = System.currentTimeMillis();
        try {
            for(int i=1;i<inputs.size();i++) {
                Log.d(TAG, "processAvg for image: " + i);
                Bitmap new_bitmap = getBitmapFromFile(inputs.get(i));
                float avg_factor = (float)i;
                mActivity.getApplicationInterface().getHDRProcessor().processAvg(nr_bitmap, new_bitmap, avg_factor, true);
                // processAvg recycles new_bitmap
                if( cb != null ) {
                    cb.doneProcessAvg(i);
                }
                //break; // test
            }
            //mActivity.getApplicationInterface().getHDRProcessor().processAvgMulti(inputs, hdr_strength, 4);
        }
        catch(HDRProcessorException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        Log.d(TAG, "Avg time: " + (System.currentTimeMillis() - time_s));

        {
            mActivity.getApplicationInterface().getHDRProcessor().avgBrighten(nr_bitmap);
            Log.d(TAG, "time after brighten: " + (System.currentTimeMillis() - time_s));
        }*/

        Bitmap nr_bitmap;
        try {
            // initialise allocation from first two bitmaps
            //int inSampleSize = mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize(inputs.size());
            int inSampleSize = mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize(iso);
            Bitmap bitmap0 = getBitmapFromFile(inputs.get(0), inSampleSize);
            Bitmap bitmap1 = getBitmapFromFile(inputs.get(1), inSampleSize);
            int width = bitmap0.getWidth();
            int height = bitmap0.getHeight();

            float avg_factor = 1.0f;
            List<Long> times = new ArrayList<>();
            long time_s = System.currentTimeMillis();
            HDRProcessor.AvgData avg_data = mActivity.getApplicationInterface().getHDRProcessor().processAvg(bitmap0, bitmap1, avg_factor, iso, zoom_factor);
            Allocation allocation = avg_data.allocation_out;
            times.add(System.currentTimeMillis() - time_s);
            // processAvg recycles both bitmaps
            if( cb != null ) {
                cb.doneProcessAvg(1);
            }

            for(int i=2;i<inputs.size();i++) {
                Log.d(TAG, "processAvg for image: " + i);

                Bitmap new_bitmap = getBitmapFromFile(inputs.get(i), inSampleSize);
                avg_factor = (float)i;
                time_s = System.currentTimeMillis();
                mActivity.getApplicationInterface().getHDRProcessor().updateAvg(avg_data, width, height, new_bitmap, avg_factor, iso, zoom_factor);
                times.add(System.currentTimeMillis() - time_s);
                // updateAvg recycles new_bitmap
                if( cb != null ) {
                    cb.doneProcessAvg(i);
                }
            }

            time_s = System.currentTimeMillis();
            nr_bitmap = mActivity.getApplicationInterface().getHDRProcessor().avgBrighten(allocation, width, height, iso, exposure_time);
            avg_data.destroy();
            avg_data = null;
            times.add(System.currentTimeMillis() - time_s);

            long total_time = 0;
            Log.d(TAG, "*** times are:");
            for(long time : times) {
                total_time += time;
                Log.d(TAG, "    " + time);
            }
            Log.d(TAG, "    total: " + total_time);
        }
        catch(HDRProcessorException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        saveBitmap(nr_bitmap, output_name);
        HistogramDetails hdrHistogramDetails = checkHistogram(nr_bitmap);
        nr_bitmap.recycle();
        System.gc();
        inputs.clear();

        Thread.sleep(500);

        return hdrHistogramDetails;
    }

    /** Tests Avg algorithm on test samples "testAvg1".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg1() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg1");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg1/input0.jpg");
        inputs.add(avg_images_path + "testAvg1/input1.jpg");
        inputs.add(avg_images_path + "testAvg1/input2.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg1_output.jpg", 1600, 1000000000L/17, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 3, 0};
                    //int [] exp_offsets_y = {0, 1, 0};
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, 1, 0};
                    //int [] exp_offsets_x = {0, 2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, 6, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, 1, 0};
                    //int [] exp_offsets_x = {0, 7, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, -4, 0};
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg2".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg2() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg2");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg2/input0.jpg");
        inputs.add(avg_images_path + "testAvg2/input1.jpg");
        inputs.add(avg_images_path + "testAvg2/input2.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg2_output.jpg", 1600, 1000000000L/17, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, -15, 0};
                    //int [] exp_offsets_y = {0, -10, 0};
                    //int [] exp_offsets_x = {0, -15, 0};
                    //int [] exp_offsets_y = {0, -11, 0};
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, -12, 0};
                    int [] exp_offsets_x = {0, -16, 0};
                    int [] exp_offsets_y = {0, -12, 0};
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, -15, 0};
                    //int [] exp_offsets_y = {0, -10, 0};
                    //int [] exp_offsets_x = {0, -13, 0};
                    //int [] exp_offsets_y = {0, -12, 0};
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, -14, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, -12, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg3".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg3() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg3");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg3/input0.jpg");
        inputs.add(avg_images_path + "testAvg3/input1.jpg");
        inputs.add(avg_images_path + "testAvg3/input2.jpg");
        inputs.add(avg_images_path + "testAvg3/input3.jpg");
        inputs.add(avg_images_path + "testAvg3/input4.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg3_output.jpg", 1600, 1000000000L/16, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                /*if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 2, 0};
                    //int [] exp_offsets_y = {0, -18, 0};
                    //int [] exp_offsets_x = {0, -1, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -9, 0};
                    //int [] exp_offsets_y = {0, -11, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, -10, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, -8, 0};
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, -18, 0};
                    //int [] exp_offsets_y = {0, 17, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -7, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, -8, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, -25, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -9, 0};
                    //int [] exp_offsets_y = {0, 14, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, 12, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, -29, 0};
                    //int [] exp_offsets_y = {0, -22, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -7, 0};
                    //int [] exp_offsets_y = {0, 11, 0};
                    //int [] exp_offsets_x = {0, -6, 0};
                    //int [] exp_offsets_y = {0, 14, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 12, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }*/
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 21, 177);
        //checkHistogramDetails(hdrHistogramDetails, 0, 21, 152);
        checkHistogramDetails(hdrHistogramDetails, 0, 21, 166);
    }

    /** Tests Avg algorithm on test samples "testAvg4".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg4() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg4");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg4/input0.jpg");
        inputs.add(avg_images_path + "testAvg4/input1.jpg");
        inputs.add(avg_images_path + "testAvg4/input2.jpg");
        inputs.add(avg_images_path + "testAvg4/input3.jpg");
        inputs.add(avg_images_path + "testAvg4/input4.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg4_output.jpg", 1600, 1000000000L/16, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 5, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    int [] exp_offsets_x = {0, 5, 0};
                    int [] exp_offsets_y = {0, 1, 0};
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, 3, 0};
                    //int [] exp_offsets_y = {0, 5, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, 7, 0};
                    //int [] exp_offsets_x = {0, 1, 0};
                    //int [] exp_offsets_y = {0, 6, 0};
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, 8, 0};
                    //int [] exp_offsets_x = {0, 3, 0};
                    //int [] exp_offsets_y = {0, 7, 0};
                    //int [] exp_offsets_x = {0, 3, 0};
                    //int [] exp_offsets_y = {0, 8, 0};
                    int [] exp_offsets_x = {0, 3, 0};
                    int [] exp_offsets_y = {0, 9, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg5".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg5() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg5");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg5/input0.jpg");
        inputs.add(avg_images_path + "testAvg5/input1.jpg");
        inputs.add(avg_images_path + "testAvg5/input2.jpg");
        inputs.add(avg_images_path + "testAvg5/input3.jpg");
        inputs.add(avg_images_path + "testAvg5/input4.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg5_output.jpg", 1600, 1000000000L/16, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                /*if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    //int [] exp_offsets_x = {0, 5, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, 6, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, 7, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, -4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, 9, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, 10, 0};
                    //int [] exp_offsets_y = {0, -4, 0};
                    int [] exp_offsets_x = {0, 11, 0};
                    int [] exp_offsets_y = {0, -3, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }*/
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg6".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg6() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg6");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg6/input0.jpg");
        inputs.add(avg_images_path + "testAvg6/input1.jpg");
        inputs.add(avg_images_path + "testAvg6/input2.jpg");
        inputs.add(avg_images_path + "testAvg6/input3.jpg");
        inputs.add(avg_images_path + "testAvg6/input4.jpg");
        inputs.add(avg_images_path + "testAvg6/input5.jpg");
        inputs.add(avg_images_path + "testAvg6/input6.jpg");
        inputs.add(avg_images_path + "testAvg6/input7.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg6_output.jpg", 1600, 1000000000L/17, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                /*if( true )
                    return;*/
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
                else if( index == 2 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 5 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 6 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 7 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 18, 51, 201);
        //checkHistogramDetails(hdrHistogramDetails, 14, 38, 200);
        //checkHistogramDetails(hdrHistogramDetails, 0, 9, 193);
        //checkHistogramDetails(hdrHistogramDetails, 0, 9, 199);
        //checkHistogramDetails(hdrHistogramDetails, 12, 46, 202);
        //checkHistogramDetails(hdrHistogramDetails, 12, 46, 205);
        //checkHistogramDetails(hdrHistogramDetails, 12, 44, 209);
        //checkHistogramDetails(hdrHistogramDetails, 12, 44, 202);
        checkHistogramDetails(hdrHistogramDetails, 5, 16, 190);
    }

    /** Tests Avg algorithm on test samples "testAvg7".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg7() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg7");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg7/input0.jpg");
        inputs.add(avg_images_path + "testAvg7/input1.jpg");
        inputs.add(avg_images_path + "testAvg7/input2.jpg");
        inputs.add(avg_images_path + "testAvg7/input3.jpg");
        inputs.add(avg_images_path + "testAvg7/input4.jpg");
        inputs.add(avg_images_path + "testAvg7/input5.jpg");
        inputs.add(avg_images_path + "testAvg7/input6.jpg");
        inputs.add(avg_images_path + "testAvg7/input7.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg7_output.jpg", 1600, 1000000000L/16, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -10, 0};
                    //int [] exp_offsets_y = {0, 6, 0};
                    //int [] exp_offsets_x = {0, -6, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -4, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -4, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg8".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg8() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg8");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg8/input0.jpg");
        inputs.add(avg_images_path + "testAvg8/input1.jpg");
        inputs.add(avg_images_path + "testAvg8/input2.jpg");
        inputs.add(avg_images_path + "testAvg8/input3.jpg");
        inputs.add(avg_images_path + "testAvg8/input4.jpg");
        inputs.add(avg_images_path + "testAvg8/input5.jpg");
        inputs.add(avg_images_path + "testAvg8/input6.jpg");
        inputs.add(avg_images_path + "testAvg8/input7.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg8_output.jpg", 1600, 1000000000L/16, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 4, 26, 92);
        //checkHistogramDetails(hdrHistogramDetails, 3, 19, 68);
        //checkHistogramDetails(hdrHistogramDetails, 0, 10, 60);
        //checkHistogramDetails(hdrHistogramDetails, 1, 8, 72);
        //checkHistogramDetails(hdrHistogramDetails, 1, 6, 64);
        //checkHistogramDetails(hdrHistogramDetails, 1, 15, 75);
        checkHistogramDetails(hdrHistogramDetails, 1, 16, 78);
    }

    /** Tests Avg algorithm on test samples "testAvg9".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg9() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg9");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        final boolean use_auto_photos = true;

        if( use_auto_photos ) {
            inputs.add(avg_images_path + "testAvg9/input_auto0.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto1.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto2.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto3.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto4.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto5.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto6.jpg");
            inputs.add(avg_images_path + "testAvg9/input_auto7.jpg");
        }
        else {
            inputs.add(avg_images_path + "testAvg9/input0.jpg");
            inputs.add(avg_images_path + "testAvg9/input1.jpg");
            inputs.add(avg_images_path + "testAvg9/input2.jpg");
            inputs.add(avg_images_path + "testAvg9/input3.jpg");
            inputs.add(avg_images_path + "testAvg9/input4.jpg");
            inputs.add(avg_images_path + "testAvg9/input5.jpg");
            inputs.add(avg_images_path + "testAvg9/input6.jpg");
            inputs.add(avg_images_path + "testAvg9/input7.jpg");
        }

        String out_filename = use_auto_photos ? "testAvg9_auto_output.jpg" : "testAvg9_output.jpg";

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, out_filename, 1600, use_auto_photos ? 1000000000L/16 : 1000000000L/11, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg10".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg10() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg10");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        final boolean use_auto_photos = false;

        if( use_auto_photos ) {
            inputs.add(avg_images_path + "testAvg10/input_auto0.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto1.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto2.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto3.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto4.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto5.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto6.jpg");
            inputs.add(avg_images_path + "testAvg10/input_auto7.jpg");
        }
        else {
            inputs.add(avg_images_path + "testAvg10/input0.jpg");
            inputs.add(avg_images_path + "testAvg10/input1.jpg");
            inputs.add(avg_images_path + "testAvg10/input2.jpg");
            inputs.add(avg_images_path + "testAvg10/input3.jpg");
            inputs.add(avg_images_path + "testAvg10/input4.jpg");
            inputs.add(avg_images_path + "testAvg10/input5.jpg");
            inputs.add(avg_images_path + "testAvg10/input6.jpg");
            inputs.add(avg_images_path + "testAvg10/input7.jpg");
        }

        String out_filename = use_auto_photos ? "testAvg10_auto_output.jpg" : "testAvg10_output.jpg";

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, out_filename, 1196, use_auto_photos ? 1000000000L/12 : 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg11".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg11() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg11");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // note, we don't actually use 8 images for a bright scene like this, but it serves as a good test for
        // misalignment/ghosting anyway
        inputs.add(avg_images_path + "testAvg11/input0.jpg");
        inputs.add(avg_images_path + "testAvg11/input1.jpg");
        inputs.add(avg_images_path + "testAvg11/input2.jpg");
        inputs.add(avg_images_path + "testAvg11/input3.jpg");
        inputs.add(avg_images_path + "testAvg11/input4.jpg");
        inputs.add(avg_images_path + "testAvg11/input5.jpg");
        inputs.add(avg_images_path + "testAvg11/input6.jpg");
        inputs.add(avg_images_path + "testAvg11/input7.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg11_output.jpg", 100, 1000000000L/338, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, -8, 0};
                    //int [] exp_offsets_x = {0, 6, 0};
                    //int [] exp_offsets_y = {0, -8, 0};
                    //int [] exp_offsets_x = {0, -6, 0};
                    //int [] exp_offsets_y = {0, 8, 0};
                    int [] exp_offsets_x = {0, -4, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    //assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, -5, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    //int [] exp_offsets_x = {0, -10, 0};
                    //int [] exp_offsets_y = {0, 6, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, -1, 0};
                    //int [] exp_offsets_y = {0, -18, 0};
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, -16, 0};
                    //int [] exp_offsets_x = {0, -4, 0};
                    //int [] exp_offsets_y = {0, -10, 0};
                    //int [] exp_offsets_x = {0, -4, 0};
                    //int [] exp_offsets_y = {0, -8, 0};
                    int [] exp_offsets_x = {0, -4, 0};
                    int [] exp_offsets_y = {0, -12, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, -3, 0};
                    //int [] exp_offsets_y = {0, -20, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, -18, 0};
                    //int [] exp_offsets_x = {0, -6, 0};
                    //int [] exp_offsets_y = {0, -12, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, -12, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 5 ) {
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -10, 0};
                    //int [] exp_offsets_y = {0, 4, 0};
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, 10, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 6 ) {
                    //int [] exp_offsets_x = {0, 0, 0};
                    //int [] exp_offsets_y = {0, -6, 0};
                    //int [] exp_offsets_x = {0, 2, 0};
                    //int [] exp_offsets_y = {0, -6, 0};
                    //int [] exp_offsets_x = {0, -4, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    int [] exp_offsets_x = {0, -4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 7 ) {
                    //int [] exp_offsets_x = {0, 7, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, 6, 0};
                    //int [] exp_offsets_y = {0, 6, 0};
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, 4, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, 8, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg12".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg12() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg12");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg12/input0.jpg");
        inputs.add(avg_images_path + "testAvg12/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg12_output.jpg", 100, 1000000000L/1617, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 30, 254);
        //checkHistogramDetails(hdrHistogramDetails, 0, 27, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 20, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 17, 254);
    }

    /** Tests Avg algorithm on test samples "testAvg13".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg13() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg13");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg13/input0.jpg");
        inputs.add(avg_images_path + "testAvg13/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg13_output.jpg", 100, 1000000000L/2482, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg14".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg14() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg14");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg14/input0.jpg");
        inputs.add(avg_images_path + "testAvg14/input1.jpg");
        inputs.add(avg_images_path + "testAvg14/input2.jpg");
        inputs.add(avg_images_path + "testAvg14/input3.jpg");
        inputs.add(avg_images_path + "testAvg14/input4.jpg");
        inputs.add(avg_images_path + "testAvg14/input5.jpg");
        inputs.add(avg_images_path + "testAvg14/input6.jpg");
        inputs.add(avg_images_path + "testAvg14/input7.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg14_output.jpg", 1600, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, -8, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
                else if( index == 7 ) {
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, 28, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 40, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg15".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg15() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg15");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg15/input0.jpg");
        inputs.add(avg_images_path + "testAvg15/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg15_output.jpg", 100, 1000000000L/1525, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg16".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg16() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg16");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg16/input0.jpg");
        inputs.add(avg_images_path + "testAvg16/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg16_output.jpg", 100, 1000000000L/293, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg17".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg17() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg17");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg17/input0.jpg");
        inputs.add(avg_images_path + "testAvg17/input1.jpg");
        inputs.add(avg_images_path + "testAvg17/input2.jpg");
        inputs.add(avg_images_path + "testAvg17/input3.jpg");
        inputs.add(avg_images_path + "testAvg17/input4.jpg");
        inputs.add(avg_images_path + "testAvg17/input5.jpg");
        inputs.add(avg_images_path + "testAvg17/input6.jpg");
        inputs.add(avg_images_path + "testAvg17/input7.jpg");

        // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
        // of 800; in reality for a scene this dark, it was probably more like ISO 1600
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg17_output.jpg", 1600, 1000000000L/17, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
                else if( index == 7 ) {
                    int [] exp_offsets_x = {0, 12, 0};
                    int [] exp_offsets_y = {0, 28, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 100, 233);
        //checkHistogramDetails(hdrHistogramDetails, 0, 100, 236);
        //checkHistogramDetails(hdrHistogramDetails, 0, 92, 234);
        //checkHistogramDetails(hdrHistogramDetails, 0, 102, 241);
        //checkHistogramDetails(hdrHistogramDetails, 0, 102, 238);
        checkHistogramDetails(hdrHistogramDetails, 0, 103, 244);
    }

    /** Tests Avg algorithm on test samples "testAvg18".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg18() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg18");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg18/input0.jpg");
        inputs.add(avg_images_path + "testAvg18/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg18_output.jpg", 100, 1000000000L/591, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    //assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg19".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg19() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg19");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // repeat same image twice
        inputs.add(avg_images_path + "testAvg19/input0.jpg");
        inputs.add(avg_images_path + "testAvg19/input0.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg19_output.jpg", 100, 1000000000L/2483, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 88, 252);
        //checkHistogramDetails(hdrHistogramDetails, 0, 77, 252);
        //checkHistogramDetails(hdrHistogramDetails, 0, 87, 252);
        //checkHistogramDetails(hdrHistogramDetails, 0, 74, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 58, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg20".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg20() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg20");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // repeat same image twice
        inputs.add(avg_images_path + "testAvg20/input0.jpg");
        inputs.add(avg_images_path + "testAvg20/input0.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg20_output.jpg", 100, 1000000000L/3124, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg21".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg21() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg21");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // repeat same image twice
        inputs.add(avg_images_path + "testAvg21/input0.jpg");
        inputs.add(avg_images_path + "testAvg21/input0.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg21_output.jpg", 102, 1000000000L/6918, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg22".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg22() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg22");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // repeat same image twice
        inputs.add(avg_images_path + "testAvg22/input0.jpg");
        inputs.add(avg_images_path + "testAvg22/input0.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg22_output.jpg", 100, 1000000000L/3459, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg23".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg23() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg23");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_0.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_1.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_2.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_3.jpg");
        // only test 4 images, to reflect latest behaviour that we take 4 images for this ISO
        /*inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_4.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_5.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_6.jpg");
        inputs.add(avg_images_path + "testAvg23/IMG_20180520_111250_7.jpg");*/

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg23_output.jpg", 1044, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, -4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    int [] exp_offsets_x = {0, -4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 5 ) {
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 6 ) {
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 7 ) {
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 81, 251);
        //checkHistogramDetails(hdrHistogramDetails, 0, 80, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 83, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg24".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg24() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg24");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg24/input0.jpg");
        inputs.add(avg_images_path + "testAvg24/input1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg24_output.jpg", 100, 1000000000L/2421, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 77, 250);
        //checkHistogramDetails(hdrHistogramDetails, 0, 74, 250);
        //checkHistogramDetails(hdrHistogramDetails, 0, 86, 250);
        //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 80, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 56, 254);
    }

    /** Tests Avg algorithm on test samples "testAvg25".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg25() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg25");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg25/input0.jpg");
        inputs.add(avg_images_path + "testAvg25/input1.jpg");
        inputs.add(avg_images_path + "testAvg25/input2.jpg");
        inputs.add(avg_images_path + "testAvg25/input3.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg25_output.jpg", 512, 1000000000L/20, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg26".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg26() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg26");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // note we now take only 3 images for bright scenes, but still test with 4 images as this serves as a good test
        // against ghosting
        inputs.add(avg_images_path + "testAvg26/input0.jpg");
        inputs.add(avg_images_path + "testAvg26/input1.jpg");
        inputs.add(avg_images_path + "testAvg26/input2.jpg");
        inputs.add(avg_images_path + "testAvg26/input3.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg26_output.jpg", 100, 1000000000L/365, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                /*if( true )
                    return;*/
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, -4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg27".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg27() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg27");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg27/IMG_20180610_205929_0.jpg");
        inputs.add(avg_images_path + "testAvg27/IMG_20180610_205929_1.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg27_output.jpg", 100, 1000000000L/482, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg28".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg28() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg28");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg28/input001.jpg");
        inputs.add(avg_images_path + "testAvg28/input002.jpg");
        inputs.add(avg_images_path + "testAvg28/input003.jpg");
        inputs.add(avg_images_path + "testAvg28/input004.jpg");
        inputs.add(avg_images_path + "testAvg28/input005.jpg");
        inputs.add(avg_images_path + "testAvg28/input006.jpg");
        inputs.add(avg_images_path + "testAvg28/input007.jpg");
        inputs.add(avg_images_path + "testAvg28/input008.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg28_output.jpg", 811, 1000000000L/21, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 21, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 18, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 8, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg29".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg29() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg29");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg29/input001.jpg");
        inputs.add(avg_images_path + "testAvg29/input002.jpg");
        inputs.add(avg_images_path + "testAvg29/input003.jpg");
        inputs.add(avg_images_path + "testAvg29/input004.jpg");
        inputs.add(avg_images_path + "testAvg29/input005.jpg");
        inputs.add(avg_images_path + "testAvg29/input006.jpg");
        inputs.add(avg_images_path + "testAvg29/input007.jpg");
        inputs.add(avg_images_path + "testAvg29/input008.jpg");
        inputs.add(avg_images_path + "testAvg29/input009.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg29_output.jpg", 40, 1000000000L/2660, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 88, 127, 255);
        //checkHistogramDetails(hdrHistogramDetails, 92, 134, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg30".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg30() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg30");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg30/input001.jpg");
        inputs.add(avg_images_path + "testAvg30/input002.jpg");
        inputs.add(avg_images_path + "testAvg30/input003.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg30_output.jpg", 60, 1000000000L/411, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, -4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    int [] exp_offsets_x = {0, 0, 0};
                    int [] exp_offsets_y = {0, -4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 134, 254);
        //checkHistogramDetails(hdrHistogramDetails, 0, 144, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 107, 254);
    }

    /** Tests Avg algorithm on test samples "testAvg31".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg31() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg31");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg31/input001.jpg");
        inputs.add(avg_images_path + "testAvg31/input002.jpg");
        inputs.add(avg_images_path + "testAvg31/input003.jpg");
        inputs.add(avg_images_path + "testAvg31/input004.jpg");
        inputs.add(avg_images_path + "testAvg31/input005.jpg");
        inputs.add(avg_images_path + "testAvg31/input006.jpg");
        inputs.add(avg_images_path + "testAvg31/input007.jpg");
        inputs.add(avg_images_path + "testAvg31/input008.jpg");
        inputs.add(avg_images_path + "testAvg31/input009.jpg");
        inputs.add(avg_images_path + "testAvg31/input010.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg31_output.jpg", 609, 1000000000L/25, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 24, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 9, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg32".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg32() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg32");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg32/input001.jpg");
        inputs.add(avg_images_path + "testAvg32/input002.jpg");
        inputs.add(avg_images_path + "testAvg32/input003.jpg");
        inputs.add(avg_images_path + "testAvg32/input004.jpg");
        inputs.add(avg_images_path + "testAvg32/input005.jpg");
        inputs.add(avg_images_path + "testAvg32/input006.jpg");
        inputs.add(avg_images_path + "testAvg32/input007.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg32_output.jpg", 335, 1000000000L/120, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 34, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 13, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 36, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg33".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg33() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg33");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg33/input001.jpg");
        inputs.add(avg_images_path + "testAvg33/input002.jpg");
        inputs.add(avg_images_path + "testAvg33/input003.jpg");
        inputs.add(avg_images_path + "testAvg33/input004.jpg");
        inputs.add(avg_images_path + "testAvg33/input005.jpg");
        inputs.add(avg_images_path + "testAvg33/input006.jpg");
        inputs.add(avg_images_path + "testAvg33/input007.jpg");
        inputs.add(avg_images_path + "testAvg33/input008.jpg");
        inputs.add(avg_images_path + "testAvg33/input009.jpg");
        inputs.add(avg_images_path + "testAvg33/input010.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg33_output.jpg", 948, 1000000000L/18, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 81, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 63, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg34".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg34() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg34");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg34/IMG_20180627_121959_0.jpg");
        inputs.add(avg_images_path + "testAvg34/IMG_20180627_121959_1.jpg");
        inputs.add(avg_images_path + "testAvg34/IMG_20180627_121959_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg34_output.jpg", 100, 1000000000L/289, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 108, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 114, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 103, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg35".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg35() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg35");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg35/IMG_20180711_144453_0.jpg");
        inputs.add(avg_images_path + "testAvg35/IMG_20180711_144453_1.jpg");
        inputs.add(avg_images_path + "testAvg35/IMG_20180711_144453_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg35_output.jpg", 100, 1000000000L/2549, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 165, 247);
        checkHistogramDetails(hdrHistogramDetails, 0, 169, 248);
    }

    /** Tests Avg algorithm on test samples "testAvg36".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg36() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg36");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_0.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_1.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_2.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_3.jpg");
        // only test 4 images, to reflect latest behaviour that we take 4 images for this ISO/exposure time
        /*inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_4.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_5.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_6.jpg");
        inputs.add(avg_images_path + "testAvg36/IMG_20180709_114831_7.jpg");*/

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg36_output.jpg", 752, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    int [] exp_offsets_x = {0, -28, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg37".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg37() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg37");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg37/IMG_20180715_173155_0.jpg");
        inputs.add(avg_images_path + "testAvg37/IMG_20180715_173155_1.jpg");
        inputs.add(avg_images_path + "testAvg37/IMG_20180715_173155_2.jpg");
        inputs.add(avg_images_path + "testAvg37/IMG_20180715_173155_3.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg37_output.jpg", 131, 1000000000L/50, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 12, 109, 255);
        //checkHistogramDetails(hdrHistogramDetails, 3, 99, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 99, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 125, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 94, 255);
        checkHistogramDetails(hdrHistogramDetails, 6, 94, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg38".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg38() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg38");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_0.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_1.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_2.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_3.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_4.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_5.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_6.jpg");
        inputs.add(avg_images_path + "testAvg38/IMG_20180716_232102_7.jpg");

        // n.b., this was a zoomed in photo, but can't quite remember the exact zoom level!
        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg38_output.jpg", 1505, 1000000000L/10, 3.95f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });
    }

    /** Tests Avg algorithm on test samples "testAvg39".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg39() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg39");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg39/input001.jpg");
        inputs.add(avg_images_path + "testAvg39/input002.jpg");
        inputs.add(avg_images_path + "testAvg39/input003.jpg");
        inputs.add(avg_images_path + "testAvg39/input004.jpg");
        inputs.add(avg_images_path + "testAvg39/input005.jpg");
        inputs.add(avg_images_path + "testAvg39/input006.jpg");
        inputs.add(avg_images_path + "testAvg39/input007.jpg");
        inputs.add(avg_images_path + "testAvg39/input008.jpg");
        inputs.add(avg_images_path + "testAvg39/input009.jpg");
        inputs.add(avg_images_path + "testAvg39/input010.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg39_output.jpg", 521, 1000000000L/27, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 64, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 25, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg40".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg40() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg40");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg40/input001.jpg");
        inputs.add(avg_images_path + "testAvg40/input002.jpg");
        inputs.add(avg_images_path + "testAvg40/input003.jpg");
        inputs.add(avg_images_path + "testAvg40/input004.jpg");
        inputs.add(avg_images_path + "testAvg40/input005.jpg");
        inputs.add(avg_images_path + "testAvg40/input006.jpg");
        inputs.add(avg_images_path + "testAvg40/input007.jpg");
        inputs.add(avg_images_path + "testAvg40/input008.jpg");
        inputs.add(avg_images_path + "testAvg40/input009.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg40_output.jpg", 199, 1000000000L/120, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 50, 255);
        //checkHistogramDetails(hdrHistogramDetails, 0, 19, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 50, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg41".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg41() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg41");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        // example from Google HDR+ dataset
        // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
        // to the Google HDR+ result
        inputs.add(avg_images_path + "testAvg41/input001.jpg");
        inputs.add(avg_images_path + "testAvg41/input002.jpg");
        inputs.add(avg_images_path + "testAvg41/input003.jpg");
        inputs.add(avg_images_path + "testAvg41/input004.jpg");
        inputs.add(avg_images_path + "testAvg41/input005.jpg");
        inputs.add(avg_images_path + "testAvg41/input006.jpg");
        inputs.add(avg_images_path + "testAvg41/input007.jpg");
        inputs.add(avg_images_path + "testAvg41/input008.jpg");
        inputs.add(avg_images_path + "testAvg41/input009.jpg");
        inputs.add(avg_images_path + "testAvg41/input010.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg41_output.jpg", 100, 1000000000L/869, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 49, 255);
        checkHistogramDetails(hdrHistogramDetails, 0, 37, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg42".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg42() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg42");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg42/IMG_20180822_145152_0.jpg");
        inputs.add(avg_images_path + "testAvg42/IMG_20180822_145152_1.jpg");
        inputs.add(avg_images_path + "testAvg42/IMG_20180822_145152_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg42_output.jpg", 100, 1000000000L/2061, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 67, 254);
        checkHistogramDetails(hdrHistogramDetails, 0, 61, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg43".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg43() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg43");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg43/IMG_20180831_143226_0.jpg");
        inputs.add(avg_images_path + "testAvg43/IMG_20180831_143226_1.jpg");
        inputs.add(avg_images_path + "testAvg43/IMG_20180831_143226_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg43_output.jpg", 100, 1000000000L/2152, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        checkHistogramDetails(hdrHistogramDetails, 0, 69, 253);
    }

    /** Tests Avg algorithm on test samples "testAvg44".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg44() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg44");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg44/IMG_20180830_133917_0.jpg");
        inputs.add(avg_images_path + "testAvg44/IMG_20180830_133917_1.jpg");
        inputs.add(avg_images_path + "testAvg44/IMG_20180830_133917_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg44_output.jpg", 40, 1000000000L/2130, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg45".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg45() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg45");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg45/IMG_20180719_133947_0.jpg");
        inputs.add(avg_images_path + "testAvg45/IMG_20180719_133947_1.jpg");
        inputs.add(avg_images_path + "testAvg45/IMG_20180719_133947_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg45_output.jpg", 100, 1000000000L/865, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg46".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg46() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg46");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_0.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_1.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_2.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_3.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_4.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_5.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_6.jpg");
        inputs.add(avg_images_path + "testAvg46/IMG_20180903_203141_7.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg46_output.jpg", 1505, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg47".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg47() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg47");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg47/IMG_20180911_114752_0.jpg");
        inputs.add(avg_images_path + "testAvg47/IMG_20180911_114752_1.jpg");
        inputs.add(avg_images_path + "testAvg47/IMG_20180911_114752_2.jpg");
        inputs.add(avg_images_path + "testAvg47/IMG_20180911_114752_3.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg47_output.jpg", 749, 1000000000L/12, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg48".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg48() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg48");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_0.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_1.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_2.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_3.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_4.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_5.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_6.jpg");
        inputs.add(avg_images_path + "testAvg48/IMG_20180911_110520_7.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg48_output.jpg", 1196, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg49".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg49() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg49");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_0.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_1.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_2.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_3.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_4.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_5.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_6.jpg");
        inputs.add(avg_images_path + "testAvg49/IMG_20180911_120200_7.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg49_output.jpg", 1505, 1000000000L/10, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg50".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg50() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg50");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg50/IMG_20181015_144335_0.jpg");
        inputs.add(avg_images_path + "testAvg50/IMG_20181015_144335_1.jpg");
        inputs.add(avg_images_path + "testAvg50/IMG_20181015_144335_2.jpg");
        inputs.add(avg_images_path + "testAvg50/IMG_20181015_144335_3.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg50_output.jpg", 114, 1000000000L/33, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg51".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg51() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg51");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_0.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_1.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_2.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_3.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_4.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_5.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_6.jpg");
        inputs.add(avg_images_path + "testAvg51/IMG_20181025_182917_7.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg51_output.jpg", 1600, 1000000000L/3, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
                if( index == 1 ) {
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    assertTrue(mActivity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                }
                else if( index == 7 ) {
                    int [] exp_offsets_x = {0, 60, 0};
                    int [] exp_offsets_y = {0, 28, 0};
                    checkHDROffsets(exp_offsets_x, exp_offsets_y, mActivity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
    }

    /** Tests Avg algorithm on test samples "testAvg52".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvg52() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg52");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvg52/IMG_20181119_144836_0.jpg");
        inputs.add(avg_images_path + "testAvg52/IMG_20181119_144836_1.jpg");
        inputs.add(avg_images_path + "testAvg52/IMG_20181119_144836_2.jpg");

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvg52_output.jpg", 100, 1000000000L/297, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
    }

    /** Tests Avg algorithm on test samples "testAvgtemp".
     *  Used for one-off testing, or to recreate NR images from the base exposures to test an updated alorithm.
     *  The test images should be copied to the test device into DCIM/testOpenCamera/testdata/hdrsamples/testAvgtemp/ .
     * @throws IOException
     * @throws InterruptedException
     */
    public void testAvgtemp() throws IOException, InterruptedException {
        Log.d(TAG, "testAvgtemp");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();
        inputs.add(avg_images_path + "testAvgtemp/input0.png");
        /*inputs.add(avg_images_path + "testAvgtemp/input0.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input1.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input2.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input3.jpg");*/
        /*inputs.add(avg_images_path + "testAvgtemp/input4.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input5.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input6.jpg");
        inputs.add(avg_images_path + "testAvgtemp/input7.jpg");*/

        HistogramDetails hdrHistogramDetails = subTestAvg(inputs, "testAvgtemp_output.jpg", 250, 1000000000L/33, 1.0f, new TestAvgCallback() {
            @Override
            public void doneProcessAvg(int index) {
                Log.d(TAG, "doneProcessAvg: " + index);
            }
        });

        //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
    }

    private int tonemapConvert(int in, TonemapCurve curve, int channel) {
        float in_f = in/255.0f;
        float out_f = 0.0f;
        // first need to undo the gamma that's already been applied to the test input images (since the tonemap curve also reapplies gamma)
        in_f = (float)Math.pow(in_f, 2.2f);
        boolean found = false;
        for(int i=0;i<curve.getPointCount(channel)-1 && !found;i++) {
            PointF p0 = curve.getPoint(channel, i);
            PointF p1 = curve.getPoint(channel, i+1);
            if( in_f >= p0.x && in_f <= p1.x ) {
                found = true;
                float alpha = (in_f - p0.x) / (p1.x - p0.x);
                out_f = p0.y + alpha * (p1.y - p0.y);
            }
        }
        if( !found ) {
            Log.d(TAG, "failed to convert: " + in_f);
            throw new RuntimeException();
        }
        return (int)(255.0f * out_f + 0.5f);
    }

    private HistogramDetails subTestLogProfile(String image_path, String output_name) throws IOException, InterruptedException {
        Log.d(TAG, "subTestLogProfile");

        if( !mPreview.usingCamera2API() ) {
            Log.d(TAG, "test requires camera2 api");
            return null;
        }

        View switchVideoButton = mActivity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        clickView(switchVideoButton);
        waitUntilCameraOpened();

        Bitmap bitmap = getBitmapFromFile(image_path);

        CameraController2 camera_controller2 = (CameraController2)mPreview.getCameraController();
        TonemapCurve curve = camera_controller2.testGetTonemapCurve();

        // compute lookup tables for faster operation
        int [][] tonemap_lut = new int[3][];
        for(int channel=0;channel<3;channel++) {
            Log.d(TAG, "compute tonemap_lut: " + channel);
            tonemap_lut[channel] = new int[256];
            for(int i=0;i<256;i++) {
                tonemap_lut[channel][i] = tonemapConvert(i, curve, channel);
            }
        }


        int [] buffer = new int[bitmap.getWidth()];
        for(int y=0;y<bitmap.getHeight();y++) {
            if( y % 100 == 0 ) {
                Log.d(TAG, "processing y = " + y + " / " + bitmap.getHeight());
            }
            bitmap.getPixels(buffer, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
            for(int x=0;x<bitmap.getWidth();x++) {
                //Log.d(TAG, "    processing x = " + x + " / " + bitmap.getWidth());
                int color = buffer[x];
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int a = Color.alpha(color);
                /*r = tonemapConvert(r, curve, 0);
                g = tonemapConvert(g, curve, 1);
                b = tonemapConvert(b, curve, 2);*/
                r = tonemap_lut[0][r];
                g = tonemap_lut[1][g];
                b = tonemap_lut[2][b];

                buffer[x] = Color.argb(a, r, g, b);
            }
            bitmap.setPixels(buffer, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
        }

        saveBitmap(bitmap, output_name);
        HistogramDetails hdrHistogramDetails = checkHistogram(bitmap);
        bitmap.recycle();
        System.gc();

        Thread.sleep(500);

        return hdrHistogramDetails;
    }

    /** Tests video log profile algorithm on test samples in "testAvg1".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile1() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile1");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "medium");
        editor.apply();
        updateForSettings();

        String image_path = avg_images_path + "testAvg1/input0.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile1_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 1, 23, 253);
    }

    /** Tests video log profile algorithm on test samples in "testAvg20".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile2() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile2");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "medium");
        editor.apply();
        updateForSettings();

        String image_path = avg_images_path + "testAvg20/input0.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile2_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 0, 58, 243);
    }

    /** Tests video log profile algorithm on test samples in "testLogProfile3".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile3() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile3");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "medium");
        editor.apply();
        updateForSettings();

        String image_path = logprofile_images_path + "testLogProfile3.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile3_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 0, 157, 255);
    }

    /** Tests video log profile algorithm on test samples in "testAvg1".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile1_extra_strong() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile1_extra_strong");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "extra_strong");
        editor.apply();
        updateForSettings();

        String image_path = avg_images_path + "testAvg1/input0.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile1_extra_strong_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 2, 67, 254);
    }

    /** Tests video log profile algorithm on test samples in "testAvg20".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile2_extra_strong() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile2_extra_strong");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "extra_strong");
        editor.apply();
        updateForSettings();

        String image_path = avg_images_path + "testAvg20/input0.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile2_extra_strong_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 0, 126, 250);
    }

    /** Tests video log profile algorithm on test samples in "testLogProfile3".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testLogProfile3_extra_strong() throws IOException, InterruptedException {
        Log.d(TAG, "testLogProfile3_extra_strong");

        setToDefault();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PreferenceKeys.VideoLogPreferenceKey, "extra_strong");
        editor.apply();
        updateForSettings();

        String image_path = logprofile_images_path + "testLogProfile3.jpg";

        HistogramDetails hdrHistogramDetails = subTestLogProfile(image_path, "testLogProfile3_extra_strong_output.jpg");

        checkHistogramDetails(hdrHistogramDetails, 0, 212, 255);
    }

    private void saveBitmap(Bitmap bitmap, String name) throws IOException {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/" + name);
        OutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        outputStream.close();
        mActivity.getStorageUtils().broadcastFile(file, true, false, true);
    }

    /**
     * @param panorama_pics_per_screen The value of panorama_pics_per_screen used when taking the input photos.
     * @param camera_angle_x The value of preview.getViewAngleX(for_preview=false) (in degrees) when taking the input photos (on the device used).
     * @param camera_angle_y The value of preview.getViewAngleY(for_preview=false) (in degrees) when taking the input photos (on the device used).
     */
    private void subTestPanorama(List<String> inputs, String output_name, String gyro_debug_info_filename, float panorama_pics_per_screen, float camera_angle_x, float camera_angle_y, float gyro_tol_degrees) throws IOException, InterruptedException {
        Log.d(TAG, "subTestPanorama");

        // we set panorama_pics_per_screen in the test rather than using MyApplicationInterface.panorama_pics_per_screen,
        // in case the latter value is changed

        boolean first = true;
        Matrix scale_matrix = null;
        int bitmap_width = 0;
        int bitmap_height = 0;
        List<Bitmap> bitmaps = new ArrayList<>();
        for(String input : inputs) {
            Bitmap bitmap = getBitmapFromFile(input);

            if( first ) {
                bitmap_width = bitmap.getWidth();
                bitmap_height = bitmap.getHeight();
                Log.d(TAG, "bitmap_width: " + bitmap_width);
                Log.d(TAG, "bitmap_height: " + bitmap_height);

                final int max_height = 2080;
                //final int max_height = 2079; // test non power of 2
                if( bitmap_height > max_height ) {
                    float scale = ((float)max_height) / ((float)bitmap_height);
                    Log.d(TAG, "scale: " + scale);
                    scale_matrix = new Matrix();
                    scale_matrix.postScale(scale, scale);
                }

                first = false;
            }

            // downscale
            if( scale_matrix != null ) {
                Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap_width, bitmap_height, scale_matrix, true);
                bitmap.recycle();
                bitmap = new_bitmap;
            }

            bitmaps.add(bitmap);
        }

        bitmap_width = bitmaps.get(0).getWidth();
        bitmap_height = bitmaps.get(0).getHeight();
        Log.d(TAG, "bitmap_width is now: " + bitmap_width);
        Log.d(TAG, "bitmap_height is now: " + bitmap_height);


        /*ImageSaver.GyroDebugInfo gyro_debug_info = null;
        if( gyro_debug_info_filename != null ) {
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(gyro_debug_info_filename);
            }
            catch(FileNotFoundException e) {
                Log.e(TAG, "failed to load gyro debug info file: " + gyro_debug_info_filename);
                e.printStackTrace();
                throw new RuntimeException();
            }

            gyro_debug_info = new ImageSaver.GyroDebugInfo();
            if( !ImageSaver.readGyroDebugXml(inputStream, gyro_debug_info) ) {
                Log.e(TAG, "failed to read gyro debug xml");
                throw new RuntimeException();
            }
            else if( gyro_debug_info.image_info.size() != bitmaps.size() ) {
                Log.e(TAG, "gyro debug xml has unexpected number of images: " + gyro_debug_info.image_info.size());
                throw new RuntimeException();
            }
        }*/
        //bitmaps.subList(2,bitmaps.size()).clear(); // test

        Bitmap panorama = null;
        try {
            final boolean crop = true;
            //final boolean crop = false; // test
            panorama = mActivity.getApplicationInterface().getPanoramaProcessor().panorama(bitmaps, panorama_pics_per_screen, camera_angle_y, crop);
        }
        catch(PanoramaProcessorException e) {
            e.printStackTrace();
            fail();
        }

        saveBitmap(panorama, output_name);
        Thread.sleep(500);

        // check we've cropped correctly:
        final float black_factor = 0.9f;
        // top:
        int n_black = 0;
        for(int i=0;i<panorama.getWidth();i++) {
            int color = panorama.getPixel(i, 0);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getWidth()*black_factor ) {
            Log.e(TAG, "too many black pixels on top border: " + n_black);
            fail();
        }
        // bottom:
        n_black = 0;
        for(int i=0;i<panorama.getWidth();i++) {
            int color = panorama.getPixel(i, panorama.getHeight()-1);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getWidth()*black_factor ) {
            Log.e(TAG, "too many black pixels on bottom border: " + n_black);
            fail();
        }
        // left:
        n_black = 0;
        for(int i=0;i<panorama.getHeight();i++) {
            int color = panorama.getPixel(0, i);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getHeight()*black_factor ) {
            Log.e(TAG, "too many black pixels on left border: " + n_black);
            fail();
        }
        // right:
        n_black = 0;
        for(int i=0;i<panorama.getHeight();i++) {
            int color = panorama.getPixel(panorama.getWidth()-1, i);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getHeight()*black_factor ) {
            Log.e(TAG, "too many black pixels on right border: " + n_black);
            fail();
        }
    }

    /** Tests panorama algorithm on test samples "testPanorama1".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama1() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama1");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        inputs.add(panorama_images_path + "testPanorama1/input0.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input1.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input2.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input3.jpg");
        float camera_angle_x = 62.93796f;
        float camera_angle_y = 47.44656f;
        float panorama_pics_per_screen = 2.0f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (47.44656/49.56283);
        String output_name = "testPanorama1_output.jpg";

        subTestPanorama(inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 2.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama2".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama2() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama2");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        /*final float panorama_pics_per_screen = 1.0f;
        //inputs.add(panorama_images_path + "testPanorama2xxx/input0.jpg");
        inputs.add(panorama_images_path + "testPanorama2xxx/input1.jpg");
        inputs.add(panorama_images_path + "testPanorama2xxx/input2.jpg");*/
        /*final float panorama_pics_per_screen = 2.0f;
        //inputs.add(panorama_images_path + "testPanorama1/input0.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input1.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input2.jpg");
        inputs.add(panorama_images_path + "testPanorama1/input3.jpg");
        String output_name = "testPanorama1_output.jpg";*/
        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama2/input0.jpg");
        inputs.add(panorama_images_path + "testPanorama2/input1.jpg");
        inputs.add(panorama_images_path + "testPanorama2/input2.jpg");
        inputs.add(panorama_images_path + "testPanorama2/input3.jpg");
        inputs.add(panorama_images_path + "testPanorama2/input4.jpg");
        inputs.add(panorama_images_path + "testPanorama2/input5.jpg");
        String output_name = "testPanorama2_output.jpg";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 2.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama3".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama3() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama3");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131249.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131252.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131255.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131258.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131301.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131303.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131305.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131307.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131315.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131317.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131320.jpg");
        String output_name = "testPanorama3_output.jpg";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama3", with panorama_pics_per_screen set
     *  to 4.0.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama3_picsperscreen2() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama3_picsperscreen2");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 2.0f;
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131249.jpg");
        //inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131252.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131255.jpg");
        //inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131258.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131301.jpg");
        //inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131303.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131305.jpg");
        //inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131307.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131315.jpg");
        //inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131317.jpg");
        inputs.add(panorama_images_path + "testPanorama3/IMG_20190214_131320.jpg");
        String output_name = "testPanorama3_picsperscreen2_output.jpg";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama4".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama4() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama4");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_0.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_1.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_2.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_3.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_4.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_5.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_6.jpg");
        inputs.add(panorama_images_path + "testPanorama4/IMG_20190222_225317_7.jpg");
        String output_name = "testPanorama4_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama4/IMG_20190222_225317.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama5".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama5() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama5");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_0.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_1.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_2.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_3.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_4.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_5.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_6.jpg");
        inputs.add(panorama_images_path + "testPanorama5/IMG_20190223_220524_7.jpg");
        String output_name = "testPanorama5_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama5/IMG_20190223_220524.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama6".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama6() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama6");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_0.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_1.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_2.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_3.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_4.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_5.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_6.jpg");
        inputs.add(panorama_images_path + "testPanorama6/IMG_20190225_154232_7.jpg");
        String output_name = "testPanorama6_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama6/IMG_20190225_154232.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama7".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama7() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama7");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 4.0f;
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_0.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_1.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_2.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_3.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_4.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_5.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_6.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_7.jpg");
        inputs.add(panorama_images_path + "testPanorama7/IMG_20190225_155510_8.jpg");
        String output_name = "testPanorama7_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama7/IMG_20190225_155510.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama8".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama8() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama8");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 2.0f;
        inputs.add(panorama_images_path + "testPanorama8/IMG_20190227_001431_0.jpg");
        inputs.add(panorama_images_path + "testPanorama8/IMG_20190227_001431_1.jpg");
        inputs.add(panorama_images_path + "testPanorama8/IMG_20190227_001431_2.jpg");
        inputs.add(panorama_images_path + "testPanorama8/IMG_20190227_001431_3.jpg");
        String output_name = "testPanorama8_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama8/IMG_20190227_001431.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/52.26029);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama9".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama9() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama9");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.0f;
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_0.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_1.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_2.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_3.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_4.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_5.jpg");
        inputs.add(panorama_images_path + "testPanorama9/IMG_20190301_145213_6.jpg");
        String output_name = "testPanorama9_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama9/IMG_20190301_145213.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/50.44399);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);

        Thread.sleep(1000); // need to wait for debug images to be saved/broadcast?
    }

    /** Tests panorama algorithm on test samples "testPanorama10".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama10() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama10");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.0f;
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_0.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_1.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_2.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_3.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_4.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_5.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_6.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_7.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_8.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_9.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_10.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_11.jpg");
        inputs.add(panorama_images_path + "testPanorama10/IMG_20190301_144948_12.jpg");
        String output_name = "testPanorama10_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama10/IMG_20190301_144948.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/50.44399);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama11".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama11() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama11");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.0f;
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_0.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_1.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_2.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_3.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_4.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_5.jpg");
        inputs.add(panorama_images_path + "testPanorama11/IMG_20190306_143652_6.jpg");
        String output_name = "testPanorama11_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama11/IMG_20190306_143652.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/50.44399);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama12".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama12() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama12");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.0f;
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_0.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_1.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_2.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_3.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_4.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_5.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_6.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_7.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_8.jpg");
        inputs.add(panorama_images_path + "testPanorama12/IMG_20190308_152008_9.jpg");
        String output_name = "testPanorama12_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama12/IMG_20190308_152008.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;
        // these images were taken with incorrect camera view angles, so we compensate in the test:
        panorama_pics_per_screen *= (50.282097/50.44399);

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama13".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama13() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama13");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.0f;
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_0.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_1.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_2.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_3.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_4.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_5.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_6.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_7.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_8.jpg");
        inputs.add(panorama_images_path + "testPanorama13/IMG_20190512_014152_9.jpg");
        String output_name = "testPanorama13_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama13/IMG_20190512_014152.xml";
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama14".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama14() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama14");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_0.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_1.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_2.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_3.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_4.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_5.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_6.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_7.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_8.jpg");
        inputs.add(panorama_images_path + "testPanorama14/IMG_20190513_151249_9.jpg");
        String output_name = "testPanorama14_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama14/IMG_20190513_151249.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama15".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama15() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama15");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_0.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_1.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_2.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_3.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_4.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_5.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_6.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_7.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_8.jpg");
        inputs.add(panorama_images_path + "testPanorama15/IMG_20190513_151624_9.jpg");
        String output_name = "testPanorama15_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama15/IMG_20190513_151624.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama16".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama16() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama16");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_0.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_1.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_2.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_3.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_4.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_5.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_6.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_7.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_8.jpg");
        inputs.add(panorama_images_path + "testPanorama16/IMG_20190624_151731_9.jpg");
        String output_name = "testPanorama16_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama16/IMG_20190624_151731.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama17".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama17() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama17");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_0.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_1.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_2.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_3.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_4.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_5.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_6.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_7.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_8.jpg");
        inputs.add(panorama_images_path + "testPanorama17/IMG_20190625_135423_9.jpg");
        String output_name = "testPanorama17_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama17/IMG_20190625_135423.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama18".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama18() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama18");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_0.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_1.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_2.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_3.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_4.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_5.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_6.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_7.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_8.jpg");
        inputs.add(panorama_images_path + "testPanorama18/IMG_20190626_152559_9.jpg");
        String output_name = "testPanorama18_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama18/IMG_20190626_152559.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama19".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama19() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama19");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_0.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_1.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_2.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_3.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_4.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_5.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_6.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_7.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_8.jpg");
        inputs.add(panorama_images_path + "testPanorama19/IMG_20190627_134059_9.jpg");
        String output_name = "testPanorama19_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama19/IMG_20190627_134059.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama20".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama20() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama20");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_0.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_1.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_2.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_3.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_4.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_5.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_6.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_7.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_8.jpg");
        inputs.add(panorama_images_path + "testPanorama20/IMG_20190628_145027_9.jpg");
        String output_name = "testPanorama20_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama20/IMG_20190628_145027.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama21".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama21() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama21");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_0.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_1.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_2.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_3.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_4.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_5.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_6.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_7.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_8.jpg");
        inputs.add(panorama_images_path + "testPanorama21/IMG_20190628_145552_9.jpg");
        String output_name = "testPanorama21_output.jpg";
        String gyro_name = panorama_images_path + "testPanorama21/IMG_20190628_145552.xml";
        //gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
    }

    /** Tests panorama algorithm on test samples "testPanorama22".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama22() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama22");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_0.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_1.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_2.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_3.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_4.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_5.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_6.jpg");
        inputs.add(panorama_images_path + "testPanorama22/IMG_20190629_165627_7.jpg");
        String output_name = "testPanorama22_output.jpg";
        String gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama23".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama23() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama23");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama23/IMG_20190702_145916_0.jpg");
        inputs.add(panorama_images_path + "testPanorama23/IMG_20190702_145916_1.jpg");
        inputs.add(panorama_images_path + "testPanorama23/IMG_20190702_145916_2.jpg");
        inputs.add(panorama_images_path + "testPanorama23/IMG_20190702_145916_3.jpg");
        inputs.add(panorama_images_path + "testPanorama23/IMG_20190702_145916_4.jpg");
        String output_name = "testPanorama23_output.jpg";
        String gyro_name = null;
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama24".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama24() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama24");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_0.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_1.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_2.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_3.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_4.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_5.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_6.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_7.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_8.jpg");
        inputs.add(panorama_images_path + "testPanorama24/IMG_20190703_154333_9.jpg");
        String output_name = "testPanorama24_output.jpg";
        String gyro_name = null;
        // taken with OnePlus 3T, Camera2 API:
        float camera_angle_x = 62.93796f;
        float camera_angle_y = 47.44656f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama25".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama25() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama25");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        //float panorama_pics_per_screen = 3.33333f / 2.0f;
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_0.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_1.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_2.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_3.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_4.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_5.jpg");
        inputs.add(panorama_images_path + "testPanorama25/IMG_20190706_215940_6.jpg");
        String output_name = "testPanorama25_output.jpg";
        String gyro_name = null;
        // taken with Nokia 8, Camera2 API:
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama26".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama26() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama26");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_0.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_1.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_2.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_3.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_4.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_5.jpg");
        inputs.add(panorama_images_path + "testPanorama26/IMG_20190706_214842_6.jpg");
        String output_name = "testPanorama26_output.jpg";
        String gyro_name = null;
        // taken with Nokia 8, Camera2 API:
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama27".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama27() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama27");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_0.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_1.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_2.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_3.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_4.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_5.jpg");
        inputs.add(panorama_images_path + "testPanorama27/IMG_20190706_192120_6.jpg");
        String output_name = "testPanorama27_output.jpg";
        String gyro_name = null;
        // taken with Nokia 8, Camera2 API:
        float camera_angle_x = 66.708595f;
        float camera_angle_y = 50.282097f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama28".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama28() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama28");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        // right-to-left:
        /*inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_9.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_8.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_7.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_6.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_5.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_4.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_3.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_2.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_1.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_0.jpg");*/
        // converted from original JPEGs to PNG using Nokia 8:
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_0.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_1.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_2.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_3.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_4.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_5.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_6.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_7.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_8.png");
        inputs.add(panorama_images_path + "testPanorama28/input_bitmap_9.png");
        String output_name = "testPanorama28_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama28", but with a nbnq similar set of
     *  input images. Instead of converting the original JPEGs to PNG on Nokia 8, this was done on
     *  the Samsung Galaxy S10e, which gives small differences, but enough to show up potential
     *  stability issues.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama28_galaxys10e() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama28_galaxys10e");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        // right-to-left:
        /*inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_9.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_8.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_7.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_6.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_5.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_4.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_3.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_2.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_1.jpg");
        inputs.add(panorama_images_path + "testPanorama28/IMG_20190725_134756_0.jpg");*/
        // converted from original JPEGs to PNG using Samsung Galaxy S10e:
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_0.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_1.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_2.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_3.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_4.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_5.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_6.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_7.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_8.png");
        inputs.add(panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_9.png");
        String output_name = "testPanorama28_galaxys10e_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama29".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama29() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama29");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        // right-to-left:
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_9.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_8.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_7.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_6.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_5.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_4.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_3.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_2.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_1.jpg");
        inputs.add(panorama_images_path + "testPanorama29/IMG_20190719_145852_0.jpg");
        String output_name = "testPanorama29_output.jpg";
        String gyro_name = null;
        // taken with Nokia 8, old API:
        float camera_angle_x = 66.1062f;
        float camera_angle_y = 49.88347f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama30".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama30() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama30");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        /*inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_0.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_1.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_2.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_3.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_4.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_5.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_6.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_7.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_8.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_9.jpg");*/
        // converted from original JPEGs to PNG using Nokia 8:
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_0.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_1.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_2.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_3.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_4.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_5.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_6.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_7.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_8.png");
        inputs.add(panorama_images_path + "testPanorama30/nokia8_input_bitmap_9.png");
        String output_name = "testPanorama30_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, old API, standard rear camera:
        // n.b., camera angles are indeed the exact same as with Camera2
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama30", but with a nbnq similar set of
     *  input images. Instead of converting the original JPEGs to PNG on Nokia 8, this was done on
     *  the Samsung Galaxy S10e, which gives small differences, but enough to show up potential
     *  stability issues.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama30_galaxys10e() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama30_galaxys10e");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        /*inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_0.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_1.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_2.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_3.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_4.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_5.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_6.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_7.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_8.jpg");
        inputs.add(panorama_images_path + "testPanorama30/IMG_20190723_142934_9.jpg");*/
        // converted from original JPEGs to PNG using Samsung Galaxy S10e:
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_0.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_1.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_2.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_3.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_4.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_5.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_6.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_7.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_8.png");
        inputs.add(panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_9.png");
        String output_name = "testPanorama30_galaxys10e_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, old API, standard rear camera:
        // n.b., camera angles are indeed the exact same as with Camera2
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama31".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama31() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama31");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_0.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_1.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_2.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_3.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_4.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_5.jpg");
        inputs.add(panorama_images_path + "testPanorama31/IMG_20190704_135633_6.jpg");
        String output_name = "testPanorama31_output.jpg";
        String gyro_name = null;
        // taken with OnePlus 3T, Camera2 API:
        float camera_angle_x = 62.93796f;
        float camera_angle_y = 47.44656f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama3".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama32() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama32");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_0.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_1.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_2.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_3.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_4.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_5.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_6.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_7.jpg");
        inputs.add(panorama_images_path + "testPanorama32/IMG_20190705_145938_8.jpg");
        String output_name = "testPanorama32_output.jpg";
        String gyro_name = null;
        // taken with OnePlus 3T, old API:
        float camera_angle_x = 60.0f;
        float camera_angle_y = 45.0f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama33".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama33() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama33");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_0.jpg");
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_1.jpg");
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_2.jpg");
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_3.jpg");
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_4.jpg");
        inputs.add(panorama_images_path + "testPanorama33/IMG_20190713_013437_5.jpg");
        String output_name = "testPanorama33_output.jpg";
        String gyro_name = null;
        // taken with Nokia 8, old API:
        float camera_angle_x = 66.1062f;
        float camera_angle_y = 49.88347f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama34".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama34() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama34");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        // right-to-left:
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_9.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_8.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_7.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_6.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_5.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_4.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_3.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_2.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_1.jpg");
        inputs.add(panorama_images_path + "testPanorama34/IMG_20190717_144042_0.jpg");
        String output_name = "testPanorama34_output.jpg";
        String gyro_name = null;
        // taken with Nexus 6, old API:
        float camera_angle_x = 62.7533f;
        float camera_angle_y = 47.298824f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama35".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama35() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama35");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_0.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_1.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_2.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_3.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_4.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_5.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_6.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_7.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_8.jpg");
        inputs.add(panorama_images_path + "testPanorama35/IMG_20190717_145114_9.jpg");
        String output_name = "testPanorama35_output.jpg";
        String gyro_name = null;
        // taken with Nexus 7, old API:
        float camera_angle_x = 55.0f;
        float camera_angle_y = 41.401073f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama36".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama36() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama36");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_0.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_1.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_2.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_3.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_4.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_5.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_6.jpg");
        inputs.add(panorama_images_path + "testPanorama36/IMG_20190722_201331_7.jpg");
        String output_name = "testPanorama36_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, Camera2 API, ultra wide rear camera:
        float camera_angle_x = 104.00253f;
        float camera_angle_y = 81.008804f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama37".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama37() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama37");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_0.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_1.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_2.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_3.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_4.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_5.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_6.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_7.jpg");
        inputs.add(panorama_images_path + "testPanorama37/IMG_20190723_203441_8.jpg");
        String output_name = "testPanorama37_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, old API, standard rear camera:
        // n.b., camera angles are indeed the exact same as with Camera2
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }

    /** Tests panorama algorithm on test samples "testPanorama38".
     * @throws IOException
     * @throws InterruptedException
     */
    public void testPanorama38() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama38");

        setToDefault();

        // list assets
        List<String> inputs = new ArrayList<>();

        float panorama_pics_per_screen = 3.33333f;
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_0.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_1.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_2.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_3.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_4.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_5.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_6.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_7.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_8.jpg");
        inputs.add(panorama_images_path + "testPanorama38/IMG_20190722_141148_9.jpg");
        String output_name = "testPanorama38_output.jpg";
        String gyro_name = null;
        // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
        float camera_angle_x = 66.3177f;
        float camera_angle_y = 50.04736f;

        subTestPanorama(inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
    }
}
