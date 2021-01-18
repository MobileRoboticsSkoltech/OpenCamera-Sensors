package net.sourceforge.opencamera.sensorlogging;

import androidx.annotation.NonNull;

import java.util.List;

public class VideoPhaseInfo {
    private final long mVideoPhaseNs;
    private final double mAvgDurationNs;

    // Accepts a sorted collection
    private static double median(List<Long> numList) {
        int middle = numList.size() / 2;
        if (numList.size() % 2 == 1) {
            return numList.get(middle);
        } else {
            return (numList.get(middle - 1) + numList.get(middle)) / 2.0;
        }
    }

    private static double mean(List<Long> numList) {
        double sum = 0;
        for (Long num : numList) {
            sum += num;
        }
        return sum / numList.size();
    }

    public VideoPhaseInfo(long videoPhaseNs, List<Long> frameDurations) {
        this.mVideoPhaseNs = videoPhaseNs;
        //Collections.sort(frameDurations);
        this.mAvgDurationNs = mean(frameDurations);
    }

    public long getVideoPhaseNs() {
        return mVideoPhaseNs;
    }

    public double getAvgDurationNs() {
        return mAvgDurationNs;
    }

    @NonNull
    @Override
    public String toString() {
        return mVideoPhaseNs + "\n" + mAvgDurationNs + "\n";
    }
}
