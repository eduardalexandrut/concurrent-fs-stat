package app.reactivex;

import io.reactivex.rxjava3.disposables.Disposable;
import lib.FSReport;
import lib.reactivex.FSStatLibRx;
import view.FSStatView;
import view.ViewListener;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * GUI program (reactive version, interactive extension).
 *
 * The view subscribes to the stream of partial reports; sample() limits the
 * update rate (keeping the last, final, report) so the EDT is not flooded.
 * Stop = dispose of the subscription.
 */
public class FSStatRxGUI {

    public static void main(String[] args) {
        RxController controller = new RxController();
        FSStatView view = new FSStatView("reactive (RxJava)", controller);
        controller.setView(view);
        view.display();
    }
}

class RxController implements ViewListener {

    private static final long UPDATE_PERIOD_MS = 100;

    private final FSStatLibRx lib = new FSStatLibRx();
    private FSStatView view;
    private Disposable subscription;   // accessed only by the EDT

    void setView(FSStatView view) {
        this.view = view;
    }

    @Override
    public void startRequested(Path dir, long maxFS, int nb) {
        FSReport[] last = { FSReport.empty(maxFS, nb) };   // touched only by the (serialised) subscriber callbacks
        subscription = lib
                .getFSReportStream(dir, maxFS, nb)
                .sample(UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS, true)
                .subscribe(
                        report -> {
                            last[0] = report;
                            view.showReport(report);
                        },
                        err -> view.showFinished(null, "Failed: " + err),
                        () -> view.showFinished(last[0], "Completed: " + last[0].totalFiles() + " files."));
    }

    @Override
    public void stopRequested() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            view.showFinished(null, "Stopped.");
        }
    }
}
