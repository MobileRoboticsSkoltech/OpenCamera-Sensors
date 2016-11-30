package net.sourceforge.opencamera.test;

import net.sourceforge.opencamera.LocationSupplier;

import org.junit.Test;

import static org.junit.Assert.*;

class Log {
	public static void d(String tag, String text) {
		System.out.println(tag + ": " + text);
	}
}

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
}
