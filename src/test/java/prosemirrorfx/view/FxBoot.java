package prosemirrorfx.view;

import java.util.concurrent.*;
import javafx.application.Platform;

/** Starts JavaFX once for the whole test run, headless; test classes share it. */
final class FxBoot {

    static boolean started;

    static synchronized void start() throws InterruptedException {
        if (started) return;
        System.setProperty("glass.platform", "Headless");
        System.setProperty("prism.order", "sw");
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await(10, TimeUnit.SECONDS);
        started = true;
    }

    private FxBoot() {}
}
