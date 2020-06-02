package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSInvalidStateException;
import android.renderscript.RenderScript;
import android.renderscript.Script;
//import android.renderscript.ScriptIntrinsicResize;
import android.renderscript.Type;
import androidx.annotation.RequiresApi;
import android.util.Log;

public class PanoramaProcessor {
    private static final String TAG = "PanoramaProcessor";

    private final Context context;
    private final HDRProcessor hdrProcessor;
    private RenderScript rs; // lazily created, so we don't take up resources if application isn't using panorama

    // we lazily create and cache scripts that would otherwise have to be repeatedly created in a single
    // panorama photo
    // these should be set to null in freeScript(), to help garbage collection
    private ScriptC_pyramid_blending pyramidBlendingScript = null;
    private ScriptC_feature_detector featureDetectorScript = null;

    public PanoramaProcessor(Context context, HDRProcessor hdrProcessor) {
        this.context = context;
        this.hdrProcessor = hdrProcessor;
    }

    private void freeScripts() {
        if( MyDebug.LOG )
            Log.d(TAG, "freeScripts");

        pyramidBlendingScript = null;
        featureDetectorScript = null;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Allocation reduceBitmap(ScriptC_pyramid_blending script, Allocation allocation) {
        if( MyDebug.LOG )
            Log.d(TAG, "reduceBitmap");
        int width = allocation.getType().getX();
        int height = allocation.getType().getY();

        Allocation reduced_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.RGBA_8888(rs), width/2, height/2));

        script.set_bitmap(allocation);
        script.forEach_reduce(reduced_allocation, reduced_allocation);

