package com.aris.auth.security;

import org.mindrot.jbcrypt.BCrypt;
import org.jetbrains.annotations.NotNull;

/**
 * Thin wrapper around jBCrypt. Centralises the cost factor so the rest of the
 * codebase never reaches into the library directly.
 */
public final class PasswordHasher {

    private final int rounds;

    public PasswordHasher(int rounds) {
        this.rounds = Math.max(4, Math.min(14, rounds));
    }

    public @NotNull String hash(@NotNull String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(rounds));
    }

    public boolean verify(@NotNull String password, @NotNull String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException ex) {
            // Hash is malformed.
            return false;
        }
    }
}
