/**
 * Modifications copyright (C) 2021 Mobile Robotics Lab. at Skoltech
 */

package com.googleresearch.capturesync.softwaresync.phasealign;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;

public class PeriodCalculator {
    private final static long CALC_DURATION_MS = 10000;
    private volatile boolean shouldRegister;
    private ArrayList<Long> registeredTimestamps = new ArrayList<>();

    // Blocking call, returns 0 in case of error
    @RequiresApi(api = Build.VERSION_CODES.N)
    public long getPeriodNs() throws InterruptedException {
        // Start recording timestamps
        registeredTimestamps = new ArrayList<>();
        shouldRegister = true;
        Thread.sleep(CALC_DURATION_MS);
        // Stop recording timestamps and calculate period
        shouldRegister = false;
        return calcPeriodNsClusters(getDiff(registeredTimestamps));
    }

    private ArrayList<Long> getDiff(ArrayList<Long> arrayList) {
        Long prev = 0L;
        ArrayList<Long> result = new ArrayList<>();
        for (Long aLong : arrayList) {
            if (prev != 0L) {
                result.add(aLong - prev);
            }
            prev = aLong;
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private long calcPeriodNsClusters(ArrayList<Long> numArray) {
        long initEstimate = Collections.min(numArray);
        long nClust = Math.round(1.0 * Collections.max(numArray) / initEstimate);
        double weightedSum = 0L;
        for (int i = 0; i < nClust; i++) {
            int finalI = i;
            ArrayList<Long> clust = (ArrayList<Long>)numArray.stream().filter(
                    x -> (x > (finalI + 0.5)*initEstimate) && (x < (finalI + 1.5)*initEstimate)
            ).collect(Collectors.toList());
            if (clust.size() > 0) {
                weightedSum += 1.0 * median(clust) / (i + 1) * clust.size();
            }
        }
        return Math.round(weightedSum / numArray.size());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private long calcPeriodNsMedian(ArrayList<Long> numArray) {
        return median(numArray);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private long median(ArrayList<Long> numArray) {
        numArray.sort(Comparator.naturalOrder());
        double median;
        if (numArray.size() % 2 == 0)
            median = ((double)numArray.get(numArray.size()/2)
                    + (double)numArray.get(numArray.size()/2 - 1))/2;
        else
            median = (double) numArray.get(numArray.size()/2);
        return (long)median;
    }

    public void onFrameTimestamp(long timestampNs) {
        // Register timestamp
        if (shouldRegister) {
            registeredTimestamps.add(timestampNs);
        }
    }
}
