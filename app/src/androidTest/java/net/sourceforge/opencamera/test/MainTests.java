package net.sourceforge.opencamera.test;

import android.os.Build;

import junit.framework.Test;
import junit.framework.TestSuite;

public class MainTests {
    // Tests that don't fit into another of the Test suites
    public static Test suite() {
        /*return new TestSuiteBuilder(AllTests.class)
        .includeAllPackagesUnderHere()
        .build();*/
        TestSuite suite = new TestSuite(MainTests.class.getName());
        // put these tests first as they require various permissions be allowed, that can only be set by user action
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationSettings"));
        // other tests:
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPause"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testImmediatelyQuit"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testStartCameraPreviewCount"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCamera2PrefUpgrade"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveModes"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFlashVideoMode"));
            //suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveFlashTorchSwitchCamera"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFlashStartup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFlashStartup2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDRRestart"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPreviewSize"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPreviewSizeWYSIWYG"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testResolutionMaxMP"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testResolutionBurst"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAutoFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAutoFocusCorners"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPopup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPopupLeftLayout"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testRightLayout"));
        //suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPopupLayout")); // don't autotest for now, see comments for the test
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchResolution"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFaceDetection"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusFlashAvailability"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusSwitchVideoSwitchCameras"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusRemainMacroSwitchCamera"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusRemainMacroSwitchPhoto"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusSaveMacroSwitchPhoto"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusSwitchVideoResetContinuous"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureRepeatTouch"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureSwitchAuto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousVideoFocusForPhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testStartupAutoFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveQuality"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testZoom"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testZoomIdle"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testZoomSwitchCamera"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchCameraIdle"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchCameraRepeat"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTouchFocusQuick"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testGallery"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettings"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsSaveLoad"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserNew"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserInvalid"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveFolderHistory"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveFolderHistorySAF"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsPrivacyPolicy"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPreviewRotation"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLayoutNoLimits"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLayoutNoLimitsStartup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCameraModes"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFailOpenCamera"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAudioControlIcon"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIconsAgainstCameras"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testOnError"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testGPSString"));
        }
        if( MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPreviewBitmap"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoFPSHighSpeed"));
        }
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            // intensive test, can crash when run as suite on older devices (Nexus 6, Nexus 7) with Camera2 at least
            // also run this test last, just in case
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchCameraRepeat2"));
        }
        return suite;
    }
}
