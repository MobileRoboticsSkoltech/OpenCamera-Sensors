package net.sourceforge.opencamera.ui;

import android.util.Log;
import android.widget.SeekBar;

import net.sourceforge.opencamera.MyDebug;

import java.util.ArrayList;
import java.util.List;

/** This contains functionality related to the seekbars for manual controls.
 */
public class ManualSeekbars {
    private static final String TAG = "ManualSeekbars";

    private static final int manual_n = 1000; // the number of values on the seekbar used for manual focus distance

    public static double seekbarScaling(double frac) {
        // For various seekbars, we want to use a non-linear scaling, so user has more control over smaller values
        return (Math.pow(100.0, frac) - 1.0) / 99.0;
    }

    private static double seekbarScalingInverse(double scaling) {
        return Math.log(99.0*scaling + 1.0) / Math.log(100.0);
    }

    public static void setProgressSeekbarScaled(SeekBar seekBar, double min_value, double max_value, double value) {
        seekBar.setMax(manual_n);
        double scaling = (value - min_value)/(max_value - min_value);
        double frac = seekbarScalingInverse(scaling);
        int new_value = (int)(frac*manual_n + 0.5); // add 0.5 for rounding
        if( new_value < 0 )
            new_value = 0;
        else if( new_value > manual_n )
            new_value = manual_n;
        seekBar.setProgress(new_value);
    }

    /*public static long exponentialScaling(double frac, double min, double max) {
		// We use S(frac) = A * e^(s * frac)
		// We want S(0) = min, S(1) = max
		// So A = min
		// and Ae^s = max
		// => s = ln(max/min)
		double s = Math.log(max / min);
		return (long)(min * Math.exp(s * frac) + 0.5f); // add 0.5f so we round to nearest
	}

    private static double exponentialScalingInverse(double value, double min, double max) {
		double s = Math.log(max / min);
		return Math.log(value / min) / s;
	}

	public void setProgressSeekbarExponential(SeekBar seekBar, double min_value, double max_value, double value) {
		seekBar.setMax(manual_n);
		double frac = exponentialScalingInverse(value, min_value, max_value);
		int new_value = (int)(frac*manual_n + 0.5); // add 0.5 for rounding
		if( new_value < 0 )
			new_value = 0;
		else if( new_value > manual_n )
			new_value = manual_n;
		seekBar.setProgress(new_value);
	}*/

    private List<Long> seekbar_values_white_balance;
    private List<Long> seekbar_values_iso;
    private List<Long> seekbar_values_shutter_speed;

    public int getWhiteBalanceTemperature(int progress) {
        return seekbar_values_white_balance.get(progress).intValue();
    }

    public int getISO(int progress) {
        return seekbar_values_iso.get(progress).intValue();
    }

    public long getExposureTime(int progress) {
        return seekbar_values_shutter_speed.get(progress);
    }

    private void setProgressBarToClosest(SeekBar seekBar, List<Long> seekbar_values, long current_value) {
        if( MyDebug.LOG )
            Log.d(TAG, "setProgressBarToClosest");
        int closest_indx = -1;
        long min_dist = 0;
        for(int i=0;i<seekbar_values.size();i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "seekbar_values[" + i + "]: " + seekbar_values.get(i));
            long dist = Math.abs(seekbar_values.get(i) - current_value);
            if( MyDebug.LOG )
                Log.d(TAG, "    dist: " + dist);
            if( closest_indx == -1 || dist < min_dist ) {
                closest_indx = i;
                min_dist = dist;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "closest_indx: " + closest_indx);
        if( closest_indx != -1 )
            seekBar.setProgress(closest_indx);
    }

    void setISOProgressBarToClosest(SeekBar seekBar, long current_iso) {
        setProgressBarToClosest(seekBar, seekbar_values_iso, current_iso);
    }

