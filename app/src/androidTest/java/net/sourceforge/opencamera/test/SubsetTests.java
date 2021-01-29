package net.sourceforge.opencamera.test;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.Test;
import junit.framework.TestSuite;

@SmallTest
public class SubsetTests {
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        // Basic video tests
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoImuInfo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoAllSensors"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoGyro"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoAccel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoMagnetometer"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));

        // TODO: update this test for new video rec stop logic, now it relies on synchronous recording stop
        // suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMacro"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoPause"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoPauseStop"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshot"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotTimer"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotPausePreview"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotMax"));
        // Basic photo tests
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuous"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuousNoTouch"));

        return suite;
    }
}

