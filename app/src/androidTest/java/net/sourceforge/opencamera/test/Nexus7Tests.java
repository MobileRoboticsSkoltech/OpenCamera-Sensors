package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class Nexus7Tests {
	// Tests to run specifically on Nexus 7
	public static Test suite() {
		TestSuite suite = new TestSuite(MainTests.class.getName());
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchVideo"));
		suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusFlashAvailability"));
        return suite;
    }
}
