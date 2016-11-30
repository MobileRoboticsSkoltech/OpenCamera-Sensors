package net.sourceforge.opencamera.test;

import net.sourceforge.opencamera.LocationSupplier;
import net.sourceforge.opencamera.Preview.Preview;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

class Log {
	public static void d(String tag, String text) {
		System.out.println(tag + ": " + text);
	}
}

/**
 * Note, need to run with MyDebug.LOG set to false, due to Android's Log.d not being mocked (good
 * practice to test release code anyway).
 */
public class UnitTest {
	private static final String TAG = "UnitTest";

	@Test
	public void testLocationToDMS() {
		Log.d(TAG, "testLocationToDMS");

		String location_string = LocationSupplier.locationToDMS(0.0);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'0\""));

		location_string = LocationSupplier.locationToDMS(0.0000306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'0\""));

		location_string = LocationSupplier.locationToDMS(0.000306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'1\""));

		location_string = LocationSupplier.locationToDMS(0.00306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'11\""));

		location_string = LocationSupplier.locationToDMS(0.9999);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°59'59\""));

		location_string = LocationSupplier.locationToDMS(1.7438);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("1°44'37\""));

		location_string = LocationSupplier.locationToDMS(53.000137);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("53°0'0\""));

		location_string = LocationSupplier.locationToDMS(147.00938);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("147°0'33\""));

		location_string = LocationSupplier.locationToDMS(-0.0);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'0\""));

		location_string = LocationSupplier.locationToDMS(-0.0000306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("0°0'0\""));

		location_string = LocationSupplier.locationToDMS(-0.000306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-0°0'1\""));

		location_string = LocationSupplier.locationToDMS(-0.00306);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-0°0'11\""));

		location_string = LocationSupplier.locationToDMS(-0.9999);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-0°59'59\""));

		location_string = LocationSupplier.locationToDMS(-1.7438);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-1°44'37\""));

		location_string = LocationSupplier.locationToDMS(-53.000137);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-53°0'0\""));

		location_string = LocationSupplier.locationToDMS(-147.00938);
		Log.d(TAG, "location_string: " + location_string);
		assertTrue(location_string.equals("-147°0'33\""));
	}

	@Test
	public void testBestPreviewFps() {
		Log.d(TAG, "testBestPreviewFps");

		List<int []> list0 = new ArrayList<>();
		list0.add(new int[]{15000, 15000});
		list0.add(new int[]{15000, 30000});
		list0.add(new int[]{7000, 30000});
		list0.add(new int[]{30000, 30000});
		int [] best_fps0 = Preview.chooseBestPreviewFps(list0);
		assertTrue(best_fps0[0] == 7000 && best_fps0[1] == 30000);

		List<int []> list1 = new ArrayList<>();
		list1.add(new int[]{15000, 15000});
		list1.add(new int[]{7000, 60000});
		list1.add(new int[]{15000, 30000});
		list1.add(new int[]{7000, 30000});
		list1.add(new int[]{30000, 30000});
		int [] best_fps1 = Preview.chooseBestPreviewFps(list1);
		assertTrue(best_fps1[0] == 7000 && best_fps1[1] == 60000);

		List<int []> list2 = new ArrayList<>();
		list2.add(new int[]{15000, 15000});
		list2.add(new int[]{7000, 15000});
		list2.add(new int[]{7000, 10000});
		list2.add(new int[]{8000, 19000});
		int [] best_fps2 = Preview.chooseBestPreviewFps(list2);
		assertTrue(best_fps2[0] == 8000 && best_fps2[1] == 19000);
	}

	@Test
	public void testMatchPreviewFpsToVideo() {
		Log.d(TAG, "matchPreviewFpsToVideo");

		List<int []> list0 = new ArrayList<>();
		list0.add(new int[]{15000, 15000});
		list0.add(new int[]{15000, 30000});
		list0.add(new int[]{7000, 30000});
		list0.add(new int[]{30000, 30000});
		int [] best_fps0 = Preview.matchPreviewFpsToVideo(list0, 30000);
		assertTrue(best_fps0[0] == 30000 && best_fps0[1] == 30000);

		List<int []> list1 = new ArrayList<>();
		list1.add(new int[]{15000, 15000});
		list1.add(new int[]{7000, 60000});
		list1.add(new int[]{15000, 30000});
		list1.add(new int[]{7000, 30000});
		list1.add(new int[]{30000, 30000});
		int [] best_fps1 = Preview.matchPreviewFpsToVideo(list1, 15000);
		assertTrue(best_fps1[0] == 15000 && best_fps1[1] == 15000);

		List<int []> list2 = new ArrayList<>();
		list2.add(new int[]{15000, 15000});
		list2.add(new int[]{7000, 15000});
		list2.add(new int[]{7000, 10000});
		list2.add(new int[]{8000, 19000});
		int [] best_fps2 = Preview.matchPreviewFpsToVideo(list2, 7000);
		assertTrue(best_fps2[0] == 7000 && best_fps2[1] == 10000);
	}
}
