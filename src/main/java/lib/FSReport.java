package lib;

import java.util.Arrays;

/**
 * Immutable report produced by every version of FSStatLib.
 *
 * bands[0 .. nb-1] count the files whose size falls in the i-th of the nb
 * equal intervals that divide [0, maxFS]; bands[nb] counts the files bigger
 * than maxFS. A file of exactly maxFS bytes belongs to the last band (nb-1).
 *
 * This is a plain value: it carries no concurrency logic, so sharing it among
 * the three versions does not affect their programming discipline.
 */
public record FSReport(long totalFiles, long maxFS, long[] bands) {

    public FSReport {
        bands = bands.clone();                  // defensive copy: really immutable
    }

    @Override
    public long[] bands() {
        return bands.clone();
    }

    public int nb() {
        return bands.length - 1;
    }

    public static FSReport empty(long maxFS, int nb) {
        checkParams(maxFS, nb);
        return new FSReport(0, maxFS, new long[nb + 1]);
    }

    /** Builds a report from per-band counters (the total is their sum). */
    public static FSReport of(long maxFS, long[] bands) {
        return new FSReport(Arrays.stream(bands).sum(), maxFS, bands);
    }

    /** Returns a new report with one more file of the given size. */
    public FSReport addFile(long size) {
        long[] b = bands.clone();
        b[bandOf(size, maxFS, nb())]++;
        return new FSReport(totalFiles + 1, maxFS, b);
    }

    /** Returns a new report summing this one and another. */
    public FSReport merge(FSReport other) {
        long[] b = bands.clone();
        long[] o = other.bands;
        for (int i = 0; i < b.length; i++) {
            b[i] += o[i];
        }
        return new FSReport(totalFiles + other.totalFiles, maxFS, b);
    }

    /** Band index of a file: 0..nb-1 for sizes in [0, maxFS], nb for sizes > maxFS. */
    public static int bandOf(long size, long maxFS, int nb) {
        if (size > maxFS) {
            return nb;
        }
        // band = floor(size * nb / maxFS): no truncation of the band width
        long band = maxFS <= Long.MAX_VALUE / nb
                ? size * nb / maxFS
                : (long) ((double) size * nb / maxFS);
        return (int) Math.min(band, nb - 1);
    }

    /** Human readable lower bound of a band (inclusive). */
    public long bandLowerBound(int band) {
        if (band >= nb()) return maxFS + 1;
        int n = nb();
        return maxFS <= Long.MAX_VALUE / n
                ? (band * maxFS + n - 1) / n                 // ceil(band * maxFS / nb)
                : (long) Math.ceil((double) band * maxFS / n);
    }

    public static void checkParams(long maxFS, int nb) {
        if (maxFS <= 0) throw new IllegalArgumentException("MaxFS must be > 0");
        if (nb <= 0) throw new IllegalArgumentException("NB must be > 0");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Total files: " + totalFiles + "\n");
        for (int i = 0; i < bands.length; i++) {
            sb.append(String.format("  %-22s %d%n", bandLabel(i), bands[i]));
        }
        return sb.toString();
    }

    public String bandLabel(int band) {
        if (band == nb()) {
            return "> " + maxFS;
        }
        long lo = bandLowerBound(band);
        long hi = band == nb() - 1 ? maxFS : bandLowerBound(band + 1);
        return band == nb() - 1 ? "[" + lo + ", " + hi + "]" : "[" + lo + ", " + hi + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FSReport r && r.totalFiles == totalFiles
                && r.maxFS == maxFS && Arrays.equals(r.bands, bands);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(totalFiles) * 31 + Arrays.hashCode(bands);
    }
}
