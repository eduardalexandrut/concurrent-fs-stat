package lib.event_loop;

import io.vertx.core.*;
import io.vertx.core.file.FileSystem;
import lib.FSReport;
import lib.FSStatLib;
import lib.FSStats;
import lib.FSUpdateListener;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FSStatLibVertx implements FSStatLib {
    private final int THROTTLE_SIZE = 300;
    private Vertx vertx =  Vertx.vertx();
    private FSUpdateListener listener;
    private Path dir;
    private long maxFS;
    private int nb;
    private long bandSize;

    private FSStats stats;

    private final AtomicBoolean isCompleted = new AtomicBoolean(false);
    private final AtomicInteger activeTasks = new AtomicInteger(0);


    @Override
    public void getFSReport(Path dir, long maxFS, int nb, FSUpdateListener listener) {

        this.listener = listener;
        this.nb = nb;
        this.maxFS = maxFS;
        this.bandSize = maxFS / nb;

        this.isCompleted.set(false);
        this.activeTasks.set(0);

        this.stats = new FSStats(nb);

        scanDirectory(dir);
    }

    @Override
    public FSReport getCurrentReport() {
        return stats != null ? stats.snapshot() : new FSReport(0, new long[nb + 1]);
    }

    @Override
    public void stop() {
        isCompleted.set(true);
    }

    private void scanDirectory(Path dir) {
        if (isCompleted.get()) {
            return;
        }

        activeTasks.incrementAndGet();

        FileSystem fs = vertx.fileSystem();
        fs.readDir(String.valueOf(dir)).onComplete(res -> {
            try {

                if (isCompleted.get()) {
                        return;
                }

                if (res.succeeded()) {
                    for (final String entry : res.result()) {
                        if (isCompleted.get()) {
                            return;
                        }

                        activeTasks.incrementAndGet();

                        fs.props(entry).onComplete(props -> {
                            try {
                                if (isCompleted.get()) {
                                    return;
                                }

                                if (props.succeeded()) {

                                    if (props.result().isDirectory()) {
                                        scanDirectory(Path.of(entry));
                                    } else {
                                        processFile(Path.of(entry));
                                    }
                                }
                            } finally {
                                if (activeTasks.decrementAndGet() == 0) {
                                    complete();
                                }
                            }

                        });
                    }
                }
            } finally {
                if (activeTasks.decrementAndGet() == 0) {
                    complete();
                }
            }

        });
    }

    private void processFile(Path path) {
        if (isCompleted.get()) {return;}

        FileSystem fs = vertx.fileSystem();

        activeTasks.incrementAndGet();

        fs.props(path.toString()).onComplete(res -> {
            try {
                if (isCompleted.get()) {return;}

                if (res.succeeded()) {
                    final long fileSize = res.result().size();
                    final int band = computeBand(fileSize);

                    stats.addFile(band);
                }
            } finally {
                if (activeTasks.decrementAndGet() == 0) {
                    complete();
                }
            }
        });
    }

    private int computeBand(long fileSize) {
        int band = (int) (fileSize / bandSize);

        if (band >= nb)
            band = nb;

        return band;
    }

    private void complete() {
        if (!isCompleted.getAndSet(true)) {
            try {
                listener.onComplete(stats.snapshot());
            } finally {
                if (vertx != null) {
                    vertx.close();
                }
            }
        }
    }
}
