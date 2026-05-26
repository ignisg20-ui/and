package com.shield.log;

import com.shield.ShieldPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Asynchronous rolling security logger.
 *
 * <p>All write operations are buffered into a lock-free queue and flushed by a
 * dedicated daemon executor every 250 ms or when the queue passes a soft
 * threshold. Rotation is triggered when the active file grows above the
 * configured size; older files are deleted past the keep-count limit.</p>
 */
public final class SecurityLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ShieldPlugin plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor;
    private final AtomicReference<Path> file = new AtomicReference<>();

    private volatile int rotateBytes;
    private volatile int keepFiles;
    private volatile Severity minLevel = Severity.INFO;

    public SecurityLogger(@NotNull ShieldPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Shield-Logger");
            t.setDaemon(true);
            return t;
        });
        reload();
        executor.scheduleAtFixedRate(this::flushSafe, 250, 250, TimeUnit.MILLISECONDS);
    }

    public void reload() {
        String rel = plugin.shieldConfig().string("logging.file", "logs/security.log");
        rotateBytes = Math.max(1, plugin.shieldConfig().integer("logging.rotate-mb", 10)) * 1024 * 1024;
        keepFiles = Math.max(1, plugin.shieldConfig().integer("logging.keep-files", 5));
        minLevel = Severity.parse(plugin.shieldConfig().string("logging.level", "INFO"), Severity.INFO);

        Path target = plugin.getDataFolder().toPath().resolve(rel);
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) Files.createFile(target);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not initialise log file " + target, ex);
        }
        file.set(target);
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        flushSafe();
    }

    public void info(@NotNull String message) {
        write(Severity.INFO, "INFO", message);
    }

    public void low(@NotNull String tag, @NotNull String message) {
        write(Severity.LOW, tag, message);
    }

    public void medium(@NotNull String tag, @NotNull String message) {
        write(Severity.MEDIUM, tag, message);
    }

    public void high(@NotNull String tag, @NotNull String message) {
        write(Severity.HIGH, tag, message);
    }

    public void critical(@NotNull String tag, @NotNull String message) {
        write(Severity.CRITICAL, tag, message);
    }

    public void write(@NotNull Severity severity, @NotNull String tag, @NotNull String message) {
        if (!plugin.shieldConfig().bool("logging.enabled", true)) return;
        if (!severity.isAtLeast(minLevel)) return;
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + severity.name() + "] [" + tag + "] " + message;
        queue.add(line);
        if (severity.isAtLeast(Severity.MEDIUM)) {
            plugin.getLogger().log(severity.isAtLeast(Severity.HIGH) ? Level.WARNING : Level.INFO,
                    "[Shield][" + tag + "] " + message);
        }
        if (queue.size() > 256) flushSafe();
    }

    private void flushSafe() {
        Path target = file.get();
        if (target == null || queue.isEmpty()) return;
        try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = queue.poll()) != null) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to flush security log", ex);
        }
        rotateIfNeeded(target);
    }

    private void rotateIfNeeded(Path target) {
        try {
            if (Files.size(target) < rotateBytes) return;
            Path parent = target.getParent();
            String base = target.getFileName().toString();
            for (int i = keepFiles - 1; i >= 1; i--) {
                Path from = parent.resolve(base + "." + i);
                Path to = parent.resolve(base + "." + (i + 1));
                if (Files.exists(from)) Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Path rotated = parent.resolve(base + ".1");
            Files.move(target, rotated, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.createFile(target);
            Path drop = parent.resolve(base + "." + (keepFiles + 1));
            if (Files.exists(drop)) Files.deleteIfExists(drop);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to rotate security log", ex);
        }
    }
}
