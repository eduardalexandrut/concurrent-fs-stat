package lib.virtual_threads;

import lib.FSReport;

/**
 * Observer of an interactive scan. Methods are called by the scan's own
 * virtual threads: implementations must not block and, if they touch a GUI,
 * must hand the work over to the GUI thread.
 */
public interface FSStatListener {

    void onUpdate(FSReport partial);

    void onCompleted(FSReport report);

    void onStopped(FSReport partial);

    void onFailed(Exception error);
}
