package com.shield.webhook;

import com.shield.log.Severity;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal Discord webhook sender that posts a single embed per alert.
 */
public final class DiscordWebhook {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean enabled;
    private final String url;
    private final String username;
    private final String avatarUrl;

    public DiscordWebhook(boolean enabled, @NotNull String url, @NotNull String username, @NotNull String avatarUrl) {
        this.enabled = enabled;
        this.url = url;
        this.username = username;
        this.avatarUrl = avatarUrl;
    }

    public boolean isEnabled() {
        return enabled && !url.isBlank();
    }

    public void send(@NotNull Severity severity, @NotNull String title, @NotNull String message) throws Exception {
        int color = switch (severity) {
            case CRITICAL -> 0x8B0000;
            case HIGH -> 0xCC2222;
            case MEDIUM -> 0xE08A1F;
            case LOW -> 0xE0C821;
            case INFO -> 0x2F8DDC;
        };
        String body = "{\"username\":\"" + esc(username) + "\","
                + "\"avatar_url\":\"" + esc(avatarUrl) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + esc(title) + "\","
                + "\"description\":\"" + esc(message) + "\","
                + "\"color\":" + color
                + "}]}";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(7))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "Shield-Plugin")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HTTP.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
