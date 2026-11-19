package com.digitalvoting.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Runs slow work (DB, HTTP, camera/OpenCV) off the Swing thread, then calls back on it. */
public final class Async {
    private Async() {}

    public static <T> void run(Callable<T> work, Consumer<T> onOk, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                try { onOk.accept(get()); }
                catch (ExecutionException e) { onError.accept(e.getCause() == null ? e : e.getCause()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); onError.accept(e); }
            }
        }.execute();
    }
}
