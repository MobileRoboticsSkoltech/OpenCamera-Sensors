package net.sourceforge.opencamera;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/** Sets up a listener to listen for noise level.
 */
public class AudioListener {
	private static final String TAG = "AudioListener";
	private boolean is_running = true;
	private int buffer_size = -1;
	private AudioRecord ar = null;
	private Thread thread = null;

	public static interface AudioListenerCallback {
		public abstract void onAudio(int level);
	}

	/** Create a new AudioListener. The caller should call the start() method to start listening.
	 */
	public AudioListener(final AudioListenerCallback cb) {
		if( MyDebug.LOG )
			Log.d(TAG, "new AudioListener");
		final int sample_rate = 8000;
		int channel_config = AudioFormat.CHANNEL_IN_MONO;
		int audio_format = AudioFormat.ENCODING_PCM_16BIT;
		try {
			buffer_size = AudioRecord.getMinBufferSize(sample_rate, channel_config, audio_format);
			//buffer_size = -1; // test
			if( MyDebug.LOG )
				Log.d(TAG, "buffer_size: " + buffer_size);
			if( buffer_size <= 0 ) {
				if( MyDebug.LOG ) {
					if( buffer_size == AudioRecord.ERROR )
						Log.e(TAG, "getMinBufferSize returned ERROR");
					else if( buffer_size == AudioRecord.ERROR_BAD_VALUE )
						Log.e(TAG, "getMinBufferSize returned ERROR_BAD_VALUE");
				}
				return;
			}

			ar = new AudioRecord(MediaRecorder.AudioSource.MIC, sample_rate, channel_config, audio_format, buffer_size);
		}
		catch(Exception e) {
			e.printStackTrace();
			if( MyDebug.LOG )
				Log.e(TAG, "failed to create audiorecord");
			return;
		}

		final short[] buffer = new short[buffer_size];
		ar.startRecording();

		this.thread = new Thread() {
			@Override
			public void run() {
				/*int sample_delay = (1000 * buffer_size) / sample_rate;
				if( MyDebug.LOG )
					Log.e(TAG, "sample_delay: " + sample_delay);*/

				while( is_running ) {
					/*try{
						Thread.sleep(sample_delay);
					}
					catch(InterruptedException e) {
						e.printStackTrace();
					}*/
					try {
					    int n_read = ar.read(buffer, 0, buffer_size);
					    if( n_read > 0 ) {
						    int average_noise = 0;
						    int max_noise = 0;
						    for(int i=0;i<n_read;i++){
						    	int value = Math.abs(buffer[i]);
						    	average_noise += value;
						    	max_noise = Math.max(max_noise, value);
						    }
						    average_noise /= n_read;
							/*if( MyDebug.LOG ) {
								Log.d(TAG, "n_read: " + n_read);
								Log.d(TAG, "average noise: " + average_noise);
								Log.d(TAG, "max noise: " + max_noise);
							}*/
							cb.onAudio(average_noise);
					    }
					    else {
							if( MyDebug.LOG ) {
								Log.d(TAG, "n_read: " + n_read);
								if( n_read == AudioRecord.ERROR_INVALID_OPERATION )
									Log.e(TAG, "read returned ERROR_INVALID_OPERATION");
								else if( n_read == AudioRecord.ERROR_BAD_VALUE )
									Log.e(TAG, "read returned ERROR_BAD_VALUE");
							}
					    }
					}
					catch(Exception e) {
						e.printStackTrace();
						if( MyDebug.LOG )
							Log.e(TAG, "failed to read from audiorecord");
					}
				}
				ar.release();
				ar = null;
			}
		};
		// n.b., not good practice to start threads in constructors, so we require the caller to call start() instead
	}

	/** Start listening.
	 */
	void start() {
		if( MyDebug.LOG )
			Log.d(TAG, "start");
		if( thread != null ) {
			thread.start();
		}
	}
	
	/** Stop listening and release the resources.
	 */
	void release() {
		if( MyDebug.LOG )
			Log.d(TAG, "release");
		is_running = false;
		thread = null;
	}
	
	boolean hasAudioRecorder() {
		return ar != null;
	}
}
