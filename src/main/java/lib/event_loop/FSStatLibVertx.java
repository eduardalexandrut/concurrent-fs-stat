package lib.event_loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import lib.FSReport;

import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;

/**
 * FSStatLib - event-loop version (Vert.x).
 *
 * getFSReport is an asynchronous method returning a Vert.x Future.
 * It is meant to be called from inside an event loop (e.g. a verticle):
 * every continuation (compose / map / otherwise) is then executed by that
 * same event-loop thread, so no state is ever shared among threads.
 *
 * The recursion is a non-blocking map-reduce built by composing futures:
 *  - map:    every entry of a directory becomes a Future<FSReport>
 *            (a file -> a report with one file, a sub-directory -> recursion)
 *  - reduce: Future.all(...) joins the children and their reports are merged.
 * Termination detection is therefore implicit: the future of a directory
 * completes exactly when the futures of all its entries have completed.
 */
public class FSStatLibVertx {

    private final Vertx vertx;
    private final FileSystem fs;

    public FSStatLibVertx(Vertx vertx) {
        this.vertx = vertx;
        this.fs = vertx.fileSystem();
    }

    public Future<FSReport> getFSReport(String dir, long maxFS, int nb) {
        final FSReport empty;
        try {
            empty = FSReport.empty(maxFS, nb);
        } catch (IllegalArgumentException ex) {
            return Future.failedFuture(ex);
        }
        // canonical path of the root: a short blocking call, offloaded to a worker
        return vertx.executeBlocking(() -> Path.of(dir).toRealPath().toString())
                .compose(root -> fs.lprops(root).compose(props -> props.isDirectory()
                        ? scanDir(root, empty)
                        : Future.failedFuture(new NotDirectoryException(dir))));
    }

    private Future<FSReport> scanDir(String dir, FSReport empty) {
        return fs.readDir(dir).compose(entries -> {
            List<Future<FSReport>> children = entries.stream()
                    .distinct()
                    .filter(entry -> isChildOf(entry, dir))   // drops resolved symbolic links
                    .map(entry -> scanEntry(entry, empty))
                    .toList();
            return Future.all(children).map(all -> {
                FSReport report = empty;
                for (FSReport child : all.<FSReport>list()) {
                    report = report.merge(child);
                }
                return report;
            });
        });
    }

    private Future<FSReport> scanEntry(String path, FSReport empty) {
        return fs.lprops(path).compose(props -> {
            if (props.isDirectory()) {
                return scanDir(path, empty);
            } else {
                return Future.succeededFuture(empty.addFile(props.size()));
            }
        }).otherwise(empty);   // unreadable entry
    }

    static boolean isChildOf(String entry, String dir) {
        Path parent = Path.of(entry).getParent();
        return parent != null && parent.toString().equals(dir);
    }
}
