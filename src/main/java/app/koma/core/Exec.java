package app.koma.core;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Shared executors: virtual threads for disk I/O, a fixed pool for image decoding.
 * Nothing in here may run on the JavaFX Application Thread.
 */
public final class Exec {
    private Exec() {}

    public static final ExecutorService IO =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("koma-io-", 0).factory());

    private static final AtomicInteger DECODE_N = new AtomicInteger();
    public static final ExecutorService DECODE = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
            r -> {
                Thread t = new Thread(r, "koma-decode-" + DECODE_N.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    public static <T> CompletableFuture<T> io(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, IO);
    }

    public static CompletableFuture<Void> io(Runnable work) {
        return CompletableFuture.runAsync(work, IO);
    }

    public static <T> CompletableFuture<T> decode(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, DECODE);
    }

    /** Run on the FX thread (immediately if already on it). */
    public static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public static void shutdown() {
        IO.shutdownNow();
        DECODE.shutdownNow();
    }
}
