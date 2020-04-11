package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSInvalidStateException;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsicHistogram;
//import android.renderscript.ScriptIntrinsicResize;
import android.renderscript.Type;
import android.support.annotation.RequiresApi;
import android.util.Log;

public class HDRProcessor {
    private static final String TAG = "HDRProcessor";

    private final Context context;
    private final boolean is_test;
    private RenderScript rs; // lazily created, so we don't take up resources if application isn't using HDR

    // we lazily create and cache scripts that would otherwise have to be repeatedly created in a single
    // HDR or NR photo
    // these should be set to null in freeScript(), to help garbage collection
    /*private ScriptC_process_hdr processHDRScript;*/
    private ScriptC_process_avg processAvgScript;
    private ScriptC_create_mtb createMTBScript;
    private ScriptC_align_mtb alignMTBScript;
	/*private ScriptC_histogram_adjust histogramAdjustScript;
	private ScriptC_histogram_compute histogramScript;
	private ScriptC_avg_brighten avgBrightenScript;
	private ScriptC_calculate_sharpness sharpnessScript;*/

    // public for access by testing
    public int [] offsets_x = null;
    public int [] offsets_y = null;
    @SuppressWarnings("CanBeFinal")
    public int sharp_index = 0;

    private enum HDRAlgorithm {
        HDRALGORITHM_STANDARD,
        HDRALGORITHM_SINGLE_IMAGE
    }

    public enum TonemappingAlgorithm {
        TONEMAPALGORITHM_CLAMP,
        TONEMAPALGORITHM_EXPONENTIAL,
        TONEMAPALGORITHM_REINHARD,
        TONEMAPALGORITHM_FILMIC,
        TONEMAPALGORITHM_ACES
    }
    public enum DROTonemappingAlgorithm {
        DROALGORITHM_NONE,
        DROALGORITHM_GAINGAMMA
    }

    public HDRProcessor(Context context, boolean is_test) {
        this.context = context;
        this.is_test = is_test;
    }

    private void freeScripts() {
        if( MyDebug.LOG )
            Log.d(TAG, "freeScripts");
        /*processHDRScript = null;*/
        processAvgScript = null;
        createMTBScript = null;
        alignMTBScript = null;
		/*histogramAdjustScript = null;
		histogramScript = null;
		avgBrightenScript = null;
		sharpnessScript = null;*/
    }

    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");

        freeScripts(); // just in case

