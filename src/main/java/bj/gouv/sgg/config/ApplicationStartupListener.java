package bj.gouv.sgg.config;

import bj.gouv.sgg.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener pour les événements de démarrage et arrêt de l'application
 * Envoie des notifications Telegram pour ces événements importants
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {
    
    private final TelegramNotificationService telegramNotificationService;
    
    /**
     * Appelé quand l'application est prête (après démarrage complet)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Application ready - sending Telegram notification");
        telegramNotificationService.notifyApplicationStarted();
    }
    
    /**
     * Appelé quand l'application est en train de s'arrêter
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        log.info("🛑 Application shutting down - sending Telegram notification");
        telegramNotificationService.notifyApplicationStopped();
    }
}
