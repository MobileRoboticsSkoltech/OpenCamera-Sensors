package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class PhotoCamera2Tests {
    // Tests related to taking photos that require Camera2 - only need to run this suite with Camera2
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoFocusReleaseDuringPhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualISOExposure"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualWB"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRaw"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawWaitCaptureResult"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawMulti"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawOnly"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawExpoWaitCaptureResult"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawOnlyExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrashRaw"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrashRaw2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExpo5"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRSaveExpoRaw"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRSaveExpoRawOnly"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFocusBracketing"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFocusBracketingHeavy"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFocusBracketingCancel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawFocusBracketing"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawOnlyFocusBracketing"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFastBurst"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuousBurst"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuousBurstSlow"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashAutoFakeMode"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashOnFakeMode"));
        // do testTakePhotoRawRepeat last, and is an intensive test, and if it fails for any reason it seems to cause the following test to crash, terminating the run (at least on Nexus 6)!
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawRepeat"));
        return suite;
    }
}
