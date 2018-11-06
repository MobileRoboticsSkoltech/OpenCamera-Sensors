package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/** Handles the audio "noise" trigger option.
 */
public class MyAudioTriggerListenerCallback implements AudioListener.AudioListenerCallback {
    private static final String TAG = "MyAudioTriggerLstnrCb";

    private final MainActivity main_activity;

    private int last_level = -1;
    private long time_quiet_loud = -1;
    private long time_last_audio_trigger_photo = -1;
    private int audio_noise_sensitivity = -1;

    MyAudioTriggerListenerCallback(MainActivity main_activity) {
        this.main_activity = main_activity;
    }

    void setAudioNoiseSensitivity(int audio_noise_sensitivity) {
        this.audio_noise_sensitivity = audio_noise_sensitivity;
    }

    /** Listens to audio noise and decides when there's been a "loud" noise to trigger taking a photo.
     */
    @Override
    public void onAudio(int level) {
        boolean audio_trigger = false;
        /*if( level > 150 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "loud noise!: " + level);
            audio_trigger = true;
        }*/

        if( last_level == -1 ) {
            last_level = level;
            return;
        }
        int diff = level - last_level;

        if( MyDebug.LOG ) {
            Log.d(TAG, "noise_sensitivity: " + audio_noise_sensitivity);
            Log.d(TAG, "diff: " + diff);
        }

        if( diff > audio_noise_sensitivity ) {
            if( MyDebug.LOG )
                Log.d(TAG, "got louder!: " + last_level + " to " + level + " , diff: " + diff);
            time_quiet_loud = System.currentTimeMillis();
            if( MyDebug.LOG )
                Log.d(TAG, "    time: " + time_quiet_loud);
        }
        else if( diff < -audio_noise_sensitivity && time_quiet_loud != -1 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "got quieter!: " + last_level + " to " + level + " , diff: " + diff);
            long time_now = System.currentTimeMillis();
            long duration = time_now - time_quiet_loud;
            if( MyDebug.LOG ) {
                Log.d(TAG, "stopped being loud - was loud since: " + time_quiet_loud);
                Log.d(TAG, "    time_now: " + time_now);
                Log.d(TAG, "    duration: " + duration);
            }
            if( duration < 1500 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "audio_trigger set");
                audio_trigger = true;
            }
            time_quiet_loud = -1;
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "audio level: " + last_level + " to " + level + " , diff: " + diff);
        }

        last_level = level;

        if( audio_trigger ) {
            if( MyDebug.LOG )
                Log.d(TAG, "audio trigger");
            // need to run on UI thread so that this function returns quickly (otherwise we'll have lag in processing the audio)
            // but also need to check we're not currently taking a photo or on timer, so we don't repeatedly queue up takePicture() calls, or cancel a timer
            long time_now = System.currentTimeMillis();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            boolean want_audio_listener = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("noise");
            if( time_last_audio_trigger_photo != -1 && time_now - time_last_audio_trigger_photo < 5000 ) {
                // avoid risk of repeatedly being triggered - as well as problem of being triggered again by the camera's own "beep"!
                if( MyDebug.LOG )
                    Log.d(TAG, "ignore loud noise due to too soon since last audio triggered photo: " + (time_now - time_last_audio_trigger_photo));
            }
            else if( !want_audio_listener ) {
                // just in case this is a callback from an AudioListener before it's been freed (e.g., if there's a loud noise when exiting settings after turning the option off
                if( MyDebug.LOG )
                    Log.d(TAG, "ignore loud noise due to audio listener option turned off");
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "audio trigger from loud noise");
                time_last_audio_trigger_photo = time_now;
                main_activity.audioTrigger();
            }
        }
    }
}
