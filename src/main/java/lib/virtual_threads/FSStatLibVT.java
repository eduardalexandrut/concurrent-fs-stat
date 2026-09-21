package lib.virtual_threads;

import lib.FSReport;
import lib.FSStatLib;
import lib.FSStats;
import lib.FSUpdateListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

public class FSStatLibVT implements FSStatLib {
    private ExecutorService executor;
    private FSUpdateListener listener;
    private FSStats globalStats;
    private long bandSize;
    private int nb;
    private final AtomicBoolean isCompleted = new AtomicBoolean(false);

    @Override
    public void getFSReport(Path dir, long maxFS, int nb, FSUpdateListener listener) {
        this.listener = listener;
        this.nb = nb;
        this.bandSize = maxFS / nb;
        this.isCompleted.set(false);
        this.globalStats = new FSStats(nb);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        executor.submit(() -> {
            try {
                try {
                    scan(dir);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (!isCompleted.getAndSet(true)) {
                    listener.onComplete(globalStats.snapshot());
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    private void scan(Path dir) throws Exception {
        if (isCompleted.get()) return;

        List<Future<FSStats>> futures;

        // MAP — spawn one task per entry
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            futures = StreamSupport.stream(stream.spliterator(), false)
                    .map(path -> executor.submit(() -> {
                        if (isCompleted.get()) return new FSStats(nb);

                        if (Files.isDirectory(path)) {
                            scan(path);               // recurse (updates globalStats internally)
                            return new FSStats(nb);   // subdir already merged itself
                        } else {
                            return processFile(path); // MAP: one partial result per file
                        }
                    }))
                    .toList();
        }

        // REDUCE — merge all file results for this directory
        FSStats dirStats = new FSStats(nb);
        for (Future<FSStats> future : futures) {
            merge(dirStats, future.get());
        }

        // merge into global and notify UI once every UPDATE_INTERVAL_MS
        merge(globalStats, dirStats);
    }

    private FSStats processFile(Path path) throws IOException {
        FSStats partial = new FSStats(nb);
        long size = Files.size(path);
        int band = Math.min((int)(size / bandSize), nb);
        partial.addFile(band);
        return partial;
    }

    private void merge(FSStats dst, FSStats src) {
        FSReport s = src.snapshot();
        dst.mergeReport(s);
    }

    @Override
    public void stop() {
        if (!isCompleted.getAndSet(true)) {
            executor.shutdownNow();
        }
    }

    @Override
    public FSReport getCurrentReport() {
        return globalStats != null ? globalStats.snapshot() : new FSReport(0, new long[nb + 1]);
    }
}
