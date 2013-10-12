package net.sourceforge.stablecamera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
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
		
		boolean supports_auto_stabilise = getIntent().getExtras().getBoolean("supports_auto_stabilise");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

		if( !supports_auto_stabilise ) {
			Preference pref = (Preference)findPreference("preference_auto_stabilise");
        	PreferenceCategory pg = (PreferenceCategory)this.findPreference("preference_category_camera_effects");
        	pg.removePreference(pref);
		}

		readFromIntent("color_effects", "preference_color_effect", Camera.Parameters.EFFECT_NONE, "preference_category_camera_effects");
		readFromIntent("scene_modes", "preference_scene_mode", Camera.Parameters.SCENE_MODE_AUTO, "preference_category_camera_effects");
		readFromIntent("white_balances", "preference_white_balance", Camera.Parameters.WHITE_BALANCE_AUTO, "preference_category_camera_effects");

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
			// now set the key, so we save for the correct cameraId
			lp.setKey(resolution_preference_key);
		}

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

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
        	// Camera.enableShutterSound requires JELLY_BEAN_MR1 or greater
        	CheckBoxPreference cbp = (CheckBoxPreference)findPreference("preference_shutter_sound");
        	PreferenceCategory pg = (PreferenceCategory)this.findPreference("preference_category_camera_controls");
        	pg.removePreference(cbp);
        }

        {
            final Preference pref = (Preference) findPreference("preference_online_help");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_online_help") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked online help");
            	        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://stablecamera.sourceforge.net/"));
            	        startActivity(browserIntent);
                		return false;
                	}
                	return false;
                }
            });
        }

        {
            final Preference pref = (Preference) findPreference("preference_donate");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_donate") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked to donate");
            	        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.getDonateLink()));
            	        startActivity(browserIntent);
                		return false;
                	}
                	return false;
                }
            });
        }
	}
	
	private void readFromIntent(String intent_key, String preference_key, String default_value, String preference_category_key) {
		String [] values = getIntent().getExtras().getStringArray(intent_key);
		if( values != null && values.length > 0 ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, intent_key + " values:");
				for(int i=0;i<values.length;i++) {
					Log.d(TAG, values[i]);
				}
			}
			@SuppressWarnings("deprecation")
			ListPreference lp = (ListPreference)findPreference(preference_key);
			lp.setEntries(values);
			lp.setEntryValues(values);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String value = sharedPreferences.getString(preference_key, default_value);
			if( MyDebug.LOG )
				Log.d(TAG, "    value: " + values);
			lp.setValue(value);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "remove preference " + preference_key + " from category " + preference_category_key);
			@SuppressWarnings("deprecation")
			ListPreference lp = (ListPreference)findPreference(preference_key);
        	@SuppressWarnings("deprecation")
        	PreferenceCategory pg = (PreferenceCategory)this.findPreference(preference_category_key);
        	pg.removePreference(lp);
		}
	}
}
