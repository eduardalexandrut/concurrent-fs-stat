package lib.reactivex;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lib.FSReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * FSStatLib - reactive version (RxJava 3).
 *
 * The file system is seen as a (cold) stream of file sizes. On top of it:
 *  - getFSReport       : Single<FSReport>   = sizes reduced into one report
 *  - getFSReportStream : Flowable<FSReport> = sizes scanned into a stream of
 *                        progressively more complete reports (interactive
 *                        extension). The last item is the final report.
 *
 * Nothing is computed until someone subscribes; disposing the subscription
 * stops the scan (cancellation travels upstream through every operator).
 * The accumulation is done by reduce/scan: Rx serialises the items that
 * flatMap merges from different threads, so the accumulator is never
 * accessed concurrently and no shared mutable state is needed.
 */
public class FSStatLibRx {

    /* max sub-directories listed concurrently by each flatMap */
    private static final int MAX_CONCURRENCY = 8;

    public Single<FSReport> getFSReport(Path dir, long maxFS, int nb) {
        return Single.defer(() -> fileSizes(dir)
                .reduce(FSReport.empty(maxFS, nb), FSReport::addFile));
    }

    public Flowable<FSReport> getFSReportStream(Path dir, long maxFS, int nb) {
        return Flowable.defer(() -> fileSizes(dir)
                .scan(FSReport.empty(maxFS, nb), FSReport::addFile));
    }

    /* sizes of all the regular files under root (recursively) */
    private Flowable<Long> fileSizes(Path root) {
        return Flowable.defer(() -> Files.isDirectory(root)
                ? sizesIn(root)
                : Flowable.<Long>error(new NotDirectoryException(root.toString())));
    }

    private Flowable<Long> sizesIn(Path dir) {
        return Flowable.defer(() -> Flowable.fromIterable(list(dir)))
                .subscribeOn(Schedulers.io())            // blocking listing on an I/O thread
                .flatMap(entry -> Flowable.defer(() -> {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isDirectory()) {
                        return sizesIn(entry);           // recursion: a sub-stream
                    } else if (attrs.isRegularFile()) {
                        return Flowable.just(attrs.size());
                    } else {
                        return Flowable.<Long>empty();   // links, sockets, devices: ignored
                    }
                }).onErrorResumeNext(err -> Flowable.empty()),   // unreadable entry: skipped
                MAX_CONCURRENCY);
    }

    private static List<Path> list(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.toList();
        }
    }
}
