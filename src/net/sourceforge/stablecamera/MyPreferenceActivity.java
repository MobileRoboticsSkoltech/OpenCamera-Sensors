package net.sourceforge.stablecamera;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class MyPreferenceActivity extends PreferenceActivity {
	private static final String TAG = "MyPreferenceActivity";

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences); // n.b., deprecated because we're not using a fragment...

		int cameraId = getIntent().getExtras().getInt("cameraId");
		if( MyDebug.LOG )
			Log.d(TAG, "cameraId: " + cameraId);
		
		int [] widths = getIntent().getExtras().getIntArray("resolution_widths");
		int [] heights = getIntent().getExtras().getIntArray("resolution_heights");
		if( widths != null && heights != null ) {
			CharSequence [] entries = new CharSequence[widths.length];
			CharSequence [] values = new CharSequence[widths.length];
			for(int i=0;i<widths.length;i++) {
				entries[i] = widths[i] + " x " + heights[i];
				values[i] = widths[i] + " " + heights[i];
			}
			ListPreference lp = (ListPreference)findPreference("preference_resolution");
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String resolution_preference_key = Preview.getResolutionPreferenceKey(cameraId);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
			if( MyDebug.LOG )
				Log.d(TAG, "resolution_value: " + resolution_value);
			lp.setValue(resolution_value);
			/*if( resolution_value.length() > 0 ) {
			}
			else {
				int current_resolution_index = getIntent().getExtras().getInt("current_resolution_index");
				if( MyDebug.LOG )
					Log.d(TAG, "set via intent bundle: current_resolution_index: " + current_resolution_index);
				lp.setValueIndex(current_resolution_index);
			}*/
			// now set the key, so we save for the correct cameraId
			lp.setKey(resolution_preference_key);
		}
		/*SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String resolution_value = sharedPreferences.getString("camera_resolution", "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		boolean found = false;
		if( resolution_value.length() > 0 ) {
			for(int i=0;i<values.length && !found;i++) {
				if( values[i].equals(resolution_value) ) {
					//lp.setDefaultValue(values[i]);
					lp.setValueIndex(i);
					found = true;
				}
			}
		}
		if( !found ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set default value for resolution");
			//lp.setDefaultValue(values[0]);
			int best_index = 0;
			int best_pixels = widths[0]*heights[0];
			for(int i=1;i<widths.length;i++) {
				int pixels = widths[i]*heights[i];
				if( pixels > best_pixels ) {
					best_index = i;
					best_pixels = pixels;
				}
			}
			lp.setValueIndex(best_index);
		}*/

		{
			final int n_quality = 100;
			CharSequence [] entries = new CharSequence[n_quality];
			CharSequence [] values = new CharSequence[n_quality];
			for(int i=0;i<n_quality;i++) {
				entries[i] = "" + (i+1) + "%";
				values[i] = "" + (i+1);
			}
			ListPreference lp = (ListPreference)findPreference("preference_quality");
			lp.setEntries(entries);
			lp.setEntryValues(values);
		}
	}
}
