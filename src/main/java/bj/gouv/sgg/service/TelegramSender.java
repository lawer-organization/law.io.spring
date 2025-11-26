package bj.gouv.sgg.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client HTTP responsable de l'envoi des messages Telegram.
 * Sépare l'aspect réseau de la construction des messages pour éviter
 * les appels @Async au sein du même bean.
 */
@Slf4j
@Component
public class TelegramSender {

    @Async
    public void sendAsync(String botToken, String chatId, String message) {
        try {
            send(botToken, chatId, message);
        } catch (IOException e) {
            log.error("Failed to send async Telegram notification", e);
        }
    }

    public void sendSync(String botToken, String chatId, String message) throws IOException {
        send(botToken, chatId, message);
    }

    private void send(String botToken, String chatId, String message) throws IOException {
        String urlString = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String postData = String.format(
            "chat_id=%s&text=%s&parse_mode=HTML",
            URLEncoder.encode(chatId, StandardCharsets.UTF_8),
            URLEncoder.encode(message, StandardCharsets.UTF_8)
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Telegram API returned status: " + responseCode);
        }
    }
}
