package net.sourceforge.opencamera;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

/** Handles listening for GPS location (both coarse and fine).
 */
public class LocationSupplier {
    private static final String TAG = "LocationSupplier";

    private final Context context;
    private final LocationManager locationManager;
    private MyLocationListener [] locationListeners;
    private volatile boolean test_force_no_location; // if true, always return null location; must be volatile for test project setting the state

    private Location cached_location;
    private long cached_location_ms;

    LocationSupplier(Context context) {
        this.context = context;
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
    }

    private Location getCachedLocation() {
        if( cached_location != null ) {
            long time_ms = System.currentTimeMillis();
            if( time_ms <= cached_location_ms + 20000 ) {
                return cached_location;
            }
            else {
                cached_location = null;
            }
        }
        return null;
    }

    /** Cache the current best location. Note that we intentionally call getLocation() from this
     *  method rather than passing it a location from onLocationChanged(), as we don't want a
     *  coarse location overriding a better fine location.
     */
    private void cacheLocation() {
        if( MyDebug.LOG )
            Log.d(TAG, "cacheLocation");
        Location location = getLocation();
        if( location == null ) {
            // this isn't an error as it can happen that we receive a call to onLocationChanged() after
            // having freed the location listener (possibly because LocationManager had already queued
            // a call to onLocationChanged?
            // we should not set cached_location to null in such cases
            Log.d(TAG, "### asked to cache location when location not available");
        }
        else {
            cached_location = new Location(location);
            cached_location_ms = System.currentTimeMillis();
        }
    }

    public static class LocationInfo {
        private boolean location_was_cached;

        public boolean LocationWasCached() {
            return location_was_cached;
        }
    }

    public Location getLocation() {
        return getLocation(null);
    }

    /** If adding extra calls to this, consider whether explicit user permission is required, and whether
     *  privacy policy needs updating.
     * @param locationInfo Optional class to return additional information about the location.
     * @return Returns null if location not available.
     */
    public Location getLocation(LocationInfo locationInfo) {
        if( locationInfo != null )
            locationInfo.location_was_cached = false; // init

        if( locationListeners == null ) {
            // if we have disabled location listening, then don't return a cached location anyway -
            // in theory, callers should have already checked for user permission/setting before calling
            // getLocation(), but just in case we didn't, don't want to return a cached location
            return null;
        }
        if( test_force_no_location )
            return null;
        // location listeners should be stored in order best to worst
        for(MyLocationListener locationListener : locationListeners) {
            Location location = locationListener.getLocation();
            if( location != null )
                return location;
        }
        Location location = getCachedLocation();
        if( location != null && locationInfo != null )
            locationInfo.location_was_cached = true;
        return location;
    }

    private class MyLocationListener implements LocationListener {
        private Location location;
        volatile boolean test_has_received_location; // must be volatile for test project reading the state

        Location getLocation() {
            return location;
        }

