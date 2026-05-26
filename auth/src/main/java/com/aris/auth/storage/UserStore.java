package com.aris.auth.storage;

import com.aris.auth.model.AuthUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence backend for authentication accounts. Two implementations are
 * shipped: {@link SqliteUserStore} (default) and {@link YamlUserStore}. The
 * interface intentionally exposes only async operations so the main thread
 * never blocks on disk I/O.
 */
public interface UserStore {

    void init() throws IOException;

    void close();

    @NotNull CompletableFuture<@Nullable AuthUser> find(@NotNull UUID uuid);

    @NotNull CompletableFuture<Void> save(@NotNull AuthUser user);

    @NotNull CompletableFuture<Boolean> delete(@NotNull UUID uuid);
}
