package bj.gouv.sgg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service pour envoyer des notifications Telegram
 * 
 * Configuration requise dans application.yml:
 * telegram:
 *   bot-token: "YOUR_BOT_TOKEN"
 *   chat-id: "YOUR_CHAT_ID"
 *   enabled: true
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramSender telegramSender;
    
    @Value("${telegram.bot-token:}")
    private String botToken;
    
    @Value("${telegram.chat-id:}")
    private String chatId;
    
    @Value("${telegram.enabled:false}")
    private boolean enabled;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Envoie une notification Telegram de façon asynchrone via le sender dédié.
     */
    public void sendNotification(String message) {
        if (!canSendNotifications()) {
            return;
        }

        String fullMessage = formatWithHeader(message);
        telegramSender.sendAsync(botToken, chatId, fullMessage);
    }
    
    /**
     * Notification pour job bloqué
     */
    public void notifyStuckJob(Long jobExecutionId, String jobName, long hoursRunning) {
        String message = String.format("""
            ⚠️ JOB BLOQUÉ DÉTECTÉ
            
            Job ID: %d
            Job: %s
            Durée: %d heures
            
            → Le job a été automatiquement marqué comme FAILED""",
            jobExecutionId, jobName, hoursRunning
        );
        sendNotification(message);
    }
    
    /**
     * Notification pour erreur critique
     */
    public void notifyCriticalError(String context, String errorMessage) {
        String message = String.format("""
            🔴 ERREUR CRITIQUE
            
            Contexte: %s
            Erreur: %s""",
            context, errorMessage
        );
        sendNotification(message);
    }
    
    /**
     * Notification pour succès de job important
     */
    public void notifyJobSuccess(String jobName, long documentsProcessed, long durationMinutes) {
        String message = String.format("""
            ✅ JOB TERMINÉ
            
            Job: %s
            Documents traités: %d
            Durée: %d minutes""",
            jobName, documentsProcessed, durationMinutes
        );
        sendNotification(message);
    }
    
    /**
     * Notification pour démarrage de l'application
     */
    public void notifyApplicationStarted() {
        String message = "🚀 APPLICATION DÉMARRÉE\n\nLaw Spring Batch est opérationnel";
        sendNotification(message);
    }
    
    /**
     * Notification pour arrêt de l'application
     */
    public void notifyApplicationStopped() {
        String message = "🛑 APPLICATION ARRÊTÉE\n\nLaw Spring Batch s'est arrêté";
        sendNotification(message);
    }
    
    /**
     * Test de la configuration Telegram
     */
    public boolean testConnection() {
        if (!isConfigurationReadyForTest()) {
            return false;
        }
        
        try {
            telegramSender.sendSync(botToken, chatId, "🧪 Test de connexion Telegram - Law Spring Batch");
            log.info("Telegram test message sent successfully");
            return true;
        } catch (IOException e) {
            log.error("Telegram test failed", e);
            return false;
        }
    }

    private boolean canSendNotifications() {
        if (!enabled) {
            log.debug("Telegram notifications disabled");
            return false;
        }
        
        if (isBlank(botToken) || isBlank(chatId)) {
            log.warn("Telegram bot token or chat ID not configured");
            return false;
        }

        return true;
    }

    private boolean isConfigurationReadyForTest() {
        if (!enabled) {
            log.info("Telegram notifications are disabled");
            return false;
        }
        
        if (isBlank(botToken) || isBlank(chatId)) {
            log.warn("Telegram configuration incomplete");
            return false;
        }
        return true;
    }

    private String formatWithHeader(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        return String.format("🤖 Law Spring Batch%n⏰ %s%n%n%s", timestamp, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
