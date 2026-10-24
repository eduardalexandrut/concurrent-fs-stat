package app.virtual_threads;

import lib.FSReport;
import lib.virtual_threads.FSStatLibVT;

import java.nio.file.Path;
import java.util.concurrent.Future;

/**
 * Example program (virtual threads version): calls the asynchronous
 * getFSReport and waits for the result with a blocking get().
 *
 * Usage: FSStatVTCLI [dir] [maxFS] [nb]
 */
public class FSStatVTCLI {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : ".");
        long maxFS = args.length > 1 ? Long.parseLong(args[1]) : 1_000_000;
        int nb = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        long t0 = System.currentTimeMillis();
        Future<FSReport> result = new FSStatLibVT().getFSReport(dir, maxFS, nb);
        log("getFSReport called, waiting for the result...");
        try {
            FSReport report = result.get();
            log("report for " + dir + " in " + (System.currentTimeMillis() - t0) + " ms\n" + report);
        } catch (Exception ex) {
            log("failed: " + (ex.getCause() != null ? ex.getCause() : ex));
        }
    }

    private static void log(String msg) {
        System.out.println("[ " + Thread.currentThread().getName() + " ] " + msg);
    }
}
