package net.sourceforge.opencamera;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class HDRProcessor {
	private static final String TAG = "HDRProcessor";

	enum HDRAlgorithm {
		HDRALGORITHM_AVERAGE,
		HDRALGORITHM_STANDARD
	};

	/** Given a set of data Xi and Yi, this function estimates a relation between X and Y
	 *  using linear least squares.
	 *  We use it to modify the pixels of images taken at the brighter or darker exposure
	 *  levels, to estimate what the pixel should be at the "base" exposure.
	 */
	private class ResponseFunction {
		double parameter = 0.0;

		/** Computes the response function.
		 * @param x_samples List of Xi samples. Must be at least 3 samples.
		 * @param y_samples List of Yi samples. Must be same length as x_samples.
		 */
		ResponseFunction(List<Double> x_samples, List<Double> y_samples) {
			if( MyDebug.LOG )
				Log.d(TAG, "ResponseFunction");

			if( x_samples.size() != y_samples.size() ) {
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
			
			double numer = 0.0;
			double denom = 0.0;
			for(int i=0;i<x_samples.size();i++) {
				double x = x_samples.get(i);
				double y = y_samples.get(i);
				numer += x*y;
				denom += x*x;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "numer = " + numer);
				Log.d(TAG, "denom = " + denom);
			}
			
			if( denom < 1.0e-5 ) {
				if( MyDebug.LOG )
					Log.e(TAG, "denom too small");
				parameter = 1.0;
			}
			else {
				parameter = numer / denom;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "parameter = " + parameter);
		}

		/** Evaluates the response function at parameter x.
		 */
		double value(double x) {
			return parameter * x;
		}
	}

	/** Converts a list of bitmaps into a HDR image, which is then tonemapped to a final RGB image.
	 * @param bitmaps The list of bitmaps, which should be in order of increasing brightness (exposure).
	 *                The resultant image is stored in the first bitmap.
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
	private ResponseFunction createFunctionFromBitmaps(Bitmap in_bitmap, Bitmap out_bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "createFunctionFromBitmaps");
		List<Double> x_samples = new ArrayList<Double>();
		List<Double> y_samples = new ArrayList<Double>();

		final int n_samples_c = 100;
		final int n_x_samples = (int)Math.sqrt(n_samples_c);
		final int n_y_samples = n_samples_c/n_x_samples;
		
		for(int y=0;y<n_y_samples;y++) {
			double alpha = ((double)y+1.0) / ((double)n_y_samples+1.0);
			int y_coord = (int)(alpha * in_bitmap.getHeight());
			for(int x=0;x<n_x_samples;x++) {
				double beta = ((double)x+1.0) / ((double)n_x_samples+1.0);
				int x_coord = (int)(beta * in_bitmap.getWidth());
				if( MyDebug.LOG )
					Log.d(TAG, "sample from " + x_coord + " , " + y_coord);
				int in_col = in_bitmap.getPixel(x_coord, y_coord);
				int out_col = out_bitmap.getPixel(x_coord, y_coord);
				double in_value = brightness(in_col);
				double out_value = brightness(out_col);
				x_samples.add(in_value);
				y_samples.add(out_value);
			}
		}
		
		ResponseFunction function = new ResponseFunction(x_samples, y_samples);
		return function;
	}

	/** Calculates the brightness for the supplied color.
	 */
	private double brightness(int color) {
		int r = color & 0xFF0000;
		int g = color & 0xFF00;
		int b = color & 0xFF;
		double value = (r + g + b)/3.0;
		return value;
	}
	
	/** Converts a HDR brightness to a 0-255 value.
	 */
	private int tonemap(double hdr) {
		// simple clamp:
		/*
		int rgb = (int)hdr;
		if( rgb > 255 )
			rgb = 255;
			*/
		/*
		// exponential:
		final double exposure_c = 1.2 / 255.0;
		int rgb = (int)(255.0*(1.0 - Math.exp(- hdr * exposure_c)));
		*/
		// Reinhard (Global):
		final double scale_c = 0.5*255.0;
		int rgb = (int)(255.0 * ( hdr / (scale_c + hdr) ));
		return rgb;
	}
	
	private double calculateWeight(double value) {
		// scale chosen so that 0 and 255 map to a non-zero weight of 1.0/127.5
		final double scale = (1.0-1.0/127.5)/127.5;
		double weight = 1.0 - scale * Math.abs( 127.5 - value );
		return weight;
	}

	/** Core implementation of HDR algorithm.
	 */
	private void processHDRCore(List<Bitmap> bitmaps) {
		if( MyDebug.LOG )
			Log.d(TAG, "processHDRCore");
		
    	long time_s = System.currentTimeMillis();
		
		int n_bitmaps = bitmaps.size();
		final int base_bitmap = 1; // index of the bitmap with the base exposure
		ResponseFunction [] response_functions = new ResponseFunction[n_bitmaps]; // ResponseFunction for each image (the ResponseFunction entry can be left null to indicate the Identity)
		
		// compute response_functions
		for(int i=0;i<n_bitmaps;i++) {
			ResponseFunction function = null;
			if( i != base_bitmap ) {
				function = createFunctionFromBitmaps(bitmaps.get(i), bitmaps.get(base_bitmap));
			}
			response_functions[i] = function;
		}

		// write new hdr image
		Bitmap bm = bitmaps.get(0);
		//int [] buffer = new int[bm.getWidth()];
		int [][] buffers = new int[n_bitmaps][];
		for(int i=0;i<n_bitmaps;i++) {
			buffers[i] = new int[bm.getWidth()];
		}
		for(int y=0;y<bm.getHeight();y++) {
			if( MyDebug.LOG ) {
				if( y % 100 == 0 )
					Log.d(TAG, "process: " + y + " / " + bm.getHeight());
			}
			// read out this row for each bitmap
			for(int i=0;i<n_bitmaps;i++) {
				bitmaps.get(i).getPixels(buffers[i], 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
			}
			for(int x=0;x<bm.getWidth();x++) {
				//int this_col = buffer[c];
				double hdr_r = 0.0, hdr_g = 0.0, hdr_b = 0.0;
				double sum_weight = 0.0;
				for(int i=0;i<n_bitmaps;i++) {
					int color = buffers[i][x];
					double r = (double)((color & 0xFF0000) >> 16);
					double g = (double)((color & 0xFF00) >> 8);
					double b = (double)(color & 0xFF);
					double weight = calculateWeight( (r+g+b) / 3.0 );
					//double weight = 1.0;
					if( MyDebug.LOG && x == 1547 && y == 1547 )
						Log.d(TAG, "" + x + "," + y + ":" + i + ":" + r + "," + g + "," + b + " weight: " + weight);
					if( response_functions[i] != null ) {
						r = response_functions[i].value(r);
						g = response_functions[i].value(g);
						b = response_functions[i].value(b);
					}
					hdr_r += weight * r;
					hdr_g += weight * g;
					hdr_b += weight * b;
					sum_weight += weight;
				}
				hdr_r /= sum_weight;
				hdr_g /= sum_weight;
				hdr_b /= sum_weight;
				int new_r = tonemap(hdr_r);
				int new_g = tonemap(hdr_g);
				int new_b = tonemap(hdr_b);
				/*{
					// check
					if( new_r < 0 || new_r > 255 )
						throw new RuntimeException();
					else if( new_g < 0 || new_g > 255 )
						throw new RuntimeException();
					else if( new_b < 0 || new_b > 255 )
						throw new RuntimeException();
				}*/
				if( MyDebug.LOG && x == 1547 && y == 1547 )
					Log.d(TAG, "" + x + "," + y + ":" + new_r + "," + new_g + "," + new_b);
				int new_col = (new_r << 16) | (new_g << 8) | new_b;
				//int new_col = Color.rgb(new_r, new_g, new_b);
				buffers[0][x] = new_col;
			}
			bm.setPixels(buffers[0], 0, bm.getWidth(), 0, y, bm.getWidth(), 1);
		}
		
		if( MyDebug.LOG )
			Log.d(TAG, "time for processHDRCore: " + (System.currentTimeMillis() - time_s));
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
	}
}
