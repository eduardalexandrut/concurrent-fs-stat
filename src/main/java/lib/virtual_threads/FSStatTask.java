package lib.virtual_threads;

import lib.FSReport;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * FSStatLib - virtual threads version, interactive extension.
 *
 * Same fork/join structure as FSStatLibVT, but partial results must be
 * observable while the scan is running, so each virtual thread periodically
 * flushes its local counters into a shared monitor (StatsMonitor).
 * A dedicated "notifier" virtual thread reads a snapshot of the monitor
 * every UPDATE_PERIOD_MS and pushes it to the listener.
 * Stopping is cooperative: stop() sets a Flag that every virtual thread
 * checks before processing the next entry.
 */
public class FSStatTask {

    private static final long UPDATE_PERIOD_MS = 100;
    private static final int FLUSH_EVERY = 256;   // files counted locally before flushing

    private final Path dir;
    private final long maxFS;
    private final int nb;
    private final FSStatListener listener;
    private final StatsMonitor stats;
    private final Flag stopFlag = new Flag();

    public FSStatTask(Path dir, long maxFS, int nb, FSStatListener listener) {
        FSReport.checkParams(maxFS, nb);
        this.dir = dir;
        this.maxFS = maxFS;
        this.nb = nb;
        this.listener = listener;
        this.stats = new StatsMonitor(maxFS, nb);
    }

    public void start() {
        Thread.ofVirtual().name("fsstat-main").start(this::run);
    }

    public void stop() {
        stopFlag.set();
    }

    public FSReport getCurrentReport() {
        return stats.snapshot();
    }

    private void run() {
        Thread notifier = Thread.ofVirtual().name("fsstat-notifier").start(() -> {
            try {
                while (true) {
                    Thread.sleep(UPDATE_PERIOD_MS);
                    listener.onUpdate(stats.snapshot());
                }
            } catch (InterruptedException ex) {
                // scan finished
            }
        });
        try {
            if (!Files.isDirectory(dir)) {
                throw new NotDirectoryException(dir.toString());
            }
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                scan(dir, executor);
            }   // close() waits for every forked virtual thread
            notifier.interrupt();
            notifier.join();
            if (stopFlag.isSet()) {
                listener.onStopped(stats.snapshot());
            } else {
                listener.onCompleted(stats.snapshot());
            }
        } catch (Exception ex) {
            notifier.interrupt();
            listener.onFailed(ex);
        }
    }

    private void scan(Path dir, ExecutorService executor) throws Exception {
        long[] batch = new long[nb + 1];
        int inBatch = 0;
        List<Future<?>> subDirs = new ArrayList<>();

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (stopFlag.isSet()) {
                    break;
                }
                try {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isDirectory()) {
                        subDirs.add(executor.submit(() -> {                // fork
                            scan(entry, executor);
                            return null;
                        }));
                    } else if (attrs.isRegularFile()) {
                        batch[FSReport.bandOf(attrs.size(), maxFS, nb)]++;
                        if (++inBatch == FLUSH_EVERY) {
                            stats.add(batch);
                            batch = new long[nb + 1];
                            inBatch = 0;
                        }
                    }
                } catch (IOException ex) {
                    // unreadable entry: skipped
                }
            }
        } catch (IOException | DirectoryIteratorException ex) {
            // unreadable directory: skipped
        }
        stats.add(batch);

        for (Future<?> subDir : subDirs) {                                  // join
            subDir.get();
        }
    }
}
