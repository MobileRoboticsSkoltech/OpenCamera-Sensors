package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsicHistogram;
import android.util.Log;

public class HDRProcessor {
	private static final String TAG = "HDRProcessor";
	
	private Context context = null;
	private RenderScript rs = null; // lazily created, so we don't take up resources if application isn't using HDR

	private enum HDRAlgorithm {
		HDRALGORITHM_AVERAGE,
		HDRALGORITHM_STANDARD
	}
	
	HDRProcessor(Context context) {
		this.context = context;
	}

	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( rs != null ) {
			// need to destroy context, otherwise this isn't necessarily garbage collected - we had tests failing with out of memory
			// problems e.g. when running MainTests as a full set with Camera2 API. Although we now reduce the problem by creating
			// the rs lazily, it's still good to explicitly clear.
			rs.destroy(); // on Android M onwards this is a NOP - instead we call RenderScript.releaseAllContexts(); in MainActivity.onDestroy()
		}
	}

	/** Given a set of data Xi and Yi, this function estimates a relation between X and Y
	 *  using linear least squares.
	 *  We use it to modify the pixels of images taken at the brighter or darker exposure
	 *  levels, to estimate what the pixel should be at the "base" exposure.
	 */
	private static class ResponseFunction {
		float parameter_A = 0.0f;
		float parameter_B = 0.0f;

		/** Computes the response function.
		 * We pass the context, so this inner class can be made static.
		 * @param x_samples List of Xi samples. Must be at least 3 samples.
		 * @param y_samples List of Yi samples. Must be same length as x_samples.
		 * @param weights List of weights. Must be same length as x_samples.
		 */
		ResponseFunction(Context context, int id, List<Double> x_samples, List<Double> y_samples, List<Double> weights) {
			if( MyDebug.LOG )
				Log.d(TAG, "ResponseFunction");

			if( x_samples.size() != y_samples.size() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "unequal number of samples");
				// throw RuntimeException, as this is a programming error
				throw new RuntimeException();
			}
			else if( x_samples.size() != weights.size() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "unequal number of samples");
				// throw RuntimeException, as this is a programming error
				throw new RuntimeException();
			}
			else if( x_samples.size() <= 3 ) {
				if( MyDebug.LOG )
					Log.e(TAG, "not enough samples");
				// throw RuntimeException, as this is a programming error
				throw new RuntimeException();
			}

			// linear Y = AX + B
			boolean done = false;
			double sum_wx = 0.0;
			double sum_wx2 = 0.0;
			double sum_wxy = 0.0;
			double sum_wy = 0.0;
			double sum_w = 0.0;
			for(int i=0;i<x_samples.size();i++) {
				double x = x_samples.get(i);
				double y = y_samples.get(i);
				double w = weights.get(i);
				sum_wx += w * x;
				sum_wx2 += w * x * x;
				sum_wxy += w * x * y;
				sum_wy += w * y;
				sum_w += w;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "sum_wx = " + sum_wx);
				Log.d(TAG, "sum_wx2 = " + sum_wx2);
				Log.d(TAG, "sum_wxy = " + sum_wxy);
				Log.d(TAG, "sum_wy = " + sum_wy);
				Log.d(TAG, "sum_w = " + sum_w);
			}
			// need to solve:
			// A . sum_wx + B . sum_w - sum_wy = 0
			// A . sum_wx2 + B . sum_wx - sum_wxy = 0
			// =>
			// A . sum_wx^2 + B . sum_w . sum_wx - sum_wy . sum_wx = 0
			// A . sum_w . sum_wx2 + B . sum_w . sum_wx - sum_w . sum_wxy = 0
			// A ( sum_wx^2 - sum_w . sum_wx2 ) = sum_wy . sum_wx - sum_w . sum_wxy
			// then plug A into:
			// B . sum_w = sum_wy - A . sum_wx
			double A_numer = sum_wy * sum_wx - sum_w * sum_wxy;
			double A_denom = sum_wx * sum_wx - sum_w * sum_wx2;
			if( MyDebug.LOG ) {
				Log.d(TAG, "A_numer = " + A_numer);
				Log.d(TAG, "A_denom = " + A_denom);
			}
			if( Math.abs(A_denom) < 1.0e-5 ) {
				if( MyDebug.LOG )
					Log.e(TAG, "denom too small");
				// will fall back to linear Y = AX
			}
			else {
				parameter_A = (float)(A_numer / A_denom);
				parameter_B = (float)((sum_wy - parameter_A * sum_wx) / sum_w);
				if( MyDebug.LOG ) {
					Log.d(TAG, "parameter_A = " + parameter_A);
					Log.d(TAG, "parameter_B = " + parameter_B);
				}
				// we don't want a function that is not monotonic, or can be negative!
				if( parameter_A < 1.0e-5 ) {
					if( MyDebug.LOG )
						Log.e(TAG, "parameter A too small or negative: " + parameter_A);
				}
				else if( parameter_B < 1.0e-5 ) {
					if( MyDebug.LOG )
						Log.e(TAG, "parameter B too small or negative: " + parameter_B);
				}
				else {
					done = true;
				}
			}
			
			if( !done ) {
				if( MyDebug.LOG )
					Log.e(TAG, "falling back to linear Y = AX");
				// linear Y = AX
				double numer = 0.0;
				double denom = 0.0;
				for(int i=0;i<x_samples.size();i++) {
					double x = x_samples.get(i);
					double y = y_samples.get(i);
					double w = weights.get(i);
					numer += w*x*y;
					denom += w*x*x;
				}
				if( MyDebug.LOG ) {
					Log.d(TAG, "numer = " + numer);
					Log.d(TAG, "denom = " + denom);
				}
				
				if( denom < 1.0e-5 ) {
					if( MyDebug.LOG )
						Log.e(TAG, "denom too small");
					parameter_A = 1.0f;
				}
				else {
					parameter_A = (float)(numer / denom);
					// we don't want a function that is not monotonic!
					if( parameter_A < 1.0e-5 ) {
						if( MyDebug.LOG )
							Log.e(TAG, "parameter A too small or negative: " + parameter_A);
						parameter_A = 1.0e-5f;
					}
				}
				parameter_B = 0.0f;
			}

			if( MyDebug.LOG ) {
				Log.d(TAG, "parameter_A = " + parameter_A);
				Log.d(TAG, "parameter_B = " + parameter_B);
			}

			if( MyDebug.LOG ) {
				// log samples to a CSV file
				File file = new File(Environment.getExternalStorageDirectory().getPath() + "/net.sourceforge.opencamera.hdr_samples_" + id + ".csv");
				if( file.exists() ) {
					if( !file.delete() ) {
						// keep FindBugs happy by checking return argument
						Log.e(TAG, "failed to delete csv file");
					}
				}
				FileWriter writer = null;
				try {
					writer = new FileWriter(file);
					//writer.append("Parameter," + parameter + "\n");
					writer.append("Parameters," + parameter_A + "," + parameter_B + "\n");
					writer.append("X,Y,Weight\n");
					for(int i=0;i<x_samples.size();i++) {
						//Log.d(TAG, "log: " + i + " / " + x_samples.size());
						double x = x_samples.get(i);
						double y = y_samples.get(i);
						double w = weights.get(i);
						writer.append(x + "," + y + "," + w + "\n");
					}
				}
				catch (IOException e) {
					Log.e(TAG, "failed to open csv file");
					e.printStackTrace();
				}
				finally {
					try {
						if( writer != null )
							writer.close();
					}
					catch (IOException e) {
						Log.e(TAG, "failed to close csv file");
						e.printStackTrace();
					}
				}
	        	MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
			}
		}
	}

	/** Converts a list of bitmaps into a HDR image, which is then tonemapped to a final RGB image.
	 * @param bitmaps The list of bitmaps, which should be in order of increasing brightness (exposure).
	 *                The resultant image is stored in the first bitmap. The remainder bitmaps will have
	 *                recycle() called on them.
	 *                Currently only supports a list of 3 images, the 2nd should be at the desired exposure
	 *                level for the resultant image.
	 *                The bitmaps must all be the same resolution.
	 */
	public void processHDR(List<Bitmap> bitmaps) {
		if( MyDebug.LOG )
			Log.d(TAG, "processHDR");
		int n_bitmaps = bitmaps.size();
		if( n_bitmaps != 3 ) {
			if( MyDebug.LOG )
				Log.e(TAG, "n_bitmaps should be 3, not " + n_bitmaps);
			// throw RuntimeException, as this is a programming error
			throw new RuntimeException();
		}
		for(int i=1;i<n_bitmaps;i++) {
			if( bitmaps.get(i).getWidth() != bitmaps.get(0).getWidth() ||
				bitmaps.get(i).getHeight() != bitmaps.get(0).getHeight() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "bitmaps not of same resolution");
				throw new RuntimeException();
			}
		}
		
		//final HDRAlgorithm algorithm = HDRAlgorithm.HDRALGORITHM_AVERAGE;
		final HDRAlgorithm algorithm = HDRAlgorithm.HDRALGORITHM_STANDARD;
		
		switch( algorithm ) {
		case HDRALGORITHM_AVERAGE:
			processHDRAverage(bitmaps);
			break;
		case HDRALGORITHM_STANDARD:
			processHDRCore(bitmaps);
			break;
		default:
			if( MyDebug.LOG )
				Log.e(TAG, "unknown algorithm " + algorithm);
			// throw RuntimeException, as this is a programming error
			throw new RuntimeException();
		}
	}

	/** Creates a ResponseFunction to estimate how pixels from the in_bitmap should be adjusted to
	 *  match the exposure level of out_bitmap.
	 */
	private ResponseFunction createFunctionFromBitmaps(int id, Bitmap in_bitmap, Bitmap out_bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "createFunctionFromBitmaps");
		List<Double> x_samples = new ArrayList<>();
		List<Double> y_samples = new ArrayList<>();
		List<Double> weights = new ArrayList<>();

		final int n_samples_c = 100;
		final int n_w_samples = (int)Math.sqrt(n_samples_c);
		final int n_h_samples = n_samples_c/n_w_samples;

		double avg_in = 0.0;
		double avg_out = 0.0;
		for(int y=0;y<n_h_samples;y++) {
			double alpha = ((double)y+1.0) / ((double)n_h_samples+1.0);
			int y_coord = (int)(alpha * in_bitmap.getHeight());
			for(int x=0;x<n_w_samples;x++) {
				double beta = ((double)x+1.0) / ((double)n_w_samples+1.0);
				int x_coord = (int)(beta * in_bitmap.getWidth());
				/*if( MyDebug.LOG )
					Log.d(TAG, "sample response from " + x_coord + " , " + y_coord);*/
				int in_col = in_bitmap.getPixel(x_coord, y_coord);
				int out_col = out_bitmap.getPixel(x_coord, y_coord);
				double in_value = averageRGB(in_col);
				double out_value = averageRGB(out_col);
				avg_in += in_value;
				avg_out += out_value;
				x_samples.add(in_value);
				y_samples.add(out_value);
			}
		}
		avg_in /= x_samples.size();
		avg_out /= x_samples.size();
		boolean is_dark_exposure = avg_in < avg_out;
		if( MyDebug.LOG ) {
			Log.d(TAG, "avg_in: " + avg_in);
			Log.d(TAG, "avg_out: " + avg_out);
			Log.d(TAG, "is_dark_exposure: " + is_dark_exposure);
		}
		{
			// calculate weights
			double min_value = x_samples.get(0);
			double max_value = x_samples.get(0);
			for(int i=1;i<x_samples.size();i++) {
				double value = x_samples.get(i);
				if( value < min_value )
					min_value = value;
				if( value > max_value )
					max_value = value;
			}
			double med_value = 0.5*(min_value + max_value);
			if( MyDebug.LOG ) {
				Log.d(TAG, "min_value: " + min_value);
				Log.d(TAG, "max_value: " + max_value);
				Log.d(TAG, "med_value: " + med_value);
			}
			double min_value_y = y_samples.get(0);
			double max_value_y = y_samples.get(0);
			for(int i=1;i<y_samples.size();i++) {
				double value = y_samples.get(i);
				if( value < min_value_y )
					min_value_y = value;
				if( value > max_value_y )
					max_value_y = value;
			}
			double med_value_y = 0.5*(min_value_y + max_value_y);
			if( MyDebug.LOG ) {
				Log.d(TAG, "min_value_y: " + min_value_y);
				Log.d(TAG, "max_value_y: " + max_value_y);
				Log.d(TAG, "med_value_y: " + med_value_y);
			}
			for(int i=0;i<x_samples.size();i++) {
				double value = x_samples.get(i);
				double value_y = y_samples.get(i);
				if( is_dark_exposure ) {
					// for dark exposure, also need to worry about the y values (which will be brighter than x) being overexposed
					double weight = (value <= med_value) ? value - min_value : max_value - value;
					double weight_y = (value_y <= med_value_y) ? value_y - min_value_y : max_value_y - value_y;
					if( weight_y < weight )
						weight = weight_y;
					weights.add(weight);
				}
				else {
					double weight = (value <= med_value) ? value - min_value : max_value - value;
					weights.add(weight);
				}
			}
		}
		
		return new ResponseFunction(context, id, x_samples, y_samples, weights);
	}

	/** Calculates average of RGB values for the supplied color.
	 */
	private double averageRGB(int color) {
		int r = (color & 0xFF0000) >> 16;
		int g = (color & 0xFF00) >> 8;
		int b = (color & 0xFF);
		return (r + g + b)/3.0;
		//return 0.27*r + 0.67*g + 0.06*b;
	}
	
	/** Calculates the luminance for an RGB colour.
	 */
	/*private double calculateLuminance(double r, double g, double b) {
		double value = 0.27*r + 0.67*g + 0.06*b;
		return value;
	}*/
	
	/*final float A = 0.15f;
	final float B = 0.50f;
	final float C = 0.10f;
	final float D = 0.20f;
	final float E = 0.02f;
	final float F = 0.30f;
	final float W = 11.2f;
	
	float Uncharted2Tonemap(float x) {
		return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
	}*/

	/** Converts a HDR brightness to a 0-255 value.
	 * @param hdr The input HDR brightness.
	 * //@param l_avg The log average luminance of the HDR image. That is, exp( sum{log(Li)}/N ).
	 */
	private void tonemap(int [] rgb, float [] hdr/*, float l_avg*/) {
		// simple clamp:
		/*for(int i=0;i<3;i++) {
			rgb[i] = (int)hdr[i];
			if( rgb[i] > 255 )
				rgb[i] = 255;
		}*/
		/*
		// exponential:
		final double exposure_c = 1.2 / 255.0;
		int rgb = (int)(255.0*(1.0 - Math.exp(- hdr * exposure_c)));
		*/
		// Reinhard (Global):
		//final float scale_c = l_avg / 0.5f;
		//final float scale_c = l_avg / 0.8f; // lower values tend to result in too dark pictures; higher values risk over exposed bright areas
		//final float scale_c = l_avg / 1.0f;
		final float scale_c = 255.0f;
		//for(int i=0;i<3;i++)
		//	rgb[i] = (int)(255.0 * ( hdr[i] / (scale_c + hdr[i]) ));
		float max_hdr = hdr[0];
		if( hdr[1] > max_hdr )
			max_hdr = hdr[1];
		if( hdr[2] > max_hdr )
			max_hdr = hdr[2];
		float scale = 255.0f / ( scale_c + max_hdr );
		for(int i=0;i<3;i++) {
			//float ref_hdr = 0.5f * ( hdr[i] + max_hdr );
			//float scale = 255.0f / ( scale_c + ref_hdr );
			rgb[i] = (int)(scale * hdr[i]);
		}
		// Uncharted 2 Hable
		/*final float exposure_bias = 2.0f / 255.0f;
		final float white_scale = 255.0f / Uncharted2Tonemap(W);
		for(int i=0;i<3;i++) {
			float curr = Uncharted2Tonemap(exposure_bias * hdr[i]);
			rgb[i] = (int)(curr * white_scale);
		}*/
	}
	
	private class HDRWriterThread extends Thread {
		int y_start = 0, y_stop = 0;
		List<Bitmap> bitmaps;
		ResponseFunction [] response_functions;
		//float avg_luminance = 0.0f;

		int n_bitmaps = 0;
		Bitmap bm = null;
		int [][] buffers = null;
		
		HDRWriterThread(int y_start, int y_stop, List<Bitmap> bitmaps, ResponseFunction [] response_functions
			//, float avg_luminance
			) {
			if( MyDebug.LOG )
				Log.d(TAG, "thread " + this.getId() + " will process " + y_start + " to " + y_stop);
			this.y_start = y_start;
			this.y_stop = y_stop;
			this.bitmaps = bitmaps;
			this.response_functions = response_functions;
			//this.avg_luminance = avg_luminance;

			this.n_bitmaps = bitmaps.size();
			this.bm = bitmaps.get(0);
			this.buffers = new int[n_bitmaps][];
			for(int i=0;i<n_bitmaps;i++) {
				buffers[i] = new int[bm.getWidth()];
			}
		}
		
		public void run() {
			float [] hdr = new float[3];
			int [] rgb = new int[3];

			for(int y=y_start;y<y_stop;y++) {
				if( MyDebug.LOG ) {
					if( y % 100 == 0 )
						Log.d(TAG, "thread " + this.getId() + ": process: " + (y - y_start) + " / " + (y_stop - y_start));
				}
				// read out this row for each bitmap
				for(int i=0;i<n_bitmaps;i++) {
					bitmaps.get(i).getPixels(buffers[i], 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
				}
				for(int x=0;x<bm.getWidth();x++) {
					//int this_col = buffer[c];
					calculateHDR(hdr, n_bitmaps, buffers, x, response_functions);
					tonemap(rgb, hdr
							//, avg_luminance
					);
					int new_col = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
					buffers[0][x] = new_col;
				}
				bm.setPixels(buffers[0], 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
			}
		}
	}
	
	/** Core implementation of HDR algorithm.
	 *  Requires Android 4.4 (API level 19, Kitkat), due to using Renderscript without the support libraries.
	 *  And we now need Android 5.0 (API level 21, Lollipop) for forEach_Dot with LaunchOptions.
	 *  Using the support libraries (set via project.properties renderscript.support.mode) would bloat the APK
	 *  by around 1799KB! We don't care about pre-Android 4.4 (HDR requires CameraController2 which requires
	 *  Android 5.0 anyway; even if we later added support for CameraController1, we can simply say HDR requires
	 *  Android 5.0).
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void processHDRCore(List<Bitmap> bitmaps) {
		if( MyDebug.LOG )
			Log.d(TAG, "processHDRCore");
		
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
			if( MyDebug.LOG )
				Log.e(TAG, "HDR requires at least Android 4.4");
			// throw runtime exception as this is a programming error - HDR should not be offered as supported on older Android versions
			// (we require Android 5 for Camera2 API, to offer burst mode, anyway)
			throw new RuntimeException();
		}
    	long time_s = System.currentTimeMillis();
		
		int n_bitmaps = bitmaps.size();
		Bitmap bm = bitmaps.get(0);
		final int base_bitmap = 1; // index of the bitmap with the base exposure
		ResponseFunction [] response_functions = new ResponseFunction[n_bitmaps]; // ResponseFunction for each image (the ResponseFunction entry can be left null to indicate the Identity)
		/*int [][] buffers = new int[n_bitmaps][];
		for(int i=0;i<n_bitmaps;i++) {
			buffers[i] = new int[bm.getWidth()];
		}*/
		//float [] hdr = new float[3];
		//int [] rgb = new int[3];

		// compute response_functions
		for(int i=0;i<n_bitmaps;i++) {
			ResponseFunction function = null;
			if( i != base_bitmap ) {
				function = createFunctionFromBitmaps(i, bitmaps.get(i), bitmaps.get(base_bitmap));
			}
			response_functions[i] = function;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "time after creating response functions: " + (System.currentTimeMillis() - time_s));
		
		/*
		// calculate average luminance by sampling
		final int n_samples_c = 100;
		final int n_w_samples = (int)Math.sqrt(n_samples_c);
		final int n_h_samples = n_samples_c/n_w_samples;

		double sum_log_luminance = 0.0;
		int count = 0;
		for(int y=0;y<n_h_samples;y++) {
			double alpha = ((double)y+1.0) / ((double)n_h_samples+1.0);
			int y_coord = (int)(alpha * bm.getHeight());
			for(int i=0;i<n_bitmaps;i++) {
				bitmaps.get(i).getPixels(buffers[i], 0, bm.getWidth(), 0, y_coord, bm.getWidth(), 1);
			}
			for(int x=0;x<n_w_samples;x++) {
				double beta = ((double)x+1.0) / ((double)n_w_samples+1.0);
				int x_coord = (int)(beta * bm.getWidth());
				if( MyDebug.LOG )
					Log.d(TAG, "sample luminance from " + x_coord + " , " + y_coord);
				calculateHDR(hdr, n_bitmaps, buffers, x_coord, response_functions);
				double luminance = calculateLuminance(hdr[0], hdr[1], hdr[2]) + 1.0; // add 1 so we don't take log of 0..;
				sum_log_luminance += Math.log(luminance);
				count++;
			}
		}
		float avg_luminance = (float)(Math.exp( sum_log_luminance / count ));
		if( MyDebug.LOG )
			Log.d(TAG, "avg_luminance: " + avg_luminance);
		if( MyDebug.LOG )
			Log.d(TAG, "time after calculating average luminance: " + (System.currentTimeMillis() - time_s));
			*/

		//final boolean use_renderscript = false;
		final boolean use_renderscript = true;

		// write new hdr image
		if( use_renderscript ) {
			if( MyDebug.LOG )
				Log.d(TAG, "use renderscipt");
			if( rs == null ) {
				this.rs = RenderScript.create(context);
				if( MyDebug.LOG )
					Log.d(TAG, "create renderscript object");
				if( MyDebug.LOG )
					Log.d(TAG, "time after creating renderscript: " + (System.currentTimeMillis() - time_s));
			}
			// create allocations
	    	Allocation [] allocations = new Allocation[n_bitmaps];
			for(int i=0;i<n_bitmaps;i++) {
				allocations[i] = Allocation.createFromBitmap(rs, bitmaps.get(i));
			}
			
			// create RenderScript
			ScriptC_process_hdr processHDRScript = new ScriptC_process_hdr(rs);
			
			// set allocations
			processHDRScript.set_bitmap1(allocations[1]);
			processHDRScript.set_bitmap2(allocations[2]);
			
			// set response functions
			processHDRScript.set_parameter_A0( response_functions[0].parameter_A );
			processHDRScript.set_parameter_B0( response_functions[0].parameter_B );
			// no response function for middle image
			processHDRScript.set_parameter_A2( response_functions[2].parameter_A );
			processHDRScript.set_parameter_B2( response_functions[2].parameter_B );

			// set globals
			//final float tonemap_scale_c = avg_luminance / 0.8f; // lower values tend to result in too dark pictures; higher values risk over exposed bright areas
			final float tonemap_scale_c = 255.0f;
			// Higher tonemap_scale_c values means darker results from the Reinhard tonemapping.
			// Colours brighter than 255-tonemap_scale_c will be made darker, colours darker than 255-tonemap_scale_c will be made brighter
			// (tonemap_scale_c==255 means therefore that colours will only be made darker).
			if( MyDebug.LOG )
				Log.d(TAG, "tonemap_scale_c: " + tonemap_scale_c);
			processHDRScript.set_tonemap_scale(tonemap_scale_c);

			if( MyDebug.LOG )
				Log.d(TAG, "call processHDRScript");
			processHDRScript.forEach_hdr(allocations[0], allocations[0]);
			if( MyDebug.LOG )
				Log.d(TAG, "time after processHDRScript: " + (System.currentTimeMillis() - time_s));

			// bitmaps.get(0) now stores the HDR image, so free up the rest of the memory asap - we no longer need the remaining bitmaps
			for(int i=1;i<bitmaps.size();i++) {
				Bitmap bitmap = bitmaps.get(i);
				bitmap.recycle();
			}

			adjustHistogram(allocations[0], bm.getWidth(), bm.getHeight(), time_s);

			allocations[0].copyTo(bm);
			if( MyDebug.LOG )
				Log.d(TAG, "time after copying to bitmap: " + (System.currentTimeMillis() - time_s));
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "use java");
			final int n_threads = Runtime.getRuntime().availableProcessors();
			if( MyDebug.LOG )
				Log.d(TAG, "create n_threads: " + n_threads);
			// create threads
			HDRWriterThread [] threads = new HDRWriterThread[n_threads];
			for(int i=0;i<n_threads;i++) {
				int y_start = (i*bm.getHeight()) / n_threads;
				int y_stop = ((i+1)*bm.getHeight()) / n_threads;
				threads[i] = new HDRWriterThread(y_start, y_stop, bitmaps, response_functions/*, avg_luminance*/);
			}
			// start threads
			if( MyDebug.LOG )
				Log.d(TAG, "start threads");
			for(int i=0;i<n_threads;i++) {
				threads[i].start();
			}
			// wait for threads to complete
			if( MyDebug.LOG )
				Log.d(TAG, "wait for threads to complete");
			try {
				for(int i=0;i<n_threads;i++) {
					threads[i].join();
				}
			}
			catch(InterruptedException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "exception waiting for threads to complete");
				e.printStackTrace();
			}
			// bitmaps.get(0) now stores the HDR image, so free up the rest of the memory asap:
			for(int i=1;i<bitmaps.size();i++) {
				Bitmap bitmap = bitmaps.get(i);
				bitmap.recycle();
			}
		}

		if( MyDebug.LOG )
			Log.d(TAG, "time for processHDRCore: " + (System.currentTimeMillis() - time_s));
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void adjustHistogram(Allocation allocation, int width, int height, long time_s) {
		final boolean adjust_histogram = false;
		//final boolean adjust_histogram = true;

		if( adjust_histogram ) {
			// create histogram
			int [] histogram = new int[256];
			Allocation histogramAllocation = Allocation.createSized(rs, Element.I32(rs), 256);
			//final boolean use_custom_histogram = false;
			final boolean use_custom_histogram = true;
			if( MyDebug.LOG )
				Log.d(TAG, "time before creating histogram: " + (System.currentTimeMillis() - time_s));
			if( use_custom_histogram ) {
				if( MyDebug.LOG )
					Log.d(TAG, "create histogramScript");
				ScriptC_histogram_compute histogramScript = new ScriptC_histogram_compute(rs);
				if( MyDebug.LOG )
					Log.d(TAG, "bind histogram allocation");
				histogramScript.bind_histogram(histogramAllocation);
				if( MyDebug.LOG )
					Log.d(TAG, "call histogramScript");
				histogramScript.forEach_histogram_compute(allocation);
			}
			else {
				ScriptIntrinsicHistogram histogramScript = ScriptIntrinsicHistogram.create(rs, Element.U8_4(rs));
				histogramScript.setOutput(histogramAllocation);
				if( MyDebug.LOG )
					Log.d(TAG, "call histogramScript");
				histogramScript.forEach_Dot(allocation); // use forEach_dot(); using forEach would simply compute a histogram for red values!
			}
			if( MyDebug.LOG )
				Log.d(TAG, "time after creating histogram: " + (System.currentTimeMillis() - time_s));

			//histogramAllocation.setAutoPadding(true);
			histogramAllocation.copyTo(histogram);

				/*if( MyDebug.LOG ) {
					// compare/adjust
					allocations[0].copyTo(bm);
					int [] debug_histogram = new int[256];
					for(int i=0;i<256;i++) {
						debug_histogram[i] = 0;
					}
					int [] debug_buffer = new int[width];
					for(int y=0;y<height;y++) {
						bm.getPixels(debug_buffer, 0, width, 0, y, width, 1);
						for(int x=0;x<width;x++) {
							int color = debug_buffer[x];
							float r = (float)((color & 0xFF0000) >> 16);
							float g = (float)((color & 0xFF00) >> 8);
							float b = (float)(color & 0xFF);
							//float value = 0.299f*r + 0.587f*g + 0.114f*b; // matches ScriptIntrinsicHistogram default behaviour
							float value = Math.max(r, g);
							value = Math.max(value, b);
							int i_value = (int)value;
							i_value = Math.min(255, i_value); // just in case
							debug_histogram[i_value]++;
						}
					}
					for(int x=0;x<256;x++) {
						Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " debug_histogram: " + debug_histogram[x]);
						//histogram[x] = debug_histogram[x];
					}
				}*/

			int [] c_histogram = new int[256];
			c_histogram[0] = histogram[0];
			for(int x=1;x<256;x++) {
				c_histogram[x] = c_histogram[x-1] + histogram[x];
			}
				/*if( MyDebug.LOG ) {
					for(int x=0;x<256;x++) {
						Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " cumulative: " + c_histogram[x]);
					}
				}*/
			histogramAllocation.copyFrom(c_histogram);

			ScriptC_histogram_adjust histogramAdjustScript = new ScriptC_histogram_adjust(rs);
			histogramAdjustScript.set_c_histogram(histogramAllocation);

			if( MyDebug.LOG )
				Log.d(TAG, "call histogramAdjustScript");
			histogramAdjustScript.forEach_histogram_adjust(allocation, allocation);
			if( MyDebug.LOG )
				Log.d(TAG, "time after histogramAdjustScript: " + (System.currentTimeMillis() - time_s));
		}

		//final boolean adjust_histogram_local = false;
		final boolean adjust_histogram_local = true;

		if( adjust_histogram_local ) {
			// Contrast Limited Adaptive Histogram Equalisation
			// Note we don't fully equalise the histogram, rather the resultant image is the mid-point of the non-equalised and fully-equalised images
			// See https://en.wikipedia.org/wiki/Adaptive_histogram_equalization#Contrast_Limited_AHE
			// Also see "Adaptive Histogram Equalization and its Variations" ( http://www.cs.unc.edu/Research/MIDAG/pubs/papers/Adaptive%20Histogram%20Equalization%20and%20Its%20Variations.pdf ),
			// Pizer, Amburn, Austin, Cromartie, Geselowitz, Greer, ter Haar Romeny, Zimmerman, Zuiderveld (1987).

			// create histograms
			Allocation histogramAllocation = Allocation.createSized(rs, Element.I32(rs), 256);
			if( MyDebug.LOG )
				Log.d(TAG, "create histogramScript");
			ScriptC_histogram_compute histogramScript = new ScriptC_histogram_compute(rs);
			if( MyDebug.LOG )
				Log.d(TAG, "bind histogram allocation");
			histogramScript.bind_histogram(histogramAllocation);

			//final int n_tiles_c = 8;
			final int n_tiles_c = 4;
			//final int n_tiles_c = 1;
			int [] c_histogram = new int[n_tiles_c*n_tiles_c*256];
			for(int i=0;i<n_tiles_c;i++) {
				double a0 = ((double)i)/(double)n_tiles_c;
				double a1 = ((double)i+1.0)/(double)n_tiles_c;
				int start_x = (int)(a0 * width);
				int stop_x = (int)(a1 * width);
				if( stop_x == start_x )
					continue;
				for(int j=0;j<n_tiles_c;j++) {
					double b0 = ((double)j)/(double)n_tiles_c;
					double b1 = ((double)j+1.0)/(double)n_tiles_c;
					int start_y = (int)(b0 * height);
					int stop_y = (int)(b1 * height);
					if( stop_y == start_y )
						continue;
						/*if( MyDebug.LOG )
							Log.d(TAG, i + " , " + j + " : " + start_x + " , " + start_y + " to " + stop_x + " , " + stop_y);*/
					Script.LaunchOptions launch_options = new Script.LaunchOptions();
					launch_options.setX(start_x, stop_x);
					launch_options.setY(start_y, stop_y);

						/*if( MyDebug.LOG )
							Log.d(TAG, "call histogramScript");*/
					histogramScript.invoke_init_histogram();
					histogramScript.forEach_histogram_compute(allocation, launch_options);

					int [] histogram = new int[256];
					histogramAllocation.copyTo(histogram);

						/*if( MyDebug.LOG ) {
							// compare/adjust
							allocations[0].copyTo(bm);
							int [] debug_histogram = new int[256];
							for(int k=0;k<256;k++) {
								debug_histogram[k] = 0;
							}
							int [] debug_buffer = new int[width];
							for(int y=start_y;y<stop_y;y++) {
								bm.getPixels(debug_buffer, 0, width, 0, y, width, 1);
								for(int x=start_x;x<stop_x;x++) {
									int color = debug_buffer[x];
									float r = (float)((color & 0xFF0000) >> 16);
									float g = (float)((color & 0xFF00) >> 8);
									float b = (float)(color & 0xFF);
									//float value = 0.299f*r + 0.587f*g + 0.114f*b; // matches ScriptIntrinsicHistogram default behaviour
									float value = Math.max(r, g);
									value = Math.max(value, b);
									int i_value = (int)value;
									i_value = Math.min(255, i_value); // just in case
									debug_histogram[i_value]++;
								}
							}
							for(int x=0;x<256;x++) {
								Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " debug_histogram: " + debug_histogram[x]);
								//histogram[x] = debug_histogram[x];
							}
						}*/

					// clip histogram, for Contrast Limited AHE algorithm
					int n_pixels = (stop_x - start_x) * (stop_y - start_y);
					int clip_limit = (5 * n_pixels) / 256;
						/*if( MyDebug.LOG )
							Log.d(TAG, "clip_limit: " + clip_limit);*/
					{
						// find real clip limit
						int bottom = 0, top = clip_limit;
						while( top - bottom > 1 ) {
							int middle = (top + bottom)/2;
							int sum = 0;
							for(int x=0;x<256;x++) {
								if( histogram[x] > middle ) {
									sum += (histogram[x] - clip_limit);
								}
							}
							if( sum > (clip_limit - middle) * 256 )
								top = middle;
							else
								bottom = middle;
						}
						clip_limit = (top + bottom)/2;
							/*if( MyDebug.LOG )
								Log.d(TAG, "updated clip_limit: " + clip_limit);*/
					}
					int n_clipped = 0;
					for(int x=0;x<256;x++) {
						if( histogram[x] > clip_limit ) {
							n_clipped += (histogram[x] - clip_limit);
							histogram[x] = clip_limit;
						}
					}
					int n_clipped_per_bucket = n_clipped / 256;
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "n_clipped: " + n_clipped);
							Log.d(TAG, "n_clipped_per_bucket: " + n_clipped_per_bucket);
						}*/
					for(int x=0;x<256;x++) {
						histogram[x] += n_clipped_per_bucket;
					}

					int histogram_offset = 256*(i*n_tiles_c+j);
					c_histogram[histogram_offset] = histogram[0];
					for(int x=1;x<256;x++) {
						c_histogram[histogram_offset+x] = c_histogram[histogram_offset+x-1] + histogram[x];
					}
						/*if( MyDebug.LOG ) {
							for(int x=0;x<256;x++) {
								Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " cumulative: " + c_histogram[histogram_offset+x]);
							}
						}*/
				}
			}

			if( MyDebug.LOG )
				Log.d(TAG, "time after creating histograms: " + (System.currentTimeMillis() - time_s));

			Allocation c_histogramAllocation = Allocation.createSized(rs, Element.I32(rs), n_tiles_c*n_tiles_c*256);
			c_histogramAllocation.copyFrom(c_histogram);
			ScriptC_histogram_adjust histogramAdjustScript = new ScriptC_histogram_adjust(rs);
			histogramAdjustScript.set_c_histogram(c_histogramAllocation);
			histogramAdjustScript.set_n_tiles(n_tiles_c);
			histogramAdjustScript.set_width(width);
			histogramAdjustScript.set_height(height);

			if( MyDebug.LOG )
				Log.d(TAG, "call histogramAdjustScript");
			histogramAdjustScript.forEach_histogram_adjust(allocation, allocation);
			if( MyDebug.LOG )
				Log.d(TAG, "time after histogramAdjustScript: " + (System.currentTimeMillis() - time_s));
		}
	}
	
	private final static float weight_scale_c = (float)((1.0-1.0/127.5)/127.5);

	// If this algorithm is changed, also update the Renderscript version in process_hdr.rs
	private void calculateHDR(float [] hdr, int n_bitmaps, int [][] buffers, int x, ResponseFunction [] response_functions) {
		float hdr_r = 0.0f, hdr_g = 0.0f, hdr_b = 0.0f;
		float sum_weight = 0.0f;
		for(int i=0;i<n_bitmaps;i++) {
			int color = buffers[i][x];
			float r = (float)((color & 0xFF0000) >> 16);
			float g = (float)((color & 0xFF00) >> 8);
			float b = (float)(color & 0xFF);
			float avg = (r+g+b) / 3.0f;
			// weight_scale_c chosen so that 0 and 255 map to a non-zero weight of 1.0/127.5
			float weight = 1.0f - weight_scale_c * Math.abs( 127.5f - avg );
			//double weight = 1.0;
			/*if( MyDebug.LOG && x == 1547 && y == 1547 )
				Log.d(TAG, "" + x + "," + y + ":" + i + ":" + r + "," + g + "," + b + " weight: " + weight);*/
			if( response_functions[i] != null ) {
				// faster to access the parameters directly
				/*float parameter = response_functions[i].parameter;
				r *= parameter;
				g *= parameter;
				b *= parameter;*/
				float parameter_A = response_functions[i].parameter_A;
				float parameter_B = response_functions[i].parameter_B;
				r = parameter_A * r + parameter_B;
				g = parameter_A * g + parameter_B;
				b = parameter_A * b + parameter_B;
			}
			hdr_r += weight * r;
			hdr_g += weight * g;
			hdr_b += weight * b;
			sum_weight += weight;
		}
		hdr_r /= sum_weight;
		hdr_g /= sum_weight;
		hdr_b /= sum_weight;
		hdr[0] = hdr_r;
		hdr[1] = hdr_g;
		hdr[2] = hdr_b;
	}

	/* Initial test implementation - for now just doing an average, rather than HDR.
	 */
	private void processHDRAverage(List<Bitmap> bitmaps) {
		if( MyDebug.LOG )
			Log.d(TAG, "processHDRAverage");
    	long time_s = System.currentTimeMillis();
		
		Bitmap bm = bitmaps.get(0);
		int n_bitmaps = bitmaps.size();
		int [] total_r = new int[bm.getWidth()*bm.getHeight()];
		int [] total_g = new int[bm.getWidth()*bm.getHeight()];
		int [] total_b = new int[bm.getWidth()*bm.getHeight()];
		for(int i=0;i<bm.getWidth()*bm.getHeight();i++) {
			total_r[i] = 0;
			total_g[i] = 0;
			total_b[i] = 0;
		}
		//int [] buffer = new int[bm.getWidth()*bm.getHeight()];
		int [] buffer = new int[bm.getWidth()];
		for(int i=0;i<n_bitmaps;i++) {
			//bitmaps.get(i).getPixels(buffer, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
			for(int y=0,c=0;y<bm.getHeight();y++) {
				if( MyDebug.LOG ) {
					if( y % 100 == 0 )
						Log.d(TAG, "process " + i + ": " + y + " / " + bm.getHeight());
				}
				bitmaps.get(i).getPixels(buffer, 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
				for(int x=0;x<bm.getWidth();x++,c++) {
					//int this_col = buffer[c];
					int this_col = buffer[x];
					total_r[c] += this_col & 0xFF0000;
					total_g[c] += this_col & 0xFF00;
					total_b[c] += this_col & 0xFF;
				}
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "time before write: " + (System.currentTimeMillis() - time_s));
		// write:
		for(int y=0,c=0;y<bm.getHeight();y++) {
			if( MyDebug.LOG ) {
				if( y % 100 == 0 )
					Log.d(TAG, "write: " + y + " / " + bm.getHeight());
			}
			for(int x=0;x<bm.getWidth();x++,c++) {
				total_r[c] /= n_bitmaps;
				total_g[c] /= n_bitmaps;
				total_b[c] /= n_bitmaps;
				//int col = Color.rgb(total_r[c] >> 16, total_g[c] >> 8, total_b[c]);
				int col = (total_r[c] & 0xFF0000) | (total_g[c] & 0xFF00) | total_b[c];
				buffer[x] = col;
			}
			bm.setPixels(buffer, 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
		}

		if( MyDebug.LOG )
			Log.d(TAG, "time for processHDRAverage: " + (System.currentTimeMillis() - time_s));

		// bitmaps.get(0) now stores the HDR image, so free up the rest of the memory asap:
		for(int i=1;i<bitmaps.size();i++) {
			Bitmap bitmap = bitmaps.get(i);
			bitmap.recycle();
		}
	}
}