    public void setProgressSeekbarWhiteBalance(SeekBar seekBar, long min_white_balance, long max_white_balance, long current_white_balance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setProgressSeekbarWhiteBalance");
        seekbar_values_white_balance = new ArrayList<>();
        List<Long> seekbar_values = seekbar_values_white_balance;

        // min to max, per 100
        for(long i=min_white_balance;i<max_white_balance;i+=100) {
            seekbar_values.add(i);
        }

        seekbar_values.add(max_white_balance);

        seekBar.setMax(seekbar_values.size()-1);

        setProgressBarToClosest(seekBar, seekbar_values, current_white_balance);
    }

    public void setProgressSeekbarISO(SeekBar seekBar, long min_iso, long max_iso, long current_iso) {
        if( MyDebug.LOG )
            Log.d(TAG, "setProgressSeekbarISO");
        seekbar_values_iso = new ArrayList<>();
        List<Long> seekbar_values = seekbar_values_iso;

        seekbar_values.add(min_iso);

        // 1 to 99, per 1
        for(long i=1;i<100;i++) {
            if( i > min_iso && i < max_iso )
                seekbar_values.add(i);
        }

        // 100 to 500, per 5
        for(long i=100;i<500;i+=5) {
            if( i > min_iso && i < max_iso )
                seekbar_values.add(i);
        }

        // 500 to 1000, per 10
        for(long i=500;i<1000;i+=10) {
            if( i > min_iso && i < max_iso )
                seekbar_values.add(i);
        }

        // 1000 to 5000, per 50
        for(long i=1000;i<5000;i+=50) {
            if( i > min_iso && i < max_iso )
                seekbar_values.add(i);
        }

        // 5000 to 10000, per 100
        for(long i=5000;i<10000;i+=100) {
            if( i > min_iso && i < max_iso )
                seekbar_values.add(i);
        }

        seekbar_values.add(max_iso);

        seekBar.setMax(seekbar_values.size()-1);

        setProgressBarToClosest(seekBar, seekbar_values, current_iso);
    }

    public void setProgressSeekbarShutterSpeed(SeekBar seekBar, long min_exposure_time, long max_exposure_time, long current_exposure_time) {
        if( MyDebug.LOG )
            Log.d(TAG, "setProgressSeekbarShutterSpeed");
        seekbar_values_shutter_speed = new ArrayList<>();
        List<Long> seekbar_values = seekbar_values_shutter_speed;

        seekbar_values.add(min_exposure_time);

        // 1/10,000 to 1/1,000
        for(int i=10;i>=1;i--) {
            long exposure = 1000000000L/(i*1000);
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 1/900 to 1/100
        for(int i=9;i>=1;i--) {
            long exposure = 1000000000L/(i*100);
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 1/90 to 1/60 (steps of 10)
        for(int i=9;i>=6;i--) {
            long exposure = 1000000000L/(i*10);
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 1/50 to 1/10 (steps of 5)
        for(int i=50;i>=10;i-=5) {
            long exposure = 1000000000L/i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 0.1 to 1.9, per 1.0s
        for(int i=1;i<20;i++) {
            long exposure = (1000000000L/10)*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 2 to 19, per 1s
        for(int i=2;i<20;i++) {
            long exposure = 1000000000L*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 20 to 60, per 5s
        for(int i=20;i<60;i+=5) {
            long exposure = 1000000000L*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // n.b., very long exposure times are not widely supported, but requested at https://sourceforge.net/p/opencamera/code/merge-requests/49/

        // 60 to 180, per 15s
        for(int i=60;i<180;i+=15) {
            long exposure = 1000000000L*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 180 to 600, per 60s
        for(int i=180;i<600;i+=60) {
            long exposure = 1000000000L*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        // 600 to 1200, per 120s
        for(int i=600;i<=1200;i+=120) {
            long exposure = 1000000000L*i;
            if( exposure > min_exposure_time && exposure < max_exposure_time )
                seekbar_values.add(exposure);
        }

        seekbar_values.add(max_exposure_time);

        seekBar.setMax(seekbar_values.size()-1);

        setProgressBarToClosest(seekBar, seekbar_values, current_exposure_time);
    }
}
