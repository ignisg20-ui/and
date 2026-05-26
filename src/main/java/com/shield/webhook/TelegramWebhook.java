package com.shield.webhook;

import com.shield.log.Severity;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal Telegram bot sender using the {@code sendMessage} endpoint.
 */
public final class TelegramWebhook {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean enabled;
    private final String botToken;
    private final String chatId;

    public TelegramWebhook(boolean enabled, @NotNull String botToken, @NotNull String chatId) {
        this.enabled = enabled;
        this.botToken = botToken;
        this.chatId = chatId;
    }

    public boolean isEnabled() {
        return enabled && !botToken.isBlank() && !chatId.isBlank();
    }

    public void send(@NotNull Severity severity, @NotNull String title, @NotNull String message) throws Exception {
        String text = "[Shield][" + severity.name() + "] " + title + "\n" + message;
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String form = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&disable_web_page_preview=true";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(7))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Shield-Plugin")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HTTP.send(req, HttpResponse.BodyHandlers.discarding());
    }
}
