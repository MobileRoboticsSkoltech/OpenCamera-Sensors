package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class EmulatorTests {
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        /**
         * Video tests
         */
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoImuInfo"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoAudioControl"));
        }
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitles"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideoDurationLimit"));
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

        /**
         * Photo tests
         */
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelLowMemory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAngles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAnglesLowMemory"));
        // other tests:
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuous"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuousNoTouch"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoStabilise"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashAuto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashTorch"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAudioButton"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNoAutofocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNoThumbnail"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashBug"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraAll"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraMulti"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraScreenFlash"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExposureCompensation"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedLandscape"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedPortrait"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPaused"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedAudioButton"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrash"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrash2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoQuickFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeatFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeatFocusLocked"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAfterFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoSingleTap"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDoubleTap"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAlt"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerBackground"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerSettings"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerPopup"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeat"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPicture1"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPicture2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeat"));
        if( MainActivityTest.test_camera2 ) {
            // test_wait_capture_result only relevant for Camera2 API
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeatWaitCaptureResult"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testKeyboardControls"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoStamp"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDRO"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDROPhotoStamp"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoBackgroundHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRSaveExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRAutoStabilise"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRPhotoStamp"));
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
