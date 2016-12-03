package net.sourceforge.opencamera.Preview;

import android.media.CamcorderProfile;
import android.util.Log;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.MyDebug;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Handles video quality options.
 *  Note that this class should avoid calls to the Android API, so we can perform local unit testing
 *  on it.
 */
public class VideoQualityHandler {
    private static final String TAG = "VideoQualityHandler";

    public static class Dimension2D {
        final int width;
        final int height;

        public Dimension2D(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    // video_quality can either be:
    // - an int, in which case it refers to a CamcorderProfile
    // - of the form [CamcorderProfile]_r[width]x[height] - we use the CamcorderProfile as a base, and override the video resolution - this is needed to support resolutions which don't have corresponding camcorder profiles
    private List<String> video_quality;
    private int current_video_quality = -1; // this is an index into the video_quality array, or -1 if not found (though this shouldn't happen?)
    private List<CameraController.Size> video_sizes;

    void resetCurrentQuality() {
        video_quality = null;
        current_video_quality = -1;
    }

    /** Initialises the class with the available video profiles and resolutions.
     *  Note that a HashMap is used instead of SparseArray (despite Android Studio warning) so that
     *  this code can be used in local
     *  unit testing.
     * @param profiles This is a hashmap where the key is the quality (see CamcorderProfile.QUALITY_*),
     *                 the Dimension2D is the width/height corresponding to that quality (as given by
     *                 videoFrameWidth, videoFrameHeight in the profile returned byCamcorderProfile.get().
     */
    public void initialiseVideoQualityFromProfiles(HashMap<Integer, Dimension2D> profiles) {
        if( MyDebug.LOG )
            Log.d(TAG, "initialiseVideoQualityFromProfiles()");
        video_quality = new ArrayList<>();
        boolean done_video_size[] = null;
        if( video_sizes != null ) {
            done_video_size = new boolean[video_sizes.size()];
            for(int i=0;i<video_sizes.size();i++)
                done_video_size[i] = false;
        }
        if( profiles.get(CamcorderProfile.QUALITY_HIGH) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_HIGH");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_HIGH);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_HIGH, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_1080P) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_1080P");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_1080P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_1080P, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_720P) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_720P");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_720P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_720P, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_480P) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_480P");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_480P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_480P, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_CIF) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_CIF");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_CIF);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_CIF, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_QVGA) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_QVGA");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_QVGA);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QVGA, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_QCIF) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_QCIF");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_QCIF);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QCIF, dim.width, dim.height);
        }
        if( profiles.get(CamcorderProfile.QUALITY_LOW) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "supports QUALITY_LOW");
            Dimension2D dim = profiles.get(CamcorderProfile.QUALITY_LOW);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_LOW, dim.width, dim.height);
        }
        if( MyDebug.LOG ) {
            for(int i=0;i<video_quality.size();i++) {
                Log.d(TAG, "supported video quality: " + video_quality.get(i));
            }
        }
    }

    // Android docs and FindBugs recommend that Comparators also be Serializable
    private static class SortVideoSizesComparator implements Comparator<CameraController.Size>, Serializable {
        private static final long serialVersionUID = 5802214721033718212L;

        @Override
        public int compare(final CameraController.Size a, final CameraController.Size b) {
            return b.width * b.height - a.width * a.height;
        }
    }

    public void sortVideoSizes() {
        if( MyDebug.LOG )
            Log.d(TAG, "sortVideoSizes()");
        Collections.sort(this.video_sizes, new SortVideoSizesComparator());
        if( MyDebug.LOG ) {
            for(CameraController.Size size : video_sizes) {
                Log.d(TAG, "    supported video size: " + size.width + ", " + size.height);
            }
        }
    }

    private void addVideoResolutions(boolean done_video_size[], int base_profile, int min_resolution_w, int min_resolution_h) {
        if( video_sizes == null ) {
            return;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "profile " + base_profile + " is resolution " + min_resolution_w + " x " + min_resolution_h);
        for(int i=0;i<video_sizes.size();i++) {
            if( done_video_size[i] )
                continue;
            CameraController.Size size = video_sizes.get(i);
            if( size.width == min_resolution_w && size.height == min_resolution_h ) {
                String str = "" + base_profile;
                video_quality.add(str);
                done_video_size[i] = true;
                if( MyDebug.LOG )
                    Log.d(TAG, "added: " + str);
            }
            else if( base_profile == CamcorderProfile.QUALITY_LOW || size.width * size.height >= min_resolution_w*min_resolution_h ) {
                String str = "" + base_profile + "_r" + size.width + "x" + size.height;
                video_quality.add(str);
                done_video_size[i] = true;
                if( MyDebug.LOG )
                    Log.d(TAG, "added: " + str);
            }
        }
    }

    public List<String> getSupportedVideoQuality() {
        if( MyDebug.LOG )
            Log.d(TAG, "getSupportedVideoQuality");
        return this.video_quality;
    }

    public int getCurrentVideoQualityIndex() {
        if( MyDebug.LOG )
            Log.d(TAG, "getCurrentVideoQualityIndex");
        return this.current_video_quality;
    }

    public void setCurrentVideoQualityIndex(int current_video_quality) {
        if( MyDebug.LOG )
            Log.d(TAG, "setCurrentVideoQualityIndex: " + current_video_quality);
        this.current_video_quality = current_video_quality;
    }

    public String getCurrentVideoQuality() {
        if( current_video_quality == -1 )
            return null;
        return video_quality.get(current_video_quality);
    }

    public List<CameraController.Size> getSupportedVideoSizes() {
        if( MyDebug.LOG )
            Log.d(TAG, "getSupportedVideoSizes");
        return this.video_sizes;
    }

    public void setVideoSizes(List<CameraController.Size> video_sizes) {
        this.video_sizes = video_sizes;
        this.sortVideoSizes();
    }

}
