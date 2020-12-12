package net.sourceforge.opencamera.sensorlogging;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public class VideoPhaseInfo {
    private final long videoPhaseNs;
    private final double avgDurationNs;

    // Accepts a sorted collection
    public static double median(List<Long> numList) {
        int middle = numList.size() / 2;
        if (numList.size() % 2 == 1) {
            return numList.get(middle);
        } else {
            return (numList.get(middle - 1) + numList.get(middle)) / 2.0;
        }
    }

    public static double mean(List<Long> numList) {
        double sum = 0;
        for (Long num : numList) {
            sum += num;
        }
        return sum / numList.size();
    }

    public VideoPhaseInfo(long videoPhaseNs, List<Long> frameDurations) {
        this.videoPhaseNs = videoPhaseNs;
        //Collections.sort(frameDurations);
        this.avgDurationNs = mean(frameDurations);
    }

    public long getVideoPhaseNs() {
        return videoPhaseNs;
    }

    public double getAvgDurationNs() {
        return avgDurationNs;
    }

    @NonNull
    @Override
    public String toString() {
        return videoPhaseNs + "\n" + avgDurationNs;
    }
}
