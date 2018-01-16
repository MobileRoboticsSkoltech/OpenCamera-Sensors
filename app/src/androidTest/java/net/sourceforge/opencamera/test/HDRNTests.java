package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class HDRNTests {
	/** Tests for HDR algorithm with more than 3 images - only need to run on a single device
	 *  Should manually look over the images dumped onto DCIM/
	 *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
	 *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
	 *  time to transfer to the device everytime we run the tests.
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite(MainTests.class.getName());
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR1_exp5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR23_exp5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR45_exp5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR46_exp5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR47_exp5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR48_exp5"));
        return suite;
    }
}
