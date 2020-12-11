package net.sourceforge.opencamera.sensorlogging;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public class VideoPhaseInfo {
    private final long videoPhaseNs;
    private final double avgDurationNs;

    // Accepts a sorted collection
    public static double median(List<Long> m) {
        int middle = m.size() / 2;
        if (m.size() % 2 == 1) {
            return m.get(middle);
        } else {
            return (m.get(middle - 1) + m.get(middle)) / 2.0;
        }
    }

    public VideoPhaseInfo(long videoPhaseNs, List<Long> frameDurations) {
        this.videoPhaseNs = videoPhaseNs;
        Collections.sort(frameDurations);
        this.avgDurationNs = median(frameDurations);
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
