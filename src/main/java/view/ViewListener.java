package view;

import java.nio.file.Path;

/** Commands issued by the user through the GUI (called on the Swing EDT). */
public interface ViewListener {

    void startRequested(Path dir, long maxFS, int nb);

    void stopRequested();
}
