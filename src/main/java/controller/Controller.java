package controller;

import lib.FSReport;
import model.Model;
import model.ModelListener;
import view.View;

import javax.swing.*;
import java.nio.file.Path;

public class Controller {
    private Model model;
    private View view;

    public Controller(Model model) {
        this.model = model;
    }

    public void setView(View view) {
        this.view = view;
    }

    public FSReport getCurrentReport() {
        return model.getCurrentReport();
    }

    public void start(final String folder, final int nb, final long maxSize) {
        view.startPolling();
        model.setListener(new ModelListener() {
            @Override
            public void onComplete(FSReport report) {
                view.stopPolling();
                SwingUtilities.invokeLater(() -> view.render(report));
            }
        });
        model.start(Path.of(folder), maxSize, nb);
    }

    public void stop() {
        this.model.stop();
    }
}
