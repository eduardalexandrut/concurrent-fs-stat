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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * FSStatLib - virtual threads version.
 *
 * The code is plain, blocking, sequential Java: concurrency comes from
 * forking one virtual thread per sub-directory and joining it (Future.get),
 * which is cheap because a blocked virtual thread releases its carrier.
 *
 * Each virtual thread counts the files of its own directory in a local
 * array and returns an immutable FSReport: results flow back through the
 * futures (fork/join), so this version shares no mutable state at all.
 */
public class FSStatLibVT {

    /** Asynchronous version: the report is computed by a new virtual thread. */
    public Future<FSReport> getFSReport(Path dir, long maxFS, int nb) {
        FutureTask<FSReport> task = new FutureTask<>(() -> computeFSReport(dir, maxFS, nb));
        Thread.ofVirtual().name("fsstat-" + dir.getFileName()).start(task);
        return task;
    }

    /** Synchronous version: blocks the caller until the report is ready. */
    public FSReport computeFSReport(Path dir, long maxFS, int nb) throws Exception {
        FSReport.checkParams(maxFS, nb);
        if (!Files.isDirectory(dir)) {
            throw new NotDirectoryException(dir.toString());
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return scan(dir, maxFS, nb, executor);
        }
    }

    private FSReport scan(Path dir, long maxFS, int nb, ExecutorService executor) throws Exception {
        long[] bands = new long[nb + 1];                     // local to this virtual thread
        List<Future<FSReport>> subDirs = new ArrayList<>();

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                try {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isDirectory()) {
                        subDirs.add(executor.submit(() -> scan(entry, maxFS, nb, executor)));   // fork
                    } else if (attrs.isRegularFile()) {
                        bands[FSReport.bandOf(attrs.size(), maxFS, nb)]++;
                    }
                } catch (IOException ex) {
                    // unreadable entry: skipped
                }
            }
        } catch (IOException | DirectoryIteratorException ex) {
            // unreadable directory: it contributes only what was read so far
        }

        FSReport report = FSReport.of(maxFS, bands);
        for (Future<FSReport> subDir : subDirs) {            // join
            try {
                report = report.merge(subDir.get());
            } catch (ExecutionException ex) {
                throw ex.getCause() instanceof Exception cause ? cause : ex;
            }
        }
        return report;
    }
}
