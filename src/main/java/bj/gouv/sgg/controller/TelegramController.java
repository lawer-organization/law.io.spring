package bj.gouv.sgg.controller;

import bj.gouv.sgg.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint pour tester les notifications Telegram
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {
    
    private final TelegramNotificationService telegramNotificationService;
    
    /**
     * Test de connexion Telegram
     * GET /api/telegram/test
     */
    @GetMapping("/test")
    public ResponseEntity<?> testConnection() {
        log.info("Testing Telegram connection");
        
        boolean success = telegramNotificationService.testConnection();
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Telegram test message sent successfully"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Telegram not configured or test failed"
            ));
        }
    }
    
    /**
     * Envoyer un message Telegram personnalisé
     * POST /api/telegram/send
     * Body: {"message": "Your message here"}
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Message cannot be empty"
            ));
        }
        
        log.info("Sending custom Telegram message: {}", message);
        telegramNotificationService.sendNotification(message);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Notification sent"
        ));
    }
}
