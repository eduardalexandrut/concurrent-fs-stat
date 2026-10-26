package lib.event_loop;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lib.FSReport;

import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/**
 * FSStatLib - event-loop version, interactive extension.
 *
 * A verticle (i.e. an event loop with its own state) that scans a directory
 * and interacts with the rest of the world only through the event bus:
 *  - every UPDATE_PERIOD_MS it publishes the partial report on ADDR_UPDATE;
 *  - when the scan ends it publishes the outcome on ADDR_DONE
 *    (status "completed", "stopped" or "failed") and undeploys itself;
 *  - a message on ADDR_STOP stops the scan.
 *
 * All the state (counters, stopped flag, timer) is confined to the verticle's
 * event-loop thread: callbacks, timer and event-bus handlers are all executed
 * by that thread, one at a time, so no locks or atomics are needed.
 * As in FSStatLibVertx, symbolic links are not followed.
 */
public class FSStatVerticle extends VerticleBase {

    public static final String ADDR_UPDATE = "fsstat.update";
    public static final String ADDR_DONE = "fsstat.done";
    public static final String ADDR_STOP = "fsstat.stop";
    private static final long UPDATE_PERIOD_MS = 100;

    private final String dir;
    private final long maxFS;
    private final int nb;

    private FileSystem fs;
    private EventBus eb;
    private long[] bands;
    private long totalFiles;
    private boolean stopped;
    private long timerId;

    public FSStatVerticle(String dir, long maxFS, int nb) {
        FSReport.checkParams(maxFS, nb);
        this.dir = dir;
        this.maxFS = maxFS;
        this.nb = nb;
    }

    @Override
    public Future<?> start() throws Exception {
        fs = vertx.fileSystem();
        eb = vertx.eventBus();
        bands = new long[nb + 1];

        eb.consumer(ADDR_STOP, msg -> finish("stopped", null));
        timerId = vertx.setPeriodic(UPDATE_PERIOD_MS, id -> eb.publish(ADDR_UPDATE, toJson(currentReport())));

        vertx.executeBlocking(() -> Path.of(dir).toRealPath().toString())   // canonical root
          .compose(root -> fs.lprops(root).compose(props -> props.isDirectory()
                  ? scanDir(root)
                  : Future.failedFuture(new NotDirectoryException(dir))))
          .onSuccess(v -> finish("completed", null))
          .onFailure(err -> finish("failed", err.toString()));

        return super.start();
    }

    private Future<Void> scanDir(String path) {
        if (stopped) {
            return Future.succeededFuture();
        }
        return fs.readDir(path).compose(entries ->
                Future.all(entries.stream()
                        .distinct()
                        .filter(entry -> FSStatLibVertx.isChildOf(entry, path))   // drops resolved links
                        .map(this::scanEntry)
                        .toList())
                      .mapEmpty());
    }

    private Future<Void> scanEntry(String path) {
        if (stopped) {
            return Future.succeededFuture();
        }
        return fs.lprops(path).compose(props -> {
            if (stopped) {
                return Future.<Void>succeededFuture();
            } else if (props.isDirectory()) {
                return scanDir(path);
            } else {
                if (props.isRegularFile()) {
                    bands[FSReport.bandOf(props.size(), maxFS, nb)]++;
                    totalFiles++;
                }
                return Future.<Void>succeededFuture();
            }
        }).recover(err -> Future.succeededFuture());   // unreadable entry: skipped
    }

    /* executed on the event loop: called at most once thanks to the stopped flag */
    private void finish(String status, String error) {
        if (stopped) {
            return;
        }
        stopped = true;
        vertx.cancelTimer(timerId);
        JsonObject done = new JsonObject()
                .put("status", status)
                .put("report", toJson(currentReport()));
        if (error != null) {
            done.put("error", error);
        }
        eb.publish(ADDR_DONE, done);
        vertx.undeploy(deploymentID());
    }

    private FSReport currentReport() {
        return new FSReport(totalFiles, maxFS, bands);
    }

    // reports travel on the event bus as JSON
    public static JsonObject toJson(FSReport r) {
        JsonArray b = new JsonArray();
        for (long v : r.bands()) {
            b.add(v);
        }
        return new JsonObject().put("totalFiles", r.totalFiles()).put("maxFS", r.maxFS()).put("bands", b);
    }

    public static FSReport fromJson(JsonObject json) {
        JsonArray b = json.getJsonArray("bands");
        long[] bands = new long[b.size()];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = b.getLong(i);
        }
        return new FSReport(json.getLong("totalFiles"), json.getLong("maxFS"), bands);
    }
}
