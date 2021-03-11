package net.sourceforge.opencamera.sensorlogging;

import androidx.annotation.NonNull;

import java.util.List;

public class VideoPhaseInfo {
    private final long mVideoPhaseNs;
    private final double mAvgDurationNs;

    private static double mean(List<Long> numList) {
        double sum = 0;
        for (Long num : numList) {
            sum += num;
        }
        return sum / numList.size();
    }

    public VideoPhaseInfo(long videoPhaseNs, List<Long> frameDurations) {
        this.mVideoPhaseNs = videoPhaseNs;
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
