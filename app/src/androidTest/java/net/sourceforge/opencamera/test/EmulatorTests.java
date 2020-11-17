package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Subset of tests for firebase devices
 * Doesn't not contain tests that require network,
 * usage of SAF
 */
public class EmulatorTests {
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        /*
         * Video tests
         */
        if( MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoImuInfo"));
        }

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoAudioControl"));
        }
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitles"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoStabilization"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoExposureLock"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMacro"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshot"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoFlashVideo"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoTimerInterrupt"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoPopup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoTimerPopup"));

        // tests for video log profile (but these don't actually record video)
        if( MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile1"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile2"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile3"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile1_extra_strong"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile2_extra_strong"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile3_extra_strong"));
        }

        /*
         * Photo tests
         */
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelLowMemory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAngles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAnglesLowMemory"));
        // other tests:
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoStabilise"));
        }
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAudioButton"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNoThumbnail"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraAll"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraMulti"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerBackground"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerSettings"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerPopup"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeat"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeat"));
        if( MainActivityTest.test_camera2 ) {
            // test_wait_capture_result only relevant for Camera2 API
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeatWaitCaptureResult"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testKeyboardControls"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoBackgroundHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRSaveExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRAutoStabilise"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanorama"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaMax"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaCancel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaCancelBySettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder1"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder2"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder3"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder4"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolderUnicode"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolderEmpty"));
        }
        // testTakePhotoPreviewPausedShare should be last, as sharing the image may sometimes cause later tests to hang
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedShare"));

        return suite;
    }
}
