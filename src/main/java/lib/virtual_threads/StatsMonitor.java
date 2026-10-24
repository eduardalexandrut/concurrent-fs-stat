package lib.virtual_threads;

import lib.FSReport;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Monitor holding the partial statistics of an interactive scan.
 *
 * Written by the scanning virtual threads (in batches), read by the notifier.
 * A ReentrantLock is used instead of synchronized: on Java 21 a virtual thread
 * blocked on a synchronized monitor pins its carrier thread, while blocking
 * on a j.u.c. lock simply parks the virtual thread.
 */
public class StatsMonitor {

    private final Lock lock = new ReentrantLock();
    private final long maxFS;
    private final long[] bands;
    private long totalFiles;

    public StatsMonitor(long maxFS, int nb) {
        this.maxFS = maxFS;
        this.bands = new long[nb + 1];
    }

    /** Adds the counters of a batch of files. */
    public void add(long[] batch) {
        lock.lock();
        try {
            for (int i = 0; i < bands.length; i++) {
                bands[i] += batch[i];
                totalFiles += batch[i];
            }
        } finally {
            lock.unlock();
        }
    }

    public FSReport snapshot() {
        lock.lock();
        try {
            return new FSReport(totalFiles, maxFS, bands);   // the record copies the array
        } finally {
            lock.unlock();
        }
    }
}
