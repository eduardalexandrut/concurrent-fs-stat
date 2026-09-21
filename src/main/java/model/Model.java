package model;

import lib.FSReport;
import lib.FSStatLib;
import lib.FSUpdateListener;

import java.nio.file.Path;

public class Model {
    private FSStatLib lib;
    private ModelListener listener;

    public Model(FSStatLib lib) {
        this.lib = lib;
    }

    public void setListener(final ModelListener listener) {
        this.listener = listener;
    }

    public FSReport getCurrentReport() {
        return lib.getCurrentReport();
    }

    public void start(Path path, long max, int nb) {
        this.lib.getFSReport(path, max, nb, new FSUpdateListener() {
//            @Override
//            public void onUpdate(FSReport report) {
//                listener.onUpdate(report);
//            }

            @Override
            public void onComplete(FSReport report) {
                listener.onComplete(report);
            }
        });
    }

    public void stop() {
        this.lib.stop();
    }

}
