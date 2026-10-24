package app.virtual_threads;

import lib.FSReport;
import lib.virtual_threads.FSStatListener;
import lib.virtual_threads.FSStatTask;
import view.FSStatView;
import view.ViewListener;

import java.nio.file.Path;

/**
 * GUI program (virtual threads version, interactive extension).
 */
public class FSStatVTGUI {

    public static void main(String[] args) {
        VTController controller = new VTController();
        FSStatView view = new FSStatView("virtual threads", controller);
        controller.setView(view);
        view.display();
    }
}

class VTController implements ViewListener {

    private FSStatView view;
    private FSStatTask task;   // accessed only by the EDT

    void setView(FSStatView view) {
        this.view = view;
    }

    @Override
    public void startRequested(Path dir, long maxFS, int nb) {
        task = new FSStatTask(dir, maxFS, nb, new FSStatListener() {
            @Override
            public void onUpdate(FSReport partial) {
                view.showReport(partial);
            }

            @Override
            public void onCompleted(FSReport report) {
                view.showFinished(report, "Completed: " + report.totalFiles() + " files.");
            }

            @Override
            public void onStopped(FSReport partial) {
                view.showFinished(partial, "Stopped: " + partial.totalFiles() + " files counted so far.");
            }

            @Override
            public void onFailed(Exception error) {
                view.showFinished(null, "Failed: " + error);
            }
        });
        task.start();
    }

    @Override
    public void stopRequested() {
        if (task != null) {
            task.stop();
        }
    }
}
