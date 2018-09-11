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
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg18"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg19"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg20"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg21"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg22"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg23"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg24"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg25"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg26"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg27"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg28"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg29"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg30"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg31"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg32"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg33"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg34"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg35"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg36"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg37"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg38"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg39"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg40"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg41"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg42"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg43"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg44"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg45"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg46"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg47"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg48"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testAvg49"));
        return suite;
    }
}
