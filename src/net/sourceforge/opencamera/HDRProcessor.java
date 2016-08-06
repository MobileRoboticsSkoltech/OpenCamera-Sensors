package net.sourceforge.opencamera;

import java.util.List;

import android.graphics.Bitmap;
import android.util.Log;

public class HDRProcessor {
	private static final String TAG = "HDRProcessor";

	/** Converts a list of bitmaps into a HDR image, which is then tonemapped to a final RGB image.
	 * @param bitmaps The list of bitmaps, which should be in order of increasing brightness (exposure).
	 *                The resultant image is stored in the first bitmap.
	 */
	public void processHDR(List<Bitmap> bitmaps) {
		if( MyDebug.LOG )
			Log.d(TAG, "processHDR");
    	long time_s = System.currentTimeMillis();
		
		int n_bitmaps = bitmaps.size();
		Bitmap bm = bitmaps.get(0);
		int [] total_r = new int[bm.getWidth()*bm.getHeight()];
		int [] total_g = new int[bm.getWidth()*bm.getHeight()];
		int [] total_b = new int[bm.getWidth()*bm.getHeight()];
		for(int i=0;i<bm.getWidth()*bm.getHeight();i++) {
			total_r[i] = 0;
			total_g[i] = 0;
			total_b[i] = 0;
		}
		/* Initial test implementation - for now just doing an average, rather than HDR.
		 */
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
			Log.d(TAG, "time for processHDR: " + (System.currentTimeMillis() - time_s));
	}

}
