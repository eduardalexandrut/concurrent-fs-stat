package app.reactivex;

import lib.reactivex.FSStatLibRx;

import java.nio.file.Path;

/**
 * Example program (reactive version): subscribes to the Single returned by
 * getFSReport and prints the report.
 *
 * Usage: FSStatRxCLI [dir] [maxFS] [nb]
 */
public class FSStatRxCLI {

    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : ".");
        long maxFS = args.length > 1 ? Long.parseLong(args[1]) : 1_000_000;
        int nb = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        long t0 = System.currentTimeMillis();
        new FSStatLibRx()
                .getFSReport(dir, maxFS, nb)
                .blockingSubscribe(
                        report -> log("report for " + dir + " in "
                                + (System.currentTimeMillis() - t0) + " ms\n" + report),
                        err -> log("failed: " + err));
    }

    private static void log(String msg) {
        System.out.println("[ " + Thread.currentThread().getName() + " ] " + msg);
    }
}
