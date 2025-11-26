package bj.gouv.sgg.batch.listener;

import bj.gouv.sgg.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Envoie des notifications Telegram au démarrage et à la fin de chaque job Spring Batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramJobExecutionListener implements JobExecutionListener {

    private final TelegramNotificationService telegramNotificationService;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.FRENCH);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            String message = String.format("""
                ▶️ JOB DÉMARRÉ
                
                Job: %s
                Exécution: %d
                Début prévu: %s
                Paramètres: %s""",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStartTime() != null
                    ? DATE_FORMATTER.format(jobExecution.getStartTime())
                    : DATE_FORMATTER.format(java.time.LocalDateTime.now()),
                jobExecution.getJobParameters().isEmpty() ? "Aucun" : jobExecution.getJobParameters().toString()
            );
            telegramNotificationService.sendNotification(message);
        } catch (Exception e) {
            log.warn("Unable to send Telegram before-job notification", e);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            String icon = statusIcon(jobExecution.getStatus());
            Duration duration = computeDuration(jobExecution);
            long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
            long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();

            StringBuilder builder = new StringBuilder();
            builder.append(String.format("""
                %s JOB TERMINÉ
                
                Job: %s
                Exécution: %d
                Statut: %s
                Exit: %s
                Début: %s
                Fin: %s
                Durée: %s
                Lus: %d | Écrits: %d""",
                icon,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                formatDate(jobExecution.getStartTime()),
                formatDate(jobExecution.getEndTime()),
                duration != null ? formatDuration(duration) : "N/A",
                readCount,
                writeCount
            ));

            List<Throwable> failures = jobExecution.getAllFailureExceptions();
            if (!failures.isEmpty()) {
                builder.append("\n\n❌ Erreurs:\n");
                failures.stream()
                    .map(Throwable::getMessage)
                    .filter(msg -> msg != null && !msg.isBlank())
                    .limit(3)
                    .forEach(msg -> builder.append("• ").append(msg).append('\n'));
            }

            telegramNotificationService.sendNotification(builder.toString());
        } catch (Exception e) {
            log.warn("Unable to send Telegram after-job notification", e);
        }
    }

    private Duration computeDuration(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null) {
            return null;
        }
        java.time.LocalDateTime start = jobExecution.getStartTime();
        java.time.LocalDateTime end = jobExecution.getEndTime() != null
            ? jobExecution.getEndTime()
            : java.time.LocalDateTime.now();
        return Duration.between(start, end);
    }

    private String statusIcon(BatchStatus status) {
        if (status == null) {
            return "ℹ️";
        }
        return switch (status) {
            case COMPLETED -> "✅";
            case FAILED -> "🔴";
            case STOPPED -> "🟡";
            case ABANDONED -> "⚪️";
            default -> "ℹ️";
        };
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).toSeconds();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        return DATE_FORMATTER.format(dateTime);
    }
}