        return reduced_allocation;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Allocation expandBitmap(ScriptC_pyramid_blending script, Allocation allocation) {
        if( MyDebug.LOG )
            Log.d(TAG, "expandBitmap");
        long time_s = 0;
        if( MyDebug.LOG )
            time_s = System.currentTimeMillis();

        int width = allocation.getType().getX();
        int height = allocation.getType().getY();
        Allocation result_allocation;

        Allocation expanded_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.RGBA_8888(rs), 2*width, 2*height));
        if( MyDebug.LOG )
            Log.d(TAG, "### expandBitmap: time after creating expanded_allocation: " + (System.currentTimeMillis() - time_s));

        script.set_bitmap(allocation);
        script.forEach_expand(expanded_allocation, expanded_allocation);
        if( MyDebug.LOG )
            Log.d(TAG, "### expandBitmap: time after expand: " + (System.currentTimeMillis() - time_s));

        final boolean use_blur_2d = false; // faster to do blue as two 1D passes
        if( use_blur_2d ) {
            result_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.RGBA_8888(rs), 2*width, 2*height));
            if( MyDebug.LOG )
                Log.d(TAG, "### expandBitmap: time after creating result_allocation: " + (System.currentTimeMillis() - time_s));
            script.set_bitmap(expanded_allocation);
            script.forEach_blur(expanded_allocation, result_allocation);
            if( MyDebug.LOG )
                Log.d(TAG, "### expandBitmap: time after blur: " + (System.currentTimeMillis() - time_s));
            expanded_allocation.destroy();
            //result_allocation = expanded_allocation;
        }
        else {
            Allocation temp_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.RGBA_8888(rs), 2*width, 2*height));
            if( MyDebug.LOG )
                Log.d(TAG, "### expandBitmap: time after creating temp_allocation: " + (System.currentTimeMillis() - time_s));
            script.set_bitmap(expanded_allocation);
            script.forEach_blur1dX(expanded_allocation, temp_allocation);
            if( MyDebug.LOG )
                Log.d(TAG, "### expandBitmap: time after blur1dX: " + (System.currentTimeMillis() - time_s));

            // now re-use expanded_allocation for the result_allocation
            result_allocation = expanded_allocation;
            script.set_bitmap(temp_allocation);
            script.forEach_blur1dY(temp_allocation, result_allocation);
            if( MyDebug.LOG )
                Log.d(TAG, "### expandBitmap: time after blur1dY: " + (System.currentTimeMillis() - time_s));

            temp_allocation.destroy();
        }

        return result_allocation;
    }

    /** Creates an allocation where each pixel equals the pixel from allocation0 minus the corresponding
     *  pixel from allocation1.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Allocation subtractBitmap(ScriptC_pyramid_blending script, Allocation allocation0, Allocation allocation1) {
        if( MyDebug.LOG )
            Log.d(TAG, "subtractBitmap");
        int width = allocation0.getType().getX();
        int height = allocation0.getType().getY();
        if( allocation1.getType().getX() != width || allocation1.getType().getY() != height ) {
            Log.e(TAG, "allocations of different dimensions");
            throw new RuntimeException();
        }
        Allocation result_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.F32_3(rs), width, height));
        script.set_bitmap(allocation1);
        script.forEach_subtract(allocation0, result_allocation);

        return result_allocation;
    }

    /** Updates allocation0 such that each pixel equals the pixel from allocation0 plus the
     *  corresponding pixel from allocation1.
     *  allocation0 should be of type RGBA_8888, allocation1 should be of type F32_3.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void addBitmap(ScriptC_pyramid_blending script, Allocation allocation0, Allocation allocation1) {
        if( MyDebug.LOG )
            Log.d(TAG, "addBitmap");
        int width = allocation0.getType().getX();
        int height = allocation0.getType().getY();
        if( allocation1.getType().getX() != width || allocation1.getType().getY() != height ) {
            Log.e(TAG, "allocations of different dimensions");
            throw new RuntimeException();
        }
        script.set_bitmap(allocation1);
        script.forEach_add(allocation0, allocation0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private List<Allocation> createGaussianPyramid(ScriptC_pyramid_blending script, Bitmap bitmap, int n_levels) {
        if( MyDebug.LOG )
            Log.d(TAG, "createGaussianPyramid");
        List<Allocation> pyramid = new ArrayList<>();

        Allocation allocation = Allocation.createFromBitmap(rs, bitmap);
        pyramid.add(allocation);
        for(int i=0;i<n_levels;i++ ) {
            allocation = reduceBitmap(script, allocation);
            pyramid.add(allocation);
        }

        return pyramid;
    }

    /** Creates a laplacian pyramid of the supplied bitmap, ordered from bottom to top. The i-th
     *  entry is equal to [G(i) - G'(i+1)], where G(i) is the i-th level of the gaussian pyramid,
     *  and G' is created by expanding a level of the gaussian pyramid; except the last entry
     *  is simply equal to the last (i.e., top) level of the gaussian pyramid.
     *  The allocations are of type floating point (F32_3), except the last which is of type
     *  RGBA_8888.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private List<Allocation> createLaplacianPyramid(ScriptC_pyramid_blending script, Bitmap bitmap, int n_levels, @SuppressWarnings("unused") String name) {
        if( MyDebug.LOG )
            Log.d(TAG, "createLaplacianPyramid");
        long time_s = 0;
        if( MyDebug.LOG )
            time_s = System.currentTimeMillis();

        List<Allocation> gaussianPyramid = createGaussianPyramid(script, bitmap, n_levels);
        if( MyDebug.LOG )
            Log.d(TAG, "### createLaplacianPyramid: time after createGaussianPyramid: " + (System.currentTimeMillis() - time_s));
        /*if( MyDebug.LOG )
		{
			// debug
			savePyramid("gaussian", gaussianPyramid);
		}*/
        List<Allocation> pyramid = new ArrayList<>();

        for(int i=0;i<gaussianPyramid.size()-1;i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "createLaplacianPyramid: i = " + i);
            Allocation this_gauss = gaussianPyramid.get(i);
            Allocation next_gauss = gaussianPyramid.get(i+1);
            Allocation next_gauss_expanded = expandBitmap(script, next_gauss);
            if( MyDebug.LOG )
                Log.d(TAG, "### createLaplacianPyramid: time after expandBitmap for level " + i + ": " + (System.currentTimeMillis() - time_s));
            if( MyDebug.LOG ) {
                Log.d(TAG, "this_gauss: " + this_gauss.getType().getX() + " , " + this_gauss.getType().getY());
                Log.d(TAG, "next_gauss: " + next_gauss.getType().getX() + " , " + next_gauss.getType().getY());
                Log.d(TAG, "next_gauss_expanded: " + next_gauss_expanded.getType().getX() + " , " + next_gauss_expanded.getType().getY());
            }
            /*if( MyDebug.LOG )
			{
				// debug
				saveAllocation(name + "_this_gauss_" + i + ".jpg", this_gauss);
				saveAllocation(name + "_next_gauss_expanded_" + i + ".jpg", next_gauss_expanded);
			}*/
            Allocation difference = subtractBitmap(script, this_gauss, next_gauss_expanded);
            if( MyDebug.LOG )
                Log.d(TAG, "### createLaplacianPyramid: time after subtractBitmap for level " + i + ": " + (System.currentTimeMillis() - time_s));
            /*if( MyDebug.LOG )
			{
				// debug
				saveAllocation(name + "_difference_" + i + ".jpg", difference);
			}*/
            pyramid.add(difference);
            //pyramid.add(this_gauss);

            this_gauss.destroy();
            gaussianPyramid.set(i, null); // to help garbage collection
            next_gauss_expanded.destroy();
            if( MyDebug.LOG )
                Log.d(TAG, "### createLaplacianPyramid: time after level " + i + ": " + (System.currentTimeMillis() - time_s));
        }
        pyramid.add(gaussianPyramid.get(gaussianPyramid.size()-1));

        return pyramid;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap collapseLaplacianPyramid(ScriptC_pyramid_blending script, List<Allocation> pyramid) {
        if( MyDebug.LOG )
            Log.d(TAG, "collapseLaplacianPyramid");

        Allocation allocation = pyramid.get(pyramid.size()-1);
        boolean first = true;
        for(int i=pyramid.size()-2;i>=0;i--) {
            Allocation expanded_allocation = expandBitmap(script, allocation);
            if( !first ) {
                allocation.destroy();
            }
            addBitmap(script, expanded_allocation, pyramid.get(i));
            allocation = expanded_allocation;
            first = false;
        }

        int width = allocation.getType().getX();
        int height = allocation.getType().getY();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        allocation.copyTo(bitmap);
        if( !first ) {
            allocation.destroy();
        }
        return bitmap;
    }

    /** Updates every allocation in pyramid0 to be a blend from the left hand of pyramid0 to the
     *  right hand of pyramid1.
     *  Note that the width of the blend region will be half of the width of each image.
     * @param best_path If non-null, the blend region will follow the supplied best path.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void mergePyramids(ScriptC_pyramid_blending script, List<Allocation> pyramid0, List<Allocation> pyramid1, int [] best_path, int best_path_n_x) {
        if( MyDebug.LOG )
            Log.d(TAG, "mergePyramids");

        if( best_path == null ) {
            best_path = new int[1];
            best_path_n_x = 3;
            best_path[0] = 1;
            //best_path[0] = 2; // test
        }
        if( MyDebug.LOG ) {
            for(int i=0;i<best_path.length;i++)
                Log.d(TAG, "best_path[" + i + "]: " + best_path[i]);
        }
        //Allocation bestPathAllocation = Allocation.createSized(rs, Element.I32(rs), best_path.length);
        //script.bind_best_path(bestPathAllocation);
        //bestPathAllocation.copyFrom(best_path);

        int max_height = 0;
        for(int i=0;i<pyramid0.size();i++) {
            Allocation allocation0 = pyramid0.get(i);
            int height = allocation0.getType().getY();
            max_height = Math.max(max_height, height);
        }

        Allocation interpolatedbestPathAllocation = Allocation.createSized(rs, Element.I32(rs), max_height);
        script.bind_interpolated_best_path(interpolatedbestPathAllocation);
        int [] interpolated_best_path = new int[max_height];

        for(int i=0;i<pyramid0.size();i++) {
            Allocation allocation0 = pyramid0.get(i);
            Allocation allocation1 = pyramid1.get(i);

            int width = allocation0.getType().getX();
            int height = allocation0.getType().getY();
            if( allocation1.getType().getX() != width || allocation1.getType().getY() != height ) {
                Log.e(TAG, "allocations of different dimensions");
                throw new RuntimeException();
            }
            else if( allocation0.getType().getElement().getDataType() != allocation1.getType().getElement().getDataType() ) {
                Log.e(TAG, "allocations of different data types");
                throw new RuntimeException();
            }

            script.set_bitmap(allocation1);

            // when using best_path, we have a narrower region to blend across
            //int blend_window_width = width;
            int blend_window_width = width/2;
            //int blend_width = (i==pyramid0.size()-1) ? blend_window_width : 2;
            int blend_width;
            if( i==pyramid0.size()-1 ) {
                blend_width = blend_window_width;
            }
            else {
                blend_width = 2;
                for(int j=0;j<i;j++) {
                    blend_width *= 2;
                }
                blend_width = Math.min(blend_width, blend_window_width);
            }
            /*int blend_width = blend_window_width;
            for(int j=i;j<pyramid0.size()-1;j++) {
                blend_width /= 2;
            }
            blend_width = Math.max(blend_width, 2);*/
            //blend_width = 1; // test

            //float best_path_x_width = width / (best_path_n_x+1.0f); // width of each "bucket" for the best paths
            //blend_width = Math.min(blend_width, (int)(2.0f*best_path_x_width+0.5f));
            float best_path_y_scale = best_path.length/(float)height;
            /*if( MyDebug.LOG ) {
                Log.d(TAG, "i = " + i);
                Log.d(TAG, "    width: " + width);
                Log.d(TAG, "    blend_width: " + blend_width);
                Log.d(TAG, "    height: " + height);
                //Log.d(TAG, "    best_path_x_width: " + best_path_x_width);
                Log.d(TAG, "    best_path_y_scale: " + best_path_y_scale);
            }*/

            // compute interpolated_best_path
            for(int y=0;y<height;y++) {
                if( false )
                {
                    // no interpolation:
                    int best_path_y_index = (int)((y+0.5f)*best_path_y_scale);
                    int best_path_value = best_path[best_path_y_index];
                    //interpolated_best_path[y] = (int)((best_path_value+1) * best_path_x_width + 0.5f);
                    float alpha = best_path_value / (best_path_n_x-1.0f);
                    float frac = (1.0f - alpha) * 0.25f + alpha * 0.75f;
                    interpolated_best_path[y] = (int)(frac*width + 0.5f);
                    /*if( MyDebug.LOG ) {
                        Log.d(TAG, "    interpolated_best_path[" + y + "]: " + interpolated_best_path[y] + " (best_path_value " + best_path_value + ")");
                    }*/
                }
                //if( false )
                {
                    // linear interpolation
                    float best_path_y_index = ((y+0.5f)*best_path_y_scale);
                    float best_path_value;
                    if( best_path_y_index <= 0.5f ) {
                        best_path_value = best_path[0];
                    }
                    else if( best_path_y_index >= best_path.length-1+0.5f ) {
                        best_path_value = best_path[best_path.length-1];
                    }
                    else {
                        best_path_y_index -= 0.5f;
                        int best_path_y_index_i = (int)best_path_y_index;
                        float linear_alpha = best_path_y_index - best_path_y_index_i;
                        //float alpha = linear_alpha;
                        //final float edge_length = 0.25f;
                        final float edge_length = 0.1f;
                        float alpha;
                        if( linear_alpha < edge_length )
                            alpha = 0.0f;
                        else if( linear_alpha > 1.0f-edge_length )
                            alpha = 1.0f;
                        else
                            alpha = (linear_alpha - edge_length) / (1.0f - 2.0f*edge_length);
                        int prev_best_path = best_path[best_path_y_index_i];
                        int next_best_path = best_path[best_path_y_index_i+1];
                        best_path_value = (1.0f-alpha) * prev_best_path + alpha * next_best_path;
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "    alpha: " + alpha);
                            Log.d(TAG, "    prev_best_path: " + prev_best_path);
                            Log.d(TAG, "    next_best_path: " + next_best_path);
                        }*/
                    }
                    //interpolated_best_path[y] = (int)((best_path_value+1) * best_path_x_width + 0.5f);
                    float alpha = best_path_value / (best_path_n_x-1.0f);
                    float frac = (1.0f - alpha) * 0.25f + alpha * 0.75f;
                    interpolated_best_path[y] = (int)(frac*width + 0.5f);
                    /*if( MyDebug.LOG ) {
                        Log.d(TAG, "    interpolated_best_path[" + y + "]: " + interpolated_best_path[y] + " (best_path_value " + best_path_value + ")");
                    }*/
                }
                if( interpolated_best_path[y] - blend_width/2 < 0 ) {
                    Log.e(TAG, "    interpolated_best_path[" + y + "]: " + interpolated_best_path[y]);
                    Log.e(TAG, "    blend_width: " + blend_width);
                    Log.e(TAG, "    width: " + width);
                    throw new RuntimeException("blend window runs off left hand size");
                }
                else if( interpolated_best_path[y] + blend_width/2 > width ) {
                    Log.e(TAG, "    interpolated_best_path[" + y + "]: " + interpolated_best_path[y]);
                    Log.e(TAG, "    blend_width: " + blend_width);
                    Log.e(TAG, "    width: " + width);
                    throw new RuntimeException("blend window runs off right hand size");
                }
            }
            interpolatedbestPathAllocation.copyFrom(interpolated_best_path);

            script.invoke_setBlendWidth(blend_width, width);
            //script.set_best_path_x_width(best_path_x_width);
            //script.set_best_path_y_scale(best_path.length/(float)height);

            if( allocation0.getType().getElement().getDataType() == Element.DataType.FLOAT_32 ) {
                script.forEach_merge_f(allocation0, allocation0);
            }
            else {
                script.forEach_merge(allocation0, allocation0);
            }
        }

        //bestPathAllocation.destroy();
        interpolatedbestPathAllocation.destroy();
    }

    /** For testing.
     */
    private void saveBitmap(Bitmap bitmap, String name) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/" + name);
            OutputStream outputStream = new FileOutputStream(file);
            if( name.toLowerCase().endsWith(".png") )
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            else
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.close();
            MainActivity mActivity = (MainActivity) context;
            mActivity.getStorageUtils().broadcastFile(file, true, false, true);
        }
        catch(IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void saveAllocation(String name, Allocation allocation) {
        Bitmap bitmap;
        int width = allocation.getType().getX();
        int height = allocation.getType().getY();
        Log.d(TAG, "count: " + allocation.getType().getCount());
        Log.d(TAG, "byte size: " + allocation.getType().getElement().getBytesSize());
        if( allocation.getType().getElement().getDataType() == Element.DataType.FLOAT_32 ) {
            float [] bytes = new float[width*height*4];
            allocation.copyTo(bytes);
            int [] pixels = new int[width*height];
            for(int j=0;j<width*height;j++) {
                float r = bytes[4*j];
                float g = bytes[4*j+1];
                float b = bytes[4*j+2];
                // each value should be from -255 to +255, we compress to be in the range [0, 255]
                int ir = (int)(255.0f * ((r/510.0f) + 0.5f) + 0.5f);
                int ig = (int)(255.0f * ((g/510.0f) + 0.5f) + 0.5f);
                int ib = (int)(255.0f * ((b/510.0f) + 0.5f) + 0.5f);
                ir = Math.max(Math.min(ir, 255), 0);
                ig = Math.max(Math.min(ig, 255), 0);
                ib = Math.max(Math.min(ib, 255), 0);
                pixels[j] = Color.argb(255, ir, ig, ib);
            }
            bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        }
        else if( allocation.getType().getElement().getDataType() == Element.DataType.UNSIGNED_8 ) {
            byte [] bytes = new byte[width*height];
            allocation.copyTo(bytes);
            int [] pixels = new int[width*height];
            for(int j=0;j<width*height;j++) {
                int b = bytes[j];
                if( b < 0 )
                    b += 255;
                pixels[j] = Color.argb(255, b, b, b);
            }
            bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        }
        else {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            allocation.copyTo(bitmap);
        }
        saveBitmap(bitmap, name);
        bitmap.recycle();
    }

    /*@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void savePyramid(String name, List<Allocation> pyramid) {
        for(int i=0;i<pyramid.size();i++) {
            Allocation allocation = pyramid.get(i);
            saveAllocation(name + "_" + i + ".jpg", allocation);
        }
    }*/

    private final static int blend_n_levels = 4; // number of levels used for pyramid blending

    /** Bitmaps passed to blendPyramids must have width and height each a multiple of the value
     *  returned by this function.
     */
    private static int getBlendDimension() {
        return (int)(Math.pow(2.0, blend_n_levels)+0.5);
    }

    /** Returns a bitmap that blends between lhs and rhs, using Laplacian pyramid blending.
     *  Note that the width of the blend region will be half of the width of the image. The blend
     *  region will follow a path in order to minimise the transition between the images.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap blendPyramids(Bitmap lhs, Bitmap rhs) {
        long time_s = 0;
        if( MyDebug.LOG )
            time_s = System.currentTimeMillis();

        if( pyramidBlendingScript == null ) {
            pyramidBlendingScript = new ScriptC_pyramid_blending(rs);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time after creating ScriptC_pyramid_blending: " + (System.currentTimeMillis() - time_s));

        // debug
        /*if( MyDebug.LOG )
        {
            saveBitmap(lhs, "lhs.jpg");
            saveBitmap(rhs, "rhs.jpg");
        }*/
        // debug
        /*if( MyDebug.LOG )
        {
            List<Allocation> lhs_pyramid = createGaussianPyramid(script, lhs, blend_n_levels);
            List<Allocation> rhs_pyramid = createGaussianPyramid(script, rhs, blend_n_levels);
            savePyramid("lhs_gauss", lhs_pyramid);
            savePyramid("rhs_gauss", rhs_pyramid);
            for(Allocation allocation : lhs_pyramid) {
                allocation.destroy();
            }
            for(Allocation allocation : rhs_pyramid) {
                allocation.destroy();
            }
        }*/

        if( lhs.getWidth() != rhs.getWidth() || lhs.getHeight() != rhs.getHeight() ) {
            Log.e(TAG, "lhs/rhs bitmaps of different dimensions");
            throw new RuntimeException();
        }

        final int blend_dimension = getBlendDimension();
        if( lhs.getWidth() % blend_dimension != 0 ) {
            Log.e(TAG, "bitmap width " + lhs.getWidth() + " not a multiple of " + blend_dimension);
            throw new RuntimeException();
        }
        else if( lhs.getHeight() % blend_dimension != 0 ) {
            Log.e(TAG, "bitmap height " + lhs.getHeight() + " not a multiple of " + blend_dimension);
            throw new RuntimeException();
        }

        //final boolean find_best_path = false;
        final boolean find_best_path = true;
        //final int best_path_n_x = 3;
        final int best_path_n_x = 7;
        final int best_path_n_y = 8;
        //final int best_path_n_y = 16;
        int [] best_path = null;
        if( find_best_path ) {
            best_path = new int[best_path_n_y];

            //Bitmap best_path_lhs = lhs;
            //Bitmap best_path_rhs = rhs;
            final int scale_factor = 4;
            Bitmap best_path_lhs = Bitmap.createScaledBitmap(lhs, lhs.getWidth()/scale_factor, lhs.getHeight()/scale_factor, true);
            Bitmap best_path_rhs = Bitmap.createScaledBitmap(rhs, rhs.getWidth()/scale_factor, rhs.getHeight()/scale_factor, true);
            // debug
            /*if( MyDebug.LOG )
            {
                saveBitmap(best_path_lhs, "best_path_lhs.jpg");
                saveBitmap(best_path_rhs, "best_path_rhs.jpg");
            }*/

            Allocation lhs_allocation = Allocation.createFromBitmap(rs, best_path_lhs);
            Allocation rhs_allocation = Allocation.createFromBitmap(rs, best_path_rhs);

            int [] errors = new int[1];
            Allocation errorsAllocation = Allocation.createSized(rs, Element.I32(rs), 1);
            pyramidBlendingScript.bind_errors(errorsAllocation);

            Script.LaunchOptions launch_options = new Script.LaunchOptions();
            if( MyDebug.LOG )
                Log.d(TAG, "### blendPyramids: time after creating allocations for best path: " + (System.currentTimeMillis() - time_s));

            pyramidBlendingScript.set_bitmap(rhs_allocation);

            int window_width = Math.max(2, best_path_lhs.getWidth()/8);
            int start_y = 0, stop_y;
            for(int y=0;y<best_path_n_y;y++) {
                best_path[y] = -1;
                int best_error = -1;

                stop_y = ((y+1) * best_path_lhs.getHeight()) / best_path_n_y;
                launch_options.setY(start_y, stop_y);
                start_y = stop_y; // set for next iteration

                //int start_x = 0, stop_x;
                for(int x=0;x<best_path_n_x;x++) {
                    // windows for computing best path should be centred with the path centres we'll actually take
                    float alpha = ((float)x)/(best_path_n_x-1.0f);
                    float frac = (1.0f - alpha) * 0.25f + alpha * 0.75f;
                    int mid_x = (int)(frac * best_path_lhs.getWidth() + 0.5f);
                    int start_x = mid_x - window_width/2;
                    int stop_x = mid_x + window_width/2;
                    //stop_x = ((x+1) * best_path_lhs.getWidth()) / best_path_n_x;
                    launch_options.setX(start_x, stop_x);
                    //start_x = stop_x; // set for next iteration

                    pyramidBlendingScript.invoke_init_errors();
                    pyramidBlendingScript.forEach_compute_error(lhs_allocation, launch_options);
                    errorsAllocation.copyTo(errors);

                    int this_error = errors[0];
                    if( MyDebug.LOG )
                        Log.d(TAG, "    best_path error[" + x + "][" + y + "]: " + this_error);
                    if( best_path[y] == -1 || this_error < best_error ) {
                        best_path[y] = x;
                        best_error = this_error;
                    }
                }

                //best_path[y] = 1; // test
                //best_path[y] = y % best_path_n_x; // test
                if( MyDebug.LOG )
                    Log.d(TAG, "best_path [" + y + "]: " + best_path[y]);
            }

            lhs_allocation.destroy();
            rhs_allocation.destroy();
            errorsAllocation.destroy();

            if( best_path_lhs != lhs ) {
                best_path_lhs.recycle();
            }
            if( best_path_rhs != rhs ) {
                best_path_rhs.recycle();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "### blendPyramids: time after finding best path: " + (System.currentTimeMillis() - time_s));
        }

        List<Allocation> lhs_pyramid = createLaplacianPyramid(pyramidBlendingScript, lhs, blend_n_levels, "lhs");
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time after createLaplacianPyramid 1st call: " + (System.currentTimeMillis() - time_s));
        List<Allocation> rhs_pyramid = createLaplacianPyramid(pyramidBlendingScript, rhs, blend_n_levels, "rhs");
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time after createLaplacianPyramid 2nd call: " + (System.currentTimeMillis() - time_s));

        // debug
        /*if( MyDebug.LOG )
		{
			savePyramid("lhs_laplacian", lhs_pyramid);
			savePyramid("rhs_laplacian", rhs_pyramid);
		}*/

        // debug
        /*if( MyDebug.LOG )
		{
			Bitmap lhs_collapsed = collapseLaplacianPyramid(script, lhs_pyramid);
			saveBitmap(lhs_collapsed, "lhs_collapsed.jpg");
			Bitmap rhs_collapsed = collapseLaplacianPyramid(script, rhs_pyramid);
			saveBitmap(rhs_collapsed, "rhs_collapsed.jpg");
			lhs_collapsed.recycle();
			rhs_collapsed.recycle();
		}*/

        mergePyramids(pyramidBlendingScript, lhs_pyramid, rhs_pyramid, best_path, best_path_n_x);
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time after mergePyramids: " + (System.currentTimeMillis() - time_s));
        Bitmap merged_bitmap = collapseLaplacianPyramid(pyramidBlendingScript, lhs_pyramid);
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time after collapseLaplacianPyramid: " + (System.currentTimeMillis() - time_s));
        // debug
        /*if( MyDebug.LOG )
        {
            savePyramid("merged_laplacian", lhs_pyramid);
            saveBitmap(merged_bitmap, "merged_bitmap.jpg");
        }*/

        for(Allocation allocation : lhs_pyramid) {
            allocation.destroy();
        }
        for(Allocation allocation : rhs_pyramid) {
            allocation.destroy();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### blendPyramids: time taken: " + (System.currentTimeMillis() - time_s));
        return merged_bitmap;
    }

    private static class FeatureMatch implements Comparable<FeatureMatch> {
        private final int index0, index1;
        private float distance; // from 0 to 1, higher means poorer match

        private FeatureMatch(int index0, int index1) {
            this.index0 = index0;
            this.index1 = index1;
        }

        @Override
        public int compareTo(FeatureMatch that) {
            //return (int)(this.distance - that.distance);
				/*if( this.distance > that.distance )
					return 1;
				else if( this.distance < that.distance )
					return -1;
				else
					return 0;*/
            return Float.compare(this.distance, that.distance);
        }

        @Override
        public boolean equals(Object that) {
            return (that instanceof FeatureMatch) && compareTo((FeatureMatch)that) == 0;
        }
    }

    private static void computeDistancesBetweenMatches(List<FeatureMatch> matches, int st_indx, int nd_indx, int feature_descriptor_radius, @SuppressWarnings("unused") List<Bitmap> bitmaps, int [] pixels0, int [] pixels1) {
        final int wid = 2*feature_descriptor_radius+1;
        final int wid2 = wid*wid;
        for(int indx=st_indx;indx<nd_indx;indx++) {
            FeatureMatch match = matches.get(indx);

				/*float distance = 0;
				for(int dy=-feature_descriptor_radius;dy<=feature_descriptor_radius;dy++) {
					for(int dx=-feature_descriptor_radius;dx<=feature_descriptor_radius;dx++) {
						int pixel0 = bitmaps.get(0).getPixel(point0.x + dx, point0.y + dy);
						int pixel1 = bitmaps.get(1).getPixel(point1.x + dx, point1.y + dy);
						//int value0 = (Color.red(pixel0) + Color.green(pixel0) + Color.blue(pixel0))/3;
						//int value1 = (Color.red(pixel1) + Color.green(pixel1) + Color.blue(pixel1))/3;
						int value0 = (int)(0.3*Color.red(pixel0) + 0.59*Color.green(pixel0) + 0.11*Color.blue(pixel0));
						int value1 = (int)(0.3*Color.red(pixel1) + 0.59*Color.green(pixel1) + 0.11*Color.blue(pixel1));
						int dist2 = value0*value0 + value1+value1;
						distance += ((float)dist2)/65025.0f; // so distance for a given pixel is from 0 to 1
					}
				}
				distance /= (float)wid2; // normalise from 0 to 1
				match.distance = distance;*/

            float fsum = 0, gsum = 0;
            float f2sum = 0, g2sum = 0;
            float fgsum = 0;

            // much faster to read via getPixels() rather than pixel by pixel
            //bitmaps.get(0).getPixels(pixels0, 0, wid, point0.x - feature_descriptor_radius, point0.y - feature_descriptor_radius, wid, wid);
            //bitmaps.get(1).getPixels(pixels1, 0, wid, point1.x - feature_descriptor_radius, point1.y - feature_descriptor_radius, wid, wid);
            //int pixel_idx = 0;

            int pixel_idx0 = match.index0*wid2;
            int pixel_idx1 = match.index1*wid2;

            for(int dy=-feature_descriptor_radius;dy<=feature_descriptor_radius;dy++) {
                for(int dx=-feature_descriptor_radius;dx<=feature_descriptor_radius;dx++) {
                    //int pixel0 = bitmaps.get(0).getPixel(point0.x + dx, point0.y + dy);
                    //int pixel1 = bitmaps.get(1).getPixel(point1.x + dx, point1.y + dy);

                    //int pixel0 = pixels0[pixel_idx];
                    //int pixel1 = pixels1[pixel_idx];
                    //pixel_idx++;

                    //int value0 = (Color.red(pixel0) + Color.green(pixel0) + Color.blue(pixel0))/3;
                    //int value1 = (Color.red(pixel1) + Color.green(pixel1) + Color.blue(pixel1))/3;
                    //int value0 = (int)(0.3*Color.red(pixel0) + 0.59*Color.green(pixel0) + 0.11*Color.blue(pixel0));
                    //int value1 = (int)(0.3*Color.red(pixel1) + 0.59*Color.green(pixel1) + 0.11*Color.blue(pixel1));

                    int value0 = pixels0[pixel_idx0];
                    int value1 = pixels1[pixel_idx1];
                    pixel_idx0++;
                    pixel_idx1++;

                    fsum += value0;
                    f2sum += value0*value0;
                    gsum += value1;
                    g2sum += value1*value1;
                    fgsum += value0*value1;
                }
            }
            float fden = wid2*f2sum - fsum*fsum;
            float f_recip = fden==0 ? 0.0f : 1/ fden;
            float gden = wid2*g2sum - gsum*gsum;
            float g_recip = gden==0 ? 0.0f : 1/ gden;
            float fg_corr = wid2*fgsum-fsum*gsum;
            //if( MyDebug.LOG ) {
            //	Log.d(TAG, "match distance: ");
            //	Log.d(TAG, "    fg_corr: " + fg_corr);
            //	Log.d(TAG, "    fden: " + fden);
            //	Log.d(TAG, "    gden: " + gden);
            //	Log.d(TAG, "    f_recip: " + f_recip);
            //	Log.d(TAG, "    g_recip: " + g_recip);
            //}
            // negate, as we want it so that lower value means better match, and normalise to 0-1
            match.distance = 1.0f-Math.abs((fg_corr*fg_corr*f_recip*g_recip));
        }
    }

    private static class ComputeDistancesBetweenMatchesThread extends Thread {
        private final List<FeatureMatch> matches;
        private final int st_indx;
        private final int nd_indx;
        private final int feature_descriptor_radius;
        private final List<Bitmap> bitmaps;
        private final int [] pixels0;
        private final int [] pixels1;

        ComputeDistancesBetweenMatchesThread(List<FeatureMatch> matches, int st_indx, int nd_indx, int feature_descriptor_radius, List<Bitmap> bitmaps, int [] pixels0, int [] pixels1) {
            this.matches = matches;
            this.st_indx = st_indx;
            this.nd_indx = nd_indx;
            this.feature_descriptor_radius = feature_descriptor_radius;
            this.bitmaps = bitmaps;
            this.pixels0 = pixels0;
            this.pixels1 = pixels1;
        }

        public void run() {
            computeDistancesBetweenMatches(matches, st_indx, nd_indx, feature_descriptor_radius, bitmaps, pixels0, pixels1);
        }
    }

    static class AutoAlignmentByFeatureResult {
        final int offset_x;
        final int offset_y;
        final float rotation;
        final float y_scale;

        AutoAlignmentByFeatureResult(int offset_x, int offset_y, float rotation, float y_scale) {
            this.offset_x = offset_x;
            this.offset_y = offset_y;
            this.rotation = rotation;
            this.y_scale = y_scale;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private AutoAlignmentByFeatureResult autoAlignmentByFeature(int width, int height, List<Bitmap> bitmaps, int debug_index) throws PanoramaProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoAlignmentByFeature");
            Log.d(TAG, "width: " + width);
            Log.d(TAG, "height: " + height);
        }
        long time_s = 0;
        if( MyDebug.LOG )
            time_s = System.currentTimeMillis();
        if( bitmaps.size() != 2 ) {
            Log.e(TAG, "must have 2 bitmaps");
            throw new PanoramaProcessorException(PanoramaProcessorException.INVALID_N_IMAGES);
        }

        initRenderscript();
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after initRenderscript: " + (System.currentTimeMillis() - time_s));
        Allocation [] allocations = new Allocation[bitmaps.size()];
        for(int i=0;i<bitmaps.size();i++) {
            allocations[i] = Allocation.createFromBitmap(rs, bitmaps.get(i));
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after creating allocations: " + (System.currentTimeMillis() - time_s));

        // create RenderScript
		if( featureDetectorScript == null ) {
            featureDetectorScript = new ScriptC_feature_detector(rs);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after create featureDetectorScript: " + (System.currentTimeMillis() - time_s));

        //final int feature_descriptor_radius = 2; // radius of square used to compare features
        final int feature_descriptor_radius = 3; // radius of square used to compare features
        //final int feature_descriptor_radius = 5; // radius of square used to compare features
        Point [][] points_arrays = new Point[2][];

        for(int i=0;i<bitmaps.size();i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "detect features for image: " + i);

            if( MyDebug.LOG )
                Log.d(TAG, "convert to greyscale");
            Allocation gs_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.U8(rs), width, height));
            //createMTBScript.set_out_bitmap(gs_allocation);
            //createMTBScript.forEach_create_greyscale(allocations[i]);
            featureDetectorScript.forEach_create_greyscale(allocations[i], gs_allocation);
            //saveAllocation("gs_bitmap" + debug_index + "_" + i + ".png", gs_allocation);

            if( MyDebug.LOG )
                Log.d(TAG, "compute derivatives");
            Allocation ix_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.U8(rs), width, height));
            Allocation iy_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.U8(rs), width, height));
            featureDetectorScript.set_bitmap(gs_allocation);
            featureDetectorScript.set_bitmap_Ix(ix_allocation);
            featureDetectorScript.set_bitmap_Iy(iy_allocation);
            featureDetectorScript.forEach_compute_derivatives(gs_allocation);

			/*if( MyDebug.LOG ) {
				// debugging
                byte [] bytes_x = new byte[width*height];
                byte [] bytes_y = new byte[width*height];
                ix_allocation.copyTo(bytes_x);
                iy_allocation.copyTo(bytes_y);
                int [] pixels_x = new int[width*height];
                int [] pixels_y = new int[width*height];
				for(int j=0;j<width*height;j++) {
                    int b = bytes_x[j];
                    if( b < 0 )
                        b += 255;
                    pixels_x[j] = Color.argb(255, b, b, b);
                    b = bytes_y[j];
                    if( b < 0 )
                        b += 255;
                    pixels_y[j] = Color.argb(255, b, b, b);
				}
                Bitmap bitmap_x = Bitmap.createBitmap(pixels_x, width, height, Bitmap.Config.ARGB_8888);
                Bitmap bitmap_y = Bitmap.createBitmap(pixels_y, width, height, Bitmap.Config.ARGB_8888);
                File file_x = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/ix_bitmap" + debug_index + "_" + i + ".png");
                File file_y = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/iy_bitmap" + debug_index + "_" + i + ".png");
				try {
                    MainActivity mActivity = (MainActivity) context;

					OutputStream outputStream = new FileOutputStream(file_x);
					bitmap_x.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
					outputStream.close();
					mActivity.getStorageUtils().broadcastFile(file_x, true, false, true);

                    outputStream = new FileOutputStream(file_y);
                    bitmap_y.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    mActivity.getStorageUtils().broadcastFile(file_y, true, false, true);
				}
				catch(IOException e) {
					e.printStackTrace();
				}
                bitmap_x.recycle();
                bitmap_y.recycle();
			}*/

            if( MyDebug.LOG )
                Log.d(TAG, "call corner detector script for image: " + i);
            Allocation strength_allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.F32(rs), width, height));
            featureDetectorScript.set_bitmap(gs_allocation);
            featureDetectorScript.set_bitmap_Ix(ix_allocation);
            featureDetectorScript.set_bitmap_Iy(iy_allocation);
            featureDetectorScript.forEach_corner_detector(gs_allocation, strength_allocation);

			/*if( MyDebug.LOG ) {
				// debugging
				float [] bytes = new float[width*height];
				strength_allocation.copyTo(bytes);
				int [] pixels = new int[width*height];
				float max_value = 0.0f;
				for(int j=0;j<width*height;j++) {
					if( bytes[j] < 1.0f )
						bytes[j] = 0.0f;
					else
						bytes[j] = (float)Math.log10(bytes[j]);
					if( bytes[j] > max_value )
						max_value = bytes[j];
				}
				if( MyDebug.LOG )
					Log.d(TAG, "strength max_value: " + max_value);
				for(int j=0;j<width*height;j++) {
					float value = bytes[j]/max_value;
					int c = (int)(255.0f*value+0.5f);
					if( c > 255 )
						c = 255;
					else if( c < 0 )
						c = 0;
					pixels[j] = Color.argb(255, c, c, c);
				}
				Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
				File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/corner_strength_bitmap" + debug_index + "_" + i + ".jpg");
				try {
					OutputStream outputStream = new FileOutputStream(file);
					bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
					outputStream.close();
					MainActivity mActivity = (MainActivity) context;
					mActivity.getStorageUtils().broadcastFile(file, true, false, true);
				}
				catch(IOException e) {
					e.printStackTrace();
				}
				bitmap.recycle();
			}*/

            ix_allocation.destroy();
            //noinspection UnusedAssignment
            ix_allocation = null;
            iy_allocation.destroy();
            //noinspection UnusedAssignment
            iy_allocation = null;

            if( MyDebug.LOG )
                Log.d(TAG, "find local maxima for image: " + i);
            // reuse gs_allocation (since it's on the same U8 type that we want)
            Allocation local_max_features_allocation = gs_allocation;
            //noinspection UnusedAssignment
            gs_allocation = null;

			/*featureDetectorScript.set_corner_threshold(100000000.0f);
			featureDetectorScript.set_bitmap(strength_allocation);
			featureDetectorScript.forEach_local_maximum(strength_allocation, local_max_features_allocation);
			// collect points
			byte [] bytes = new byte[width*height];
			local_max_features_allocation.copyTo(bytes);
			// find points
			List<Point> points = new ArrayList<>();
			for(int y=feature_descriptor_radius;y<height-feature_descriptor_radius;y++) {
				for(int x=feature_descriptor_radius;x<width-feature_descriptor_radius;x++) {
					int j = y*width + x;
					// remember, bytes are signed!
					if( bytes[j] != 0 ) {
						Point point = new Point(x, y);
						points.add(point);
					}
				}
			}
			points_arrays[i] = points.toArray(new Point[0]);
			*/

            featureDetectorScript.set_bitmap(strength_allocation);
            //final int n_y_chunks = 1;
            final int n_y_chunks = 2;
            //final int n_y_chunks = 3;
            //final int n_y_chunks = 4;
            //final int total_max_corners = 500;
            final int total_max_corners = 200;
            final int max_corners = total_max_corners/n_y_chunks;
            final int min_corners = max_corners/2;
            byte [] bytes = new byte[width*height];

            List<Point> all_points = new ArrayList<>();
            for(int cy=0;cy<n_y_chunks;cy++) {
                if( MyDebug.LOG )
                    Log.d(TAG, ">>> find corners, chunk " + cy + " / " + n_y_chunks);
                float threshold = 5000000.0f;
                // setting a min_threshold fixes testPanorama11, also helps testPanorama1
                // note that this needs to be at least 1250000.0f - at 625000.0f, testPanorama1
                // still has problems and in fact ends up being worse than having no min threshold
                final float min_threshold = 1250000.0f;
                //final float min_threshold = 625000.0f;
                float low_threshold = 0.0f;
                float high_threshold = -1.0f;
                int start_y = (cy*height)/n_y_chunks;
                int stop_y = ((cy+1)*height)/n_y_chunks;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "    start_y: " + start_y);
                    Log.d(TAG, "    stop_y: " + stop_y);
                }
                final int max_iter = 10;
                for(int count=0;;count++) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "### attempt " + count + " try threshold: " + threshold + " [ " + low_threshold + " : " + high_threshold + " ]");
                    featureDetectorScript.set_corner_threshold(threshold);
                    Script.LaunchOptions launch_options = new Script.LaunchOptions();
                    launch_options.setX(0, width);
                    launch_options.setY(start_y, stop_y);
                    featureDetectorScript.forEach_local_maximum(strength_allocation, local_max_features_allocation, launch_options);

                    // collect points
                    local_max_features_allocation.copyTo(bytes);
                    // find points
                    List<Point> points = new ArrayList<>();
                    for(int y=Math.max(start_y, feature_descriptor_radius);y<Math.min(stop_y, height-feature_descriptor_radius);y++) {
                        for(int x=feature_descriptor_radius;x<width-feature_descriptor_radius;x++) {
                            int j = y*width + x;
                            // remember, bytes are signed!
                            if( bytes[j] != 0 ) {
                                Point point = new Point(x, y);
                                points.add(point);
                            }
                        }
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "    " + points.size() + " points");
                    if( points.size() >= min_corners && points.size() <= max_corners ) {
                        all_points.addAll(points);
                        break;
                    }
                    else if( points.size() < min_corners ) {
                        if( threshold <= min_threshold ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "    hit minimum threshold: " + threshold);
                            all_points.addAll(points);
                            break;
                        }
                        else if( count+1 == max_iter ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "    too few points but hit max iterations: " + points.size());
                            all_points.addAll(points);
                            //if( true )
                            //    throw new RuntimeException("too few points: " + points.size()); // test
                            break;
                        }
                        else {
                            high_threshold = threshold;
                            threshold = 0.5f * ( low_threshold + threshold );
                            if( MyDebug.LOG )
                                Log.d(TAG, "    reduced threshold to: " + threshold);
							/*if( low_threshold == 0.0f ) {
								throw new RuntimeException();
							}*/
							/*if( count == 0 ) {
								throw new RuntimeException();
							}*/
                        }
                    }
                    else if( count+1 == max_iter ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "    too many points but hit max iterations: " + points.size());
                        // arbitrarily take a subset
                        points.subList(max_corners,points.size()).clear();
                        all_points.addAll(points);
                        //if( true )
                        //    throw new RuntimeException("too many points: " + points.size()); // test
                        break;
                    }
                    else {
                        low_threshold = threshold;
                        if( high_threshold < 0.0f ) {
                            threshold *= 10.0f;
                        }
                        else
                            threshold = 0.5f * ( threshold + high_threshold );
                        if( MyDebug.LOG )
                            Log.d(TAG, "    increased threshold to: " + threshold);
                    }
                }
            }
            points_arrays[i] = all_points.toArray(new Point[0]);

            if( MyDebug.LOG )
                Log.d(TAG, "### image: " + i + " has " + points_arrays[i].length + " points");

            strength_allocation.destroy();
            //noinspection UnusedAssignment
            strength_allocation = null;

            local_max_features_allocation.destroy();
            //noinspection UnusedAssignment
            local_max_features_allocation = null;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after feature detection: " + (System.currentTimeMillis() - time_s));

        // if we have too few good corners, risk of getting a poor match
        final int min_required_corners = 10;
        if( points_arrays[0].length < min_required_corners || points_arrays[1].length < min_required_corners ) {
            if( MyDebug.LOG )
                Log.d(TAG, "too few points!");
			/*if( true )
				throw new RuntimeException();*/

            // free allocations
            for(int i=0;i<allocations.length;i++) {
                if( allocations[i] != null ) {
                    allocations[i].destroy();
                    allocations[i] = null;
                }
            }

            return new AutoAlignmentByFeatureResult(0, 0, 0.0f, 1.0f);
        }

        // generate candidate matches
        //noinspection UnnecessaryLocalVariable
        final int max_match_dist_x = width;
        final int max_match_dist_y = height/16;
        final int max_match_dist2 = max_match_dist_x*max_match_dist_x + max_match_dist_y*max_match_dist_y;
        if( MyDebug.LOG ) {
            Log.d(TAG, "max_match_dist_x: " + max_match_dist_x);
            Log.d(TAG, "max_match_dist_y: " + max_match_dist_y);
            Log.d(TAG, "max_match_dist2: " + max_match_dist2);
        }
        List<FeatureMatch> matches = new ArrayList<>();
        for(int i=0;i<points_arrays[0].length;i++) {
            int x0 = points_arrays[0][i].x;
            int y0 = points_arrays[0][i].y;
            for(int j=0;j<points_arrays[1].length;j++) {
                int x1 = points_arrays[1][j].x;
                int y1 = points_arrays[1][j].y;
                // only consider a match if close enough in actual distance
                int dx = x1 - x0;
                int dy = y1 - y0;
                int dist2 = dx*dx + dy*dy;
                if( dist2 < max_match_dist2 )
                {
                    FeatureMatch match = new FeatureMatch(i, j);
                    matches.add(match);
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### possible matches: " + matches.size());
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after finding possible matches: " + (System.currentTimeMillis() - time_s));

        // compute distances between matches
        {
            final int wid = 2*feature_descriptor_radius+1;
            final int wid2 = wid*wid;
            int [] pixels0 = new int[points_arrays[0].length*wid2];
            int [] pixels1 = new int[points_arrays[1].length*wid2];
            for(int i=0;i<points_arrays[0].length;i++) {
                int x = points_arrays[0][i].x;
                int y = points_arrays[0][i].y;
                bitmaps.get(0).getPixels(pixels0, i*wid2, wid, x - feature_descriptor_radius, y - feature_descriptor_radius, wid, wid);
            }
            for(int i=0;i<points_arrays[1].length;i++) {
                int x = points_arrays[1][i].x;
                int y = points_arrays[1][i].y;
                bitmaps.get(1).getPixels(pixels1, i*wid2, wid, x - feature_descriptor_radius, y - feature_descriptor_radius, wid, wid);
            }
            // convert to greyscale
            for(int i=0;i<pixels0.length;i++) {
                int pixel = pixels0[i];
                pixels0[i] = (int)(0.3*Color.red(pixel) + 0.59*Color.green(pixel) + 0.11*Color.blue(pixel));
            }
            for(int i=0;i<pixels1.length;i++) {
                int pixel = pixels1[i];
                pixels1[i] = (int)(0.3*Color.red(pixel) + 0.59*Color.green(pixel) + 0.11*Color.blue(pixel));
            }

            final boolean use_smp = true;
            if( use_smp ) {
                // testing shows 2 threads gives slightly better than using more threads, or not using smp
                //int n_threads = Math.min(matches.size(), Runtime.getRuntime().availableProcessors());
                int n_threads = Math.min(matches.size(), 2);
                if( MyDebug.LOG )
                    Log.d(TAG, "n_threads: " + n_threads);
                ComputeDistancesBetweenMatchesThread [] threads = new ComputeDistancesBetweenMatchesThread[n_threads];
                int st_indx = 0;
                for(int i=0;i<n_threads;i++) {
                    int nd_indx = (((i+1)*matches.size())/n_threads);
                    if( MyDebug.LOG )
                        Log.d(TAG, "thread " + i + " from " + st_indx + " to " + nd_indx);
                    threads[i] = new ComputeDistancesBetweenMatchesThread(matches, st_indx, nd_indx, feature_descriptor_radius, bitmaps, pixels0, pixels1);
                    st_indx = nd_indx;
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
                    Log.e(TAG, "ComputeDistancesBetweenMatchesThread threads interrupted");
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "threads completed");
            }
            else {
                int st_indx = 0, nd_indx = matches.size();
				/*final int wid = 2*feature_descriptor_radius+1;
				final int wid2 = wid*wid;
				int [] pixels0 = new int[wid2];
				int [] pixels1 = new int[wid2];*/
                computeDistancesBetweenMatches(matches, st_indx, nd_indx, feature_descriptor_radius, bitmaps, pixels0, pixels1);
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after computing match distances: " + (System.currentTimeMillis() - time_s));

        // sort
        Collections.sort(matches);
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after sorting matches: " + (System.currentTimeMillis() - time_s));
        if( MyDebug.LOG ) {
            FeatureMatch best_match = matches.get(0);
            FeatureMatch worst_match = matches.get(matches.size()-1);
            Log.d(TAG, "best match between " + best_match.index0 + " and " + best_match.index1 + " distance: " + best_match.distance);
            Log.d(TAG, "worst match between " + worst_match.index0 + " and " + worst_match.index1 + " distance: " + worst_match.distance);
        }

        // choose matches
        boolean [] rejected0 = new boolean[points_arrays[0].length];
        boolean [] has_matched0 = new boolean[points_arrays[0].length];
        boolean [] has_matched1 = new boolean[points_arrays[1].length];
        List<FeatureMatch> actual_matches = new ArrayList<>();
        //final int n_matches = (int)(matches.size()*0.25f)+1;
        //for(FeatureMatch match : matches) {
        for(int i=0;i<matches.size();i++){
            FeatureMatch match = matches.get(i);
            if( has_matched0[match.index0] || has_matched1[match.index1] ) {
                continue;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "    match between " + match.index0 + " and " + match.index1 + " distance: " + match.distance);
            }

            // Lowe's test
            boolean found = false;
            boolean reject = false;
            for(int j=i+1;j<matches.size() && !found;j++) {
                FeatureMatch match2 = matches.get(j);
                if( match.index0 == match2.index0 ) {
                    found = true;
                    float ratio = match.distance / match2.distance;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "        next best match for index0 " + match.index0 + " is with " + match2.index1 + " distance: " + match2.distance + " , ratio: " + ratio);
                    }
                    // Need a threshold of 0.8 or less to help testPanorama15 images _5 to _6, otherwise we get too many incorrect
                    // matches in the grass region
                    if( ratio+1.0e-5 > 0.8f ) {
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "        reject due to Lowe's test, ratio: " + ratio);
                        }
                        reject = true;
                    }
                }
            }
            if( reject ) {
                has_matched0[match.index0] = true;
                rejected0[match.index0] = true;
                continue;
            }

            actual_matches.add(match);
            has_matched0[match.index0] = true;
            has_matched1[match.index1] = true;
			/*if( actual_matches.size() == n_matches ) {
				// only use best matches
				break;
			}*/
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after initial matching: " + (System.currentTimeMillis() - time_s));
        if( MyDebug.LOG )
            Log.d(TAG, "### found: " + actual_matches.size() + " matches");
        Log.d(TAG, "### autoAlignmentByFeature: time after finding possible matches: " + (System.currentTimeMillis() - time_s));

        // but now choose only best actual matches
        // using 0.4 rather than 0.7 helps testPanorama15 images _5 to _6, to get rid of incorrect grass matches (together with Lowe's test)
        //int n_matches = (int)(actual_matches.size()*0.1)+1;
        int n_matches = (int)(actual_matches.size()*0.4)+1;
        //int n_matches = (int)(actual_matches.size()*0.7)+1;
        // but don't want too few matches - need at least 4 to get a good transform for testPanorama18, images _2 to _3;
        // and at least 5 to get good transform for testPanorama30_galaxys10e, images _0 to _1
        //final int n_minimum_matches_c = 4;
        final int n_minimum_matches_c = 5;
        /*if( n_matches < n_minimum_matches_c ) {
            throw new RuntimeException("n_matches: " + n_matches);
        }*/
        n_matches = Math.max(n_minimum_matches_c, n_matches);
        if( n_matches < actual_matches.size() )
            actual_matches.subList(n_matches,actual_matches.size()).clear();
        if( MyDebug.LOG )
            Log.d(TAG, "### resized to: " + actual_matches.size() + " actual matches");
        // need to reset has_matched arrays
        has_matched0 = new boolean[points_arrays[0].length];
        has_matched1 = new boolean[points_arrays[1].length];
        for(FeatureMatch match : actual_matches) {
            has_matched0[match.index0] = true;
            has_matched1[match.index1] = true;
            if( MyDebug.LOG )
                Log.d(TAG, "    actual match between " + match.index0 + " and " + match.index1 + " distance: " + match.distance);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after choosing best matches: " + (System.currentTimeMillis() - time_s));

        if( actual_matches.size() == 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "no matches!");
			/*if( true )
				throw new RuntimeException();*/

            // free allocations
            for(int i=0;i<allocations.length;i++) {
                if( allocations[i] != null ) {
                    allocations[i].destroy();
                    allocations[i] = null;
                }
            }

            return new AutoAlignmentByFeatureResult(0, 0, 0.0f, 1.0f);
        }

        final boolean use_ransac = true;
        //final boolean use_ransac = false;
        //final boolean estimate_rotation = false;
        final boolean estimate_rotation = true;
        final boolean estimate_y_scale = false;
        //final boolean estimate_y_scale = true;
        //final boolean estimate_rotation = debug_index < 3;
        boolean use_rotation = false;
        boolean use_y_scale = false;
        final float max_y_scale = 1.05f + 1.0e-5f;

        // needed a larger min_rotation_dist of Math.max(width, height)/4.0f to help testPanorama15 images _5 to _6, otherwise
        // we risk choose matches that are too close, and getting an incorrect rotation
        //final float min_rotation_dist = Math.max(5.0f, Math.max(width, height)/32.0f);
        final float min_rotation_dist = Math.max(5.0f, Math.max(width, height)/4.0f);
        if( MyDebug.LOG )
            Log.d(TAG, "min_rotation_dist: " + min_rotation_dist);
        //final float min_rotation_dist2 = 1.0e-5f;
        final float min_rotation_dist2 = min_rotation_dist*min_rotation_dist;

        List<FeatureMatch> ransac_matches = new ArrayList<>(); // used for debugging: the matches that were used to define the transform
        if( use_ransac ) {
            // RANSAC
            List<FeatureMatch> best_inliers = new ArrayList<>();
            List<FeatureMatch> inliers = new ArrayList<>();
            //final float max_inlier_dist = 2.01f;
            //final float max_inlier_dist = 5.01f;
            //final float max_inlier_dist = 10.01f;
            //final float max_inlier_dist = 20.01f;
            //final float max_inlier_dist = Math.max(10.01f, Math.max(width, height)/100.0f);
            final float max_inlier_dist = Math.max(5.01f, Math.max(width, height)/100.0f);
            //final float max_inlier_dist = Math.max(2.51f, Math.max(width, height)/200.0f);
            //final float max_inlier_dist = Math.max(1.26f, Math.max(width, height)/400.0f);
            if( MyDebug.LOG )
                Log.d(TAG, "max_inlier_dist: " + max_inlier_dist);
            final float max_inlier_dist2 = max_inlier_dist*max_inlier_dist;
            for(int i=0;i<actual_matches.size();i++) {
                FeatureMatch match = actual_matches.get(i);

                {
                    // compute exact translation from the i-th match only
                    int candidate_offset_x = points_arrays[1][match.index1].x - points_arrays[0][match.index0].x;
                    int candidate_offset_y = points_arrays[1][match.index1].y - points_arrays[0][match.index0].y;
                    // find the inliers from this
                    inliers.clear();
                    for(FeatureMatch other_match : actual_matches) {
                        int x0 = points_arrays[0][other_match.index0].x;
                        int y0 = points_arrays[0][other_match.index0].y;
                        int x1 = points_arrays[1][other_match.index1].x;
                        int y1 = points_arrays[1][other_match.index1].y;
                        int transformed_x0 = x0 + candidate_offset_x;
                        int transformed_y0 = y0 + candidate_offset_y;
                        float dx = transformed_x0 - x1;
                        float dy = transformed_y0 - y1;
                        float error2 = dx*dx + dy*dy;
                        if( error2 + 1.0e-5 <= max_inlier_dist2 ) {
                            inliers.add(other_match);
                        }
                    }
                    if( inliers.size() > best_inliers.size() ) {
                        // found an improved model!
                        if( MyDebug.LOG )
                            Log.d(TAG, "match " + i + " gives better translation model: " + inliers.size() + " inliers vs " + best_inliers.size());
                        ransac_matches.clear();
                        ransac_matches.add(match);
                        best_inliers.clear();
                        best_inliers.addAll(inliers);
                        use_rotation = false;
                        use_y_scale = false;
                        if( best_inliers.size() == actual_matches.size() ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "all matches are inliers");
                            // no point trying any further
                            break;
                        }
                    }
                }

                if( estimate_rotation ) {
                    // compute exact rotation and translation
                    // we need two points, so compare to every other point
                    for(int j=0;j<i;j++) {
                        FeatureMatch match2 = actual_matches.get(j);
                        final int c0_x = (points_arrays[0][match.index0].x + points_arrays[0][match2.index0].x)/2;
                        final int c0_y = (points_arrays[0][match.index0].y + points_arrays[0][match2.index0].y)/2;
                        final int c1_x = (points_arrays[1][match.index1].x + points_arrays[1][match2.index1].x)/2;
                        final int c1_y = (points_arrays[1][match.index1].y + points_arrays[1][match2.index1].y)/2;
                        // model is a (scale about c0, followed by) rotation about c0, followed by translation
                        final float dx0 = (points_arrays[0][match.index0].x - points_arrays[0][match2.index0].x);
                        final float dy0 = (points_arrays[0][match.index0].y - points_arrays[0][match2.index0].y);
                        final float dx1 = (points_arrays[1][match.index1].x - points_arrays[1][match2.index1].x);
                        final float dy1 = (points_arrays[1][match.index1].y - points_arrays[1][match2.index1].y);
                        final float mag_sq0 = dx0*dx0 + dy0*dy0;
                        final float mag_sq1 = dx1*dx1 + dy1*dy1;
                        if( mag_sq0 < min_rotation_dist2 || mag_sq1 < min_rotation_dist2 ) {
                            continue;
                        }
                        //final float min_height = 0.25f*height;
                        //final float max_height = 0.75f*height;
                        final float min_height = 0.3f*height;
                        final float max_height = 0.7f*height;
                        if( points_arrays[0][match.index0].y < min_height || points_arrays[0][match.index0].y > max_height ||
                                points_arrays[1][match.index1].y < min_height || points_arrays[1][match.index1].y > max_height ||
                                points_arrays[0][match2.index0].y < min_height || points_arrays[0][match2.index0].y > max_height ||
                                points_arrays[1][match2.index1].y < min_height || points_arrays[1][match2.index1].y > max_height
                                ) {
                            // for testPanorama28 - can get poor rotations if using matches too low or high, as photos more likely to be distorted
                            // also helps testPanorama31, testPanorama34, testPanorama35
                            continue;
                        }

						/*float y_scale = 1.0f;
						boolean found_y_scale = false;
						if( estimate_y_scale && Math.abs(dy0) > min_rotation_dist && Math.abs(dy1) > min_rotation_dist ) {
							y_scale = dy1 / dy0;
							if( y_scale <= max_y_scale && y_scale >= 1.0f/max_y_scale ) {
								found_y_scale = true;
							}
							else {
								y_scale = 1.0f;
							}
							dy0 *= y_scale;
						}*/

                        float angle = (float)(Math.atan2(dy1, dx1) - Math.atan2(dy0, dx0));
                        if( angle < -Math.PI )
                            angle += 2.0f*Math.PI;
                        else if( angle > Math.PI )
                            angle -= 2.0f*Math.PI;
                        if( Math.abs(angle) > 30.0f*Math.PI/180.0f ) {
                            // reject too large angles
                            continue;
                        }
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "ransac: " + i + " , " + j + ": ");
							Log.d(TAG, "    match 0: " + points_arrays[0][match.index0].x + " , " + points_arrays[0][match.index0].y);
							Log.d(TAG, "    match 1: " + points_arrays[1][match.index1].x + " , " + points_arrays[1][match.index1].y);
							Log.d(TAG, "    match2 0: " + points_arrays[0][match2.index0].x + " , " + points_arrays[0][match2.index0].y);
							Log.d(TAG, "    match2 1: " + points_arrays[1][match2.index1].x + " , " + points_arrays[1][match2.index1].y);
							Log.d(TAG, "    y_scale: " + y_scale);
							Log.d(TAG, "    angle: " + angle);
							Log.d(TAG, "    mag0: " + Math.sqrt(mag_sq0));
							Log.d(TAG, "    mag1: " + Math.sqrt(mag_sq1));
						}*/

                        float y_scale = 1.0f;
                        boolean found_y_scale = false;
                        if( estimate_y_scale ) {
                            //int transformed_dx0 = (int)(dx0 * Math.cos(angle) - dy0 * Math.sin(angle));
                            int transformed_dy0 = (int)(dx0 * Math.sin(angle) + dy0 * Math.cos(angle));
                            if( Math.abs(transformed_dy0) > min_rotation_dist && Math.abs(dy1) > min_rotation_dist ) {
                                y_scale = dy1 / transformed_dy0;
                                if( y_scale <= max_y_scale && y_scale >= 1.0f/max_y_scale ) {
                                    found_y_scale = true;
                                }
                                else {
                                    y_scale = 1.0f;
                                }
                            }
                        }

                        // find the inliers from this
                        inliers.clear();
                        for(FeatureMatch other_match : actual_matches) {
                            int x0 = points_arrays[0][other_match.index0].x;
                            int y0 = points_arrays[0][other_match.index0].y;
                            int x1 = points_arrays[1][other_match.index1].x;
                            int y1 = points_arrays[1][other_match.index1].y;
                            x0 -= c0_x;
                            y0 -= c0_y;
                            //y0 *= y_scale;
                            int transformed_x0 = (int)(x0 * Math.cos(angle) - y0 * Math.sin(angle));
                            int transformed_y0 = (int)(x0 * Math.sin(angle) + y0 * Math.cos(angle));
                            transformed_y0 *= y_scale;
                            transformed_x0 += c1_x;
                            transformed_y0 += c1_y;

                            float dx = transformed_x0 - x1;
                            float dy = transformed_y0 - y1;
							/*if( MyDebug.LOG ) {
								if( other_match == match )
									Log.d(TAG, "    ransac on match: " + i + " , " + j + " : " + dx + " , " + dy);
								else if( other_match == match2 )
									Log.d(TAG, "    ransac on match2: " + i + " , " + j + " : " + dx + " , " + dy);
							}*/
                            float error2 = dx*dx + dy*dy;
                            if( error2 + 1.0e-5 <= max_inlier_dist2 ) {
                                inliers.add(other_match);
                            }
                        }

                        if( inliers.size() > best_inliers.size() && inliers.size() >= 5 ) {
                            // found an improved model!
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "match " + i + " gives better rotation model: " + inliers.size() + " inliers vs " + best_inliers.size());
                                Log.d(TAG, "    c0_x: " + c0_x + " , c0_y: " + c0_y);
                                Log.d(TAG, "    c1_x: " + c1_x + " , c1_y: " + c1_y);
                                Log.d(TAG, "    dx0: " + dx0 + " , dy0: " + dy0);
                                Log.d(TAG, "    dx1: " + dx1 + " , dy1: " + dy1);
                                Log.d(TAG, "    rotate by " + angle + " about: " + c0_x + " , " + c0_y);
                                Log.d(TAG, "    y scale by " + y_scale);
                                Log.d(TAG, "    translate by: " + (c1_x-c0_x) + " , " + (c1_y-c0_y));
                            }
                            ransac_matches.clear();
                            ransac_matches.add(match);
                            ransac_matches.add(match2);
                            best_inliers.clear();
                            best_inliers.addAll(inliers);
                            use_rotation = true;
                            use_y_scale = found_y_scale;
                            if( best_inliers.size() == actual_matches.size() ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "all matches are inliers");
                                // no point trying any further
                                break;
                            }
                        }
                    }

                    if( best_inliers.size() == actual_matches.size() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "all matches are inliers");
                        // no point trying any further
                        break;
                    }
                }
            }
            actual_matches = best_inliers;
            if( MyDebug.LOG )
                Log.d(TAG, "### autoAlignmentByFeature: time after RANSAC: " + (System.currentTimeMillis() - time_s));
            if( MyDebug.LOG ) {
                for(FeatureMatch match : actual_matches) {
                    Log.d(TAG, "    after ransac: actual match between " + match.index0 + " and " + match.index1 + " distance: " + match.distance);
                }
            }
        }

        Point [] centres = new Point[2];
        for(int i=0;i<2;i++) {
            centres[i] = new Point();
        }
        for(FeatureMatch match : actual_matches) {
            centres[0].x += points_arrays[0][match.index0].x;
            centres[0].y += points_arrays[0][match.index0].y;
            centres[1].x += points_arrays[1][match.index1].x;
            centres[1].y += points_arrays[1][match.index1].y;
        }
        for(int i=0;i<2;i++) {
            centres[i].x /= actual_matches.size();
            centres[i].y /= actual_matches.size();
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "centres[0]: " + centres[0].x + " , " + centres[0].y);
            Log.d(TAG, "centres[1]: " + centres[1].x + " , " + centres[1].y);
        }

        int offset_x = centres[1].x - centres[0].x;
        int offset_y = centres[1].y - centres[0].y;
        float rotation = 0.0f;
        float y_scale = 1.0f;

        if( estimate_rotation && use_rotation ) {
			/*if( true )
				throw new RuntimeException(); // test*/

            // first compute an ideal y_scale
			/*if( estimate_y_scale && use_y_scale ) {
				float y_scale_sum = 0.0f;
				int n_y_scale = 0;
				for(FeatureMatch match : actual_matches) {
					float d0_y = points_arrays[0][match.index0].y - centres[0].y;
					float d1_y = points_arrays[1][match.index1].y - centres[1].y;
					if( Math.abs(d0_y) > min_rotation_dist && Math.abs(d1_y) > min_rotation_dist ) {
						float this_y_scale = d1_y / d0_y;
						y_scale_sum += this_y_scale;
						n_y_scale++;
						if( MyDebug.LOG )
							Log.d(TAG, "    match has scale: " + this_y_scale);
					}
				}
				if( n_y_scale > 0 ) {
					y_scale = y_scale_sum / n_y_scale;
				}
			}*/

            // compute an ideal rotation for a transformation where we rotate about centres[0], and then translate
            float angle_sum = 0.0f;
            int n_angles = 0;
            for(FeatureMatch match : actual_matches) {
                float dx0 = points_arrays[0][match.index0].x - centres[0].x;
                float dy0 = points_arrays[0][match.index0].y - centres[0].y;
                float dx1 = points_arrays[1][match.index1].x - centres[1].x;
                float dy1 = points_arrays[1][match.index1].y - centres[1].y;
                float mag_sq0 = dx0*dx0 + dy0*dy0;
                float mag_sq1 = dx1*dx1 + dy1*dy1;
                if( mag_sq0 < 1.0e-5 || mag_sq1 < 1.0e-5 ) {
                    continue;
                }

                //dy0 *= y_scale;

                float angle = (float)(Math.atan2(dy1, dx1) - Math.atan2(dy0, dx0));
                if( angle < -Math.PI )
                    angle += 2.0f*Math.PI;
                else if( angle > Math.PI )
                    angle -= 2.0f*Math.PI;
                if( MyDebug.LOG )
                    Log.d(TAG, "    match has angle: " + angle);
                angle_sum += angle;
                n_angles++;
            }
            if( n_angles > 0 ) {
                rotation = angle_sum / n_angles;
            }
            //rotation = 0.0f; // test
            //rotation = (float)(0.125*Math.PI); // test
            //centres[1].x = centres[0].x; // test
            //centres[1].y = centres[0].y; // test
            //offset_x = 0; // test
            //offset_y = 0; // test

            if( estimate_y_scale && use_y_scale ) {
                float y_scale_sum = 0.0f;
                int n_y_scale = 0;
                for(FeatureMatch match : actual_matches) {
                    float dx0 = (points_arrays[0][match.index0].x - centres[0].x);
                    float dy0 = (points_arrays[0][match.index0].y - centres[0].y);
                    //float dx1 = (points_arrays[1][match.index1].x - centres[1].x);
                    float dy1 = (points_arrays[1][match.index1].y - centres[1].y);
                    int transformed_dy0 = (int)(dx0 * Math.sin(rotation) + dy0 * Math.cos(rotation));
                    if( Math.abs(transformed_dy0) > min_rotation_dist && Math.abs(dy1) > min_rotation_dist ) {
                        float this_y_scale = dy1 / transformed_dy0;
                        y_scale_sum += this_y_scale;
                        n_y_scale++;
                        if( MyDebug.LOG )
                            Log.d(TAG, "    match has scale: " + this_y_scale);
                    }
                }
                if( n_y_scale > 0 ) {
                    y_scale = y_scale_sum / n_y_scale;
                }
            }

            // but instead we want to (scale and) rotate about the origin and then translate:
            // R[x-c] + c + d = R[x] + (d + c - R[c])
            // Or with scale:
            // // RS[x-c] + c + d = RS[x] + (d + c - RS[c])
            //float rotated_centre_x = (float)(centres[0].x * Math.cos(rotation) - y_scale * centres[0].y * Math.sin(rotation));
            //float rotated_centre_y = (float)(centres[0].x * Math.sin(rotation) + y_scale * centres[0].y * Math.cos(rotation));
            // SR[x-c] + c + d = SR[x] + (d + c - SR[c])
            float rotated_centre_x = (float)(centres[0].x * Math.cos(rotation) - centres[0].y * Math.sin(rotation));
            float rotated_centre_y = (float)(centres[0].x * Math.sin(rotation) + centres[0].y * Math.cos(rotation));
            rotated_centre_y *= y_scale;
            if( MyDebug.LOG ) {
                Log.d(TAG, "offset_x before rotation: " + offset_x);
                Log.d(TAG, "offset_y before rotation: " + offset_y);
                Log.d(TAG, "rotated_centre: " + rotated_centre_x + " , " + rotated_centre_y);
            }
            offset_x += centres[0].x - rotated_centre_x;
            offset_y += centres[0].y - rotated_centre_y;

        }
        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: time after computing transformation: " + (System.currentTimeMillis() - time_s));
        if( MyDebug.LOG ) {
            Log.d(TAG, "offset_x: " + offset_x);
            Log.d(TAG, "offset_y: " + offset_y);
            Log.d(TAG, "rotation: " + rotation);
            Log.d(TAG, "y_scale: " + y_scale);

            Log.d(TAG, "ransac matches are:");
            for(FeatureMatch match : ransac_matches) {
                int x0 = points_arrays[0][match.index0].x;
                int y0 = points_arrays[0][match.index0].y;
                int x1 = points_arrays[1][match.index1].x;
                int y1 = points_arrays[1][match.index1].y;
                Log.d(TAG, "    index : " + match.index0 + " to " + match.index1);
                Log.d(TAG, "        coords " + x0 + " , " + y0 + " to " + x1 + " , " + y1);
                Log.d(TAG, "        distance: " + match.distance);
            }
        }
		/*if( Math.abs(rotation) > 30.0f*Math.PI/180.0f ) {
			// test
			throw new RuntimeException();
		}*/

        if( false && MyDebug.LOG ) {
            // debug:
            Bitmap bitmap = Bitmap.createBitmap(2*width, height, Bitmap.Config.ARGB_8888);
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            Canvas canvas = new Canvas(bitmap);

            // draw bitmaps
            canvas.drawBitmap(bitmaps.get(0), 0, 0, p);
            canvas.drawBitmap(bitmaps.get(1), width, 0, p);

            // draw feature points
            for(int i=0;i<2;i++) {
                for(int j=0;j<points_arrays[i].length;j++) {
                    int off_x = (i==0) ? 0 : width;
                    boolean was_matched;
                    if( i == 0 ) {
						/*if( MyDebug.LOG )
							Log.d(TAG, "### has_matched0[" + j + "]: " + has_matched0[j]);*/
                        was_matched = has_matched0[j];
                    }
                    else {
						/*if( MyDebug.LOG )
							Log.d(TAG, "### has_matched1[" + j + "]: " + has_matched1[j]);*/
                        was_matched = has_matched1[j];
                    }
					/*if( !was_matched ) {
						continue;
					}*/
					if( i == 0 && rejected0[j] )
                        p.setColor(Color.CYAN);
					else
                        p.setColor(was_matched ? Color.YELLOW : Color.RED);
                    //canvas.drawCircle(points_arrays[i][j].x + off_x, points_arrays[i][j].y, 5.0f, p);
                    canvas.drawRect(points_arrays[i][j].x + off_x - feature_descriptor_radius - 1, points_arrays[i][j].y - feature_descriptor_radius - 1, points_arrays[i][j].x + off_x + feature_descriptor_radius + 1, points_arrays[i][j].y + feature_descriptor_radius + 1, p);
                }
            }
            // draw matches
            for(FeatureMatch match : actual_matches) {
                int x0 = points_arrays[0][match.index0].x;
                int y0 = points_arrays[0][match.index0].y;
                int x1 = points_arrays[1][match.index1].x;
                int y1 = points_arrays[1][match.index1].y;
                p.setColor(ransac_matches.contains(match) ? Color.BLUE : Color.MAGENTA);
                p.setAlpha((int)(255.0f * (1.0f-match.distance)));
                canvas.drawLine(x0, y0, width + x1, y1, p);

                // also draw where the match is actually translated to
                //int t_cx = (int)(x0 * Math.cos(rotation) - y_scale * y0 * Math.sin(rotation));
                //int t_cy = (int)(x0 * Math.sin(rotation) + y_scale * y0 * Math.cos(rotation));
                int t_cx = (int)(x0 * Math.cos(rotation) - y0 * Math.sin(rotation));
                int t_cy = (int)(x0 * Math.sin(rotation) + y0 * Math.cos(rotation));
                t_cy *= y_scale;
                t_cx += offset_x;
                t_cy += offset_y;
                // draw on right hand side
                t_cx += width;
                p.setColor(Color.GREEN);
                canvas.drawPoint(t_cx, t_cy, p);
            }
            p.setAlpha(255);

            // draw centres
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.CYAN);
            p.setAlpha(127);
            for(int i=0;i<2;i++) {
                int off_x = (i==0) ? 0 : width;
                canvas.drawCircle(centres[i].x + off_x, centres[i].y, 5.0f, p);
                // draw the rotation and scale:
                int dir_r_x = 50, dir_r_y = 0;
                int dir_u_x = 0, dir_u_y = -50;
                if( i == 1 ) {
                    // transform
                    dir_r_y *= y_scale;
                    dir_u_y *= y_scale;
                    int n_dir_r_x = (int)(dir_r_x * Math.cos(rotation) - dir_r_y * Math.sin(rotation));
                    int n_dir_r_y = (int)(dir_r_x * Math.sin(rotation) + dir_r_y * Math.cos(rotation));
                    int n_dir_u_x = (int)(dir_u_x * Math.cos(rotation) - dir_u_y * Math.sin(rotation));
                    int n_dir_u_y = (int)(dir_u_x * Math.sin(rotation) + dir_u_y * Math.cos(rotation));
                    dir_r_x = n_dir_r_x;
                    dir_r_y = n_dir_r_y;
                    dir_u_x = n_dir_u_x;
                    dir_u_y = n_dir_u_y;
                }
                canvas.drawLine(centres[i].x + off_x, centres[i].y, centres[i].x + off_x + dir_r_x, centres[i].y + dir_r_y, p);
                canvas.drawLine(centres[i].x + off_x, centres[i].y, centres[i].x + off_x + dir_u_x, centres[i].y + dir_u_y, p);
            }
            // also draw a grid that shows the affect of the offset translation we've chosen
            final int n_x = 3;
            final int n_y = 10;
            p.setColor(Color.BLUE);
            p.setAlpha(127);
            for(int i=0;i<n_x;i++) {
                int cx = (width*(i+1))/(n_x+1);
                for(int j=0;j<n_y;j++) {
                    int cy = (height*(j+1))/(n_y+1);
                    for(int k=0;k<2;k++) {
						/*int off_x = (k==0) ? 0 : width + offset_x;
						int off_y = (k==0) ? 0 : offset_y;
						canvas.drawCircle(cx + off_x, cy + off_y, 5.0f, p);*/
                        int t_cx = cx, t_cy = cy;
                        if( k == 1 ) {
                            // transform
                            //t_cx = (int)(cx * Math.cos(rotation) - y_scale * cy * Math.sin(rotation));
                            //t_cy = (int)(cx * Math.sin(rotation) + y_scale * cy * Math.cos(rotation));
                            t_cx = (int)(cx * Math.cos(rotation) - cy * Math.sin(rotation));
                            t_cy = (int)(cx * Math.sin(rotation) + cy * Math.cos(rotation));
                            t_cy *= y_scale;
                            t_cx += offset_x;
                            t_cy += offset_y;
                            // draw on right hand side
                            t_cx += width;
                        }
                        canvas.drawCircle(t_cx, t_cy, 5.0f, p);
                    }
                }
            }
            p.setAlpha(255);

            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/matched_bitmap_" + debug_index + ".png");
            try {
                OutputStream outputStream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.close();
                MainActivity mActivity = (MainActivity) context;
                mActivity.getStorageUtils().broadcastFile(file, true, false, true);
            }
            catch(IOException e) {
                e.printStackTrace();
            }
            bitmap.recycle();
        }

        // free allocations
        for(int i=0;i<allocations.length;i++) {
            if( allocations[i] != null ) {
                allocations[i].destroy();
                allocations[i] = null;
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### autoAlignmentByFeature: total time: " + (System.currentTimeMillis() - time_s));
        return new AutoAlignmentByFeatureResult(offset_x, offset_y, rotation, y_scale);
    }

    @SuppressWarnings("unused")
    private Bitmap blend_panorama_alpha(Bitmap lhs, Bitmap rhs) {
        int width = lhs.getWidth();
        int height = lhs.getHeight();
        if( width != rhs.getWidth() ) {
            Log.e(TAG, "bitmaps have different widths");
            throw new RuntimeException();
        }
        else if( height != rhs.getHeight() ) {
            Log.e(TAG, "bitmaps have different heights");
            throw new RuntimeException();
        }
        Paint p = new Paint();
        Rect rect = new Rect();
        Bitmap blended_bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas blended_canvas = new Canvas(blended_bitmap);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for(int x=0;x<width;x++) {
            rect.set(x, 0, x+1, height);

            // left hand blend
            // if x=0: frac=1
            // if x=width-1: frac=0
            float frac = (width-1.0f-x)/(width-1.0f);
            p.setAlpha((int)(255.0f*frac));
            blended_canvas.drawBitmap(lhs, rect, rect, p);

            // right hand blend
            // if x=0: frac=0
            // if x=width-1: frac=1
            frac = ((float)x)/(width-1.0f);
            p.setAlpha((int)(255.0f*frac));
            blended_canvas.drawBitmap(rhs, rect, rect, p);
        }
        return blended_bitmap;
    }

    /*private static int nextPowerOf2(int value) {
        int power = 1;
        while( value > power )
            power *= 2;
        return power;
    }*/

    private static int nextMultiple(int value, int multiple) {
        int remainder = value % multiple;
        if( remainder > 0 ) {
            value += multiple - remainder;
        }
        return value;
    }

    private Bitmap createProjectedBitmap(final Rect src_rect_workspace, final Rect dst_rect_workspace, final Bitmap bitmap, final Paint p, final int bitmap_width, final int bitmap_height, final double camera_angle, final int centre_shift_x) {
        Bitmap projected_bitmap = Bitmap.createBitmap(bitmap_width, bitmap_height, Bitmap.Config.ARGB_8888);
        {
            // project
            Canvas projected_canvas = new Canvas(projected_bitmap);
            int prev_x = 0;
            int prev_y0 = -1, prev_y1 = -1;
            for(int x=0;x<bitmap_width;x++) {
                float dx = (float)(x - (bitmap_width/2 + centre_shift_x));
                // rectangular projection:
                //float new_height = bitmap_height * (float)(h / (h * Math.cos(alpha) - dx * Math.sin(alpha)));
                // cylindrical projection:
                float theta = (float)(dx*camera_angle)/(float)bitmap_width;
                float new_height = bitmap_height * (float)Math.cos(theta);

                //float fixed_y_frac = 0.5f;
                //int dst_y0 = (int)(fixed_y_frac*(bitmap_height - new_height) + 0.5f);
                //int dst_y1 = (int)(fixed_y_frac*bitmap_height + (1.0f - fixed_y_frac)*new_height + 0.5f);
                int dst_y0 = (int)((bitmap_height - new_height)/2.0f+0.5f);
                int dst_y1 = (int)((bitmap_height + new_height)/2.0f+0.5f);

                // y_tol: boost performance at the expense of accuracy (but only by up to 1 pixel)
                //final int y_tol = 0;
                final int y_tol = 1;
                if( x == 0 ) {
                    prev_y0 = dst_y0;
                    prev_y1 = dst_y1;
                }
                //else if( dst_y0 != prev_y0 || dst_y1 != prev_y1 ) {
                else if( Math.abs(dst_y0 - prev_y0) > y_tol || Math.abs(dst_y1 - prev_y1) > y_tol ) {
                    src_rect_workspace.set(prev_x, 0, x, bitmap_height);
                    dst_rect_workspace.set(prev_x, dst_y0, x, dst_y1);
                    projected_canvas.drawBitmap(bitmap, src_rect_workspace, dst_rect_workspace, p);
                    prev_x = x;
                    prev_y0 = dst_y0;
                    prev_y1 = dst_y1;
                }

                if( x == bitmap_width-1 ) {
                    // draw last
                    src_rect_workspace.set(prev_x, 0, x+1, bitmap_height);
                    dst_rect_workspace.set(prev_x, dst_y0, x+1, dst_y1);
                    projected_canvas.drawBitmap(bitmap, src_rect_workspace, dst_rect_workspace, p);
                }

                /*src_rect.set(x, 0, x+1, bitmap_height);
                dst_rect.set(x, dst_y0, x+1, dst_y1);

                projected_canvas.drawBitmap(bitmap, src_rect, dst_rect, p);*/
            }
        }
        return projected_bitmap;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void renderPanoramaImage(final int i, final int n_bitmaps, final Rect src_rect_workspace, final Rect dst_rect_workspace,
                                     final Bitmap bitmap, final Paint p, final int bitmap_width, final int bitmap_height,
                                     final int blend_hwidth, final int slice_width, final int offset_x,
                                     final Bitmap panorama, final Canvas canvas, final int crop_x0, final int crop_y0,
                                     final int align_x, final int align_y, final int dst_offset_x, final int shift_stop_x, final int centre_shift_x,
                                     final double camera_angle, long time_s) {
        //float alpha = (float)((camera_angle * i)/panorama_pics_per_screen);
        if( MyDebug.LOG ) {
            //Log.d(TAG, "    alpha: " + alpha + " ( " + Math.toDegrees(alpha) + " degrees )");
            Log.d(TAG, "    align_x: " + align_x);
            Log.d(TAG, "    align_y: " + align_y);
            Log.d(TAG, "    dst_offset_x: " + dst_offset_x);
            Log.d(TAG, "    shift_stop_x: " + shift_stop_x);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### time before projection for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
        Bitmap projected_bitmap = createProjectedBitmap(src_rect_workspace, dst_rect_workspace, bitmap, p, bitmap_width, bitmap_height, camera_angle, centre_shift_x);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after projection for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));

        if( i > 0 && blend_hwidth > 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "### time before blending for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
            // first blend right hand side of previous image with left hand side of new image
            final int blend_dimension = getBlendDimension();

            // ensure we blend images that are a multiple of blend_dimension
            int blend_width = nextMultiple(2*blend_hwidth, blend_dimension);
            int blend_height = nextMultiple(bitmap_height, blend_dimension);
            if( MyDebug.LOG ) {
                Log.d(TAG, "blend_dimension: " + blend_dimension);
                Log.d(TAG, "blend_hwidth: " + blend_hwidth);
                Log.d(TAG, "bitmap_height: " + bitmap_height);
                Log.d(TAG, "blend_width: " + blend_width);
                Log.d(TAG, "blend_height: " + blend_height);
            }

            // Note that we don't handle the crop_x0 and crop_y0 in the same way: for the x crop, it's
            // important to shift the x coordinate of the blend window to match what we'll blend if not
            // cropping. Otherwise we have problems in testPanorama6 and especially testPanorama28
            // (note, due to instability at the time of writing, testPanorama28 issue was reproduced on
            // Nokia 8, but not Samsung Galaxy S10e).
            // For the y crop, there isn't any advantage to shifting.

            //Bitmap lhs = Bitmap.createBitmap(panorama, offset_x + dst_offset_x - blend_hwidth, 0, 2*blend_hwidth, bitmap_height);
            Bitmap lhs = Bitmap.createBitmap(blend_width, blend_height, Bitmap.Config.ARGB_8888);
            {
                Canvas lhs_canvas = new Canvas(lhs);
                src_rect_workspace.set(offset_x + dst_offset_x - blend_hwidth, 0, offset_x + dst_offset_x + blend_hwidth, bitmap_height);
                // n.b., shouldn't shift by align_x, align_y
                src_rect_workspace.offset(-crop_x0, 0);
                dst_rect_workspace.set(0, 0, blend_width, blend_height);
                lhs_canvas.drawBitmap(panorama, src_rect_workspace, dst_rect_workspace, p);
            }

            //Bitmap rhs = Bitmap.createBitmap(projected_bitmap, offset_x - blend_hwidth, 0, 2*blend_hwidth, bitmap_height);
            Bitmap rhs = Bitmap.createBitmap(blend_width, blend_height, Bitmap.Config.ARGB_8888);
            {
                Canvas rhs_canvas = new Canvas(rhs);
                src_rect_workspace.set(offset_x - blend_hwidth, 0, offset_x + blend_hwidth, bitmap_height);
                src_rect_workspace.offset(align_x, align_y);
                dst_rect_workspace.set(0, -crop_y0, blend_width, blend_height-crop_y0);
                rhs_canvas.drawBitmap(projected_bitmap, src_rect_workspace, dst_rect_workspace, p);
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "lhs dimensions: " + lhs.getWidth() + " x " + lhs.getHeight());
                Log.d(TAG, "rhs dimensions: " + rhs.getWidth() + " x " + rhs.getHeight());
            }
            //Bitmap blended_bitmap = blend_panorama_alpha(lhs, rhs);
            Bitmap blended_bitmap = blendPyramids(lhs, rhs);
            /*Bitmap blended_bitmap = Bitmap.createBitmap(2*blend_hwidth, bitmap_height, Bitmap.Config.ARGB_8888);
            Canvas blended_canvas = new Canvas(blended_bitmap);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            for(int x=0;x<2*blend_hwidth;x++) {
                src_rect_workspace.set(x, 0, x+1, bitmap_height);

                // left hand blend
                // if x=0: frac=1
                // if x=2*blend_width-1: frac=0
                float frac = (2.0f*blend_hwidth-1.0f-x)/(2.0f*blend_hwidth-1.0f);
                p.setAlpha((int)(255.0f*frac));
                blended_canvas.drawBitmap(lhs, src_rect_workspace, src_rect_workspace, p);

                // right hand blend
                // if x=0: frac=0
                // if x=2*blend_width-1: frac=1
                frac = ((float)x)/(2.0f*blend_hwidth-1.0f);
                p.setAlpha((int)(255.0f*frac));
                blended_canvas.drawBitmap(rhs, src_rect_workspace, src_rect_workspace, p);
            }
            p.setAlpha(255); // reset
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)); // reset
            */

            // now draw the blended region
            // note it's intentional that we don't shift for crop_y0, see comment above
            canvas.drawBitmap(blended_bitmap, offset_x + dst_offset_x - blend_hwidth - crop_x0, 0, p);

            lhs.recycle();
            rhs.recycle();
            blended_bitmap.recycle();
            if( MyDebug.LOG )
                Log.d(TAG, "### time after blending for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
        }

        int start_x = blend_hwidth;
        int stop_x = slice_width+blend_hwidth;
        if( i == 0 )
            start_x = -offset_x;
        if( i == n_bitmaps-1 ) {
            stop_x = slice_width + offset_x;
            stop_x -= align_x; // to undo the shift of src_rect_workspace by align_x below
        }
        stop_x -= shift_stop_x;
        if( MyDebug.LOG ) {
            Log.d(TAG, "    offset_x: " + offset_x);
            Log.d(TAG, "    dst_offset_x: " + dst_offset_x);
            Log.d(TAG, "    start_x: " + start_x);
            Log.d(TAG, "    stop_x: " + stop_x);
        }

        // draw rest of this image
        if( MyDebug.LOG )
            Log.d(TAG, "### time before drawing non-blended region for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
        src_rect_workspace.set(offset_x + start_x, 0, offset_x + stop_x, bitmap_height);
        src_rect_workspace.offset(align_x, align_y);
        dst_rect_workspace.set(offset_x + dst_offset_x + start_x - crop_x0, -crop_y0, offset_x + dst_offset_x + stop_x - crop_x0, bitmap_height-crop_y0);
        if( MyDebug.LOG ) {
            Log.d(TAG, "    src_rect_workspace: " + src_rect_workspace);
            Log.d(TAG, "    dst_rect_workspace: " + dst_rect_workspace);
        }
        canvas.drawBitmap(projected_bitmap, src_rect_workspace, dst_rect_workspace, p);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after drawing non-blended region for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));

        /*
        int start_x = -blend_hwidth;
        int stop_x = slice_width+blend_hwidth;
        if( i == 0 )
            start_x = -offset_x;
        if( i == bitmaps.size()-1 )
            stop_x = slice_width+offset_x;
        stop_x -= align_x;
        if( MyDebug.LOG ) {
            Log.d(TAG, "    start_x: " + start_x);
            Log.d(TAG, "    stop_x: " + stop_x);
        }

        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        for(int x=start_x;x<stop_x;x++) {
            src_rect_workspace.set(offset_x + x, 0, offset_x + x+1, bitmap_height);
            src_rect_workspace.offset(align_x, align_y);
            dst_rect_workspace.set(offset_x + dst_offset_x + x, 0, offset_x + dst_offset_x + x+1, bitmap_height);

            int blend_alpha = 255;
            if( i > 0 && x < blend_hwidth ) {
                // left hand blend
                //blend_alpha = 127;
                // if x=-blend_hwidth: frac=0
                // if x=blend_hwidth-1: frac=1
                float frac = ((float)x+blend_hwidth)/(2*blend_hwidth-1.0f);
                blend_alpha = (int)(255.0f*frac);
                //if( MyDebug.LOG )
                //Log.d(TAG, "    left hand blend_alpha: " + blend_alpha);
            }
            else if( i < bitmaps.size()-1 && x > stop_x-2*blend_hwidth-1 ) {
                // right hand blend
                //blend_alpha = 127;
                // if x=stop_x-2*blend_hwidth: frac=1
                // if x=stop_x-1: frac=0
                float frac = ((float)stop_x-1-x)/(2*blend_hwidth-1.0f);
                blend_alpha = (int)(255.0f*frac);
                //if( MyDebug.LOG )
                //Log.d(TAG, "    right hand blend_alpha: " + blend_alpha);
            }
            p.setAlpha(blend_alpha);

            //canvas.drawBitmap(bitmap, src_rect_workspace, dst_rect_workspace, p);
            canvas.drawBitmap(projected_bitmap, src_rect_workspace, dst_rect_workspace, p);
        }
        p.setAlpha(255); // reset
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)); // reset
        */

        projected_bitmap.recycle();
        /*if( rotated_bitmap != null ) {
            rotated_bitmap.recycle();
        }*/

        /*float x0 = -slice_width/2;
        float new_height0 = bitmap_height * (float)(h / (h * Math.cos(alpha) - x0 * Math.sin(alpha)));
        if( MyDebug.LOG )
            Log.d(TAG, "    new_height0: " + new_height0);

        float x1 = slice_width/2;
        float new_height1 = bitmap_height * (float)(h / (h * Math.cos(alpha) - x1 * Math.sin(alpha)));
        if( MyDebug.LOG )
            Log.d(TAG, "    new_height1: " + new_height1);

        float src_x0 = 0, src_y0 = 0.0f;
        float src_x1 = 0, src_y1 = bitmap_height;
        float src_x2 = slice_width, src_y2 = 0.0f;
        float src_x3 = slice_width, src_y3 = bitmap_height;

        float dst_x0 = src_x0, dst_y0 = (bitmap_height - new_height0)/2.0f;
        float dst_x1 = src_x1, dst_y1 = (bitmap_height + new_height0)/2.0f;
        float dst_x2 = src_x2, dst_y2 = (bitmap_height - new_height1)/2.0f;
        float dst_x3 = src_x3, dst_y3 = (bitmap_height + new_height1)/2.0f;

        float [] src_points = new float[]{src_x0, src_y0, src_x1, src_y1, src_x2, src_y2, src_x3, src_y3};
        float [] dst_points = new float[]{dst_x0, dst_y0, dst_x1, dst_y1, dst_x2, dst_y2, dst_x3, dst_y3};
        if( MyDebug.LOG ) {
            Log.d(TAG, "    src top-left: " + src_x0 + " , " + src_y0);
            Log.d(TAG, "    src bottom-left: " + src_x1 + " , " + src_y1);
            Log.d(TAG, "    src top-right: " + src_x2 + " , " + src_y2);
            Log.d(TAG, "    src bottom-right: " + src_x3 + " , " + src_y3);
            Log.d(TAG, "    dst top-left: " + dst_x0 + " , " + dst_y0);
            Log.d(TAG, "    dst bottom-left: " + dst_x1 + " , " + dst_y1);
            Log.d(TAG, "    dst top-right: " + dst_x2 + " , " + dst_y2);
            Log.d(TAG, "    dst bottom-right: " + dst_x3 + " , " + dst_y3);
        }

        Matrix matrix = new Matrix();
        if( !matrix.setPolyToPoly(src_points, 0, dst_points, 0, 4) ) {
            Log.e(TAG, "failed to create matrix");
            throw new RuntimeException();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "matrix: " + matrix);

        matrix.postTranslate(i*slice_width, 0.0f);

        Bitmap bitmap_slice = Bitmap.createBitmap(bitmap, (bitmap_width - slice_width)/2, 0, slice_width, bitmap_height);
        canvas.drawBitmap(bitmap_slice, matrix, null);
        bitmap_slice.recycle();
        */
    }

    /**
     * @return Returns the ratio between maximum and minimum computed brightnesses.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private float adjustExposuresLocal(List<Bitmap> bitmaps, int bitmap_width, int bitmap_height, int slice_width, long time_s) {
        final int exposure_hwidth = bitmap_width/10;
        final int offset_x = (bitmap_width - slice_width)/2;

        List<Float> relative_brightness = new ArrayList<>();
        float current_relative_brightness = 1.0f;
        relative_brightness.add(current_relative_brightness);
        float min_relative_brightness = current_relative_brightness;
        float max_relative_brightness = current_relative_brightness;

        if( MyDebug.LOG )
            Log.d(TAG, "### time before computing brightnesses: " + (System.currentTimeMillis() - time_s));

        for(int i=0;i<bitmaps.size()-1;i++) {
            // compute brightness difference between i-th and (i+1)-th images
            Bitmap bitmap_l = bitmaps.get(i);
            Bitmap bitmap_r = bitmaps.get(i+1);
            if( MyDebug.LOG )
                Log.d(TAG, "### time before cropping bitmaps: " + (System.currentTimeMillis() - time_s));

            // scale down for performance
            Matrix scale_matrix = new Matrix();
            scale_matrix.postScale(0.5f, 0.5f);

            //bitmap_l = Bitmap.createBitmap(bitmap_l, offset_x+slice_width-exposure_hwidth, 0, 2*exposure_hwidth, bitmap_height);
            //bitmap_r = Bitmap.createBitmap(bitmap_r, offset_x-exposure_hwidth, 0, 2*exposure_hwidth, bitmap_height);
            bitmap_l = Bitmap.createBitmap(bitmap_l, offset_x+slice_width-exposure_hwidth, 0, 2*exposure_hwidth, bitmap_height, scale_matrix, true);
            bitmap_r = Bitmap.createBitmap(bitmap_r, offset_x-exposure_hwidth, 0, 2*exposure_hwidth, bitmap_height, scale_matrix, true);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after cropping bitmaps: " + (System.currentTimeMillis() - time_s));
            // debug
            /*if( MyDebug.LOG )
            {
                saveBitmap(bitmap_l, "exposure_bitmap_l.jpg");
                saveBitmap(bitmap_r, "exposure_bitmap_r.jpg");
            }*/

            int [] histo_l = hdrProcessor.computeHistogram(bitmap_l, false);
            HDRProcessor.HistogramInfo histogramInfo_l = hdrProcessor.getHistogramInfo(histo_l);
            int [] histo_r = hdrProcessor.computeHistogram(bitmap_r, false);
            HDRProcessor.HistogramInfo histogramInfo_r = hdrProcessor.getHistogramInfo(histo_r);

            float brightness_scale = ((float)Math.max(histogramInfo_r.median_brightness, 1)) / (float)Math.max(histogramInfo_l.median_brightness, 1);
            current_relative_brightness *= brightness_scale;
            if( MyDebug.LOG ) {
                Log.d(TAG, "compare brightnesses from images " + i + " to " + (i+1) + ":");
                Log.d(TAG, "    left median: " + histogramInfo_l.median_brightness);
                Log.d(TAG, "    right median: " + histogramInfo_r.median_brightness);
                Log.d(TAG, "    brightness_scale: " + brightness_scale);
                Log.d(TAG, "    current_relative_brightness: " + current_relative_brightness);
            }
            relative_brightness.add(current_relative_brightness);

            min_relative_brightness = Math.min(min_relative_brightness, current_relative_brightness);
            max_relative_brightness = Math.max(max_relative_brightness, current_relative_brightness);

            if( bitmap_l != bitmaps.get(i) )
                bitmap_l.recycle();
            if( bitmap_r != bitmaps.get(i+1) )
                bitmap_r.recycle();
        }

        float ratio_brightnesses = (max_relative_brightness/min_relative_brightness);
        if( MyDebug.LOG ) {
            Log.d(TAG, "min_relative_brightness: " + min_relative_brightness);
            Log.d(TAG, "max_relative_brightness: " + max_relative_brightness);
            Log.d(TAG, "ratio of max to min relative brightness: " + ratio_brightnesses);
        }

        /*
        float avg_relative_brightness = 0.0f;
        int count = 0;
        for(float b : relative_brightness) {
            avg_relative_brightness += b;
            count++;
        }
        avg_relative_brightness /= count;
        if( MyDebug.LOG )
            Log.d(TAG, "avg_relative_brightness: " + avg_relative_brightness);
        */

        if( MyDebug.LOG )
            Log.d(TAG, "### time after computing brightnesses: " + (System.currentTimeMillis() - time_s));

        List<HDRProcessor.HistogramInfo> histogramInfos = new ArrayList<>();
        float mean_median_brightness = 0.0f; // mean of the global median brightnesse
        float mean_equalised_brightness = 0.0f; // mean of the brightnesses if all adjusted to match exposure of the first image
        for(int i=0;i<bitmaps.size();i++) {
            Bitmap bitmap = bitmaps.get(i);
            int [] histo = hdrProcessor.computeHistogram(bitmap, false);
            HDRProcessor.HistogramInfo histogramInfo = hdrProcessor.getHistogramInfo(histo);
            histogramInfos.add(histogramInfo);
            mean_median_brightness += histogramInfo.median_brightness;
            float equalised_brightness = histogramInfo.median_brightness/relative_brightness.get(i);
            mean_equalised_brightness += equalised_brightness;
            if( MyDebug.LOG ) {
                Log.d(TAG, "image " + i + " has median brightness " + histogramInfo.median_brightness);
                Log.d(TAG, "    and equalised_brightness " + equalised_brightness);
            }
        }
        mean_median_brightness /= bitmaps.size();
        mean_equalised_brightness /= bitmaps.size();
        if( MyDebug.LOG ) {
            Log.d(TAG, "mean_median_brightness: " + mean_median_brightness);
            Log.d(TAG, "mean_equalised_brightness: " + mean_equalised_brightness);
        }

        float avg_relative_brightness = mean_median_brightness / Math.max(mean_equalised_brightness, 1.0f);

        if( MyDebug.LOG )
            Log.d(TAG, "### time after computing global histograms: " + (System.currentTimeMillis() - time_s));

        float min_preferred_scale = 1000.0f, max_preferred_scale = 0.0f;
        for(int i=0;i<bitmaps.size();i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "    adjust exposure for image: " + i);

            Bitmap bitmap = bitmaps.get(i);
            HDRProcessor.HistogramInfo histogramInfo = histogramInfos.get(i);

            int brightness_target = (int)(histogramInfo.median_brightness*avg_relative_brightness/relative_brightness.get(i) + 0.1f);
            brightness_target = Math.min(255, brightness_target);
            if( MyDebug.LOG ) {
                Log.d(TAG, "    image " + i + " has initial brightness_target: " + brightness_target);
                Log.d(TAG, "    median_brightness: " + histogramInfo.median_brightness);
                Log.d(TAG, "    relative_brightness: " + relative_brightness.get(i));
                Log.d(TAG, "    avg_relative_brightness: " + avg_relative_brightness);
            }

            min_preferred_scale = Math.min(min_preferred_scale, brightness_target/(float)histogramInfo.median_brightness);
            max_preferred_scale = Math.max(max_preferred_scale, brightness_target/(float)histogramInfo.median_brightness);
            int min_brightness = (int)(histogramInfo.median_brightness*0.5f+0.5f);
            int max_brightness = (int)(histogramInfo.median_brightness*2.0f+0.5f);
            int this_brightness_target = brightness_target;
            this_brightness_target = Math.max(this_brightness_target, min_brightness);
            this_brightness_target = Math.min(this_brightness_target, max_brightness);
            if( MyDebug.LOG ) {
                Log.d(TAG, "    brightness_target: " + brightness_target);
                Log.d(TAG, "    preferred brightness scale: " + brightness_target / (float) histogramInfo.median_brightness);
                Log.d(TAG, "    this_brightness_target: " + this_brightness_target);
                Log.d(TAG, "    actual brightness scale: " + this_brightness_target / (float) histogramInfo.median_brightness);
            }

            hdrProcessor.brightenImage(bitmap, histogramInfo.median_brightness, histogramInfo.max_brightness, this_brightness_target);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "min_preferred_scale: " + min_preferred_scale);
            Log.d(TAG, "max_preferred_scale: " + max_preferred_scale);
            Log.d(TAG, "### time after adjusting brightnesses: " + (System.currentTimeMillis() - time_s));
        }
        /*if( min_preferred_scale < 0.5f || max_preferred_scale > 2.0f ) {
            throw new RuntimeException("");
        }*/

        return ratio_brightnesses;
    }

    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void adjustExposures(List<Bitmap> bitmaps, long time_s) {
        List<HDRProcessor.HistogramInfo> histogramInfos = new ArrayList<>();

        float mean_median_brightness = 0.0f;
        List<Integer> median_brightnesses = new ArrayList<>();
        for(int i=0;i<bitmaps.size();i++) {
            Bitmap bitmap = bitmaps.get(i);
            int [] histo = hdrProcessor.computeHistogram(bitmap, false);
            HDRProcessor.HistogramInfo histogramInfo = hdrProcessor.getHistogramInfo(histo);
            histogramInfos.add(histogramInfo);
            mean_median_brightness += histogramInfo.median_brightness;
            median_brightnesses.add(histogramInfo.median_brightness);
            if( MyDebug.LOG ) {
                Log.d(TAG, "image " + i + " has median brightness " + histogramInfo.median_brightness);
            }
        }
        mean_median_brightness /= bitmaps.size();
        final int brightness_target = (int)(mean_median_brightness + 0.1f);
        if( MyDebug.LOG )
            Log.d(TAG, "mean_median_brightness: " + mean_median_brightness);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after computing brightnesses: " + (System.currentTimeMillis() - time_s));
        float min_preferred_scale = 1000.0f, max_preferred_scale = 0.0f;
        for(int i=0;i<bitmaps.size();i++) {
            Bitmap bitmap = bitmaps.get(i);
            HDRProcessor.HistogramInfo histogramInfo = histogramInfos.get(i);
            if( MyDebug.LOG )
                Log.d(TAG, "    adjust exposure for image: " + i);

            /*
            // use local average
            float local_mean_brightness = median_brightnesses.get(i);
            int count = 1;
            if( i > 0 ) {
                local_mean_brightness += median_brightnesses.get(i-1);
                count++;
            }
            if( i < bitmaps.size()-1 ) {
                local_mean_brightness += median_brightnesses.get(i+1);
                count++;
            }
            local_mean_brightness /= count;
            if( MyDebug.LOG )
                Log.d(TAG, "    local_mean_brightness: " + local_mean_brightness);
            final int brightness_target = (int)(local_mean_brightness + 0.1f);
            */

            min_preferred_scale = Math.min(min_preferred_scale, brightness_target/(float)histogramInfo.median_brightness);
            max_preferred_scale = Math.max(max_preferred_scale, brightness_target/(float)histogramInfo.median_brightness);
            int min_brightness = (int)(histogramInfo.median_brightness*2.0f/3.0f+0.5f);
            //int min_brightness = (int)(histogramInfo.median_brightness*1.0f+0.5f);
            int max_brightness = (int)(histogramInfo.median_brightness*1.5f+0.5f);
            int this_brightness_target = brightness_target;
            this_brightness_target = Math.max(this_brightness_target, min_brightness);
            this_brightness_target = Math.min(this_brightness_target, max_brightness);
            if( MyDebug.LOG ) {
                Log.d(TAG, "    brightness_target: " + brightness_target);
                Log.d(TAG, "    preferred brightness scale: " + brightness_target / (float) histogramInfo.median_brightness);
                Log.d(TAG, "    this_brightness_target: " + this_brightness_target);
                Log.d(TAG, "    actual brightness scale: " + this_brightness_target / (float) histogramInfo.median_brightness);
            }

            hdrProcessor.brightenImage(bitmap, histogramInfo.median_brightness, histogramInfo.max_brightness, this_brightness_target);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "min_preferred_scale: " + min_preferred_scale);
            Log.d(TAG, "max_preferred_scale: " + max_preferred_scale);
            Log.d(TAG, "### time after adjusting brightnesses: " + (System.currentTimeMillis() - time_s));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void computePanoramaTransforms(List<Matrix> cumulative_transforms, List<Integer> align_x_values, List<Integer> dst_offset_x_values,
                                           List<Bitmap> bitmaps, final int bitmap_width, final int bitmap_height,
                                           final int offset_x, final int slice_width, final int align_hwidth,
                                           long time_s) throws PanoramaProcessorException {
        Matrix cumulative_transform = new Matrix();
        int align_x = 0, align_y = 0;
        int dst_offset_x = 0;
        List<Integer> align_y_values = new ArrayList<>();

        final boolean use_auto_align = true;
        //final boolean use_auto_align = false;

        for(int i=0;i<bitmaps.size();i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "process bitmap: " + i);

            double angle_z = 0.0;

            if( use_auto_align && i > 0 ) {
                // autoalignment
                List<Bitmap> alignment_bitmaps = new ArrayList<>();
                //alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i-1), offset_x+slice_width-align_hwidth, 0, 2*align_hwidth, bitmap_height) );
                //alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i), offset_x-align_hwidth, 0, 2*align_hwidth, bitmap_height) );
                // tall:
                if( MyDebug.LOG ) {
                    Log.d(TAG, "    align_x: " + align_x);
                    Log.d(TAG, "    offset_x: " + offset_x);
                    Log.d(TAG, "    slice_width: " + slice_width);
                    Log.d(TAG, "    align_x+offset_x+slice_width-align_hwidth: " + (align_x + offset_x + slice_width - align_hwidth));
                    Log.d(TAG, "    bitmap(i-1) width: " + bitmaps.get(i - 1).getWidth());
                }

                //final boolean use_align_by_feature = false;
                final boolean use_align_by_feature = true;
                float align_downsample = 1.0f;
                if( use_align_by_feature ) {
                    // scale height to 520
                    // although in theory the alignment algorithm should work on any size, it is best to standardise, as most testing
                    // was done where input images had height 2080 or 2048, and the alignment images were downscaled by a factor of 4
                    align_downsample = bitmap_height/520.0f;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "downscale by: " + align_downsample);
                        Log.d(TAG, "### time before downscaling creating alignment bitmaps for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
                    }
                    // snap to power of 2
                    for(int k=0,power=1;k<=4;k++,power*=2) {
                        double ratio = power/align_downsample;
                        if( ratio >= 0.95f && ratio <= 1.05f ) {
                            align_downsample = power;
                            if( MyDebug.LOG )
                                Log.d(TAG, "snapped downscale to: " + align_downsample);
                            break;
                        }
                    }
                }

                int align_bitmap_height = (3*bitmap_height)/4;
                if( MyDebug.LOG )
                    Log.d(TAG, "### time before creating alignment bitmaps for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
                // n.b., we add in reverse order, so we find the transformation to map the next image (i) onto the previous image (i-1)
                //alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i), align_x+offset_x-align_hwidth, (bitmap_height-align_bitmap_height)/2, 2*align_hwidth, align_bitmap_height) );
                //alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i-1), align_x+offset_x+slice_width-align_hwidth, (bitmap_height-align_bitmap_height)/2, 2*align_hwidth, align_bitmap_height) );
                Matrix align_scale_matrix = new Matrix();
                align_scale_matrix.postScale(1.0f/align_downsample, 1.0f/align_downsample);
                alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i), align_x+offset_x-align_hwidth, (bitmap_height-align_bitmap_height)/2, 2*align_hwidth, align_bitmap_height, align_scale_matrix, true) );
                alignment_bitmaps.add( Bitmap.createBitmap(bitmaps.get(i-1), align_x+offset_x+slice_width-align_hwidth, (bitmap_height-align_bitmap_height)/2, 2*align_hwidth, align_bitmap_height, align_scale_matrix, true) );
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after creating alignment bitmaps for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));

                /*if( use_align_by_feature ) {
                    Matrix align_scale_matrix = new Matrix();
                    align_scale_matrix.postScale(1.0f/align_downsample, 1.0f/align_downsample);
                    for(int j=0;j<alignment_bitmaps.size();j++) {
                        Bitmap new_bitmap = Bitmap.createBitmap(alignment_bitmaps.get(j), 0, 0, alignment_bitmaps.get(j).getWidth(), alignment_bitmaps.get(j).getHeight(), align_scale_matrix, true);
                        alignment_bitmaps.get(j).recycle();
                        alignment_bitmaps.set(j, new_bitmap);
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "### time after downscaling creating alignment bitmaps for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
                }*/

                // save bitmaps used for alignments
                /*if( MyDebug.LOG ) {
                    for(int j=0;j<alignment_bitmaps.size();j++) {
                        Bitmap alignment_bitmap = alignment_bitmaps.get(j);
                        saveBitmap(alignment_bitmap, "alignment_bitmap_" + i + "_" + j +".png");
                    }
                }*/

                int this_align_x, this_align_y;
                float y_scale = 1.0f;
                if( MyDebug.LOG )
                    Log.d(TAG, "### time before auto-alignment for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
                if( use_align_by_feature ) {
                    PanoramaProcessor.AutoAlignmentByFeatureResult res = autoAlignmentByFeature(alignment_bitmaps.get(0).getWidth(), alignment_bitmaps.get(0).getHeight(), alignment_bitmaps, i);
                    this_align_x = res.offset_x;
                    this_align_y = res.offset_y;
                    angle_z = res.rotation;
                    y_scale = res.y_scale;
                }
                else {
                    final boolean use_mtb = false;
                    //final boolean use_mtb = true;
                    int [] offsets_x = new int[alignment_bitmaps.size()];
                    int [] offsets_y = new int[alignment_bitmaps.size()];
                    hdrProcessor.autoAlignment(offsets_x, offsets_y, alignment_bitmaps.get(0).getWidth(), alignment_bitmaps.get(0).getHeight(), alignment_bitmaps, 0, use_mtb, 8);
                    this_align_x = offsets_x[1];
                    this_align_y = offsets_y[1];
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after auto-alignment for " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
                this_align_x *= align_downsample;
                this_align_y *= align_downsample;
                for(Bitmap alignment_bitmap : alignment_bitmaps) {
                    alignment_bitmap.recycle();
                }
                alignment_bitmaps.clear();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "    this_align_x: " + this_align_x);
                    Log.d(TAG, "    this_align_y: " + this_align_y);
                }

                Matrix this_transform = new Matrix();
                this_transform.postRotate((float)Math.toDegrees(angle_z), align_x+offset_x-align_hwidth, 0);
                this_transform.postScale(1.0f, y_scale);
                this_transform.postTranslate(this_align_x, this_align_y);

                {
                    // first need to shift cumulative_transform so that it's about the origin of the new bitmap
                    cumulative_transform.preTranslate(slice_width, 0.0f);
                    cumulative_transform.postTranslate(-slice_width, 0.0f);

                    cumulative_transform.preConcat(this_transform);
                }

                {
                    /*float [] values = new float[9];
                    cumulative_transform.getValues(values);
                    align_x = - (int)values[Matrix.MTRANS_X];*/

                    float [] points = new float[2];
                    points[0] = bitmap_width/2.0f;
                    points[1] = bitmap_height/2.0f;
                    cumulative_transform.mapPoints(points);
                    float trans_x = points[0] - bitmap_width/2.0f;
                    align_x = - (int)trans_x;
                }

                if( MyDebug.LOG ) {
                    Log.d(TAG, "    align_x is now: " + align_x);
                    Log.d(TAG, "    align_y is now: " + align_y);
                }
            }

            align_x_values.add(align_x);
            align_y_values.add(align_y);
            dst_offset_x_values.add(dst_offset_x);
            cumulative_transforms.add(new Matrix(cumulative_transform));

            {
                dst_offset_x += slice_width;
                // set back to zero after we've saved them, so we don't use them in the later iterations of this loop
                align_x = 0;
                align_y = 0;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "    dst_offset_x is now: " + dst_offset_x);

            if( MyDebug.LOG )
                Log.d(TAG, "### time after processing " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
        }
    }

    /** Typically images will have different rotations. Rather than assuming the first image is the
     *  optimal transform (with no rotation), we rotate the transforms to the mean of the rotations.
     *  This is effectively equivalent to rotating the final image to be hopefully more level.
     */
    private void adjustPanoramaTransforms(List<Bitmap> bitmaps, List<Matrix> cumulative_transforms,
                                          int panorama_width, int slice_width, int bitmap_width, int bitmap_height) {
        float [] values = new float[9];

        float min_rotation = 1000, max_rotation = - 1000;
        float sum_rotation = 0.0f;
        for(int i=0;i<bitmaps.size();i++) {
            cumulative_transforms.get(i).getValues(values);
            // get rotation anticlockwise in degrees - https://stackoverflow.com/questions/12256854/get-the-rotate-value-from-matrix-in-android
            float rotation = (float)Math.toDegrees(Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]));
            if( MyDebug.LOG )
                Log.d(TAG, "bitmap " + i + " has rotation " + rotation + " degrees");
            min_rotation = Math.min(min_rotation, rotation);
            max_rotation = Math.max(max_rotation, rotation);
            sum_rotation += rotation;
        }
        //float mid_rotation = 0.5f*(min_rotation + max_rotation);
        //float mid_rotation = sum_rotation/bitmaps.size();
        if( MyDebug.LOG ) {
            Log.d(TAG, "min_rotation: " + min_rotation + " degrees");
            Log.d(TAG, "max_rotation: " + max_rotation + " degrees");
            //Log.d(TAG, "mid_rotation: " + mid_rotation + " degrees");
        }

        // this method helps testPanorama29
        float [] points = new float[2];
        points[0] = 0.0f;
        points[1] = bitmap_height/2.0f;
        cumulative_transforms.get(0).mapPoints(points);
        float x0 = points[0];
        float y0 = points[1];
        points[0] = bitmap_width-1.0f;
        points[1] = bitmap_height/2.0f;
        cumulative_transforms.get(cumulative_transforms.size()-1).mapPoints(points);
        float x1 = points[0] + (cumulative_transforms.size()-1) * slice_width;
        float y1 = points[1];
        float dx = x1 - x0;
        float dy = y1 - y0;
        float mid_rotation = -(float)Math.toDegrees(Math.atan2(dy, dx));
        if( MyDebug.LOG ) {
            Log.d(TAG, "x0: " + x0);
            Log.d(TAG, "y0: " + y0);
            Log.d(TAG, "x1: " + x1);
            Log.d(TAG, "y1: " + y1);
            Log.d(TAG, "dx: " + dx);
            Log.d(TAG, "dy: " + dy);
            Log.d(TAG, "mid_rotation: " + mid_rotation + " degrees");
        }
        // but don't rotate more than the input transforms - helps testPanorama22
        mid_rotation = Math.max(mid_rotation, min_rotation);
        mid_rotation = Math.min(mid_rotation, max_rotation);
        if( MyDebug.LOG ) {
            Log.d(TAG, "limited mid_rotation to: " + mid_rotation + " degrees");
        }

        // we now apply a rotation of -mid_rotation about what will be the centre of the resultant panoramic image, remembering
        // that each matrix in cumulative_transforms is set up for each input images coordinate space
        for(int i=0;i<bitmaps.size();i++) {
            float centre_x = panorama_width/2.0f - i*slice_width;
            float centre_y = bitmap_height/2.0f;
            // apply a post rotate of mid_rotation clockwise about (centre_x, centre_y)
            cumulative_transforms.get(i).postRotate(mid_rotation, centre_x, centre_y);
            {
                cumulative_transforms.get(i).getValues(values);
                float rotation = (float)Math.toDegrees(Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]));
                if( MyDebug.LOG )
                    Log.d(TAG, "bitmap " + i + " now has rotation " + rotation + " degrees");
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void renderPanorama(List<Bitmap> bitmaps, int bitmap_width, int bitmap_height,
                                List<Matrix> cumulative_transforms, List<Integer> align_x_values, List<Integer> dst_offset_x_values,
                                final int blend_hwidth, final int slice_width, final int offset_x,
                                final Bitmap panorama, final int crop_x0, final int crop_y0,
                                final double camera_angle, long time_s) {

        Rect src_rect = new Rect();
        Rect dst_rect = new Rect();
        //Paint p = new Paint();
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        Canvas canvas = new Canvas(panorama);

        for(int i=0;i<bitmaps.size();i++) {
            if( MyDebug.LOG )
                Log.d(TAG, "render bitmap: " + i);
            Bitmap bitmap = bitmaps.get(i);
            int align_x = align_x_values.get(i);
            //int align_y = align_y_values.get(i);
            int align_y = 0;
            int dst_offset_x = dst_offset_x_values.get(i);

            //noinspection UnusedAssignment
            boolean free_bitmap = false;
            int shift_stop_x = align_x;
            int centre_shift_x;

            {
                final boolean shift_transition = true;
                //final boolean shift_transition = false;
                centre_shift_x = - align_x;
                align_x = 0;
                //align_y = 0;
                if( !shift_transition ) {
                    shift_stop_x = 0;
                }

                if( i != 0 && shift_transition ) {
                    int shift_start_x = align_x_values.get(i-1); // +ve means shift to the left
                    dst_offset_x -= shift_start_x;
                    align_x = - shift_start_x;
                    shift_stop_x -= shift_start_x;
                }

                if( align_x != 0 ) {
                    // Bake the alignment into the transform.
                    // Otherwise we have risk that we can transform the image too far off the bitmap, only to try to undo
                    // that translation via align_x in renderPanoramaImage(), which means we get black regions due to having
                    // lost the parts of the image that were translated too far!
                    // This can show up when the blend_hwidth is sufficiently large, and means we get dark bands on the
                    // resultant image.

                    float [] points = new float[2];
                    points[0] = bitmap_width/2.0f;
                    points[1] = bitmap_height/2.0f;
                    cumulative_transforms.get(i).mapPoints(points);
                    int trans_x = (int)(points[0] - bitmap_width/2.0f);

                    int bake_trans_x = -align_x;
                    // ...but on the last image, we don't want to shift too far off screen, as we'll then chop
                    // off part of the image.
                    // See testPanorama19, where without this fix we lose a bit along the right hand side
                    if( i == bitmaps.size()-1 && trans_x < 0 && bake_trans_x + trans_x > 0 ) {
                        bake_trans_x = - trans_x;
                        //if( true )
                        //    throw new RuntimeException(); // test
                    }

                    cumulative_transforms.get(i).postTranslate(bake_trans_x, 0.0f);
                    //if( MyDebug.LOG )
                    //Log.d(TAG, "centre_shift_x: " + centre_shift_x);
                    //if( MyDebug.LOG )
                    //Log.d(TAG, "    align_x: " + align_x);
                    centre_shift_x += bake_trans_x;
                    //if( MyDebug.LOG )
                    //Log.d(TAG, "new centre_shift_x: " + centre_shift_x);
                    align_x += bake_trans_x;
                }

                {
                    Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap_width, bitmap_height, Bitmap.Config.ARGB_8888);
                    Canvas rotated_canvas = new Canvas(rotated_bitmap);
                    rotated_canvas.save();

                    rotated_canvas.setMatrix(cumulative_transforms.get(i));

                    rotated_canvas.drawBitmap(bitmap, 0, 0, p);
                    rotated_canvas.restore();

                    bitmap = rotated_bitmap;
                    /*if( MyDebug.LOG ) {
                        saveBitmap(bitmap, "transformed_bitmap_" + i + ".jpg");
                    }*/
                    free_bitmap = true;
                }
            }

            renderPanoramaImage(i, bitmaps.size(), src_rect, dst_rect,
                    bitmap, p, bitmap_width, bitmap_height,
                    blend_hwidth, slice_width, offset_x,
                    panorama, canvas, crop_x0, crop_y0,
                    align_x, align_y, dst_offset_x, shift_stop_x, centre_shift_x,
                    camera_angle, time_s);

            if( free_bitmap ) {
                bitmap.recycle();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "### time after rendering " + i + "th bitmap: " + (System.currentTimeMillis() - time_s));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Bitmap panorama(List<Bitmap> bitmaps, float panorama_pics_per_screen, float camera_angle_y, final boolean crop) throws PanoramaProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "panorama");
            Log.d(TAG, "camera_angle_y: " + camera_angle_y);
        }

        long time_s = 0;
        if( MyDebug.LOG )
            time_s = System.currentTimeMillis();

        int bitmap_width = bitmaps.get(0).getWidth();
        int bitmap_height = bitmaps.get(0).getHeight();
        if( MyDebug.LOG ) {
            Log.d(TAG, "bitmap_width: " + bitmap_width);
            Log.d(TAG, "bitmap_height: " + bitmap_height);
        }

        for(int i=1;i<bitmaps.size();i++) {
            Bitmap bitmap = bitmaps.get(i);
            if( bitmap.getWidth() != bitmap_width || bitmap.getHeight() != bitmap_height ) {
                Log.e(TAG, "bitmaps not of equal sizes");
                throw new PanoramaProcessorException(PanoramaProcessorException.UNEQUAL_SIZES);
            }
        }
        /*{
            // test
            for(int i=0;i<bitmaps.size();i++) {
                Bitmap bitmap = bitmaps.get(i);
                saveBitmap(bitmap, "input_bitmap_" + i +".png");
            }
        }*/

        final int slice_width = (int) (bitmap_width / panorama_pics_per_screen);
        if( MyDebug.LOG )
            Log.d(TAG, "slice_width: " + slice_width);

        final double camera_angle = Math.toRadians(camera_angle_y);
        if( MyDebug.LOG ) {
            Log.d(TAG, "camera_angle_y: " + camera_angle_y);
            Log.d(TAG, "camera_angle: " + camera_angle);
        }
        // max offset error of gyro_tol_degrees - convert this to pixels
        //int max_offset_error_x = (int)(gyro_tol_degrees * bitmap_width / mActivity.getPreview().getViewAngleY() + 0.5f);
        //int max_offset_error_y = (int)(gyro_tol_degrees * bitmap_height / mActivity.getPreview().getViewAngleX() + 0.5f);
        //if we use the above code, remember not to use the camera view angles, but those that the test photos were taken with!
        //double h = ((double)bitmap_width) / (2.0 * Math.tan(camera_angle/2.0) );
        /*int max_offset_error_x = (int)(h * Math.tan(Math.toRadians(gyro_tol_degrees)) + 0.5f);
        max_offset_error_x *= 2; // allow a fudge factor
        int max_offset_error_y = max_offset_error_x;
        if( MyDebug.LOG ) {
            Log.d(TAG, "h: " + h);
            Log.d(TAG, "max_offset_error_x: " + max_offset_error_x);
            Log.d(TAG, "max_offset_error_y: " + max_offset_error_y);
        }
        */

        final int offset_x = (bitmap_width - slice_width)/2;
        // blend_hwidth is the half-width of the region that we blend between.
        // N.B., when using blendPyramids(), the region we actually have blending over is only half
        // of the width of the images it receives to blend receive (i.e., the blend region width
        // is equal to blend_hwidth), because of the code to find a best path.
        //final int blend_hwidth = 0;
        //final int blend_hwidth = nextPowerOf2(bitmap_width/20);
        //final int blend_hwidth = nextPowerOf2(bitmap_width/10);
        final int blend_hwidth = nextMultiple((int)(bitmap_width/6.1f+0.5f), getBlendDimension()/2);
        //final int blend_hwidth = nextPowerOf2(bitmap_width/5);
        final int align_hwidth = bitmap_width/10;
        //final int align_hwidth = bitmap_width/5;
        if( MyDebug.LOG ) {
            Log.d(TAG, "    blend_hwidth: " + blend_hwidth);
            Log.d(TAG, "    align_hwidth: " + align_hwidth);
        }

        List<Matrix> cumulative_transforms = new ArrayList<>(); // i-th entry is the transform to apply to the i-th bitmap so that it's aligned to the same space as the 1st bitmap

        List<Integer> align_x_values = new ArrayList<>();
        List<Integer> dst_offset_x_values = new ArrayList<>();

        computePanoramaTransforms(cumulative_transforms, align_x_values, dst_offset_x_values, bitmaps,
                bitmap_width, bitmap_height, offset_x, slice_width, align_hwidth, time_s);

        // note that we crop the panorama_width later on, but for now we still need an estimate, before finalising
        // the transforms
        int panorama_width = (bitmaps.size()*slice_width+2*offset_x);
        if( MyDebug.LOG ) {
            Log.d(TAG, "original panorama_width: " + panorama_width);
        }

        adjustPanoramaTransforms(bitmaps, cumulative_transforms, panorama_width, slice_width, bitmap_width, bitmap_height);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after adjusting transforms: " + (System.currentTimeMillis() - time_s));

        //adjustExposures(bitmaps, time_s);
        float ratio_brightnesses = adjustExposuresLocal(bitmaps, bitmap_width, bitmap_height, slice_width, time_s);

        int panorama_height = bitmap_height;
        int crop_x0 = 0;
        int crop_y0 = 0;

        if( crop ) {
            // compute crop regions
            int crop_x1 = bitmap_width-1;
            int crop_y1 = bitmap_height-1;
            for(int i=0;i<bitmaps.size();i++) {
                float [] points = new float[8];

                points[0] = 0.0f;
                points[1] = 0.0f;

                points[2] = bitmap_width-1.0f;
                points[3] = 0.0f;

                points[4] = 0.0f;
                points[5] = bitmap_height-1.0f;

                points[6] = bitmap_width-1.0f;
                points[7] = bitmap_height-1.0f;

                cumulative_transforms.get(i).mapPoints(points);

                crop_y0 = Math.max(crop_y0, (int)points[1]);
                crop_y0 = Math.max(crop_y0, (int)points[3]);

                crop_y1 = Math.min(crop_y1, (int)points[5]);
                crop_y1 = Math.min(crop_y1, (int)points[7]);

                if( MyDebug.LOG ) {
                    Log.d(TAG, "i: " + i);
                    Log.d(TAG, "    points[0]: " + points[0]);
                    Log.d(TAG, "    points[1]: " + points[1]);
                    Log.d(TAG, "    points[2]: " + points[2]);
                    Log.d(TAG, "    points[3]: " + points[3]);
                    Log.d(TAG, "    points[4]: " + points[4]);
                    Log.d(TAG, "    points[5]: " + points[5]);
                    Log.d(TAG, "    points[6]: " + points[6]);
                    Log.d(TAG, "    points[7]: " + points[7]);
                }
                if( i == 0 ) {
                    crop_x0 = Math.max(crop_x0, (int)points[0]);
                    crop_x0 = Math.max(crop_x0, (int)points[4]);
                }
                if( i == bitmaps.size()-1 ) {
                    crop_x1 = Math.min(crop_x1, (int)points[2]);
                    crop_x1 = Math.min(crop_x1, (int)points[6]);
                }
            }

            panorama_width -= (bitmap_width - 1) - crop_x1;
            panorama_width -= crop_x0;
            if( MyDebug.LOG ) {
                Log.d(TAG, "crop_x0: " + crop_x0);
                Log.d(TAG, "crop_x1: " + crop_x1);
                Log.d(TAG, "panorama_width: " + panorama_width);
            }
            /*if( crop_x0 > 0 ) {
                // need to shift transforms over
                for(int i=0;i<bitmaps.size();i++) {
                    cumulative_transforms.get(i).postTranslate(-crop_x0, 0.0f);
                }
            }*/

            panorama_height = crop_y1 - crop_y0 + 1;
            if( MyDebug.LOG ) {
                Log.d(TAG, "crop_y0: " + crop_y0);
                Log.d(TAG, "crop_y1: " + crop_y1);
                Log.d(TAG, "panorama_height: " + panorama_height);
            }

            // take cylindrical projection into account
            float theta = (float)((bitmap_width/2)*camera_angle)/(float)bitmap_width;
            float yscale = (float)Math.cos(theta);
            if( MyDebug.LOG ) {
                Log.d(TAG, "theta: " + theta);
                Log.d(TAG, "yscale: " + yscale);
            }
            //yscale = 1.0f;
            crop_y0 = (int)(bitmap_height/2.0f + yscale*(crop_y0 - bitmap_height/2.0f) + 0.5f);
            crop_y1 = (int)(bitmap_height/2.0f + yscale*(crop_y1 - bitmap_height/2.0f) + 0.5f);

            panorama_height = crop_y1 - crop_y0 + 1;
            if( MyDebug.LOG ) {
                Log.d(TAG, "crop_y0: " + crop_y0);
                Log.d(TAG, "crop_y1: " + crop_y1);
                Log.d(TAG, "panorama_height: " + panorama_height);
            }
        }

        Bitmap panorama = Bitmap.createBitmap(panorama_width, panorama_height, Bitmap.Config.ARGB_8888);

        if( MyDebug.LOG )
            Log.d(TAG, "### time before rendering bitmaps: " + (System.currentTimeMillis() - time_s));
        renderPanorama(bitmaps, bitmap_width, bitmap_height, cumulative_transforms, align_x_values, dst_offset_x_values,
                blend_hwidth, slice_width, offset_x, panorama, crop_x0, crop_y0, camera_angle, time_s);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after rendering bitmaps: " + (System.currentTimeMillis() - time_s));

        for(Bitmap bitmap : bitmaps) {
            bitmap.recycle();
        }
        bitmaps.clear();

        if( ratio_brightnesses >= 3.0f ) {
            if( MyDebug.LOG )
                Log.d(TAG, "apply contrast enhancement, ratio_brightnesses: " + ratio_brightnesses);

            /*if( true )
                throw new RuntimeException("ratio_brightnesses: " + ratio_brightnesses);*/

            Allocation allocation = Allocation.createFromBitmap(rs, panorama);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after creating allocation_out: " + (System.currentTimeMillis() - time_s));
            hdrProcessor.adjustHistogram(allocation, allocation, panorama.getWidth(), panorama.getHeight(), 0.25f, 1, true, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
            allocation.copyTo(panorama);
            allocation.destroy();
            if( MyDebug.LOG )
                Log.d(TAG, "### time after copying to bitmap: " + (System.currentTimeMillis() - time_s));
        }

        if( MyDebug.LOG )
            Log.d(TAG, "panorama complete!");

        freeScripts();

        if( MyDebug.LOG )
            Log.d(TAG, "### time taken: " + (System.currentTimeMillis() - time_s));

        return panorama;
    }

}
