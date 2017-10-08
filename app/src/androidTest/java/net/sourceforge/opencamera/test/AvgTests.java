package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AvgTests {
	/** Tests for Avg algorithm - only need to run on a single device
	 *  Should manually look over the images dumped onto DCIM/
	 *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
	 *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
	 *  time to transfer to the device everytime we run the tests.
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite(MainTests.class.getName());
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg1"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg2"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg3"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg4"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg5"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg6"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg7"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg8"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg9"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg10"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg11"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg12"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg13"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg14"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg15"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg16"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg17"));
        return suite;
    }
}