        public void onLocationChanged(Location location) {
            if( MyDebug.LOG )
                Log.d(TAG, "onLocationChanged");
            this.test_has_received_location = true;
            // Android camera source claims we need to check lat/long != 0.0d
            // also check for not being null just in case - had a nullpointerexception on Google Play!
            if( location != null && ( location.getLatitude() != 0.0d || location.getLongitude() != 0.0d ) ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "received location:");
                    Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy());
                }
                this.location = location;
                cacheLocation();
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch( status ) {
                case LocationProvider.OUT_OF_SERVICE:
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                {
                    if( MyDebug.LOG ) {
                        if( status == LocationProvider.OUT_OF_SERVICE )
                            Log.d(TAG, "location provider out of service");
                        else if( status == LocationProvider.TEMPORARILY_UNAVAILABLE )
                            Log.d(TAG, "location provider temporarily unavailable");
                    }
                    this.location = null;
                    this.test_has_received_location = false;
                    cached_location = null;
                    break;
                }
                default:
                    break;
            }
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
            if( MyDebug.LOG )
                Log.d(TAG, "onProviderDisabled");
            this.location = null;
            this.test_has_received_location = false;
            cached_location = null;
        }
    }

    /* Best to only call this from MainActivity.initLocation().
     * @return Returns false if location permission not available for either coarse or fine.
     */
    boolean setupLocationListener() {
        if( MyDebug.LOG )
            Log.d(TAG, "setupLocationListener");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        // Define a listener that responds to location updates
        // we only set it up if store_location is true, important for privacy and unnecessary battery use
        boolean store_location = sharedPreferences.getBoolean(PreferenceKeys.LocationPreferenceKey, false);
        if( store_location && locationListeners == null ) {
            // Note, ContextCompat.checkSelfPermission is meant to handle being called on any Android version, i.e., pre
            // Android Marshmallow it should return true as permissions are set an installation, and can't be switched off by
            // the user. However on Galaxy Nexus Android 4.3 and Nexus 7 (2013) Android 5.1.1, ACCESS_COARSE_LOCATION returns
            // PERMISSION_DENIED! So we keep the checks to Android Marshmallow or later (where we need them), and avoid
            // checking behaviour for earlier devices.
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "check for location permissions");
                boolean has_coarse_location_permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean has_fine_location_permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "has_coarse_location_permission? " + has_coarse_location_permission);
                    Log.d(TAG, "has_fine_location_permission? " + has_fine_location_permission);
                }
                // require both permissions to be present
                if( !has_coarse_location_permission || !has_fine_location_permission ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "location permission not available");
                    // return false, which tells caller to request permission - we'll call this function again if permission is granted
                    return false;
                }
            }

            locationListeners = new MyLocationListener[2];
            locationListeners[0] = new MyLocationListener();
            locationListeners[1] = new MyLocationListener();

            // location listeners should be stored in order best to worst
            // also see https://sourceforge.net/p/opencamera/tickets/1/ - need to check provider is available
            // now also need to check for permissions - need to support devices that might have one but not both of fine and coarse permissions supplied
            if( locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) ) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListeners[1]);
                if( MyDebug.LOG )
                    Log.d(TAG, "created coarse (network) location listener");
            }
            else {
                if( MyDebug.LOG )
                    Log.e(TAG, "don't have a NETWORK_PROVIDER");
            }
            if( locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) ) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListeners[0]);
                if( MyDebug.LOG )
                    Log.d(TAG, "created fine (gps) location listener");
            }
            else {
                if( MyDebug.LOG )
                    Log.e(TAG, "don't have a GPS_PROVIDER");
            }
        }
        else if( !store_location ) {
            freeLocationListeners();
        }
        return true;
    }

    void freeLocationListeners() {
        if( MyDebug.LOG )
            Log.d(TAG, "freeLocationListeners");
        if( locationListeners != null ) {
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                // Android Lint claims we need location permission for LocationManager.removeUpdates().
                // also see http://stackoverflow.com/questions/32715189/location-manager-remove-updates-permission
                if( MyDebug.LOG )
                    Log.d(TAG, "check for location permissions");
                boolean has_coarse_location_permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean has_fine_location_permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "has_coarse_location_permission? " + has_coarse_location_permission);
                    Log.d(TAG, "has_fine_location_permission? " + has_fine_location_permission);
                }
                // require at least one permission to be present
                if( !has_coarse_location_permission && !has_fine_location_permission ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "location permission not available");
                    return;
                }
            }
            for(int i=0;i<locationListeners.length;i++) {
                locationManager.removeUpdates(locationListeners[i]);
                locationListeners[i] = null;
            }
            locationListeners = null;
            if( MyDebug.LOG )
                Log.d(TAG, "location listeners now freed");
        }
    }

    // for testing:

    public boolean testHasReceivedLocation() {
        if( locationListeners == null )
            return false;
        for(MyLocationListener locationListener : locationListeners) {
            if( locationListener.test_has_received_location )
                return true;
        }
        return false;
    }

    public void setForceNoLocation(boolean test_force_no_location) {
        this.test_force_no_location = test_force_no_location;
    }

    /** Use this when we want to test (assert) that location listeners are turned on.
     *  If we want to assert that they are turned off, then use noLocationListeners.
     */
    public boolean hasLocationListeners() {
        if( this.locationListeners == null )
            return false;
        if( this.locationListeners.length != 2 )
            return false;
        for(MyLocationListener locationListener : locationListeners) {
            if( locationListener == null )
                return false;
        }
        return true;
    }

    /** Use this when we want to test (assert) that location listeners are turned on. Note that this
     *  is NOT an inverse of hasLocationListeners. For example this means that if
     *  locationListeners.length==1, hasLocationListeners would return false (so we'd flag up that
     *  we've not set them up correctly), but noLocationListeners would also return false (to flag
     *  up that we did set some location listeners up).
     */
    public boolean noLocationListeners() {
        if( this.locationListeners == null )
            return true;
        return false;
    }

    public static String locationToDMS(double coord) {
        String sign = (coord < 0.0) ? "-" : "";
        coord = Math.abs(coord);
        int intPart = (int)coord;
        boolean is_zero = (intPart==0);
        String degrees = String.valueOf(intPart);
        double mod = coord - intPart;

        coord = mod * 60;
        intPart = (int)coord;
        is_zero = is_zero && (intPart==0);
        mod = coord - intPart;
        String minutes = String.valueOf(intPart);

        coord = mod * 60;
        intPart = (int)coord;
        is_zero = is_zero && (intPart==0);
        String seconds = String.valueOf(intPart);

        if( is_zero ) {
            // so we don't show -ve for coord that is -ve but smaller than 1"
            sign = "";
        }

        // use unicode rather than degrees symbol, due to Android Studio warning - see https://sourceforge.net/p/opencamera/tickets/107/
        return sign + degrees + "\u00b0" + minutes + "'" + seconds + "\"";
    }
}
