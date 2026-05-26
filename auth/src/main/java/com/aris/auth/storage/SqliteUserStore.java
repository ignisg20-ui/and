package com.aris.auth.storage;

import com.aris.auth.AuthPlugin;
import com.aris.auth.model.AuthUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite-backed user store. All operations run on a single-threaded executor
 * so we get strict ordering without locking the main thread.
 */
public final class SqliteUserStore implements UserStore {

    private final AuthPlugin plugin;
    private final File dbFile;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ArisAuth-DB");
        t.setDaemon(true);
        return t;
    });

    private volatile Connection conn;

    public SqliteUserStore(@NotNull AuthPlugin plugin, @NotNull File dbFile) {
        this.plugin = plugin;
        this.dbFile = dbFile;
    }

    @Override
    public void init() throws IOException {
        if (!dbFile.getParentFile().exists() && !dbFile.getParentFile().mkdirs()) {
            throw new IOException("Cannot create directory " + dbFile.getParentFile());
        }
        try {
            // JDBC ServiceLoader discovers org.sqlite.JDBC via the META-INF/services
            // entry that the shadow `mergeServiceFiles()` step preserves. Calling
            // Class.forName explicitly is belt-and-braces for plugins loaded under
            // legacy classloaders that don't trigger ServiceLoader.
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            uuid TEXT PRIMARY KEY NOT NULL,
                            name TEXT NOT NULL,
                            password TEXT NOT NULL,
                            last_ip TEXT,
                            last_login_ms INTEGER NOT NULL DEFAULT 0,
                            created_ms INTEGER NOT NULL
                        )
                        """);
            }
        } catch (ClassNotFoundException | SQLException ex) {
            throw new IOException("Failed to initialise SQLite store", ex);
        }
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to close SQLite", ex);
            }
        }
    }

    @Override
    public @NotNull CompletableFuture<@Nullable AuthUser> find(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, name, password, last_ip, last_login_ms, created_ms FROM users WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return new AuthUser(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getString("password"),
                            rs.getString("last_ip"),
                            rs.getLong("last_login_ms"),
                            rs.getLong("created_ms"));
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read user " + uuid, ex);
                return null;
            }
        }, io);
    }

    @Override
    public @NotNull CompletableFuture<Void> save(@NotNull AuthUser user) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO users (uuid, name, password, last_ip, last_login_ms, created_ms)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        password = excluded.password,
                        last_ip = excluded.last_ip,
                        last_login_ms = excluded.last_login_ms
                    """)) {
                ps.setString(1, user.uuid().toString());
                ps.setString(2, user.name());
                ps.setString(3, user.passwordHash());
                ps.setString(4, user.lastIp());
                ps.setLong(5, user.lastLoginMs());
                ps.setLong(6, user.createdMs());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save user " + user.uuid(), ex);
            }
        }, io);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> delete(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                return ps.executeUpdate() > 0;
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete user " + uuid, ex);
                return false;
            }
        }, io);
    }
}
