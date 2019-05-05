package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class PanoramaTests {
    /** Tests for Panorama algorithm - only need to run on a single device
     *  Should manually look over the images dumped onto DCIM/
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device every time we run the tests.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama1"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama3"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama3_picsperscreen2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama4"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama5"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama6"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama7"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama8"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama9"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama10"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama11"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPanorama12"));
        return suite;
    }
}
