package app.event_loop;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lib.FSReport;
import lib.event_loop.FSStatVerticle;
import view.FSStatView;
import view.ViewListener;

import java.nio.file.Path;

/**
 * GUI program (event-loop version, interactive extension).
 *
 * The controller never shares state with the scanning verticle: it deploys
 * it to start a scan and sends a message on the event bus to stop it.
 * Updates come back through the event bus to ViewAgent, a verticle whose
 * handlers run on one event loop (so updates and the final outcome are
 * received in order) and are forwarded to the Swing EDT by the view.
 */
public class FSStatVertxGUI {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        VertxController controller = new VertxController(vertx);
        FSStatView view = new FSStatView("event loop (Vert.x)", controller);
        vertx.deployVerticle(new ViewAgent(view)).onSuccess(id -> view.display());
        controller.setView(view);
    }
}

class VertxController implements ViewListener {

    private final Vertx vertx;
    private FSStatView view;

    VertxController(Vertx vertx) {
        this.vertx = vertx;
    }

    void setView(FSStatView view) {
        this.view = view;
    }

    @Override
    public void startRequested(Path dir, long maxFS, int nb) {
        vertx.deployVerticle(new FSStatVerticle(dir.toString(), maxFS, nb))
             .onFailure(err -> view.showFinished(null, "Failed: " + err.getMessage()));
    }

    @Override
    public void stopRequested() {
        vertx.eventBus().publish(FSStatVerticle.ADDR_STOP, "stop");
    }
}

class ViewAgent extends VerticleBase {

    private final FSStatView view;

    ViewAgent(FSStatView view) {
        this.view = view;
    }

    @Override
    public Future<?> start() throws Exception {
        vertx.eventBus().<JsonObject>consumer(FSStatVerticle.ADDR_UPDATE, msg ->
                view.showReport(FSStatVerticle.fromJson(msg.body())));

        vertx.eventBus().<JsonObject>consumer(FSStatVerticle.ADDR_DONE, msg -> {
            JsonObject body = msg.body();
            FSReport report = FSStatVerticle.fromJson(body.getJsonObject("report"));
            String status = switch (body.getString("status")) {
                case "completed" -> "Completed: " + report.totalFiles() + " files.";
                case "stopped" -> "Stopped: " + report.totalFiles() + " files counted so far.";
                default -> "Failed: " + body.getString("error");
            };
            view.showFinished(report, status);
        });
        return super.start();
    }
}
