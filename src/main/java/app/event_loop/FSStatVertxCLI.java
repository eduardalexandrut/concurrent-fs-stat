package app.event_loop;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import lib.event_loop.FSStatLibVertx;

/**
 * Example program (event-loop version): a verticle calls the asynchronous
 * getFSReport and prints the report when its future completes.
 *
 * Usage: FSStatVertxCLI [dir] [maxFS] [nb]
 */
class ReportAgent extends VerticleBase {

    private final String dir;
    private final long maxFS;
    private final int nb;

    ReportAgent(String dir, long maxFS, int nb) {
        this.dir = dir;
        this.maxFS = maxFS;
        this.nb = nb;
    }

    @Override
    public Future<?> start() throws Exception {
        long t0 = System.currentTimeMillis();
        new FSStatLibVertx(vertx)
                .getFSReport(dir, maxFS, nb)
                .onSuccess(report -> log("report for " + dir + " in "
                        + (System.currentTimeMillis() - t0) + " ms\n" + report))
                .onFailure(err -> log("failed: " + err))
                .onComplete(res -> context.runOnContext(v -> vertx.close()));   // after start() returns
        log("getFSReport called, waiting for the result...");
        return super.start();
    }

    private void log(String msg) {
        System.out.println("[ " + Thread.currentThread().getName() + " ] " + msg);
    }
}

public class FSStatVertxCLI {

    public static void main(String[] args) {
        String dir = args.length > 0 ? args[0] : ".";
        long maxFS = args.length > 1 ? Long.parseLong(args[1]) : 1_000_000;
        int nb = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        Vertx.vertx().deployVerticle(new ReportAgent(dir, maxFS, nb));
    }
}
