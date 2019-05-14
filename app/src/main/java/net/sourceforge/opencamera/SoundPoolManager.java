package net.sourceforge.opencamera;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

/** Manages loading and playing sounds, via SoundPool.
 */
class SoundPoolManager {
    private static final String TAG = "SoundPoolManager";

    private final Context context;

    private SoundPool sound_pool;
    private SparseIntArray sound_ids;

    SoundPoolManager(Context context) {
        this.context = context;
    }

    void initSound() {
        if( sound_pool == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "create new sound_pool");
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                AudioAttributes audio_attributes = new AudioAttributes.Builder()
                    .setLegacyStreamType(AudioManager.STREAM_SYSTEM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                sound_pool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audio_attributes)
                    .build();
            }
            else {
                sound_pool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
            }
            sound_ids = new SparseIntArray();
        }
    }

    void releaseSound() {
        if( sound_pool != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release sound_pool");
            sound_pool.release();
            sound_pool = null;
            sound_ids = null;
        }
    }

    /* Must be called before playSound (allowing enough time to load the sound).
     */
    void loadSound(int resource_id) {
        if( sound_pool != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "loading sound resource: " + resource_id);
            int sound_id = sound_pool.load(context, resource_id, 1);
            if( MyDebug.LOG )
                Log.d(TAG, "    loaded sound: " + sound_id);
            sound_ids.put(resource_id, sound_id);
        }
    }

    /* Must call loadSound first (allowing enough time to load the sound).
     */
    void playSound(int resource_id) {
        if( sound_pool != null ) {
            if( sound_ids.indexOfKey(resource_id) < 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "resource not loaded: " + resource_id);
            }
            else {
                int sound_id = sound_ids.get(resource_id);
                if( MyDebug.LOG )
                    Log.d(TAG, "play sound: " + sound_id);
                sound_pool.play(sound_id, 1.0f, 1.0f, 0, 0, 1);
            }
        }
    }
}