        if( rs != null ) {
            // need to destroy context, otherwise this isn't necessarily garbage collected - we had tests failing with out of memory
            // problems e.g. when running MainTests as a full set with Camera2 API. Although we now reduce the problem by creating
            // the rs lazily, it's still good to explicitly clear.
            try {
                rs.destroy(); // on Android M onwards this is a NOP - instead we call RenderScript.releaseAllContexts(); in MainActivity.onDestroy()
            }
            catch(RSInvalidStateException e) {
                e.printStackTrace();
            }
            rs = null;
        }
    }

    /** Given a set of data Xi and Yi, this function estimates a relation between X and Y
     *  using linear least squares.
     *  We use it to modify the pixels of images taken at the brighter or darker exposure
     *  levels, to estimate what the pixel should be at the "base" exposure.
     *  We estimate as y = parameter_A * x + parameter_B.
     */
    private static class ResponseFunction {
        float parameter_A;
        float parameter_B;

        private ResponseFunction(float parameter_A, float parameter_B) {
            this.parameter_A = parameter_A;
            this.parameter_B = parameter_B;
        }

        static ResponseFunction createIdentity() {
            return new ResponseFunction(1.0f, 0.0f);
        }

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
                    writer.append("Parameters,").append(String.valueOf(parameter_A)).append(",").append(String.valueOf(parameter_B)).append("\n");
                    writer.append("X,Y,Weight\n");
                    for(int i=0;i<x_samples.size();i++) {
                        //Log.d(TAG, "log: " + i + " / " + x_samples.size());
                        double x = x_samples.get(i);
                        double y = y_samples.get(i);
                        double w = weights.get(i);
                        writer.append(String.valueOf(x)).append(",").append(String.valueOf(y)).append(",").append(String.valueOf(w)).append("\n");
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

    @SuppressWarnings("WeakerAccess")
    public interface SortCallback {
        /** This is called when the sort order for the input bitmaps is known, from darkest to brightest.
         * @param sort_order A list of length equal to the supplied bitmaps.size(). sort_order.get(i)
         *                   returns the index in the bitmaps array of the i-th image after sorting,
         *                   where i==0 represents the darkest image, and i==bitmaps.size()-1 is the
         *                   brightest.
         */
        void sortOrder(List<Integer> sort_order);
    }

    /** Converts a list of bitmaps into a HDR image, which is then tonemapped to a final RGB image.
     * @param bitmaps The list of bitmaps, which should be in order of increasing brightness (exposure).
     *                Currently only supports a list of either 1 image, or 3 images (the 2nd should be
     *                at the desired exposure level for the resultant image).
     *                The bitmaps must all be the same resolution.
     * @param release_bitmaps If true, the resultant image will be stored in one of the input bitmaps.
     *                        The bitmaps array will be updated so that the first entry will contain
     *                        the output bitmap. If assume_sorted is true, this will be equal to the
     *                        input bitmaps.get( (bitmaps.size()-1) / 2). The remainder bitmaps will have
     *                        recycle() called on them.
     *                        If false, the resultant image is copied to output_bitmap.
     * @param output_bitmap If release_bitmaps is false, the resultant image is stored in this bitmap.
     *                      If release_bitmaps is true, this parameter is ignored.
     * @param assume_sorted If true, the input bitmaps should be sorted in order from darkest to brightest
     *                      exposure. If false, the function will automatically resort.
     * @param sort_cb       If assume_sorted is false and this is non-null, sort_cb.sortOrder() will be
     *                      called to indicate the sort order when this is known.
     * @param hdr_alpha     A value from 0.0f to 1.0f indicating the "strength" of the HDR effect. Specifically,
     *                      this controls the level of the local contrast enhancement done in adjustHistogram().
     * @param n_tiles       A value of 1 or greater indicating how local the contrast enhancement algorithm should be.
     * @param ce_preserve_blacks
     * 						If true (recommended), then we apply a modification to the contrast enhancement algorithm to avoid
     *                      making darker pixels too dark. A value of false gives more contrast on the darker regions of the
     *                      resultant image.
     * @param tonemapping_algorithm
     *                      Algorithm to use for tonemapping (if multiple images are received).
     * @param dro_tonemapping_algorithm
     *                      Algorithm to use for tonemapping (if single image is received).
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void processHDR(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, TonemappingAlgorithm tonemapping_algorithm, DROTonemappingAlgorithm dro_tonemapping_algorithm) throws HDRProcessorException {
        if( MyDebug.LOG )
            Log.d(TAG, "processHDR");
        if( !assume_sorted && !release_bitmaps ) {
            if( MyDebug.LOG )
                Log.d(TAG, "take a copy of bitmaps array");
            // if !release_bitmaps, then we shouldn't be modifying the input bitmaps array - but if !assume_sorted, we need to sort them
            // so make sure we take a copy
            bitmaps = new ArrayList<>(bitmaps);
        }
        int n_bitmaps = bitmaps.size();
        //if( n_bitmaps != 1 && n_bitmaps != 3 && n_bitmaps != 5 && n_bitmaps != 7 ) {
        if( n_bitmaps < 1 || n_bitmaps > 7 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "n_bitmaps not supported: " + n_bitmaps);
            throw new HDRProcessorException(HDRProcessorException.INVALID_N_IMAGES);
        }
        for(int i=1;i<n_bitmaps;i++) {
            if( bitmaps.get(i).getWidth() != bitmaps.get(0).getWidth() ||
                    bitmaps.get(i).getHeight() != bitmaps.get(0).getHeight() ) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "bitmaps not of same resolution");
                    for(int j=0;j<n_bitmaps;j++) {
                        Log.e(TAG, "bitmaps " + j + " : " + bitmaps.get(j).getWidth() + " x " + bitmaps.get(j).getHeight());
                    }
                }
                throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
            }
        }

        final HDRAlgorithm algorithm = n_bitmaps == 1 ? HDRAlgorithm.HDRALGORITHM_SINGLE_IMAGE : HDRAlgorithm.HDRALGORITHM_STANDARD;

        switch( algorithm ) {
            case HDRALGORITHM_SINGLE_IMAGE:
                if( !assume_sorted && sort_cb != null ) {
                    List<Integer> sort_order = new ArrayList<>();
                    sort_order.add(0);
                    sort_cb.sortOrder(sort_order);
                }
                processSingleImage(bitmaps, release_bitmaps, output_bitmap, hdr_alpha, n_tiles, ce_preserve_blacks, dro_tonemapping_algorithm);
                break;
            case HDRALGORITHM_STANDARD:
                processHDRCore(bitmaps, release_bitmaps, output_bitmap, assume_sorted, sort_cb, hdr_alpha, n_tiles, ce_preserve_blacks, tonemapping_algorithm);
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
     *  The supplied offsets offset_x, offset_y give the offset for in_bitmap as computed by
     *  autoAlignment().
     */
    private ResponseFunction createFunctionFromBitmaps(int id, Bitmap in_bitmap, Bitmap out_bitmap, int offset_x, int offset_y) {
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
                if( x_coord + offset_x < 0 || x_coord + offset_x >= in_bitmap.getWidth() || y_coord + offset_y < 0 || y_coord + offset_y >= in_bitmap.getHeight() ) {
                    continue;
                }
                int in_col = in_bitmap.getPixel(x_coord + offset_x, y_coord + offset_y);
                int out_col = out_bitmap.getPixel(x_coord, y_coord);
                double in_value = averageRGB(in_col);
                double out_value = averageRGB(out_col);
                avg_in += in_value;
                avg_out += out_value;
                x_samples.add(in_value);
                y_samples.add(out_value);
            }
        }
        if( x_samples.size() == 0 ) {
            Log.e(TAG, "no samples for response function!");
            // shouldn't happen, but could do with a very large offset - just make up a dummy sample
            double in_value = 255.0;
            double out_value = 255.0;
            avg_in += in_value;
            avg_out += out_value;
            x_samples.add(in_value);
            y_samples.add(out_value);
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

    /** Core implementation of HDR algorithm.
     *  Requires Android 4.4 (API level 19, Kitkat), due to using Renderscript without the support libraries.
     *  And we now need Android 5.0 (API level 21, Lollipop) for forEach_Dot with LaunchOptions.
     *  Using the support libraries (set via project.properties renderscript.support.mode) would bloat the APK
     *  by around 1799KB! We don't care about pre-Android 4.4 (HDR requires CameraController2 which requires
     *  Android 5.0 anyway; even if we later added support for CameraController1, we can simply say HDR requires
     *  Android 5.0).
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void processHDRCore(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, TonemappingAlgorithm tonemapping_algorithm) {
        if( MyDebug.LOG )
            Log.d(TAG, "processHDRCore");

        long time_s = System.currentTimeMillis();

        int n_bitmaps = bitmaps.size();
        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();
        ResponseFunction [] response_functions = new ResponseFunction[n_bitmaps]; // ResponseFunction for each image (the ResponseFunction entry can be left null to indicate the Identity)
        offsets_x = new int[n_bitmaps];
        offsets_y = new int[n_bitmaps];
		/*int [][] buffers = new int[n_bitmaps][];
		for(int i=0;i<n_bitmaps;i++) {
			buffers[i] = new int[bm.getWidth()];
		}*/
        //float [] hdr = new float[3];
        //int [] rgb = new int[3];

        initRenderscript();
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating renderscript: " + (System.currentTimeMillis() - time_s));
        // create allocations
        Allocation [] allocations = new Allocation[n_bitmaps];
        for(int i=0;i<n_bitmaps;i++) {
            allocations[i] = Allocation.createFromBitmap(rs, bitmaps.get(i));
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating allocations from bitmaps: " + (System.currentTimeMillis() - time_s));
        //final int base_bitmap = (n_bitmaps - 1) / 2; // index of the bitmap with the base exposure and offsets
        final int base_bitmap = n_bitmaps % 2 == 0 ? n_bitmaps/2 : (n_bitmaps - 1) / 2; // index of the bitmap with the base exposure and offsets
        // for even number of images, round up to brighter image

        // perform auto-alignment
        // if assume_sorted if false, this function will also sort the allocations and bitmaps from darkest to brightest.
        BrightnessDetails brightnessDetails = autoAlignment(offsets_x, offsets_y, allocations, width, height, bitmaps, base_bitmap, assume_sorted, sort_cb, true, false, 1, true, 1, width, height, time_s);
        int median_brightness = brightnessDetails.median_brightness;
        if( MyDebug.LOG ) {
            Log.d(TAG, "### time after autoAlignment: " + (System.currentTimeMillis() - time_s));
            Log.d(TAG, "median_brightness: " + median_brightness);
        }

        //final boolean use_hdr_n = true; // test always using hdr_n
        final boolean use_hdr_n = n_bitmaps != 3;

        // compute response_functions
        for(int i=0;i<n_bitmaps;i++) {
            ResponseFunction function = null;
            if( i != base_bitmap ) {
                function = createFunctionFromBitmaps(i, bitmaps.get(i), bitmaps.get(base_bitmap), offsets_x[i], offsets_y[i]);
            }
            else if( use_hdr_n ) {
                // for hdr_n, need to still create the identity response function
                function = ResponseFunction.createIdentity();
            }
            response_functions[i] = function;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating response functions: " + (System.currentTimeMillis() - time_s));

        if( n_bitmaps % 2 == 0 ) {
            // need to remap so that we aim for a brightness between the middle two images
            float a = (float)Math.sqrt(response_functions[base_bitmap-1].parameter_A);
            float b = response_functions[base_bitmap-1].parameter_B / (a+1.0f);
            if( MyDebug.LOG ) {
                Log.d(TAG, "remap for even number of images");
                Log.d(TAG, "    a: " + a);
                Log.d(TAG, "    b: " + b);
            }
            if( a < 1.0e-5f ) {
                // avoid risk of division by 0
                a = 1.0e-5f;
                if( MyDebug.LOG )
                    Log.e(TAG, "    clamp a to: " + a);
            }
            for(int i=0;i<n_bitmaps;i++) {
                float this_A = response_functions[i].parameter_A;
                float this_B = response_functions[i].parameter_B;
                response_functions[i].parameter_A = this_A / a;
                response_functions[i].parameter_B = this_B - this_A * b / a;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "remapped: " + i);
                    Log.d(TAG, "    A: " + this_A + " -> " + response_functions[i].parameter_A);
                    Log.d(TAG, "    B: " + this_B + " -> " + response_functions[i].parameter_B);
                }
            }
        }

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

        // write new hdr image

        // create RenderScript
		/*if( processHDRScript == null ) {
			processHDRScript = new ScriptC_process_hdr(rs);
		}*/
        ScriptC_process_hdr processHDRScript = new ScriptC_process_hdr(rs);

        // set allocations
        processHDRScript.set_bitmap0(allocations[0]);
        if( n_bitmaps > 2 ) {
            processHDRScript.set_bitmap2(allocations[2]);
        }

        // set offsets
        processHDRScript.set_offset_x0(offsets_x[0]);
        processHDRScript.set_offset_y0(offsets_y[0]);
        // no offset for middle image
        if( n_bitmaps > 2 ) {
            processHDRScript.set_offset_x2(offsets_x[2]);
            processHDRScript.set_offset_y2(offsets_y[2]);
        }

        // set response functions
        processHDRScript.set_parameter_A0(response_functions[0].parameter_A);
        processHDRScript.set_parameter_B0(response_functions[0].parameter_B);
        // no response function for middle image
        if( n_bitmaps > 2 ) {
            processHDRScript.set_parameter_A2(response_functions[2].parameter_A);
            processHDRScript.set_parameter_B2(response_functions[2].parameter_B);
        }

        if( use_hdr_n ) {
            // now need to set values for image 1
            processHDRScript.set_bitmap1(allocations[1]);
            processHDRScript.set_offset_x1(offsets_x[1]);
            processHDRScript.set_offset_y1(offsets_y[1]);
            processHDRScript.set_parameter_A1(response_functions[1].parameter_A);
            processHDRScript.set_parameter_B1(response_functions[1].parameter_B);
        }

        if( n_bitmaps > 3 ) {
            processHDRScript.set_bitmap3(allocations[3]);
            processHDRScript.set_offset_x3(offsets_x[3]);
            processHDRScript.set_offset_y3(offsets_y[3]);
            processHDRScript.set_parameter_A3(response_functions[3].parameter_A);
            processHDRScript.set_parameter_B3(response_functions[3].parameter_B);

            if( n_bitmaps > 4 ) {
                processHDRScript.set_bitmap4(allocations[4]);
                processHDRScript.set_offset_x4(offsets_x[4]);
                processHDRScript.set_offset_y4(offsets_y[4]);
                processHDRScript.set_parameter_A4(response_functions[4].parameter_A);
                processHDRScript.set_parameter_B4(response_functions[4].parameter_B);

                if( n_bitmaps > 5 ) {
                    processHDRScript.set_bitmap5(allocations[5]);
                    processHDRScript.set_offset_x5(offsets_x[5]);
                    processHDRScript.set_offset_y5(offsets_y[5]);
                    processHDRScript.set_parameter_A5(response_functions[5].parameter_A);
                    processHDRScript.set_parameter_B5(response_functions[5].parameter_B);

                    if( n_bitmaps > 6 ) {
                        processHDRScript.set_bitmap6(allocations[6]);
                        processHDRScript.set_offset_x6(offsets_x[6]);
                        processHDRScript.set_offset_y6(offsets_y[6]);
                        processHDRScript.set_parameter_A6(response_functions[6].parameter_A);
                        processHDRScript.set_parameter_B6(response_functions[6].parameter_B);
                    }
                }
            }
        }

        // set globals

        // set tonemapping algorithm
        switch( tonemapping_algorithm ) {
            case TONEMAPALGORITHM_CLAMP:
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemapping algorithm: clamp");
                processHDRScript.set_tonemap_algorithm( processHDRScript.get_tonemap_algorithm_clamp_c() );
                break;
            case TONEMAPALGORITHM_EXPONENTIAL:
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemapping algorithm: exponential");
                processHDRScript.set_tonemap_algorithm( processHDRScript.get_tonemap_algorithm_exponential_c() );
                break;
            case TONEMAPALGORITHM_REINHARD:
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemapping algorithm: reinhard");
                processHDRScript.set_tonemap_algorithm( processHDRScript.get_tonemap_algorithm_reinhard_c() );
                break;
            case TONEMAPALGORITHM_FILMIC:
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemapping algorithm: filmic");
                processHDRScript.set_tonemap_algorithm( processHDRScript.get_tonemap_algorithm_filmic_c() );
                break;
            case TONEMAPALGORITHM_ACES:
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemapping algorithm: aces");
                processHDRScript.set_tonemap_algorithm( processHDRScript.get_tonemap_algorithm_aces_c() );
                break;
        }

        float max_possible_value = response_functions[0].parameter_A * 255 + response_functions[0].parameter_B;
        //float max_possible_value = response_functions[base_bitmap - 1].parameter_A * 255 + response_functions[base_bitmap - 1].parameter_B;
        if( MyDebug.LOG )
            Log.d(TAG, "max_possible_value: " + max_possible_value);
        if( max_possible_value < 255.0f ) {
            max_possible_value = 255.0f; // don't make dark images too bright, see below about linear_scale for more details
            if( MyDebug.LOG )
                Log.d(TAG, "clamp max_possible_value to: " + max_possible_value);
        }

        //hdr_alpha = 0.0f; // test
        //final float tonemap_scale_c = avg_luminance / 0.8f; // lower values tend to result in too dark pictures; higher values risk over exposed bright areas
        //final float tonemap_scale_c = 255.0f;
        //final float tonemap_scale_c = 255.0f - median_brightness;
        float tonemap_scale_c = 255.0f;

        int median_target = getBrightnessTarget(median_brightness, 2, 119);

        if( MyDebug.LOG ) {
            Log.d(TAG, "median_target: " + median_target);
            Log.d(TAG, "compare: " + 255.0f / max_possible_value);
            Log.d(TAG, "to: " + (((float)median_target)/(float)median_brightness + median_target / 255.0f - 1.0f));
        }
        if( 255.0f / max_possible_value < ((float)median_target)/(float)median_brightness + median_target / 255.0f - 1.0f ) {
            // For Reinhard tonemapping:
            // As noted below, we have f(V) = V.S / (V+C), where V is the HDR value, C is tonemap_scale_c
            // and S = (Vmax + C)/Vmax (see below)
            // Ideally we try to choose C such that we map median value M to target T:
            // f(M) = T
            // => T = M . (Vmax + C) / (Vmax . (M + C))
            // => (T/M).(M + C) = (Vmax + C) / Vmax = 1 + C/Vmax
            // => C . ( T/M - 1/Vmax ) = 1 - T
            // => C = (1-T) / (T/M - 1/Vmax)
            // Since we want C <= 1, we must have:
            // 1-T <= T/M - 1/Vmax
            // => 1/Vmax <= T/M + T - 1
            // If this isn't the case, we set C to 1 (to preserve the median as close as possible).
            // Note that if we weren't doing the linear scaling below, this would reduce to choosing
            // C = M(1-T)/T. We also tend to that as max_possible_value tends to infinity. So even though
            // we only sometimes enter this case, it's important for cases where max_possible_value
            // might be estimated too large (also consider that if we ever support more than 3 images,
            // we'd risk having too large values).
            // If T=M, then this simplifies to C = 1-M.
            // I've tested that using "C = 1-M" always (and no linear scaling) also gives good results:
            // much better compared to Open Camera 1.39, though not quite as good as doing both this
            // and linear scaling (testHDR18, testHDR26, testHDR32 look too grey and/or bright).
            final float tonemap_denom = ((float)median_target)/(float)median_brightness - (255.0f / max_possible_value);
            if( MyDebug.LOG )
                Log.d(TAG, "tonemap_denom: " + tonemap_denom);
            if( tonemap_denom != 0.0f ) // just in case
                tonemap_scale_c = (255.0f - median_target) / tonemap_denom;
            //throw new RuntimeException(); // test
        }
        // Higher tonemap_scale_c values means darker results from the Reinhard tonemapping.
        // Colours brighter than 255-tonemap_scale_c will be made darker, colours darker than 255-tonemap_scale_c will be made brighter
        // (tonemap_scale_c==255 means therefore that colours will only be made darker).
        if( MyDebug.LOG )
            Log.d(TAG, "tonemap_scale_c: " + tonemap_scale_c);
        processHDRScript.set_tonemap_scale(tonemap_scale_c);

        // algorithm specific parameters
        switch( tonemapping_algorithm ) {
            case TONEMAPALGORITHM_EXPONENTIAL:
            {
                // The basic algorithm is f(V) = 1 - exp( - E * V ), where V is the HDR value, E is a
                // constant. This maps [0, infinity] to [0, 1]. However we have an estimate of the maximum
                // possible value, Vmax, so we can set a linear scaling S so that [0, Vmax] maps to [0, 1]
                // f(V) = S . (1 - exp( - E * V ))
                // so 1 = S . (1 - exp( - E * Vmax ))
                // => S = 1 / (1 - exp( - E * Vmax ))
                // Note that Vmax should be set to a minimum of 255, else we'll make darker images brighter.
                float E = processHDRScript.get_exposure();
                float linear_scale = (float)(1.0 / (1.0 - Math.exp(-E * max_possible_value / 255.0)));
                if( MyDebug.LOG )
                    Log.d(TAG, "linear_scale: " + linear_scale);
                processHDRScript.set_linear_scale(linear_scale);
                break;
            }
            case TONEMAPALGORITHM_REINHARD: {
                // The basic algorithm is f(V) = V / (V+C), where V is the HDR value, C is tonemap_scale_c
                // This was used until Open Camera 1.39, but has the problem of making images too dark: it
                // maps [0, infinity] to [0, 1], but since in practice we never have very large V values, we
                // won't use the full [0, 1] range. So we apply a linear scale S:
                // f(V) = V.S / (V+C)
                // S is chosen such that the maximum possible value, Vmax, maps to 1. So:
                // 1 = Vmax . S / (Vmax + C)
                // => S = (Vmax + C)/Vmax
                // Note that we don't actually know the maximum HDR value, but instead we estimate it with
                // max_possible_value, which gives the maximum value we'd have if even the darkest image was
                // 255.0.
                // Note that if max_possible_value was less than 255, we'd end up scaling a max value less than
                // 1, to [0, 1], i.e., making dark images brighter, which we don't want, which is why above we
                // set max_possible_value to a minimum of 255. In practice, this is unlikely to ever happen
                // since max_possible_value is calculated as a maximum possible based on the response functions
                // (as opposed to the real brightest HDR value), so even for dark photos we'd expect to have
                // max_possible_value >= 255.
                // Note that the original Reinhard tonemapping paper describes a non-linear scaling by (1 + CV/Vmax^2),
                // though this is poorer performance (in terms of calculation time).
                float linear_scale = (max_possible_value + tonemap_scale_c) / max_possible_value;
                if( MyDebug.LOG )
                    Log.d(TAG, "linear_scale: " + linear_scale);
                processHDRScript.set_linear_scale(linear_scale);
                break;
            }
            case TONEMAPALGORITHM_FILMIC:
            {
                // For filmic, we have f(V) = U(EV) / U(W), where V is the HDR value, U is a function.
                // We want f(Vmax) = 1, so EVmax = W
                float E = processHDRScript.get_filmic_exposure_bias();
                float W = E * max_possible_value;
                if( MyDebug.LOG )
                    Log.d(TAG, "filmic W: " + W);
                processHDRScript.set_W(W);
                break;
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "call processHDRScript");
        Allocation output_allocation;
        boolean free_output_allocation = false;
        if( release_bitmaps ) {
            // must use allocations[base_bitmap] as the output, as that's the image guaranteed to have no offset (otherwise we'll have
            // problems due to the output being equal to one of the inputs)
            output_allocation = allocations[base_bitmap];
        }
        else {
            output_allocation = Allocation.createFromBitmap(rs, output_bitmap);
            free_output_allocation = true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### time before processHDRScript: " + (System.currentTimeMillis() - time_s));
        if( use_hdr_n ) {
            processHDRScript.set_n_bitmaps_g(n_bitmaps);
            processHDRScript.forEach_hdr_n(allocations[base_bitmap], output_allocation);
        }
        else {
            processHDRScript.forEach_hdr(allocations[base_bitmap], output_allocation);
        }
		/*processHDRScript.set_n_bitmaps_g(n_bitmaps);
		processHDRScript.forEach_hdr_n(allocations[base_bitmap], output_allocation);*/
        if( MyDebug.LOG )
            Log.d(TAG, "### time after processHDRScript: " + (System.currentTimeMillis() - time_s));

        if( release_bitmaps ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmaps");
            // bitmaps.get(base_bitmap) will store HDR image, so free up the rest of the memory asap - we no longer need the remaining bitmaps
            for(int i=0;i<bitmaps.size();i++) {
                if (i != base_bitmap) {
                    Bitmap bitmap = bitmaps.get(i);
                    bitmap.recycle();
                }
            }
        }

        if( hdr_alpha != 0.0f ) {
            adjustHistogram(output_allocation, output_allocation, width, height, hdr_alpha, n_tiles, ce_preserve_blacks, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
        }

        if( release_bitmaps ) {
            // must be the base_bitmap we copy to - see note above about using allocations[base_bitmap] as the output
            allocations[base_bitmap].copyTo(bitmaps.get(base_bitmap));
            if( MyDebug.LOG )
                Log.d(TAG, "### time after copying to bitmap: " + (System.currentTimeMillis() - time_s));

            // make it so that we store the output bitmap as first in the list
            bitmaps.set(0, bitmaps.get(base_bitmap));
            for(int i=1;i<bitmaps.size();i++) {
                bitmaps.set(i, null);
            }
        }
        else {
            output_allocation.copyTo(output_bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after copying to bitmap: " + (System.currentTimeMillis() - time_s));
        }

        if( free_output_allocation )
            output_allocation.destroy();
        for(int i=0;i<n_bitmaps;i++) {
            allocations[i].destroy();
            allocations[i] = null;
        }
        freeScripts();
        if( MyDebug.LOG )
            Log.d(TAG, "### time for processHDRCore: " + (System.currentTimeMillis() - time_s));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void processSingleImage(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, DROTonemappingAlgorithm dro_tonemapping_algorithm) {
        if( MyDebug.LOG )
            Log.d(TAG, "processSingleImage");

        long time_s = System.currentTimeMillis();

        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();

        initRenderscript();
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating renderscript: " + (System.currentTimeMillis() - time_s));

        // create allocation
        Allocation allocation = Allocation.createFromBitmap(rs, bitmaps.get(0));

        Allocation output_allocation;
        boolean free_output_allocation = false;
        if( release_bitmaps ) {
            output_allocation = allocation;
        }
        else {
            free_output_allocation = true;
            output_allocation = Allocation.createFromBitmap(rs, output_bitmap);
        }

        if( dro_tonemapping_algorithm == DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA ) {
            // brighten?
            int [] histo = computeHistogram(allocation, false, false);
            HistogramInfo histogramInfo = getHistogramInfo(histo);
            int brightness = histogramInfo.median_brightness;
            int max_brightness = histogramInfo.max_brightness;
            if( MyDebug.LOG )
                Log.d(TAG, "### time after computeHistogram: " + (System.currentTimeMillis() - time_s));
            if( MyDebug.LOG ) {
                Log.d(TAG, "median brightness: " + brightness);
                Log.d(TAG, "max brightness: " + max_brightness);
            }
            BrightenFactors brighten_factors = computeBrightenFactors(false, 0, 0, brightness, max_brightness);
            float gain = brighten_factors.gain;
            float gamma = brighten_factors.gamma;
            float low_x = brighten_factors.low_x;
            float mid_x = brighten_factors.mid_x;
            if( MyDebug.LOG ) {
                Log.d(TAG, "gain: " + gain);
                Log.d(TAG, "gamma: " + gamma);
                Log.d(TAG, "low_x: " + low_x);
                Log.d(TAG, "mid_x: " + mid_x);
            }

            if( Math.abs(gain - 1.0) > 1.0e-5 || max_brightness != 255 || Math.abs(gamma - 1.0) > 1.0e-5 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "apply gain/gamma");
                //if( true )
                //	throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES); // test

                ScriptC_avg_brighten script = new ScriptC_avg_brighten(rs);
                script.invoke_setBrightenParameters(gain, gamma, low_x, mid_x, max_brightness);

                script.forEach_dro_brighten(allocation, output_allocation);

                // output is now the input for subsequent operations
                if( free_output_allocation ) {
                    allocation.destroy();
                    free_output_allocation = false;
                }
                allocation = output_allocation;
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after dro_brighten: " + (System.currentTimeMillis() - time_s));
            }
        }

        adjustHistogram(allocation, output_allocation, width, height, hdr_alpha, n_tiles, ce_preserve_blacks, time_s);

        if( release_bitmaps ) {
            allocation.copyTo(bitmaps.get(0));
            if( MyDebug.LOG )
                Log.d(TAG, "time after copying to bitmap: " + (System.currentTimeMillis() - time_s));
        }
        else {
            output_allocation.copyTo(output_bitmap);
            if( MyDebug.LOG )
                Log.d(TAG, "time after copying to bitmap: " + (System.currentTimeMillis() - time_s));
        }

        if( free_output_allocation )
            allocation.destroy();
        output_allocation.destroy();
        freeScripts();

        if( MyDebug.LOG )
            Log.d(TAG, "time for processSingleImage: " + (System.currentTimeMillis() - time_s));
    }

    void brightenImage(Bitmap bitmap, int brightness, int max_brightness, int brightness_target) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightenImage");
            Log.d(TAG, "brightness: " + brightness);
            Log.d(TAG, "max_brightness: " + max_brightness);
            Log.d(TAG, "brightness_target: " + brightness_target);
        }
        BrightenFactors brighten_factors = computeBrightenFactors(false, 0, 0, brightness, max_brightness, brightness_target, false);
        float gain = brighten_factors.gain;
        float gamma = brighten_factors.gamma;
        float low_x = brighten_factors.low_x;
        float mid_x = brighten_factors.mid_x;
        if( MyDebug.LOG ) {
            Log.d(TAG, "gain: " + gain);
            Log.d(TAG, "gamma: " + gamma);
            Log.d(TAG, "low_x: " + low_x);
            Log.d(TAG, "mid_x: " + mid_x);
        }

        if( Math.abs(gain - 1.0) > 1.0e-5 || max_brightness != 255 || Math.abs(gamma - 1.0) > 1.0e-5 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "apply gain/gamma");

            initRenderscript();

            Allocation allocation = Allocation.createFromBitmap(rs, bitmap);
            ScriptC_avg_brighten script = new ScriptC_avg_brighten(rs);
            script.invoke_setBrightenParameters(gain, gamma, low_x, mid_x, max_brightness);

            script.forEach_dro_brighten(allocation, allocation);

            allocation.copyTo(bitmap);
            allocation.destroy();

            freeScripts();
        }
    }

    private void initRenderscript() {
        if( MyDebug.LOG )
            Log.d(TAG, "initRenderscript");
        if( rs == null ) {
            // initialise renderscript
            this.rs = RenderScript.create(context);
            if( MyDebug.LOG )
                Log.d(TAG, "create renderscript object");
        }
    }

    private int cached_avg_sample_size = 1;

    /** As part of the noise reduction process, the caller should scale the input images down by the factor returned
     *  by this method. This both provides a spatial smoothing, as well as improving performance and memory usage.
     */
    public int getAvgSampleSize(int capture_result_iso) {
        // If changing this, may also want to change the radius of the spatial filter in avg_brighten.rs ?
        //this.cached_avg_sample_size = (n_images>=8) ? 2 : 1;
        this.cached_avg_sample_size = (capture_result_iso >= 1100) ? 2 : 1;
        //this.cached_avg_sample_size = 1;
        //this.cached_avg_sample_size = 2;
        if( MyDebug.LOG )
            Log.d(TAG, "getAvgSampleSize: " + cached_avg_sample_size);
        return cached_avg_sample_size;
    }

    public int getAvgSampleSize() {
        return cached_avg_sample_size;
    }

    public static class AvgData {
        public Allocation allocation_out;
        Bitmap bitmap_avg_align;
        Allocation allocation_avg_align;

        AvgData(Allocation allocation_out, Bitmap bitmap_avg_align, Allocation allocation_avg_align) {
            this.allocation_out = allocation_out;
            this.bitmap_avg_align = bitmap_avg_align;
            this.allocation_avg_align = allocation_avg_align;
        }

        public void destroy() {
            if( MyDebug.LOG )
                Log.d(TAG, "AvgData.destroy()");
            if( allocation_out != null ) {
                allocation_out.destroy();
                allocation_out = null;
            }
            if( bitmap_avg_align != null ) {
                bitmap_avg_align.recycle();
                bitmap_avg_align = null;
            }
            if( allocation_avg_align != null ) {
                allocation_avg_align.destroy();
                allocation_avg_align = null;
            }
        }
    }

    /** Combines two images by averaging them. Each pixel of bitmap_avg is modified to contain:
     *      (avg_factor * bitmap_avg + bitmap_new)/(avg_factor+1)
     *  A simple average is therefore obtained by calling this function with avg_factor = 1.0f.
     *  For averaging multiple images, first call this function with avg_factor 1.0 for the first
     *  two images, then call updateAvg() for subsequent images, increasing avg_factor by 1.0 each
     *  time.
     *  The reason we do it this way (rather than just receiving a list of bitmaps) is so that we
     *  can average multiple images without having to keep them all in memory simultaneously.
     * @param bitmap_avg     One of the input images. The bitmap is recycled.
     * @param bitmap_new     The other input image. The bitmap is recycled.
     * @param avg_factor     The weighting factor for bitmap_avg.
     * @param iso            The ISO used to take the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public AvgData processAvg(Bitmap bitmap_avg, Bitmap bitmap_new, float avg_factor, int iso, float zoom_factor) throws HDRProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "processAvg");
            Log.d(TAG, "avg_factor: " + avg_factor);
        }
        if( bitmap_avg.getWidth() != bitmap_new.getWidth() ||
                bitmap_avg.getHeight() != bitmap_new.getHeight() ) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "bitmaps not of same resolution");
            }
            throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
        }

        long time_s = System.currentTimeMillis();

        int width = bitmap_avg.getWidth();
        int height = bitmap_avg.getHeight();

        initRenderscript();
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating renderscript: " + (System.currentTimeMillis() - time_s));
        // create allocations
		/*Allocation allocation_avg = Allocation.createFromBitmap(rs, bitmap_avg);
		//Allocation allocation_new = Allocation.createFromBitmap(rs, bitmap_new);
		//Allocation allocation_out = Allocation.createTyped(rs, Type.createXY(rs, Element.F32_3(rs), width, height));
		if( MyDebug.LOG )
			Log.d(TAG, "### time after creating allocations from bitmaps: " + (System.currentTimeMillis() - time_s));
			*/

		/*final boolean use_sharpness_test = false; // disabled for now - takes about 1s extra, and no evidence this helps quality
		if( use_sharpness_test ) {
			float sharpness_avg = computeSharpness(allocation_avg, width, time_s);
			float sharpness_new = computeSharpness(allocation_new, width, time_s);
			if( sharpness_new > sharpness_avg ) {
				if( MyDebug.LOG )
					Log.d(TAG, "use new image as reference");
				Allocation dummy_allocation = allocation_avg;
				allocation_avg = allocation_new;
				allocation_new = dummy_allocation;
				Bitmap dummy_bitmap = bitmap_avg;
				bitmap_avg = bitmap_new;
				bitmap_new = dummy_bitmap;
				sharp_index = 1;
			}
			else {
				sharp_index = 0;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "sharp_index: " + sharp_index);
		}*/

		/*LuminanceInfo luminanceInfo = computeMedianLuminance(bitmap_avg, 0, 0, width, height);
		if( MyDebug.LOG )
			Log.d(TAG, "median: " + luminanceInfo.median_value);*/

        AvgData avg_data = processAvgCore(null, null, bitmap_avg, bitmap_new, width, height, avg_factor, iso, zoom_factor, null, null, time_s);

        //allocation_avg.copyTo(bitmap_avg);

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processAvg: " + (System.currentTimeMillis() - time_s));

        return avg_data;
    }

    /** Combines multiple images by averaging them. See processAvg() for more details.
     * @param avg_data       The argument returned by processAvg().
     * @param width          The width of the images.
     * @param height         The height of the images.
     * @param bitmap_new     The new input image. The bitmap is recycled.
     * @param avg_factor     The weighting factor for bitmap_avg.
     * @param iso            The ISO used to take the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void updateAvg(AvgData avg_data, int width, int height, Bitmap bitmap_new, float avg_factor, int iso, float zoom_factor) throws HDRProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateAvg");
            Log.d(TAG, "avg_factor: " + avg_factor);
        }
        if( width != bitmap_new.getWidth() ||
                height != bitmap_new.getHeight() ) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "bitmaps not of same resolution");
            }
            throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
        }

        long time_s = System.currentTimeMillis();

        // create allocations
		/*Allocation allocation_new = Allocation.createFromBitmap(rs, bitmap_new);
		if( MyDebug.LOG )
			Log.d(TAG, "### time after creating allocations from bitmaps: " + (System.currentTimeMillis() - time_s));*/

        processAvgCore(avg_data.allocation_out, avg_data.allocation_out, null, bitmap_new, width, height, avg_factor, iso, zoom_factor, avg_data.allocation_avg_align, avg_data.bitmap_avg_align, time_s);

        if( MyDebug.LOG )
            Log.d(TAG, "### time for updateAvg: " + (System.currentTimeMillis() - time_s));
    }

    /** Core algorithm for Noise Reduction algorithm.
     * @param allocation_out If non-null, this will be used for the output allocation, otherwise a
     *                       new one will be created.
     * @param allocation_avg If non-null, an allocation for the averaged image so far. If null, the
     *                       first bitmap should be supplied as bitmap_avg.
     * @param bitmap_avg     If non-null, the first bitmap (which will be recycled). If null, an
     *                       allocation_avg should be supplied.
     * @param bitmap_new     The new bitmap to combined. The bitmap will be recycled.
     * @param width          The width of the bitmaps.
     * @param height         The height of the bitmaps.
     * @param avg_factor     The averaging factor.
     * @param iso            The ISO used for the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     * @param allocation_avg_align If non-null, use this allocation for alignment for averaged image.
     * @param bitmap_avg_align Should be supplied if allocation_avg_align is non-null, and stores
     *                         the bitmap corresponding to the allocation_avg_align.
     * @param time_s         Time, for debugging.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private AvgData processAvgCore(Allocation allocation_out, Allocation allocation_avg, Bitmap bitmap_avg, Bitmap bitmap_new, int width, int height, float avg_factor, int iso, float zoom_factor, Allocation allocation_avg_align, Bitmap bitmap_avg_align, long time_s) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "processAvgCore");
            Log.d(TAG, "iso: " + iso);
            Log.d(TAG, "zoom_factor: " + zoom_factor);
        }

        Allocation allocation_new = null;
        boolean free_allocation_avg = false;

        //Allocation allocation_diffs = null;

        offsets_x = new int[2];
        offsets_y = new int[2];
        boolean floating_point = bitmap_avg == null;
        {
            boolean floating_point_align = floating_point;
            // perform auto-alignment
            List<Bitmap> align_bitmaps = new ArrayList<>();
            Allocation [] allocations = new Allocation[2];
            Bitmap bitmap_new_align = null;
            Allocation allocation_new_align = null;
            int alignment_width = width;
            int alignment_height = height;
            int full_alignment_width = width;
            int full_alignment_height = height;

            //final boolean scale_align = false;
            final boolean scale_align = true;
            //final int scale_align_size = 2;
            //final int scale_align_size = 4;
            //final int scale_align_size = Math.max(4 / this.cached_avg_sample_size, 1);
            final int scale_align_size = (zoom_factor > 3.9f) ?
                    1 :
                    Math.max(4 / this.getAvgSampleSize(iso), 1);
            if( MyDebug.LOG )
                Log.d(TAG, "scale_align_size: " + scale_align_size);
            boolean crop_to_centre = true;
            if( scale_align ) {
                // use scaled down and/or cropped bitmaps for alignment
                if( MyDebug.LOG )
                    Log.d(TAG, "### time before creating allocations for autoalignment: " + (System.currentTimeMillis() - time_s));
                Matrix align_scale_matrix = new Matrix();
                align_scale_matrix.postScale(1.0f/scale_align_size, 1.0f/scale_align_size);
                full_alignment_width /= scale_align_size;
                full_alignment_height /= scale_align_size;

                final boolean full_align = false; // whether alignment images should be created as being cropped to the centre
                //final boolean full_align = true; // whether alignment images should be created as being cropped to the centre
                int align_width = width;
                int align_height = height;
                int align_x = 0;
                int align_y = 0;
                if( !full_align ) {
                    // need to use /2 rather than /4 to prevent misalignment in testAvg26
                    //align_width = width/4;
                    //align_height = height/4;
                    align_width = width/2;
                    align_height = height/2;
                    align_x = (width - align_width)/2;
                    align_y = (height - align_height)/2;
                    crop_to_centre = false; // no need to crop in autoAlignment, as we're cropping here
                }

                final boolean filter_align = false;
                //final boolean filter_align = true;
                if( allocation_avg_align == null ) {
                    bitmap_avg_align = Bitmap.createBitmap(bitmap_avg, align_x, align_y, align_width, align_height, align_scale_matrix, filter_align);
                    allocation_avg_align = Allocation.createFromBitmap(rs, bitmap_avg_align);
                    if( MyDebug.LOG )
                        Log.d(TAG, "### time after creating avg allocation for autoalignment: " + (System.currentTimeMillis() - time_s));
                }
                bitmap_new_align = Bitmap.createBitmap(bitmap_new, align_x, align_y, align_width, align_height, align_scale_matrix, filter_align);
                allocation_new_align = Allocation.createFromBitmap(rs, bitmap_new_align);

                alignment_width = bitmap_new_align.getWidth();
                alignment_height = bitmap_new_align.getHeight();

                align_bitmaps.add(bitmap_avg_align);
                align_bitmaps.add(bitmap_new_align);
                allocations[0] = allocation_avg_align;
                allocations[1] = allocation_new_align;
                floating_point_align = false;
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after creating allocations for autoalignment: " + (System.currentTimeMillis() - time_s));
            }
            else {
                if( allocation_avg == null ) {
                    allocation_avg = Allocation.createFromBitmap(rs, bitmap_avg);
                    free_allocation_avg = true;
                }
                allocation_new = Allocation.createFromBitmap(rs, bitmap_new);
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after creating allocations from bitmaps: " + (System.currentTimeMillis() - time_s));
                align_bitmaps.add(bitmap_avg);
                align_bitmaps.add(bitmap_new);
                allocations[0] = allocation_avg;
                allocations[1] = allocation_new;
            }

            // misalignment more likely in "dark" images with more images and/or longer exposures
            // using max_align_scale=2 needed to prevent misalignment in testAvg51; also helps testAvg14
            boolean wider = iso >= 1100;
            autoAlignment(offsets_x, offsets_y, allocations, alignment_width, alignment_height, align_bitmaps, 0, true, null, false, floating_point_align, 1, crop_to_centre, wider ? 2 : 1, full_alignment_width, full_alignment_height, time_s);

			/*
			// compute allocation_diffs
			// if enabling this, should also:
			// - set full_align above to true
			// - set filter_align above to true
			if( processAvgScript == null ) {
				processAvgScript = new ScriptC_process_avg(rs);
			}
			processAvgScript.set_bitmap_align_new(allocations[1]);
			processAvgScript.set_offset_x_new(offsets_x[1]);
			processAvgScript.set_offset_y_new(offsets_y[1]);
			allocation_diffs = Allocation.createTyped(rs, Type.createXY(rs, Element.F32(rs), alignment_width, alignment_height));
			processAvgScript.forEach_compute_diff(allocations[0], allocation_diffs);
			processAvgScript.set_scale_align_size(scale_align_size);
			processAvgScript.set_allocation_diffs(allocation_diffs);
			*/

            if( scale_align ) {
                for(int i=0;i<offsets_x.length;i++) {
                    offsets_x[i] *= scale_align_size;
                }
                for(int i=0;i<offsets_y.length;i++) {
                    offsets_y[i] *= scale_align_size;
                }
            }

            if( bitmap_new_align != null ) {
                bitmap_new_align.recycle();
                //noinspection UnusedAssignment
                bitmap_new_align = null;
            }
            if( allocation_new_align != null ) {
                allocation_new_align.destroy();
                //noinspection UnusedAssignment
                allocation_new_align = null;
            }

            if( MyDebug.LOG ) {
                Log.d(TAG, "### time after autoAlignment: " + (System.currentTimeMillis() - time_s));
            }
        }

        if( allocation_out == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to create allocation_out");
            allocation_out = Allocation.createTyped(rs, Type.createXY(rs, Element.F32_3(rs), width, height));
            if( MyDebug.LOG )
                Log.d(TAG, "### time after create allocation_out: " + (System.currentTimeMillis() - time_s));
        }
        if( allocation_avg == null ) {
            allocation_avg = Allocation.createFromBitmap(rs, bitmap_avg);
            free_allocation_avg = true;
            if( MyDebug.LOG )
                Log.d(TAG, "### time after creating allocation_avg from bitmap: " + (System.currentTimeMillis() - time_s));
        }

        // write new avg image

        // create RenderScript
        if( processAvgScript == null ) {
            processAvgScript = new ScriptC_process_avg(rs);
        }
        //ScriptC_process_avg processAvgScript = new ScriptC_process_avg(rs);

		/*final boolean separate_first_pass = false; // whether to convert the first two images in separate passes (reduces memory)
		if( first && separate_first_pass ) {
			if( MyDebug.LOG )
				Log.d(TAG, "### time before convert_to_f: " + (System.currentTimeMillis() - time_s));
			processAvgScript.forEach_convert_to_f(allocation_avg, allocation_out);
			if( MyDebug.LOG )
				Log.d(TAG, "### time after convert_to_f: " + (System.currentTimeMillis() - time_s));
			if( free_allocation_avg ) {
				allocation_avg.destroy();
				free_allocation_avg = false;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "release bitmap_avg");
			bitmap_avg.recycle();
			bitmap_avg = null;
			allocation_avg = allocation_out;
			first = false;
		}*/

        if( allocation_new == null ) {
            allocation_new = Allocation.createFromBitmap(rs, bitmap_new);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after creating allocation_new from bitmap: " + (System.currentTimeMillis() - time_s));
        }

        // set allocations
        //processAvgScript.set_bitmap_avg(allocation_avg);
        processAvgScript.set_bitmap_new(allocation_new);

        // set offsets
        processAvgScript.set_offset_x_new(offsets_x[1]);
        processAvgScript.set_offset_y_new(offsets_y[1]);

        // set globals

        processAvgScript.set_avg_factor(avg_factor);

        // if changing this, pay close attention to tests testAvg6, testAvg8, testAvg17, testAvg23
        float limited_iso = Math.min(iso, 400);
        float wiener_cutoff_factor = 1.0f;
        if( iso >= 700 ) {
            // helps reduce speckles in testAvg17, testAvg23, testAvg33, testAvg36, testAvg38
            // using this level for testAvg31 (ISO 609) would increase ghosting
            //limited_iso = 500;
            limited_iso = 800;
            if( iso >= 1100 ) {
                // helps further reduce speckles in testAvg17, testAvg38
                // but don't do for iso >= 700 as makes "vicks" text in testAvg23 slightly more blurred
                wiener_cutoff_factor = 8.0f;
            }
        }
        limited_iso = Math.max(limited_iso, 100);
        float wiener_C = 10.0f * limited_iso;
        //float wiener_C = 1000.0f;
        //float wiener_C = 4000.0f;

        // Tapering the wiener scale means that we do more averaging for earlier images in the stack, the
        // logic being we'll have more chance of ghosting or misalignment with later images.
        // This helps: testAvg31, testAvg33.
        // Also slightly helps testAvg17, testAvg23 (slightly less white speckle on tv), testAvg28
        // (one less white speckle on face).
        // Note that too much tapering risks increasing ghosting in testAvg26, testAvg39.
        float tapered_wiener_scale = 1.0f - (float)Math.pow(0.5, avg_factor);
        if( MyDebug.LOG ) {
            Log.d(TAG, "avg_factor: " + avg_factor);
            Log.d(TAG, "tapered_wiener_scale: " + tapered_wiener_scale);
        }
        wiener_C /= tapered_wiener_scale;

        float wiener_C_cutoff = wiener_cutoff_factor * wiener_C;
        if( MyDebug.LOG ) {
            Log.d(TAG, "wiener_C: " + wiener_C);
            Log.d(TAG, "wiener_cutoff_factor: " + wiener_cutoff_factor);
        }
        processAvgScript.set_wiener_C(wiener_C);
        processAvgScript.set_wiener_C_cutoff(wiener_C_cutoff);

		/*final float max_weight = 0.9375f;
		if( MyDebug.LOG ) {
			Log.d(TAG, "max_weight: " + max_weight);
		}
		processAvgScript.set_max_weight(max_weight);*/

        if( MyDebug.LOG )
            Log.d(TAG, "call processAvgScript");
        if( MyDebug.LOG )
            Log.d(TAG, "### time before processAvgScript: " + (System.currentTimeMillis() - time_s));
        if( floating_point )
            processAvgScript.forEach_avg_f(allocation_avg, allocation_out);
        else
            processAvgScript.forEach_avg(allocation_avg, allocation_out);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after processAvgScript: " + (System.currentTimeMillis() - time_s));

		/*if( allocation_diffs != null ) {
			allocation_diffs.destroy();
			allocation_diffs = null;
		}*/
        allocation_new.destroy();
        if( free_allocation_avg ) {
            allocation_avg.destroy();
        }
        if( bitmap_avg != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmap_avg");
            bitmap_avg.recycle();
            //noinspection UnusedAssignment
            bitmap_avg = null;
        }
        if( bitmap_new != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmap_new");
            bitmap_new.recycle();
            //noinspection UnusedAssignment
            bitmap_new = null;
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processAvgCore: " + (System.currentTimeMillis() - time_s));
        return new AvgData(allocation_out, bitmap_avg_align, allocation_avg_align);
    }

    /** Combines multiple images by averaging them.
     * @param bitmaps Input bitmaps. The resultant bitmap will be stored as the first bitmap on exit,
     *                the other input bitmaps will be recycled.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void processAvgMulti(List<Bitmap> bitmaps, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks) throws HDRProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "processAvgMulti");
            Log.d(TAG, "hdr_alpha: " + hdr_alpha);
        }
        int n_bitmaps = bitmaps.size();
        if( n_bitmaps != 8 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "n_bitmaps should be 8, not " + n_bitmaps);
            throw new HDRProcessorException(HDRProcessorException.INVALID_N_IMAGES);
        }
        for(int i=1;i<n_bitmaps;i++) {
            if( bitmaps.get(i).getWidth() != bitmaps.get(0).getWidth() ||
                    bitmaps.get(i).getHeight() != bitmaps.get(0).getHeight() ) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "bitmaps not of same resolution");
                    for(int j=0;j<n_bitmaps;j++) {
                        Log.e(TAG, "bitmaps " + j + " : " + bitmaps.get(j).getWidth() + " x " + bitmaps.get(j).getHeight());
                    }
                }
                throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
            }
        }

        long time_s = System.currentTimeMillis();

        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();

        initRenderscript();
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating renderscript: " + (System.currentTimeMillis() - time_s));
        // create allocations
        Allocation allocation0 = Allocation.createFromBitmap(rs, bitmaps.get(0));
        Allocation allocation1 = Allocation.createFromBitmap(rs, bitmaps.get(1));
        Allocation allocation2 = Allocation.createFromBitmap(rs, bitmaps.get(2));
        Allocation allocation3 = Allocation.createFromBitmap(rs, bitmaps.get(3));
        Allocation allocation4 = Allocation.createFromBitmap(rs, bitmaps.get(4));
        Allocation allocation5 = Allocation.createFromBitmap(rs, bitmaps.get(5));
        Allocation allocation6 = Allocation.createFromBitmap(rs, bitmaps.get(6));
        Allocation allocation7 = Allocation.createFromBitmap(rs, bitmaps.get(7));
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating allocations from bitmaps: " + (System.currentTimeMillis() - time_s));

        // perform auto-alignment
		/*for(int i=1;i<bitmaps.size();i++) {
		{
			List<Bitmap> bitmaps2 = new ArrayList<>();
			bitmaps2.add(bitmaps.get(0));
			bitmaps2.add(bitmap.get(i));
			Allocation [] allocations = new Allocation[2];
			allocations[0] = allocation_avg;
			allocations[1] = allocation_new;
			BrightnessDetails brightnessDetails = autoAlignment(offsets_x, offsets_y, allocations, width, height, bitmaps, 0, true, null, true, time_s);
			int median_brightness = brightnessDetails.median_brightness;
			if( MyDebug.LOG ) {
				Log.d(TAG, "### time after autoAlignment: " + (System.currentTimeMillis() - time_s));
				Log.d(TAG, "median_brightness: " + median_brightness);
			}
		}*/

        // write new avg image

        // create RenderScript
		/*if( processAvgScript == null ) {
			processAvgScript = new ScriptC_process_avg(rs);
		}*/
        ScriptC_process_avg processAvgScript = new ScriptC_process_avg(rs);

        // set allocations
        processAvgScript.set_bitmap1(allocation1);
        processAvgScript.set_bitmap2(allocation2);
        processAvgScript.set_bitmap3(allocation3);
        processAvgScript.set_bitmap4(allocation4);
        processAvgScript.set_bitmap5(allocation5);
        processAvgScript.set_bitmap6(allocation6);
        processAvgScript.set_bitmap7(allocation7);

        // set offsets
        //processAvgScript.set_offset_x_new(offsets_x[1]);
        //processAvgScript.set_offset_y_new(offsets_y[1]);

        //hdr_alpha = 0.0f; // test

        // set globals

        if( MyDebug.LOG )
            Log.d(TAG, "call processAvgScript");
        if( MyDebug.LOG )
            Log.d(TAG, "### time before processAvgScript: " + (System.currentTimeMillis() - time_s));
        processAvgScript.forEach_avg_multi(allocation0, allocation0);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after processAvgScript: " + (System.currentTimeMillis() - time_s));

        {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmaps");
            for(int i=1;i<bitmaps.size();i++) {
                bitmaps.get(i).recycle();
            }
        }

        if( hdr_alpha != 0.0f ) {
            adjustHistogram(allocation0, allocation0, width, height, hdr_alpha, n_tiles, ce_preserve_blacks, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
        }

        allocation0.copyTo(bitmaps.get(0));

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processAvgMulti: " + (System.currentTimeMillis() - time_s));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void autoAlignment(int[] offsets_x, int[] offsets_y, int width, int height, List<Bitmap> bitmaps, int base_bitmap, boolean use_mtb, int max_align_scale) {
        if( MyDebug.LOG )
            Log.d(TAG, "autoAlignment");
        initRenderscript();
        Allocation [] allocations = new Allocation[bitmaps.size()];
        for(int i=0;i<bitmaps.size();i++) {
            allocations[i] = Allocation.createFromBitmap(rs, bitmaps.get(i));
        }

        autoAlignment(offsets_x, offsets_y, allocations, width, height, bitmaps, base_bitmap, true, null, use_mtb, false, 1, false, max_align_scale, width, height, 0);

        for(int i=0;i<allocations.length;i++) {
            if( allocations[i] != null ) {
                allocations[i].destroy();
                allocations[i] = null;
            }
        }
        freeScripts();
    }

    static class BrightnessDetails {
        final int median_brightness; // median brightness value of the median image

        BrightnessDetails(int median_brightness) {
            this.median_brightness = median_brightness;
        }
    }

    /**
     *
     * @param bitmaps       Only required if use_mtb is true, otherwise may be null.
     * @param base_bitmap   Index of bitmap in bitmaps that should be kept fixed; the other bitmaps
     *                      will be aligned relative to this.
     * @param assume_sorted If assume_sorted if false, and use_mtb is true, this function will also
     *                      sort the allocations and bitmaps from darkest to brightest.
     * @param use_mtb       Whether to align based on the median threshold bitmaps or not.
     * @param floating_point If true, the first allocation is in floating point (F32_3) format.
     * @param max_align_scale If larger than 1, start from a larger start area.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private BrightnessDetails autoAlignment(int [] offsets_x, int [] offsets_y, Allocation [] allocations, int width, int height, List<Bitmap> bitmaps, int base_bitmap, boolean assume_sorted, SortCallback sort_cb, boolean use_mtb, boolean floating_point, int min_step_size, boolean crop_to_centre, int max_align_scale, int full_width, int full_height, long time_s) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoAlignment");
            Log.d(TAG, "width: " + width);
            Log.d(TAG, "height: " + height);
            Log.d(TAG, "use_mtb: " + use_mtb);
            Log.d(TAG, "max_align_scale: " + max_align_scale);
            Log.d(TAG, "allocations: " + allocations.length);
            for(Allocation allocation : allocations) {
                Log.d(TAG, "    allocation:");
                Log.d(TAG, "    element: " + allocation.getElement());
                Log.d(TAG, "    type X: " + allocation.getType().getX());
                Log.d(TAG, "    type Y: " + allocation.getType().getY());
            }
        }

        // initialise
        for(int i=0;i<offsets_x.length;i++) {
            offsets_x[i] = 0;
            offsets_y[i] = 0;
        }

        Allocation [] mtb_allocations = new Allocation[allocations.length];
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating mtb_allocations: " + (System.currentTimeMillis() - time_s));

        // Testing shows that in practice we get good results by only aligning the centre quarter of the images. This gives better
        // performance, and uses less memory.
        // If copy_to_centre is false, this has already been done by the caller.
        int mtb_width = width;
        int mtb_height = height;
        int mtb_x = 0;
        int mtb_y = 0;
        if( crop_to_centre ) {
            mtb_width = width/2;
            mtb_height = height/2;
            mtb_x = mtb_width/2;
            mtb_y = mtb_height/2;
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "mtb_x: " + mtb_x);
            Log.d(TAG, "mtb_y: " + mtb_y);
            Log.d(TAG, "mtb_width: " + mtb_width);
            Log.d(TAG, "mtb_height: " + mtb_height);
        }

        // create RenderScript
        if( createMTBScript == null ) {
            createMTBScript = new ScriptC_create_mtb(rs);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after creating createMTBScript: " + (System.currentTimeMillis() - time_s));
        }
        //ScriptC_create_mtb createMTBScript = new ScriptC_create_mtb(rs);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating createMTBScript: " + (System.currentTimeMillis() - time_s));

        LuminanceInfo [] luminanceInfos = null;
        if( use_mtb ) {
            luminanceInfos = new LuminanceInfo[allocations.length];
            for(int i = 0; i < allocations.length; i++) {
                luminanceInfos[i] = computeMedianLuminance(bitmaps.get(i), mtb_x, mtb_y, mtb_width, mtb_height);
                if( MyDebug.LOG )
                    Log.d(TAG, i + ": median_value: " + luminanceInfos[i].median_value);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time after computeMedianLuminance: " + (System.currentTimeMillis() - time_s));
        }

        if( !assume_sorted && use_mtb ) {
            if( MyDebug.LOG )
                Log.d(TAG, "sort bitmaps");
            class BitmapInfo {
                final LuminanceInfo luminanceInfo;
                final Bitmap bitmap;
                final Allocation allocation;
                final int index;

                BitmapInfo(LuminanceInfo luminanceInfo, Bitmap bitmap, Allocation allocation, int index) {
                    this.luminanceInfo = luminanceInfo;
                    this.bitmap = bitmap;
                    this.allocation = allocation;
                    this.index = index;
                }

            }

            List<BitmapInfo> bitmapInfos = new ArrayList<>(bitmaps.size());
            for(int i=0;i<bitmaps.size();i++) {
                BitmapInfo bitmapInfo = new BitmapInfo(luminanceInfos[i], bitmaps.get(i), allocations[i], i);
                bitmapInfos.add(bitmapInfo);
            }
            Collections.sort(bitmapInfos, new Comparator<BitmapInfo>() {
                @Override
                public int compare(BitmapInfo o1, BitmapInfo o2) {
                    return o1.luminanceInfo.median_value - o2.luminanceInfo.median_value;
                }
            });
            bitmaps.clear();
            for(int i=0;i<bitmapInfos.size();i++) {
                bitmaps.add(bitmapInfos.get(i).bitmap);
                luminanceInfos[i] = bitmapInfos.get(i).luminanceInfo;
                allocations[i] = bitmapInfos.get(i).allocation;
            }
            if( MyDebug.LOG ) {
                for(int i=0;i<allocations.length;i++) {
                    Log.d(TAG, i + ": median_value: " + luminanceInfos[i].median_value);
                }
            }
            if( sort_cb != null ) {
                List<Integer> sort_order = new ArrayList<>();
                for(int i=0;i<bitmapInfos.size();i++) {
                    sort_order.add( bitmapInfos.get(i).index);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "sort_order: " + sort_order);
                sort_cb.sortOrder(sort_order);
            }
        }

        int median_brightness = -1;
        if( use_mtb ) {
            median_brightness = luminanceInfos[base_bitmap].median_value;
            if( MyDebug.LOG )
                Log.d(TAG, "median_brightness: " + median_brightness);
        }

        for(int i=0;i<allocations.length;i++) {
            int median_value = -1;
            if( use_mtb ) {
                median_value = luminanceInfos[i].median_value;
                if( MyDebug.LOG )
                    Log.d(TAG, i + ": median_value: " + median_value);

				/*if( median_value < 16 ) {
					// needed for testHDR2, testHDR28
					if( MyDebug.LOG )
						Log.d(TAG, "image too dark to do alignment");
					mtb_allocations[i] = null;

					continue;
				}*/
            }

            if( use_mtb && luminanceInfos[i].noisy ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "unable to compute median luminance safely");
                mtb_allocations[i] = null;
                continue;
            }

            mtb_allocations[i] = Allocation.createTyped(rs, Type.createXY(rs, Element.U8(rs), mtb_width, mtb_height));

            // set parameters
            if( use_mtb )
                createMTBScript.set_median_value(median_value);
            createMTBScript.set_start_x(mtb_x);
            createMTBScript.set_start_y(mtb_y);
            createMTBScript.set_out_bitmap(mtb_allocations[i]);

            if( MyDebug.LOG )
                Log.d(TAG, "call createMTBScript");
            Script.LaunchOptions launch_options = new Script.LaunchOptions();
            //launch_options.setX((int)(width*0.25), (int)(width*0.75));
            //launch_options.setY((int)(height*0.25), (int)(height*0.75));
            //createMTBScript.forEach_create_mtb(allocations[i], mtb_allocations[i], launch_options);
            launch_options.setX(mtb_x, mtb_x+mtb_width);
            launch_options.setY(mtb_y, mtb_y+mtb_height);
            if( use_mtb )
                createMTBScript.forEach_create_mtb(allocations[i], launch_options);
            else {
                if( floating_point && i == 0 )
                    createMTBScript.forEach_create_greyscale_f(allocations[i], launch_options);
                else
                    createMTBScript.forEach_create_greyscale(allocations[i], launch_options);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time after createMTBScript: " + (System.currentTimeMillis() - time_s));

			/*if( MyDebug.LOG ) {
				// debugging
				byte [] mtb_bytes = new byte[mtb_width*mtb_height];
				mtb_allocations[i].copyTo(mtb_bytes);
				int [] pixels = new int[mtb_width*mtb_height];
				for(int j=0;j<mtb_width*mtb_height;j++) {
					int b = mtb_bytes[j];
					if( b < 0 )
						b += 255;
					pixels[j] = Color.argb(255, b, b, b);
				}
				Bitmap mtb_bitmap = Bitmap.createBitmap(pixels, mtb_width, mtb_height, Bitmap.Config.ARGB_8888);
				File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/mtb_bitmap" + i + ".jpg");
				try {
					OutputStream outputStream = new FileOutputStream(file);
					mtb_bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
					outputStream.close();
					MainActivity mActivity = (MainActivity) context;
					mActivity.getStorageUtils().broadcastFile(file, true, false, true);
				}
				catch(IOException e) {
					e.printStackTrace();
				}
				mtb_bitmap.recycle();
			}*/
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### time after all createMTBScript: " + (System.currentTimeMillis() - time_s));

        // The initial step_size N should be a power of 2; the maximum offset we can achieve by the algorithm is N-1.
        // For pictures resolution 4160x3120, this gives max_ideal_size 27, and initial_step_size 32.
        // On tests testHDR1 to testHDR35, the max required offset was 24 pixels (for testHDR33) even when using
        // inital_step_size of 64.
        // Note, there isn't really a performance cost in allowing higher initial step sizes (as larger sizes have less
        // sampling - since we sample every step_size pixels - though there might be some overhead for every extra call
        // to renderscript that we do). But high step sizes have a risk of producing really bad results if we were
        // to misidentify cases as needing a large offset.
        int max_dim = Math.max(full_width, full_height); // n.b., use the full width and height here, not the mtb_width, height
        //int max_ideal_size = max_dim / (wider ? 75 : 150);
        int max_ideal_size = (max_align_scale * max_dim) / 150;
        int initial_step_size = 1;
        while( initial_step_size < max_ideal_size ) {
            initial_step_size *= 2;
        }
        //initial_step_size = 64;
        if( MyDebug.LOG ) {
            Log.d(TAG, "max_dim: " + max_dim);
            Log.d(TAG, "max_ideal_size: " + max_ideal_size);
            Log.d(TAG, "initial_step_size: " + initial_step_size);
        }

        if( mtb_allocations[base_bitmap] == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "base image not suitable for image alignment");
            for(int i=0;i<mtb_allocations.length;i++) {
                if( mtb_allocations[i] != null ) {
                    mtb_allocations[i].destroy();
                    mtb_allocations[i] = null;
                }
            }
            return new BrightnessDetails(median_brightness);
        }

        // create RenderScript
        if( alignMTBScript == null ) {
            alignMTBScript = new ScriptC_align_mtb(rs);
        }
        //ScriptC_align_mtb alignMTBScript = new ScriptC_align_mtb(rs);

        // set parameters
        alignMTBScript.set_bitmap0(mtb_allocations[base_bitmap]);
        // bitmap1 set below

        for(int i=0;i<allocations.length;i++)  {
            if( i == base_bitmap ) {
                // don't need to align the "base" reference image
                continue;
            }
            if( mtb_allocations[i] == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "image " + i + " not suitable for image alignment");
                continue;
            }

            alignMTBScript.set_bitmap1(mtb_allocations[i]);

            //final int pixel_step = use_mtb ? 1 : 4;
            final int pixel_step = 1;
            int step_size = initial_step_size;
            while( step_size > min_step_size ) {
                step_size /= 2;
                int pixel_step_size = step_size * pixel_step;
                if( pixel_step_size > mtb_width || pixel_step_size > mtb_height )
                    pixel_step_size = step_size;

                if( MyDebug.LOG ) {
                    Log.d(TAG, "call alignMTBScript for image: " + i);
                    Log.d(TAG, "    versus base image: " + base_bitmap);
                    Log.d(TAG, "step_size: " + step_size);
                    Log.d(TAG, "pixel_step_size: " + pixel_step_size);
                }

                final boolean use_pyramid = false;
                //final boolean use_pyramid = true;
				/*if( use_pyramid ) {
					// downscale by step_size
					Allocation [] scaled_allocations = new Allocation[2];
					for(int j=0;j<2;j++) {
						int scaled_width = mtb_width/step_size;
						int scaled_height = mtb_height/step_size;
						if( MyDebug.LOG ) {
							Log.d(TAG, "create scaled image: " + j);
							Log.d(TAG, "    scaled_width: " + scaled_width);
							Log.d(TAG, "    scaled_height: " + scaled_height);
						}
						Allocation allocation_to_scale = mtb_allocations[(j==0) ? base_bitmap : i];
						Type type = Type.createXY(rs, allocation_to_scale.getElement(), scaled_width, scaled_height);
						scaled_allocations[j] = Allocation.createTyped(rs, type);
						ScriptIntrinsicResize theIntrinsic = ScriptIntrinsicResize.create(rs);
						theIntrinsic.setInput(allocation_to_scale);
						theIntrinsic.forEach_bicubic(scaled_allocations[j]);
					}
					alignMTBScript.set_bitmap0(scaled_allocations[0]);
					alignMTBScript.set_bitmap1(scaled_allocations[1]);
					int off_x = offsets_x[i]/step_size;
					int off_y = offsets_y[i]/step_size;
					if( MyDebug.LOG ) {
						Log.d(TAG, "off_x: " + off_x);
						Log.d(TAG, "off_y: " + off_y);
					}
					alignMTBScript.set_off_x( off_x );
					alignMTBScript.set_off_y( off_y );
					alignMTBScript.set_step_size( 1 );
				}
				else*/ {
                    alignMTBScript.set_off_x( offsets_x[i] );
                    alignMTBScript.set_off_y( offsets_y[i] );
                    alignMTBScript.set_step_size( pixel_step_size );
                }

                Allocation errorsAllocation = Allocation.createSized(rs, Element.I32(rs), 9);
                alignMTBScript.bind_errors(errorsAllocation);
                alignMTBScript.invoke_init_errors();

                Script.LaunchOptions launch_options = new Script.LaunchOptions();
                if( !use_pyramid ) {
                    // see note inside align_mtb.rs/align_mtb() for why we sample over a subset of the image
                    int stop_x = mtb_width/pixel_step_size;
                    int stop_y = mtb_height/pixel_step_size;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "stop_x: " + stop_x);
                        Log.d(TAG, "stop_y: " + stop_y);
                    }
                    //launch_options.setX((int)(stop_x*0.25), (int)(stop_x*0.75));
                    //launch_options.setY((int)(stop_y*0.25), (int)(stop_y*0.75));
                    launch_options.setX(0, stop_x);
                    launch_options.setY(0, stop_y);
                }
                long this_time_s = System.currentTimeMillis();
                if( use_mtb )
                    alignMTBScript.forEach_align_mtb(mtb_allocations[base_bitmap], launch_options);
                else
                    alignMTBScript.forEach_align(mtb_allocations[base_bitmap], launch_options);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "time for alignMTBScript: " + (System.currentTimeMillis() - this_time_s));
                    Log.d(TAG, "time after alignMTBScript: " + (System.currentTimeMillis() - time_s));
                }

                int best_error = -1;
                int best_id = -1;
                int [] errors = new int[9];
                errorsAllocation.copyTo(errors);
                errorsAllocation.destroy();
                for(int j=0;j<9;j++) {
                    int this_error = errors[j];
                    if( MyDebug.LOG )
                        Log.d(TAG, "    errors[" + j + "]: " + this_error);
                    if( best_id==-1 || this_error < best_error ) {
                        best_error = this_error;
                        best_id = j;
                    }
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "    best_id " + best_id + " error: " + best_error);
                if( best_error >= 2000000000 ) {
                    Log.e(TAG, "    auto-alignment failed due to overflow");
                    // hitting overflow means behaviour will be unstable under SMP, and auto-alignment won't be reliable anyway
                    best_id = 4; // default to centre
					if( is_test ) {
						throw new RuntimeException();
					}
                }
				/*if( best_id != 4 ) {
					int this_off_x = best_id % 3;
					int this_off_y = best_id/3;
					this_off_x--;
					this_off_y--;
					for(int j=0;j<9;j++) {
						int that_off_x = j % 3;
						int that_off_y = j/3;
						that_off_x--;
						that_off_y--;
						if( this_off_x * that_off_x == -1 || this_off_y * that_off_y == -1 ) {
							float diff = ((float)(best_error - errors[j]))/(float)errors[j];
							if( MyDebug.LOG )
								Log.d(TAG, "    opposite errors[" + j + "] diff: " + diff);
							if( Math.abs(diff) <= 0.02f ) {
								if( MyDebug.LOG )
									Log.d(TAG, "    reject auto-alignment");
								best_id = 4;
								break;
							}
						}
					}
				}*/
                if( best_id != -1 ) {
                    int this_off_x = best_id % 3;
                    int this_off_y = best_id/3;
                    this_off_x--;
                    this_off_y--;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "this_off_x: " + this_off_x);
                        Log.d(TAG, "this_off_y: " + this_off_y);
                    }
                    offsets_x[i] += this_off_x * step_size;
                    offsets_y[i] += this_off_y * step_size;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "offsets_x is now: " + offsets_x[i]);
                        Log.d(TAG, "offsets_y is now: " + offsets_y[i]);
                    }
					/*if( wider && step_size == initial_step_size/2 && (this_off_x != 0 || this_off_y != 0 ) ) {
						throw new RuntimeException(); // test
					}*/
                }
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "resultant offsets for image: " + i);
                Log.d(TAG, "resultant offsets_x: " + offsets_x[i]);
                Log.d(TAG, "resultant offsets_y: " + offsets_y[i]);
            }
        }

		/*for(int i=0;i<allocations.length;i++) {
			offsets_x[i] = 0;
			offsets_y[i] = 0;
		}*/
        for(int i=0;i<mtb_allocations.length;i++) {
            if( mtb_allocations[i] != null ) {
                mtb_allocations[i].destroy();
                mtb_allocations[i] = null;
            }
        }
        return new BrightnessDetails(median_brightness);
    }

    private static class LuminanceInfo {
        final int median_value;
        final boolean noisy;

        LuminanceInfo(int median_value, boolean noisy) {
            this.median_value = median_value;
            this.noisy = noisy;
        }
    }

    private LuminanceInfo computeMedianLuminance(Bitmap bitmap, int mtb_x, int mtb_y, int mtb_width, int mtb_height) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computeMedianLuminance");
            Log.d(TAG, "mtb_x: " + mtb_x);
            Log.d(TAG, "mtb_y: " + mtb_y);
            Log.d(TAG, "mtb_width: " + mtb_width);
            Log.d(TAG, "mtb_height: " + mtb_height);
        }
        final int n_samples_c = 100;
        final int n_w_samples = (int)Math.sqrt(n_samples_c);
        final int n_h_samples = n_samples_c/n_w_samples;

        int [] histo = new int[256];
        for(int i=0;i<256;i++)
            histo[i] = 0;
        int total = 0;
        //double sum_log_luminance = 0.0;
        for(int y=0;y<n_h_samples;y++) {
            double alpha = ((double) y + 1.0) / ((double) n_h_samples + 1.0);
            //int y_coord = (int) (alpha * bitmap.getHeight());
            int y_coord = mtb_y + (int) (alpha * mtb_height);
            for(int x=0;x<n_w_samples;x++) {
                double beta = ((double) x + 1.0) / ((double) n_w_samples + 1.0);
                //int x_coord = (int) (beta * bitmap.getWidth());
                int x_coord = mtb_x + (int) (beta * mtb_width);
				/*if( MyDebug.LOG )
					Log.d(TAG, "sample value from " + x_coord + " , " + y_coord);*/
                int color = bitmap.getPixel(x_coord, y_coord);
                int r = (color & 0xFF0000) >> 16;
                int g = (color & 0xFF00) >> 8;
                int b = (color & 0xFF);
                int luminance = Math.max(r, g);
                luminance = Math.max(luminance, b);
                histo[luminance]++;
                //sum_log_luminance += Math.log(luminance+1.0); // add 1 so we don't take log of 0...;
                total++;
            }
        }
		/*float avg_luminance = (float)(Math.exp( sum_log_luminance / total ));
		if( MyDebug.LOG )
			Log.d(TAG, "avg_luminance: " + avg_luminance);*/
        int middle = total/2;
        int count = 0;
        boolean noisy = false;
        for(int i=0;i<256;i++) {
            count += histo[i];
            if( count >= middle ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "median luminance " + i);
                final int noise_threshold = 4;
                int n_below = 0, n_above = 0;
                for(int j=0;j<=i-noise_threshold;j++) {
                    n_below += histo[j];
                }
                for(int j=0;j<=i+noise_threshold && j<256;j++) {
                    n_above += histo[j];
                }
                double frac_below = n_below / (double)total;
                if( MyDebug.LOG ) {
                    double frac_above = 1.0 - n_above / (double)total;
                    Log.d(TAG, "count: " + count);
                    Log.d(TAG, "n_below: " + n_below);
                    Log.d(TAG, "n_above: " + n_above);
                    Log.d(TAG, "frac_below: " + frac_below);
                    Log.d(TAG, "frac_above: " + frac_above);
                }
                if( frac_below < 0.2 ) {
                    // needed for testHDR2, testHDR28
                    // note that we don't exclude cases where frac_above is too small, as this could be an overexposed image - see testHDR31
                    if( MyDebug.LOG )
                        Log.d(TAG, "too dark/noisy");
                    noisy = true;
                }
                return new LuminanceInfo(i, noisy);
            }
        }
        Log.e(TAG, "computeMedianLuminance failed");
        return new LuminanceInfo(127, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void adjustHistogram(Allocation allocation_in, Allocation allocation_out, int width, int height, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, long time_s) {
        if( MyDebug.LOG )
            Log.d(TAG, "adjustHistogram");
        final boolean adjust_histogram = false;
        //final boolean adjust_histogram = true;

        if( adjust_histogram ) {
            // create histogram
            int [] histogram = new int[256];
            if( MyDebug.LOG )
                Log.d(TAG, "time before creating histogram: " + (System.currentTimeMillis() - time_s));
            Allocation histogramAllocation = computeHistogramAllocation(allocation_in, false, false, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "time after creating histogram: " + (System.currentTimeMillis() - time_s));
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

			/*if( histogramAdjustScript == null ) {
				histogramAdjustScript = new ScriptC_histogram_adjust(rs);
			}*/
            ScriptC_histogram_adjust histogramAdjustScript = new ScriptC_histogram_adjust(rs);
            histogramAdjustScript.set_c_histogram(histogramAllocation);
            histogramAdjustScript.set_hdr_alpha(hdr_alpha);

            if( MyDebug.LOG )
                Log.d(TAG, "call histogramAdjustScript");
            histogramAdjustScript.forEach_histogram_adjust(allocation_in, allocation_out);
            if( MyDebug.LOG )
                Log.d(TAG, "time after histogramAdjustScript: " + (System.currentTimeMillis() - time_s));
            histogramAllocation.destroy();
        }

        //final boolean adjust_histogram_local = false;
        final boolean adjust_histogram_local = true;

        if( adjust_histogram_local ) {
            // Contrast Limited Adaptive Histogram Equalisation (CLAHE)
            // Note we don't fully equalise the histogram, rather the resultant image is the mid-point of the non-equalised and fully-equalised images
            // See https://en.wikipedia.org/wiki/Adaptive_histogram_equalization#Contrast_Limited_AHE
            // Also see "Adaptive Histogram Equalization and its Variations" ( http://www.cs.unc.edu/Research/MIDAG/pubs/papers/Adaptive%20Histogram%20Equalization%20and%20Its%20Variations.pdf ),
            // Pizer, Amburn, Austin, Cromartie, Geselowitz, Greer, ter Haar Romeny, Zimmerman, Zuiderveld (1987).
            // Also note that if ce_preserve_blacks is true, we apply a modification to this algorithm, see below.

            // create histograms
            Allocation histogramAllocation = Allocation.createSized(rs, Element.I32(rs), 256);
			/*if( histogramScript == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "create histogramScript");
				histogramScript = new ScriptC_histogram_compute(rs);
			}*/
            if( MyDebug.LOG )
                Log.d(TAG, "create histogramScript");
            ScriptC_histogram_compute histogramScript = new ScriptC_histogram_compute(rs);
            if( MyDebug.LOG )
                Log.d(TAG, "bind histogram allocation");
            histogramScript.bind_histogram(histogramAllocation);

            //final int n_tiles_c = 8;
            //final int n_tiles_c = 4;
            //final int n_tiles_c = 1;
            int [] c_histogram = new int[n_tiles*n_tiles*256];
            int [] temp_c_histogram = new int[256];
            for(int i=0;i<n_tiles;i++) {
                double a0 = ((double)i)/(double)n_tiles;
                double a1 = ((double)i+1.0)/(double)n_tiles;
                int start_x = (int)(a0 * width);
                int stop_x = (int)(a1 * width);
                if( stop_x == start_x )
                    continue;
                for(int j=0;j<n_tiles;j++) {
                    double b0 = ((double)j)/(double)n_tiles;
                    double b1 = ((double)j+1.0)/(double)n_tiles;
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
                    // We compute a histogram based on the max RGB value, so this matches with the scaling we do in histogram_adjust.rs.
                    // This improves the look of the grass in testHDR24, testHDR27.
                    histogramScript.forEach_histogram_compute_by_value(allocation_in, launch_options);

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
					/*if( MyDebug.LOG ) {
                        Log.d(TAG, "clip_limit: " + clip_limit);
                        Log.d(TAG, "    relative clip limit: " + clip_limit*256.0f/n_pixels);
                    }*/
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
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "updated clip_limit: " + clip_limit);
                            Log.d(TAG, "    relative updated clip limit: " + clip_limit*256.0f/n_pixels);
                        }*/
                    }
                    int n_clipped = 0;
                    for(int x=0;x<256;x++) {
                        if( histogram[x] > clip_limit ) {
                            /*if( MyDebug.LOG ) {
                                Log.d(TAG, "    " + x + " : " + histogram[x] + " : " + (histogram[x]*256.0f/n_pixels));
                            }*/
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

                    if( ce_preserve_blacks ) {
                        // This helps tests such as testHDR52, testHDR57, testAvg26, testAvg30
                        // The basic idea is that we want to avoid making darker pixels darker (by too
                        // much). We do this by adjusting the histogram:
                        // * We can set a minimum value of each histogram value. E.g., if we set all
                        //   pixels up to a certain brightness to a value equal to n_pixels/256, then
                        //   we prevent those pixels from being made darker. In practice, we choose
                        //   a tapered minimum, starting at (n_pixels/256) for black pixels, linearly
                        //   interpolating to no minimum at brightness 128 (dark_threshold_c).
                        // * For any adjusted value of the histogram, we redistribute, by reducing
                        //   the histogram values of brighter pixels with values larger than (n_pixels/256),
                        //   reducing them to a minimum of (n_pixels/256).
                        // * Lastly, we only modify a given histogram value if pixels of that brightness
                        //   would be made darker by the CLAHE algorithm. We can do this by looking at
                        //   the cumulative histogram (as computed before modifying any values).
                        if( MyDebug.LOG ) {
                            for(int x=0;x<256;x++) {
                                Log.d(TAG, "pre-brighten histogram[" + x + "] = " + histogram[x]);
                            }
                        }

                        temp_c_histogram[0] = histogram[0];
                        for(int x=1;x<256;x++) {
                            temp_c_histogram[x] = temp_c_histogram[x-1] + histogram[x];
                        }

                        // avoid making pixels too dark
                        int equal_limit = n_pixels / 256;
                        if( MyDebug.LOG )
                            Log.d(TAG, "equal_limit: " + equal_limit);
                        //final int dark_threshold_c = 64;
                        final int dark_threshold_c = 128;
                        //final int dark_threshold_c = 256;
                        for(int x=0;x<dark_threshold_c;x++) {
                            int c_equal_limit = equal_limit * (x+1);
                            if( temp_c_histogram[x] >= c_equal_limit ) {
                                continue;
                            }
                            float alpha = 1.0f - ((float)x)/((float)dark_threshold_c);
                            //float alpha = 1.0f - ((float)x)/256.0f;
                            int limit = (int)(alpha * equal_limit);
                            //int limit = equal_limit;
                            if( MyDebug.LOG )
                                Log.d(TAG, "x: " + x + " ; limit: " + limit);
                            /*histogram[x] = Math.max(histogram[x], limit);
							if( MyDebug.LOG )
								Log.d(TAG, "    histogram pulled up to: "  + histogram[x]);*/
                            if( histogram[x] < limit ) {
                                // top up by redistributing later values
                                for(int y=x+1;y<256 && histogram[x] < limit;y++) {
                                    if( histogram[y] > equal_limit ) {
                                        int move = histogram[y] - equal_limit;
                                        move = Math.min(move, limit - histogram[x]);
                                        histogram[x] += move;
                                        histogram[y] -= move;
                                    }
                                }
                                if( MyDebug.LOG )
                                    Log.d(TAG, "    histogram pulled up to: "  + histogram[x]);
	                        	/*if( temp_c_histogram[x] >= c_equal_limit )
                        			throw new RuntimeException(); // test*/
                            }
                        }
                    }

                    // compute cumulative histogram
                    int histogram_offset = 256*(i*n_tiles+j);
                    c_histogram[histogram_offset] = histogram[0];
                    for(int x=1;x<256;x++) {
                        c_histogram[histogram_offset+x] = c_histogram[histogram_offset+x-1] + histogram[x];
                    }
                    if( MyDebug.LOG ) {
                        for(int x=0;x<256;x++) {
                            Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " cumulative: " + c_histogram[histogram_offset+x]);
                        }
                    }
                }
            }

            if( MyDebug.LOG )
                Log.d(TAG, "time after creating histograms: " + (System.currentTimeMillis() - time_s));

            Allocation c_histogramAllocation = Allocation.createSized(rs, Element.I32(rs), n_tiles*n_tiles*256);
            c_histogramAllocation.copyFrom(c_histogram);
			/*if( histogramAdjustScript == null ) {
				histogramAdjustScript = new ScriptC_histogram_adjust(rs);
			}*/
            ScriptC_histogram_adjust histogramAdjustScript = new ScriptC_histogram_adjust(rs);
            histogramAdjustScript.set_c_histogram(c_histogramAllocation);
            histogramAdjustScript.set_hdr_alpha(hdr_alpha);
            histogramAdjustScript.set_n_tiles(n_tiles);
            histogramAdjustScript.set_width(width);
            histogramAdjustScript.set_height(height);

            if( MyDebug.LOG )
                Log.d(TAG, "time before histogramAdjustScript: " + (System.currentTimeMillis() - time_s));
            histogramAdjustScript.forEach_histogram_adjust(allocation_in, allocation_out);
            if( MyDebug.LOG )
                Log.d(TAG, "time after histogramAdjustScript: " + (System.currentTimeMillis() - time_s));

            histogramAllocation.destroy();
            c_histogramAllocation.destroy();
        }
    }

    /**
     * @param avg If true, compute the color value as the average of the rgb values. If false,
     *            compute the color value as the maximum of the rgb values.
     * @param floating_point Whether the allocation_in is in floating point (F32_3) format, or
     *                       RGBA_8888 format.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Allocation computeHistogramAllocation(Allocation allocation_in, boolean avg, boolean floating_point, long time_s) {
        if( MyDebug.LOG )
            Log.d(TAG, "computeHistogramAllocation");
        Allocation histogramAllocation = Allocation.createSized(rs, Element.I32(rs), 256);
        //final boolean use_custom_histogram = false;
        final boolean use_custom_histogram = true;
        if( use_custom_histogram ) {
			/*if( histogramScript == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "create histogramScript");
				histogramScript = new ScriptC_histogram_compute(rs);
			}*/
            if( MyDebug.LOG )
                Log.d(TAG, "create histogramScript");
            ScriptC_histogram_compute histogramScript = new ScriptC_histogram_compute(rs);
            if( MyDebug.LOG )
                Log.d(TAG, "bind histogram allocation");
            histogramScript.bind_histogram(histogramAllocation);
            histogramScript.invoke_init_histogram();
            if( MyDebug.LOG )
                Log.d(TAG, "call histogramScript");
            if( MyDebug.LOG )
                Log.d(TAG, "time before histogramScript: " + (System.currentTimeMillis() - time_s));
            if( avg ) {
                if( floating_point )
                    histogramScript.forEach_histogram_compute_by_intensity_f(allocation_in);
                else
                    histogramScript.forEach_histogram_compute_by_intensity(allocation_in);
            }
            else {
                if( floating_point )
                    histogramScript.forEach_histogram_compute_by_value_f(allocation_in);
                else
                    histogramScript.forEach_histogram_compute_by_value(allocation_in);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time after histogramScript: " + (System.currentTimeMillis() - time_s));
        }
        else {
            ScriptIntrinsicHistogram histogramScriptIntrinsic = ScriptIntrinsicHistogram.create(rs, Element.U8_4(rs));
            histogramScriptIntrinsic.setOutput(histogramAllocation);
            if( MyDebug.LOG )
                Log.d(TAG, "call histogramScriptIntrinsic");
            histogramScriptIntrinsic.forEach_Dot(allocation_in); // use forEach_dot(); using forEach would simply compute a histogram for red values!
        }

        //histogramAllocation.setAutoPadding(true);
        return histogramAllocation;
    }

    /**
     * @param avg If true, compute the color value as the average of the rgb values. If false,
     *            compute the color value as the maximum of the rgb values.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int [] computeHistogram(Bitmap bitmap, boolean avg) {
        if( MyDebug.LOG )
            Log.d(TAG, "computeHistogram");
        long time_s = System.currentTimeMillis();
        initRenderscript();
        Allocation allocation_in = Allocation.createFromBitmap(rs, bitmap);
        if( MyDebug.LOG )
            Log.d(TAG, "time after createFromBitmap: " + (System.currentTimeMillis() - time_s));
        int [] histogram = computeHistogram(allocation_in, avg, false);
        allocation_in.destroy();
        freeScripts();
        return histogram;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int [] computeHistogram(Allocation allocation, boolean avg, boolean floating_point) {
        if( MyDebug.LOG )
            Log.d(TAG, "computeHistogram");
        long time_s = System.currentTimeMillis();
        int [] histogram = new int[256];
        Allocation histogramAllocation = computeHistogramAllocation(allocation, avg, floating_point, time_s);
        histogramAllocation.copyTo(histogram);
        histogramAllocation.destroy();
        return histogram;
    }

    static class HistogramInfo {
        final int total;
        final int mean_brightness;
        final int median_brightness;
        final int max_brightness;

        HistogramInfo(int total, int mean_brightness, int median_brightness, int max_brightness) {
            this.total = total;
            this.mean_brightness = mean_brightness;
            this.median_brightness = median_brightness;
            this.max_brightness = max_brightness;
        }
    }

    HistogramInfo getHistogramInfo(int[] histo) {
        int total = 0;
        for(int value : histo)
            total += value;
        int middle = total / 2;
        int count = 0;
        double sum_brightness = 0.0f;
        int median_brightness = -1;
        int max_brightness = 0;
        for(int i = 0; i < histo.length; i++) {
            count += histo[i];
            sum_brightness += (histo[i] * i);
            if( count >= middle && median_brightness == -1 ) {
                median_brightness = i;
            }
            if( histo[i] > 0 ) {
                max_brightness = i;
            }
        }
        int mean_brightness = (int)(sum_brightness/count + 0.1);

        return new HistogramInfo(total, mean_brightness, median_brightness, max_brightness);
    }

    private static int getBrightnessTarget(int brightness, float max_gain_factor, int ideal_brightness) {
        if( brightness <= 0 )
            brightness = 1;
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightness: " + brightness);
            Log.d(TAG, "max_gain_factor: " + max_gain_factor);
            Log.d(TAG, "ideal_brightness: " + ideal_brightness);
        }
        int median_target = Math.min(ideal_brightness, (int)(max_gain_factor*brightness));
        return Math.max(brightness, median_target); // don't make darker
    }

    public static class BrightenFactors {
        public final float gain;
        public final float low_x;
        public final float mid_x;
        public final float gamma;

        BrightenFactors(float gain, float low_x, float mid_x, float gamma) {
            this.gain = gain;
            this.low_x = low_x;
            this.mid_x = mid_x;
            this.gamma = gamma;
        }
    }

    /** Computes various factors used in the avg_brighten.rs script.
     */
    public static BrightenFactors computeBrightenFactors(boolean has_iso_exposure, int iso, long exposure_time, int brightness, int max_brightness) {
        // for outdoor/bright images, don't want max_gain_factor 4, otherwise we lose variation in grass colour in testAvg42
        // and having max_gain_factor at 1.5 prevents testAvg43, testAvg44 being too bright and oversaturated
        // for other images, we also don't want max_gain_factor 4, as makes cases too bright and overblown if it would
        // take the max_possible_value over 255. Especially testAvg46, but also testAvg25, testAvg31, testAvg38,
        // testAvg39
        float max_gain_factor = 1.5f;
        int ideal_brightness = 119;
        if( has_iso_exposure && iso < 1100 && exposure_time < 1000000000L/59 ) {
            // this helps: testAvg12, testAvg21, testAvg35
            // but note we don't want to treat the following as "bright": testAvg17, testAvg23, testAvg36, testAvg37, testAvg50
            ideal_brightness = 199;
        }
        int brightness_target = getBrightnessTarget(brightness, max_gain_factor, ideal_brightness);
        //int max_target = Math.min(255, (int)((max_brightness*brightness_target)/(float)brightness + 0.5f) );
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightness target: " + brightness_target);
            //Log.d(TAG, "max target: " + max_target);
        }

        return computeBrightenFactors(has_iso_exposure, iso, exposure_time, brightness, max_brightness, brightness_target, true);
    }

    /** Computes various factors used in the avg_brighten.rs script.
     */
    private static BrightenFactors computeBrightenFactors(boolean has_iso_exposure, int iso, long exposure_time, int brightness, int max_brightness, int brightness_target, boolean brighten_only) {
        /* We use a combination of gain and gamma to brighten images if required. Gain works best for
         * dark images (e.g., see testAvg8), gamma works better for bright images (e.g., testAvg12).
         */
        if( brightness <= 0 )
            brightness = 1;
        float gain = brightness_target / (float)brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "gain " + gain);
        if( gain < 1.0f && brighten_only ) {
            gain = 1.0f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "clamped gain to: " + gain);
            }
        }
        float gamma = 1.0f;
        float max_possible_value = gain*max_brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "max_possible_value: " + max_possible_value);

		/*if( max_possible_value > 255.0f ) {
			gain = 255.0f / max_brightness;
			if( MyDebug.LOG )
				Log.d(TAG, "limit gain to: " + gain);
			// use gamma correction for the remainder
			if( brightness_target > gain * brightness ) {
				gamma = (float) (Math.log(brightness_target / 255.0f) / Math.log(gain * brightness / 255.0f));
			}
		}

		//float gamma = (float)(Math.log(brightness_target/255.0f) / Math.log(brightness/255.0f));
		if( MyDebug.LOG )
			Log.d(TAG, "gamma " + gamma);
		final float min_gamma_non_bright_c = 0.75f;
		//final float min_gamma_non_bright_c = 0.5f;
		if( gamma > 1.0f ) {
			gamma = 1.0f;
			if( MyDebug.LOG ) {
				Log.d(TAG, "clamped gamma to : " + gamma);
			}
		}
		else if( has_iso_exposure && iso > 150 && gamma < min_gamma_non_bright_c ) {
			// too small gamma on non-bright reduces contrast too much (e.g., see testAvg9)
			// however we can't clamp too much, see testAvg28, testAvg32
			gamma = min_gamma_non_bright_c;
			if( MyDebug.LOG ) {
				Log.d(TAG, "clamped gamma to : " + gamma);
			}
		}*/

        float mid_x = 255.5f;
        if( max_possible_value > 255.0f ) {
            if( MyDebug.LOG )
                Log.d(TAG, "use piecewise gain/gamma");
            // use piecewise function with gain and gamma
            // changed from 0.5 to 0.6 to help grass colour variation in testAvg42; also helps testAvg6; using 0.8 helps testAvg46 and testAvg50 further
            //float mid_y = ( has_iso_exposure && iso <= 150 ) ? 0.6f*255.0f : 0.8f*255.0f;
            float mid_y = ( has_iso_exposure && iso < 1100 && exposure_time < 1000000000L/59 ) ? 0.6f*255.0f : 0.8f*255.0f;
            mid_x = mid_y / gain;
            gamma = (float)(Math.log(mid_y/255.0f) / Math.log(mid_x/max_brightness));
        }
        else if( brighten_only && max_possible_value < 255.0f && max_brightness > 0 ) {
            // slightly brightens testAvg17; also brightens testAvg8 to be clearer
            float alt_gain = 255.0f / max_brightness;
            // okay to allow higher max than max_gain_factor, when it isn't going to take us over 255
            alt_gain = Math.min(alt_gain, 4.0f);
            if( MyDebug.LOG )
                Log.d(TAG, "alt_gain: " + alt_gain);
            if( alt_gain > gain ) {
                gain = alt_gain;
                if( MyDebug.LOG )
                    Log.d(TAG, "increased gain to: " + gain);
            }
        }
        float low_x = 0.0f;
        if( has_iso_exposure && iso >= 400 ) {
            // this helps: testAvg10, testAvg28, testAvg31, testAvg33
            //low_x = Math.min(8.0f, 0.125f*mid_x);
            // don't use mid_x directly, otherwise we get unstable behaviour depending on whether we
            // entered "use piecewise gain/gamma" above or not
            // see unit test testBrightenFactors().
            float piecewise_mid_y = 0.5f*255.0f;
            float piecewise_mid_x = piecewise_mid_y / gain;
            low_x = Math.min(8.0f, 0.125f*piecewise_mid_x);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "low_x " + low_x);
            Log.d(TAG, "mid_x " + mid_x);
            Log.d(TAG, "gamma " + gamma);
        }

        return new BrightenFactors(gain, low_x, mid_x, gamma);
    }

    /** Final stage of the noise reduction algorithm.
     *  Note that the returned bitmap will be scaled up by the factor returned by getAvgSampleSize().
     * @param input         The allocation in floating point format.
     * @param width         Width of the input.
     * @param height        Height of the input.
     * @param iso           ISO used for the original images.
     * @param exposure_time Exposure time used for the original images.
     * @return              Resultant bitmap.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Bitmap avgBrighten(Allocation input, int width, int height, int iso, long exposure_time) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "avgBrighten");
            Log.d(TAG, "iso: " + iso);
            Log.d(TAG, "exposure_time: " + exposure_time);
        }
        initRenderscript();

        long time_s = System.currentTimeMillis();

        int [] histo = computeHistogram(input, false, true);
        HistogramInfo histogramInfo = getHistogramInfo(histo);
        int brightness = histogramInfo.median_brightness;
        int max_brightness = histogramInfo.max_brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "### time after computeHistogram: " + (System.currentTimeMillis() - time_s));

        if( MyDebug.LOG ) {
            Log.d(TAG, "median brightness: " + histogramInfo.median_brightness);
            Log.d(TAG, "mean brightness: " + histogramInfo.mean_brightness);
            Log.d(TAG, "max brightness: " + max_brightness);
        }

        BrightenFactors brighten_factors = computeBrightenFactors(true, iso, exposure_time, brightness, max_brightness);
        float gain = brighten_factors.gain;
        float low_x = brighten_factors.low_x;
        float mid_x = brighten_factors.mid_x;
        float gamma = brighten_factors.gamma;

        //float gain = brightness_target / (float)brightness;
		/*float gamma = (float)(Math.log(max_target/(float)brightness_target) / Math.log(max_brightness/(float)brightness));
		float gain = brightness_target / ((float)Math.pow(brightness/255.0f, gamma) * 255.0f);
		if( MyDebug.LOG ) {
			Log.d(TAG, "gamma " + gamma);
			Log.d(TAG, "gain " + gain);
			Log.d(TAG, "gain2 " + max_target / ((float)Math.pow(max_brightness/255.0f, gamma) * 255.0f));
		}*/
		/*float gain = brightness_target / (float)brightness;
		if( MyDebug.LOG ) {
			Log.d(TAG, "gain: " + gain);
		}
		if( gain < 1.0f ) {
			gain = 1.0f;
			if( MyDebug.LOG ) {
				Log.d(TAG, "clamped gain to : " + gain);
			}
		}*/

		/*if( avgBrightenScript == null ) {
			avgBrightenScript = new ScriptC_avg_brighten(rs);
		}*/
        ScriptC_avg_brighten avgBrightenScript = new ScriptC_avg_brighten(rs);
        avgBrightenScript.set_bitmap(input);
        float black_level = 0.0f;
        {
            // quick and dirty dehaze algorithm
            // helps (among others): testAvg1 to testAvg10, testAvg27, testAvg30, testAvg31, testAvg39, testAvg40
            int total = histogramInfo.total;
            int percentile = (int)(total*0.001f);
            int count = 0;
            int darkest_brightness = -1;
            for(int i = 0; i < histo.length; i++) {
                count += histo[i];
                if( count >= percentile && darkest_brightness == -1 ) {
                    darkest_brightness = i;
                }
            }
            black_level = Math.max(black_level, darkest_brightness);
            // don't allow black_level too high for "dark" images, as this can cause problems due to exaggerating noise (e.g.,
            // see testAvg38)
            black_level = Math.min(black_level, iso <= 700 ? 18 : 4);
            if( MyDebug.LOG ) {
                Log.d(TAG, "percentile: " + percentile);
                Log.d(TAG, "darkest_brightness: " + darkest_brightness);
                Log.d(TAG, "black_level is now: " + black_level);
            }
        }
        avgBrightenScript.invoke_setBlackLevel(black_level);

        // use a lower medial filter strength for pixel binned images, so that we don't blur testAvg46 so much (especially sign text)
        float median_filter_strength = (cached_avg_sample_size >= 2) ? 0.5f : 1.0f;
        if( MyDebug.LOG )
            Log.d(TAG, "median_filter_strength: " + median_filter_strength);
        avgBrightenScript.set_median_filter_strength(median_filter_strength);
        avgBrightenScript.invoke_setBrightenParameters(gain, gamma, low_x, mid_x, max_brightness);

		/*float tonemap_scale_c = 255.0f;
		if( MyDebug.LOG )
			Log.d(TAG, "tonemap_scale_c: " + tonemap_scale_c);
		avgBrightenScript.set_tonemap_scale(tonemap_scale_c);

		float max_possible_value = gain*max_brightness;
		if( MyDebug.LOG )
			Log.d(TAG, "max_possible_value: " + max_possible_value);
		if( max_possible_value < 255.0f ) {
			max_possible_value = 255.0f; // don't make dark images too bright
			if( MyDebug.LOG )
				Log.d(TAG, "clamp max_possible_value to: " + max_possible_value);
		}
		float linear_scale = (max_possible_value + tonemap_scale_c) / max_possible_value;
		if( MyDebug.LOG )
			Log.d(TAG, "linear_scale: " + linear_scale);
		avgBrightenScript.set_linear_scale(linear_scale);*/

		/*{
			max_possible_value = max_brightness;
			float tonemap_scale_c = 255.0f;
			if( 255.0f / max_possible_value < ((float)brightness_target)/(float)brightness + brightness_target / 255.0f - 1.0f ) {
				final float tonemap_denom = ((float)brightness_target)/(float)brightness - (255.0f / max_possible_value);
				if( MyDebug.LOG )
					Log.d(TAG, "tonemap_denom: " + tonemap_denom);
				if( tonemap_denom != 0.0f ) // just in case
					tonemap_scale_c = (255.0f - brightness_target) / tonemap_denom;
				//throw new RuntimeException(); // test
			}
			// Higher tonemap_scale_c values means darker results from the Reinhard tonemapping.
			// Colours brighter than 255-tonemap_scale_c will be made darker, colours darker than 255-tonemap_scale_c will be made brighter
			// (tonemap_scale_c==255 means therefore that colours will only be made darker).
			if( MyDebug.LOG )
				Log.d(TAG, "tonemap_scale_c: " + tonemap_scale_c);
			avgBrightenScript.set_tonemap_scale(tonemap_scale_c);

			float linear_scale = (max_possible_value + tonemap_scale_c) / max_possible_value;
			if( MyDebug.LOG )
				Log.d(TAG, "linear_scale: " + linear_scale);
			avgBrightenScript.set_linear_scale(linear_scale);
		}*/

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation allocation_out = Allocation.createFromBitmap(rs, bitmap);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating allocation_out: " + (System.currentTimeMillis() - time_s));

        avgBrightenScript.forEach_avg_brighten_f(input, allocation_out);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after avg_brighten: " + (System.currentTimeMillis() - time_s));

        //if( iso <= 150 ) {
        if( iso < 1100 && exposure_time < 1000000000L/59 ) {
            // for bright scenes, contrast enhancement helps improve the quality of images (especially where we may have both
            // dark and bright regions, e.g., testAvg12); but for dark scenes, it just blows up the noise too much
            // keep n_tiles==1 - get too much contrast enhancement with n_tiles==4 e.g. for testAvg34
            // tests that are better at 25% (median brightness in brackets): testAvg16 (90), testAvg26 (117), testAvg30 (79),
            //     testAvg43 (55), testAvg44 (82)
            // tests that are better at 50%: testAvg12 (8), testAvg13 (38), testAvg15 (10), testAvg18 (39), testAvg19 (37)
            // other tests improved by doing contrast enhancement: testAvg32, testAvg40
            //adjustHistogram(allocation_out, allocation_out, width, height, 0.5f, 4, time_s);
            //adjustHistogram(allocation_out, allocation_out, width, height, 0.25f, 4, time_s);
            //adjustHistogram(allocation_out, allocation_out, width, height, 0.25f, 1, time_s);
            //adjustHistogram(allocation_out, allocation_out, width, height, 0.5f, 1, time_s);
            final int median_lo = 60, median_hi = 35;
            float alpha = (histogramInfo.median_brightness - median_lo) / (float)(median_hi - median_lo);
            alpha = Math.max(alpha, 0.0f);
            alpha = Math.min(alpha, 1.0f);
            float amount = (1.0f-alpha) * 0.25f + alpha * 0.5f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "dro alpha: " + alpha);
                Log.d(TAG, "dro amount: " + amount);
            }
            adjustHistogram(allocation_out, allocation_out, width, height, amount, 1, true, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
        }

        allocation_out.copyTo(bitmap);
        allocation_out.destroy();
        if( MyDebug.LOG )
            Log.d(TAG, "### time after copying to bitmap: " + (System.currentTimeMillis() - time_s));

		/*int sample_size = getAvgSampleSize();
		if( MyDebug.LOG )
			Log.d(TAG, "sample_size: " + sample_size);
		if( sample_size > 1 ) {
			Matrix matrix = new Matrix();
			matrix.postScale(sample_size, sample_size);
			Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
			bitmap.recycle();
			bitmap = new_bitmap;
		}*/

        freeScripts();
        if( MyDebug.LOG )
            Log.d(TAG, "### total time for avgBrighten: " + (System.currentTimeMillis() - time_s));
        return bitmap;
    }

    /**
     * Computes a value for how sharp the image is perceived to be. The higher the value, the
     * sharper the image.
     * @param allocation_in The input allocation.
     * @param width         The width of the allocation.
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private float computeSharpness(Allocation allocation_in, int width, long time_s) {
        if( MyDebug.LOG )
            Log.d(TAG, "computeSharpness");
        if( MyDebug.LOG )
            Log.d(TAG, "### time: " + (System.currentTimeMillis() - time_s));
        Allocation sumsAllocation = Allocation.createSized(rs, Element.I32(rs), width);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after createSized: " + (System.currentTimeMillis() - time_s));
		/*if( sharpnessScript == null ) {
			sharpnessScript = new ScriptC_calculate_sharpness(rs);
			if( MyDebug.LOG )
				Log.d(TAG, "### time after create sharpnessScript: " + (System.currentTimeMillis() - time_s));
		}*/
        ScriptC_calculate_sharpness sharpnessScript = new ScriptC_calculate_sharpness(rs);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after create sharpnessScript: " + (System.currentTimeMillis() - time_s));
        if( MyDebug.LOG )
            Log.d(TAG, "bind sums allocation");
        sharpnessScript.bind_sums(sumsAllocation);
        sharpnessScript.set_bitmap(allocation_in);
        sharpnessScript.set_width(width);
        sharpnessScript.invoke_init_sums();
        if( MyDebug.LOG )
            Log.d(TAG, "call sharpnessScript");
        if( MyDebug.LOG )
            Log.d(TAG, "### time before sharpnessScript: " + (System.currentTimeMillis() - time_s));
        sharpnessScript.forEach_calculate_sharpness(allocation_in);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after sharpnessScript: " + (System.currentTimeMillis() - time_s));

        int [] sums = new int[width];
        sumsAllocation.copyTo(sums);
        sumsAllocation.destroy();
        float total_sum = 0.0f;
        for(int i=0;i<width;i++) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "sums[" + i + "] = " + sums[i]);*/
            total_sum += (float)sums[i];
        }
        if( MyDebug.LOG )
            Log.d(TAG, "total_sum: " + total_sum);
        return total_sum;
    }
}
