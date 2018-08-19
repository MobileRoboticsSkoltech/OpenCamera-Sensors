package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class PhotoCamera2Tests {
	// Tests related to taking photos that require Camera2 - only need to run this suite with Camera2
	public static Test suite() {
		TestSuite suite = new TestSuite(MainTests.class.getName());
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualFocus"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualISOExposure"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoManualWB"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRaw"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawMulti"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawWaitCaptureResult"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRawOnly"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrashRaw"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrashRaw2"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExpo5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFocusBracketing"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFastBurst"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNR"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashAutoFakeMode"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashOnFakeMode"));
        return suite;
    }
}
