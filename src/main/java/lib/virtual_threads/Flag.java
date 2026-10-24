package lib.virtual_threads;

/**
 * Simple monitor used as a stop flag shared by all the virtual threads
 * of a scan.
 */
public class Flag {

    private boolean isSet;

    public synchronized void set() {
        isSet = true;
    }

    public synchronized boolean isSet() {
        return isSet;
    }
}
