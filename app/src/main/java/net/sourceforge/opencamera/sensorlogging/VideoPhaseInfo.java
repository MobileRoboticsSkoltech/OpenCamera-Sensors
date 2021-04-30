package net.sourceforge.opencamera.sensorlogging;

import androidx.annotation.NonNull;

import java.util.List;

public class VideoPhaseInfo {
    private final long mVideoPhaseNs;
    private final double mAvgDurationNs;
    private final long mExposureTime;


    private static double mean(List<Long> numList) {
        double sum = 0;
        for (Long num : numList) {
            sum += num;
        }
        return sum / numList.size();
    }

    public VideoPhaseInfo(long videoPhaseNs, List<Long> frameDurations, long exposureTime) {
        this.mVideoPhaseNs = videoPhaseNs;
        this.mAvgDurationNs = mean(frameDurations);
        this.mExposureTime = exposureTime;
    }

    public long getVideoPhaseNs() {
        return mVideoPhaseNs;
    }

    public double getAvgDurationNs() {
        return mAvgDurationNs;
    }

    public long getExposureTime() { return mExposureTime; }

    @NonNull
    @Override
    public String toString() {
        return mVideoPhaseNs + "\n" + mAvgDurationNs + "\n" + mExposureTime + "\n";
    }
}
