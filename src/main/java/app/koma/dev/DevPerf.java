package app.koma.dev;

import app.koma.AppContext;
import app.koma.library.Volume;
import app.koma.ui.shell.Screen;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Frame-time benchmark. Enabled with -Dkoma.devperf=&lt;series count&gt; (use with -Dkoma.home=&lt;temp dir&gt;).
 * Seeds a synthetic library, drives the real UI (screen switches, library scrolling) and prints, per phase,
 * the average fps, the 95th / 99th percentile and worst frame time, the number of frames over 25 ms,
 * and how long each screen switch blocked the FX thread. Exits when done.
 */
public final class DevPerf {
    private final AppContext ctx;
    private final int count;
    private final Deque<Runnable> steps = new ArrayDeque<>();
    private final List<Double> deltas = new ArrayList<>();
    private final List<Double> blocked = new ArrayList<>();
    private final List<String> slow = new ArrayList<>();
    private long last;
    private long phaseStart;
    private boolean recording;

    private DevPerf(AppContext ctx, int count) {
        this.ctx = ctx;
        this.count = count;
    }

    public static void startIfRequested(AppContext ctx) {
        String n = System.getProperty("koma.devperf");
        if (n == null) return;
        new DevPerf(ctx, Integer.parseInt(n)).run();
    }

    /** One real CBZ (3 noisy 900x1300 JPEG pages) that every synthetic volume points at, so covers really decode. */
    private java.nio.file.Path makeCbz() throws java.io.IOException {
        java.nio.file.Path f = java.nio.file.Path.of(System.getProperty("koma.home", "."), "perf.cbz");
        java.util.Random rnd = new java.util.Random(7);
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(f))) {
            for (int p = 1; p <= 3; p++) {
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(900, 1300, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 1300; y++) {
                    for (int x = 0; x < 900; x++) {
                        int v = ((x * 255 / 900) + (y * 255 / 1300)) / 2 + rnd.nextInt(48);
                        bi.setRGB(x, y, (Math.min(255, v) << 16) | (Math.min(255, 255 - v / 2) << 8) | 120);
                    }
                }
                zip.putNextEntry(new java.util.zip.ZipEntry("p" + p + ".jpg"));
                javax.imageio.ImageIO.write(bi, "jpg", zip);
                zip.closeEntry();
            }
        }
        return f;
    }

    private void seed() {
        String now = Instant.now().toString();
        String cbz;
        long cbzSize;
        try {
            java.nio.file.Path f = makeCbz();
            cbz = f.toString();
            cbzSize = java.nio.file.Files.size(f);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (int i = 0; i < count; i++) {
            for (int v = 1; v <= 3; v++) {
                Volume vol = new Volume();
                vol.number = v;
                vol.path = cbz;
                vol.size = cbzSize;
                vol.mtime = 1000 + i * 4 + v; // distinct thumbnail key per volume, same real file
                vol.pages = 180;
                vol.addedAt = now;
                if (i < 8 && v == 1) vol.progress = new Volume.Progress(40 + i, now, false);
                ctx.store.addVolume("Perf Series " + i + " with a long title", i % 3 == 0 ? "テスト" : null,
                        i % 2 == 0 ? "manga" : "manhwa", i % 2 == 0 ? "rtl" : "ltr", vol);
            }
        }
    }

    private void phase(String name, double seconds, Runnable action) {
        steps.add(() -> {
            deltas.clear();
            blocked.clear();
            long t0 = System.nanoTime();
            if (action != null) action.run();
            blocked.add((System.nanoTime() - t0) / 1e6);
            recording = true;
            last = 0;
            slow.clear();
            phaseStart = System.nanoTime();
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(seconds));
            p.setOnFinished(e -> {
                recording = false;
                report(name);
                next();
            });
            p.play();
        });
    }

    private void switchLoop(String name, int rounds) {
        Screen[] cycle = {Screen.HOME, Screen.LIBRARY, Screen.SETTINGS, Screen.IMPORT};
        steps.add(() -> {
            deltas.clear();
            blocked.clear();
            recording = true;
            last = 0;
            int[] i = {0};
            javafx.animation.Timeline t = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(700), e -> {
                long t0 = System.nanoTime();
                Screen target = cycle[i[0]++ % cycle.length];
                ctx.router.navigate(target, null);
                double ms = (System.nanoTime() - t0) / 1e6;
                blocked.add(ms);
                System.out.printf("PERF   switch to %-9s blocked %.0f ms%n", target, ms);
            }));
            t.setCycleCount(rounds);
            t.setOnFinished(e -> {
                javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
                p.setOnFinished(x -> {
                    recording = false;
                    report(name);
                    next();
                });
                p.play();
            });
            t.play();
        });
    }

    private void report(String name) {
        if (deltas.isEmpty()) {
            System.out.println("PERF " + name + ": no frames");
            return;
        }
        double[] d = deltas.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(d);
        double sum = Arrays.stream(d).sum();
        long over = Arrays.stream(d).filter(x -> x > 25).count();
        String blk = blocked.isEmpty() ? "" : String.format(" | UI-thread block per switch: max %.0f ms, avg %.0f ms",
                blocked.stream().mapToDouble(Double::doubleValue).max().orElse(0),
                blocked.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        if (!slow.isEmpty()) System.out.println("PERF   slow frames (offset:duration): " + slow);
        System.out.printf("PERF %-26s frames %4d | avg %5.1f fps | p95 %5.1f ms | p99 %5.1f ms | worst %6.1f ms | >25ms: %d%s%n",
                name, d.length, d.length / (sum / 1000), d[(int) (d.length * 0.95)], d[(int) Math.min(d.length - 1, d.length * 0.99)],
                d[d.length - 1], over, blk);
    }

    private void next() {
        Runnable r = steps.poll();
        if (r != null) r.run();
    }

    private void run() {
        seed();
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (recording && last != 0) {
                    double d = (now - last) / 1e6;
                    deltas.add(d);
                    if (d > 100) slow.add(String.format("+%.0fms:%.0fms", (now - phaseStart) / 1e6, d));
                }
                last = now;
            }
        }.start();
        System.out.println("PERF library seeded: " + ctx.store.series().size() + " series, " + ctx.store.volumeCount() + " volumes");
        phase("home (open + idle 4s)", 4, () -> ctx.router.navigate(Screen.HOME, null));
        phase("library covers (open 3s)", 3, () -> ctx.router.navigate(Screen.LIBRARY, null));
        steps.add(() -> {
            deltas.clear();
            recording = true;
            last = 0;
            var sp = ctx.router.currentView().root() instanceof javafx.scene.control.ScrollPane s ? s : null;
            javafx.animation.Timeline t = new javafx.animation.Timeline();
            if (sp != null) {
                t.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(6),
                        new javafx.animation.KeyValue(sp.vvalueProperty(), 0.25, javafx.animation.Interpolator.LINEAR)));
            }
            t.setOnFinished(e -> {
                recording = false;
                report("library fast scroll (~15 rows/s)");
                next();
            });
            t.play();
        });
        phase("library details table", 2, () -> {
            ctx.router.navigate(Screen.LIBRARY, null);
            ctx.stage.getScene().getRoot().lookupAll(".chip").stream()
                    .filter(n -> n instanceof javafx.scene.control.ToggleButton b && "Details".equals(b.getText()))
                    .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).fire());
        });
        phase("series screen", 2, () -> ctx.router.navigate(Screen.SERIES, ctx.store.series().get(0).id));
        switchLoop("screen switching x16", 16);
        steps.add(() -> {
            Platform.exit();
        });
        next();
    }
}
